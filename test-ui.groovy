///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.googlecode.lanterna:lanterna:3.1.2

import com.googlecode.lanterna.*
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.screen.TerminalScreen

println("Testing Lanterna UI creation...")
def factory = new DefaultTerminalFactory()
def screen = factory.createScreen()
screen.startScreen()

def textGUI = new MultiWindowTextGUI(screen)
textGUI.setBlockingIO(false)
textGUI.setEOFWhenNoWindows(true)

def window = new BasicWindow("Test Window")
def panel = new Panel()
panel.setLayoutManager(new LinearLayout(Direction.VERTICAL))
panel.addComponent(new Label("Hello Lanterna!"))
window.setComponent(panel)

textGUI.addWindow(window)

println("UI created successfully!")
screen.stopScreen()
