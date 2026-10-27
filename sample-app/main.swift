// Minimal AppKit app used as the system-under-test for the DMG automation sample.
// Every interactive control has a stable accessibility identifier so Appium Mac2
// can locate it with AppiumBy.accessibilityId / XCUIElementTypeX[@identifier=...].
import AppKit

final class AppDelegate: NSObject, NSApplicationDelegate {
    var window: NSWindow!
    let nameField = NSTextField()
    let greetButton = NSButton(title: "Greet", target: nil, action: nil)
    let clearButton = NSButton(title: "Clear", target: nil, action: nil)
    let greetingLabel = NSTextField(labelWithString: "Waiting for input")
    let counterLabel = NSTextField(labelWithString: "Greetings: 0")
    var count = 0

    func applicationDidFinishLaunching(_ notification: Notification) {
        installMainMenu()
        window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 420, height: 220),
            styleMask: [.titled, .closable, .miniaturizable],
            backing: .buffered, defer: false)
        window.title = "DMG Sample App"
        window.identifier = NSUserInterfaceItemIdentifier("mainWindow")

        nameField.placeholderString = "Enter your name"
        nameField.identifier = NSUserInterfaceItemIdentifier("nameField")

        greetButton.identifier = NSUserInterfaceItemIdentifier("greetButton")
        greetButton.target = self
        greetButton.action = #selector(greet)
        greetButton.keyEquivalent = "\r"

        clearButton.identifier = NSUserInterfaceItemIdentifier("clearButton")
        clearButton.target = self
        clearButton.action = #selector(clear)

        greetingLabel.identifier = NSUserInterfaceItemIdentifier("greetingLabel")
        counterLabel.identifier = NSUserInterfaceItemIdentifier("counterLabel")

        let buttons = NSStackView(views: [greetButton, clearButton])
        let stack = NSStackView(views: [nameField, buttons, greetingLabel, counterLabel])
        stack.orientation = .vertical
        stack.alignment = .leading
        stack.spacing = 12
        stack.edgeInsets = NSEdgeInsets(top: 20, left: 20, bottom: 20, right: 20)
        stack.translatesAutoresizingMaskIntoConstraints = false
        nameField.widthAnchor.constraint(equalToConstant: 380).isActive = true

        window.contentView = stack
        window.center()
        window.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    // Standard App + Edit menus; AppKit routes Cmd+C/V/A through these menu items.
    private func installMainMenu() {
        let main = NSMenu()
        let appItem = NSMenuItem()
        appItem.submenu = NSMenu()
        appItem.submenu?.addItem(withTitle: "Quit DMG Sample App", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        main.addItem(appItem)

        let editItem = NSMenuItem()
        let edit = NSMenu(title: "Edit")
        edit.addItem(withTitle: "Cut", action: #selector(NSText.cut(_:)), keyEquivalent: "x")
        edit.addItem(withTitle: "Copy", action: #selector(NSText.copy(_:)), keyEquivalent: "c")
        edit.addItem(withTitle: "Paste", action: #selector(NSText.paste(_:)), keyEquivalent: "v")
        edit.addItem(withTitle: "Select All", action: #selector(NSText.selectAll(_:)), keyEquivalent: "a")
        editItem.submenu = edit
        main.addItem(editItem)
        NSApp.mainMenu = main
    }

    @objc func greet() {
        let name = nameField.stringValue.trimmingCharacters(in: .whitespaces)
        if name.isEmpty {
            greetingLabel.stringValue = "Please enter a name"
            return
        }
        count += 1
        greetingLabel.stringValue = "Hello, \(name)!"
        counterLabel.stringValue = "Greetings: \(count)"
    }

    @objc func clear() {
        nameField.stringValue = ""
        greetingLabel.stringValue = "Waiting for input"
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.regular)
app.run()
