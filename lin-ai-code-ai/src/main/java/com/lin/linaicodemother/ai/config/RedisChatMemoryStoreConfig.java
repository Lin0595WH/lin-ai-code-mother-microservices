package com.lin.linaicodemother.ai.config;


import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * @Author Lin
 * @Date 2026/1/26 21:40
 * @Descriptions Redis存储对话记忆的配置类
 */
@Configuration
public class RedisChatMemoryStoreConfig {

    @Bean
    public ChatMemoryStore redisChatMemoryStore(StringRedisTemplate redis,
            @Value("${spring.data.redis.ttl:3600}") long ttl) {
        return new ChatMemoryStore() {
            private String key(Object memoryId) {
                return "app:chat:memory:" + ensureNotBlank(
                        ensureNotNull(memoryId, "memoryId").toString(), "memoryId");
            }

            @Override
            public List<ChatMessage> getMessages(Object memoryId) {
                String json = redis.opsForValue().get(key(memoryId));
                return json == null ? List.of() : ChatMessageDeserializer.messagesFromJson(json);
            }

            @Override
            public void updateMessages(Object memoryId, List<ChatMessage> messages) {
                String json = ChatMessageSerializer.messagesToJson(ensureNotNull(messages, "messages"));
                if (ttl > 0) {
                    redis.opsForValue().set(key(memoryId), json, Duration.ofSeconds(ttl));
                } else {
                    redis.opsForValue().set(key(memoryId), json);
                }
            }

            @Override
            public void deleteMessages(Object memoryId) {
                redis.delete(key(memoryId));
            }
        };
    }
}
