package com.testmu.samples.mac;

import io.appium.java_client.AppiumBy;
import org.openqa.selenium.WebElement;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Native AppKit sample app (scripts/build-sample-dmg.sh). */
public class GreetingTest extends BaseMacTest {

    private WebElement el(String id) {
        return driver.findElement(AppiumBy.accessibilityId(id));
    }

    private void click(String id) {
        step("Click " + id);
        el(id).click();
    }

    private String text(String id) {
        return el(id).getAttribute("value");
    }

    @Test
    public void appLaunchesWithMainWindow() {
        step("Find the main window");
        WebElement window = driver.findElement(AppiumBy.iOSClassChain("**/XCUIElementTypeWindow[`title == \"DMG Sample App\"`]"));
        assertTrue(window.isDisplayed(), "Main window should be visible");
        assertEquals(text("greetingLabel"), "Waiting for input");
    }

    @Test
    public void greetsEnteredName() {
        typeInto(el("nameField"), "Alex");
        click("greetButton");

        assertEquals(text("greetingLabel"), "Hello, Alex!");
        assertEquals(text("counterLabel"), "Greetings: 1");
    }

    @Test
    public void showsValidationForEmptyName() {
        click("greetButton");
        assertEquals(text("greetingLabel"), "Please enter a name");
    }

    @Test
    public void clearResetsTheForm() {
        typeInto(el("nameField"), "Sample User");
        click("greetButton");
        click("clearButton");

        assertEquals(text("nameField"), "");
        assertEquals(text("greetingLabel"), "Waiting for input");
    }
}
