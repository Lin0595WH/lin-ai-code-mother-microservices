package com.lin.linaicodemother.ai.model;


import com.lin.linaicodemother.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * @Author Lin
 * @Date 2026/3/26 22:27
 * @Descriptions 路由结果
 */
@Data
@Description("针对用户提示词智能路由结果")
public class RoutingResult {

    /**
     * 代码生成类型枚举
     */
    @Description("代码生成类型枚举")
    private CodeGenTypeEnum codeGenTypeEnum;

    /**
     * 应用名称（最多12字符，空字符串表示无）
     */
    @Description("应用名称")
    private String appName;
}
