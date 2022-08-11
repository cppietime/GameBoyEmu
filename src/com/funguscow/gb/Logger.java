package com.funguscow.gb;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public class Logger implements AutoCloseable {

    private Writer writer;
    private String lastLine;
    private int lastPc = -1;

    public Logger(String filepath) {
        try {
            writer = new BufferedWriter(new FileWriter(filepath));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() throws IOException {
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
        if (!cpu.mmu.left_bios) {
            return;
        }
        try {
            String pcPart = cpu.pc < 0x4000 ? String.format("00:%04x", cpu.pc) :
                    cpu.pc < 0x8000 ? String.format("%02d:%04x", cpu.mmu.rom_bank, cpu.pc) :
                            String.format("%04x", cpu.pc);
            String line = String.format("%sBC=%04x DE=%04x HL=%04x AF=%04x SP=%04x PC=%04x   $%x$%x$%x$%x$%x\r\n",
                    pcPart, cpu.get_register(8), cpu.get_register(9), cpu.get_register(10), cpu.get_register(13),
                    cpu.get_register(11), cpu.pc, cpu.mmu.read8(cpu.pc), cpu.mmu.read8(0xFF04), cpu.mmu.read8(0xFF05),
                    cpu.mmu.read8(0xFF06), cpu.mmu.read8(0xFF07)).toUpperCase();
            if (cpu.pc != lastPc) {
                writer.write(line);
            }
            lastLine = line;
            lastPc = cpu.pc;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
