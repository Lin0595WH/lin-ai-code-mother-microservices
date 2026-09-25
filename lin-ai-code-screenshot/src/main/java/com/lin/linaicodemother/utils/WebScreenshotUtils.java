package com.lin.linaicodemother.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 网页截图组件。
 *
 * <p>ChromeDriver 延迟创建并在请求间复用。由于 Selenium WebDriver 不是线程安全的，
 * 截图与销毁操作通过实例锁串行执行。</p>
 */
@Slf4j
@Component
public class WebScreenshotUtils {

    private static final int DEFAULT_WIDTH = 1920;
    private static final int DEFAULT_HEIGHT = 1080;
    private static final float COMPRESSION_QUALITY = 0.3f;
    private static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration READY_STATE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DYNAMIC_CONTENT_WAIT = Duration.ofSeconds(2);
    private static final String TEMP_DIRECTORY_PREFIX = "lin-ai-code-screenshot-";

    private final Supplier<WebDriver> webDriverFactory;
    private final Duration dynamicContentWait;
    private WebDriver webDriver;

    public WebScreenshotUtils() {
        this(WebScreenshotUtils::createChromeDriver, DYNAMIC_CONTENT_WAIT);
    }

    WebScreenshotUtils(Supplier<WebDriver> webDriverFactory) {
        this(webDriverFactory, DYNAMIC_CONTENT_WAIT);
    }

    WebScreenshotUtils(Supplier<WebDriver> webDriverFactory, Duration dynamicContentWait) {
        this.webDriverFactory = Objects.requireNonNull(webDriverFactory);
        this.dynamicContentWait = Objects.requireNonNull(dynamicContentWait);
    }

    /**
     * 生成网页截图。
     *
     * @param webUrl 网页 URL
     * @return 压缩后的截图路径，失败返回 {@code null}
     */
    public String saveWebPageScreenshot(String webUrl) {
        if (CharSequenceUtil.isBlank(webUrl)) {
            log.warn("网页截图地址不能为空");
            return null;
        }

        Path tempDirectory = null;
        Path originalImagePath = null;
        try {
            tempDirectory = Files.createTempDirectory(TEMP_DIRECTORY_PREFIX);
            originalImagePath = tempDirectory.resolve("original.png");
            Path compressedImagePath = tempDirectory.resolve("compressed.jpg");

            byte[] screenshotBytes = takeScreenshot(webUrl);
            FileUtil.writeBytes(screenshotBytes, originalImagePath.toString());
            ImgUtil.compress(
                    originalImagePath.toFile(),
                    compressedImagePath.toFile(),
                    COMPRESSION_QUALITY
            );

            log.info("网页截图生成成功: {}", compressedImagePath);
            return compressedImagePath.toString();
        } catch (Exception e) {
            deleteDirectoryQuietly(tempDirectory);
            log.error("网页截图失败: {}", webUrl, e);
            return null;
        } finally {
            deleteFileQuietly(originalImagePath);
        }
    }

    private synchronized byte[] takeScreenshot(String webUrl) {
        WebDriver driver = getOrCreateWebDriver();
        try {
            driver.manage().deleteAllCookies();
            driver.get(webUrl);
            waitForPageLoad(driver);
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        } catch (WebDriverException e) {
            // 丢弃异常会话，使下一次请求可以创建新的 Driver，而不是持续使用坏会话。
            disposeWebDriver();
            throw e;
        }
    }

    private WebDriver getOrCreateWebDriver() {
        if (webDriver == null) {
            webDriver = webDriverFactory.get();
            log.info("ChromeDriver 初始化成功");
        }
        return webDriver;
    }

    private static WebDriver createChromeDriver() {
        WebDriver driver = null;
        try {
            if (CharSequenceUtil.isBlank(System.getProperty("webdriver.chrome.driver"))) {
                WebDriverManager.chromedriver().setup();
            }
            driver = new ChromeDriver(createChromeOptions());
            driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT);
            return driver;
        } catch (RuntimeException e) {
            quitQuietly(driver);
            throw new IllegalStateException("初始化 Chrome 浏览器失败，请检查 Chrome 与 ChromeDriver", e);
        }
    }

    private static ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments(String.format("--window-size=%d,%d", DEFAULT_WIDTH, DEFAULT_HEIGHT));
        options.addArguments("--disable-extensions");
        return options;
    }

    private void waitForPageLoad(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, READY_STATE_TIMEOUT);
            wait.until(currentDriver -> Objects.equals(
                    ((JavascriptExecutor) currentDriver).executeScript("return document.readyState"),
                    "complete"
            ));
        } catch (TimeoutException e) {
            log.warn("等待页面加载完成超时，将使用当前页面状态继续截图");
        }

        try {
            Thread.sleep(dynamicContentWait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待动态内容加载时线程被中断", e);
        }
    }

    @PreDestroy
    public synchronized void destroy() {
        disposeWebDriver();
    }

    private void disposeWebDriver() {
        WebDriver driver = webDriver;
        webDriver = null;
        quitQuietly(driver);
    }

    private static void quitQuietly(WebDriver driver) {
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
        } catch (RuntimeException e) {
            log.warn("关闭 ChromeDriver 失败", e);
        }
    }

    private static void deleteFileQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("清理临时截图文件失败: {}", path, e);
        }
    }

    private static void deleteDirectoryQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            FileUtil.del(path.toFile());
        } catch (RuntimeException e) {
            log.warn("清理临时截图目录失败: {}", path, e);
        }
    }
}
