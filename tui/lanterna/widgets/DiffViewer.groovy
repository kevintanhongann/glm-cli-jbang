package tui.lanterna.widgets

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor

class DiffViewer extends Panel {
    
    enum ViewMode { SPLIT, UNIFIED }
    
    private String originalContent
    private String modifiedContent
    private String filePath
    private ViewMode viewMode = ViewMode.UNIFIED
    private int availableWidth
    
    private static final int SPLIT_THRESHOLD = 80
    
    DiffViewer(String filePath, String original, String modified, int width) {
        this.filePath = filePath
        this.originalContent = original
        this.modifiedContent = modified
        this.availableWidth = width
        
        determineViewMode()
        render()
    }
    
    private void determineViewMode() {
        // Auto-select based on available width
        viewMode = availableWidth >= SPLIT_THRESHOLD ? ViewMode.SPLIT : ViewMode.UNIFIED
    }
    
    private void render() {
        removeAllComponents()
        setLayoutManager(new LinearLayout(Direction.VERTICAL))
        
        // Header
        addComponent(createHeader())
        
        if (viewMode == ViewMode.SPLIT) {
            renderSplitView()
        } else {
            renderUnifiedView()
        }
    }
    
    private Component createHeader() {
        Label header = new Label("📄 ${filePath}")
        return header
    }
    
    private void renderSplitView() {
        Panel splitPanel = new Panel()
        splitPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
        
        int paneWidth = Math.max(20, (availableWidth - 3) / 2) // -3 for separator
        
        // Left pane (original)
        Panel leftPane = createPane(originalContent, paneWidth, "Before")
        leftPane.setBorder(Borders.singleLine("Before"))
        
        // Separator
        Label separator = new Label("│")
        
        // Right pane (modified)  
        Panel rightPane = createPane(modifiedContent, paneWidth, "After")
        rightPane.setBorder(Borders.singleLine("After"))
        
        splitPanel.addComponent(leftPane)
        splitPanel.addComponent(separator)
        splitPanel.addComponent(rightPane)
        
        addComponent(splitPanel)
    }
    
    private void renderUnifiedView() {
        // Traditional unified diff format
        List<String> diffLines = computeUnifiedDiff()
        
        Panel diffPanel = new Panel()
        diffPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))
        
        diffLines.each { line ->
            Label label = new Label(line)
            
            if (line.startsWith('+') && !line.startsWith('+++')) {
                label.setForegroundColor(TextColor.ANSI.GREEN)
            } else if (line.startsWith('-') && !line.startsWith('---')) {
                label.setForegroundColor(TextColor.ANSI.RED)
            } else if (line.startsWith('@@')) {
                label.setForegroundColor(TextColor.ANSI.CYAN)
            }
            
            diffPanel.addComponent(label)
        }
        
        addComponent(diffPanel)
    }
    
    private Panel createPane(String content, int width, String title) {
        Panel pane = new Panel()
        pane.setLayoutManager(new LinearLayout(Direction.VERTICAL))
        pane.setPreferredSize(new TerminalSize(width, 10))
        
        def lines = content ? content.split('\n') : []
        lines.each { line ->
            Label lineLabel = new Label(truncate(line, width))
            pane.addComponent(lineLabel)
        }
        
        return pane
    }
    
    private List<String> computeUnifiedDiff() {
        def originalLines = originalContent ? originalContent.split('\n') : []
        def modifiedLines = modifiedContent ? modifiedContent.split('\n') : []
        
        List<String> result = []
        result << "--- a/${filePath}"
        result << "+++ b/${filePath}"
        result << "@@ -1,${originalLines.size()} +1,${modifiedLines.size()} @@"
        
        // Simple line-by-line diff (not a real LCS diff)
        int maxLines = Math.max(originalLines.size(), modifiedLines.size())
        for (int i = 0; i < maxLines; i++) {
            if (i < originalLines.size()) {
                result << "- ${originalLines[i]}"
            }
            if (i < modifiedLines.size()) {
                result << "+ ${modifiedLines[i]}"
            }
        }
        
        return result
    }
    
    void setViewMode(ViewMode mode) {
        this.viewMode = mode
        render()
    }
    
    void updateWidth(int width) {
        this.availableWidth = width
        determineViewMode()
        render()
    }
    
    ViewMode getViewMode() {
        return viewMode
    }
    
    private static String truncate(String s, int maxLen) {
        if (!s) return ''
        if (s.length() <= maxLen) return s
        return s.substring(0, maxLen - 3) + '...'
    }
}
