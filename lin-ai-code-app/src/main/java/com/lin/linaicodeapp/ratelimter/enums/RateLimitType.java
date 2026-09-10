package com.lin.linaicodeapp.ratelimter.enums;


/**
 * @Author Lin
 * @Date 2026/7/6 21:40
 * @Descriptions 限流类型枚举，支持接口、用户、IP多个维度的限流。
 */
public enum RateLimitType {
    /**
     * 接口级别限流
     */
    API,

    /**
     * 用户级别限流
     */
    USER,

    /**
     * IP级别限流
     */
    IP,

    /**
     * 应用对话级别限流
     */
    APPID
}
