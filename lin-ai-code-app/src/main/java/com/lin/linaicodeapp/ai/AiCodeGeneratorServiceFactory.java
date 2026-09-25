package com.lin.linaicodeapp.ai;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lin.linaicodemother.ai.AiCodeGeneratorService;
import com.lin.linaicodemother.ai.guardrail.PromptSafetyInputGuardrail;
import com.lin.linaicodemother.ai.tools.ToolManager;
import com.lin.linaicodemother.model.enums.CodeGenTypeEnum;
import com.lin.linaicodeapp.service.ChatHistoryService;
import com.lin.linaicodemother.utils.SpringContextUtil;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * @Author Lin
 * @Date 2026/1/7 21:18
 * @Descriptions AI 服务创建工厂
 */
@Slf4j
@Configuration
public class AiCodeGeneratorServiceFactory {

    private final ChatModel chatModel;

    private final ChatMemoryStore redisChatMemoryStore;

    private final ChatHistoryService chatHistoryService;

    private final ToolManager toolManager;

    public AiCodeGeneratorServiceFactory(
            @Qualifier("openAiChatModel") ChatModel chatModel,
            ChatMemoryStore redisChatMemoryStore,
            ChatHistoryService chatHistoryService,
            ToolManager toolManager
    ) {
        this.chatModel = chatModel;
        this.redisChatMemoryStore = redisChatMemoryStore;
        this.chatHistoryService = chatHistoryService;
        this.toolManager = toolManager;
    }

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) ->
                    log.debug("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause))
            .build();

    /**
     * 根据 appId 获取服务（为了兼容老逻辑）
     *
     * @param appId
     * @return
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    /**
     * 根据 appId 获取服务
     *
     * @param appId
     * @return
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        String cacheKey = buildCacheKey(appId, codeGenType);
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
    }

    /**
     * 构建缓存键
     *
     * @param appId       应用id
     * @param codeGenType 代码生成类型
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + ":" + codeGenType.getValue();
    }

    /**
     * 创建新的 AI 服务实例
     *
     * @param appId
     * @return
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        log.info("为 appId: {} 创建新的 AI 服务实例", appId);
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(50)
                .build();
        // 从数据库中加载对话历史到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        // 根据代码生成类型选择不同的模型配置
        return switch (codeGenType) {
            case VUE_PROJECT -> {
                StreamingChatModel reasoningStreamingChatModel = SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .streamingChatModel(reasoningStreamingChatModel)
                        .chatMemoryProvider(memoryId -> chatMemory)
                        .tools((Object[]) toolManager.getAllTools())
                        .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                                toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                        ))
                        .maxSequentialToolsInvocations(20)  // 最多连续调用 20 次工具
                        // 添加输入护轨
                        .inputGuardrails(new PromptSafetyInputGuardrail())
                        .build();
            }

            // HTML 和多文件生成使用默认模型
            case HTML, MULTI_FILE -> {
                StreamingChatModel openAiStreamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
                // 使用多例模式的 StreamingChatModel 解决并发问题
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(chatModel)
                        .streamingChatModel(openAiStreamingChatModel)
                        .chatMemory(chatMemory)
                        // 添加输入护轨
                        .inputGuardrails(new PromptSafetyInputGuardrail())
                        .build();
            }
        };
    }

    /**
     * 创建 AI 代码生成器服务
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0);
    }
}
