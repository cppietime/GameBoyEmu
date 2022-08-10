package com.funguscow.gb;

import java.util.HashSet;
import java.util.Set;

public class CPU {

    int m, m_delta;

    int a, b, c, d, e, f, h, l;
    int pc, sp, pc_delta;
    boolean zero, carry, half, subtract;
    boolean interrupts = true;
    boolean halt_bug = false;

    Debugger debugger;

    MMU mmu;

    public CPU(Machine.MachineMode mode, MMU mmu, Debugger debugger){
        this.mmu = mmu;
        a = mode.af_initial;
        zero = half = carry = true;
        subtract = false;
        b = 0x00;
        c = 0x13;
        d = 0x00;
        e = 0xd8;
        h = 0x01;
        l = 0x4d;
        sp = 0xfffe;
        pc = 0x100; // Start with the opcode at 0x100, as this is what's loaded after the BIOS
        this.debugger = debugger;
    }

    public void perform_op(Machine machine){
        pc &= 0xffff;
        if(machine.halt)
            m_delta = 1;
        else{
            int opcode = mmu.read8(pc);
            if(debugger != null)
                debugger.debug(pc, this, opcode);
            if(!halt_bug)
                pc ++;
            halt_bug = false;
            m_delta = opcode(machine, opcode);
        }
        m += m_delta;
        if(machine.interrupts_fired != 0) // Solely for  debugging
            ;//System.out.println(String.format("Enabled: %02x; Fired: %02x", machine.interrupts_enabled, machine.interrupts_fired));
//        if((machine.interrupts_fired & machine.interrupts_enabled) != 0)
//            machine.halt = false;
        int interrupt_handles = machine.interrupts_enabled & machine.interrupts_fired & 0x1f;
        if(interrupt_handles != 0){
            machine.halt = false;
            if(interrupts) {
                interrupts = false;
                if ((interrupt_handles & 1) != 0) { // V-blank
                    machine.interrupts_fired &= ~1;
                    int_rst(0x40);
                } else if ((interrupt_handles & 2) != 0) { // LCDC interrupt
                    machine.interrupts_fired &= ~2;
                    int_rst(0x48);
                } else if ((interrupt_handles & 4) != 0) { // Timer overflow
                    machine.interrupts_fired &= ~4;
                    int_rst(0x50);
                } else if ((interrupt_handles & 8) != 0) { // Serial transfer
                    machine.interrupts_fired &= ~8;
                    int_rst(0x58);
                } else if ((interrupt_handles & 16) != 0) { // P10-P13 Hi->Lo
                    machine.interrupts_fired &= ~16;
                    int_rst(0x60);
                } else
                    interrupts = true;
            }
        }
    }

    public void dump_registers(){
        System.out.println(String.format("Time: %d", m));
        System.out.println(String.format("b: %02x;\tc: %02x;", b, c));
        System.out.println(String.format("d: %02x;\te: %02x;", d, e));
        System.out.println(String.format("h: %02x;\tl: %02x;", h, l));
        System.out.println(String.format("a: %02x;", a));
        System.out.println(String.format("Zero: %s;\tHalf-carry: %s;\tCarry: %s;\tSubtraction: %s;", zero, half, carry, subtract));
        System.out.println(String.format("SP: %04x;", sp));
        System.out.println(String.format("PC: %04x: %02x;", pc, mmu.read8(pc)));
        System.out.println(String.format("Interrupts: %s;", interrupts));
    }

    private void int_rst(int address){
        System.out.println("Handling interrupt at " + address);
        sp -= 2;
        mmu.write16(sp, pc);
        pc = address;
    }

    private void unimplemented(int opcode){
        System.err.printf("ERROR: Unimplemented opcode %02x at PC = %04x!%n", opcode, pc - 1);
        //System.exit(1);
    }

    // For debugging only
    Set<Integer> calledOps = new HashSet<>();

