///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.googlecode.lanterna:lanterna:3.1.2

import com.googlecode.lanterna.terminal.DefaultTerminalFactory

try {
    def terminalFactory = new DefaultTerminalFactory()
    def terminal = terminalFactory.createTerminal()

    int cols = terminal.getTerminalSize().getColumns()
    int rows = terminal.getTerminalSize().getRows()

    println "Terminal size detected: ${cols} columns x ${rows} rows"
    println "Should show sidebar: ${cols >= 80}"

    terminal.close()

    if (cols < 80) {
        println ""
        println "WARNING: Terminal is only ${cols} columns wide."
        println "Sidebar requires at least 80 columns."
        println "Please resize your terminal to at least 80x24 and try again."
        System.exit(1)
    }

} catch (Exception e) {
    System.err.println "Error detecting terminal size: ${e.message}"
    e.printStackTrace()
    System.exit(1)
}
