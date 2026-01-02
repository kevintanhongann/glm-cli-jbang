package commands

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import tui.JexerTUI
import tui.LanternaTUI

@Command(name = "glm", mixinStandardHelpOptions = true, version = "glm-cli 1.0.0",
        description = "GLM-4 based AI coding agent",
        subcommands = [ChatCommand.class, AgentCommand.class, AuthCommand.class, InitCommand.class, SessionCommand.class])
class GlmCli implements Runnable {

    @Option(names = ["--simple"], description = "Use simple console mode (no TUI)")
    boolean simpleMode = false

    @Option(names = ["--tui"], description = "TUI backend to use (lanterna or jexer, default: lanterna)")
    String tuiBackend = "lanterna"

    @Option(names = ["-m", "--model"], description = "Model to use (default: glm-4.7)")
    String model = "glm-4.7"

    @Override
    void run() {
        // If no subcommand is specified, launch the TUI
        if (simpleMode) {
            // Simple console mode - just show help
            CommandLine.usage(this, System.out)
        } else {
            // Launch the selected TUI backend
            try {
                if (tuiBackend.toLowerCase() == "lanterna") {
                    LanternaTUI tui = new LanternaTUI()
                    tui.start(model, System.getProperty("user.dir"))
                } else if (tuiBackend.toLowerCase() == "jexer") {
                    JexerTUI tui = new JexerTUI()
                    tui.start(model, System.getProperty("user.dir"))
                } else {
                    System.err.println("Unknown TUI backend: ${tuiBackend}")
                    System.err.println("Valid options: lanterna, jexer")
                    CommandLine.usage(this, System.out)
                }
            } catch (Exception e) {
                // Fallback to simple mode if TUI fails
                System.err.println("TUI failed to start: ${e.message}")
                System.err.println("Use --simple flag for console mode, or use 'glm chat' for basic chat.")
                CommandLine.usage(this, System.out)
            }
        }
    }
}


