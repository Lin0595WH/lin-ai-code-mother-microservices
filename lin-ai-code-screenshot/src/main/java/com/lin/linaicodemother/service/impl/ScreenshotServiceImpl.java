package com.lin.linaicodemother.service.impl;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.RandomUtil;
import com.lin.linaicodemother.exception.ErrorCode;
import com.lin.linaicodemother.exception.ThrowUtils;
import com.lin.linaicodemother.manager.CosManager;
import com.lin.linaicodemother.service.ScreenshotService;
import com.lin.linaicodemother.utils.WebScreenshotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * @Author Lin
 * @Date 2026/3/24 22:31
 * @Descriptions 截图服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenshotServiceImpl implements ScreenshotService {

    private final CosManager cosManager;

    /**
     * 通用的截图服务，可以得到访问地址
     *
     * @param webUrl 网址
     * @return 访问地址
     */
    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        // 参数校验
        ThrowUtils.throwIf(CharSequenceUtil.isBlank(webUrl), ErrorCode.PARAMS_ERROR, "截图的网址不能为空");
        // 本地截图(压缩后的）
        String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
        ThrowUtils.throwIf(CharSequenceUtil.isBlank(localScreenshotPath), ErrorCode.OPERATION_ERROR, "生成网页截图失败");
        try {
            // 上传图片到 COS
            String cosUrl = uploadScreenshotToCos(localScreenshotPath);
            ThrowUtils.throwIf(CharSequenceUtil.isBlank(cosUrl), ErrorCode.OPERATION_ERROR, "上传截图到对象存储失败");
            log.info("截图上传成功，URL：{}", cosUrl);
            return cosUrl;
        } finally {
            // 清理本地文件
            cleanupLocalFile(localScreenshotPath);
        }
    }

    /**
     * 上传截图到对象存储
     *
     * @param localScreenshotPath 本地截图路径
     * @return 对象存储访问URL，失败返回null
     */
    private String uploadScreenshotToCos(String localScreenshotPath) {
        if (CharSequenceUtil.isBlank(localScreenshotPath)) {
            log.error("截图文件地址为空: {}", localScreenshotPath);
            return null;
        }
        File screenshotFile = new File(localScreenshotPath);
        if (!screenshotFile.exists()) {
            log.error("当前地址下，截图文件不存在: {}", localScreenshotPath);
            return null;
        }
        // 生成 COS 对象键
        String fileName = RandomUtil.randomNumbers(8) + "_compressed.jpg";
        String cosKey = generateScreenshotKey(fileName);
        return cosManager.uploadFile(cosKey, screenshotFile);
    }

    /**
     * 生成截图的对象存储键
     * 格式：/screenshots/2025/07/31/filename.jpg
     */
    private String generateScreenshotKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("/screenshots/%s/%s", datePath, fileName);
    }

    /**
     * 清理本地文件
     *
     * @param localFilePath 本地文件路径
     */
    private void cleanupLocalFile(String localFilePath) {
        File localFile = new File(localFilePath);
        if (localFile.exists()) {
            // 清理压缩后的图片
            FileUtil.del(localFile);
            // 清理上一层文件夹
            File parentFile = localFile.getParentFile();
            if (parentFile != null && parentFile.exists() && parentFile.isDirectory()) {
                FileUtil.del(parentFile);
            }
            log.info("清理本地文件成功: {}", localFilePath);
        }
    }
}
