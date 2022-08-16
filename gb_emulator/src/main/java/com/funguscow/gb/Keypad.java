package com.funguscow.gb;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Handles joypad input for gameboy
 */
public class Keypad {

    public static final int KEY_RIGHT = 0,
                            KEY_LEFT = 1,
                            KEY_UP = 2,
                            KEY_DOWN = 3,
                            KEY_A = 4,
                            KEY_B = 5,
                            KEY_SELECT = 6,
                            KEY_START = 7;

    private int keysUp = 0xff;
    private boolean p14, p15;
    private final Machine machine;

    /**
     *
     * @param machine Parent machine
     */
    public Keypad(Machine machine) {
        this.machine = machine;
    }

    /**
     * Write the register value
     * @param val Value being written
     */
    public void write(int val){
        p14 = (val & 0x10) == 0;
        p15 = (val & 0x20) == 0;
    }

    /**
     *
     * @return Current keypad value
     */
    public int read(){
        int keys = 0;
        if(p14)
            keys |= keysUp & 0xf;
        if(p15)
            keys |= keysUp >> 4;
        return keys;
    }

    /**
     * Processed when a key goes down
     * @param key Key code
     */
    public void keyDown(int key){
        if((keysUp & (1 << key)) != 0){
            machine.interruptsFired |= 0x10;
            machine.stop = false;
        }
        keysUp &= ~(1 << key);
    }

    /**
     * Processed when a key is released
     * @param key Key code
     */
    public void keyUp(int key){
        keysUp |= (1 << key);
    }

    /**
     * Save key state
     * @param dos Output destination stream
     * @throws IOException From inner writes
     */
    public void save(DataOutputStream dos) throws IOException {
        dos.write("JOYP".getBytes(StandardCharsets.UTF_8));
        dos.writeInt(keysUp);
        dos.writeBoolean(p14);
        dos.writeBoolean(p15);
    }

    /**
     * Load state
     * @param dis Source stream
     * @throws IOException From inner reads
     */
    public void load(DataInputStream dis) throws IOException {
        keysUp = dis.readInt();
        p14 = dis.readBoolean();
        p15 = dis.readBoolean();
    }

}
