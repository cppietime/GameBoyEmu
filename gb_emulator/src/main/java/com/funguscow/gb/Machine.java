package com.funguscow.gb;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents the machine as a whole, holds certain registers
 */
public class Machine {

    /**
     * Thrown for exceptions in ROM file processing
     */
    public static class RomException extends Exception {
        public RomException(Exception e) {
            super(e);
        }
        public RomException(String msg) {
            super(msg);
        }
    }

    public static final int[] RAM_SIZES = {0, 1 << 11, 1 << 13, 1 << 15, 1 << 17};

    /**
     * Specifies color mode and BIOS
     */
    public enum MachineMode{
        GAMEBOY(1, false, BootRoms.BIOS_DMG),
        GAMEBOY_POCKET(1, false, BootRoms.BIOS_DMG),
        GAMEBOY_COLOR(0x11, true, BootRoms.BIOS_CGB);

        public final int afInitial;
        public final boolean isCgb;
        public final byte[] BIOS;

        MachineMode(int afInitial, boolean isCgb, byte[] BIOS){
            this.afInitial = afInitial;
            this.isCgb = isCgb;
            this.BIOS = BIOS;
        }
    }

    private long cyclesExecuted;
    private final ReentrantLock mutex = new ReentrantLock();

    // All these things are accessed by each other
    CPU cpu;
    MMU mmu;
    GPU gpu;
    Timer timer;
    Keypad keypad;
    SoundBoard soundBoard;
    final Scheduler scheduler = new Scheduler();

    boolean halt;
    boolean stop;

    int interruptsEnabled;
    int interruptsFired;

    /**
     * Speeds up emulation by the specified factor
     * Must never be 0!!
     */
    public int speedUp = 1;

    MachineMode mode;

    File saveFile;

    // CGB stuff
    boolean doubleSpeed;
    private boolean usingColor;
    private boolean monochromeCompatibility;

    private String baseNamePath;

    private static String saveExtension(File ROM) {
        String romPath = ROM.getPath();
        int dot = romPath.lastIndexOf('.');
        String savePath;
        if (dot == -1) {
            savePath = romPath + ".ram";
        } else {
            savePath = romPath.substring(0, dot) + ".ram";
        }
        return savePath;
    }

    /**
     * Create a machine with a loaded ROM
     * @param ROM ROM file
     * @param mode Machine mode to boot into
     */
    public Machine(File ROM, MachineMode mode) throws RomException {
        this(ROM, mode, new File(saveExtension(ROM)));
    }

    /**
     * Create a machine with a loaded ROM
     * @param ROM ROM file
     * @param mode Machine mode to boot into
     * @param saveFile File to save external RAM to (or NULL)
     */
    public Machine(File ROM, MachineMode mode, File saveFile) throws RomException {
        baseNamePath = saveFile.getPath();
        timer = new Timer(this);
        keypad = new Keypad(this);
        soundBoard = new SoundBoard(this); // Not yet used
        this.saveFile = saveFile;
        this.mode = mode;
        byte[] header = new byte[0x150];
        try (FileInputStream fis = new FileInputStream(ROM)) {
            // Get the header information of the ROM and use it to determine MBC type, RAM/ROM size
            int read = fis.read(header, 0, 0x150);
            if(read < 0x150) {
                throw new RomException("ROM file too small to be valid!");
            }
            int cartridgeType = header[0x147] & 0xff;
            int colorMode = header[0x143] & 0xff;
            usingColor = (mode.isCgb && (colorMode & 0x6) == 0);
            monochromeCompatibility = usingColor && (colorMode & 0x80) == 0;
            int mbc = 0;
            int romBanks;
            int ramSize = 0;
            if(header[0x149] != 0){
                ramSize = RAM_SIZES[header[0x149]];
            }
            System.out.printf("Cartridge type = %02x, ramkey = %02x, Color? %s (Compatibility? %s)\n", cartridgeType, header[0x149], usingColor, monochromeCompatibility);
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
            gpu = new GPU(this, usingColor, monochromeCompatibility);
            mmu = new MMU(this, mbc, romBanks, (ramSize + 0x1fff) >> 13, ramSize, usingColor);
            // Load this ROM file into it
            mmu.loadRom(header, 0, 0x150);
            mmu.loadRom(fis,  0x150, (romBanks << 14) - 0x150);
//            try {
//                loadExternal();
//            } catch (RomException re) {
//                System.err.println("Could not load save file: ");
//                re.printStackTrace();
//            }
        }catch(Exception e){
            throw new RomException(e);
        }
        cpu = new CPU(mode, mmu, null, null, true);
    }

