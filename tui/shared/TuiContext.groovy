package tui.shared

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Singleton context for shared TUI state across panes.
 * Manages application-wide settings and notifications.
 */
class TuiContext {
    
    private static final TuiContext instance = new TuiContext()
    
    private int terminalWidth = 80
    private int terminalHeight = 24
    private String diffViewMode = "auto" // "auto", "split", "unified"
    private boolean showThinking = false
    private boolean showTimestamps = true
    private int scrollSpeed = 3
    private boolean scrollAcceleration = true
    
    // Event listeners
    private List<WidthChangeListener> widthListeners = new CopyOnWriteArrayList<>()
    private List<SettingsChangeListener> settingsListeners = new CopyOnWriteArrayList<>()
    
    static TuiContext getInstance() {
        return instance
    }
    
    void notifyWidthChange(int newWidth, int newHeight) {
        if (newWidth != terminalWidth || newHeight != terminalHeight) {
            terminalWidth = newWidth
            terminalHeight = newHeight
            widthListeners.each { it.onWidthChange(newWidth, newHeight) }
        }
    }
    
    void notifySettingsChange(String setting, Object value) {
        settingsListeners.each { it.onSettingsChange(setting, value) }
    }
    
    void onWidthChange(WidthChangeListener listener) {
        widthListeners.add(listener)
    }
    
    void onSettingsChange(SettingsChangeListener listener) {
        settingsListeners.add(listener)
    }
    
    // Getters/Setters
    int getTerminalWidth() { terminalWidth }
    int getTerminalHeight() { terminalHeight }
    
    String getDiffViewMode() { diffViewMode }
    void setDiffViewMode(String mode) {
        if (mode in ["auto", "split", "unified"]) {
            this.diffViewMode = mode
            notifySettingsChange("diffViewMode", mode)
        }
    }
    
    boolean isShowThinking() { showThinking }
    void setShowThinking(boolean show) {
        this.showThinking = show
        notifySettingsChange("showThinking", show)
    }
    
    boolean isShowTimestamps() { showTimestamps }
    void setShowTimestamps(boolean show) {
        this.showTimestamps = show
        notifySettingsChange("showTimestamps", show)
    }
    
    int getScrollSpeed() { scrollSpeed }
    void setScrollSpeed(int speed) {
        this.scrollSpeed = Math.max(1, Math.min(10, speed))
        notifySettingsChange("scrollSpeed", scrollSpeed)
    }
    
    boolean isScrollAcceleration() { scrollAcceleration }
    void setScrollAcceleration(boolean enabled) {
        this.scrollAcceleration = enabled
        notifySettingsChange("scrollAcceleration", enabled)
    }
    
    // Interface definitions
    interface WidthChangeListener {
        void onWidthChange(int width, int height)
    }
    
    interface SettingsChangeListener {
        void onSettingsChange(String setting, Object value)
    }
}
