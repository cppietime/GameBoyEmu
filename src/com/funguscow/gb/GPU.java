package com.funguscow.gb;

/**
 * Handle graphics
 */
public class GPU {

    public  interface GameboyScreen {
        void putPixel(int x, int y, int color);
        void update();
    }

    public static final int VRAM_SIZE = 0x2000;

    private static class SpriteAttrib{
        public int x, y, pattern;
        public boolean priority, y_flip, x_flip, use_pal1;
    }

    private int line; // Current line being scanned
    private int window_line;
    private int mode = 2; // 0 - hblank, 1 - vblank, 2 - OAM, 3 - VRAM
    private int mode_cycles; // Cycles spent on this line
    Machine machine;

    private boolean lcd_on, window_map_high, window_on, bg_tile_high, bg_map_high, tall_sprites, sprites_on, bg_on;
    private boolean oam_int, vblank_int, hblank_int, lyc_int, lyc_coincidence;
    private int lyc;
    private int scroll_x, scroll_y, window_x, window_y;
    private int[] bg_pal = new int[4], ob0_pal = new int[4], ob1_pal = new int[4];

    private SpriteAttrib[] attribs = new SpriteAttrib[40];
    private byte[] vram = new byte[VRAM_SIZE];

    private int[] z_buf = new int[144 * 160];

    public GameboyScreen screen = null;

    public GPU(Machine machine){
        this.machine = machine;
        for(int i = 0; i < 40; i++){
            attribs[i] = new SpriteAttrib();
        }
    }

    public void init_state() {
        mode = 1; // Is this even really right?
        lyc_coincidence = true;
    }

    /**
     * For now, just update the screen if there is one
     */
    private void sync_time(){
        /* TODO does not yet implement flips for sprites, even in theory... */
        if(screen != null) {
            if(sprites_on){
                for(int i = 0; i < 40; i++){
                    SpriteAttrib attrib = attribs[i];
                    if(attrib.x <= 0 || attrib.y <= 0 || attrib.x >= 168 || attrib.y >= 152)
                        continue;
                    int pattern = attrib.pattern;
                    if(tall_sprites)
                        pattern &= ~1;
                    int pattern_base = pattern * 16;
                    int height = tall_sprites ? 16 : 8;
                    for(int y = 0; y < height; y++){
                        int screen_y = y - 16 + attrib.y;
                        if(screen_y < 0 || screen_y >= 144)
                            continue;
                        int row0 = vram[pattern_base];
                        int row1 = vram[pattern_base + 1];
                        for(int x = 7; x >= 0; x--){
                            int screen_x = x - 8 + attrib.x;
                            if(screen_x < 0 || screen_x >= 160)
                                continue;
                            if(attrib.priority && z_buf[screen_y * 160 + screen_x] != 0)
                                continue; // Occluded
                            int palid = (row0 & 1) | ((row1 & 1) << 1);
                            int color = (attrib.use_pal1 ? ob1_pal : ob0_pal)[palid];
                            if(color == 0)
                                continue; // Transparent
                            color *= 85;
                            color = (color << 16) | (color << 8) | color;
                            screen.putPixel(screen_x, screen_y, color);
                        }
                    }
                }
            }
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
        if(bg_on){
            int tiledata_base = bg_tile_high ? 0x1000 : 0x0000;
            {
                int tilemap_base = bg_map_high ? 0x1c00 : 0x1800;
                int ty = line + scroll_y;
                int mty = ty >> 3;
                mty &= 31;
                ty &= 7;
                int row_base = tiledata_base + ty * 2;
                for (int tx = 0; tx < 20; tx++) {
                    int mtx = ((tx << 3) + scroll_x) >> 3;
                    mtx &= 31;
                    int tile_num = vram[tilemap_base + mty * 32 + mtx] & 0xff;
                    if (bg_tile_high) tile_num = (byte)tile_num;
                    int row0 = vram[tile_num * 16 + row_base];
                    int row1 = vram[tile_num * 16 + row_base + 1];
                    for (int x = 7; x >= 0; x--) {
                        int screen_x = x - (scroll_x & 7) + tx * 8;
                        if (screen_x < 0) {
                            break;
                        }
                        int palid = ((row1 & 1) << 1) | (row0 & 1);
                        row1 >>= 1;
                        row0 >>= 1;
                        int shade = bg_pal[palid] * 85; // Transform [0,3] to [0,255]
                        screen.putPixel(screen_x, line, (shade << 16) | (shade << 8) | shade);
                        z_buf[line * 160 + screen_x] = palid;
                    }
                }
            }
            if(window_on && window_x >= 0 && window_y >= 0 && window_x <= 166 && window_y <= 143){
                int tilemap_base = window_map_high ? 0x1c00 : 0x1800;
                int window_line = line - window_y;
                if(window_line >= 0){
                    for(int tx = 0; tx < 20; tx ++){
                        int tile_num = vram[tilemap_base + window_line * 32 + tx] & 0xff;
                        if(bg_tile_high) tile_num = (byte)tile_num;
                        int row_base = tiledata_base + window_line * 2;
                        int row0 = vram[tile_num * 16 + row_base];
                        int row1 = vram[tile_num * 16 + row_base + 1];
                        for(int x = 7; x >= 0; x--){
                            int screen_x = x - 7 + tx * 8 + window_x;
                            if(screen_x < 0 || screen_x >= 160)
                                continue; // Off-screen
                            int palid = ((row1 & 1) << 1) | (row0 & 1);
                            row1 >>= 1;
                            row0 >>= 1;
                            int shade = bg_pal[palid] * 85; // Transform [0,3] to [0,255]
                            screen.putPixel(screen_x, line, (shade << 16) | (shade << 8) | shade);
                            z_buf[line * 160 + screen_x] = palid;
                        }
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
        mode_cycles += cycles;
        switch(mode){
            case 0: // Hblank
                if(mode_cycles  >= 51){
                    mode_cycles -= 51;
                    line++;
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
                        sync_time();
                        machine.interrupts_fired |= 1; // Vblank interrupt
                        if(vblank_int)
                            machine.interrupts_fired |= 0x2;
                        mode = 1;
                    }
                }
                break;
            case 1: // Vblank
                if(mode_cycles >= 114){
                    mode_cycles -= 114;
                    line++;
                    if(line >= 154) {
                        if(oam_int)
                            machine.interrupts_fired |= 0x2;
                        mode = 2;
                        line = 0;
                        window_line = 0;
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

    public int read(int address){
        switch(address >> 12){
            case 0x8: // VRAM
            case 0x9:
                return vram[address & 0x1fff] & 0xff;
            case 0xf:
                switch((address >> 8) & 0xf) {
                    case 0xe: { // OAM
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
                                if (sprite.use_pal1) flags |= 0x10;
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
                        }
                        break;
                    }
                }
        }
        return 0;
    }

    public void write(int address, int value){
        switch(address >> 12){
            case 0x8: // VRAM
            case 0x9:
                vram[address & 0x1fff] = (byte)value;
                break;
            case 0xf: // OAM and registers
                switch((address >> 8) & 0xf) {
                    case 0xe: // OAM
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
                                sprite.use_pal1 = (value & 0x10) != 0;
                                break;
                            }
                        }
                        break;
                    case 0xf: // Registers
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
                        }
                }
        }
    }

}
