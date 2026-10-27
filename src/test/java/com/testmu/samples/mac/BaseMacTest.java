package com.testmu.samples.mac;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.mac.Mac2Driver;
import io.appium.java_client.mac.options.Mac2Options;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.testng.Assert.assertEquals;

/**
 * Starts a fresh Mac2 session against the app installed from the DMG by scripts/install-dmg.sh.
 * App path and bundle id come from build/{@link #installedAppEnv()}, or APP_PATH / BUNDLE_ID env vars.
 */
public abstract class BaseMacTest {

    protected Mac2Driver driver;
    protected Properties app;

    private static final Path RESULTS_DIR = Paths.get("build", "test-results", "screenshots");

    /** Env file under build/ written by scripts/install-dmg.sh for this test's app. */
    protected String installedAppEnv() {
        return "installed-app.env";
    }

    /** Extra command-line arguments passed to the app on launch. */
    protected List<String> launchArguments() {
        return List.of();
    }

    @BeforeMethod
    public void startSession(Method test) throws IOException {
        System.out.println();
        System.out.println("[TEST] START " + getClass().getSimpleName() + "." + test.getName());
        app = loadInstalledApp();

        Mac2Options options = new Mac2Options()
                .setBundleId(app.getProperty("BUNDLE_ID"))
                // Launch the copy we installed from the DMG, not whatever LaunchServices finds first.
                .amend("appium:appPath", app.getProperty("APP_PATH"))
                .setNewCommandTimeout(Duration.ofSeconds(120))
                .setServerStartupTimeout(Duration.ofSeconds(180));
        if (!launchArguments().isEmpty()) {
            options.setArguments(launchArguments());
        }

        String appiumUrl = System.getenv().getOrDefault("APPIUM_URL", "http://127.0.0.1:4723");
        waitForAppium(appiumUrl);
        step("Launch " + app.getProperty("BUNDLE_ID") + " from " + app.getProperty("APP_PATH"));
        driver = new Mac2Driver(new URL(appiumUrl), options);
        waitUntilReady();
        step("App is ready");
    }

    /** Logs a test step to stdout, so it shows up in the Maven / CI output. */
    protected static void step(String message) {
        System.out.println("[STEP] " + message);
    }

    /** Hook for apps that render asynchronously (e.g. Electron): block until the UI is usable. */
    protected void waitUntilReady() {
    }

    // Mac2 does not apply implicit waits to element lookups, so poll explicitly for anything
    // that may not be rendered yet.
    protected WebElement waitFor(By locator) {
        return new WebDriverWait(driver, Duration.ofSeconds(20))
                .ignoring(NoSuchElementException.class)
                .until(d -> d.findElement(locator));
    }

    @AfterMethod(alwaysRun = true)
    public void stopSession(ITestResult result) throws IOException {
        try {
            quitApp(result);
        } finally {
            String status = switch (result.getStatus()) {
                case ITestResult.SUCCESS -> "PASS";
                case ITestResult.FAILURE -> "FAIL";
                default -> "SKIP";
            };
            System.out.printf("[TEST] %s %s.%s (%.1fs)%s%n", status, getClass().getSimpleName(), result.getName(),
                    (result.getEndMillis() - result.getStartMillis()) / 1000.0,
                    result.getThrowable() == null ? "" : ": " + result.getThrowable());
        }
    }

    private void quitApp(ITestResult result) throws IOException {
        if (driver == null) {
            return;
        }
        try {
            Files.createDirectories(RESULTS_DIR);
            // Capture only the app's window; a full-screen capture would include whatever else is on the desktop.
            byte[] png = driver.findElement(AppiumBy.className("XCUIElementTypeWindow")).getScreenshotAs(OutputType.BYTES);
            String status = result.isSuccess() ? "pass" : "fail";
            Path file = RESULTS_DIR.resolve(getClass().getSimpleName() + "-" + result.getName() + "-" + status + ".png");
            Files.write(file, png);
            step("Saved screenshot " + file);
        } finally {
            // Quit the app too, so the next test starts from a clean window.
            step("Quit app and end session");
            driver.executeScript("macos: terminateApp", Map.of("bundleId", app.getProperty("BUNDLE_ID")));
            driver.quit();
            driver = null;
        }
    }

    // XCTest synthesizes keystrokes through the active keyboard layout, and some layouts
    // (e.g. ABC-India with Press-and-Hold) drop characters. If typing doesn't land exactly,
    // fall back to pasting through the clipboard, which is layout-independent.
    protected void typeInto(WebElement field, String value) {
        step("Type \"" + value + "\"");
        field.sendKeys(value);
        if (value.equals(field.getAttribute("value"))) {
            return;
        }
        step("Typed text did not land exactly; pasting it from the clipboard instead");
        field.clear();
        field.click();
        driver.executeScript("macos: setClipboard", Map.of(
                "content", Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)),
                "contentType", "plaintext"));
        driver.executeScript("macos: keys", Map.of("keys", List.of(
                Map.of("key", "v", "modifierFlags", 1 << 4))));  // XCUIKeyModifierCommand
        assertEquals(field.getAttribute("value"), value, "Typed value");
    }

    // On HyperExecute Appium is started as a background service, so it may still be booting
    // when the first test runs.
    private static void waitForAppium(String appiumUrl) {
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest status = HttpRequest.newBuilder(URI.create(appiumUrl + "/status")).timeout(Duration.ofSeconds(2)).build();
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (http.send(status, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    return;
                }
            } catch (IOException e) {
                // Not listening yet.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException("Appium is not reachable at " + appiumUrl);
    }

    private Properties loadInstalledApp() throws IOException {
        Properties props = new Properties();
        Path env = Paths.get("build", installedAppEnv());
        if (Files.exists(env)) {
            try (var reader = Files.newBufferedReader(env)) {
                props.load(reader);
            }
        }
        System.getenv().forEach((k, v) -> {
            if (k.equals("APP_PATH") || k.equals("BUNDLE_ID")) {
                props.setProperty(k, v);
            }
        });
        if (props.getProperty("BUNDLE_ID") == null) {
            throw new IllegalStateException(
                    "No installed app found. Run scripts/install-dmg.sh <dmg> first, or set APP_PATH and BUNDLE_ID.");
        }
        return props;
    }
}
