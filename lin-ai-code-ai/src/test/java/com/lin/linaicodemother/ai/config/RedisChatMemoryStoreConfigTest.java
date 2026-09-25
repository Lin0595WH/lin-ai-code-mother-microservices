package com.lin.linaicodemother.ai.config;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisChatMemoryStoreConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldRoundTripToolMessagesWithTtlAndClearOnlyItsOwnMemory() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class, withSettings().mockMaker(MockMakers.SUBCLASS));
        ValueOperations<String, String> values = mock(ValueOperations.class, withSettings().mockMaker(MockMakers.SUBCLASS));
        Map<String, String> data = new HashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> data.get(call.getArgument(0)));
        doAnswer(call -> {
            data.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.delete(anyString())).thenAnswer(call -> data.remove(call.getArgument(0)) != null);
        var config = new RedisChatMemoryStoreConfig();
        var store = config.redisChatMemoryStore(redis, 3600);
        var tool = ToolExecutionRequest.builder().id("call-1").name("writeFile")
                .arguments("{\"path\":\"src/App.vue\",\"content\":\"中文\\n页面\"}").build();
        List<ChatMessage> messages = List.of(SystemMessage.from("Generate a Vue app"),
                UserMessage.from("创建页面"), AiMessage.from(tool),
                ToolExecutionResultMessage.from(tool, "saved"), AiMessage.from("完成"));

        assertTrue(store.getMessages(42L).isEmpty());
        store.updateMessages(42L, messages);
        assertEquals(messages, store.getMessages(42L));
        verify(values).set(eq("app:chat:memory:42"), anyString(), eq(Duration.ofHours(1)));
        assertTrue(store.getMessages(43L).isEmpty());
        store.updateMessages(43L, List.of(UserMessage.from("other app")));
        MessageWindowChatMemory.builder().id(42L).maxMessages(50).chatMemoryStore(store).build().clear();
        assertTrue(store.getMessages(42L).isEmpty());
        assertEquals(List.of(UserMessage.from("other app")), store.getMessages(43L));
        verify(redis).delete("app:chat:memory:42");

        config.redisChatMemoryStore(redis, 0).updateMessages(44L, messages);
        verify(values).set(eq("app:chat:memory:44"), anyString());
        assertThrows(IllegalArgumentException.class, () -> store.getMessages(null));
        assertThrows(IllegalArgumentException.class, () -> store.getMessages(" "));
    }
}
