package com.lin.linaicodeapp.controller;

import com.lin.linaicodeapp.service.AppService;
import com.lin.linaicodeapp.service.ProjectDownloadService;
import com.lin.linaicodemother.config.CorsConfig;
import com.lin.linaicodemother.constant.AppConstant;
import com.lin.linaicodemother.constant.UserConstant;
import com.lin.linaicodemother.exception.ErrorCode;
import com.lin.linaicodemother.exception.GlobalExceptionHandler;
import com.lin.linaicodemother.model.entity.App;
import com.lin.linaicodemother.model.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(PreviewCorsTest.TestConfig.class)
@WebAppConfiguration
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class PreviewCorsTest {
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private AppService appService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private MockMvc mvc;
    private long appId;
    private Path project;
    private String previewPath;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        reset(appService, redisTemplate);
        appId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        String projectKey = "vue_project_" + appId;
        when(appService.getById(appId)).thenReturn(
                App.builder().id(appId).userId(7L).codeGenType("vue_project").build());
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("app:preview:valid-token")).thenReturn(appId + ":7");
        when(values.get("app:preview:other-app-token")).thenReturn("0:7");
        when(values.get("app:preview:other-owner-token")).thenReturn(appId + ":8");
        project = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, projectKey);
        Files.createDirectories(project.resolve("dist/assets"));
        Files.writeString(project.resolve("dist/index.html"), "<script type=\"module\" src=\"/assets/app.js\"></script>");
        Files.writeString(project.resolve("dist/assets/app.js"), "document.body.textContent = 'preview ready'");
        Files.writeString(project.resolve("dist/assets/app.css"), "body { color: green }");
        previewPath = "/api/static/" + projectKey + "/preview/valid-token/dist/";
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (project != null && Files.exists(project)) {
            try (var paths = Files.walk(project)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"index.html", "assets/app.js", "assets/app.css"})
    void shouldServeSandboxedPreviewWithTokenAndNoSession(String asset) throws Exception {
        mvc.perform(get(previewPath + asset).contextPath("/api").header("Origin", "null"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "null"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
                .andExpect(header().string("Content-Security-Policy", "sandbox allow-scripts"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired-token", "other-app-token", "other-owner-token"})
    void shouldStillRejectInvalidPreviewTokens(String token) throws Exception {
        mvc.perform(get(previewPath.replace("valid-token", token) + "assets/app.js")
                        .contextPath("/api").header("Origin", "null"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
    }

    @Test
    void shouldKeepNullOriginAwayFromSessionBasedEndpoints() throws Exception {
        User owner = new User();
        owner.setId(7L);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserConstant.USER_LOGIN_STATE, owner);
        mvc.perform(get("/api/app/preview-token/" + appId).contextPath("/api")
                        .session(session).header("Origin", "null"))
                .andExpect(status().isForbidden());
        mvc.perform(get(previewPath.replace("/preview/valid-token", "") + "assets/app.js")
                        .contextPath("/api").session(session).header("Origin", "null"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/app/preview-token/" + appId).contextPath("/api")
                        .session(session).header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void shouldRejectOtherOriginsAndWritePreflights() throws Exception {
        mvc.perform(get(previewPath + "assets/app.js").contextPath("/api")
                        .header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden());
        mvc.perform(options(previewPath + "assets/app.js").contextPath("/api")
                        .header("Origin", "null").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        mvc.perform(get(previewPath + "assets/app.js").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(content().string("document.body.textContent = 'preview ready'"));
        mvc.perform(get(previewPath + "assets/app.js").contextPath("/api")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Configuration
    @EnableWebMvc
    @Import({CorsConfig.class, StaticResourceController.class, AppController.class, GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean
        AppService appService() {
            return mock(AppService.class);
        }

        @Bean
        StringRedisTemplate redisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        ProjectDownloadService projectDownloadService() {
            return mock(ProjectDownloadService.class);
        }
    }
}
