package commands

import picocli.CommandLine
import picocli.CommandLine.Command

@Command(name = "glm", mixinStandardHelpOptions = true, version = "glm-cli 0.1",
        description = "GLM-4 based AI coding agent",
        subcommands = [ChatCommand.class, AgentCommand.class])
class GlmCli implements Runnable {

    @Override
    void run() {
        // Default behavior if no subcommand is specified
        CommandLine.usage(this, System.out)
    }
}
