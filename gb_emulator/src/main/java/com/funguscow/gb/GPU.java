package com.funguscow.gb;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

/**
 * The graphical processing unit that handles pixels of the GB screen
 */
public class GPU {

    public static final int MS_BETWEEN_VBLANKS = (144 * (51 + 20 + 43)) * 1000 / (1 << 20);
    public static final int WAIT_THRESHOLD = 4;

    /**
     * Accepts pixels
     */
    public interface GameboyScreen {
        /**
         * Place a pixel into the screen
         * @param x X coordinate
         * @param y Y coordinate
         * @param color Color in RGB888 format
         */
        void putPixel(int x, int y, int color);

        /**
         * Update the screen
         */
        void update();
    }

    public static final int VRAM_SIZE = 0x2000;
    public static final int SCREEN_HEIGHT = 144;
    public static final int SCREEN_WIDTH = 160;

    private static class SpriteAttrib{
        public int x, y, pattern;
        public boolean priority, yFlip, xFlip, usePal1;

        // CGB only
        public boolean useVramBank1;
        public int cgbPalette;
    }

    public final int[] grayPalette = {
            0x00ffffff,
            0x00aaaaaa,
            0x00555555,
            0x00000000
    };

    private int line; // Current line being scanned
    private int windowLine;
    private int mode = 2; // 0 - hblank, 1 - vblank, 2 - OAM, 3 - VRAM
    private int modeCycles; // Cycles spent on this line
    private Machine machine;

    private boolean lcdOn, windowMapHigh, windowOn, bgTileHigh, bgMapHigh, tallSprites, spritesOn, bgOn;
    private boolean oamInt, vblankInt, hblankInt, lycInt, lycCoincidence;
    private int lyc;
    private int scrollX, scrollY, windowX, windowY;
    private final int[] bgPal = new int[4], ob0Pal = new int[4], ob1Pal = new int[4];

    // CGB Only
    final int[] bgPalColor = new int[4 * 8], obPalColor = new int[4 * 8];
    private int bgPalIndex, obPalIndex;
    private boolean bgPalIncrement, obPalIncrement;
    // End CGB Only

    private final SpriteAttrib[] attribs = new SpriteAttrib[40];
    private final Integer[] spriteOrder = new Integer[40];
    private final boolean[] occluded = new boolean[SCREEN_WIDTH];

    // Accessed by OpcodeTest
    byte[] vram;

    private final int[] zBuf = new int[SCREEN_WIDTH * SCREEN_HEIGHT];

    private long lastVBlank;

    public GameboyScreen screen = null;

    private final boolean cgb;
    private final boolean compatibility;
    private int vramBank; // CGB only
    private boolean oamPosOrder;

    /**
     *
     * @param machine Machine running
     * @param cgb True for gameboy color mode
     * @param compatibility True for monochrome compatibility mode on gameboy color
     */
    public GPU(Machine machine, boolean cgb, boolean compatibility){
        this.machine = machine;
        this.cgb = cgb;
        this.compatibility = compatibility;
        vram = new byte[cgb ? (VRAM_SIZE * 2) : VRAM_SIZE];
        for(int i = 0; i < 40; i++){
            attribs[i] = new SpriteAttrib();
            spriteOrder[i] = i;
        }
        lastVBlank = System.currentTimeMillis();
    }

    /**
     * Needs to be called after constructor
     */
    public void initState() {
        mode = 3; // Is this even really right?
        lycCoincidence = true;
    }

    /**
     * For now, just update the screen if there is one
     */
    private void doDraw(){
        if(screen != null) {
            screen.update();
        }
    }

