package com.funguscow.gb;

import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

/**
 * Debugger for GB emulator
 */
public class Debugger {

    private final Scanner scanner;
    private final Set<Integer> breakpoints;
    private final Set<Integer> lookout;
    private boolean paused;
    private int countdown;

    public Debugger(){
        this.scanner = new Scanner(System.in);
        breakpoints = new HashSet<>();
        lookout = new HashSet<>();
        paused = true;
    }

    public void debug(int pc, CPU cpu, int opcode){
        if(!paused && !breakpoints.contains(pc) && !lookout.contains(opcode))
            return;
        if((breakpoints.contains(pc) || lookout.contains(opcode)) && countdown > 0) {
            countdown--;
            return;
        }
        paused = true;
        cpu.dumpRegisters();
        input_loop:
        while(paused){
            String line = scanner.nextLine();
            if(line.length() == 0)
                continue;
            switch(line.charAt(0)){
                case 's': // Skip
                    break input_loop;
                case 'c': // Continue
                    paused = false;
                    break;
                case 'b': // Breakpoint
                {
                    int bp = Integer.parseInt(line.substring(1), 16);
                    breakpoints.add(bp);
                    break;
                }
                case 'd': // Delete
                {
                    int bp = Integer.parseInt(line.substring(1), 16);
                    breakpoints.remove(bp);
                    break;
                }
                case 'p': // Print
                {
                    int addr = Integer.parseInt(line.substring(1), 16);
                    int mem = cpu.mmu.read8(addr);
                    System.out.printf("%04x: %02x\n", addr, mem);
                    break;
                }
                case 'r': // Registers
                    cpu.dumpRegisters();
                    break;
                case 'n': // Countdown
                    countdown = Integer.parseInt(line.substring(1), 16);
                    break;
                case 'l': // Lookout
                    lookout.add(Integer.parseInt(line.substring(1), 16));
                    break;
                case 'e': // Erase
                    lookout.remove(Integer.parseInt(line.substring(1), 16));
                    break;
            }
        }
        System.out.println("Continuing...");
    }



}
