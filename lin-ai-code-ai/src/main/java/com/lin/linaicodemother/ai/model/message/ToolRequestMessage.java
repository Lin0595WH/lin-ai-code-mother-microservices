package com.lin.linaicodemother.ai.model.message;


import dev.langchain4j.service.tool.BeforeToolExecution;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @Author Lin
 * @Date 2026/3/21 17:07
 * @Descriptions 工具调用消息
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ToolRequestMessage extends StreamMessage {

    private String id;

    private String name;

    private String arguments;

    public ToolRequestMessage(BeforeToolExecution beforeToolExecution ) {
        super(StreamMessageTypeEnum.TOOL_REQUEST.getValue());
        this.id = beforeToolExecution.request().id();
        this.name = beforeToolExecution.request().name();
        this.arguments = beforeToolExecution.request().arguments();
    }
}
