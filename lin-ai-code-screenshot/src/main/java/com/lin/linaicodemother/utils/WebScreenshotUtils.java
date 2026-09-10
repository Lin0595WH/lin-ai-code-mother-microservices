package com.lin.linaicodemother.utils;


import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.RandomUtil;
import com.lin.linaicodemother.exception.BusinessException;
import com.lin.linaicodemother.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.Objects;

/**
 * @Author Lin
 * @Date 2026/3/24 21:13
 * @Descriptions 截图工具类
 */
// TODO:可以优化这个类，当前webDriver 在并发场景下可能出现问题
@Slf4j
public class WebScreenshotUtils {

    private static final WebDriver webDriver;

    // 浏览器窗口宽度
    private static final int DEFAULT_WIDTH = 1920;

    // 浏览器窗口高度
    private static final int DEFAULT_HEIGHT = 1080;

    // 图片后缀
    private static final String IMAGE_SUFFIX = ".jpg";

    // 压缩图片质量（0.1 = 10% 质量）
    private static final float COMPRESSION_QUALITY = 0.3f;

    // 全局静态初始化，避免重复初始化驱动程序：
    static {
        webDriver = initChromeDriver();
    }

    /**
     * 退出时销毁
     */
    @PreDestroy
    public void destroy() {
        webDriver.quit();
    }

    /**
     * 生成网页截图
     *
     * @param webUrl 网页URL
     * @return 压缩后的截图文件路径，失败返回null
     */
    public static String saveWebPageScreenshot(String webUrl) {
        // 参数校验
        if (CharSequenceUtil.isBlank(webUrl)) {
            log.error("WebScreenshotUtils 网页URL不能为空");
            return null;
        }
        try {
            // 创建临时目录
            String tempScreenshotsDirPath = System.getProperty("user.dir") + File.separator + "tmp"
                    + File.separator + "screenshots" + File.separator + RandomUtil.randomNumbers(6);
            FileUtil.mkdir(tempScreenshotsDirPath);
            // 原始截图文件路径
            String originalImageSavePath = tempScreenshotsDirPath
                    + File.separator + "original_" + RandomUtil.randomNumbers(6) + IMAGE_SUFFIX;
            // 访问网页
            webDriver.get(webUrl);
            // 等待页面加载完成
            waitForPageLoad();
            // 截图
            byte[] screenshotBytes = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
            // 保存原始图片
            saveImage(screenshotBytes, originalImageSavePath);
            log.info("原始截图保存成功：{}", originalImageSavePath);
            // 压缩图片
            String compressedImageSavePath = tempScreenshotsDirPath
                    + File.separator + "compressed_" + RandomUtil.randomNumbers(6) + IMAGE_SUFFIX;
            compressImage(originalImageSavePath, compressedImageSavePath);
            log.info("压缩图片保存成功：{}", compressedImageSavePath);
            // 删除原始图片，只保留压缩图片
            FileUtil.del(originalImageSavePath);
            return compressedImageSavePath;
        } catch (Exception e) {
            log.error("WebScreenshotUtils 网页截图失败: {}", webUrl, e);
            return null;
        }
    }


    /**
     * 初始化 Chrome 浏览器驱动
     */
    private static WebDriver initChromeDriver() {
        try {
            // 自动管理 ChromeDriver
            WebDriverManager.chromedriver().setup();
            // 配置 Chrome 选项
            ChromeOptions options = getChromeOptions();
            // 创建驱动
            WebDriver driver = new ChromeDriver(options);
            // 设置页面加载超时
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            // 设置隐式等待
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }

    /**
     * 配置 Chrome 选项
     **/
    private static ChromeOptions getChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        // 无头模式
        options.addArguments("--headless");
        // 禁用GPU（在某些环境下避免问题）
        options.addArguments("--disable-gpu");
        // 禁用沙盒模式（Docker环境需要）
        options.addArguments("--no-sandbox");
        // 禁用开发者shm使用
        options.addArguments("--disable-dev-shm-usage");
        // 设置窗口大小
        options.addArguments(String.format("--window-size=%d,%d", DEFAULT_WIDTH, DEFAULT_HEIGHT));
        // 禁用扩展
        options.addArguments("--disable-extensions");
        // 设置用户代理
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        return options;
    }

    /**
     * 保存图片到文件
     *
     * @param imageBytes 图片字节数组
     * @param imagePath  图片保存路径
     */
    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("WebScreenshotUtils 保存图片失败：{}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    /**
     * 压缩图片
     *
     * @param originalImagePath   原图片路径
     * @param compressedImagePath 压缩图片保存路径
     */
    private static void compressImage(String originalImagePath, String compressedImagePath) {
        try {
            ImgUtil.compress(
                    FileUtil.file(originalImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY
            );
        } catch (Exception e) {
            log.error("WebScreenshotUtils 压缩图片失败: {} -> {}", originalImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    /**
     * 等待页面加载完成
     */
    private static void waitForPageLoad() {
        try {
            // 创建等待页面加载对象
            WebDriverWait wait = new WebDriverWait(WebScreenshotUtils.webDriver, Duration.ofSeconds(10));
            // 等待 document.readyState 为complete
            wait.until(webDriver ->
                    Objects.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState"), "complete")
            );
            // 额外等待一段时间，确保动态内容加载完成
            Thread.sleep(2000);
            log.info("WebScreenshotUtils 页面加载完成");
        } catch (Exception e) {
            log.error("WebScreenshotUtils 等待页面加载时出现异常，继续执行截图", e);
        }
    }

}
