package com.lin.linaicodemother.ai.config;


import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author Lin
 * @Date 2026/1/26 21:40
 * @Descriptions Redis存储对话记忆的配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedisChatMemoryStoreConfig {

    private String host;

    private String username;

    private int port;

    private String password;

    private long ttl;

    @Bean
    public RedisChatMemoryStore redisChatMemoryStore() {
        return RedisChatMemoryStore.builder()
                .host(host)
                // 密码不为空才开user
                //.user( username)
                .port(port)
                .password(password)
                .ttl(ttl)
                .build();
    }
}
