package com.lin.linaicodemother.innerservice;


/**
 * @Author Lin
 * @Date 2026/9/7 22:10
 * @Descriptions 被其他服务内部调用的截图服务接口
 */
public interface InnerScreenshotService {

    String generateAndUploadScreenshot(String webUrl);
}
