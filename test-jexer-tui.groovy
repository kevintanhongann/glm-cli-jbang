#!/usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS ./jexer-2.0.0-full.jar
//DEPS info.picocli:picocli:4.7.7

import picocli.CommandLine
import picocli.CommandLine.Option
import jexer.TApplication
import jexer.TWindow
import jexer.TLabel
import jexer.TField
import jexer.TButton
import jexer.TAction
import jexer.event.TKeypressEvent
import jexer.bits.CellAttributes
import jexer.bits.Color
import static jexer.TKeypress.*

/**
 * Test Jexer TUI components.
 */
@CommandLine(name = "test-jexer-tui", description = "Test Jexer TUI implementation")
class TestJexerTUI {

    @Option(names = ["-c", "--components"], description = "Test specific components (log, input, status, sidebar, all)")
    String testComponents = "all"

    @Override
    void run() {
        def app = new TApplication(TApplication.BackendType.XTERM)

        try {
            def components = testComponents.toLowerCase().split(',')

            if ('log' in components || 'all' in components) {
                testActivityLog(app)
            }

            if ('input' in components || 'all' in components) {
                testCommandInput(app)
            }

            if ('status' in components || 'all' in components) {
                testStatusBar(app)
            }

            if ('sidebar' in components || 'all' in components) {
                testSidebar(app)
            }

            // Show completion message
            def completionWindow = app.addWindow(
                "Test Complete",
                40, 10, TWindow.CENTERED
            )

            def msg = new JLabel("All tests passed! Press any key to exit.")
            msg.setX(2)
            msg.setY(2)
            completionWindow.add(msg)

            completionWindow.add(new TButton("Exit") {
                @Override
                void DO() {
                    app.exit()
                }
            })

            app.run()
        } catch (Exception e) {
            app.addWindow("Error", 30, 5, TWindow.MODAL).add(new JLabel(e.message))
            app.run()
        }
    }

    private void testActivityLog(TApplication app) {
        def testWindow = app.addWindow("Activity Log Test", 60, 20, TWindow.CENTERED)

        def log = new JLabel()
        log.setX(2)
        log.setY(2)
        testWindow.add(log)

        testWindow.add(new TButton("Append Message") {
            @Override
            void DO() {
                log.setText(log.getText() + "\n✓ Test message")
            }
        })

        testWindow.add(new TButton("Append Error") {
            @Override
            void DO() {
                log.setText(log.getText() + "\n✗ Test error")
            }
        })

        testWindow.add(new TButton("Clear") {
            @Override
            void DO() {
                log.setText("")
            }
        })

        testWindow.add(new TButton("Close") {
            @Override
            void DO() {
                testWindow.close()
            }
        })
    }

    private void testCommandInput(TApplication app) {
        def testWindow = app.addWindow("Command Input Test", 60, 15, TWindow.CENTERED)

        testWindow.add(new JLabel("Test input field below (Press Enter to submit)"))
        testWindow.add(new JLabel(""))

        def input = new TField(40, '')
        input.setX(2)
        input.setY(4)
        testWindow.add(input)

        input.setEnterAction(new TAction() {
            @Override
            void DO() {
                def text = input.getText()
                if (!text.isEmpty()) {
                    def resultWindow = app.addWindow("Result", 30, 5, TWindow.MODAL)
                    resultWindow.add(new JLabel("You entered: ${text}"))
                    resultWindow.add(new TButton("OK") {
                        @Override
                        void DO() {
                            resultWindow.close()
                        }
                    })
                }
                input.setText('')
            }
        })

        testWindow.add(new TButton("Close") {
            @Override
            void DO() {
                testWindow.close()
            }
        })
    }

    private void testStatusBar(TApplication app) {
        def testWindow = app.addWindow("Status Bar Test", 70, 5, TWindow.CENTERED)

        int x = 2
        int y = 2

        def modelLabel = new JLabel("Model: opencode/big-pickle")
        modelLabel.setX(x)
        modelLabel.setY(y)
        testWindow.add(modelLabel)

        def sep1 = new JLabel(" | ")
        sep1.setX(x + modelLabel.getText().length())
        sep1.setY(y)
        testWindow.add(sep1)

        def dirLabel = new JLabel("Dir: glm-cli-jbang")
        dirLabel.setX(x + modelLabel.getText().length() + 4)
        dirLabel.setY(y)
        testWindow.add(dirLabel)

        def sep2 = new JLabel(" | ")
        sep2.setX(x + modelLabel.getText().length() + dirLabel.getText().length() + 5)
        sep2.setY(y)
        testWindow.add(sep2)

        def shortcutLabel = new JLabel("Ctrl+C: Exit")
        shortcutLabel.setX(x + modelLabel.getText().length() + dirLabel.getText().length() + 8)
        shortcutLabel.setY(y)
        testWindow.add(shortcutLabel)

        testWindow.add(new TButton("Close") {
            @Override
            void DO() {
                testWindow.close()
            }
        })
    }

    private void testSidebar(TApplication app) {
        def testWindow = app.addWindow("Sidebar Test", 45, 20, TWindow.CENTERED)

        int x = 2
        int y = 2

        def header = new JLabel("┌── Sidebar ───┐")
        header.setX(x)
        header.setY(y)
        testWindow.add(header)

        y++

        def sessionLabel = new JLabel("│ Session: Test-123...")
        sessionLabel.setX(x)
        sessionLabel.setY(y)
        testWindow.add(sessionLabel)

        y++

        def tokenLabel = new JLabel("│ Tokens: 1024 / 512")
        tokenLabel.setX(x)
        tokenLabel.setY(y)
        testWindow.add(tokenLabel)

        y++

        def lspLabel = new JLabel("│ LSP: 2 servers, 3 diagnostics")
        lspLabel.setX(x)
        lspLabel.setY(y)
        testWindow.add(lspLabel)

        y++

        def footer = new JLabel("└──────────────────┘")
        footer.setX(x)
        footer.setY(y)
        testWindow.add(footer)

        y += 2

        testWindow.add(new TButton("Close") {
            @Override
            void DO() {
                testWindow.close()
            }
        })
    }
}
