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
    private static final String VISUAL_EDITOR_SCRIPT = """
            <script id="visual-edit-script">
            (() => {
              let editing = false;
              let hovered = null;
              let selected = null;

              const clearHover = () => {
                hovered?.classList.remove('edit-hover');
                hovered = null;
              };
              const clearSelection = () => {
                selected?.classList.remove('edit-selected');
                selected = null;
              };
              const selectorFor = (element) => {
                const path = [];
                for (let current = element; current && current !== document.body; current = current.parentElement) {
                  let selector = current.tagName.toLowerCase();
                  if (current.id) {
                    path.unshift(selector + '#' + CSS.escape(current.id));
                    break;
                  }
                  const classes = typeof current.className === 'string'
                    ? current.className.split(/\\s+/).filter((name) => name && !name.startsWith('edit-'))
                    : [];
                  if (classes.length) selector += '.' + classes.map(CSS.escape).join('.');
                  const siblings = [...(current.parentElement?.children || [])];
                  selector += ':nth-child(' + (siblings.indexOf(current) + 1) + ')';
                  path.unshift(selector);
                }
                return path.join(' > ');
              };
              const selectable = (target) => target instanceof Element
                && target !== document.body
                && target !== document.documentElement
                && !['SCRIPT', 'STYLE'].includes(target.tagName);

              const style = document.createElement('style');
              style.textContent = `
                .edit-hover { outline: 2px dashed #1890ff !important; outline-offset: 2px !important; cursor: crosshair !important; }
                .edit-selected { outline: 3px solid #52c41a !important; outline-offset: 2px !important; }
              `;
              document.head.appendChild(style);

              document.addEventListener('mouseover', (event) => {
                if (!editing || !selectable(event.target) || event.target === selected) return;
                clearHover();
                hovered = event.target;
                hovered.classList.add('edit-hover');
              }, true);
              document.addEventListener('mouseout', () => editing && clearHover(), true);
              document.addEventListener('click', (event) => {
                if (!editing || !selectable(event.target)) return;
                event.preventDefault();
                event.stopPropagation();
                clearHover();
                clearSelection();
                selected = event.target;
                selected.classList.add('edit-selected');
                const rect = selected.getBoundingClientRect();
                window.parent.postMessage({
                  type: 'ELEMENT_SELECTED',
                  data: { elementInfo: {
                    tagName: selected.tagName,
                    id: selected.id || '',
                    className: typeof selected.className === 'string' ? selected.className : '',
                    textContent: (selected.textContent || '').trim().substring(0, 100),
                    selector: selectorFor(selected),
                    pagePath: location.search + location.hash,
                    rect: { top: rect.top, left: rect.left, width: rect.width, height: rect.height }
                  }}
                }, '*');
              }, true);
              window.addEventListener('message', (event) => {
                if (event.source !== window.parent) return;
                if (event.data?.type === 'TOGGLE_EDIT_MODE') {
                  editing = Boolean(event.data.editMode);
                  if (!editing) {
                    clearHover();
                    clearSelection();
                  }
                } else if (event.data?.type === 'CLEAR_SELECTION') {
                  clearSelection();
                } else if (event.data?.type === 'CLEAR_ALL_EFFECTS') {
                  editing = false;
                  clearHover();
                  clearSelection();
                }
              });
            })();
            </script>
            """;

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
            boolean vueRootEntry = type == CodeGenTypeEnum.VUE_PROJECT
                    && (resourcePath.equals("/") || resourcePath.equals("/index.html"));
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
            if (target.getFileName().toString().equals("index.html")) {
                String html = Files.readString(target);
                if (type == CodeGenTypeEnum.VUE_PROJECT) {
                    html = rewriteVueHtmlResources(html, vueRootEntry);
                }
                html = injectVisualEditor(html);
                body = new org.springframework.core.io.ByteArrayResource(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else if (type == CodeGenTypeEnum.VUE_PROJECT && target.toString().endsWith(".css")) {
                String css = rewriteVueCssResources(Files.readString(target));
                body = new org.springframework.core.io.ByteArrayResource(css.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, getContentType(target))
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "sandbox allow-scripts")
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

    private String rewriteVueHtmlResources(String html, boolean rootEntry) {
        String prefix = rootEntry ? "./dist/" : "./";
        return ROOT_RELATIVE_HTML_RESOURCE.matcher(html).replaceAll("$1" + prefix + "$2$3");
    }

    private String rewriteVueCssResources(String css) {
        return css.replace("url(/assets/", "url(./").replace("url('/assets/", "url('./")
                .replace("url(\"/assets/", "url(\"./").replace("url(/", "url(../")
                .replace("url('/", "url('../").replace("url(\"/", "url(\"../");
    }

    private String injectVisualEditor(String html) {
        int bodyEnd = html.length() - 7;
        while (bodyEnd >= 0 && !html.regionMatches(true, bodyEnd, "</body>", 0, 7)) {
            bodyEnd--;
        }
        return bodyEnd < 0
                ? html + VISUAL_EDITOR_SCRIPT
                : html.substring(0, bodyEnd) + VISUAL_EDITOR_SCRIPT + html.substring(bodyEnd);
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
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".ico")) return "image/x-icon";
        if (name.endsWith(".woff2")) return "font/woff2";
        if (name.endsWith(".woff")) return "font/woff";
        if (name.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }
}
