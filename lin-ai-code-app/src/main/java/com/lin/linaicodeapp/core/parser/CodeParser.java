package com.lin.linaicodeapp.core.parser;


/**
 * @Author Lin
 * @Date 2026/1/12 20:56
 * @Descriptions 代码解析器策略接口
 */
public interface CodeParser<T> {

    /**
     * 解析代码内容
     *
     * @param codeContent 原始代码内容
     * @return 解析后的结果对象
     */
    T parseCode(String codeContent);
}
