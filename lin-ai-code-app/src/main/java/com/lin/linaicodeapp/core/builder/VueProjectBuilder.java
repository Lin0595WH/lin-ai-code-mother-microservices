package com.lin.linaicodeapp.core.builder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Builds generated Vue projects locally in development or in the isolated production worker. */
@Slf4j
@Component
public class VueProjectBuilder {
    private static final long MAX_ARCHIVE_BYTES = 100L * 1024 * 1024;
    private static final int MAX_ENTRIES = 5000;
    private final String builderUrl;
    private final Environment environment;

    public VueProjectBuilder(@Value("${VUE_BUILDER_URL:}") String builderUrl, Environment environment) {
        this.builderUrl = builderUrl;
        this.environment = environment;
    }

    public void buildProjectAsync(String projectPath) {
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis())
                .start(() -> buildProject(projectPath));
    }

    public synchronized boolean buildProject(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return false;
        }
        Path project = Path.of(projectPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(project, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(project.resolve("package.json"), LinkOption.NOFOLLOW_LINKS)) {
            log.error("Vue 项目目录或 package.json 不存在：{}", projectPath);
            return false;
        }
        if (!builderUrl.isBlank()) {
            return buildInWorker(project);
        }
        if (!environment.acceptsProfiles(Profiles.of("local", "dev"))
                || environment.acceptsProfiles(Profiles.of("prod"))) {
            log.error("仅 local/dev 环境允许本地构建 Vue 项目，其他环境须配置 VUE_BUILDER_URL");
            return false;
        }
        String npm = isWindows() ? "npm.cmd" : "npm";
        File projectDir = project.toFile();
        return execute(projectDir, List.of(npm, "install"), 300)
                && execute(projectDir, List.of(npm, "run", "build"), 180)
                && safeDist(project.resolve("dist"));
    }

    private boolean buildInWorker(Path project) {
        Path source = null;
        Path output = null;
        try {
            source = Files.createTempFile("vue-source-", ".zip");
            output = Files.createTempFile("vue-dist-", ".zip");
            archiveSource(project, source);
            HttpRequest request = HttpRequest.newBuilder(URI.create(builderUrl + "/build"))
                    .timeout(Duration.ofMinutes(11))
                    .header("Content-Type", "application/zip")
                    .POST(HttpRequest.BodyPublishers.ofFile(source)).build();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            for (int attempt = 0; attempt < 5; attempt++) {
                HttpResponse<Path> response;
                try {
                    response = client.send(request, HttpResponse.BodyHandlers.ofFile(output,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE));
                } catch (IOException e) {
                    if (attempt == 4) throw e;
                    Thread.sleep(1000);
                    continue;
                }
                if (response.statusCode() == 200) {
                    if (Files.size(output) > MAX_ARCHIVE_BYTES) {
                        throw new IOException("Vue 构建产物超过大小限制");
                    }
                    installDist(project, output);
                    return true;
                }
                if (response.statusCode() != 503) {
                    log.error("Vue 构建容器返回状态码 {}", response.statusCode());
                    return false;
                }
                Thread.sleep(1000);
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Vue 构建被中断", e);
            return false;
        } catch (IOException | IllegalArgumentException e) {
            log.error("Vue 构建失败: {}", e.getMessage());
            return false;
        } finally {
            if (source != null) source.toFile().delete();
            if (output != null) output.toFile().delete();
        }
    }

    private void archiveSource(Path project, Path archive) throws IOException {
        long[] size = {0};
        int[] entries = {0};
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            Files.walkFileTree(project, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(project)) {
                        String name = dir.getFileName().toString();
                        if (name.equals("node_modules") || name.equals(".git")
                                || dir.equals(project.resolve("dist"))) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!attrs.isRegularFile() || attrs.size() > MAX_ARCHIVE_BYTES - size[0]
                            || ++entries[0] > MAX_ENTRIES) {
                        throw new IOException("Vue 项目包含链接、特殊文件或过大的文件");
                    }
                    size[0] += attrs.size();
                    String name = project.relativize(file).toString().replace(File.separatorChar, '/');
                    if (name.indexOf('\\') >= 0) throw new IOException("Vue 项目文件名无效");
                    zip.putNextEntry(new ZipEntry(name));
                    try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                        input.transferTo(zip);
                    }
                    zip.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        if (Files.size(archive) > MAX_ARCHIVE_BYTES) throw new IOException("Vue 项目超过大小限制");
    }

    private void installDist(Path project, Path archive) throws IOException {
        Path stage = Files.createTempDirectory(project.getParent(), ".dist-build-");
        Path dist = project.resolve("dist");
        Path backup = project.getParent().resolve(".dist-old-" + UUID.randomUUID());
        try {
            long total = 0;
            int count = 0;
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zip.getNextEntry()) != null) {
                    if (++count > MAX_ENTRIES) throw new IOException("Vue 构建产物文件过多");
                    String name = entry.getName();
                    if (name.equals("./")) {
                        zip.closeEntry();
                        continue;
                    }
                    if (name.startsWith("./")) name = name.substring(2);
                    Path relative;
                    try {
                        relative = Path.of(name);
                    } catch (InvalidPathException e) {
                        throw new IOException("Vue 构建产物路径无效", e);
                    }
                    if (relative.isAbsolute() || name.indexOf('\\') >= 0
                            || !relative.normalize().equals(relative) || relative.startsWith("..")) {
                        throw new IOException("Vue 构建产物路径无效");
                    }
                    Path target = stage.resolve(relative);
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        try (OutputStream out = Files.newOutputStream(target,
                                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                            int read;
                            while ((read = zip.read(buffer)) != -1) {
                                total += read;
                                if (total > MAX_ARCHIVE_BYTES) throw new IOException("Vue 构建产物超过大小限制");
                                out.write(buffer, 0, read);
                            }
                        }
                    }
                    zip.closeEntry();
                }
            }
            if (!Files.isRegularFile(stage.resolve("index.html"), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Vue 构建产物缺少 index.html");
            }
            boolean hadOldDist = Files.exists(dist, LinkOption.NOFOLLOW_LINKS);
            if (hadOldDist) Files.move(dist, backup);
            try {
                Files.move(stage, dist);
            } catch (IOException e) {
                if (hadOldDist) Files.move(backup, dist);
                throw e;
            }
            if (hadOldDist) {
                try {
                    deleteTree(backup);
                } catch (IOException e) {
                    log.warn("旧 Vue 构建目录清理失败: {}", e.getMessage());
                }
            }
        } finally {
            deleteTree(stage);
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean safeDist(Path dist) {
        if (!Files.isDirectory(dist, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(dist.resolve("index.html"), LinkOption.NOFOLLOW_LINKS)) return false;
        try (var entries = Files.walk(dist)) {
            return entries.noneMatch(Files::isSymbolicLink);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean execute(File workingDir, List<String> command, long timeoutSeconds) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .directory(workingDir)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                terminate(process);
                log.error("命令执行超时：{}", command);
                return false;
            }
            if (process.exitValue() != 0) {
                log.error("命令执行失败，退出码 {}：{}", process.exitValue(), command);
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            if (process != null) terminate(process);
            Thread.currentThread().interrupt();
            log.error("执行命令被中断：{}", command, e);
            return false;
        } catch (IOException e) {
            log.error("执行命令失败：{}", command, e);
            return false;
        }
    }

    private void terminate(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }
}
