package com.funguscow.gb;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Timer component
 */
public class Timer {

    // NOTE: Allegedly, the divider is actually in m-cycles, not t-cycles,
    // hence why the bit counts are off-by-2

    // Accessed in initialization
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
    public void increment(int cycles){
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

    /**
     * Trigger an interrupt if the right bits are flipped
     */
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

    /**
     *
     * @param address Address to read
     * @return Read value
     */
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

    /**
     *
     * @param address Address to write
     * @param value Value to write
     */
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

    /**
     * Save Timer state
     * @param dos Dest stream
     * @throws IOException Errors writing
     */
    public void save(DataOutputStream dos) throws IOException {
        dos.write("TIME".getBytes(StandardCharsets.UTF_8));
        dos.writeInt(divider);
        dos.writeInt(tima);
        dos.writeInt(tma);
        dos.writeInt(tac);
        dos.writeBoolean(delayed);
        dos.writeBoolean(pendingOverflow);
    }

    /**
     * Load timer state
     * @param dis Source stream
     * @throws IOException Errors reading
     */
    public void load(DataInputStream dis) throws IOException {
        divider = dis.readInt();
        tima = dis.readInt();
        tma = dis.readInt();
        tac = dis.readInt();
        delayed = dis.readBoolean();
        pendingOverflow = dis.readBoolean();
    }

    /**
     * Print debug state of timer
     */
    public void printDebugState() {
        System.out.printf("DIV: 0x%x, TIMA: 0x%x, TMA: 0x%x, TAC: 0x%x\n", divider, tima, tma, tac);
        System.out.printf("Last latch? %s, overflow processing? %s\n", delayed, pendingOverflow);
    }

}
