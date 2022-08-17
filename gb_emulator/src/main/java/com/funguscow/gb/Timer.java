package com.funguscow.gb;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
    private boolean pendingOverflow; // Used for save stating
    private final Machine machine;

    // Scheduling stuff
    private long lastTimestamp;
    private final List<Scheduler.Task> tasks = new ArrayList<>();

    private static final int[] DIV_BIT = {
            7,
            1,
            3,
            5
    };

    public Timer (Machine machine) {
        this.machine = machine;
        lastTimestamp = machine.getCyclesExecuted();
        scheduleTimaOverflow();
    }

    /**
     *
     * @param address Address to read
     * @return Read value
     */
    public int read(int address){
        switch(address){ // Already &3'd
            case 0:
                // When reading DIV, we may need to update it to the most recent value
                updateEarly();
                return (divider >> 6) & 0xff;
            case 1:
                // Same for TIMA
                updateEarly();
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
                // Update TIMA and saved time stamp, then reset DIV, and possibly schedule an overflow
                updateEarly();
                divider = 0;
                scheduleTimaOverflow();
                break;
            case 1:
                // Update DIV and saved time stamp, set TIMA, and possibly schedule an overflow
                updateEarly();
                tima = value;
                scheduleTimaOverflow();
                break;
            case 2:
                tma = value;
                break;
            case 3:
                updateEarly();
                tac = value & 7;
                scheduleTimaOverflow();
                break;
        }
    }

    /**
     * Save Timer state
     * @param dos Dest stream
     * @throws IOException Errors writing
     */
    public void save(DataOutputStream dos) throws IOException {
        dos.write("TIME".getBytes(StandardCharsets.UTF_8));
        updateEarly();
        dos.writeInt(divider);
        dos.writeInt(tima);
        dos.writeInt(tma);
        dos.writeInt(tac);
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
        pendingOverflow = dis.readBoolean();
        if (pendingOverflow && (tac & 4) != 0) {
            tasks.add(
                    machine.scheduler.add(
                            new Scheduler.Task(
                                    this::reloadTima,
                                    null,
                                    machine.getCyclesExecuted() + 1
                            )));
        }
    }

    /**
     * Print debug state of timer
     */
    public void printDebugState() {
        System.out.printf("DIV: 0x%x, TIMA: 0x%x, TMA: 0x%x, TAC: 0x%x\n", divider, tima, tma, tac);
        System.out.printf("Overflow processing? %s\n", pendingOverflow);
    }

    // Scheduling functions

    /**
     * Called internally when tasks are invalidated by an updated register
     */
    private void cancelAll() {
        // Cancel pending tasks
        for (Scheduler.Task task : tasks) {
            machine.scheduler.cancel(task);
        }
        tasks.clear();
        pendingOverflow = false;
    }

    /**
     * Called when loading a state, or otherwise when the scheduler is cleared
     */
    public void invalidateTasks() {
        tasks.clear();
    }

    /**
     * Schedules an overflow event of TIMA (when TIMA becomes 0) on the appropriate next cycle
     */
    public void scheduleTimaOverflow() {
        cancelAll();

        // Do nothing if TIMA is disabled
        if ((tac & 4) == 0) {
            return;
        }

        updateEarly();

        int timaTilOverflow = 0x100 - tima;
        int bitMask = 1 << (DIV_BIT[tac & 3] + 1);
        int divOfNextUpdate = (divider & ~(bitMask - 1)) + bitMask;
        int cyclesLeft = divOfNextUpdate - divider;
        timaTilOverflow -= 1;
        cyclesLeft += timaTilOverflow * bitMask;

        tasks.add(
                machine.scheduler.add(
                        new Scheduler.Task(
                                this::timaOverflow,
                                null,
                                lastTimestamp + cyclesLeft
                        )));
    }

    /**
     * Called by the scheduled TIMA overflow event
     */
    private void timaOverflow(long cycles, Object argument) {
        // Cancel pending tasks
        for (Scheduler.Task task : tasks) {
            machine.scheduler.cancel(task);
        }
        tasks.clear();

        // Overflow TIMA
        tima = 0;

        // Sync saved time
        divider += cycles - lastTimestamp;
        lastTimestamp = cycles;

        // Schedule reloading TIMA and triggering interrupt
        tasks.add(
                machine.scheduler.add(
                        new Scheduler.Task(
                                this::reloadTima,
                                null,
                                cycles + 1)
                ));
        pendingOverflow = true;
    }

    /**
     * Reloads TMA into TIMA and fires an interrupt
     * Called one cycle AFTER TIMA overflows
     */
    private void reloadTima(long cycles, Object argument) {
        // Cancel pending tasks
        for (Scheduler.Task task : tasks) {
            machine.scheduler.cancel(task);
        }
        tasks.clear();

        // Set TIMA
        tima = tma;
        machine.interruptsFired |= 0x4;

        // Sync saved time
        divider += cycles - lastTimestamp;
        lastTimestamp = cycles;

        // Schedule next TIMA overflow
        scheduleTimaOverflow();

        pendingOverflow = false;
    }

    /**
     * Call when the DIV and TIMA registers must be up to date in between scheduled events
     * As TIMA overflow is always handled by events, this should never need to reset/reload
     * TIMA, only increase it
     * Should cancel any scheduled events, set the new up to date values of DIV and TIMA,
     * and schedule any necessary events
     */
    public void updateEarly() {
        // Update DIV
        long cyclesPassed = machine.getCyclesExecuted() - lastTimestamp;
        int bitMask = 1 << (DIV_BIT[tac & 3] + 1);
        int divOfFirstIncrement = divider & ~(bitMask - 1);
        divOfFirstIncrement += bitMask;

        // TIMA must be updated at least once
        if ((tac & 4) != 0 && cyclesPassed >= divOfFirstIncrement - divider) {
            cyclesPassed -= divOfFirstIncrement - divider;
            divider = divOfFirstIncrement;
            tima ++;
            while (cyclesPassed >= bitMask) {
                cyclesPassed -= bitMask;
                divider += bitMask;
                tima ++;
            }
        }

        // Increase divider by any remaining cycles, or all cycles when TIMA is turned off
        divider += cyclesPassed;

        // Sync saved time
        lastTimestamp = machine.getCyclesExecuted();
    }

}
