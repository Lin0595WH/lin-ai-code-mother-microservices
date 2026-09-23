package com.lin.linaicodeapp.core.builder;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @Author Lin
 * @Date 2026/3/22 16:19
 * @Descriptions 构建 Vue 项目
 */
@Slf4j
@Component
public class VueProjectBuilder {

    /**
     * 异步构建 Vue 项目
     *
     * @param projectPath
     */
    public void buildProjectAsync(String projectPath) {
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis())
                .start(() -> {
                    try {
                        buildProject(projectPath);
                    } catch (Exception e) {
                        log.error("异步构建 Vue 项目时发生异常: {}", e.getMessage(), e);
                    }
                });
    }

    /**
     * 构建 Vue 项目
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return false;
        }
        File projectDir = new File(projectPath);
        if (!projectDir.isDirectory() || !new File(projectDir, "package.json").isFile()) {
            log.error("Vue 项目目录或 package.json 不存在：{}", projectPath);
            return false;
        }
        String npm = isWindows() ? "npm.cmd" : "npm";
        return execute(projectDir, List.of(npm, "install"), 300)
                && execute(projectDir, List.of(npm, "run", "build"), 180)
                && new File(projectDir, "dist").isDirectory();
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
            if (process != null) {
                terminate(process);
            }
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
