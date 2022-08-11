package com.funguscow.gb;

/**
 * Timer component
 */
public class Timer {

    int divider;
    private int div_accum;
    private int tima;
    private int tma;
    private int tac;
    private int delta;

    private static final int[] PERIOD = {
            256, //4096 Hz
            4, // 262144 Hz
            16, // 65536 Hz
            64, // 16384 Hz
    };

    /**
     * Advance the timer by cycles m-cycles
     * @param machine Parent machine
     * @param cycles m-cycles to advance
     */
    public void incr(Machine machine, int cycles){
        div_accum += cycles;
        while(div_accum >= 64){
            divider ++;
            divider &= 0xff;
            div_accum -= 64;
        }
        if((tac & 4) != 0) {
            delta += cycles;
            int threshold = PERIOD[tac & 3];
            if (delta >= threshold && (tac & 4) != 0) {
                delta -= threshold;
                tima++;
                if (tima > 0xff) {
                    tima = tma;
                    machine.interrupts_fired |= 4;
                }
            }
        }
    }

    public int read(int address){
        switch(address){ // Already &3'd
            case 0:
                return divider;
            case 1:
                return tima;
            case 2:
                return tma;
            case 3:
                return tac;
        }
        return 0;
    }

    public void write(int address, int value){
        switch(address) { // ibid
            case 0:
                div_accum = 0;
                divider = 0; break;
            case 1:
                tima = value; break;
            case 2:
                tma = value; break;
            case 3:
                delta = 0;
                tac = value; break;
        }
    }

}
