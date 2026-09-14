package com.lin.linaicodemother.service.impl;


import com.lin.linaicodemother.innerservice.InnerScreenshotService;
import com.lin.linaicodemother.service.ScreenshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * @Author Lin
 * @Date 2026/9/14 21:11
 * @Descriptions 内部服务实现了
 */
@DubboService
@RequiredArgsConstructor
public class InnerScreenshotServiceImpl implements InnerScreenshotService {

    private final ScreenshotService screenshotService;

    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        return screenshotService.generateAndUploadScreenshot(webUrl);
    }
}
