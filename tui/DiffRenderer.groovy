package tui

class DiffRenderer {
    
    static String renderDiff(String original, String modified) {
        List<String> origLines = original.split('\n').toList()
        List<String> modLines = modified.split('\n').toList()
        
        StringBuilder output = new StringBuilder()
        output.append(AnsiColors.bold("=== DIFF ===\n"))
        
        List<DiffLine> diffLines = computeDiff(origLines, modLines)
        
        diffLines.each { line ->
            switch (line.type) {
                case DiffType.CONTEXT:
                    output.append(AnsiColors.dim("  ${line.content}\n"))
                    break
                case DiffType.ADDITION:
                    output.append(AnsiColors.green("+ ${line.content}\n"))
                    break
                case DiffType.DELETION:
                    output.append(AnsiColors.red("- ${line.content}\n"))
                    break
            }
        }
        
        return output.toString()
    }
    
    static String renderUnifiedDiff(String original, String modified, String fileName) {
        List<String> origLines = original.split('\n').toList()
        List<String> modLines = modified.split('\n').toList()
        
        StringBuilder output = new StringBuilder()
        output.append(AnsiColors.bold("--- a/${fileName}\n"))
        output.append(AnsiColors.bold("+++ b/${fileName}\n"))
        
        List<DiffLine> diffLines = computeDiff(origLines, modLines)
        
        // Group into hunks
        List<List<DiffLine>> hunks = groupIntoHunks(diffLines, 3)
        
        hunks.each { hunk ->
            if (!hunk.isEmpty()) {
                int startLine = hunk[0].lineNumber
                output.append(AnsiColors.cyan("@@ -${startLine},${hunk.size()} +${startLine},${hunk.size()} @@\n"))
                
                hunk.each { line ->
                    switch (line.type) {
                        case DiffType.CONTEXT:
                            output.append(" ${line.content}\n")
                            break
                        case DiffType.ADDITION:
                            output.append(AnsiColors.green("+${line.content}\n"))
                            break
                        case DiffType.DELETION:
                            output.append(AnsiColors.red("-${line.content}\n"))
                            break
                    }
                }
            }
        }
        
        return output.toString()
    }
    
    public static List<DiffLine> computeDiff(List<String> origLines, List<String> modLines) {
        List<DiffLine> result = []
        
        // Simple LCS-based diff
        int i = 0, j = 0
        int lineNum = 1
        
        while (i < origLines.size() || j < modLines.size()) {
            if (i >= origLines.size()) {
                // Remaining lines are additions
                while (j < modLines.size()) {
                    result.add(new DiffLine(DiffType.ADDITION, modLines[j], lineNum++))
                    j++
                }
            } else if (j >= modLines.size()) {
                // Remaining lines are deletions
                while (i < origLines.size()) {
                    result.add(new DiffLine(DiffType.DELETION, origLines[i], lineNum++))
                    i++
                }
            } else if (origLines[i] == modLines[j]) {
                // Lines match - context
                result.add(new DiffLine(DiffType.CONTEXT, origLines[i], lineNum++))
                i++
                j++
            } else {
                // Lines differ
                result.add(new DiffLine(DiffType.DELETION, origLines[i], lineNum))
                result.add(new DiffLine(DiffType.ADDITION, modLines[j], lineNum++))
                i++
                j++
            }
        }
        
        return result
    }
    
    private static List<List<DiffLine>> groupIntoHunks(List<DiffLine> lines, int contextLines) {
        List<List<DiffLine>> hunks = []
        List<DiffLine> currentHunk = []
        int contextCount = 0
        
        lines.each { line ->
            if (line.type != DiffType.CONTEXT) {
                currentHunk.add(line)
                contextCount = 0
            } else {
                currentHunk.add(line)
                contextCount++
                
                if (contextCount > contextLines * 2 && currentHunk.size() > contextLines) {
                    // Split hunk
                    hunks.add(currentHunk)
                    currentHunk = []
                    contextCount = 0
                }
            }
        }
        
        if (!currentHunk.isEmpty()) {
            hunks.add(currentHunk)
        }
        
        return hunks
    }
    
    static enum DiffType {
        CONTEXT, ADDITION, DELETION
    }
    
    static class DiffLine {
        DiffType type
        String content
        int lineNumber
        
        DiffLine(DiffType type, String content, int lineNumber) {
            this.type = type
            this.content = content
            this.lineNumber = lineNumber
        }
    }
}
