package com.lin.linaicodemother;


import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author Lin
 * @Date 2026/9/10 22:18
 * @Descriptions 截图服务启动类
 */
@EnableDubbo
@SpringBootApplication
public class LinAiCodeScreenshotApplication {
    public static void main(String[] args) {
        SpringApplication.run(LinAiCodeScreenshotApplication.class, args);
    }
}
