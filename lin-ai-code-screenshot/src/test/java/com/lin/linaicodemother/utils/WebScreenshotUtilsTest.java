package com.lin.linaicodemother.utils;

import cn.hutool.core.io.FileUtil;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class WebScreenshotUtilsTest {

    private static final byte[] ONE_PIXEL_PNG = createPng();

    private WebScreenshotUtils screenshotUtils;

    @AfterEach
    void tearDown() {
        if (screenshotUtils != null) {
            screenshotUtils.destroy();
        }
    }

    @Test
    void shouldNotInitializeDriverForBlankUrl() {
        AtomicInteger factoryCalls = new AtomicInteger();
        screenshotUtils = new WebScreenshotUtils(() -> {
            factoryCalls.incrementAndGet();
            return createWebDriver();
        }, Duration.ZERO);

        assertNull(screenshotUtils.saveWebPageScreenshot(" "));
        assertEquals(0, factoryCalls.get());
    }

    @Test
    void shouldUseConfiguredChromeDriverWithoutDownloadingAnotherVersion() {
        String original = System.getProperty("webdriver.chrome.driver");
        System.setProperty("webdriver.chrome.driver", "/usr/bin/chromedriver");
        try (var manager = mockStatic(WebDriverManager.class);
             var drivers = mockConstruction(ChromeDriver.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
                     (driver, context) -> {
                         when(driver.executeScript("return document.readyState")).thenReturn("complete");
                         when(driver.getScreenshotAs(OutputType.BYTES)).thenReturn(ONE_PIXEL_PNG);
                     })) {
            screenshotUtils = new WebScreenshotUtils();
            String screenshot = screenshotUtils.saveWebPageScreenshot("https://example.com");
            assertScreenshotExists(screenshot);
            assertEquals(1, drivers.constructed().size());
            manager.verifyNoInteractions();
            assertEquals("/usr/bin/chromedriver", System.getProperty("webdriver.chrome.driver"));
            deleteScreenshot(screenshot);
        } finally {
            if (original == null) System.clearProperty("webdriver.chrome.driver");
            else System.setProperty("webdriver.chrome.driver", original);
        }
    }

    @Test
    void shouldInitializeDriverLazilyAndReuseIt() {
        WebDriver driver = createWebDriver();
        screenshotUtils = new WebScreenshotUtils(() -> driver, Duration.ZERO);

        String firstScreenshot = screenshotUtils.saveWebPageScreenshot("https://example.com/first");
        String secondScreenshot = screenshotUtils.saveWebPageScreenshot("https://example.com/second");

        assertScreenshotExists(firstScreenshot);
        assertScreenshotExists(secondScreenshot);
        verify(driver).get("https://example.com/first");
        verify(driver).get("https://example.com/second");
        verify(driver, times(2)).manage();

        deleteScreenshot(firstScreenshot);
        deleteScreenshot(secondScreenshot);
    }

    @Test
    void shouldRetryInitializationOnNextRequestAfterFailure() {
        AtomicInteger factoryCalls = new AtomicInteger();
        WebDriver driver = createWebDriver();
        screenshotUtils = new WebScreenshotUtils(() -> {
            if (factoryCalls.getAndIncrement() == 0) {
                throw new IllegalStateException("Chrome is unavailable");
            }
            return driver;
        }, Duration.ZERO);

        assertNull(screenshotUtils.saveWebPageScreenshot("https://example.com/failed"));
        String screenshot = screenshotUtils.saveWebPageScreenshot("https://example.com/retried");

        assertScreenshotExists(screenshot);
        assertEquals(2, factoryCalls.get());
        deleteScreenshot(screenshot);
    }

    @Test
    void shouldRecreateDriverAfterSessionFailure() {
        WebDriver failedDriver = createWebDriver();
        doThrow(new WebDriverException("Session is closed"))
                .when(failedDriver).get("https://example.com/failed-session");
        WebDriver recoveredDriver = createWebDriver();
        AtomicInteger factoryCalls = new AtomicInteger();
        screenshotUtils = new WebScreenshotUtils(
                () -> factoryCalls.getAndIncrement() == 0 ? failedDriver : recoveredDriver,
                Duration.ZERO
        );

        assertNull(screenshotUtils.saveWebPageScreenshot("https://example.com/failed-session"));
        String screenshot = screenshotUtils.saveWebPageScreenshot("https://example.com/recovered");

        assertScreenshotExists(screenshot);
        verify(failedDriver).quit();
        verify(recoveredDriver).get("https://example.com/recovered");
        assertEquals(2, factoryCalls.get());
        deleteScreenshot(screenshot);
    }

    @Test
    void shouldQuitDriverOnDestroy() {
        WebDriver driver = createWebDriver();
        screenshotUtils = new WebScreenshotUtils(() -> driver, Duration.ZERO);
        String screenshot = screenshotUtils.saveWebPageScreenshot("https://example.com");

        screenshotUtils.destroy();

        verify(driver).quit();
        deleteScreenshot(screenshot);
        screenshotUtils = null;
    }

    private WebDriver createWebDriver() {
        WebDriver driver = mock(
                WebDriver.class,
                withSettings().extraInterfaces(TakesScreenshot.class, JavascriptExecutor.class)
        );
        WebDriver.Options options = mock(WebDriver.Options.class);
        when(driver.manage()).thenReturn(options);
        when(((JavascriptExecutor) driver).executeScript(any(String.class))).thenReturn("complete");
        when(((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES)).thenReturn(ONE_PIXEL_PNG);
        return driver;
    }

    private void assertScreenshotExists(String screenshotPath) {
        assertNotNull(screenshotPath);
        assertTrue(Files.isRegularFile(Path.of(screenshotPath)));
    }

    private void deleteScreenshot(String screenshotPath) {
        if (screenshotPath == null) {
            return;
        }
        Path screenshot = Path.of(screenshotPath);
        FileUtil.del(screenshot.getParent().toFile());
    }

    private static byte[] createPng() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
