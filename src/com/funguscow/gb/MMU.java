package com.funguscow.gb;

import javax.imageio.IIOException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Handles memory access, bank switching, and memory mapped IO and registers
 */
public class MMU {

    private static final int[] BIOS = {
            0x31, 0xFE, 0xFF, 0xAF, 0x21, 0xFF, 0x9F, 0x32, 0xCB, 0x7C, 0x20, 0xFB, 0x21, 0x26, 0xFF, 0x0E,
            0x11, 0x3E, 0x80, 0x32, 0xE2, 0x0C, 0x3E, 0xF3, 0xE2, 0x32, 0x3E, 0x77, 0x77, 0x3E, 0xFC, 0xE0,
            0x47, 0x11, 0x04, 0x01, 0x21, 0x10, 0x80, 0x1A, 0xCD, 0x95, 0x00, 0xCD, 0x96, 0x00, 0x13, 0x7B,
            0xFE, 0x34, 0x20, 0xF3, 0x11, 0xD8, 0x00, 0x06, 0x08, 0x1A, 0x13, 0x22, 0x23, 0x05, 0x20, 0xF9,
            0x3E, 0x19, 0xEA, 0x10, 0x99, 0x21, 0x2F, 0x99, 0x0E, 0x0C, 0x3D, 0x28, 0x08, 0x32, 0x0D, 0x20,
            0xF9, 0x2E, 0x0F, 0x18, 0xF3, 0x67, 0x3E, 0x64, 0x57, 0xE0, 0x42, 0x3E, 0x91, 0xE0, 0x40, 0x04,
            0x1E, 0x02, 0x0E, 0x0C, 0xF0, 0x44, 0xFE, 0x90, 0x20, 0xFA, 0x0D, 0x20, 0xF7, 0x1D, 0x20, 0xF2,
            0x0E, 0x13, 0x24, 0x7C, 0x1E, 0x83, 0xFE, 0x62, 0x28, 0x06, 0x1E, 0xC1, 0xFE, 0x64, 0x20, 0x06,
            0x7B, 0xE2, 0x0C, 0x3E, 0x87, 0xF2, 0xF0, 0x42, 0x90, 0xE0, 0x42, 0x15, 0x20, 0xD2, 0x05, 0x20,
            0x4F, 0x16, 0x20, 0x18, 0xCB, 0x4F, 0x06, 0x04, 0xC5, 0xCB, 0x11, 0x17, 0xC1, 0xCB, 0x11, 0x17,
            0x05, 0x20, 0xF5, 0x22, 0x23, 0x22, 0x23, 0xC9, 0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D, 0x00, 0x0B,
            0x03, 0x73, 0x00, 0x83, 0x00, 0x0C, 0x00, 0x0D, 0x00, 0x08, 0x11, 0x1F, 0x88, 0x89, 0x00, 0x0E,
            0xDC, 0xCC, 0x6E, 0xE6, 0xDD, 0xDD, 0xD9, 0x99, 0xBB, 0xBB, 0x67, 0x63, 0x6E, 0x0E, 0xEC, 0xCC,
            0xDD, 0xDC, 0x99, 0x9F, 0xBB, 0xB9, 0x33, 0x3E, 0x3c, 0x42, 0xB9, 0xA5, 0xB9, 0xA5, 0x42, 0x4C,
            0x21, 0x04, 0x01, 0x11, 0xA8, 0x00, 0x1A, 0x13, 0xBE, 0x20, 0xFE, 0x23, 0x7D, 0xFE, 0x34, 0x20,
            0xF5, 0x06, 0x19, 0x78, 0x86, 0x23, 0x05, 0x20, 0xFB, 0x86, 0x20, 0xFE, 0x3E, 0x01, 0xE0, 0x50};

