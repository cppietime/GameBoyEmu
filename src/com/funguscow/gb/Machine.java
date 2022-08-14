package com.funguscow.gb;

import com.funguscow.gb.frontend.PcSpeaker;
import com.funguscow.gb.frontend.Screen;

import java.io.*;
import java.util.List;

/**
 * Represents the machine as a whole, holds certain registers
 */
public class Machine {

    public static final int[] RAM_SIZES = {0, 1 << 11, 1 << 13, 1 << 15, 1 << 17};

    public enum MachineMode{
        GAMEBOY(1, false, MMU.BIOS_DMG),
        GAMEBOY_POCKET(1, false, MMU.BIOS_DMG),
        GAMEBOY_COLOR(0x11, true, MMU.BIOS_DMG);

        public final int afInitial;
        public final boolean isCgb;
        public final byte[] BIOS;

        MachineMode(int afInitial, boolean isCgb, byte[] BIOS){
            this.afInitial = afInitial;
            this.isCgb = isCgb;
            this.BIOS = BIOS;
        }
    }

    CPU cpu;
    MMU mmu;
    GPU gpu;
    Timer timer;
    Keypad keypad;
    SoundBoard soundBoard;

    boolean halt;
    boolean stop;

    int interruptsEnabled;
    int interruptsFired;

    /**
     * Speeds up emulation by the specified factor
     * Must never be 0!!
     */
    int speedUp = 1;

    MachineMode mode;
    boolean doubleSpeed;
    boolean usingColor;

    /**
     * Load a ROM file
     * @param ROM File containing ROM
     */
    public Machine(File ROM, MachineMode mode){
        timer = new Timer(this);
        keypad = new Keypad();
        keypad.machine = this;
        soundBoard = new SoundBoard(); // Not yet used
        this.mode = mode;
        byte[] header = new byte[0x150];
        try{
            FileInputStream fis = new FileInputStream(ROM);
            // Get the header information of the ROM and use it to determine MBC type, RAM/ROM size
            int read = fis.read(header, 0, 0x150);
            if(read < 0x150)
                throw new Exception("ROM file too small to be valid!");
            int cartridgeType = header[0x147] & 0xff;
            int colorMode = header[0x143] & 0xff;
            usingColor = (mode.isCgb && (colorMode & 0x80) != 0 && (colorMode & 0x6) == 0);
            int mbc = 0;
            int romBanks;
            int ramSize = 0;
            if(header[0x149] != 0){
                ramSize = RAM_SIZES[header[0x149]];
            }
            System.out.printf("Cartridge type = %02x, ramkey = %02x, Color? %s\n", cartridgeType, header[0x149], usingColor);
            switch(cartridgeType){
                case 0:
                    ramSize = 0; break;
                case 1:
                    ramSize = 0;
                case 2:
                case 3:
                    mbc = 1; break;
                case 5:
                case 6:
                    mbc = 2; ramSize = 512; break;
                case 0xF:
                case 0x10:
                case 0x11:
                case 0x12:
                case 0x13:
                    mbc = 3; break;
                case 0x19:
                case 0x1C:
                    ramSize = 0;
                case 0x1A:
                case 0x1B:
                case 0x1D:
                case 0x1E:
                    mbc = 5; break;
            }
            romBanks = header[0x148];
            if(romBanks <= 8)
                romBanks = 1 << (1 + romBanks);
            else switch(romBanks){
                case 0x52:
                    romBanks = 72; break;
                case 0x53:
                    romBanks = 80; break;
                case 0x54:
                    romBanks = 96; break;
            }
            // Create the memory component
            gpu = new GPU(this, usingColor);
            mmu = new MMU(this, mbc, romBanks, (ramSize + 0x1fff) >> 13, ramSize, usingColor);
            // Load this ROM file into it
            mmu.loadRom(header, 0, 0x150);
            mmu.loadRom(fis,  0x150, (romBanks << 14) - 0x150);
            fis.close();
        }catch(Exception e){
            e.printStackTrace();
            System.exit(3);
        }
        Logger logger = null;//new Logger("log.txt");
        cpu = new CPU(mode, mmu, null, logger);
    }

