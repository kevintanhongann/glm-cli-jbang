package tui

import core.PermissionManager
import core.PermissionPromptHandler
import tui.shared.InteractivePrompt

class TuiPermissionPromptHandler implements PermissionPromptHandler {
    
    @Override
    PermissionManager.PermissionResult prompt(String message, String toolName,
                                               Map<String, Object> args) {
        // Use the existing InteractivePrompt for TUI-based prompts
        String formattedMessage = """
        PERMISSION REQUIRED
        ==================
        
        ${message}
        
        Tool: ${toolName}
        Arguments: ${formatArgs(args)}
        
        Options:
          [a]llow - Allow this call
          [A]lways - Allow this tool permanently
          [d]eny - Deny this call
          [D]eny always - Deny this tool permanently
          [c]ontinue - Allow and continue without prompting
          [s]top - Stop execution
        """.stripIndent()
        
        println formattedMessage
        
        String choice = InteractivePrompt.prompt("Your choice: ")
        
        return switch(choice?.trim()?.toLowerCase()) {
            case "a", "allow" -> PermissionManager.PermissionResult.allow(false)
            case "A", "always" -> PermissionManager.PermissionResult.allow(true, 60)
            case "d", "deny" -> PermissionManager.PermissionResult.deny(false)
            case "D", "deny always" -> PermissionManager.PermissionResult.deny(true, 60)
            case "c", "continue" -> PermissionManager.PermissionResult.allow(false)
            case "s", "stop" -> throw new PermissionManager.PermissionDeniedException("User stopped execution")
            default -> {
                println "Invalid choice, defaulting to ask..."
                PermissionManager.PermissionResult.ask()
            }
        }
    }
    
    private String formatArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "(none)"
        }
        return args.findAll { it.value != null }.take(5)
            .collect { "${it.key}: ${it.value}" }
            .join(", ") + (args.size() > 5 ? " ..." : "")
    }
}