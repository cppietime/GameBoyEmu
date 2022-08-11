package com.funguscow.gb;

import com.funguscow.gb.frontend.Screen;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Represents the machine as a whole, holds certain registers
 */
public class Machine {

    public static enum MachineMode{
        GAMEBOY(1),
        GAMEBOY_POCKET(1),
        GAMEBOY_COLOR(1);

        public int af_initial;

        MachineMode(int af_initial){
            this.af_initial = af_initial;
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

    int interrupts_enabled;
    int interrupts_fired;

    MachineMode mode;

    /**
     * Load a ROM file
     * @param ROM File containing ROM
     */
    public Machine(File ROM){
        timer = new Timer();
        keypad = new Keypad();
        soundBoard = new SoundBoard(); // Not yet used
        gpu = new GPU(this);
        byte[] header = new byte[0x150];
        try{
            FileInputStream fis = new FileInputStream(ROM);
            // Get the header information of the ROM and use it to determine MBC type, RAM/ROM size
            int read = fis.read(header, 0, 0x150);
            if(read < 0x150)
                throw new Exception("ROM file too small to be valid!");
            int cartridge_type = header[0x147];
            int mbc = 0;
            int rom_banks;
            int ram_size = 0;
            if(header[0x149] != 0){
                ram_size = 0x800 << (2 * header[0x149]);
            }
            switch(cartridge_type){
                case 0:
                    ram_size = 0; break;
                case 1:
                    ram_size = 0;
                case 2:
                case 3:
                    mbc = 1; break;
                case 5:
                case 6:
                    mbc = 2; ram_size = 512; break;
                case 0x12:
                case 0x13:
                    mbc = 3; break;
                case 0x19:
                case 0x1C:
                    ram_size = 0;
                case 0x1A:
                case 0x1B:
                case 0x1D:
                case 0x1E:
                    mbc = 5; break;
            }
            rom_banks = header[0x148];
            if(rom_banks <= 6)
                rom_banks = 1 << (1 + rom_banks);
            else switch(rom_banks){
                case 0x52:
                    rom_banks = 72; break;
                case 0x53:
                    rom_banks = 80; break;
                case 0x54:
                    rom_banks = 96; break;
            }
            // Create the memory component
            mmu = new MMU(this, mbc, rom_banks, (ram_size + 0x1fff) >> 13, ram_size);
            // Load this ROM file into it
            mmu.load_ROM(header, 0, 0x150);
            mmu.load_ROM(fis,  0x150, (rom_banks << 14) - 0x150);
            fis.close();
        }catch(Exception e){
            e.printStackTrace();
            System.exit(3);
        }
        Logger logger = null;//new Logger("log.txt");
        cpu = new CPU(MachineMode.GAMEBOY, mmu, null, logger);
    }

    /**
     * Perform one instruction cycle
     */
    public void cycle(){
        cpu.perform_op(this); // Execute an opcode after checking for interrupts
        gpu.incr(cpu.m_delta); // Increment the GPU's state
        timer.incr(this, cpu.m_delta); // Increment the timer's state
    }

    public static void main(String[] args){
        Machine machine = new Machine(new File("D:\\Games\\GBA\\gbtest\\mem_timing\\individual\\01-read_timing.gb"));
//        Machine machine = new Machine(new File("D:\\Games\\GBA\\gbtest\\cpu_instrs\\cpu_instrs.gb"));
        Screen screen = new Screen();
        screen.keypad = machine.keypad;
        machine.gpu.screen = screen;
        screen.makeContainer();
        while(!machine.stop){
            machine.cycle();
        }
    }

}
