// TODO Not all MBCs are implemented (5 for sure will not work right yet)

package com.funguscow.gb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.stream.Collectors;

/**
 * Handles memory access, bank switching, and memory mapped IO and registers
 */
public class MMU {

    public static final byte[] BIOS_DMG = {
        (byte)0x31, (byte)0xfe, (byte)0xff, (byte)0xaf, (byte)0x21, (byte)0xff, (byte)0x9f, (byte)0x32, (byte)0xcb, (byte)0x7c, (byte)0x20, (byte)0xfb, (byte)0x21, (byte)0x26, (byte)0xff, (byte)0x0e,
        (byte)0x11, (byte)0x3e, (byte)0x80, (byte)0x32, (byte)0xe2, (byte)0x0c, (byte)0x3e, (byte)0xf3, (byte)0xe2, (byte)0x32, (byte)0x3e, (byte)0x77, (byte)0x77, (byte)0x3e, (byte)0xfc, (byte)0xe0,
        (byte)0x47, (byte)0x11, (byte)0x04, (byte)0x01, (byte)0x21, (byte)0x10, (byte)0x80, (byte)0x1a, (byte)0xcd, (byte)0x95, (byte)0x00, (byte)0xcd, (byte)0x96, (byte)0x00, (byte)0x13, (byte)0x7b,
        (byte)0xfe, (byte)0x34, (byte)0x20, (byte)0xf3, (byte)0x11, (byte)0xd8, (byte)0x00, (byte)0x06, (byte)0x08, (byte)0x1a, (byte)0x13, (byte)0x22, (byte)0x23, (byte)0x05, (byte)0x20, (byte)0xf9,
        (byte)0x3e, (byte)0x19, (byte)0xea, (byte)0x10, (byte)0x99, (byte)0x21, (byte)0x2f, (byte)0x99, (byte)0x0e, (byte)0x0c, (byte)0x3d, (byte)0x28, (byte)0x08, (byte)0x32, (byte)0x0d, (byte)0x20,
        (byte)0xf9, (byte)0x2e, (byte)0x0f, (byte)0x18, (byte)0xf3, (byte)0x67, (byte)0x3e, (byte)0x64, (byte)0x57, (byte)0xe0, (byte)0x42, (byte)0x3e, (byte)0x91, (byte)0xe0, (byte)0x40, (byte)0x04,
        (byte)0x1e, (byte)0x02, (byte)0x0e, (byte)0x0c, (byte)0xf0, (byte)0x44, (byte)0xfe, (byte)0x90, (byte)0x20, (byte)0xfa, (byte)0x0d, (byte)0x20, (byte)0xf7, (byte)0x1d, (byte)0x20, (byte)0xf2,
        (byte)0x0e, (byte)0x13, (byte)0x24, (byte)0x7c, (byte)0x1e, (byte)0x83, (byte)0xfe, (byte)0x62, (byte)0x28, (byte)0x06, (byte)0x1e, (byte)0xc1, (byte)0xfe, (byte)0x64, (byte)0x20, (byte)0x06,
        (byte)0x7b, (byte)0xe2, (byte)0x0c, (byte)0x3e, (byte)0x87, (byte)0xe2, (byte)0xf0, (byte)0x42, (byte)0x90, (byte)0xe0, (byte)0x42, (byte)0x15, (byte)0x20, (byte)0xd2, (byte)0x05, (byte)0x20,
        (byte)0x4f, (byte)0x16, (byte)0x20, (byte)0x18, (byte)0xcb, (byte)0x4f, (byte)0x06, (byte)0x04, (byte)0xc5, (byte)0xcb, (byte)0x11, (byte)0x17, (byte)0xc1, (byte)0xcb, (byte)0x11, (byte)0x17,
        (byte)0x05, (byte)0x20, (byte)0xf5, (byte)0x22, (byte)0x23, (byte)0x22, (byte)0x23, (byte)0xc9, (byte)0xce, (byte)0xed, (byte)0x66, (byte)0x66, (byte)0xcc, (byte)0x0d, (byte)0x00, (byte)0x0b,
        (byte)0x03, (byte)0x73, (byte)0x00, (byte)0x83, (byte)0x00, (byte)0x0c, (byte)0x00, (byte)0x0d, (byte)0x00, (byte)0x08, (byte)0x11, (byte)0x1f, (byte)0x88, (byte)0x89, (byte)0x00, (byte)0x0e,
        (byte)0xdc, (byte)0xcc, (byte)0x6e, (byte)0xe6, (byte)0xdd, (byte)0xdd, (byte)0xd9, (byte)0x99, (byte)0xbb, (byte)0xbb, (byte)0x67, (byte)0x63, (byte)0x6e, (byte)0x0e, (byte)0xec, (byte)0xcc,
        (byte)0xdd, (byte)0xdc, (byte)0x99, (byte)0x9f, (byte)0xbb, (byte)0xb9, (byte)0x33, (byte)0x3e, (byte)0x3c, (byte)0x42, (byte)0xb9, (byte)0xa5, (byte)0xb9, (byte)0xa5, (byte)0x42, (byte)0x3c,
        (byte)0x21, (byte)0x04, (byte)0x01, (byte)0x11, (byte)0xa8, (byte)0x00, (byte)0x1a, (byte)0x13, (byte)0xbe, (byte)0x20, (byte)0xfe, (byte)0x23, (byte)0x7d, (byte)0xfe, (byte)0x34, (byte)0x20,
        (byte)0xf5, (byte)0x06, (byte)0x19, (byte)0x78, (byte)0x86, (byte)0x23, (byte)0x05, (byte)0x20, (byte)0xfb, (byte)0x86, (byte)0x20, (byte)0xfe, (byte)0x3e, (byte)0x01, (byte)0xe0, (byte)0x50};

