package com.lin.linaicodeapp.core.saver;


import cn.hutool.core.text.CharSequenceUtil;
import com.lin.linaicodemother.ai.model.MultiFileCodeResult;
import com.lin.linaicodemother.exception.BusinessException;
import com.lin.linaicodemother.exception.ErrorCode;
import com.lin.linaicodemother.model.enums.CodeGenTypeEnum;

/**
 * @Author Lin
 * @Date 2026/1/12 21:21
 * @Descriptions 多文件代码保存器
 */
public class MultiFileCodeFileSaverTemplate extends CodeFileSaverTemplate<MultiFileCodeResult> {
    /**
     * 保存文件（具体实现交给子类）
     *
     * @param result      代码结果对象
     * @param baseDirPath 基础目录路径
     */
    @Override
    protected void saveFiles(MultiFileCodeResult result, String baseDirPath) {
        // 保存 HTML 文件
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        // 保存 CSS 文件
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        // 保存 JavaScript 文件
        writeToFile(baseDirPath, "script.js", result.getJsCode());
    }

    /**
     * 获取代码生成类型
     *
     * @return 代码生成类型枚举
     */
    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    /**
     * 验证输入参数（可由子类覆盖）
     *
     * @param result 代码结果对象
     */
    @Override
    protected void validateInput(MultiFileCodeResult result, Long appId) {
        super.validateInput(result, appId);
        // 至少要有 HTML 代码，CSS 和 JS 可以为空
        if (CharSequenceUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码内容不能为空");
        }
    }
}
