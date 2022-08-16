package com.funguscow.gb;

import java.io.InputStream;
import java.util.*;

public class OpcodeTest {

    private static int parseHex(Scanner scanner) {
        String hexStr = scanner.next().split("0x")[1];
        return Integer.parseInt(hexStr, 16);
    }

    public static class CpuState {
        int a, b, c, d, e, h, l, f, pc, sp;
        Map<Integer, Integer> ram;

        public CpuState(Scanner scanner) {
            a = parseHex(scanner);
            b = parseHex(scanner);
            c = parseHex(scanner);
            d = parseHex(scanner);
            e = parseHex(scanner);
            f = parseHex(scanner);
            h = parseHex(scanner);
            l = parseHex(scanner);
            pc = parseHex(scanner);
            sp = parseHex(scanner);
            int numRam = scanner.nextInt();
            ram = new HashMap<>();
            for (int i = 0; i < numRam; i++) {
                int address = parseHex(scanner);
                int value = parseHex(scanner);
                ram.put(address, value);
            }
        }

        public String toString() {
            return String.format("a=%02x b=%02x c=%02x d=%02x e=%02x f=%02x h=%02x l=%02x pc=%04x sp=%04x",
                    a, b, c, d, e, f, h, l, pc, sp);
        }
    }

    CpuState begin, end;
    int cycles;

    public boolean test(Machine machine) {
        CPU cpu = machine.cpu;
        MMU mmu = machine.mmu;

        // Set up initial
        for (Map.Entry<Integer, Integer> entry : begin.ram.entrySet()) {
            int address = entry.getKey();
            int value = entry.getValue();
            if (address < 0x8000) { // ROM
                mmu.rom[address] = (byte)value;
            }
            else if (address < 0xA000) { // VRAM (really??)
                machine.gpu.vram[address - 0x8000] = (byte)value;
            }
            else if (address < 0xC000) { // External ram
                mmu.externalRam[address - 0xA000] = (byte)value;
            }
            else if (address < 0xFE00) { // WRAM
                mmu.internalRam[(address - 0xC000) & 0x1fff] = (byte)value;
            }
            else { // High RAM
                mmu.zeroPage[address - 0xFF80] = (byte)value;
            }
        }
        cpu.a = begin.a;
        cpu.b = begin.b;
        cpu.c = begin.c;
        cpu.d = begin.d;
        cpu.e = begin.e;
        cpu.h = begin.h;
        cpu.l = begin.l;
        cpu.setFlagRegister(begin.f);
        cpu.pc = begin.pc;
        cpu.sp = begin.sp;

        // Run
        int actualCycles = cpu.performOp(machine);

        // Compare
        for (Map.Entry<Integer, Integer> entry : end.ram.entrySet()) {
            int actual = mmu.read8(entry.getKey());
            if (actual != entry.getValue()) {
                return false;
            }
        }
        if (cpu.a != end.a) {
            return false;
        }
        if (cpu.b != end.b) {
            return false;
        }
        if (cpu.c != end.c) {
            return false;
        }
        if (cpu.d != end.d) {
            return false;
        }
        if (cpu.e != end.e) {
            return false;
        }
        if (cpu.getFlagRegister() != end.f) {
            return false;
        }
        if (cpu.h != end.h) {
            return false;
        }
        if (cpu.l != end.l) {
            return false;
        }
        if (cpu.pc != end.pc) {
            return false;
        }
        if (cpu.sp != end.sp) {
            return false;
        }
        return actualCycles == cycles;
    }

    public OpcodeTest(Scanner scanner) {
        begin = new CpuState(scanner);
        end = new CpuState(scanner);
        cycles = scanner.nextInt();
    }

    public OpcodeTest(InputStream is) {
        Scanner scanner = new Scanner(is);
        begin = new CpuState(scanner);
        end = new CpuState(scanner);
        cycles = scanner.nextInt();
        scanner.close();
    }

    public static List<OpcodeTest> parse(InputStream is) {
        Scanner scanner = new Scanner(is);
        List<OpcodeTest> tests = new ArrayList<>();
        while (scanner.hasNext()) {
            OpcodeTest test = new OpcodeTest(scanner);
            tests.add(test);
        }
        scanner.close();
        return tests;
    }

}