    /**
     * Executes a given opcode
     * @param machine The containing machine
     * @param opcode Value of first byte of instruction
     * @return The number of m-cycles
     */
    public int opcode(Machine machine, int opcode){
//        if (mmu.left_bios) {
//            System.out.printf("%02x: %02x\n", pc - 1, opcode);
//        }
        calledOps.add(opcode);
        switch(opcode) {
            /* 0xX0 */
            case 0x00: // NOP
                return 1;
            case 0x10: // STOP
                System.out.printf("Stopping at %04xf\n", pc++);
//                machine.stop = true;
                // Currently no way to unset stop
                return 1;
            case 0x20: // JR NZ,n
                if (zero) {
                    pc++;
                }
                else {
                    pc = pc + 1 + (byte) mmu.read8(pc);
                }
                return 2;
            case 0x30: // JR NC,n
                if (carry)
                    pc++;
                else
                    pc = pc + 1 + (byte) mmu.read8(pc);
                return 2;
            case 0x40: // LD B,B - IDENTITY
                return 1;
            case 0x50: // LD D,B
                d = b;
                return 1;
            case 0x60: // LD H,B
                h = b;
                return 1;
            case 0x70: // LD (HL),B
                mmu.write8((h << 8) | l, b);
                return 2;
            case 0x80: // ADD A,B
                half = (a & 0xf) + (b & 0xf) > 0xf;
                subtract = false;
                a += b;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 1;
            case 0x90: // SUB A,B
                subtract = true;
                half = (b & 0xf) > (a & 0xf);
                a -= b;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 1;
            case 0xA0: // AND A,B
                a &= b;
                subtract = carry = false;
                zero = a == 0;
                half = true;
                return 1;
            case 0xB0: // OR A,B
                a |= b;
                subtract = carry = half = false;
                zero = a == 0;
                return 1;
            case 0xC0: // RET NZ
                if (!zero) {
                    pc = mmu.read16(sp);
                    sp += 2;
                }
                return 2;
            case 0xD0: // RET NC
                if (!carry) {
                    pc = mmu.read16(sp);
                    sp += 2;
                }
                return 2;
            case 0xE0: // LDH (FF00+n),A
                mmu.write8(0xff00 + mmu.read8(pc), a);
                pc++;
                return 3;
            case 0xF0: // LDH A,(FF00+n)
                a = mmu.read8(0xff00 + mmu.read8(pc));
                pc++;
                return 3;

            /* 0xX1 */
            case 0x01: // LD BC,nn
                c = mmu.read8(pc);
                b = mmu.read8(pc + 1);
                pc += 2;
                return 3;
            case 0x11: // LD DE,nn
                e = mmu.read8(pc);
                d = mmu.read8(pc + 1);
                pc += 2;
                return 3;
            case 0x21: // LD HL,nn
                l = mmu.read8(pc);
                h = mmu.read8(pc + 1);
                pc += 2;
                return 3;
            case 0x31: // LD SP,nn
                sp = mmu.read16(pc);
                pc += 2;
                return 3;
            case 0x41: // LD B,C
                b = c;
                return 1;
            case 0x51: // LD D,C
                d = c;
                return 1;
            case 0x61: // LD H,C
                h = c;
                return 1;
            case 0x71: // LD (HL),C
                mmu.write8((h << 8) | l, c);
                return 2;
            case 0x81: // ADD A,C
                half = (a & 0xf) + (c & 0xf) > 0xf;
                subtract = false;
                a += c;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 1;
            case 0x91: // SUB A,C
                subtract = true;
                half = (c & 0xf) > (a & 0xf);
                a -= c;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 1;
            case 0xA1: // AND A,C
                subtract = carry = false;
                half = true;
                a &= c;
                zero = a == 0;
                return 1;
            case 0xB1: // AND A,C
                subtract = carry = half = false;
                a |= c;
                zero = a == 0;
                return 1;
            case 0xC1: // POP BC
                c = mmu.read8(sp);
                b = mmu.read8(sp + 1);
                sp += 2;
                return 3;
            case 0xD1: // POP DE
                e = mmu.read8(sp);
                d = mmu.read8(sp + 1);
                sp += 2;
                return 3;
            case 0xE1: // POP HL
                l = mmu.read8(sp);
                h = mmu.read8(sp + 1);
                sp += 2;
                return 3;
            case 0xF1: // POP AF
            {
                int f = mmu.read8(sp);
                a = mmu.read8(sp + 1);
                sp += 2;
                carry = (f & 0x10) != 0;
                half = (f & 0x20) != 0;
                subtract = (f & 0x40) != 0;
                zero = (f & 0x80) != 0;
                return 3;
            }

            /* 0xX2 */
            case 0x02: //LD (BC),A
                mmu.write8((b << 8) | c, a);
                return 2;
            case 0x12: //LD (DE),A
                mmu.write8((d << 8) | e, a);
                return 2;
            case 0x22: // LDI (HL),A
                mmu.write8((h << 8) | l, a);
                l++;
                h += (l >> 8);
                l &= 0xff;
                h &= 0xff;
                return 2;
            case 0x32: // LDD (HL),A
                mmu.write8((h << 8) | l, a);
                l--;
                if (l < 0) {
                    h--;
                    l += 0x100;
                }
                l &= 0xff;
                h &= 0xff;
                return 2;
            case 0x42: // LD B,D
                b = d;
                return 1;
            case 0x52: // LD D,D - IDENTITY
                return 1;
            case 0x62: // LD H,D
                h = d;
                return 1;
            case 0x72: // LD (HL),D
                mmu.write8((h << 8) | l, d);
                return 2;
            case 0x82: // ADD A,D
                half = (a & 0xf) + (d & 0xf) > 0xf;
                subtract = false;
                a += d;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 1;
            case 0x92: // SUB A,D
                subtract = true;
                half = (d & 0xf) > (a & 0xf);
                a -= d;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 1;
            case 0xA2: // AND A,D
                subtract = carry = false;
                half = true;
                a &= d;
                zero = a == 0;
                return 1;
            case 0xB2: // AND A,D
                subtract = carry = half = false;
                a |= d;
                zero = a == 0;
                return 1;
            case 0xC2: // JP NZ
                if (zero)
                    pc += 2;
                else
                    pc = mmu.read16(pc);
                return 3;
            case 0xD2: // JP NC
                if (carry)
                    pc += 2;
                else
                    pc = mmu.read16(pc);
                return 3;
            case 0xE2: // LD (FF00+C),A
                mmu.write8(0xff00 + c, a);
                return 2;
            case 0xF2: // LD A,(FF00+C)
                a = mmu.read8(0xff00 + c);
                return 2;

            /* 0xX3 */
            case 0x03: //INC BC
                c++;
                b += c >> 8;
                c &= 0xff;
                b &= 0xff;
                return 2;
            case 0x13: // INC DE
                e++;
                d += e >> 8;
                e &= 0xff;
                d &= 0xff;
                return 2;
            case 0x23: // INC HL
                l++;
                h += l >> 8;
                l &= 0xff;
                h &= 0xff;
                return 2;
            case 0x33: // INC SP
                sp++;
                return 2;
            case 0x43: // LD B,E
                b = e;
                return 1;
            case 0x53: // LD D,E
                d = e;
                return 1;
            case 0x63: // LD H,E
                h = e;
                return 1;
            case 0x73: // LD (HL),E
                mmu.write8((h << 8) | l, e);
                return 2;
            case 0x83: // ADD A,E
                half = (a & 0xf) + (e & 0xf) > 0xf;
                subtract = false;
                a += e;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 1;
            case 0x93: // SUB A,E
                subtract = true;
                half = (e & 0xf) > (a & 0xf);
                a -= e;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 1;
            case 0xA3: // AND A,E
                subtract = carry = false;
                half = true;
                a &= e;
                zero = a == 0;
                return 1;
            case 0xB3: // AND A,E
                subtract = carry = half = false;
                a |= e;
                zero = a == 0;
                return 1;
            case 0xC3: // JP nn
                pc = mmu.read16(pc);
                return 3;
            //case 0xD3: REMOVED OPCODE OUT n,A
            //case 0xE3: REMOVED OPCODE EX (SP),HL
            case 0xF3: // DI
                interrupts = false;

                /* 0xX4 */
            case 0x04: //INC B
                b = (b + 1) & 0xff;
                subtract = false;
                half = (b & 0xf) == 0;
                zero = b == 0;
                return 1;
            case 0x14: // INC D
                subtract = false;
                half = (d & 0xf) == 0xf;
                d = (d + 1) & 0xff;
                zero = d == 0;
                return 1;
            case 0x24: // INC H
                subtract = false;
                half = (h & 0xf) == 0xf;
                h = (h + 1) & 0xff;
                zero = h == 0;
                return 1;
            case 0x34: // INC (HL) (byte)
            {
                int hl = mmu.read8((h << 8) | l);
                subtract = false;
                half = (hl & 0xf) == 0xf;
                hl = (hl + 1) & 0xff;
                zero = hl == 0;
                mmu.write8((h << 8) | l, hl);
                return 3;
            }
            case 0x44: // LD B,H
                b = h;
                return 1;
            case 0x54: // LD D,H
                d = h;
                return 1;
            case 0x64: // LD H,H - IDENTITY
                return 1;
            case 0x74: // LD (HL),H
                mmu.write8((h << 8) | l, h);
                return 2;
            case 0x84: // ADD A,H
                half = (a & 0xf) + (h & 0xf) > 0xf;
                subtract = false;
                a += h;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 1;
            case 0x94: // SUB A,H
                subtract = true;
                half = (h & 0xf) > (a & 0xf);
                a -= h;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 1;
            case 0xA4: // AND A,H
                subtract = carry = false;
                half = true;
                a &= h;
                zero = a == 0;
                return 1;
            case 0xB4: // AND A,H
                subtract = carry = half = false;
                a |= h;
                zero = a == 0;
                return 1;
            case 0xC4: // CALL NZ,nn
                if(zero)
                    pc += 2;
                else {
                    sp -= 2;
                    mmu.write16(sp, pc + 2);
                    pc = mmu.read16(pc);
                }
                return 3;
            case 0xD4: // CALL NC,nn
                if(carry)
                    pc += 2;
                else {
                    sp -= 2;
                    mmu.write16(sp, pc + 2);
                    pc = mmu.read16(pc);
                }
                return 3;
            //case 0xE4: REMOVED OPCODE CALL P
            //case 0xF4: REMOVED OPCODE CALL S

            /* 0xX5 */
            case 0x05: //DEC B
                b = (b - 1) & 0xff;
                subtract = true;
                half = (b & 0xf) == 0xf;
                zero = b == 0;
                return 1;
            case 0x15: // DEC D
                subtract = true;
                half = (d & 0xf) == 0;
                d = (d - 1) & 0xff;
                zero = d == 0;
                return 1;
            case 0x25: // DEC H
                subtract = true;
                half = (h & 0xf) == 0;
                h = (h - 1) & 0xff;
                zero = h == 0;
                return 1;
            case 0x35: // DEC (HL)
            {
                int hl = mmu.read8((h << 8) | l);
                subtract = true;
                half = (hl & 0xf) == 0;
                hl = (hl - 1) & 0xff;
                zero = hl == 0;
                mmu.write8((h << 8) | l, hl);
                return 3;
            }
            case 0x45: // LD B,L
                b = l;
                return 1;
            case 0x55: // LD D,L
                d = l;
                return 1;
            case 0x65: // LD H,L
                h = l;
                return 1;
            case 0x75: // LD (HL),L
                mmu.write8((h << 8) | l, l);
                return 2;
            case 0x85: // ADD A,L
                half = (a & 0xf) + (l & 0xf) > 0xf;
                subtract = false;
                a += l;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 1;
            case 0x95: // SUB A,L
                subtract = true;
                half = (l & 0xf) > (a & 0xf);
                a -= l;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 1;
            case 0xA5: // AND A,L
                subtract = carry = false;
                half = true;
                a &= l;
                zero = a == 0;
                return 1;
            case 0xB5: // AND A,L
                subtract = carry = half = false;
                a |= l;
                zero = a == 0;
                return 1;
            case 0xC5: // PUSH BC
                sp -= 2;
                mmu.write16(sp, (b << 8) | c);
                return 4;
            case 0xD5: // PUSH DE
                sp -= 2;
                mmu.write16(sp, (d << 8) | e);
                return 4;
            case 0xE5: // PUSH HL
                sp -= 2;
                mmu.write16(sp, (h << 8) | l);
                return 4;
            case 0xF5: // PUSH AF
            {
                int af = a << 8;
                if(zero)
                    af |= 0x80;
                if(subtract)
                    af |= 0x40;
                if(half)
                    af |= 0x20;
                if(carry)
                    af |= 0x10;
                sp -= 2;
                mmu.write16(sp, af);
                return 4;
            }

            /* 0xX6 */
            case 0x06: // LD B,n
                b = mmu.read8(pc);
                pc ++;
                return 2;
            case 0x16: // LD D,n
                d = mmu.read8(pc);
                pc ++;
                return 2;
            case 0x26: // LD H,n
                h = mmu.read8(pc);
                pc ++;
                return 2;
            case 0x36: // LD (HL),n
                mmu.write8((h << 8) | l, mmu.read8(pc));
                pc ++;
                return 3;
            case 0x46: // LD B,(HL)
                b = mmu.read8((h << 8) | l);
                return 2;
            case 0x56: // LD D,(HL)
                d = mmu.read8((h << 8) | l);
                return 2;
            case 0x66: // LD H,(HL)
                h = mmu.read8((h << 8) | l);
                return 2;
            case 0x76: // HALT
                if(interrupts)
                    machine.halt = true;
                else{
                    if((machine.interrupts_fired & machine.interrupts_enabled & 0x1f) != 0){
                        halt_bug = true;
                    }
                    else
                        machine.halt = true;
                }
                return 1;
            case 0x86: // ADD A,(HL)
            {
                int hl = mmu.read8((h << 8) | l);
                half = (a & 0xf) + (hl & 0xf) > 0xf;
                subtract = false;
                a += hl;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 2;
            }
            case 0x96: // SUB A,(HL)
            {
                int hl = mmu.read8((h << 8) | l);
                subtract = true;
                half = (hl & 0xf) > (a & 0xf);
                a -= hl;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 2;
            }
            case 0xA6: // AND A,(HL)
            {
                int hl = mmu.read8((h << 8) | l);
                subtract = carry = false;
                half = true;
                a &= hl;
                zero = a == 0;
                return 2;
            }
            case 0xB6: // OR A,(HL)
            {
                int hl = mmu.read8((h << 8) | l);
                subtract = carry = half = false;
                a |= hl;
                zero = a == 0;
                return 2;
            }
            case 0xC6: // ADD A,n
            {
                subtract = false;
                int n = mmu.read8(pc);
                pc ++;
                half = (a & 0xf) + (n & 0xf) > 0xf;
                a += n;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 2;
            }
            case 0xD6: // SUB A,n
            {
                subtract = true;
                int n = mmu.read8(pc);
                pc ++;
                half = (a & 0xf) < (n & 0xf);
                a -= n;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 2;
            }
            case 0xE6: // AND A,n
            {
                subtract = carry = false;
                int n = mmu.read8(pc);
                pc ++;
                half = true;
                a &= n;
                zero = a == 0;
                return 2;
            }
            case 0xF6: // OR A,n
            {
                subtract = carry = half = false;
                int n = mmu.read8(pc);
                pc ++;
                a |= n;
                zero = a == 0;
                return 2;
            }

            /* 0xX7 */
            case 0x07: //RLCA
                a <<= 1;
                carry = a > 0xff;
                a &= 0xff;
                a |= carry ? 1 : 0;
                subtract = half = false;
                zero = false;
                return 1;
            case 0x17: // RLA, treats carry as a buffer-9th bit, in contrast to RLCA above
                a <<= 1;
                a += carry ? 1 : 0;
                carry = a > 0xff;
                a &= 0xff;
                subtract = half = false;
                zero = false;
                return 1;
            case 0x27: // DAA
                int diff = 0;
                if(((a & 0xf) > 9 && !subtract) || half)
                    diff |= 0x6;
                if((a > 0x99) && !subtract || carry) {
                    diff |= 0x60;
                    carry = true;
                }
                a += subtract ? -diff : diff;
                a &= 0xff;
                half = false;
                zero = a == 0;
                return 1;
            case 0x37: // SCF
                carry = true;
                subtract = half = false;
                return 1;
            case 0x47: // LD B,A
                b = a;
                return 1;
            case 0x57: // LD D,A
                d = a;
                return 1;
            case 0x67: // LD H,A
                h = a;
                return 1;
            case 0x77: // LD (HL),A
                mmu.write8((h << 8) | l, a);
                return 2;
            case 0x87: // ADD A,A
                subtract = false;
                half = (a & 0xf) >= 0x8;
                carry = a >= 0x80;
                a = (a << 1) & 0xff;
                zero = a == 0;
                return 1;
            case 0x97: // SUB A,A
                a = 0;
                subtract = zero = true;
                half = carry = false;
                return 1;
            case 0xA7: // AND A,A
                subtract = carry = false;
                half = true;
                zero = a == 0;
                return 1;
            case 0xB7: // OR A,A
                subtract = carry = half = false;
                zero = a == 0;
                return 1;
            case 0xC7: // RST 0x00
                sp -= 2;
                mmu.write16(sp, pc);
                pc = 0x00;
                return 8;
            case 0xD7: // RST 0x10
                sp -= 2;
                mmu.write16(sp, pc);
                pc = 0x10;
                return 8;
            case 0xE7: // RST 0x20
                sp -= 2;
                mmu.write16(sp, pc);
                pc = 0x20;
                return 8;
            case 0xF7: // RST 0x30
                sp -= 2;
                mmu.write16(sp, pc);
                pc = 0x30;
                return 8;

            /* 0xX8 */
            case 0x08: // LD (nn),SP
                mmu.write16(mmu.read16(pc), sp);
                pc += 2;
                return 5;
            case 0x18: // JR n
                pc += 1 + (byte)mmu.read8(pc);
                return 2;
            case 0x28: // JR Z,n
                if(zero)
                    pc += 1 + (byte)mmu.read8(pc);
                else
                    pc ++;
                return 2;
            case 0x38: // JR c,n
                if(carry)
                    pc += 1 + (byte)mmu.read8(pc);
                else
                    pc ++;
                return 2;
            case 0x48: // LD C,B
                c = b;
                return 1;
            case 0x58: // LD E,B
                e = b;
                return 1;
            case 0x68: // LD L,B
                l = b;
                return 1;
            case 0x78: // LD A,B
                a = b;
                return 1;
            case 0x88: // ADC A,B
            {
                int cf = carry ? 1 : 0;
                subtract = false;
                half = (a & 0xf) + (b & 0xf) + cf > 0xf;
                a += b + cf;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 1;
            }
            case 0x98: // SBC A,B
            {
                int cf = carry ? 1 : 0;
                subtract = true;
                half = (a & 0xf) < ((b & 0xf) + cf);
                a -= b + cf;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 1;
            }
            case 0xA8: // XOR A,B
                subtract = half = carry = false;
                a ^= b;
                zero = a == 0;
                return 1;
            case 0xB8: // CMP A,B
            {
                subtract = true;
                zero = a == b;
                carry = a < b;
                half = (a & 0xf) < (b & 0xf);
                return 1;
            }
            case 0xC8: // RET Z
                if(zero){
                    pc = mmu.read16(sp);
                    sp += 2;
                }
                return 2;
            case 0xD8: // RET C
                if(carry){
                    pc = mmu.read16(sp);
                    sp += 2;
                }
                return 2;
            case 0xE8: // ADD SP,n (byte)
            {
                int n = (byte)mmu.read8(pc);
                pc ++;
                zero = subtract = false;
                half = (sp & 0xf) + (n & 0xf) > 0xf;
                carry = (sp & 0xff) + (n & 0xff) > 0xff;
                sp += n;
                sp &= 0xffff;
                return 4;
            }
            case 0xF8: // LDHL SP,n (byte)
            {
                int n = (byte)mmu.read8(pc);
                pc ++;
                zero = subtract = false;
                half  = (sp & 0xf) + (n & 0xf) > 0xf;
                carry = (sp & 0xff) + (n & 0xff) > 0xff;
                n += sp;
                n &= 0xffff;
                h = n >> 8;
                l = n & 0xff;
                return 3;
            }

            /* 0xX9 */
            case 0x09: // ADD HL,BC
            {
                subtract = false;
                int low = c + l;
                l = low & 0xff;
                low >>= 8;
                half = (h & 0xf) + (b & 0xf) + (low & 0xf) > 0xf;
                h = h + b + low;
                carry = h > 0xff;
                h &= 0xff;
                return 2;
            }
            case 0x19: // ADD HL,DE
            {
                subtract = false;
                int low = e + l;
                l = low & 0xff;
                low >>= 8;
                half = (h & 0xf) + (d & 0xf) + (low & 0xf) > 0xf;
                h = h + d + low;
                carry = h > 0xff;
                h &= 0xff;
                return 2;
            }
            case 0x29: // ADD HL,HL
            {
                subtract = false;
                l <<= 1;
                int low = l >> 8;
                l &= 0xff;
                half = ((h & 0xf) << 1) + (low & 0xf) > 0xf;
                h = (h << 1) + low;
                carry = h > 0xff;
                h &= 0xff;
                return 2;
            }
            case 0x39: // ADD HL,SP
            {
                subtract = false;
                int hl = (h << 8) | l;
                half = (hl & 0xfff) + (sp & 0xfff) > 0xfff;
                hl += sp;
                carry = hl > 0xffff;
                hl &= 0xffff;
                h = hl >> 8;
                l = hl & 0xff;
                return 2;
            }
            case 0x49: // LD C,C - IDENTITY
                return 1;
            case 0x59: // LD E,C
                e = c;
                return 1;
            case 0x69: // LD L,C
                l = c;
                return 1;
            case 0x79: // LD A,C
                a = c;
                return 1;
            case 0x89: // ADC A,C
            {
                int cf = carry ? 1 : 0;
                subtract = false;
                half = (a & 0xf) + (c & 0xf) + cf > 0xf;
                a += c + cf;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 1;
            }
            case 0x99: // SBC A,C
            {
                int cf = carry ? 1 : 0;
                subtract = true;
                half = (a & 0xf) < ((c & 0xf) + cf);
                a -= c + cf;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 1;
            }
            case 0xA9: // XOR A,C
                subtract = half = carry = false;
                a ^= c;
                zero = a == 0;
                return 1;
            case 0xB9: // CMP A,C
            {
                subtract = true;
                zero = a == c;
                carry = a < c;
                half = (a & 0xf) < (c & 0xf);
                return 1;
            }
            case 0xD9: // RETI
                interrupts = true;
                /* Fall through */
            case 0xC9: // RET
                pc = mmu.read16(sp);
                sp += 2;
                return 2;
            case 0xE9: // JP (HL)
                pc = (h << 8) | l;
                return 1;
            case 0xF9: // LD SP,HL
                sp = (h << 8) | l;
                return 1;

            /* 0xXA */
            case 0x0A: // LD A,(BC)
                a = mmu.read8((b << 8) | c);
                return 2;
            case 0x1A: // LD A,(DE)
                a = mmu.read8((d << 8) | e);
                return 2;
            case 0x2A: // LDI A,(HL)
            {
                int hl = (h << 8) | l;
                a = mmu.read8(hl);
                hl ++;
                l = hl & 0xff;
                h = hl >> 8;
                return 2;
            }
            case 0x3A: // LDD A,(HL)
            {
                int hl = (h << 8) | l;
                a = mmu.read8(hl);
                hl --;
                l = hl & 0xff;
                h = hl >> 8;
                return 2;
            }
            case 0x4A: // LD C,D
                c = d;
                return 1;
            case 0x5A: // LD E,D
                e = d;
                return 1;
            case 0x6A: // LD L,D
                l = d;
                return 1;
            case 0x7A: // LD A,D
                a = d;
                return 1;
            case 0x8A: // ADD A,D
            {
                int cf = carry ? 1 : 0;
                subtract = false;
                half = (a & 0xf) + (d & 0xf) + cf > 0xf;
                a += d + cf;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 1;
            }
            case 0x9A: // SBC A,D
            {
                int cf = carry ? 1 : 0;
                subtract = true;
                half = (a & 0xf) < ((d & 0xf) + cf);
                a -= d + cf;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 1;
            }
            case 0xAA: // XOR A,D
                subtract = half = carry = false;
                a ^= d;
                zero = a == 0;
                return 1;
            case 0xBA: // CMP A,D
            {
                subtract = true;
                zero = a == d;
                carry = a < d;
                half = (a & 0xf) < (d & 0xf);
                return 1;
            }
            case 0xCA: // JZ nn
                if(zero)
                    pc = mmu.read16(pc);
                else
                    pc += 2;
                return 3;
            case 0xDA: // JC nn
                if(carry)
                    pc = mmu.read16(pc);
                else
                    pc += 2;
                return 3;
            case 0xEA: //LD (nn),A
                mmu.write8(mmu.read16(pc), a);
                pc += 2;
                return 4;
            case 0xFA: // LD A(nn)
                a = mmu.read8(mmu.read16(pc));
                pc += 2;
                return 4;

            /* 0xXB */
            case 0x0B: // DEC BC
            {
                int bc = (((b << 8) | c) - 1) & 0xffff;
                b = bc >> 8;
                c = bc & 0xff;
                return 2;
            }
            case 0x1B: // DEC DE
            {
                int de = (((d << 8) | e) - 1) & 0xffff;
                d = de >> 8;
                e = de & 0xff;
                return 2;
            }
            case 0x2B: // DEC HL
            {
                int hl = (((h << 8) | l) - 1) & 0xffff;
                h = hl >> 8;
                l = hl & 0xff;
                return 2;
            }
            case 0x3B: // DEC SP
                sp --;
                return 2;
            case 0x4B: // LD C,E
                c = e;
                return 1;
            case 0x5B: // LD E,E - IDENTITY
                return 1;
            case 0x6B: // LD L,E
                l = e;
                return 1;
            case 0x7B: // LD A,E
                a = e;
                return 1;
            case 0x8B: // ADD A,E
            {
                int cf = carry ? 1 : 0;
                subtract = false;
                half = (a & 0xf) + (e & 0xf) + cf > 0xf;
                a += e + cf;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 1;
            }
            case 0x9B: // SBC A,E
            {
                int cf = carry ? 1 : 0;
                subtract = true;
                half = (a & 0xf) < ((e & 0xf) + cf);
                a -= e + cf;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 1;
            }
            case 0xAB: // XOR A,E
                subtract = half = carry = false;
                a ^= e;
                zero = a == 0;
                return 1;
            case 0xBB: // CMP A,E
            {
                subtract = true;
                zero = a == e;
                carry = a < e;
                half = (a & 0xf) < (e & 0xf);
                return 1;
            }
            case 0xCB: // CB extra instruction
                return extra_cb(mmu.read8(pc++));
            //case 0xDB: REMOVED INSTRUCTION IN A,n
            //case 0xEB: REMOVED INSTRUCTION EX DE,HL
            case 0xFB: // EI
                interrupts = true;
                return 1;

            /* 0xXC */
            case 0x0C: // INC C
                c = (c + 1) & 0xff;
                half = (c & 0xf) == 0;
                zero = c == 0;
                subtract = false;
                return 1;
            case 0x1C: // INC E
                e = (e + 1) & 0xff;
                half = (e & 0xf) == 0;
                zero = e == 0;
                subtract = false;
                return 1;
            case 0x2C: // INC L
                l = (l + 1) & 0xff;
                half = (l & 0xf) == 0;
                zero = l == 0;
                subtract = false;
                return 1;
            case 0x3C: // INC A
                a = (a + 1) & 0xff;
                half = (a & 0xf) == 0;
                zero = a == 0;
                subtract = false;
                return 1;
            case 0x4C: // LD C,H
                c = h;
                return 1;
            case 0x5C: // LD E,H
                e = h;
                return 1;
            case 0x6C: // LD L,H
                l = h;
                return 1;
            case 0x7C: // LD A,H
                a = h;
                return 1;
            case 0x8C: // ADD A,H
            {
                int cf = carry ? 1 : 0;
                subtract = false;
                half = (a & 0xf) + (h & 0xf) + cf > 0xf;
                a += h + cf;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 1;
            }
            case 0x9C: // SBC A,H
            {
                int cf = carry ? 1 : 0;
                subtract = true;
                half = (a & 0xf) < ((h & 0xf) + cf);
                a -= h + cf;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 1;
            }
            case 0xAC: // XOR A,H
                subtract = half = carry = false;
                a ^= h;
                zero = a == 0;
                return 1;
            case 0xBC: // CMP A,H
            {
                subtract = true;
                zero = a == h;
                carry = a < h;
                half = (a & 0xf) < (h & 0xf);
                return 1;
            }
            case 0xCC: // CALL Z nn
                if(zero) {
                    sp -= 2;
                    mmu.write16(sp, pc + 2);
                    pc = mmu.read16(pc);
                }
                else
                    pc += 2;
                return 3;
            case 0xDC: // CALL C nn
                if(carry) {
                    sp -= 2;
                    mmu.write16(sp, pc + 2);
                    pc = mmu.read16(pc);
                }
                else
                    pc += 2;
                return 3;
            //case 0xEC REMOVED INSTRUCTION
            //case 0xFC REMOVED INSTRUCTION

            /* 0xXD */
            case 0x0D: // DEC C
                c = (c - 1) & 0xff;
                half = (c & 0xf) == 0xf;
                zero = c == 0;
                subtract = true;
                return 1;
            case 0x1D: // DEC E
                e = (e - 1) & 0xff;
                half = (e & 0xf) == 0xf;
                zero = e == 0;
                subtract = true;
                return 1;
            case 0x2D: // DEC L
                l = (l - 1) & 0xff;
                half = (l & 0xf) == 0xf;
                zero = l == 0;
                subtract = true;
                return 1;
            case 0x3D: // DEC A
                a = (a - 1) & 0xff;
                half = (a & 0xf) == 0xf;
                zero = a == 0;
                subtract = true;
                return 1;
            case 0x4D: // LD C,L
                c = l;
                return 1;
            case 0x5D: // LD E,L
                e = l;
                return 1;
            case 0x6D: // LD L,L - IDENTITY
                return 1;
            case 0x7D: // LD A,L
                a = l;
                return 1;
            case 0x8D: // ADD A,L
            {
                int cf = carry ? 1 : 0;
                subtract = false;
                half = (a & 0xf) + (l & 0xf) + cf > 0xf;
                a += l + cf;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 1;
            }
            case 0x9D: // SBC A,L
            {
                int cf = carry ? 1 : 0;
                subtract = true;
                half = (a & 0xf) < ((l & 0xf) + cf);
                a -= l + cf;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 1;
            }
            case 0xAD: // XOR A,L
                subtract = half = carry = false;
                a ^= l;
                zero = a == 0;
                return 1;
            case 0xBD: // CMP A,L
            {
                subtract = true;
                zero = a == l;
                carry = a < l;
                half = (a & 0xf) < (l & 0xf);
                return 1;
            }
            case 0xCD: // CALL nn
                sp -= 2;
                mmu.write16(sp, pc + 2);
                pc = mmu.read16(pc);
                return 3;
            //case 0xDD REMOVED IX INSTRUCTIONS
            //case 0xED REMOVED EXTD INSTRUCTIONS
            //case 0xFD REMOVED IY INSTRUCTIONS

            /* 0xXE */
            case 0x0E: // LD C,n
                c = mmu.read8(pc);
                pc ++;
                return 2;
            case 0x1E: // LD E,n
                e = mmu.read8(pc);
                pc ++;
                return 2;
            case 0x2E: // LD L,n
                l = mmu.read8(pc);
                pc ++;
                return 2;
            case 0x3E: // LD A,n
                a = mmu.read8(pc);
                pc ++;
                return 2;
            case 0x4E: // LD C,(HL)
                c = mmu.read8((h << 8) | l);
                return 2;
            case 0x5E: // LD E,(HL)
                e = mmu.read8((h << 8) | l);
                return 2;
            case 0x6E: // LD L,(HL)
                l = mmu.read8((h << 8) | l);
                return 2;
            case 0x7E: // LD A,(HL)
                a = mmu.read8((h << 8) | l);
                return 2;
            case 0x8E: // ADD A,(HL)
            {
                int cf = carry ? 1 : 0;
                int hl = mmu.read8((h << 8) | l);
                subtract = false;
                half = (a & 0xf) + (hl & 0xf) + cf > 0xf;
                a += hl + cf;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 2;
            }
            case 0x9E: // SBC A,(HL)
            {
                int hl = mmu.read8((h << 8) | l);
                int cf = carry ? 1 : 0;
                subtract = true;
                half = (a & 0xf) < ((hl & 0xf) + cf);
                a -= hl + cf;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 2;
            }
            case 0xAE: // XOR A,(HL)
                subtract = half = carry = false;
                a ^= mmu.read8((h << 8) | l);
                zero = a == 0;
                return 1;
            case 0xBE: // CMP A,(HL)
            {
                int hl = mmu.read8((h << 8) | l);
                subtract = true;
                zero = a == hl;
                carry = a < hl;
                half = (a & 0xf) < (hl & 0xf);
                return 1;
            }
            case 0xCE: // ADC A,n
            {
                int cf = carry ? 1 : 0;
                int n = mmu.read8(pc);
                pc ++;
                subtract = false;
                half = (a & 0xf) + (n & 0xf) + cf > 0xf;
                a += n + cf;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 1;
            }
            case 0xDE: // SBC A,n
            {
                int n = mmu.read8(pc);
                pc ++;
                int cf = carry ? 1 : 0;
                subtract = true;
                half = (a & 0xf) < ((n & 0xf) + cf);
                a -= n + cf;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 2;
            }
            case 0xEE: // XOR A,n
                half = carry = subtract = false;
                a ^= mmu.read8(pc);
                pc ++;
                zero = a == 0;
                return 2;
            case 0xFE: // CMP A,n
            {
                int n = mmu.read8(pc);
                pc ++;
                half = (a & 0xf) < (n & 0xf);
                subtract = true;
                carry = a < n;
                zero = a == n;
                return 2;
            }

            /* 0xXF */
            case 0x0F: // RRCA
                carry = (a & 1) == 1;
                half = subtract = false;
                a >>= 1;
                a |= carry ? 0x80 : 0;
                zero = false;
                return 1;
            case 0x1F: // RRA
                subtract = half = zero = false;
                if(carry)
                    a |= 0x100;
                carry = (a & 1) == 1;
                a >>= 1;
                return 1;
            case 0x2F: // CPL A
                half = subtract = true;
                a = (~a) & 0xff;
                return 1;
            case 0x3F: // CCF (or CPL CARRY)
                carry = !carry;
                subtract = half = false;
                return 1;
            case 0x4F: // LD C,A
                c = a;
                return 1;
            case 0x5F: // LD E,A
                e = a;
                return 1;
            case 0x6F: // LD L,A
                l = a;
                return 1;
            case 0x7F: // LD A,A - IDENTITY
                return 1;
            case 0x8F: // ADC A,A
            {
                int cf = carry ? 1 : 0;
                subtract = false;
                half = ((a & 0xf) << 1) + cf > 0xf;
                a = (a << 1) + cf;
                carry = a > 0xff;
                a &= 0xff;
                zero = a == 0;
                return 1;
            }
            case 0x9F: // SBC A,A
            {
                int cf = carry ? 1 : 0;
                subtract = true;
                half = (a & 0xf) < ((a & 0xf) + cf);
                a = -cf;
                carry = a < 0;
                a &= 0xff;
                zero = a == 0;
                return 2;
            }
            case 0xAF: // XOR A,A
                a = 0;
                zero = true;
                subtract = half = carry = false;
                return 1;
            case 0xBF: // CMP A,A
                zero = subtract = true;
                half = carry = false;
                return 1;
            case 0xCF: // RST 0x08
                sp -= 2;
                mmu.write16(sp, pc);
                pc = 0x08;
                return 8;
            case 0xDF: // RST 0x18
                sp -= 2;
                mmu.write16(sp, pc);
                pc = 0x18;
                return 8;
            case 0xEF: // RST 0x28
                sp -= 2;
                mmu.write16(sp, pc);
                pc = 0x28;
                return 8;
            case 0xFF: // RST 0x38
                sp -= 2;
                mmu.write16(sp, pc);
                pc = 0x38;
                return 8;
            default:
                unimplemented(opcode);
        }
        return 1;
    }

