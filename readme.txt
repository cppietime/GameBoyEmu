A prototype Gameboy emulator in Java. Main function is in Machine.java.

Currently, nothing is drawn to the screen, and interrupts are never received as the Interrupt Enable register (IE @ 0xffff) is never set to anything other than 0.