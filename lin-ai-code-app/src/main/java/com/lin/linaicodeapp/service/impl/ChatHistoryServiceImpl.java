package com.lin.linaicodeapp.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.lin.linaicodemother.constant.UserConstant;
import com.lin.linaicodemother.exception.BusinessException;
import com.lin.linaicodemother.exception.ErrorCode;
import com.lin.linaicodemother.exception.ThrowUtils;
import com.lin.linaicodeapp.mapper.ChatHistoryMapper;
import com.lin.linaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.lin.linaicodemother.model.entity.App;
import com.lin.linaicodemother.model.entity.ChatHistory;
import com.lin.linaicodemother.model.entity.User;
import com.lin.linaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.lin.linaicodeapp.service.AppService;
import com.lin.linaicodeapp.service.ChatHistoryService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.lin.linaicodemother.model.entity.table.ChatHistoryTableDef.CHAT_HISTORY;

/**
 * 对话历史 服务层实现。
 *
 * @author Lin
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Lazy))
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    private final AppService appService;

    /**
     * 添加对话历史
     *
     * @param appId       应用 id
     * @param message     消息
     * @param messageType 消息类型
     * @param userId      用户 id
     * @return 是否成功
     */
    @Override
    public boolean addChatMessage(Long appId, String message, String messageType, Long userId) {
        // 基础校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(CharSequenceUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(CharSequenceUtil.isBlank(messageType), ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        // 验证消息类型是否有效
        ChatHistoryMessageTypeEnum messageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "不支持的消息类型");
        // 插入数据库
        ChatHistory chatHistory = ChatHistory.builder()
                .appId(appId)
                .message(message)
                .messageType(messageType)
                .userId(userId)
                .build();
        return this.save(chatHistory);
    }

    /**
     * 根据应用 id 删除所有对话历史
     *
     * @param appId 应用 id
     * @return 是否删除成功
     */
    @Override
    public boolean deleteByAppId(Long appId) {
        if (appId == null || appId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        }
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("app_id", appId);
        return this.remove(queryWrapper);
    }

    /**
     * 分页查询某 APP 的对话记录
     *
     * @param appId
     * @param pageSize
     * @param lastCreateTime
     * @param loginUser
     * @return
     */
    @Override
    public Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize, LocalDateTime lastCreateTime, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50, ErrorCode.PARAMS_ERROR, "页面大小必须在1-50之间");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 验证权限：只有应用创建者和管理员可以查看
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权查看该应用的对话历史");
        // 构建查询条件
        ChatHistoryQueryRequest queryRequest = new ChatHistoryQueryRequest();
        queryRequest.setAppId(appId);
        queryRequest.setLastCreateTime(lastCreateTime);
        QueryWrapper queryWrapper = this.getQueryWrapper(queryRequest);
        // 查询数据
        return this.page(Page.of(1, pageSize), queryWrapper);
    }

    /**
     * 加载对话历史到内存
     *
     * @param appId      应用 id
     * @param chatMemory 对话记忆
     * @param maxCount   最多加载多少条
     * @return 加载成功的条数
     */
    @Override
    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
        maxCount = Math.clamp(maxCount, 1, 20);
        try {
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq(ChatHistory::getAppId, appId)
                    .orderBy(ChatHistory::getCreateTime, false)
                    .limit(1, maxCount);
            List<ChatHistory> historyList = this.list(queryWrapper);
            if (CollUtil.isEmpty(historyList)) {
                return 0;
            }
            // 反转列表，确保按照时间正序（老的在前，新的在后）
            historyList = historyList.reversed();
            // 按照时间顺序将消息添加到记忆中
            int loadedCount = 0;
            // 先清理历史缓存，防止重复加载
            chatMemory.clear();
            for (ChatHistory history : historyList) {
                if (ChatHistoryMessageTypeEnum.USER.getValue().equals(history.getMessageType())) {
                    chatMemory.add(UserMessage.from(history.getMessage()));
                } else if (ChatHistoryMessageTypeEnum.AI.getValue().equals(history.getMessageType())) {
                    chatMemory.add(AiMessage.from(history.getMessage()));
                }
                loadedCount++;
            }
            log.info("成功为 appId: {} 加载 {} 条历史消息", appId, loadedCount);
            return loadedCount;
        } catch (Exception e) {
            log.error("加载历史对话失败，appId: {}, error: {}", appId, e.getMessage(), e);
            // 加载失败不影响系统运行，只是没有历史上下文
            return 0;
        }
    }

    /**
     * 构造对话历史查询条件
     *
     * @param chatHistoryQueryRequest 查询条件
     * @return 查询条件
     */
    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (chatHistoryQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chatHistoryQueryRequest.getId();
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.where(CHAT_HISTORY.ID.eq( id))
                .and(CHAT_HISTORY.MESSAGE.like(message))
                .and(CHAT_HISTORY.MESSAGE_TYPE.eq(messageType))
                .and(CHAT_HISTORY.APP_ID.eq(appId))
                .and(CHAT_HISTORY.USER_ID.eq(userId));
        // 游标查询逻辑 - 只使用 createTime 作为游标
        if (lastCreateTime != null) {
            queryWrapper.and(CHAT_HISTORY.CREATE_TIME.lt(lastCreateTime));
        }
        // 排序
        if (CharSequenceUtil.isNotBlank(sortField)) {
            // 使用 TableDef 进行类型安全的排序
            boolean isAscend = "ascend".equals(sortOrder);
            switch (sortField) {
                case "id":
                    queryWrapper.orderBy(CHAT_HISTORY.ID, isAscend);
                    break;
                case "message":
                    queryWrapper.orderBy(CHAT_HISTORY.MESSAGE, isAscend);
                    break;
                case "messageType":
                    queryWrapper.orderBy(CHAT_HISTORY.MESSAGE_TYPE, isAscend);
                    break;
                case "appId":
                    queryWrapper.orderBy(CHAT_HISTORY.APP_ID, isAscend);
                    break;
                case "userId":
                    queryWrapper.orderBy(CHAT_HISTORY.USER_ID, isAscend);
                    break;
                case "createTime":
                    queryWrapper.orderBy(CHAT_HISTORY.CREATE_TIME, isAscend);
                    break;
                case "updateTime":
                    queryWrapper.orderBy(CHAT_HISTORY.UPDATE_TIME, isAscend);
                    break;
                default:
                    // 无效字段，使用默认排序
                    queryWrapper.orderBy(CHAT_HISTORY.CREATE_TIME.desc());
                    break;
            }
        } else {
            // 默认按创建时间降序排列
            queryWrapper.orderBy(CHAT_HISTORY.CREATE_TIME.desc());
        }
        return queryWrapper;
    }


}