    private void set_register(int id, int value){
        switch(id){
            case 0:
                b = value; break;
            case 1:
                c = value; break;
            case 2:
                d = value; break;
            case 3:
                e = value; break;
            case 4:
                h = value; break;
            case 5:
                l = value; break;
            case 6:
                mmu.write8((h << 8) | l, value); break;
            case 7:
                a = value; break;
        }
    }

    private int get_register(int id){
        switch(id){
            case 0: return b;
            case 1: return c;
            case 2: return d;
            case 3: return e;
            case 4: return h;
            case 5: return l;
            case 6: return mmu.read8((h << 8) | l);
            case 7: return a;
        }
        System.err.printf("ERROR: Invalid register numbered %d", id);
        System.exit(2);
        return 0;
    }

    private int rlc(int reg){
        reg <<= 1;
        carry = reg > 0xff;
        reg &= 0xff;
        reg |= carry ? 1 : 0;
        subtract = half = false;
        zero = reg == 0;
        return reg;
    }

    private int rrc(int reg){
        carry = (reg & 1) != 0;
        reg >>= 1;
        reg |= carry ? 0x80 : 0;
        subtract = half = false;
        zero = reg == 0;
        return reg;
    }

    private int rotl(int reg){
        reg <<= 1;
        reg |= carry ? 1 : 0;
        carry = reg > 0xff;
        reg &= 0xff;
        subtract = half = false;
        zero = reg == 0;
        return reg;
    }

