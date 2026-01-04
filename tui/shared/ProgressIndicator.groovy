package tui.shared

class ProgressIndicator {
    
    private static final String[] SPINNER_FRAMES = ['⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏']
    private int frame = 0
    private boolean running = false
    private Thread spinnerThread
    private String message
    
    void start(String message) {
        this.message = message
        this.running = true
        this.frame = 0
        
        spinnerThread = Thread.start {
            while (running) {
                print "\r${AnsiColors.cyan(SPINNER_FRAMES[frame])} ${message}"
                frame = (frame + 1) % SPINNER_FRAMES.length
                Thread.sleep(80)
            }
        }
    }
    
    void stop(boolean success = true) {
        running = false
        spinnerThread?.join()
        
        String icon = success ? AnsiColors.green("✓") : AnsiColors.red("✗")
        println "\r${icon} ${message}"
    }
    
    static void withSpinner(String message, Closure action) {
        ProgressIndicator spinner = new ProgressIndicator()
        spinner.start(message)
        
        try {
            action.call()
            spinner.stop(true)
        } catch (Exception e) {
            spinner.stop(false)
            throw e
        }
    }
}
