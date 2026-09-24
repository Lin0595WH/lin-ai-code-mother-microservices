package com.lin.linaicodemother.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.stream.Stream;

/**
 * 全局跨域问题解决
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Sandboxed module requests use Origin: null. Keep this token-only path
        // ahead of the session-based API policy and do not allow credentials.
        registry.addMapping("/static/*/preview/*/**")
                .allowedOrigins(Stream.concat(allowedOrigins.stream(), Stream.of("null")).toArray(String[]::new))
                .allowedMethods("GET", "HEAD")
                .allowCredentials(false);

        if (allowedOrigins.isEmpty()) {
            return;
        }
        if (allowedOrigins.contains("*")) {
            throw new IllegalStateException("app.cors.allowed-origins must not contain '*'");
        }
        registry.addMapping("/**")
                .allowCredentials(true)
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*");
    }
}