    private int rotr(int reg){
        reg |= carry ? 0x100 : 0;
        carry = (reg & 1) != 0;
        reg >>= 1;
        subtract = half = false;
        zero = reg == 0;
        return reg;
    }

    private int shl(int reg){
        carry = (reg & 0x80) != 0;
        reg <<= 1;
        reg &= 0xff;
        subtract = half = false;
        zero = reg == 0;
        return reg;
    }

    private int shr(int reg){
        carry = (reg & 1) != 0;
        reg >>= 1;
        subtract = half = false;
        zero = reg == 0;
        return reg;
    }

    private int shar(int reg){
        carry = (reg & 1) != 0;
        reg >>= 1;
        if(reg >= 0x40)
            reg |= 0x80;
        subtract = half = false;
        zero = reg == 0;
        return reg;
    }

    private int swap(int reg){
        half = subtract = carry = false;
        reg = ((reg >> 4) | (reg << 4)) & 0xff;
        zero = reg == 0;
        return reg;
    }

    private void bit(int reg, int bit){
        subtract = false;
        half = true;
        zero = (reg & (1 << bit)) == 0;
    }

    private int set(int reg, int bit){
        return reg | (1 << bit);
    }

    private int reset(int reg, int bit){
        return reg & ~(1 << bit);
    }

