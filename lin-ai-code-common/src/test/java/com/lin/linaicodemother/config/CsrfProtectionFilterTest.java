package com.lin.linaicodemother.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CsrfProtectionFilterTest {

    private final CsrfProtectionFilter filter = new CsrfProtectionFilter(List.of("http://localhost:5173"));

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

    @ParameterizedTest
    @ValueSource(strings = {"/api/user/logout", "/api/app/good/list/page/vo"})
    void shouldAllowConfiguredFrontendOriginThroughGateway(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader("Host", "localhost:8080");
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("Sec-Fetch-Site", "same-site");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5174", "https://localhost:5173",
            "http://localhost:5173.attacker.example", "null", "not an origin"})
    void shouldRejectOriginsOutsideExactAllowlist(String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/user/logout");
        request.addHeader("Host", "localhost:8080");
        request.addHeader("Origin", origin);
        request.addHeader("Sec-Fetch-Site", "same-site");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void shouldStillRejectExplicitCrossSiteRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/user/logout");
        request.addHeader("Host", "localhost:8080");
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("Sec-Fetch-Site", "cross-site");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldNotTrustForwardedHostWithoutConfiguredOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/user/logout");
        request.addHeader("Host", "localhost:8080");
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("X-Forwarded-Host", "localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CsrfProtectionFilter(List.of()).doFilterInternal(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }
}
