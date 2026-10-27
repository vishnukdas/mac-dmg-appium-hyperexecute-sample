package com.testmu.samples.mac;

import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * MarkText, a real third-party Electron app installed from its public release DMG:
 *   scripts/run-local.sh auto suites/marktext.xml
 *
 * Electron renders the UI in a WebView, so elements carry no accessibility identifiers.
 * Locators rely on what Chromium exposes instead: ARIA labels (accessibility id),
 * visible text (StaticText value) and native menu bar titles.
 */
public class MarkTextTest extends BaseMacTest {

    private static final int ZOOM_IN_STEPS_TO_MAX = 8;
    private static final String TEXT = "This is a sample test for automating the marktext app in macOS on HyperExecute";

    @Override
    protected String installedAppEnv() {
        return "installed-marktext.env";
    }

    @Override
    protected List<String> launchArguments() {
        // Fresh Electron profile per run, so no restored documents or settings leak between runs.
        String profile = Paths.get("build", "marktext-profile", String.valueOf(System.nanoTime())).toAbsolutePath().toString();
        // Chromium builds the web content's accessibility tree lazily; without this flag
        // XCTest only sees the native window shell and every WebView lookup fails.
        return List.of("--user-data-dir=" + profile, "--force-renderer-accessibility");
    }

    @Override
    protected void waitUntilReady() {
        // The window appears before the web content loads; wait for the editor itself.
        waitFor(AppiumBy.accessibilityId("Document"));
    }

    private WebElement editor() {
        // MarkText's contenteditable has aria-label="Document".
        return driver.findElement(AppiumBy.accessibilityId("Document"));
    }

    // Element-level sendKeys/clear on Electron's contenteditable stalls while XCTest waits for
    // keyboard focus, and the synthesized keys leak to the system (e.g. Spotlight). Click the
    // editor to focus it, then send key events to the focused element with `macos: keys`.
    private void typeInEditor(String value) {
        step("Type \"" + value + "\" in the editor");
        editor().click();
        List<String> keys = value.chars().mapToObj(c -> String.valueOf((char) c)).toList();
        driver.executeScript("macos: keys", Map.of("keys", keys));
        assertEquals(editor().getAttribute("value"), value, "Editor content");
    }

    private String wordCount() {
        return driver.findElement(By.xpath(
                "//XCUIElementTypeStaticText[@value='Words']/following-sibling::XCUIElementTypeStaticText[1]"))
                .getAttribute("value");
    }

    private WebElement staticText(String value) {
        return waitFor(AppiumBy.iOSClassChain(
                "**/XCUIElementTypeStaticText[`value == \"" + value + "\"`]"));
    }

    private void clickMenu(String menu, String item) {
        step("Menu " + menu + " > " + item);
        driver.findElement(AppiumBy.iOSClassChain("**/XCUIElementTypeMenuBarItem[`title == \"" + menu + "\"`]")).click();
        waitFor(AppiumBy.iOSClassChain("**/XCUIElementTypeMenuItem[`title == \"" + item + "\"`]")).click();
    }

    @Test
    public void launchesWithEmptyUntitledDocument() {
        assertTrue(staticText("Untitled-1").isDisplayed(), "Untitled tab should be shown");
        assertEquals(wordCount(), "0");
    }

    @Test
    public void typingUpdatesEditorAndWordCount() {
        typeInEditor(TEXT);

        assertEquals(editor().getAttribute("value"), TEXT);
        assertEquals(wordCount(), String.valueOf(TEXT.split(" ").length));
    }

    @Test
    public void nativeMenuTurnsParagraphIntoHeading() {
        typeInEditor(TEXT);
        clickMenu("Paragraph", "Heading 1");

        // The block re-renders as an H1: markdown source gains "# " and heading-only controls appear.
        WebElement heading = waitFor(AppiumBy.iOSClassChain(
                "**/XCUIElementTypeTextView[`value == \"# " + TEXT + "\"`]"));
        assertTrue(heading.isDisplayed(), "Heading block should be rendered");
        assertTrue(waitFor(AppiumBy.accessibilityId("Fold this section")).isDisplayed());
    }

    @Test
    public void documentStatisticsPopupShowsCounts() {
        typeInEditor(TEXT);
        step("Open document statistics");
        driver.findElement(AppiumBy.accessibilityId("Open document statistics")).click();

        assertTrue(staticText("DOCUMENT STATISTICS").isDisplayed(), "Statistics popup should open");
        // "Characters (with spaces)" also counts the paragraph's trailing newline, so check the
        // whitespace-free count instead.
        String characters = driver.findElement(By.xpath(
                "//XCUIElementTypeStaticText[@value='Characters']/following-sibling::XCUIElementTypeStaticText[1]"))
                .getAttribute("value");
        assertEquals(characters, String.valueOf(TEXT.replace(" ", "").length()));
    }

    // Runs first (TestNG default priority is 0; ties run alphabetically).
    @Test(priority = -1)
    public void zoomInEnlargesTextToMaximum() {
        typeInEditor(TEXT);
        // The editor's TextView fills the window whatever the zoom, so measure a rendered label instead.
        int before = staticText("Words").getRect().getHeight();
        step("Words label height before zoom: " + before + "px");

        // Window > Zoom In steps 0.125 from 1.0 and stops at 2.0, so 8 clicks reach the maximum;
        // one more checks that it stays there.
        for (int i = 0; i < ZOOM_IN_STEPS_TO_MAX + 1; i++) {
            clickMenu("Window", "Zoom In");
        }
        int atMax = staticText("Words").getRect().getHeight();
        step("Words label height at max zoom: " + atMax + "px");
        assertTrue(atMax >= before * 1.8, "Text should render about 2x larger at max zoom: " + before + " -> " + atMax);
    }
}