    private int extra_cb(int opcode){
        int register = opcode & 0x7;
        int operation = opcode & 0xf8;
        switch(operation){
            case 0x00: //RLC
                set_register(register, rlc(get_register(register)));
                break;
            case 0x08: //RRC
                set_register(register, rrc(get_register(register)));
                break;
            case 0x10: //RL
                set_register(register, rotl(get_register(register)));
                break;
            case 0x18: //RL
                set_register(register, rotr(get_register(register)));
                break;
            case 0x20: //SHL
                set_register(register, shl(get_register(register)));
                break;
            case 0x28: //SHAR
                set_register(register, shar(get_register(register)));
                break;
            case 0x30: //SWAP
                set_register(register, swap(get_register(register)));
                break;
            case 0x38: //SHR
                set_register(register, shr(get_register(register)));
                break;
            case 0x40:
            case 0x48:
            case 0x50:
            case 0x58:
            case 0x60:
            case 0x68:
            case 0x70:
            case 0x78:
                bit(get_register(register), (operation - 0x40) >> 3);
                break;
            case 0x80:
            case 0x88:
            case 0x90:
            case 0x98:
            case 0xA0:
            case 0xA8:
            case 0xB0:
            case 0xB8:
                set_register(register, reset(get_register(register), (operation - 0x80) >> 3));
                break;
            case 0xC0:
            case 0xC8:
            case 0xD0:
            case 0xD8:
            case 0xE0:
            case 0xE8:
            case 0xF0:
            case 0xF8:
                set_register(register, set(get_register(register), (operation - 0xC0) >> 3));
                break;
        }
        return (register == 6) ? 4 : 2;
    }

}
