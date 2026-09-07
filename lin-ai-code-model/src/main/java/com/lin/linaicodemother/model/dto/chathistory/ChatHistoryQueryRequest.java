package com.lin.linaicodemother.model.dto.chathistory;

import com.lin.linaicodemother.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话历史查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChatHistoryQueryRequest extends PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    private Long id;

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 消息类型（user/ai）
     */
    private String messageType;

    /**
     * 消息内容（模糊搜索）
     */
    private String message;

    /**
     * 创建用户id
     */
    private Long userId;

    /**
     * 游标查询 - 最后一条记录的创建时间
     * 用于分页查询，获取早于此时间的记录
     */
    private LocalDateTime lastCreateTime;

    /**
     * 排序顺序（默认降序）
     */
    private String sortOrder = "descend";

}
