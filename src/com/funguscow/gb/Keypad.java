package com.funguscow.gb;

public class Keypad {

    public static final int KEY_RIGHT = 0,
                            KEY_LEFT = 1,
                            KEY_UP = 2,
                            KEY_DOWN = 3,
                            KEY_A = 4,
                            KEY_B = 5,
                            KEY_SELECT = 6,
                            KEY_START = 7;

    int keysUp = 0xff;
    boolean p14, p15;
    Machine machine;

    public void write(int val){
        p14 = (val & 0x10) == 0;
        p15 = (val & 0x20) == 0;
    }

    public int read(){
        int keys = 0;
        if(p14)
            keys |= keysUp & 0xf;
        if(p15)
            keys |= keysUp >> 4;
        return keys;
    }

    public void keyDown(int key){
        if((keysUp & (1 << key)) != 0){
            machine.interruptsFired |= 0x10;
            machine.stop = false;
        }
        keysUp &= ~(1 << key);
    }

    public void keyUp(int key){
        keysUp |= (1 << key);
    }

}
