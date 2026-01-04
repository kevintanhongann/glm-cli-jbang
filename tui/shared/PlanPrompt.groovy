package tui.shared

import java.util.Scanner

class PlanPrompt {

    static boolean confirmPlan(String planFilePath) {
        println "\n${AnsiColors.bold("═" * 60)}"
        println AnsiColors.bold("PLAN REVIEW")
        println AnsiColors.bold("═" * 60) + "\n"

        def planFile = new File(planFilePath)
        if (planFile.exists()) {
            println planFile.text
        } else {
            OutputFormatter.printError("Plan file not found: ${planFilePath}")
            return false
        }

        println "\n${AnsiColors.bold("═" * 60)}\n"

        while (true) {
            def choice = InteractivePrompt.select(
                "What would you like to do?",
                ["Approve and execute", "Revise the plan", "Cancel plan mode"]
            )

            switch (choice) {
                case 0:
                    return true
                case 1:
                    return false
                case 2:
                    throw new InterruptedException("Plan cancelled by user")
            }
        }
    }

    static String askRevision() {
        println "\n${AnsiColors.yellow("Please describe what needs to be revised in the plan:")}"
        def scanner = new Scanner(System.in)
        return scanner.nextLine()
    }

    static void showProgress(String message, int current, int total) {
        def percent = (current / total * 100).intValue()
        def filled = percent / 5
        def bar = "█" * filled + "░" * (20 - filled)
        print "\r${AnsiColors.cyan("▶")} ${message} [${bar}] ${percent}%"
        if (current == total) {
            println ""
        }
    }

    static String prompt(String question) {
        return InteractivePrompt.prompt(question)
    }
}