    /**
     *
     * @return A base path that can be used for save states, based on the
     * path of the ROM loaded
     */
    public String getBaseNamePath() {
        return baseNamePath;
    }

    /**
     *
     * @return true if speed switch succeeded
     */
    public boolean trySpeedSwitch() {
        if (usingColor) {
            doubleSpeed = !doubleSpeed;
            stop = false;
            return true;
        }
        return false;
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

        // Skip to next event if halted
        if (halt && !scheduler.isEmpty() && (soundBoard.silent || soundBoard.speaker == null)) {
            try {
                cyclesExecuted = scheduler.skip(cyclesExecuted);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        int mCycles = cpu.performOp(this); // Execute an opcode after checking for interrupts

        cyclesExecuted += mCycles;
        try {
            mutex.lock();
            scheduler.update(mCycles);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mutex.unlock();
        }
    }

    /**
     * Used for debugging, prints state info
     */
    public void printDebugState() {
        System.out.printf("Halt? %s, Stop? %s, Double Speed? %s\n", halt, stop, doubleSpeed);
        System.out.printf("Interrupts - enabled: 0x%08x, fired: 0x%08x\n", interruptsEnabled, interruptsFired);
        System.out.println("\nCPU:");
        cpu.dumpRegisters();
        System.out.println("\nMMU:");
        mmu.printDebugState();
        System.out.println("\nGPU:");
        gpu.printDebugState();
        System.out.println("\nTimer:");
        timer.printDebugState();
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

    /**
     *
     * @param screen Screen to attach to the GPU
     */
    public void attachScreen(GPU.GameboyScreen screen) {
        this.gpu.screen = screen;
    }

    /**
     *
     * @param speaker Speaker to attach to the SoundBoard
     */
    public void attachSpeaker(SoundBoard.Speaker speaker) {
        soundBoard.speaker = speaker;
        soundBoard.setSpeaker(speaker);
    }

    /**
     *
     * @return Exposes the MMU, used for cheats
     */
    public MMU getMmu() {
        return mmu;
    }

    /**
     *
     * @return The attached keypad
     */
    public Keypad getKeypad() {
        return keypad;
    }

    /**
     * Exposed so the frontend can adjust palettes
     * @return The palette used when NOT in color mode
     */
    public int[] getDmgPalette() {
        return gpu.grayPalette;
    }

    /**
     * Exposed so the frontend can adjust palettes
     * @return The color palette used for background in CGB mode
     */
    public int[] getCgbBgPalette() {
        return gpu.bgPalColor;
    }

    /**
     * Exposed so the frontend can adjust palettes
     * @return The color palette used for sprites in CGB mode
     */
    public int[] getCgbObPalette() {
        return gpu.obPalColor;
    }

    public long getCyclesExecuted() {
        return cyclesExecuted;
    }

    /**
     * Sets the mute status of the SoundBoard
     * @param muted True if the SoundBoard should be muted
     */
    public void mute(boolean muted) {
        this.soundBoard.mute(muted);
    }

    /**
     * Called when any pending tasks MUST be totally wiped out
     */
    private void cancelComponentSchedules() {
        scheduler.clear();
        timer.invalidateTasks();
        gpu.invalidateTasks();
        soundBoard.invalidateTasks();
    }

    /**
     * Save the machine state
     * @param os Destination stream
     * @throws RomException Error writing state
     */
    public void saveState(OutputStream os) throws RomException {
        try (DataOutputStream dos = new DataOutputStream(os)) {
            dos.write("STAT".getBytes(StandardCharsets.UTF_8));
            dos.writeBoolean(usingColor);
            dos.writeBoolean(monochromeCompatibility);
            dos.writeBoolean(halt);
            dos.writeBoolean(stop);
            dos.writeInt(interruptsEnabled);
            dos.writeInt(interruptsFired);
            dos.writeBoolean(doubleSpeed);
            mutex.lock();
            cpu.save(dos);
            mmu.saveState(dos);
            gpu.save(dos);
            timer.save(dos);
            keypad.save(dos);
            soundBoard.save(dos);
            dos.write("end ".getBytes(StandardCharsets.UTF_8));
            dos.flush();
        } catch (Exception e) {
            throw new RomException(e);
        } finally {
            mutex.unlock();
        }
    }

    /**
     * Load machine state
     * @param is Source stream
     * @throws RomException Error reading state
     */
    public void loadState(InputStream is) throws RomException {
        try (DataInputStream dis = new DataInputStream(is)) {
            byte[] buffer = new byte[4];
            dis.read(buffer);
            String key = new String(buffer, StandardCharsets.UTF_8);
            if (!key.equals("STAT")) {
                throw new RomException("Not a state file");
            }
            if (usingColor != dis.readBoolean()) {
                throw new RomException("Color modes do not match");
            }
            if (monochromeCompatibility != dis.readBoolean()) {
                throw new RomException("Compatibility modes do not match");
            }
            mutex.lock();
            cancelComponentSchedules();
            halt = dis.readBoolean();
            stop = dis.readBoolean();
            interruptsEnabled = dis.readInt();
            interruptsFired = dis.readInt();
            doubleSpeed = dis.readBoolean();
            boolean reading = true;
            while (reading) {
                if (dis.read(buffer) < 4) {
                    break;
                }
                key = new String(buffer, StandardCharsets.UTF_8);
                switch (key) {
                    case "end ":
                        reading = false;
                        break;
                    case "MMU ":
                        mmu.loadState(dis);
                        break;
                    case "CPU ":
                        cpu.load(dis);
                        break;
                    case "GPU ":
                        gpu.load(dis);
                        break;
                    case "TIME":
                        timer.load(dis);
                        break;
                    case "APU ":
                        soundBoard.load(dis);
                        break;
                    case "JOYP":
                        keypad.load(dis);
                        break;
                    default:
                        throw new RomException(String.format("Unidentified key %s", key));
                }
            }
        } catch (Exception e) {
            throw new RomException(e);
        } finally {
            mutex.unlock();
        }
    }

    /**
     * Save external RAM (e.g. save file)
     * @param os Destination stream
     * @throws RomException Error writing save
     */
    public void saveExternal(OutputStream os) throws RomException {
        try (DataOutputStream dos = new DataOutputStream(os)) {
            mutex.lock();
            dos.write("SAVE".getBytes(StandardCharsets.UTF_8));
            mmu.saveExternal(dos);
            dos.flush();
        } catch (Exception e) {
            throw new RomException(e);
        } finally {
            mutex.unlock();
        }
    }

    /**
     * Load external RAM (e.g. save file)
     * @param is Source stream
     * @throws RomException Error reading save
     */
    public void loadExternal(InputStream is) throws RomException {
        try (DataInputStream dis = new DataInputStream(is)) {
            mutex.lock();
            byte[] buffer = new byte[4];
            dis.read(buffer);
            String key = new String(buffer, StandardCharsets.UTF_8);
            if(!key.equals("SAVE")) {
                throw new RomException(String.format("Bad key: %s, Not a save file", key));
            }
            cancelComponentSchedules();
            mmu.loadExternal(dis);
        } catch (Exception e) {
            throw new RomException(e);
        } finally {
            mutex.unlock();
        }
    }

    /**
     * Save state to default save file
     * @throws RomException Errors writing save
     */
    public void saveExternal() throws RomException {
        if (saveFile == null) {
            throw new IllegalStateException("No save file specified");
        }
        try (OutputStream os = new FileOutputStream(saveFile)) {
            saveExternal(os);
        } catch (Exception e) {
            throw new RomException(e);
        }
    }

    /**
     * Loads save from default file
     * @throws RomException Errors reading save
     */
    public void loadExternal() throws RomException {
        if (saveFile == null || !saveFile.isFile()) {
            throw new RomException("No save file specified");
        }
        try (InputStream is = new FileInputStream(saveFile)) {
            loadExternal(is);
        } catch (Exception e) {
            throw new RomException(e);
        }
    }

}
