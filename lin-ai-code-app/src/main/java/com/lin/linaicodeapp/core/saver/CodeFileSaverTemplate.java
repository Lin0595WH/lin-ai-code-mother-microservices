package com.lin.linaicodeapp.core.saver;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.lin.linaicodemother.constant.AppConstant;
import com.lin.linaicodemother.exception.ErrorCode;
import com.lin.linaicodemother.exception.ThrowUtils;
import com.lin.linaicodemother.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * @Author Lin
 * @Date 2026/1/12 21:06
 * @Descriptions 抽象代码文件保存器
 * 模板方法模式 代替原先的CodeFileSaver类
 */
public abstract class CodeFileSaverTemplate<T> {
    /**
     * 文件保存的根目录
     */
    private static final String FILE_SAVE_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 模板方法：保存代码的标准流程
     *
     * @param result 代码结果对象
     * @param appId  应用ID
     * @return 保存的目录
     */
    public final File saveCode(T result, Long appId) {
        // 1. 验证输入
        validateInput(result, appId);
        // 2. 构建唯一目录
        String baseDirPath = buildUniqueDir(appId);
        // 3. 保存文件（具体实现交给子类）
        saveFiles(result, baseDirPath);
        // 4. 返回文件目录对象
        return new File(baseDirPath);
    }

    /**
     * 验证输入参数（可由子类覆盖）
     *
     * @param result 代码结果对象
     * @param appId  应用ID
     */
    protected void validateInput(T result, Long appId) {
        ThrowUtils.throwIf(result == null, ErrorCode.SYSTEM_ERROR, "代码结果对象不能为空");
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.SYSTEM_ERROR, "应用ID不能为空");
    }

    /**
     * 保存单个文件
     *
     * @param dirPath  保存的目录
     * @param filename 文件名
     * @param content  文件内容
     */
    public final void writeToFile(String dirPath, String filename, String content) {
        if (CharSequenceUtil.isNotBlank(content)) {
            String filePath = dirPath + File.separator + filename;
            FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
        }
    }

    /**
     * 构建文件的唯一路径：tmp/code_output/bizType_应用ID
     *
     * @param appId 应用ID
     * @return 目录路径
     */
    protected String buildUniqueDir(Long appId) {
        String codeType = getCodeType().getValue();
        String uniqueDirName = CharSequenceUtil.format("{}_{}", codeType, appId);
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 保存文件（具体实现交给子类）
     *
     * @param result      代码结果对象
     * @param baseDirPath 基础目录路径
     */
    protected abstract void saveFiles(T result, String baseDirPath);

    /**
     * 获取代码生成类型
     *
     * @return 代码生成类型枚举
     */
    protected abstract CodeGenTypeEnum getCodeType();
}
