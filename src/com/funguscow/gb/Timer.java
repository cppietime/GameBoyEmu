package com.funguscow.gb;

/**
 * Timer component
 */
public class Timer {

    // NOTE: Allegedly, the divider is actually in m-cycles, not t-cycles,
    // hence why the bit counts are off-by-2
    int divider;
    private int tima;
    private int tma;
    private int tac;
    private boolean delayed;
    private boolean pendingOverflow;
    private final Machine machine;

    private static final int[] DIV_BIT = {
            7,
            1,
            3,
            5
    };

    public Timer (Machine machine) {
        this.machine = machine;
    }

    /**
     * Advance the timer by cycles m-cycles
     * @param cycles m-cycles to advance
     */
    public void incr(int cycles){
        for (int i = 0; i < cycles; i++) {
            if (pendingOverflow) {
                tima = tma;
                machine.interruptsFired |= 4;
                pendingOverflow = false;
            }
            divider = (divider + 1) & 0xffff;
            updateEdge();
        }
    }

    private void updateEdge() {
        boolean bit = (divider & (1 << DIV_BIT[tac & 3])) != 0;
        bit &= (tac & 4) != 0;
        if (delayed && !bit) {
            tima += 1;
            if (tima > 0xff) {
                pendingOverflow = true;
                tima = 0;
            }
        }
        delayed = bit;
    }

    public int read(int address){
        switch(address){ // Already &3'd
            case 0:
                return (divider >> 6) & 0xff;
            case 1:
                return tima;
            case 2:
                return tma;
            case 3:
                return tac | 0xF8;
        }
        return 0;
    }

    public void write(int address, int value){
        switch(address) { // ibid
            case 0:
                divider = 0; break;
            case 1:
                pendingOverflow = false;
                tima = value; break;
            case 2:
                tma = value; break;
            case 3:
                value &= 7;
                tac = value; break;
        }
        updateEdge();
    }

}