    Machine machine;
    private int ram_size;
    private byte[] rom;
    private byte[] internal_ram;
    private byte[] external_ram;
    private byte[] zero_page;
    private int rom_bank;
    private int num_rom_banks;
    private int ram_bank;
    private int num_ram_banks;
    private int mbc_type;
    private boolean mbc1_ram_32k;
    private boolean ram_enabled;
    private int mbc3_rtc_register;
    private int mbc3_rtc_latch;
    private int seconds;
    private int minutes;
    private int hours;
    private int days;
    private boolean mbc3_halt_rtc;
    public boolean left_bios = false;

    /**
     * Initialize according to power up seqeunce
     * @param machine Parent machine
     * @param mbc_type Cartridge type
     * @param num_rom_banks Number of ROM banks
     * @param num_ram_banks Number of RAM banks
     * @param ram_size Size in bytes of RAM
     */
    public MMU(Machine machine, int mbc_type, int num_rom_banks, int num_ram_banks, int ram_size){
        this.machine = machine;
        this.mbc_type = mbc_type;
        this.num_rom_banks = num_rom_banks;
        this.num_ram_banks = num_ram_banks;
        this.ram_size = ram_size;
        rom = new byte[num_rom_banks * 0x4000];
        rom_bank = 1;
        ram_bank = 0;
        external_ram = new byte[ram_size];
        internal_ram = new byte[0x2000];
        zero_page = new byte[128];
        // Startup sequence
        write8(0xff0, 0);
        write8(0xff06, 0);
        write8(0xff07, 0);
        /* TODO write initial sound values */
        write8(0xff40, 0x91);
        write8(0xff42, 0);
        write8(0xff43, 0);
        write8(0xff45, 0);
        write8(0xff47, 0xfc);
        write8(0xff48, 0xff);
        write8(0xff49, 0xff);
        write8(0xfffa, 0);
        write8(0xff4b, 0);
        write8(0xffff, 0);
        System.out.println(ram_size + " byte of RAM across " + num_ram_banks + " banks");
        System.out.println(num_rom_banks + " banks of ROM using MBC #" + mbc_type);
    }

    /**
     * Load from an arry
     * @param ROM Array of bytes  of ROM data
     * @param offset WHere to load to
     * @param len How many bytes to load
     */
    public void load_ROM(byte[] ROM, int offset, int len){
        System.arraycopy(ROM, 0, rom, offset, len);
    }

    /**
     * Load from an input stream
     * @param ROM InputStream of ROM
     * @param offset Where to load to
     * @param len How many bytes
     * @throws IOException
     */
    public void load_ROM(InputStream ROM, int offset, int len) throws IOException {
        ROM.read(rom, offset, len);
    }

    /**
     * Directly transfer memory from ROM/RAM to OAM
     * @param base Base address for DMA
     */
    public void DMA(int base){
        for(int i = 0; i < 160; i ++){
            write8(0xfe00 + i, read8(base + i));
        }
    }

