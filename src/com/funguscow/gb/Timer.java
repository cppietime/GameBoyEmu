package com.funguscow.gb;

/**
 * Timer component
 */
public class Timer {

    private int divider;
    private int div_accum;
    private int tima;
    private int tma;
    private int tac;
    private int delta;

    /**
     * Advance the timer by cycles m-cycles
     * @param machine Parent machine
     * @param cycles m-cycles to advance
     */
    public void incr(Machine machine, int cycles){
        delta += cycles;
        if(delta >= 4){
            div_accum ++;
            if(div_accum >= 16){
                divider ++;
                div_accum = 0;
            }
            int threshold = 4 << ((tac & 3) << 1);
            if(delta >= threshold){
                delta -= threshold;
                tima++;
                if(tima > 0xff) {
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
                divider = 0; break;
            case 1:
                tima = value; break;
            case 2:
                tma = value; break;
            case 3:
                tac = value; break;
        }
    }

}
