package com.lin.linaicodemother.service;


/**
 * @Author Lin
 * @Date 2026/3/24 22:30
 * @Descriptions 截图服务
 */
public interface ScreenshotService {

    /**
     * 通用的截图服务，可以得到访问地址
     *
     * @param webUrl 网址
     * @return
     */
    String generateAndUploadScreenshot(String webUrl);
}
