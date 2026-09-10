package com.lin.linaicodeapp.core.saver;


import cn.hutool.core.text.CharSequenceUtil;
import com.lin.linaicodemother.ai.model.HtmlCodeResult;
import com.lin.linaicodemother.exception.BusinessException;
import com.lin.linaicodemother.exception.ErrorCode;
import com.lin.linaicodemother.model.enums.CodeGenTypeEnum;

/**
 * @Author Lin
 * @Date 2026/1/12 21:19
 * @Descriptions HTML代码文件保存器
 */
public class HtmlCodeFileSaverTemplate extends CodeFileSaverTemplate<HtmlCodeResult> {

    @Override
    protected void saveFiles(HtmlCodeResult result, String baseDirPath) {
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
    }

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.HTML;
    }

    @Override
    protected void validateInput(HtmlCodeResult result, Long appId) {
        super.validateInput(result, appId);
        // HTML 代码不能为空
        if (CharSequenceUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML 代码不能为空");
        }
    }
}