    /**
     * Read 1 byte from address
     * @param address Address of byte to read
     * @return The byte at [address]
     */
    public int read8(int address){
        if(!left_bios && address >= 0x100)
            left_bios = true;
        switch(address >> 13){
            case 0:
                if(address < 0x100 && !left_bios)
                    return BIOS[address];
            case 1: //0x0000 - 0x3fff
                return rom[address] & 0xff;
            case 2:
            case 3: //0x4000 - 0x7fff
                return rom[(address & 0x3fff) + (rom_bank << 14)] & 0xff;
            case 4: //0x8000 - 0x9fff
                return machine.gpu.read(address);
            case 5: //0xa000 - 0xbfff
                switch(mbc_type){
                    case 1:
                    case 5: // Just write RAM if it's there
                        if(ram_enabled){
                            int ram_addr = (address & 0x1fff) + (ram_bank << 13);
                            if(ram_addr < ram_size)
                                return external_ram[ram_addr] & 0xff;
                        }
                        return 0;
                    case 2: // Write low 4 bits of "RAM"
                        if(ram_enabled && address < 0xa200){
                            return external_ram[address & 0x1ff] & 0xf;
                        }
                        return 0;
                    case 3: // Either write RAM or set a register
                        switch(mbc3_rtc_register){
                            case 8:
                                return seconds;
                            case 9:
                                return minutes;
                            case 10:
                                return hours;
                            case 11:
                                return days & 0xff;
                            case 12:
                                return (days >> 8) | (mbc3_halt_rtc ? 0x40 : 0);
                        }
                        return 0;
                }
                return 0;
            case 6: //0xc000 - 0xdfff
            case 7: //0xe000 - 0xffff
                if(address < 0xfe00)
                    return internal_ram[address & 0x1fff] & 0xff; // Echo of RAM
                else if(address < 0xfea0){
                    return machine.gpu.read(address);
                }
                else if(address < 0xff00) return 0; // Unusable
                else if(address < 0xff4c){
                    if((address & ~3) == 0xff04)
                        return machine.timer.read(address & 3);
                    else if(address >= 0xff40 && address != 0xff46)
                        return machine.gpu.read(address);
                    else if(address >= 0xff10 && address < 0xff40)
                        return machine.soundBoard.read(address);
                    else {
                        switch (address & 0xff) {
                            case 0x00: // 0xff00 - P1/Keypad
                                return machine.keypad.read();
                            case 0x0f: // 0xff0f - IF
                                return machine.interrupts_fired;
                        }
                    }
                    return 0;
                }
                else if(address < 0xff80) return 0; // Unusable
                else if(address != 0xffff) // Zero-page
                    return zero_page[address & 0x1f] & 0xff;
                else return machine.interrupts_enabled;
        }
        return 0;
    }

    /**
     * Read 1 x 2-byte word from address
     * @param address Address of word (reads address, address + 1)
     * @return 2 bytes at [address, address + 1], little-endian
     */
    public int read16(int address){
        return read8(address) + (read8(address + 1) << 8);
    }

