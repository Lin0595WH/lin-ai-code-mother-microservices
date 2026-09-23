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

@RestController
@RequestMapping("/static")
@RequiredArgsConstructor
public class StaticResourceController {
    private final AppService appService;

    @GetMapping("/{projectKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String projectKey,
            HttpServletRequest request) {
        User loginUser = InnerUserService.getLoginUser(request);
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
            if (app == null || !loginUser.getId().equals(app.getUserId())
                    || !codeGenType.equals(app.getCodeGenType())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }

            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            String prefix = "/static/" + projectKey;
            resourcePath = resourcePath.substring(prefix.length());
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

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, getContentType(target))
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new FileSystemResource(target));
        } catch (NumberFormatException | IOException e) {
            return ResponseEntity.notFound().build();
        }
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
