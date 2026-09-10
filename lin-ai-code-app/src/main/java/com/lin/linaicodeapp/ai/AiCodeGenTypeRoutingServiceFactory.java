package com.lin.linaicodeapp.ai;

import com.lin.linaicodemother.ai.AiCodeGenTypeRoutingService;
import com.lin.linaicodemother.ai.guardrail.RetryOutputGuardrail;
import com.lin.linaicodemother.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI代码生成类型路由服务工厂
 *
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiCodeGenTypeRoutingServiceFactory {

    /**
     * 创建AI代码生成类型路由服务实例
     */
    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService() {
        ChatModel chatModel = SpringContextUtil.getBean("routingChatModelPrototype", ChatModel.class);
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(chatModel)
                // 添加输出护轨，为了流式输出，只在路由这里使用，ai对话不使用
                .outputGuardrails(new RetryOutputGuardrail())
                .build();
    }

    /**
     * 创建AI代码生成类型路由服务实例
     */
    @Bean
    public AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService() {
        return createAiCodeGenTypeRoutingService();
    }
}