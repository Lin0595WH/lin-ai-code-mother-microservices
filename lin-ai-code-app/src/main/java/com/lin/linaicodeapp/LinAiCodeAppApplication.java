package com.lin.linaicodeapp;


import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * @Author Lin
 * @Date 2026/9/10 21:56
 * @Descriptions app服务主启动类
 */
@EnableDubbo
@EnableCaching
@MapperScan("com.lin.linaicodeapp.mapper")
@SpringBootApplication(scanBasePackages = {"com.lin.linaicodeapp", "com.lin.linaicodemother"})
public class LinAiCodeAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(LinAiCodeAppApplication.class, args);
    }
}
