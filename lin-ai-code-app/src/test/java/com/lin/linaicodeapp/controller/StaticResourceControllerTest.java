package com.lin.linaicodeapp.controller;

import com.lin.linaicodemother.constant.UserConstant;
import com.lin.linaicodemother.constant.AppConstant;
import com.lin.linaicodemother.exception.BusinessException;
import com.lin.linaicodemother.model.entity.App;
import com.lin.linaicodemother.model.entity.User;
import com.lin.linaicodeapp.service.AppService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.HandlerMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaticResourceControllerTest {
    private AppService appService;
    private StaticResourceController controller;
    private User user;
    private String projectKey;

    @BeforeEach
    void setUp() {
        appService = mock(AppService.class);
        controller = new StaticResourceController(appService);
        user = new User();
        user.setId(7L);
        projectKey = Long.toString(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
    }

    @AfterEach
    void cleanUp() throws Exception {
        Path project = outputRoot().resolve(projectKey);
        if (Files.exists(project)) {
            try (var paths = Files.walk(project)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void requiresOwnerAndServesOnlyGeneratedArtifacts() throws Exception {
        String key = "html_" + projectKey;
        App app = App.builder().id(Long.parseLong(projectKey)).userId(7L).codeGenType("html").build();
        when(appService.getById(app.getId())).thenReturn(app);
        Path html = outputRoot().resolve(key + "/index.html");
        Files.createDirectories(html.getParent());
        Files.writeString(html, "<h1>ok</h1>");

        var response = controller.serveStaticResource(key, request("/static/" + key + "/"));
        assertEquals(200, response.getStatusCode().value());
        assertEquals("<h1>ok</h1>", response.getBody().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals(404, controller.serveStaticResource(key, request("/static/" + key + "/application.yml"))
                .getStatusCode().value());

        app.setUserId(8L);
        assertThrows(BusinessException.class,
                () -> controller.serveStaticResource(key, request("/static/" + key + "/")));
    }

    @Test
    void vuePreviewUsesDistAndRejectsSourceFiles() throws Exception {
        String key = "vue_project_" + projectKey;
        App app = App.builder().id(Long.parseLong(projectKey)).userId(7L)
                .codeGenType("vue_project").build();
        when(appService.getById(app.getId())).thenReturn(app);
        Path distIndex = outputRoot().resolve(key + "/dist/index.html");
        Files.createDirectories(distIndex.getParent());
        Files.writeString(distIndex, "<h1>built</h1>");
        Files.writeString(outputRoot().resolve(key + "/package.json"), "{}");

        var rootResponse = controller.serveStaticResource(key, request("/static/" + key + "/"));
        assertEquals(200, rootResponse.getStatusCode().value());
        var response = controller.serveStaticResource(key, request("/static/" + key + "/dist/index.html"));
        assertEquals(200, response.getStatusCode().value());
        assertEquals(404, controller.serveStaticResource(key,
                request("/static/" + key + "/package.json")).getStatusCode().value());
    }

    private HttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserConstant.USER_LOGIN_STATE, user);
        request.setSession(session);
        request.setRequestURI(path);
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, path);
        return request;
    }

    private Path outputRoot() {
        return Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR);
    }
}