    /**
     * Write one byte to [address]
     * @param address Address to which to write
     * @param value Value to write
     */
    public void write8(int address, int value){
        switch(address >> 13){
            case 0: //0x0000-0x1fff
                switch(mbc_type){
                    case 1: // Enable RAM if low nibble is 0xa, else disable
                    case 3:
                    case 5:
                        ram_enabled = (value & 0xf) == 0xa; break;
                    case 2: // Enable/disable RAM if high address byte is even
                        if(((address >> 8) & 1) == 0)
                            ram_enabled = (value & 0xf) == 0xa;
                        break;
                }
                break;
            case 1: //0x2000-0x3fff
                switch(mbc_type){
                    case 1: // Set ROM bank, or at least lower 5 bits
                        value &= 0x1f;
                        if(value == 0)
                            value = 1;
                        rom_bank &= ~0x1f;
                        rom_bank |= value;
                        break;
                    case 2: // Set ROM bank, but only if high address byte is odd
                        value &= 0xf;
                        if(value == 0)
                            value = 1;
                        if(((address >> 8) & 1) == 1)
                            rom_bank = value;
                        break;
                    case 3: // Set ROM bank
                        value &= 0x7f;
                        if(value == 0)
                            value = 1;
                        rom_bank = value;
                        break;
                    case 5: // Set low 8 bits of ROM bank (allow 0)
                        if((address & 0x1000) == 0) {
                            rom_bank &= ~0xff;
                            rom_bank |= value;
                        }else{
                            rom_bank &= 0xff;
                            rom_bank |= (value & 1) << 8;
                        }
                        break;
                }
                rom_bank %= num_rom_banks; // Ignore pins too high
                break;
            case 2: //0x4000-0x5fff
                switch(mbc_type){
                    case 1: // Write high 2 bits of ROM bank, or RAM bank
                        value &= 0x3;
                        if(mbc1_ram_32k)
                            ram_bank = value;
                        else {
                            rom_bank &= 0x1f;
                            rom_bank |= value << 5;
                        }
                        break;
                    case 3: // Write RAM bank if <=3 or enable RTC registers
                        if(value <= 3) {
                            ram_bank = value;
                            mbc3_rtc_register = 0;
                        }
                        else if(value >= 8 && value <= 0xc)
                            mbc3_rtc_register = value;
                        break;
                    case 5: // Write RAM bank
                        ram_bank = value & 0x3;
                        break;
                }
                ram_bank %= num_ram_banks;
                break;
            case 3: //0x6000 - 0x7fff
                switch(mbc_type){
                    case 1: // Set ROM/RAM mode
                        mbc1_ram_32k = (value & 1) != 0;
                        if(mbc1_ram_32k) {
                            rom_bank &= 0x1f;
                            rom_bank %= num_rom_banks;
                        }
                        else
                            ram_bank = 0;
                        break;
                    case 3: // Latch RTC
                        if((mbc3_rtc_latch & 1) == 0 && value == 0)
                            mbc3_rtc_latch ++;
                        else if((mbc3_rtc_latch & 1) != 0 && value == 1)
                            mbc3_rtc_latch = (mbc3_rtc_latch + 1) & 3;
                        else
                            mbc3_rtc_latch &= ~1;
                        break;
                }
                break;
            case 4: //0x8000 - 0x9fff
                machine.gpu.write(address, value);
                break;
            case 5: //0xa000 - 0xbfff
                switch(mbc_type){
                    case 1:
                    case 5: // Just write RAM if it's there
                        if(ram_enabled){
                            int ram_addr = (address & 0x1fff) + (ram_bank << 13);
                            if(ram_addr < ram_size)
                                external_ram[ram_addr] = (byte)(value & 0xff);
                        }
                        break;
                    case 2: // Write low 4 bits of "RAM"
                        if(ram_enabled && address < 0xa200){
                            external_ram[address & 0x1ff] = (byte)(value & 0xf);
                        }
                        break;
                    case 3: // Either write RAM or set a register
                        switch(mbc3_rtc_register){
                            case 8:
                                seconds = value % 60; break;
                            case 9:
                                minutes = value % 60; break;
                            case 10:
                                hours = value % 24; break;
                            case 11:
                                days &= ~0xff; days |= value; break;
                            case 12:
                                days &= 0xff;
                                days |= (value & 1) << 8;
                                mbc3_halt_rtc = (value & 0x40) != 0;
                                break;
                        }
                        break;
                }
                break;
            case 6: // 0xc000 - 0xdfff Internal RAM
            case 7: // 0xe000 - 0xffff Echo of RAM + later data
                if(address < 0xfe00)
                    internal_ram[address & 0x1fff] = (byte)(value & 0xff);
                else if(address < 0xfea0){
                    machine.gpu.write(address, value);
                }
                else if(address < 0xff00); // Unusable
                else if(address < 0xff4c){
                    if((address & ~3) == 0xff04)
                        machine.timer.write(address & 3, value);
                    else if(address >= 0xff40 && address != 0xff46)
                        machine.gpu.write(address, value);
                    else if(address >= 0xff10 && address < 0xff40)
                        machine.soundBoard.write(address, value);
                    else{
                        switch(address & 0xff){
                            case 0x00: // P1 - Keypad
                                machine.keypad.write(value); break;
                            case 0x0f: // 0xff0f - IF
                                machine.interrupts_fired = value; break;
                            case 0x46: // 0xff46 - DMA
                                DMA(value); break;
                        }
                    }
                }
                else if(address < 0xff80); // Unusable
                else if(address == 0xffff){ // Interrupt enable register
                    machine.interrupts_enabled = value;
                }
                else{ // 0xff80 - 0xfffe, Zero Page Ram
                    zero_page[address & 0x7f] = (byte)(value & 0xff);
                }
                break;
        }
    }

    /**
     * Write 1 x 2-word byte to [address]
     * @param address Address to write (low)
     * @param value Little-endian 2-byte value
     */
    public void write16(int address, int value){
        write8(address, value & 0xff);
        write8(address + 1, value >> 8);
    }

}
