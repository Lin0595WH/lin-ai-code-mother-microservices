package com.lin.linaicodemother.ai;

import com.lin.linaicodemother.ai.model.RoutingResult;
import com.lin.linaicodemother.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.SystemMessage;

/**
 * AI代码生成类型智能路由服务
 * 使用结构化输出直接返回枚举类型
 *
 */
public interface AiCodeGenTypeRoutingService {

    /**
     * 根据用户需求智能生成应用描述
     *
     * @param userPrompt 用户输入的需求描述
     * @return 推荐的代码生成类型以及应用名称
     */
    @SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
    RoutingResult routing(String userPrompt);

    /**
     * 根据用户需求智能选择代码生成类型
     *
     * @param userPrompt 用户输入的需求描述
     * @return 推荐的代码生成类型
     */
    @SystemMessage(fromResource = "prompt/codegen-routing2-system-prompt.txt")
    CodeGenTypeEnum routeCodeGenType(String userPrompt);
}
