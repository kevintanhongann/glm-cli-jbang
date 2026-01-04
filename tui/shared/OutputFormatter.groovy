package tui.shared

class OutputFormatter {
    
    static void printHeader(String title) {
        int width = 60
        String border = "═" * width
        
        println AnsiColors.cyan("╔${border}╗")
        println AnsiColors.cyan("║") + AnsiColors.bold(title.center(width)) + AnsiColors.cyan("║")
        println AnsiColors.cyan("╚${border}╝")
    }
    
    static void printSection(String title) {
        println "\n${AnsiColors.bold(AnsiColors.blue("▶ ${title}"))}"
        println AnsiColors.dim("─" * 40)
    }
    
    static void printSuccess(String message) {
        println "${AnsiColors.green("✓")} ${message}"
    }
    
    static void printError(String message) {
        println "${AnsiColors.red("✗")} ${message}"
    }
    
    static void printWarning(String message) {
        println "${AnsiColors.yellow("⚠")} ${message}"
    }
    
    static void printInfo(String message) {
        println "${AnsiColors.blue("ℹ")} ${message}"
    }
    
    static void printCode(String code, String language = null) {
        String fence = '```'
        println AnsiColors.dim("${fence}${language ?: ''}")
        code.split('\n').each { line ->
            println "  ${line}"
        }
        println AnsiColors.dim(fence)
    }
    
    static void printTable(List<List<String>> rows, List<String> headers = null) {
        if (rows.isEmpty()) return
        
        int cols = headers?.size() ?: rows[0].size()
        List<Integer> widths = (0..<cols).collect { col ->
            int maxWidth = headers ? headers[col].length() : 0
            rows.each { row ->
                if (col < row.size()) {
                    maxWidth = Math.max(maxWidth, row[col].length())
                }
            }
            return maxWidth + 2
        }
        
        // Print header
        if (headers) {
            String headerRow = headers.withIndex().collect { h, i ->
                h.padRight(widths[i])
            }.join("│")
            println AnsiColors.bold(headerRow)
            println "─" * widths.sum()
        }
        
        // Print rows
        rows.each { row ->
            String dataRow = row.withIndex().collect { cell, i ->
                (cell ?: "").padRight(widths[i])
            }.join("│")
            println dataRow
        }
    }
}
