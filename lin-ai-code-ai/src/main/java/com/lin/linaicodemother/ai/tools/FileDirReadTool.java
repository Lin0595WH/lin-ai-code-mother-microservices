package com.lin.linaicodemother.ai.tools;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 文件目录读取工具
 * 使用 Hutool 简化文件操作
 */
@Slf4j
@Component
public class FileDirReadTool extends BaseTool {

    /**
     * 需要忽略的文件和目录
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules", ".git", "dist", "build", ".DS_Store",
            ".env", "target", ".mvn", ".idea", ".vscode", "coverage"
    );

    /**
     * 需要忽略的文件扩展名
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log", ".tmp", ".cache", ".lock"
    );

    @Tool("读取目录结构，获取指定目录下的所有文件和子目录信息")
    public String readDir(@P("目录的相对路径，为空则读取整个项目结构") String relativeDirPath, @ToolMemoryId Long appId) {
        synchronized (projectLock(appId)) {
            try {
                Path path = relativeDirPath == null || relativeDirPath.isBlank()
                        ? resolveProjectPath(appId, ".", true)
                        : resolveProjectPath(appId, relativeDirPath, true);
                File targetDir = path.toFile();
                if (!targetDir.exists() || !targetDir.isDirectory()) {
                    return "错误：目录不存在或不是目录 - " + relativeDirPath;
                }
                StringBuilder structure = new StringBuilder("项目目录结构:\n");
                List<File> allFiles;
                try (Stream<Path> paths = Files.walk(path)) {
                    List<Path> entries = paths.toList();
                    if (entries.stream().anyMatch(Files::isSymbolicLink)) {
                        throw new IOException("项目路径不能包含符号链接");
                    }
                    allFiles = entries.stream()
                            .filter(Files::isRegularFile)
                            .filter(file -> !shouldIgnore(file.getFileName().toString()))
                            .map(Path::toFile)
                            .toList();
                }
                allFiles.stream()
                        .sorted((f1, f2) -> {
                            int depth1 = getRelativeDepth(targetDir, f1);
                            int depth2 = getRelativeDepth(targetDir, f2);
                            return depth1 == depth2 ? f1.getPath().compareTo(f2.getPath()) : Integer.compare(depth1, depth2);
                        })
                        .forEach(file -> {
                            int depth = getRelativeDepth(targetDir, file);
                            structure.append("  ".repeat(depth)).append(file.getName()).append('\n');
                        });
                return structure.toString();
            } catch (Exception e) {
                String errorMessage = "读取目录结构失败: " + relativeDirPath + ", 错误: " + e.getMessage();
                log.error(errorMessage, e);
                return errorMessage;
            }
        }
    }

    /**
     * 计算文件相对于根目录的深度
     */
    private int getRelativeDepth(File root, File file) {
        Path rootPath = root.toPath();
        Path filePath = file.toPath();
        return rootPath.relativize(filePath).getNameCount() - 1;
    }

    /**
     * 判断是否应该忽略该文件或目录
     */
    private boolean shouldIgnore(String fileName) {
        // 检查是否在忽略名称列表中
        if (IGNORED_NAMES.contains(fileName)) {
            return true;
        }

        // 检查文件扩展名
        return IGNORED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    /**
     * 获取工具的英文名称（对应方法名）
     *
     * @return 工具英文名称
     */
    @Override
    public String getToolName() {
        return "readDir";
    }

    /**
     * 获取工具的中文显示名称
     *
     * @return 工具中文名称
     */
    @Override
    public String getDisplayName() {
        return "读取目录";
    }

    /**
     * 生成工具执行结果格式（保存到数据库）
     *
     * @param arguments 工具执行参数
     * @return 格式化的工具执行结果
     */
    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeDirPath = arguments.getStr("relativeDirPath");
        if (CharSequenceUtil.isEmpty(relativeDirPath)) {
            relativeDirPath = "根目录";
        }
        return String.format("[工具调用] %s %s", getDisplayName(), relativeDirPath);
    }
}
