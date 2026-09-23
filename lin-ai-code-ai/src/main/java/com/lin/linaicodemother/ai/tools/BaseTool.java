package com.lin.linaicodemother.ai.tools;

import cn.hutool.json.JSONObject;
import com.lin.linaicodemother.constant.AppConstant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Set;

/**
 * @Author Lin
 * @Date 2026/4/16 21:02
 * @Descriptions 工具基类
 * 定义所有工具的通用接口
 */
public abstract class BaseTool {
    private static final Object[] PROJECT_LOCKS = new Object[64];

    static {
        Arrays.setAll(PROJECT_LOCKS, ignored -> new Object());
    }

    protected Object projectLock(Long appId) {
        // ponytail: JVM-local striped locks; use filesystem-level locking if project files become shared across hosts.
        return PROJECT_LOCKS[Math.floorMod(appId == null ? 0 : Long.hashCode(appId), PROJECT_LOCKS.length)];
    }

    protected void restrictProjectDirectory(Path directory) throws IOException {
        if (Files.getFileAttributeView(directory, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(directory, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        }
    }

    protected Path resolveProjectPath(Long appId, String relativePath, boolean allowRoot) throws IOException {
        if (appId == null || relativePath == null) {
            throw new IOException("无效的项目路径");
        }
        Path suppliedPath;
        try {
            suppliedPath = Path.of(relativePath);
        } catch (InvalidPathException e) {
            throw new IOException("无效的项目路径", e);
        }
        if (suppliedPath.isAbsolute() || relativePath.isBlank() || relativePath.indexOf('\\') >= 0) {
            throw new IOException("只允许项目内相对路径");
        }

        Path root = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + appId)
                .toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) {
            throw new IOException("项目目录不能是符号链接");
        }
        // The service account owns this tree; deployment must not run untrusted processes as that account.
        restrictProjectDirectory(root);
        root = root.toRealPath();
        Path target = root.resolve(suppliedPath).normalize();
        if (!target.startsWith(root) || (!allowRoot && target.equals(root))) {
            throw new IOException("路径超出项目目录");
        }
        Path current = root;
        for (Path part : root.relativize(target)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("项目路径不能包含符号链接");
            }
        }
        return target;
    }

    /**
     * 获取工具的英文名称（对应方法名）
     *
     * @return 工具英文名称
     */
    public abstract String getToolName();

    /**
     * 获取工具的中文显示名称
     *
     * @return 工具中文名称
     */
    public abstract String getDisplayName();

    /**
     * 生成工具请求时的返回值（显示给用户）
     *
     * @return 工具请求显示内容
     */
    public String generateToolRequestResponse() {
        return String.format("[选择工具] %s", getDisplayName());
    }

    /**
     * 生成工具执行结果格式（保存到数据库）
     *
     * @param arguments 工具执行参数
     * @return 格式化的工具执行结果
     */
    public abstract String generateToolExecutedResult(JSONObject arguments);
}
