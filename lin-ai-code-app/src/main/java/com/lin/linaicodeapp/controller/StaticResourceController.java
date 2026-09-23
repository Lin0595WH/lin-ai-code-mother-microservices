package com.lin.linaicodeapp.controller;

import com.lin.linaicodemother.constant.AppConstant;
import com.lin.linaicodemother.exception.BusinessException;
import com.lin.linaicodemother.exception.ErrorCode;
import com.lin.linaicodemother.innerservice.InnerUserService;
import com.lin.linaicodemother.model.entity.App;
import com.lin.linaicodemother.model.entity.User;
import com.lin.linaicodemother.model.enums.CodeGenTypeEnum;
import com.lin.linaicodeapp.service.AppService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessException;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/static")
@RequiredArgsConstructor
public class StaticResourceController {
    private static final Pattern ROOT_RELATIVE_HTML_RESOURCE =
            Pattern.compile("((?:src|href)\\s*=\\s*['\"])\\/(?!/)([^'\"]*)(['\"])", Pattern.CASE_INSENSITIVE);

    private final AppService appService;
    private final StringRedisTemplate redisTemplate;

    @GetMapping("/{projectKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String projectKey,
            HttpServletRequest request) {
        try {
            int separator = projectKey.lastIndexOf('_');
            if (separator < 1) {
                return ResponseEntity.notFound().build();
            }
            String codeGenType = projectKey.substring(0, separator);
            long appId = Long.parseLong(projectKey.substring(separator + 1));
            CodeGenTypeEnum type = CodeGenTypeEnum.getEnumByValue(codeGenType);
            if (type == null) {
                return ResponseEntity.notFound().build();
            }
            App app = appService.getById(appId);
            if (app == null || !codeGenType.equals(app.getCodeGenType())
                    || !isPreviewTokenValid(request, appId, app.getUserId())
                    && !isSessionOwner(request, app)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }

            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            String prefix = "/static/" + projectKey;
            resourcePath = resourcePath.substring(prefix.length());
            if (resourcePath.startsWith("/preview/")) {
                int tokenEnd = resourcePath.indexOf('/', "/preview/".length());
                if (tokenEnd < 0) return ResponseEntity.notFound().build();
                resourcePath = resourcePath.substring(tokenEnd);
            }
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.LOCATION, request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }

            Path projectRoot = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, projectKey).toAbsolutePath().normalize();
            if (Files.isSymbolicLink(projectRoot)) {
                return ResponseEntity.notFound().build();
            }
            Path artifactRoot = type == CodeGenTypeEnum.VUE_PROJECT ? projectRoot.resolve("dist") : projectRoot;
            if (type == CodeGenTypeEnum.VUE_PROJECT) {
                if (resourcePath.equals("/index.html")) {
                    // Root Vue preview resolves to the built entry point.
                } else if (resourcePath.startsWith("/dist/")) {
                    resourcePath = resourcePath.substring("/dist".length());
                } else {
                    return ResponseEntity.notFound().build();
                }
            }
            Path requested = Path.of(resourcePath.substring(1));
            if (requested.isAbsolute() || requested.normalize().startsWith("..")) {
                return ResponseEntity.notFound().build();
            }
            if (type != CodeGenTypeEnum.VUE_PROJECT && !isFixedArtifact(type, requested)) {
                return ResponseEntity.notFound().build();
            }
            Path target = resolveArtifact(artifactRoot, requested);
            if (target == null || !Files.isRegularFile(target)) {
                return ResponseEntity.notFound().build();
            }

            Resource body = new FileSystemResource(target);
            if (type == CodeGenTypeEnum.VUE_PROJECT && target.getFileName().toString().equals("index.html")) {
                String html = rewritePreviewResourcePaths(Files.readString(target));
                body = new org.springframework.core.io.ByteArrayResource(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else if (type == CodeGenTypeEnum.VUE_PROJECT && target.toString().endsWith(".css")) {
                String css = rewritePreviewResourcePaths(Files.readString(target));
                body = new org.springframework.core.io.ByteArrayResource(css.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, getContentType(target))
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Referrer-Policy", "no-referrer").body(body);
        } catch (NumberFormatException | IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private boolean isSessionOwner(HttpServletRequest request, App app) {
        try {
            User user = InnerUserService.getLoginUser(request);
            return user.getId().equals(app.getUserId());
        } catch (BusinessException e) {
            return false;
        }
    }

    private boolean isPreviewTokenValid(HttpServletRequest request, long appId, long ownerId) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String marker = "/preview/";
        int tokenStart = path == null ? -1 : path.indexOf(marker);
        if (tokenStart < 0) return false;
        int tokenEnd = path.indexOf('/', tokenStart + marker.length());
        return tokenEnd > tokenStart && isPreviewTokenValid(
                path.substring(tokenStart + marker.length(), tokenEnd), appId, ownerId);
    }

    private boolean isPreviewTokenValid(String token, long appId, long ownerId) {
        if (token == null || token.isBlank()) return false;
        try {
            String value = redisTemplate.opsForValue().get("app:preview:" + token);
            return (appId + ":" + ownerId).equals(value);
        } catch (DataAccessException e) {
            return false;
        }
    }

    private String rewritePreviewResourcePaths(String content) {
        String relativeHtml = ROOT_RELATIVE_HTML_RESOURCE.matcher(content).replaceAll("$1./$2$3");
        return relativeHtml.replace("url(/", "url(./").replace("url('/", "url('./")
                .replace("url(\"/", "url(\"./");
    }

    private Path resolveArtifact(Path root, Path requested) throws IOException {
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            return null;
        }
        Path realRoot = root.toRealPath();
        Path current = realRoot;
        for (Path part : requested) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                return null;
            }
        }
        Path target = current.normalize();
        return target.startsWith(realRoot) ? target : null;
    }

    private boolean isFixedArtifact(CodeGenTypeEnum type, Path path) {
        if (path.getNameCount() != 1) {
            return false;
        }
        String name = path.toString();
        return name.equals("index.html") || (type == CodeGenTypeEnum.MULTI_FILE
                && (name.equals("style.css") || name.equals("script.js")));
    }

    private String getContentType(Path path) {
        String name = path.getFileName().toString();
        if (name.endsWith(".html")) return "text/html; charset=UTF-8";
        if (name.endsWith(".css")) return "text/css; charset=UTF-8";
        if (name.endsWith(".js")) return "application/javascript; charset=UTF-8";
        return "application/octet-stream";
    }
}
