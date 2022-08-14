package com.funguscow.gb;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Handle graphics
 */
public class GPU {

    public static final int MS_BETWEEN_VBLANKS = (144 * (51 + 20 + 43)) * 1000 / (1 << 20);
    public static final int WAIT_THRESHOLD = 4;

    public  interface GameboyScreen {
        void putPixel(int x, int y, int color);
        void update();
    }

    public static final int VRAM_SIZE = 0x2000;
    public static final int SCREEN_HEIGHT = 144;
    public static final int SCREEN_WIDTH = 160;

    private static class SpriteAttrib{
        public int x, y, pattern;
        public boolean priority, y_flip, x_flip, use_pal1;

        // CGB only
        public boolean useVramBank1;
        public int cgbPalette;
    }

    int line; // Current line being scanned
    private int window_line;
    private int mode = 2; // 0 - hblank, 1 - vblank, 2 - OAM, 3 - VRAM
    private int mode_cycles; // Cycles spent on this line
    Machine machine;

    private boolean lcd_on, window_map_high, window_on, bg_tile_high, bg_map_high, tall_sprites, sprites_on, bg_on;
    private boolean oam_int, vblank_int, hblank_int, lyc_int, lyc_coincidence;
    private int lyc;
    private int scroll_x, scroll_y, window_x, window_y;
    private final int[] bg_pal = new int[4], ob0_pal = new int[4], ob1_pal = new int[4];

    // CGB Only
    private final int[] bgPalColor = new int[4 * 8], obPalColor = new int[4 * 8];
    private int bgPalIndex, obPalIndex;
    private boolean bgPalIncrement, obPalIncrement;
    // End CGB Only

    private final SpriteAttrib[] attribs = new SpriteAttrib[40];
    private final Integer[] spriteOrder = new Integer[40];
    private final boolean[] occluded = new boolean[SCREEN_WIDTH];
    byte[] vram;

    private final int[] z_buf = new int[SCREEN_WIDTH * SCREEN_HEIGHT];

    private long lastVBlank;

    public GameboyScreen screen = null;

    private boolean cgb;
    private int vramBank; // CGB only

    public GPU(Machine machine, boolean cgb){
        this.machine = machine;
        this.cgb = cgb;
        vram = new byte[cgb ? (VRAM_SIZE * 2) : VRAM_SIZE];
        for(int i = 0; i < 40; i++){
            attribs[i] = new SpriteAttrib();
            spriteOrder[i] = i;
        }
        lastVBlank = System.currentTimeMillis();
    }

    public void init_state() {
        mode = 3; // Is this even really right?
        lyc_coincidence = true;
    }

    /**
     * For now, just update the screen if there is one
     */
    private void do_draw(){
        if(screen != null) {
            screen.update();
        }
    }

