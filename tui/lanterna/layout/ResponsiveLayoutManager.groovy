package tui.lanterna.layout

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.*
import tui.lanterna.widgets.SidebarPanel

class ResponsiveLayoutManager implements LayoutManager {
    
    private static final int SIDEBAR_WIDTH = 42
    private static final int SIDEBAR_THRESHOLD = 120
    private static final int MARGINS = 4
    
    private String sidebarMode = "auto" // "auto", "show", "hide"
    
    boolean isSidebarVisible(int terminalWidth) {
        switch (sidebarMode) {
            case "show": return true
            case "hide": return false
            default: return terminalWidth >= SIDEBAR_THRESHOLD
        }
    }
    
    int getContentWidth(int terminalWidth) {
        int contentWidth = terminalWidth - (isSidebarVisible(terminalWidth) ? SIDEBAR_WIDTH : 0) - MARGINS
        return Math.max(contentWidth, 20) // Minimum 20 chars for content
    }
    
    void setSidebarMode(String mode) {
        if (mode in ["auto", "show", "hide"]) {
            this.sidebarMode = mode
        }
    }
    
    String getSidebarMode() {
        return sidebarMode
    }
    
    @Override
    TerminalSize getPreferredSize(List<Component> components) {
        // Let components determine their own size
        TerminalSize maxSize = new TerminalSize(80, 24)
        return maxSize
    }
    
    @Override
    boolean hasChanged() {
        return false
    }
    
    @Override
    void doLayout(TerminalSize area, List<Component> components) {
        int width = area.getColumns()
        int height = area.getRows()
        boolean showSidebar = isSidebarVisible(width)
        
        components.each { component ->
            if (component instanceof SidebarPanel) {
                if (showSidebar) {
                    component.setPosition(width - SIDEBAR_WIDTH, 0)
                    component.setSize(SIDEBAR_WIDTH, height)
                    component.setVisible(true)
                } else {
                    component.setVisible(false)
                }
            } else {
                // Main content area (all non-sidebar components)
                int contentWidth = showSidebar ? width - SIDEBAR_WIDTH : width
                component.setSize(contentWidth, height)
                component.setPosition(0, 0)
            }
        }
    }
}
