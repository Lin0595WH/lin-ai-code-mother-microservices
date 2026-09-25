package com.lin.linaicodemother.exception;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    @Test
    void shouldWriteSseFailureWhenWriterWasAlreadyStarted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/event-stream");
        response.getWriter().write("data: started\n\n");
        response.getWriter().flush();
        response.setOutputStreamAccessAllowed(false);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        try {
            assertNull(new GlobalExceptionHandler().businessExceptionHandler(
                    new BusinessException(ErrorCode.OPERATION_ERROR, "构建失败")));
            assertTrue(response.getContentAsString().contains("event: business-error\n"));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldWriteSseFailureAfterOutputStreamStartedWithoutSendingDone(boolean businessError) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        if (businessError) request.addHeader("Accept", "text/event-stream");
        response.setContentType("text/event-stream");
        String priorEvent = "data: {\"d\":\"started\"}\n\n";
        response.getOutputStream().write(priorEvent.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
        response.setWriterAccessAllowed(false);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        try {
            GlobalExceptionHandler handler = new GlobalExceptionHandler();
            assertNull(businessError
                    ? handler.businessExceptionHandler(new BusinessException(ErrorCode.OPERATION_ERROR, "构建失败"))
                    : handler.runtimeExceptionHandler(new IllegalStateException("internal detail")));
            String result = response.getContentAsString(StandardCharsets.UTF_8);
            String prefix = priorEvent + "event: business-error\ndata: ";
            assertTrue(result.startsWith(prefix));
            assertTrue(result.endsWith("\n\n"));
            assertFalse(result.contains("event: done"));
            var error = JSONUtil.parseObj(result.substring(prefix.length()).trim());
            assertTrue(error.getBool("error"));
            assertEquals(businessError ? 50001 : 50000, error.getInt("code"));
            assertEquals(businessError ? "构建失败" : "系统错误", error.getStr("message"));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
