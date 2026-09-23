package com.lin.linaicodemother.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONObject;
import com.lin.linaicodemother.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 文件写入工具
 * 支持 AI 通过工具调用的方式写入文件
 */
@Slf4j
@Component
public class FileWriteTool extends BaseTool {

    @Tool("写入文件到指定路径")
    public String writeFile(@P("文件的相对路径") String relativeFilePath, @P("要写入文件的内容") String content,
                            @ToolMemoryId Long appId) {
        synchronized (projectLock(appId)) {
            try {
                Path path = resolveProjectPath(appId, relativeFilePath, false);
                Path parentDir = path.getParent();
                if (parentDir != null) {
                    Files.createDirectories(parentDir);
                    Path root = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + appId)
                            .toAbsolutePath().normalize();
                    Path current = root;
                    for (Path part : root.relativize(parentDir)) {
                        current = current.resolve(part);
                        restrictProjectDirectory(current);
                    }
                }
                Files.writeString(path, content,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                log.info("成功写入文件: {}", path.toAbsolutePath());
                return "文件写入成功: " + relativeFilePath;
            } catch (IOException e) {
                String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
                log.error(errorMessage, e);
                return errorMessage;
            }
        }
    }

    /**
     * 获取工具的英文名称（对应方法名）
     *
     * @return 工具英文名称
     */
    @Override
    public String getToolName() {
        return "writeFile";
    }

    /**
     * 获取工具的中文显示名称
     *
     * @return 工具中文名称
     */
    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    /**
     * 生成工具执行结果格式（保存到数据库）
     *
     * @param arguments 工具执行参数
     * @return 格式化的工具执行结果
     */
    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String suffix = FileUtil.getSuffix(relativeFilePath);
        String content = arguments.getStr("content");
        String resultTemplate = """
                [工具调用] {} {}
                ```{}
                {}
                ```
                """;
        return CharSequenceUtil.format(resultTemplate, getDisplayName(), relativeFilePath, suffix, content);
    }
}