    Machine machine;
    private int ram_size;
    byte[] rom;
    byte[] internal_ram;
    byte[] external_ram;
    byte[] zero_page;
    int rom_bank;
    private int num_rom_banks;
    private int ram_bank;
    private int num_ram_banks;
    private int mbc_type;
    private boolean mbc1_bank_mode;
    private boolean ram_enabled;
    private int mbc3_rtc_register;
    private int mbc3_rtc_latch;
    private int seconds;
    private int minutes;
    private int hours;
    private int days;
    private boolean mbc3_halt_rtc, mbc3_days_overflow;
    public boolean left_bios = false;

    private boolean cgb;

    /**
     * Initialize according to power up seqeunce
     * @param machine Parent machine
     * @param mbc_type Cartridge type
     * @param num_rom_banks Number of ROM banks
     * @param num_ram_banks Number of RAM banks
     * @param ram_size Size in bytes of RAM
     */
    public MMU(Machine machine, int mbc_type, int num_rom_banks, int num_ram_banks, int ram_size, boolean cgb){
        System.out.printf("Initialize MMU with MBC: %x, %d ROM and %d RAM\n", mbc_type, num_rom_banks, num_ram_banks);
        this.machine = machine;
        this.mbc_type = mbc_type;
        this.num_rom_banks = num_rom_banks;
        this.num_ram_banks = num_ram_banks;
        this.ram_size = ram_size;
        this.cgb = cgb;
        rom = new byte[num_rom_banks * 0x4000];
        rom_bank = 1;
        ram_bank = 0;
        external_ram = new byte[ram_size];
        internal_ram = new byte[0x2000];
        zero_page = new byte[128];
        // Startup sequence
        write8(0xff00, 0xCF);
        write8(0xff02, 0x7E);
        machine.timer.divider = 0xAB;
        write8(0xff06, 0);
        write8(0xff07, 0xF8);
        write8(0xff0f, 0xE1);
        /* TODO write initial sound values */
        write8(0xff40, 0x91);
        write8(0xff41, 0x85);
        machine.gpu.init_state();
        write8(0xff42, 0);
        write8(0xff43, 0);
        write8(0xff44, 0);
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
     * @throws IOException From inner read
     */
    public void load_ROM(InputStream ROM, int offset, int len) throws IOException {
        ROM.read(rom, offset, len);
    }

    /**
     * Load the external RAM from a byte array
     * @param RAM Source
     * @param offset offset into external ram
     * @param len number of bytes
     */
    public void load_RAM(byte[] RAM, int offset, int len) {
        System.arraycopy(RAM, 0, external_ram, offset, len);
    }

    /**
     * Load external RAM from stream
     * @param RAM source
     * @param offset offset into ram
     * @param len number of bytes
     * @throws IOException from inner read
     */
    public void load_RAM(InputStream RAM, int offset, int len) throws IOException {
        RAM.read(external_ram, offset, len);
    }

    /**
     * Save external RAM to another array
     * @param RAM destination
     * @param dstOffset offset in RAM
     * @param srcOffset offset from external ram
     * @param len length in bytes
     */
    public void save_RAM(byte[] RAM, int dstOffset, int srcOffset, int len) {
        System.arraycopy(external_ram, srcOffset, RAM, dstOffset, len);
    }

    /**
     * Save external RAM to a stream
     * @param RAM destination
     * @param offset offset in self
     * @param len length in bytes
     * @throws IOException from inner read
     */
    public void save_RAM(OutputStream RAM, int offset, int len) throws IOException {
        RAM.write(external_ram, offset, len);
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
        switch(address >> 13){
            case 0:
                if(address < 0x100 && !left_bios)
                    return machine.mode.BIOS[address] & 0xff;
                if (machine.mode.isCgb && address >= 0x200 && address < 0x900 && !left_bios && machine.mode.BIOS.length > 0x100)
                    return machine.mode.BIOS[address - 0x100] & 0xff;
            case 1: //0x0000 - 0x3fff
                switch (mbc_type) {
                    case 1:
                        if (mbc1_bank_mode)
                            return rom[address | ((rom_bank & ~0x1f) << 14)] & 0xff;
                    default:
                        return rom[address] & 0xff;
                }
            case 2:
            case 3: //0x4000 - 0x7fff
                return rom[(address & 0x3fff) | (rom_bank << 14)] & 0xff;
            case 4: //0x8000 - 0x9fff
                return machine.gpu.read(address);
            case 5: //0xa000 - 0xbfff
                switch(mbc_type){
                    case 1:
                        if(ram_enabled){
                            int erb = mbc1_bank_mode ? ram_bank : 0;
                            int ram_addr = (address & 0x1fff) | (erb << 13);
                            if(ram_addr < ram_size)
                                return external_ram[ram_addr] & 0xff;
                        }
                        return 0xff;
                    case 2: // Write low 4 bits of "RAM"
                        if(ram_enabled){
                            return 0xf0 | (external_ram[address & 0x1ff] & 0xf);
                        }
                        return 0xff;
                    case 3: // Either write RAM or set a register
                        switch(mbc3_rtc_register){
                            case 0:
                                return external_ram[(ram_bank << 13) | (address & 0x1fff)] & 0xff;
                            case 8:
                                return seconds;
                            case 9:
                                return minutes;
                            case 10:
                                return hours;
                            case 11:
                                return days & 0xff;
                            case 12:
                                return (days >> 8) | (mbc3_halt_rtc ? 0x40 : 0) | (mbc3_days_overflow ? 0x80 : 0);
                        }
                        return 0xff;
                    case 5:
                        if (ram_enabled) {
                            return external_ram[(address & 0x1fff) | (ram_bank << 13)] & 0xff;
                        }
                }
                return 0xff;
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
                    return 0xff;
                }
                else if(address < 0xff80) return 0xff; // Unusable
                else if(address != 0xffff) // Zero-page
                    return zero_page[address & 0x7f] & 0xff;
                else return machine.interrupts_enabled;
        }
        return 0xff;
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
        // For debugging serial IO ouput
//        if (address == 0xff01) {
//            char c = (value == ' ') ? '\n' : (char)value;
//            System.out.print(c);
//        }
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
                        else {
                            value &= 0xf;
                            if(value == 0)
                                value = 1;
                            rom_bank = value;
                        }
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
                        if(((address >> 8) & 1) == 0)
                            ram_enabled = (value & 0xf) == 0xa;
                        else {
                            value &= 0xf;
                            if(value == 0)
                                value = 1;
                            rom_bank = value;
                        }
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
                        if(ram_enabled && mbc1_bank_mode)
                            ram_bank = value;
                        rom_bank &= 0x1f;
                        rom_bank |= value << 5;
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
                        ram_bank = value & 0xf;
                        break;
                }
                rom_bank %= num_rom_banks;
                ram_bank %= Math.max(1, num_ram_banks);
                break;
            case 3: //0x6000 - 0x7fff
                switch(mbc_type){
                    case 1: // Set ROM/RAM mode
                        mbc1_bank_mode = (value & 1) != 0;
                        break;
                    case 3: // Latch RTC
                        if((mbc3_rtc_latch & 1) == 0 && value == 0)
                            mbc3_rtc_latch ++;
                        else if((mbc3_rtc_latch & 1) != 0 && value == 1)
                            mbc3_rtc_latch = (mbc3_rtc_latch + 1) & 3;
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
                            int erb = mbc1_bank_mode ? ram_bank : 0;
                            int ram_addr = (address & 0x1fff) + (erb << 13);
                            if(ram_addr < ram_size)
                                external_ram[ram_addr] = (byte)(value & 0xff);
                        }
                        break;
                    case 2: // Write low 4 bits of "RAM"
                        if(ram_enabled){
                            external_ram[address & 0x1ff] = (byte)(value & 0xf);
                        }
                        break;
                    case 3: // Either write RAM or set a register
                        switch(mbc3_rtc_register){
                            case 0:
                                external_ram[(ram_bank << 13) + (address & 0x1fff)] = (byte)(value & 0xff); break;
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
                                mbc3_days_overflow = (value & 0x80) != 0;
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
                                DMA(value << 8); break;
                        }
                    }
                }
                else if (address == 0xff50) {
                    if (value != 0) {
                        left_bios = true;
                        System.out.println(machine.cpu.calledOps.stream().sorted().map(Integer::toHexString).collect(Collectors.joining(", ")));
                        System.out.println("Line on leave bios is " + machine.gpu.line);
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
