package com.lin.linaicodemother.ai.model.message;


import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @Author Lin
 * @Date 2026/3/21 17:06
 * @Descriptions AI 响应消息
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AiResponseMessage extends StreamMessage {

    private String data;

    public AiResponseMessage(String data) {
        super(StreamMessageTypeEnum.AI_RESPONSE.getValue());
        this.data = data;
    }
}