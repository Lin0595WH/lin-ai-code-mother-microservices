package com.lin.linaicodemother;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * @Author Lin
 * @Date 2026/9/9 20:37
 * @Descriptions 用户服务主启动类
 */
@ComponentScan("com.lin")
@SpringBootApplication
@MapperScan("com.lin.linaicodemother.mapper")
public class LinAiCodeUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(LinAiCodeUserApplication.class, args);
    }
}
