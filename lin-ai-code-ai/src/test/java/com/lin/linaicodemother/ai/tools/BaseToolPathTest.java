package com.lin.linaicodemother.ai.tools;

import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseToolPathTest {

    @TempDir
    Path tempDir;

    private String originalUserDir;

    @BeforeEach
    void setProjectRoot() {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
    }

    @AfterEach
    void restoreProjectRoot() {
        System.setProperty("user.dir", originalUserDir);
    }

    @Test
    void allowsProjectFilesAndRejectsEscapesAndSymlinks() throws IOException {
        TestTool tool = new TestTool();
        Path file = tool.resolve(1L, "src/App.vue", false);
        assertEquals(tempDir.toRealPath().resolve("tmp/code_output/vue_project_1/src/App.vue"), file);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "<template />");
        assertThrows(IOException.class, () -> tool.resolve(1L, "../outside", false));
        assertThrows(IOException.class, () -> tool.resolve(1L, file.toString(), false));
        assertThrows(IOException.class, () -> tool.resolve(1L, "..\\outside", false));
        String directory = new FileDirReadTool().readDir(".", 1L);
        assertTrue(directory.startsWith("项目目录结构:"));
        assertTrue(directory.contains("App.vue"));

        Path dependencies = Files.createDirectories(file.getParent().getParent().resolve("node_modules/pkg"));
        Files.writeString(dependencies.resolve("private.txt"), "ignored");
        directory = new FileDirReadTool().readDir(".", 1L);
        assertFalse(directory.contains("private.txt"));

        Path root = tempDir.resolve("tmp/code_output/vue_project_1");
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.createSymbolicLink(root.resolve("link"), outside);
        assertThrows(IOException.class, () -> tool.resolve(1L, "link/file.txt", false));
        Files.writeString(outside.resolve("external.txt"), "outside");
        Files.createSymbolicLink(root.resolve("external.txt"), outside.resolve("external.txt"));

        directory = new FileDirReadTool().readDir(".", 1L);
        assertFalse(directory.contains("external.txt"));
        assertTrue(directory.contains("符号链接"));
    }

    private static final class TestTool extends BaseTool {
        Path resolve(Long appId, String path, boolean allowRoot) throws IOException {
            return resolveProjectPath(appId, path, allowRoot);
        }

        @Override
        public String getToolName() {
            return "test";
        }

        @Override
        public String getDisplayName() {
            return "test";
        }

        @Override
        public String generateToolExecutedResult(JSONObject arguments) {
            return "";
        }
    }
}
