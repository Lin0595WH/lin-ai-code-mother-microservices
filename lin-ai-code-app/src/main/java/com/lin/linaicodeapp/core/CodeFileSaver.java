package com.lin.linaicodeapp.core;


import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import com.lin.linaicodemother.ai.model.HtmlCodeResult;
import com.lin.linaicodemother.ai.model.MultiFileCodeResult;
import com.lin.linaicodemother.constant.AppConstant;
import com.lin.linaicodemother.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * @Author Lin
 * @Date 2026/1/8 21:05
 * @Descriptions 文件保存器 v1.0
 * 后续使用模板方法 CodeFileSaverTemplate 代替
 */
@Deprecated
public class CodeFileSaver {
    /**
     * 文件保存的根目录
     */
    private static final String FILE_SAVE_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 保存 HTML 网页代码
     *
     * @param htmlCodeResult HTML 网页代码结果
     * @return
     */
    public static File saveHtmlCodeResult(HtmlCodeResult htmlCodeResult) {
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.HTML.getValue());
        writeToFile(baseDirPath, "index.html", htmlCodeResult.getHtmlCode());
        return new File(baseDirPath);
    }

    /**
     * 保存多文件网页代码
     *
     * @param result 多文件网页代码结果
     * @return
     */
    public static File saveMultiFileCodeResult(MultiFileCodeResult result) {
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.MULTI_FILE.getValue());
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        writeToFile(baseDirPath, "script.js", result.getJsCode());
        return new File(baseDirPath);
    }

    /**
     * 构建文件的唯一路径：tmp/code_output/bizType_时间戳_雪花 ID
     *
     * @param bizType 代码生成类型（单页面还是多文件）
     * @return
     */
    private static String buildUniqueDir(String bizType) {
        String uniqueDirName = CharSequenceUtil.format("{}_{}_{}",
                bizType, DateUtil.format(DateUtil.date(), "yyyyMMddHHmmss"), IdUtil.getSnowflakeNextIdStr());
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 保存单个文件
     *
     * @param dirPath  保存的目录
     * @param filename 文件名
     * @param content  文件内容
     */
    private static void writeToFile(String dirPath, String filename, String content) {
        String filePath = dirPath + File.separator + filename;
        FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
    }
}
