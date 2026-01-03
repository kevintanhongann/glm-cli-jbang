package commands

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import tui.LanternaTUI

@Command(name = 'glm', mixinStandardHelpOptions = true, version = 'glm-cli 1.0.0',
        description = 'GLM-4 based AI coding agent',
        subcommands = [ChatCommand.class, AgentCommand.class, AuthCommand.class, InitCommand.class, SessionCommand.class, ModelsCommand.class])
class GlmCli implements Runnable {

    @Option(names = ['--simple'], description = 'Use simple console mode (no TUI)')
    boolean simpleMode = false

    @Option(names = ['-m', '--model'], description = 'Model to use (default: opencode/big-pickle)')
    String model = 'opencode/big-pickle'

    @Override
    void run() {
        // If no subcommand is specified, launch the Lanterna TUI
        if (simpleMode) {
            // Simple console mode - just show help
            CommandLine.usage(this, System.out)
        } else {
            // Launch Lanterna TUI
            try {
                LanternaTUI tui = new LanternaTUI()
                tui.start(model, System.getProperty('user.dir'))
            } catch (Exception e) {
                // Fallback to simple mode if TUI fails
                System.err.println("TUI failed to start: ${e.message}")
                System.err.println("Use --simple flag for console mode, or use 'glm chat' for basic chat.")
                CommandLine.usage(this, System.out)
            }
        }
    }

}