    /**
     * Load up the saved external RAM
     * @param RAM File containing literal binary data of external RAM state
     */
    public void loadRAM(File RAM) {
        try {
            InputStream is = new FileInputStream(RAM);
            mmu.loadRam(is, 0, mmu.externalRam.length);
            is.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Perform one instruction cycle
     */
    public void cycle(){
        while (stop) {
            try {
                Thread.sleep(16);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        int mCycles = cpu.performOp(this); // Execute an opcode after checking for interrupts
        gpu.incr(mCycles); // Increment the GPU's state
        timer.incr(mCycles); // Increment the timer's state
        soundBoard.step(mCycles, speedUp);
    }

    /**
     * Test single opcode tests
     * @param source Inputstream to file specifying test
     */
    public void test(InputStream source) {
        mmu.leftBios = true;
        List<OpcodeTest> tests = OpcodeTest.parse(source);
        for (int i = 0; i < tests.size(); i++) {
            OpcodeTest test = tests.get(i);
            if (!test.test(this)) {
                System.err.printf("Failed test #%d\n", i);
                System.err.println(test.end);
                cpu.dumpRegisters();
                break;
            }
            System.out.printf("Passed test #%d\n", i);
        }
    }

    public static void main(String[] args){
//        String ROMPath = "D:\\Games\\GBA\\gbtest\\mario_land.gb";
//        String ROMPath = "D:\\Games\\GBA\\pokemon\\vanilla\\Pokemon red.gb";
//        String ROMPath = "D:\\Games\\GBA\\pokemon\\vanilla\\Pokemon yellow.gbc";
        String ROMPath = "D:\\Games\\GBA\\pokemon\\vanilla\\Pokemon gold.gbc";
//        String ROMPath = "D:\\Games\\GBA\\pokemon\\vanilla\\Pokemon crystal.gbc";
//        String ROMPath = "D:\\Games\\GBA\\gbtest\\tetris.gb";
//        String ROMPath = "D:\\Games\\GBA\\gbtest\\mooneye-test-suite\\build\\emulator-only\\mbc5\\rom_64Mb.gb";
//        String ROMPath = "D:\\Games\\GBA\\gbtest\\dmg_sound\\rom_singles\\01-registers.gb";
//        String ROMPath = "D:\\Games\\GBA\\gbtest\\cpu_instrs\\cpu_instrs.gb";
//        String ROMPath = "D:\\Games\\GBA\\gbtest\\dmg-acid2.gb";
//        String ROMPath = "D:\\Games\\GBA\\gbtest\\cgb-acid2.gbc";
        Machine machine = new Machine(new File(ROMPath), MachineMode.GAMEBOY_COLOR);
//        machine.loadRAM(new File("D:\\Games\\GBA\\pokemon\\vanilla\\Pokemon red.ram"));
        Screen screen = new Screen();
        screen.keypad = machine.keypad;
        machine.gpu.screen = screen;
        screen.makeContainer();
        PcSpeaker speaker = new PcSpeaker();
        machine.soundBoard.setSpeaker(speaker);
//        try {
//            InputStream source = new FileInputStream("D:\\Games\\GBA\\gbtest\\gameboy-test-data\\cpu_tests\\v1\\01.test");
//            machine.test(source);
//            source.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        while(screen.isOpen()){
            machine.cycle();
        }
//        try {
//            OutputStream os = new FileOutputStream("D:\\Games\\GBA\\pokemon\\vanilla\\Pokemon red.ram");
//            machine.mmu.save_RAM(os, 0, machine.mmu.external_ram.length);
//            os.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        System.out.println("Stopped");
    }

}
