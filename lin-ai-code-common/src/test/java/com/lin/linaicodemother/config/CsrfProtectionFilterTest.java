package com.lin.linaicodemother.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsrfProtectionFilterTest {

    private final CsrfProtectionFilter filter = new CsrfProtectionFilter();

    @Test
    void shouldRejectCrossSitePost() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/app/add");
        request.addHeader("Host", "app.example.com");
        request.addHeader("Origin", "https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldAllowSameOriginPost() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/app/add");
        request.addHeader("Host", "app.example.com");
        request.addHeader("Origin", "https://app.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowNonBrowserPostWithoutOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/app/add");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }
}