    /**
     * For now it only draws the background
     */
    private void scanline(){
        if(screen == null) {
            return;
        }
        if(!lcdOn) {
            return;
        }
        if (line >= SCREEN_HEIGHT) {
            return;
        }
        Arrays.fill(zBuf, 0);
        if(bgOn || (cgb && !compatibility)){ // Bit 0 of LCDC is different in CGB
            int tiledataBase = bgTileHigh ? 0x1000 : 0x0000;
            // Draw Background
            {
                int tilemapBase = bgMapHigh ? 0x1c00 : 0x1800;
                int ty = line + scrollY;
                int mty = ty >> 3;
                mty &= 31;
                ty &= 7;
                for (int tx = 0; tx < 21; tx++) {
                    int mtx = ((tx << 3) + scrollX) >> 3;
                    mtx &= 31;

                    // Index into tile map
                    int tileNum = vram[tilemapBase + mty * 32 + mtx] & 0xff;

                    // CGB attributes
                    boolean flipX = false, flipY = false, bgPriority = false, highVramBank = false;
                    int cgbPalette = 0;
                    if (cgb) {
                        int attribute = vram[tilemapBase + mty * 32 + mtx + 0x2000];
                        bgPriority = (attribute & 0x80) != 0;
                        flipY = (attribute & 0x40) != 0;
                        flipX = (attribute & 0x20) != 0;
                        highVramBank = (attribute & 0x8) != 0;
                        cgbPalette = attribute & 7;
                    }

                    if (bgTileHigh) tileNum = (byte)tileNum;
                    int rowY = ty;
                    if (flipY)
                        rowY = 7 - rowY;
                    int rowBase = tiledataBase + rowY * 2;
                    int tileAddress = rowBase + tileNum * 16;
                    if (highVramBank)
                        tileAddress += 0x2000;
                    int row0 = vram[tileAddress];
                    int row1 = vram[tileAddress + 1];
                    for (int x = 7; x >= 0; x--) {
                        int screenX = x;
                        if (flipX)
                            screenX = 7 - screenX;
                        screenX = screenX - (scrollX & 7) + tx * 8;
                        if (screenX < 0) {
                            break;
                        }
                        int palid = ((row1 & 1) << 1) | (row0 & 1);
                        row1 >>= 1;
                        row0 >>= 1;
                        if (screenX >= SCREEN_WIDTH) {
                            continue;
                        }
                        int color;
                        if (cgb) {
                            int pIndex = palid;
                            if (compatibility) {
                                pIndex = bgPal[pIndex];
                            }
                            int rgb = bgPalColor[cgbPalette * 4 + pIndex];
                            int r = rgb & 31;
                            int g = (rgb >> 5) & 31;
                            int b = (rgb >> 10) & 31;
                            color = (r << 19) | (g << 11) | (b << 3);
                        } else {
                            int shade = bgPal[palid];
                            color = grayPalette[shade];
                        }
                        screen.putPixel(screenX, line, color);
                        if (bgPriority && palid != 0) {
                            zBuf[line * 160 + screenX] = -1;
                        } else {
                            zBuf[line * 160 + screenX] = palid;
                        }
                    }
                }
            }
            // Draw window
            if(windowOn && windowX >= 0 && windowY >= 0 && windowX <= 166 && windowY <= 143){
                int tilemapBase = windowMapHigh ? 0x1c00 : 0x1800;
                int wline = windowLine - windowY;
                int mty = wline >> 3;
                int ty = wline & 7;
                if(wline >= 0){
                    for(int tx = 0; tx < 21; tx ++){
                        int index = tilemapBase + mty * 32 + tx;
                        if (index >= VRAM_SIZE) {
                            continue;
                        }
                        int tileNum = vram[index] & 0xff;

                        // CGB attributes
                        boolean flipX = false, flipY = false, bgPriority = false, highVramBank = false;
                        int cgbPalette = 0;
                        if (cgb) {
                            int attribute = vram[index + 0x2000];
                            bgPriority = (attribute & 0x80) != 0;
                            flipY = (attribute & 0x40) != 0;
                            flipX = (attribute & 0x20) != 0;
                            highVramBank = (attribute & 0x8) != 0;
                            cgbPalette = attribute & 7;
                        }

                        if(bgTileHigh) tileNum = (byte)tileNum;
                        int rowY = ty;
                        if (flipY)
                            rowY = 7 - rowY;
                        int rowBase = tiledataBase + rowY * 2;
                        if (highVramBank)
                            rowBase += 0x2000;
                        int row0 = vram[tileNum * 16 + rowBase];
                        int row1 = vram[tileNum * 16 + rowBase + 1];

                        for(int x = 7; x >= 0; x--){
                            int screenX = x;
                            if (flipX)
                                screenX = 7 - screenX;
                            screenX = screenX - 7 + tx * 8 + windowX;
                            int palid = ((row1 & 1) << 1) | (row0 & 1);
                            row1 >>= 1;
                            row0 >>= 1;
                            if(screenX < 0 || screenX >= 160) {
                                continue; // Off-screen
                            }
                            int color;
                            if (cgb) {
                                int pIndex = palid;
                                if (compatibility) {
                                    pIndex = bgPal[pIndex];
                                }
                                int rgb = bgPalColor[cgbPalette * 4 + pIndex];
                                int r = rgb & 31;
                                int g = (rgb >> 5) & 31;
                                int b = (rgb >> 10) & 31;
                                color = (r << 19) | (g << 11) | (b << 3);
                            } else {
                                int shade = bgPal[palid];
                                color = grayPalette[shade];
                            }
                            screen.putPixel(screenX, line, color);
                            if (bgPriority && palid != 0) {
                                zBuf[line * 160 + screenX] = -1;
                            } else {
                                zBuf[line * 160 + screenX] |= palid;
                            }
                        }
                    }
                }
            }
        }
        // Draw sprites (something still isn't right here)
        if (spritesOn) {
            Arrays.fill(occluded, false);
            int height = tallSprites ? 16 : 8;
            if (cgb && !oamPosOrder) {
                Arrays.sort(spriteOrder);
            } else {
                Arrays.sort(spriteOrder, Comparator.comparingInt(a -> attribs[a].x));
            }
            int spritesThisLine = 0;
            for (int i = 0; i < spriteOrder.length && spritesThisLine < 10; i++) {
                SpriteAttrib sprite = attribs[spriteOrder[i]];
                int y0 = sprite.y - 16;
                if (y0 > line || y0 + height <= line) {
                    continue;
                }
                spritesThisLine++;
                int x0 = sprite.x - 8;
                if (x0 + 8 <= 0 || x0 >= SCREEN_WIDTH) {
                    continue;
                }
                int pattern = sprite.pattern;
                if (tallSprites) {
                    pattern &= ~1;
                }
                int patternBase = pattern << 4;
                if (cgb && sprite.useVramBank1)
                    patternBase += 0x2000;
                int spriteY = line - y0;
                if (sprite.yFlip) {
                    spriteY = height - 1 - spriteY;
                }
                int row0 = vram[patternBase + 2 * spriteY];
                int row1 = vram[patternBase + 2 * spriteY + 1];
                for (int x = 0; x < 8; x++) {
                    int screenX = x0 + x;
                    if (sprite.xFlip) {
                        screenX = x0 + 7 - x;
                    }
                    if (screenX < 0 || screenX >= SCREEN_WIDTH) {
                        continue;
                    }
                    if (occluded[screenX]) {
                        continue;
                    }
                    int pixel = ((row0 >> (7 - x)) & 1) | (((row1 >> (7 - x)) & 1) << 1);
                    int oldZ = zBuf[line * 160 + screenX];
                    boolean draw = false;
                    if (cgb && !bgOn) { // Master priority is overwritten
                        draw = true;
                    } else if (cgb && oldZ == -1) { // BG-to-OAM priority takes precedence(?)
                        draw = false;
                    } else if (!sprite.priority || oldZ == 0) {
                        draw = true;
                    }
                    draw &= (pixel != 0);
                    if (draw) {
                        int color;
                        if (cgb) {
                            int pIndex = pixel;
                            int sPal = sprite.cgbPalette;
                            if (compatibility) {
                                pIndex = (sprite.usePal1 ? ob1Pal : ob0Pal)[pIndex];
                                sPal = 0;
                            }
                            int rgb = obPalColor[sPal * 4 + pIndex];
                            int r = rgb & 31;
                            int g = (rgb >> 5) & 31;
                            int b = (rgb >> 10) & 31;
                            color = (r << 19) | (g << 11) | (b << 3);
                        } else {
                            int shade = (sprite.usePal1 ? ob1Pal : ob0Pal)[pixel];
                            color = grayPalette[shade];
                        }
                        screen.putPixel(screenX, line, color);
                        occluded[screenX] = true;
                    }
                }
            }
        }
    }

