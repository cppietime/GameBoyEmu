// TODO Not all MBCs are implemented (5 for sure will not work right yet)

package com.funguscow.gb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
    private int ramSize;
    byte[] rom;
    byte[] internalRam;
    byte[] externalRam;
    byte[] zeroPage;
    int romBank;
    private int numRomBanks;
    private int ramBank;
    private int numRamBanks;
    private int mbcType;
    private boolean mbc1BankMode;
    private boolean ramEnabled;
    private int mbc3RtcRegister;
    private int mbc3RtcLatch;
    private int seconds;
    private int minutes;
    private int hours;
    private int days;
    private boolean mbc3HaltRtc, mbc3DaysOverflow;
    public boolean leftBios = false;

    // CGB only stuff
    private boolean cgb;
    private int wramBank = 1;
    boolean pendingSpeedSwitch;
    private int hdmaSource;
    private int hdmaDest;
    private int hdmaRemaining;
    private int hdmaProgress;
    private boolean hdmaActive;

    /**
     * Initialize according to power up seqeunce
     * @param machine Parent machine
     * @param mbcType Cartridge type
     * @param numRomBanks Number of ROM banks
     * @param numRamBanks Number of RAM banks
     * @param ramSize Size in bytes of RAM
     */
    public MMU(Machine machine, int mbcType, int numRomBanks, int numRamBanks, int ramSize, boolean cgb){
        System.out.printf("Initialize MMU with MBC: %x, %d ROM and %d RAM\n", mbcType, numRomBanks, numRamBanks);
        this.machine = machine;
        this.mbcType = mbcType;
        this.numRomBanks = numRomBanks;
        this.numRamBanks = numRamBanks;
        this.ramSize = ramSize;
        this.cgb = cgb;
        rom = new byte[numRomBanks * 0x4000];
        romBank = 1;
        ramBank = 0;
        externalRam = new byte[ramSize];
        internalRam = new byte[cgb ? 0x8000 : 0x2000];
        zeroPage = new byte[128];
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
        machine.gpu.initState();
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
        System.out.println(ramSize + " byte of RAM across " + numRamBanks + " banks");
        System.out.println(numRomBanks + " banks of ROM using MBC #" + mbcType);
    }

    /**
     * Load from an arry
     * @param ROM Array of bytes  of ROM data
     * @param offset WHere to load to
     * @param len How many bytes to load
     */
    public void loadRom(byte[] ROM, int offset, int len){
        System.arraycopy(ROM, 0, rom, offset, len);
    }

    /**
     * Load from an input stream
     * @param ROM InputStream of ROM
     * @param offset Where to load to
     * @param len How many bytes
     * @throws IOException From inner read
     */
    public void loadRom(InputStream ROM, int offset, int len) throws IOException {
        ROM.read(rom, offset, len);
    }

    /**
     * Load the external RAM from a byte array
     * @param RAM Source
     * @param offset offset into external ram
     * @param len number of bytes
     */
    public void loadRam(byte[] RAM, int offset, int len) {
        System.arraycopy(RAM, 0, externalRam, offset, len);
    }

    /**
     * Load external RAM from stream
     * @param RAM source
     * @param offset offset into ram
     * @param len number of bytes
     * @throws IOException from inner read
     */
    public void loadRam(InputStream RAM, int offset, int len) throws IOException {
        RAM.read(externalRam, offset, len);
    }

    /**
     * Save external RAM to another array
     * @param RAM destination
     * @param dstOffset offset in RAM
     * @param srcOffset offset from external ram
     * @param len length in bytes
     */
    public void saveRam(byte[] RAM, int dstOffset, int srcOffset, int len) {
        System.arraycopy(externalRam, srcOffset, RAM, dstOffset, len);
    }

    /**
     * Save external RAM to a stream
     * @param RAM destination
     * @param offset offset in self
     * @param len length in bytes
     * @throws IOException from inner read
     */
    public void saveRam(OutputStream RAM, int offset, int len) throws IOException {
        RAM.write(externalRam, offset, len);
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

    public void startHDMA(boolean hblank, int size) {
        if (hblank) {
            hdmaRemaining = (size + 1) << 4;
            hdmaProgress = 0;
            hdmaActive = true;
        } else {
            size = (size + 1) << 4;
            for (int i = 0; i < size; i++) {
                int src = read8(hdmaSource + i);
                write8(0x8000 + hdmaDest + i, src);
            }
        }
    }

    public void onHblank() {
        if (!hdmaActive) {
            return;
        }
        for (int i = 0; i < 0x10; i++) {
            int src = read8(hdmaSource + (hdmaProgress << 4) + i);
            write8(0x8000 + hdmaDest + (hdmaProgress << 4) + i, src);
        }
        hdmaProgress++;
        if (--hdmaRemaining == 0) {
            hdmaActive = false;
            hdmaProgress = 0xff;
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
                if(address < 0x100 && !leftBios) {
                    return machine.mode.BIOS[address] & 0xff;
                }
                if (machine.mode.isCgb && address >= 0x200 && address < 0x900 && !leftBios && machine.mode.BIOS.length > 0x100) {
                    return machine.mode.BIOS[address - 0x100] & 0xff;
                }
            case 1: //0x0000 - 0x3fff
                switch (mbcType) {
                    case 1:
                        if (mbc1BankMode) {
                            return rom[address | ((romBank & ~0x1f) << 14)] & 0xff;
                        }
                    default:
                        return rom[address] & 0xff;
                }
            case 2:
            case 3: //0x4000 - 0x7fff
                return rom[(address & 0x3fff) | (romBank << 14)] & 0xff;
            case 4: //0x8000 - 0x9fff
                return machine.gpu.read(address);
            case 5: //0xa000 - 0xbfff
                switch(mbcType){
                    case 1:
                        if(ramEnabled){
                            int erb = mbc1BankMode ? ramBank : 0;
                            int ramAddr = (address & 0x1fff) | (erb << 13);
                            if(ramAddr < ramSize) {
                                return externalRam[ramAddr] & 0xff;
                            }
                        }
                        return 0xff;
                    case 2: // Write low 4 bits of "RAM"
                        if(ramEnabled){
                            return 0xf0 | (externalRam[address & 0x1ff] & 0xf);
                        }
                        return 0xff;
                    case 3: // Either write RAM or set a register
                        switch(mbc3RtcRegister){
                            case 0:
                                return externalRam[(ramBank << 13) | (address & 0x1fff)] & 0xff;
                            case 8:
                                return seconds;
                            case 9:
                                return minutes;
                            case 10:
                                return hours;
                            case 11:
                                return days & 0xff;
                            case 12:
                                return (days >> 8) | (mbc3HaltRtc ? 0x40 : 0) | (mbc3DaysOverflow ? 0x80 : 0);
                        }
                        return 0xff;
                    case 5:
                        if (ramEnabled) {
                            return externalRam[(address & 0x1fff) | (ramBank << 13)] & 0xff;
                        }
                }
                return 0xff;
            case 6: //0xc000 - 0xdfff
            case 7: //0xe000 - 0xffff
                if(address < 0xfe00) {
                    address &= 0x1fff;
                    if (cgb && address >= 0x1000) {
                        address = (wramBank << 12) | (address & 0xfff);
                    }
                    return internalRam[address] & 0xff; // Echo of RAM
                }
                else if(address < 0xfea0){
                    return machine.gpu.read(address);
                }
                else if(address < 0xff00) return 0xff; // Unusable
                else if(address < 0xff50){
                    if((address & ~3) == 0xff04) {
                        return machine.timer.read(address & 3);
                    }
                    else if (address == 0xff4d && cgb) {
                        return 0x7e | (machine.doubleSpeed ? 0x80 : 0) | (pendingSpeedSwitch ? 1 : 0);
                    }
                    else if(address >= 0xff40 && address != 0xff46) {
                        return machine.gpu.read(address);
                    }
                    else if(address >= 0xff10 && address < 0xff40) {
                        return machine.soundBoard.read(address);
                    }
                    else {
                        switch (address & 0xff) {
                            case 0x00: // 0xff00 - P1/Keypad
                                return machine.keypad.read();
                            case 0x0f: // 0xff0f - IF
                                return machine.interruptsFired;
                        }
                    }
                    return 0xff;
                }
                else if (address == 0xff55 && cgb) {
                    return (hdmaActive ? 0x80 : 0) | ((hdmaProgress >> 4) - 1);
                }
                else if (address < 0xff68) {
                    return 0xff;
                }
                else if (address < 0xff70 && cgb) { // 0xFF68 - 0xFF6F, CGB palette info
                    return machine.gpu.read(address);
                }
                else if (address == 0xff70 && cgb) {
                    return wramBank | 0xf8;
                }
                else if(address < 0xff80) {
                    return 0xff;
                }
                else if(address != 0xffff) { // Zero-page
                    return zeroPage[address & 0x7f] & 0xff;
                }
                else {
                    return machine.interruptsEnabled;
                }
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
        switch(address >> 13){
            case 0: //0x0000-0x1fff
                switch(mbcType){
                    case 1: // Enable RAM if low nibble is 0xa, else disable
                    case 3:
                    case 5:
                        ramEnabled = (value & 0xf) == 0xa; break;
                    case 2: // Enable/disable RAM if high address byte is even
                        if(((address >> 8) & 1) == 0) {
                            ramEnabled = (value & 0xf) == 0xa;
                        }
                        else {
                            value &= 0xf;
                            if(value == 0) {
                                value = 1;
                            }
                            romBank = value;
                        }
                        break;
                }
                break;
            case 1: //0x2000-0x3fff
                switch(mbcType){
                    case 1: // Set ROM bank, or at least lower 5 bits
                        value &= 0x1f;
                        if(value == 0) {
                            value = 1;
                        }
                        romBank &= ~0x1f;
                        romBank |= value;
                        break;
                    case 2: // Set ROM bank, but only if high address byte is odd
                        if(((address >> 8) & 1) == 0)
                            ramEnabled = (value & 0xf) == 0xa;
                        else {
                            value &= 0xf;
                            if(value == 0) {
                                value = 1;
                            }
                            romBank = value;
                        }
                        break;
                    case 3: // Set ROM bank
                        value &= 0x7f;
                        if(value == 0) {
                            value = 1;
                        }
                        romBank = value;
                        break;
                    case 5: // Set low 8 bits of ROM bank (allow 0)
                        if((address & 0x1000) == 0) {
                            romBank &= ~0xff;
                            romBank |= value;
                        }else{
                            romBank &= 0xff;
                            romBank |= (value & 1) << 8;
                        }
                        break;
                }
                romBank %= numRomBanks; // Ignore pins too high
                break;
            case 2: //0x4000-0x5fff
                switch(mbcType){
                    case 1: // Write high 2 bits of ROM bank, or RAM bank
                        value &= 0x3;
                        if(ramEnabled && mbc1BankMode) {
                            ramBank = value;
                        }
                        romBank &= 0x1f;
                        romBank |= value << 5;
                        break;
                    case 3: // Write RAM bank if <=3 or enable RTC registers
                        if(value <= 3) {
                            ramBank = value;
                            mbc3RtcRegister = 0;
                        }
                        else if(value >= 8 && value <= 0xc) {
                            mbc3RtcRegister = value;
                        }
                        break;
                    case 5: // Write RAM bank
                        ramBank = value & 0xf;
                        break;
                }
                romBank %= numRomBanks;
                ramBank %= Math.max(1, numRamBanks);
                break;
            case 3: //0x6000 - 0x7fff
                switch(mbcType){
                    case 1: // Set ROM/RAM mode
                        mbc1BankMode = (value & 1) != 0;
                        break;
                    case 3: // Latch RTC
                        if((mbc3RtcLatch & 1) == 0 && value == 0) {
                            mbc3RtcLatch++;
                        }
                        else if((mbc3RtcLatch & 1) != 0 && value == 1) {
                            mbc3RtcLatch = (mbc3RtcLatch + 1) & 3;
                        }
                        break;
                }
                break;
            case 4: //0x8000 - 0x9fff
                machine.gpu.write(address, value);
                break;
            case 5: //0xa000 - 0xbfff
                switch(mbcType){
                    case 1:
                    case 5: // Just write RAM if it's there
                        if(ramEnabled){
                            int erb = mbc1BankMode ? ramBank : 0;
                            int ramAddr = (address & 0x1fff) + (erb << 13);
                            if(ramAddr < ramSize) {
                                externalRam[ramAddr] = (byte) (value & 0xff);
                            }
                        }
                        break;
                    case 2: // Write low 4 bits of "RAM"
                        if(ramEnabled){
                            externalRam[address & 0x1ff] = (byte)(value & 0xf);
                        }
                        break;
                    case 3: // Either write RAM or set a register
                        switch(mbc3RtcRegister){
                            case 0:
                                externalRam[(ramBank << 13) + (address & 0x1fff)] = (byte)(value & 0xff); break;
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
                                mbc3HaltRtc = (value & 0x40) != 0;
                                mbc3DaysOverflow = (value & 0x80) != 0;
                                break;
                        }
                        break;
                }
                break;
            case 6: // 0xc000 - 0xdfff Internal RAM
            case 7: // 0xe000 - 0xffff Echo of RAM + later data
                if(address < 0xfe00) {
                    address &= 0x1fff;
                    if (cgb && address >= 0x1000) {
                        address = (wramBank << 12) | (address & 0xfff);
                    }
                    internalRam[address] = (byte) (value & 0xff);
                }
                else if(address < 0xfea0){
                    machine.gpu.write(address, value);
                }
                else if(address < 0xff00) {} // Unusable
                else if(address < 0xff50){
                    if((address & ~3) == 0xff04)
                        machine.timer.write(address & 3, value);
                    else if (address == 0xff4d) {
                        pendingSpeedSwitch = (value & 1) != 0;
                    }
                    else if(address >= 0xff40 && address != 0xff46) {
                        machine.gpu.write(address, value);
                    }
                    else if(address >= 0xff10 && address < 0xff40) {
                        machine.soundBoard.write(address, value);
                    }
                    else{
                        switch(address & 0xff){
                            case 0x00: // P1 - Keypad
                                machine.keypad.write(value); break;
                            case 0x0f: // 0xff0f - IF
                                machine.interruptsFired = value; break;
                            case 0x46: // 0xff46 - DMA
                                DMA(value << 8); break;
                        }
                    }
                }
                else if (address == 0xff50) {
                    if (value != 0) {
                        leftBios = true;
                    }
                }
                else if (address == 0xff51 && cgb) {
                    hdmaSource &= 0xff;
                    hdmaSource |= value << 8;
                }
                else if (address == 0xff52 && cgb) {
                    hdmaSource &= ~0xff;
                    hdmaSource |= value & 0xf0;
                }
                else if (address == 0xff53 && cgb) {
                    hdmaDest &= 0xff;
                    hdmaDest |= (value << 8) & 0x1f00;
                }
                else if (address == 0xff54 && cgb) {
                    hdmaDest &= ~0xff;
                    hdmaDest |= value & 0xf0;
                }
                else if (address == 0xff55 && cgb) {
                    boolean hblank = (value & 0x80) != 0;
                    if (!hblank && hdmaActive) {
                        hdmaActive = false;
                    } else {
                        startHDMA(hblank, value & 0x7f);
                    }
                }
                else if (address < 0xff68) {} // Unused
                else if (address < 0xff70 && cgb) { // 0xFF68 - 0xFF6F, CGB stuff
                    machine.gpu.write(address, value);
                }
                else if (address == 0xff70 && cgb) {
                    wramBank = value & 7;
                }
                else if(address < 0xff80) {} // Unusable
                else if(address == 0xffff){ // Interrupt enable register
                    machine.interruptsEnabled = value;
                }
                else{ // 0xff80 - 0xfffe, Zero Page Ram
                    zeroPage[address & 0x7f] = (byte)(value & 0xff);
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
