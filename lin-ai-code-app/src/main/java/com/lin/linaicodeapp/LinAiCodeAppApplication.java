package com.lin.linaicodeapp;


import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
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
@SpringBootApplication(
        scanBasePackages = {"com.lin.linaicodeapp", "com.lin.linaicodemother"},
        exclude = {RedisEmbeddingStoreAutoConfiguration.class})
public class LinAiCodeAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(LinAiCodeAppApplication.class, args);
    }
}