    /**
     * Move GPU ahead a number of cycles, progressing mode as needed
     * @param cycles Cycles to increment (m-cycles)
     * @param silent When the APU is active, it handles timing. Otherwise, the GPU should
     */
    public void increment(int cycles, boolean silent){
        if (!lcdOn) {
            mode = 0;
            modeCycles = 0;
            line = 0;
            return;
        }
        modeCycles += cycles;
        switch(mode){
            case 0: // Hblank
                if(modeCycles >= 51){
                    modeCycles -= 51;
                    incrementLine();
                    if(line == lyc) {
                        lycCoincidence = true;
                        if(lycInt) {
                            machine.interruptsFired |= 0x2;
                        }
                    }
                    else {
                        lycCoincidence = false;
                    }
                    if(line < 144) {
                        if(oamInt) {
                            machine.interruptsFired |= 0x2;
                        }
                        mode = 2;
                    }
                    else {
                        doDraw();
                        machine.interruptsFired |= 1; // Vblank interrupt
                        if(vblankInt) {
                            machine.interruptsFired |= 0x2;
                        }
                        mode = 1;
                        long passed = System.currentTimeMillis() - lastVBlank;
                        long targetWait = MS_BETWEEN_VBLANKS / machine.speedUp - passed;
                        if (targetWait > WAIT_THRESHOLD && silent) {
                            try {
                                Thread.sleep(targetWait);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                break;
            case 1: // Vblank
                if(modeCycles >= 114){
                    modeCycles -= 114;
                    incrementLine();
                    if(line >= 154) {
                        if(oamInt) {
                            machine.interruptsFired |= 0x2;
                        }
                        mode = 2;
                        line = 0;
                        windowLine = 0;
                        lastVBlank = System.currentTimeMillis();
                    }
                }
                break;
            case 2: // OAM
                if(modeCycles >= 20){
                    modeCycles -= 20;
                    mode = 3;
                }
                break;
            case 3: // VRAM
                if(modeCycles >= 43){
                    modeCycles -= 43;
                    mode = 0;
                    if (cgb) {
                        machine.mmu.onHblank();
                    }
                    scanline();
                    if(hblankInt) {
                        machine.interruptsFired |= 0x2;
                    }
                }
                break;
        }
    }

    /**
     * Increment the line and windowline as appropriate
     */
    private void incrementLine() {
        line++;
        if (windowX >= 0 && windowY >= 0 && windowX <= 166 && windowY <= 143) {
            windowLine++;
        }
    }

    /**
     * Fetch a byte
     * @param address Address to read
     * @return Byte value
     */
    public int read(int address){
        switch(address >> 12){
            case 0x8: // VRAM
            case 0x9:
                address &= 0x1fff;
                if (cgb) {
                    address = address | (vramBank << 13);
                }
                return vram[address] & 0xff;
            case 0xf:
                if ((address & 0xf0) == 0x40) {
                    switch ((address >> 8) & 0xf) {
                        case 0xe: { // OAM
                            if (mode > 1) {
                                return 0xff;
                            }
                            int offset = address & 0xff;
                            if (offset >= 0xa0) {
                                return 0;
                            }
                            offset >>= 2;
                            SpriteAttrib sprite = attribs[offset];
                            switch (address & 3) {
                                case 0: //Y
                                    return sprite.y;
                                case 1: // X
                                    return sprite.x;
                                case 2:
                                    return sprite.pattern;
                                case 3: {
                                    int flags = 0;
                                    if (sprite.priority) flags |= 0x80;
                                    if (sprite.yFlip) flags |= 0x40;
                                    if (sprite.xFlip) flags |= 0x20;
                                    if (cgb) {
                                        if (sprite.useVramBank1) flags |= 0x8;
                                        flags |= sprite.cgbPalette;
                                    } else {
                                        if (sprite.usePal1) flags |= 0x10;
                                        flags |= 7;
                                    }
                                    return flags;
                                }
                            }
                            return 0;
                        }
                        case 0xf: { // Registers
                            switch (address & 0xf) {
                                case 0x00: // 0xff40 LCDC
                                {
                                    int lcdc = 0;
                                    if (lcdOn) lcdc |= 0x80;
                                    if (windowMapHigh) lcdc |= 0x40;
                                    if (windowOn) lcdc |= 0x20;
                                    if (!bgTileHigh) lcdc |= 0x10;
                                    if (bgMapHigh) lcdc |= 0x8;
                                    if (tallSprites) lcdc |= 0x4;
                                    if (spritesOn) lcdc |= 0x2;
                                    if (bgOn) lcdc |= 0x1;
                                    return lcdc;
                                }
                                case 0x01: // 0xff41 STAT
                                {
                                    int stat = mode;
                                    if (lycInt) stat |= 0x40;
                                    if (oamInt) stat |= 0x20;
                                    if (vblankInt) stat |= 0x10;
                                    if (hblankInt) stat |= 0x8;
                                    if (lycCoincidence) stat |= 0x4;
                                    return stat | 0x80;
                                }
                                case 0x02: // 0xff41 SCY
                                    return scrollY;
                                case 0x03: // SCX
                                    return scrollX;
                                case 0x04: // LY
                                    return line;
                                case 0x05: // LYC
                                    return lyc;
                                case 0x07: // BGP
                                    return (bgPal[0] | (bgPal[1] << 2) | (bgPal[2] << 4) | (bgPal[3] << 6));
                                case 0x08: // OB9
                                    return (ob0Pal[0] | (ob0Pal[1] << 2) | (ob0Pal[2] << 4) | (ob0Pal[3] << 6));
                                case 0x09: // OB1
                                    return (ob1Pal[0] | (ob1Pal[1] << 2) | (ob1Pal[2] << 4) | (ob1Pal[3] << 6));
                                case 0x0a: // WY
                                    return windowY;
                                case 0x0b: // WX
                                    return windowX;
                                case 0x0f:
                                    if (!cgb) {
                                        return 0xff;
                                    }
                                    return vramBank | 0xfe;
                            }
                            break;
                        }
                    }
                } else if ((address & 0xf0) == 0x60) {
                    switch (address & 0xf) {
                        case 0x8:
                            return bgPalIndex | 0x40 | (bgPalIncrement ? 0x80 : 0);
                        case 0x9:
                            if ((bgPalIndex & 1) == 0) {
                                return bgPalColor[bgPalIndex >> 1] & 0xff;
                            }
                            return bgPalColor[bgPalIndex >> 1] >> 8;
                        case 0xA:
                            return obPalIndex | 0x40 | (obPalIncrement ? 0x80 : 0);
                        case 0xB:
                            if ((obPalIndex & 1) == 0) {
                                return obPalColor[obPalIndex >> 1] & 0xff;
                            }
                            return obPalColor[obPalIndex >> 1] >> 8;
                        case 0xC:
                            if (cgb) {
                                return 0xfe | (oamPosOrder ? 1 : 0);
                            }
                            return 0xff;
                    }
                }
        }
        return 0;
    }

    /**
     * Write a value to GPU
     * @param address Address to write to
     * @param value Value to write
     */
    public void write(int address, int value){
        switch(address >> 12){
            case 0x8: // VRAM
            case 0x9:
                address &= 0x1fff;
                if (cgb) {
                    address = address | (vramBank << 13);
                }
                vram[address] = (byte)value;
                break;
            case 0xf: // OAM and registers
                switch((address >> 8) & 0xf) {
                    case 0xe: // OAM
                        if (mode > 1) {
                            break;
                        }
                        int offset = address & 0xff;
                        if (offset >= 0xa0) {
                            break;
                        }
                        offset >>= 2;
                        SpriteAttrib sprite = attribs[offset];
                        switch (address & 3) {
                            case 0: //Y
                                sprite.y = value;
                                break;
                            case 1: // X
                                sprite.x = value;
                                break;
                            case 2:
                                sprite.pattern = value;
                                break;
                            case 3: {
                                sprite.priority = (value & 0x80) != 0;
                                sprite.yFlip = (value & 0x40) != 0;
                                sprite.xFlip = (value & 0x20) != 0;
                                if (cgb) {
                                    sprite.useVramBank1 = (value & 0x8) != 0;
                                    sprite.cgbPalette = value & 7;
                                } else {
                                    sprite.usePal1 = (value & 0x10) != 0;
                                }
                                break;
                            }
                        }
                        break;
                    case 0xf: // Registers
                        if ((address & 0xf0) == 0x40) {
                            switch (address & 0xf) {
                                case 0x0: // LCDC
                                    lcdOn = (value & 0x80) != 0;
                                    windowMapHigh = (value & 0x40) != 0;
                                    windowOn = (value & 0x20) != 0;
                                    bgTileHigh = (value & 0x10) == 0; /* Tiledata address is higher when this bit not set */
                                    bgMapHigh = (value & 0x8) != 0;
                                    tallSprites = (value & 0x4) != 0;
                                    spritesOn = (value & 0x2) != 0;
                                    bgOn = (value & 0x1) != 0;
                                    break;
                                case 0x1: // STAT
                                    lycInt = (value & 0x40) != 0;
                                    oamInt = (value & 0x20) != 0;
                                    vblankInt = (value & 0x10) != 0;
                                    hblankInt = (value & 0x8) != 0;
                                    break;
                                case 0x2:
                                    scrollY = value;
                                    break;
                                case 0x3:
                                    scrollX = value;
                                    break;
                                case 0x4:
                                    line = value;
                                    break;
                                case 0x5:
                                    lyc = value;
                                    break;
                                case 0x7:
                                    bgPal[0] = value & 3;
                                    bgPal[1] = (value >> 2) & 3;
                                    bgPal[2] = (value >> 4) & 3;
                                    bgPal[3] = (value >> 6) & 3;
                                    break;
                                case 0x8:
                                    ob0Pal[0] = value & 3;
                                    ob0Pal[1] = (value >> 2) & 3;
                                    ob0Pal[2] = (value >> 4) & 3;
                                    ob0Pal[3] = (value >> 6) & 3;
                                    break;
                                case 0x9:
                                    ob1Pal[0] = value & 3;
                                    ob1Pal[1] = (value >> 2) & 3;
                                    ob1Pal[2] = (value >> 4) & 3;
                                    ob1Pal[3] = (value >> 6) & 3;
                                    break;
                                case 0xa:
                                    windowY = value;
                                    break;
                                case 0xb:
                                    windowX = value;
                                    break;
                                case 0xf:
                                    if (cgb) {
                                        vramBank = value & 1;
                                    }
                                    break;
                            }
                        } else if ((address & 0xf0) == 0x60) {
                            switch (address & 0xf) {
                                case 0x8: // BG Pal index
                                    bgPalIndex = value & 63;
                                    bgPalIncrement = (value & 0x80) != 0;
                                    break;
                                case 0x9: { // BG Pal data
                                    int index = bgPalIndex >> 1;
                                    if ((bgPalIndex & 1) == 0) {
                                        bgPalColor[index] &= ~0xff;
                                        bgPalColor[index] |= value;
                                    } else {
                                        bgPalColor[index] &= 0xff;
                                        bgPalColor[index] |= value << 8;
                                    }
                                    if (bgPalIncrement) {
                                        bgPalIndex = (bgPalIndex + 1) & 63;
                                    }
                                    break;
                                }
                                case 0xA: // Ob Pal index
                                    obPalIndex = value & 63;
                                    obPalIncrement = (value & 0x80) != 0;
                                    break;
                                case 0xB: { // OB Pal data
                                    int index = obPalIndex >> 1;
                                    if ((obPalIndex & 1) == 0) {
                                        obPalColor[index] &= ~0xff;
                                        obPalColor[index] |= value;
                                    } else {
                                        obPalColor[index] &= 0xff;
                                        obPalColor[index] |= value << 8;
                                    }
                                    if (obPalIncrement) {
                                        obPalIndex = (obPalIndex + 1) & 63;
                                    }
                                }
                                case 0xC:
                                    if (cgb) {
                                        oamPosOrder = (value & 1) != 0;
                                    }
                                    break;
                            }
                        }
                }
        }
    }

    /**
     * Save state of GPU
     * @param dos Destination stream
     * @throws IOException From inner write calls
     */
    public void save(DataOutputStream dos) throws IOException {
        dos.write("GPU ".getBytes(StandardCharsets.UTF_8));
        dos.writeInt(vram.length);
        dos.writeBoolean(cgb);
        dos.write(vram);
        for (int c : bgPal) {
            dos.writeInt(c);
        }
        for (int c : ob0Pal) {
            dos.writeInt(c);
        }
        for (int c : ob1Pal) {
            dos.writeInt(c);
        }
        for (SpriteAttrib sprite : attribs) {
            dos.writeInt(sprite.x);
            dos.writeInt(sprite.y);
            dos.writeInt(sprite.pattern);
            dos.writeBoolean(sprite.priority);
            dos.writeBoolean(sprite.xFlip);
            dos.writeBoolean(sprite.yFlip);
            dos.writeBoolean(sprite.usePal1);
            dos.writeBoolean(sprite.useVramBank1);
            dos.writeInt(sprite.cgbPalette);
        }
        dos.writeInt(mode);
        dos.writeInt(modeCycles);
        dos.writeInt(line);
        dos.writeInt(windowLine);
        dos.writeInt(scrollX);
        dos.writeInt(scrollY);
        dos.writeInt(windowX);
        dos.writeInt(windowY);
        dos.writeInt(lyc);
        dos.writeBoolean(lcdOn);
        dos.writeBoolean(bgOn);
        dos.writeBoolean(windowOn);
        dos.writeBoolean(spritesOn);
        dos.writeBoolean(windowMapHigh);
        dos.writeBoolean(bgMapHigh);
        dos.writeBoolean(bgTileHigh);
        dos.writeBoolean(tallSprites);
        dos.writeBoolean(oamInt);
        dos.writeBoolean(vblankInt);
        dos.writeBoolean(hblankInt);
        dos.writeBoolean(lycInt);
        dos.writeBoolean(lycCoincidence);
        if (cgb) {
            dos.writeBoolean(compatibility);
            for (int c : bgPalColor) {
                dos.writeInt(c);
            }
            for (int c : obPalColor) {
                dos.writeInt(c);
            }
            dos.writeInt(bgPalIndex);
            dos.writeInt(obPalIndex);
            dos.writeInt(vramBank);
            dos.writeBoolean(bgPalIncrement);
            dos.writeBoolean(obPalIncrement);
            dos.writeBoolean(oamPosOrder);
        }
    }

    /**
     * Load GPU state
     * @param dis Source stream
     * @throws IOException From inner read calls
     */
    public void load(DataInputStream dis) throws IOException {
        if (dis.readInt() != vram.length) {
            throw new IOException("VRAM sizes do not match");
        }
        if (dis.readBoolean() != cgb) {
            throw new IOException("CGB modes do not match");
        }
        dis.read(vram);
        for (int i = 0; i < bgPal.length; i++) {
            bgPal[i] = dis.readInt();
        }
        for (int i = 0; i < ob0Pal.length; i++) {
            ob0Pal[i] = dis.readInt();
        }
        for (int i = 0; i < ob1Pal.length; i++) {
            ob1Pal[i] = dis.readInt();
        }
        for (SpriteAttrib sprite : attribs) {
            sprite.x = dis.readInt();
            sprite.y = dis.readInt();
            sprite.pattern = dis.readInt();
            sprite.priority = dis.readBoolean();
            sprite.xFlip = dis.readBoolean();
            sprite.yFlip = dis.readBoolean();
            sprite.usePal1 = dis.readBoolean();
            sprite.useVramBank1 = dis.readBoolean();
            sprite.cgbPalette = dis.readInt();
        }
        mode = dis.readInt();
        modeCycles = dis.readInt();
        line = dis.readInt();
        windowLine = dis.readInt();
        scrollX = dis.readInt();
        scrollY = dis.readInt();
        windowX = dis.readInt();
        windowY = dis.readInt();
        lyc = dis.readInt();
        lcdOn = dis.readBoolean();
        bgOn = dis.readBoolean();
        windowOn = dis.readBoolean();
        spritesOn = dis.readBoolean();
        windowMapHigh = dis.readBoolean();
        bgMapHigh = dis.readBoolean();
        bgTileHigh = dis.readBoolean();
        tallSprites = dis.readBoolean();
        oamInt = dis.readBoolean();
        vblankInt = dis.readBoolean();
        hblankInt = dis.readBoolean();
        lycInt = dis.readBoolean();
        lycCoincidence = dis.readBoolean();
        if (cgb) {
            if (compatibility != dis.readBoolean()) {
                throw new IOException("Compatibility modes do not match");
            }
            for (int i = 0; i < bgPalColor.length; i++) {
                bgPalColor[i] = dis.readInt();
            }
            for (int i = 0; i < obPalColor.length; i++) {
                obPalColor[i] = dis.readInt();
            }
            bgPalIndex = dis.readInt();
            obPalIndex = dis.readInt();
            vramBank = dis.readInt();
            bgPalIncrement = dis.readBoolean();
            obPalIncrement = dis.readBoolean();
            oamPosOrder = dis.readBoolean();
        }
    }

    /**
     * Output the debugging state
     */
    public void printDebugState() {
        System.out.printf("On? %s, bg? %s, window? %s, sprites? %s\n", lcdOn, bgOn, windowOn, spritesOn);
        System.out.printf("In mode %d for 0x%x cycles\n", mode, modeCycles);
        System.out.printf("High bg map? %s, High window map? %s, High bg tiles? %s, Tall sprites? %s\n", bgMapHigh, windowMapHigh, bgTileHigh, tallSprites);
        System.out.printf("Scroll X: %d, Y: %d, Window X: %d, Y: %x\n", scrollX, scrollY, windowX, windowY);
        System.out.printf("Line %d, winline %d, LYC %d, coincidence? %s\n", line, windowLine, lyc, lycCoincidence);
        System.out.printf("Interrupts - OAM? %s, VBlank? %s, HBlank? %s, LYC? %s\n", oamInt, vblankInt, hblankInt, lycInt);
        System.out.printf("BG pal index %d, incr? %s, OP index %d, incr? %s\n", bgPalIndex, bgPalIndex, obPalIndex, obPalIncrement);
        System.out.printf("VRAM bank 0x%x, OAM priority? %s\n", vramBank, oamPosOrder);
    }

}
