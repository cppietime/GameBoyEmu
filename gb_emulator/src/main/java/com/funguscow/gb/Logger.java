package com.funguscow.gb;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public class Logger implements AutoCloseable {

    private Writer writer;
    private int lastPc = -1;

    public Logger(String filepath) {
        try {
            writer = new BufferedWriter(new FileWriter(filepath));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                writer = null;
            }
        }
    }

    public void log(CPU cpu) {
        // Don't log BIOS
        if (!cpu.mmu.leftBios) {
            return;
        }
        try {
            String pcPart = cpu.pc < 0x4000 ? String.format("00:%04x", cpu.pc) :
                    cpu.pc < 0x8000 ? String.format("%02d:%04x", cpu.mmu.romBank, cpu.pc) :
                            String.format("%04x", cpu.pc);
            String line = String.format("%sBC=%04x DE=%04x HL=%04x AF=%04x SP=%04x PC=%04x   $%x$%x$%x$%x$%x$%x$%x\r\n",
                    pcPart, cpu.getRegister(8), cpu.getRegister(9), cpu.getRegister(10), cpu.getRegister(13),
                    cpu.getRegister(11), cpu.pc, cpu.mmu.read8(cpu.pc), cpu.mmu.read8(cpu.pc + 1), cpu.mmu.read8(0xFF05),
                    cpu.mmu.read8(0xFF06), cpu.mmu.read8(0xFF07), cpu.mmu.read8(0xFF41), cpu.mmu.read8(0xFF44)).toUpperCase();
            if (cpu.pc != lastPc) {
                writer.write(line);
            }
            lastPc = cpu.pc;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
