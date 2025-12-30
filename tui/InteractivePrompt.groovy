package tui

class InteractivePrompt {
    
    static boolean confirm(String message) {
        print "${AnsiColors.yellow("?")} ${message} ${AnsiColors.dim("[y/N]")} "
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))
        String response = reader.readLine()?.trim()?.toLowerCase()
        
        return response in ['y', 'yes']
    }
    
    static String prompt(String message, String defaultValue = null) {
        String promptText = "${AnsiColors.cyan("?")} ${message}"
        if (defaultValue) {
            promptText += " ${AnsiColors.dim("(${defaultValue})")}"
        }
        promptText += " "
        
        print promptText
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))
        String response = reader.readLine()?.trim()
        
        return response?.isEmpty() ? defaultValue : response
    }
    
    static int select(String message, List<String> options) {
        println "${AnsiColors.cyan("?")} ${message}"
        
        options.eachWithIndex { option, i ->
            println "  ${AnsiColors.bold("${i + 1}.")} ${option}"
        }
        
        print "${AnsiColors.dim("Enter number:")} "
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))
        String response = reader.readLine()?.trim()
        
        try {
            int selection = Integer.parseInt(response) - 1
            if (selection >= 0 && selection < options.size()) {
                return selection
            }
        } catch (NumberFormatException e) {
            // Invalid input
        }
        
        return -1
    }
}
