package tui.shared

import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole

class AnsiColors {
    
    static void install() {
        AnsiConsole.systemInstall()
    }
    
    static void uninstall() {
        AnsiConsole.systemUninstall()
    }
    
    static String red(String text) {
        Ansi.ansi().fg(Ansi.Color.RED).a(text).reset().toString()
    }
    
    static String green(String text) {
        Ansi.ansi().fg(Ansi.Color.GREEN).a(text).reset().toString()
    }
    
    static String yellow(String text) {
        Ansi.ansi().fg(Ansi.Color.YELLOW).a(text).reset().toString()
    }
    
    static String blue(String text) {
        Ansi.ansi().fg(Ansi.Color.BLUE).a(text).reset().toString()
    }
    
    static String cyan(String text) {
        Ansi.ansi().fg(Ansi.Color.CYAN).a(text).reset().toString()
    }
    
    static String magenta(String text) {
        Ansi.ansi().fg(Ansi.Color.MAGENTA).a(text).reset().toString()
    }
    
    static String bold(String text) {
        Ansi.ansi().bold().a(text).reset().toString()
    }
    
    static String dim(String text) {
        Ansi.ansi().fgBrightBlack().a(text).reset().toString()
    }
}