    /**
     * For now it only draws the background
     */
    private void scanline(){
        if(screen == null)
            return;
        if(!lcd_on)
            return;
        if (line >= SCREEN_HEIGHT) {
            return;
        }
        Arrays.fill(z_buf, 0);
        if(bg_on || cgb){ // Bit 0 of LCDC is different in CGB
            int tiledata_base = bg_tile_high ? 0x1000 : 0x0000;
            // Draw Background
            {
                int tilemap_base = bg_map_high ? 0x1c00 : 0x1800;
                int ty = line + scroll_y;
                int mty = ty >> 3;
                mty &= 31;
                ty &= 7;
                int row_base = tiledata_base + ty * 2;
                for (int tx = 0; tx < 21; tx++) {
                    int mtx = ((tx << 3) + scroll_x) >> 3;
                    mtx &= 31;

                    // Index into tile map
                    int tile_num = vram[tilemap_base + mty * 32 + mtx] & 0xff;

                    // CGB attributes
                    boolean flipX = false, flipY = false, bgPriority = false, highVramBank = false;
                    int cgbPalette = 0;
                    if (cgb) {
                        int attribute = vram[tilemap_base + mty * 32 + mtx + 0x2000];
                        bgPriority = (attribute & 0x80) != 0;
                        flipY = (attribute & 0x40) != 0;
                        flipX = (attribute & 0x20) != 0;
                        highVramBank = (attribute & 0x8) != 0;
                        cgbPalette = attribute & 7;
                    }

                    if (bg_tile_high) tile_num = (byte)tile_num;
                    int tileAddress = row_base + tile_num * 16;
                    if (highVramBank)
                        tileAddress += 0x2000;
                    int row0 = vram[tileAddress];
                    int row1 = vram[tileAddress + 1];
                    for (int x = 7; x >= 0; x--) {
                        int screen_x = x - (scroll_x & 7) + tx * 8;
                        if (screen_x < 0) {
                            break;
                        }
                        int palid = ((row1 & 1) << 1) | (row0 & 1);
                        row1 >>= 1;
                        row0 >>= 1;
                        if (screen_x >= SCREEN_WIDTH) {
                            continue;
                        }
                        int color;
                        if (cgb) {
                            int rgb = bgPalColor[cgbPalette * 4 + palid];
                            int r = rgb & 31;
                            int g = (rgb >> 5) & 31;
                            int b = (rgb >> 10) & 31;
                            color = (r << 19) | (g << 11) | (b << 3);
                        } else {
                            int shade = bg_pal[palid] * 85; // Transform [0,3] to [0,255]
                            shade = 255 - shade;
                            color = (shade << 16) | (shade << 8) | shade;
                        }
                        screen.putPixel(screen_x, line, color);
                        z_buf[line * 160 + screen_x] = palid;
                    }
                }
            }
            // Draw window
            if(window_on && window_x >= 0 && window_y >= 0 && window_x <= 166 && window_y <= 143){
                int tilemap_base = window_map_high ? 0x1c00 : 0x1800;
                int wline = window_line - window_y;
                int mty = wline >> 3;
                int ty = wline & 7;
                if(wline >= 0){
                    for(int tx = 0; tx < 21; tx ++){
                        int index = tilemap_base + mty * 32 + tx;
                        if (index >= VRAM_SIZE) {
                            continue;
                        }
                        int tile_num = vram[index] & 0xff;

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

                        if(bg_tile_high) tile_num = (byte)tile_num;
                        int row_base = tiledata_base + ty * 2;
                        if (highVramBank)
                            row_base += 0x2000;
                        int row0 = vram[tile_num * 16 + row_base];
                        int row1 = vram[tile_num * 16 + row_base + 1];

                        for(int x = 7; x >= 0; x--){
                            int screen_x = x - 7 + tx * 8 + window_x;
                            int palid = ((row1 & 1) << 1) | (row0 & 1);
                            row1 >>= 1;
                            row0 >>= 1;
                            if(screen_x < 0 || screen_x >= 160) {
                                continue; // Off-screen
                            }
                            int color;
                            if (cgb) {
                                int rgb = bgPalColor[cgbPalette * 4 + palid];
                                int r = rgb & 31;
                                int g = (rgb >> 5) & 31;
                                int b = (rgb >> 10) & 31;
                                color = (r << 19) | (g << 11) | (b << 3);
                            } else {
                                int shade = bg_pal[palid] * 85; // Transform [0,3] to [0,255]
                                shade = 255 - shade;
                                color = (shade << 16) | (shade << 8) | shade;
                            }
                            screen.putPixel(screen_x, line, color);
                            z_buf[line * 160 + screen_x] |= palid;
                        }
                    }
                }
            }
        }
        // Draw sprites (something still isn't right here)
        if (sprites_on) {
            Arrays.fill(occluded, false);
            int height = tall_sprites ? 16 : 8;
            Arrays.sort(spriteOrder, Comparator.comparingInt(a -> attribs[a].x));
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
                if (tall_sprites) {
                    pattern &= ~1;
                }
                int pattern_base = pattern << 4;
                if (cgb && sprite.useVramBank1)
                    pattern_base += 0x2000;
                int spriteY = line - y0;
                if (sprite.y_flip) {
                    spriteY = height - 1 - spriteY;
                }
                int row0 = vram[pattern_base + 2 * spriteY];
                int row1 = vram[pattern_base + 2 * spriteY + 1];
                for (int x = 0; x < 8; x++) {
                    int screenX = x0 + x;
                    if (sprite.x_flip) {
                        screenX = x0 + 7 - x;
                    }
                    if (screenX < 0 || screenX >= SCREEN_WIDTH) {
                        continue;
                    }
                    if (occluded[screenX]) {
                        continue;
                    }
                    int pixel = ((row0 >> (7 - x)) & 1) | (((row1 >> (7 - x)) & 1) << 1);
                    boolean draw = !sprite.priority;
                    draw |= z_buf[line * 160 + screenX] == 0;
                    draw |= (!bg_on && cgb);
                    draw &= (pixel != 0);
                    if (draw) {
                        int color;
                        if (cgb) {
                            int rgb = obPalColor[sprite.cgbPalette * 4 + pixel];
                            int r = rgb & 31;
                            int g = (rgb >> 5) & 31;
                            int b = (rgb >> 10) & 31;
                            color = (r << 19) | (g << 11) | (b << 3);
                        } else {
                            int shade = (sprite.use_pal1 ? ob1_pal : ob0_pal)[pixel] * 85;
                            shade = 255 - shade;
                            color = (shade << 16) | (shade << 8) | shade;
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
     */
    public void incr(int cycles){
        if (!lcd_on) {
            mode = 0;
            mode_cycles = 0;
            line = 0;
//            sprites_on = false;
//            bg_on = false;
            return;
        }
        mode_cycles += cycles;
        switch(mode){
            case 0: // Hblank
                if(mode_cycles  >= 51){
                    mode_cycles -= 51;
                    increment_line();
                    if(line == lyc) {
                        lyc_coincidence = true;
                        if(lyc_int)
                            machine.interrupts_fired |= 0x2;
                    }
                    else
                        lyc_coincidence = false;
                    if(line < 144) {
                        if(oam_int)
                            machine.interrupts_fired |= 0x2;
                        mode = 2;
                    }
                    else {
                        do_draw();
                        machine.interrupts_fired |= 1; // Vblank interrupt
                        if(vblank_int)
                            machine.interrupts_fired |= 0x2;
                        mode = 1;
                        long passed = System.currentTimeMillis() - lastVBlank;
                        long targetWait = MS_BETWEEN_VBLANKS / machine.speedUp - passed;
                        if (targetWait > WAIT_THRESHOLD) {
                            try {
//                                Thread.sleep(targetWait);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                break;
            case 1: // Vblank
                if(mode_cycles >= 114){
                    mode_cycles -= 114;
                    increment_line();
                    if(line >= 154) {
                        if(oam_int)
                            machine.interrupts_fired |= 0x2;
                        mode = 2;
                        line = 0;
                        window_line = 0;
                        lastVBlank = System.currentTimeMillis();
                    }
                }
                break;
            case 2: // OAM
                if(mode_cycles >= 20){
                    mode_cycles -= 20;
                    mode = 3;
                }
                break;
            case 3: // VRAM
                if(mode_cycles >= 43){
                    mode_cycles -= 43;
                    mode = 0;
                    scanline();
                    if(hblank_int)
                        machine.interrupts_fired |= 0x2;
                }
                break;
        }
    }

    private void increment_line() {
        line++;
        if (window_x >= 0 && window_y >= 0 && window_x <= 166 && window_y <= 143) {
            window_line++;
        }
    }

    public int read(int address){
        switch(address >> 12){
            case 0x8: // VRAM
            case 0x9:
//                if (mode == 3)
//                    return 0xff;
                address &= 0x1fff;
                if (cgb) {
                    address = address | (vramBank << 13);
                }
                return vram[address] & 0xff;
            case 0xf:
                if ((address & 0xf0) == 0x40) {
                    switch ((address >> 8) & 0xf) {
                        case 0xe: { // OAM
                            if (mode > 1)
                                return 0xff;
                            int offset = address & 0xff;
                            if (offset >= 0xa0)
                                return 0;
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
                                    if (sprite.y_flip) flags |= 0x40;
                                    if (sprite.x_flip) flags |= 0x20;
                                    if (cgb) {
                                        if (sprite.useVramBank1) flags |= 0x8;
                                        flags |= sprite.cgbPalette;
                                    } else {
                                        if (sprite.use_pal1) flags |= 0x10;
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
                                    if (lcd_on) lcdc |= 0x80;
                                    if (window_map_high) lcdc |= 0x40;
                                    if (window_on) lcdc |= 0x20;
                                    if (!bg_tile_high) lcdc |= 0x10;
                                    if (bg_map_high) lcdc |= 0x8;
                                    if (tall_sprites) lcdc |= 0x4;
                                    if (sprites_on) lcdc |= 0x2;
                                    if (bg_on) lcdc |= 0x1;
                                    return lcdc;
                                }
                                case 0x01: // 0xff41 STAT
                                {
                                    int stat = mode;
                                    if (lyc_int) stat |= 0x40;
                                    if (oam_int) stat |= 0x20;
                                    if (vblank_int) stat |= 0x10;
                                    if (hblank_int) stat |= 0x8;
                                    if (lyc_coincidence) stat |= 0x4;
                                    return stat | 0x80;
                                }
                                case 0x02: // 0xff41 SCY
                                    return scroll_y;
                                case 0x03: // SCX
                                    return scroll_x;
                                case 0x04: // LY
                                    return line;
                                case 0x05: // LYC
                                    return lyc;
                                case 0x07: // BGP
                                    return (bg_pal[0] | (bg_pal[1] << 2) | (bg_pal[2] << 4) | (bg_pal[3] << 6));
                                case 0x08: // OB9
                                    return (ob0_pal[0] | (ob0_pal[1] << 2) | (ob0_pal[2] << 4) | (ob0_pal[3] << 6));
                                case 0x09: // OB1
                                    return (ob1_pal[0] | (ob1_pal[1] << 2) | (ob1_pal[2] << 4) | (ob1_pal[3] << 6));
                                case 0x0a: // WY
                                    return window_y;
                                case 0x0b: // WX
                                    return window_x;
                                case 0x0f:
                                    if (!cgb)
                                        return 0xff;
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
                            if ((bgPalIndex & 1) == 0)
                                return bgPalColor[bgPalIndex >> 1] & 0xff;
                            return bgPalColor[bgPalIndex >> 1] >> 8;
                        case 0xA:
                            return obPalIndex | 0x40 | (obPalIncrement ? 0x80 : 0);
                        case 0xB:
                            if ((obPalIndex & 1) == 0)
                                return obPalColor[obPalIndex >> 1] & 0xff;
                            return obPalColor[obPalIndex >> 1] >> 8;
                    }
                }
        }
        return 0;
    }

    public void write(int address, int value){
        switch(address >> 12){
            case 0x8: // VRAM
            case 0x9:
//                if (mode != 3)
                address &= 0x1fff;
                if (cgb) {
                    address = address | (vramBank << 13);
                }
                vram[address] = (byte)value;
                break;
            case 0xf: // OAM and registers
                switch((address >> 8) & 0xf) {
                    case 0xe: // OAM
                        if (mode > 1)
                            break;
                        int offset = address & 0xff;
                        if (offset >= 0xa0)
                            break;
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
                                sprite.y_flip = (value & 0x40) != 0;
                                sprite.x_flip = (value & 0x20) != 0;
                                if (cgb) {
                                    sprite.useVramBank1 = (value & 0x8) != 0;
                                    sprite.cgbPalette = value & 7;
                                } else {
                                    sprite.use_pal1 = (value & 0x10) != 0;
                                }
                                break;
                            }
                        }
                        break;
                    case 0xf: // Registers
                        if ((address & 0xf0) == 0x40) {
                            switch (address & 0xf) {
                                case 0x0: // LCDC
                                    lcd_on = (value & 0x80) != 0;
                                    window_map_high = (value & 0x40) != 0;
                                    window_on = (value & 0x20) != 0;
                                    bg_tile_high = (value & 0x10) == 0; /* Tiledata address is higher when this bit not set */
                                    bg_map_high = (value & 0x8) != 0;
                                    tall_sprites = (value & 0x4) != 0;
                                    sprites_on = (value & 0x2) != 0;
                                    bg_on = (value & 0x1) != 0;
                                    break;
                                case 0x1: // STAT
                                    lyc_int = (value & 0x40) != 0;
                                    oam_int = (value & 0x20) != 0;
                                    vblank_int = (value & 0x10) != 0;
                                    hblank_int = (value & 0x8) != 0;
                                    break;
                                case 0x2:
                                    scroll_y = value;
                                    break;
                                case 0x3:
                                    scroll_x = value;
                                    break;
                                case 0x4:
                                    line = value;
                                    break;
                                case 0x5:
                                    lyc = value;
                                    break;
                                case 0x7:
                                    bg_pal[0] = value & 3;
                                    bg_pal[1] = (value >> 2) & 3;
                                    bg_pal[2] = (value >> 4) & 3;
                                    bg_pal[3] = (value >> 6) & 3;
                                    break;
                                case 0x8:
                                    ob0_pal[0] = value & 3;
                                    ob0_pal[1] = (value >> 2) & 3;
                                    ob0_pal[2] = (value >> 4) & 3;
                                    ob0_pal[3] = (value >> 6) & 3;
                                    break;
                                case 0x9:
                                    ob1_pal[0] = value & 3;
                                    ob1_pal[1] = (value >> 2) & 3;
                                    ob1_pal[2] = (value >> 4) & 3;
                                    ob1_pal[3] = (value >> 6) & 3;
                                    break;
                                case 0xa:
                                    window_y = value;
                                    break;
                                case 0xb:
                                    window_x = value;
                                    break;
                                case 0xf:
                                    if (cgb)
                                        vramBank = value & 1;
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
                                    if (bgPalIncrement)
                                        bgPalIndex = (bgPalIndex + 1) & 63;
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
                                    if (obPalIncrement)
                                        obPalIndex = (obPalIndex + 1) & 63;
                                }
                            }
                        }
                }
        }
    }

}
