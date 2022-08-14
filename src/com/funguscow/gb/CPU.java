package com.funguscow.gb;

import java.util.HashSet;
import java.util.Set;

public class CPU {

    int m, m_delta;

    int a, b, c, d, e, h, l;
    int pc, sp;
    boolean zero, carry, half, subtract;
    boolean interrupts = false;
    boolean halt_bug = false;

    int lastInt = 0;

    Debugger debugger;
    Logger logger;

    MMU mmu;

    public CPU(Machine.MachineMode mode, MMU mmu, Debugger debugger, Logger logger){
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
        pc = 0x00; // Start with the opcode at 0x100, as this is what's loaded after the BIOS
        this.debugger = debugger;
        this.logger = logger;
    }

    public int perform_op(Machine machine){
        pc &= 0xffff;
        if (checkInterrupt(machine)) {
            return m_delta;
        }
        if(machine.halt || machine.stop)
            m_delta = 1;
        else {
            if (pc == 0x100 && !mmu.left_bios) {
                mmu.left_bios = true;
            }
            int opcode = next8();
            if (logger != null) {
                logger.log(this);
            }
            if(debugger != null)
                debugger.debug(pc, this, opcode);
            m_delta = opcode(machine, opcode);
        }
        m += m_delta;
        return m_delta;
    }

    private boolean checkInterrupt(Machine machine) {
        int interrupt_handles = machine.interrupts_enabled & machine.interrupts_fired & 0x1f;
        if(interrupt_handles != 0){
            machine.halt = false;
            if(interrupts) {
                if ((interrupt_handles & 1) != 0) { // V-blank
                    machine.interrupts_fired &= ~1;
                    int_rst(0x40);
                    return true;
                } else if ((interrupt_handles & 2) != 0) { // LCDC interrupt
                    machine.interrupts_fired &= ~2;
                    int_rst(0x48);
                    return true;
                } else if ((interrupt_handles & 4) != 0) { // Timer overflow
                    machine.interrupts_fired &= ~4;
                    int_rst(0x50);
                    return true;
                } else if ((interrupt_handles & 8) != 0) { // Serial transfer
                    machine.interrupts_fired &= ~8;
                    int_rst(0x58);
                    return true;
                } else if ((interrupt_handles & 16) != 0) { // P10-P13 Hi->Lo
                    machine.interrupts_fired &= ~16;
                    int_rst(0x60);
                    return true;
                }
            }
        }
        return false;
    }

    public void dump_registers(){
        System.out.printf("Time: %d\n", m);
        System.out.printf("b: %02x;\tc: %02x;\n", b, c);
        System.out.printf("d: %02x;\te: %02x;\n", d, e);
        System.out.printf("h: %02x;\tl: %02x;\n", h, l);
        System.out.printf("a: %02x;\n", a);
        System.out.printf("Zero: %s;\tHalf-carry: %s;\tCarry: %s;\tSubtraction: %s;\n", zero, half, carry, subtract);
        System.out.printf("SP: %04x;\n", sp);
        System.out.printf("PC: %04x: %02x;\n", pc, mmu.read8(pc));
        System.out.printf("Interrupts: %s;\n", interrupts);
    }

    private void int_rst(int address){
//        System.out.println("Handling interrupt at " + address);
        lastInt = address;
        sp -= 2;
        mmu.write16(sp, pc);
        pc = address;
        m_delta = 5;
        interrupts = false;
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
//        if (!mmu.left_bios) {
//            System.out.printf("%02x: %02x - %04x\n", pc - 1, opcode, sp);
//        }
        calledOps.add(opcode);
        // Opcodes with common high nybbles
        switch (opcode >> 4) {
            case 0x4:
            case 0x5:
            case 0x6:
            case 0x7:
                if (opcode != 0x76) {
                    return ld8_r_r((opcode - 0x40) >> 3, opcode & 7);
                }
                break;
            case 0x8:
                return add8_r_rn(7, (opcode - 0x80) & 7, opcode >= 0x88);
            case 0x9:
                return sub8_r_r(7, (opcode - 0x90) & 7, opcode >= 0x98, true);
            case 0xA:
                return and_xor8_r_rn(7, (opcode - 0xA0) & 7, opcode >= 0xA8);
            case 0xB:
                if (opcode < 0xB8) {
                    return or8_r_rn(7, opcode - 0xB0);
                } else {
                    return sub8_r_r(7, opcode - 0xB8, false, false);
                }
        }
        switch(opcode) {
            // 8 bit Increments
            case 0x04:
                return inc8(0);
            case 0x0C:
                return inc8(1);
            case 0x14:
                return inc8(2);
            case 0x1C:
                return inc8(3);
            case 0x24:
                return inc8(4);
            case 0x2C:
                return inc8(5);
            case 0x34:
                return inc8(6);
            case 0x3C:
                return inc8(7);

            // 8 bit decrements
            case 0x05:
                return dec8(0);
            case 0x0D:
                return dec8(1);
            case 0x15:
                return dec8(2);
            case 0x1D:
                return dec8(3);
            case 0x25:
                return dec8(4);
            case 0x2D:
                return dec8(5);
            case 0x35:
                return dec8(6);
            case 0x3D:
                return dec8(7);

            // 16 bit add
            case 0x09:
                return add16_rp(10, 8);
            case 0x19:
                return add16_rp(10, 9);
            case 0x29:
                return add16_rp(10, 10);
            case 0x39:
                return add16_rp(10, 11);

            // 16 bit increment/decrement
            case 0x03:
                return inc_dec16(8, false);
            case 0x13:
                return inc_dec16(9, false);
            case 0x23:
                return inc_dec16(10, false);
            case 0x33:
                return inc_dec16(11, false);
            case 0x0B:
                return inc_dec16(8, true);
            case 0x1B:
                return inc_dec16(9, true);
            case 0x2B:
                return inc_dec16(10, true);
            case 0x3B:
                return inc_dec16(11, true);

            // 8-bit immediate load
            case 0x06:
                return ld8_imm(0);
            case 0x0E:
                return ld8_imm(1);
            case 0x16:
                return ld8_imm(2);
            case 0x1E:
                return ld8_imm(3);
            case 0x26:
                return ld8_imm(4);
            case 0x2E:
                return ld8_imm(5);
            case 0x36:
                return ld8_imm(6);
            case 0x3E:
                return ld8_imm(7);

            // Indirect store A
            case 0x02:
                return ld8_mem_r(8, 7);
            case 0x12:
                return ld8_mem_r(9, 7);
            case 0x77:
                return ld8_mem_r(10, 7);
            case 0xEA:
                return ld8_mem_r(-2, 7);
            case 0xE2:
                return ld8_mem_r(1, 7);

            // Indirect load A
            case 0x0A:
                return ld8_r_mem(7, 8);
            case 0x1A:
                return ld8_r_mem(7, 9);
            case 0x7E:
                return ld8_r_mem(7, 10);
            case 0xFA:
                return ld8_r_mem(7, -2);
            case 0xF2:
                return ld8_r_mem(7, 1);

            // LDD/I
            case 0x3A:
                return ld_a_hl_di(true);
            case 0x32:
                return ld_hl_a_di(true);
            case 0x2A:
                return ld_a_hl_di(false);
            case 0x22:
                return ld_hl_a_di(false);

            // Shadowed immediate memory
            case 0xE0:
                return ld_shadow(true);
            case 0xF0:
                return ld_shadow(false);

            // 16 bit immediates
            case 0x01:
                return ld16_imm(8);
            case 0x11:
                return ld16_imm(9);
            case 0x21:
                return ld16_imm(10);
            case 0x31:
                return ld16_imm(11);

            // Push/pop
            case 0xC1:
                return pop16(8);
            case 0xD1:
                return pop16(9);
            case 0xE1:
                return pop16(10);
            case 0xF1:
                return pop16(13);
            case 0xC5:
                return push16(8);
            case 0xD5:
                return push16(9);
            case 0xE5:
                return push16(10);
            case 0xF5:
                return push16(13);

            /* 0xX0 */
            case 0x00: // NOP
                return 1;
            case 0x10: // STOP
                System.out.printf("Stopping at %04xf\n", pc++);
//                machine.stop = true;
                // Currently no way to unset stop
                return 1;
            case 0x20: // JR NZ,n
                return jump_relative(!zero);
            case 0x30: // JR NC,n
                return jump_relative(!carry);
            case 0xC0: // RET NZ
                return return_conditional(!zero);
            case 0xD0: // RET NC
                return return_conditional(!carry);

            case 0xC2: // JP NZ
                return jump_immediate(!zero);
            case 0xD2: // JP NC
                return jump_immediate(!carry);
            case 0xC3: // JP nn
                return jump_immediate(true);

            //case 0xD3: REMOVED OPCODE OUT n,A
            //case 0xE3: REMOVED OPCODE EX (SP),HL
            case 0xF3: // DI
                interrupts = false;
                return 1;

            case 0xC4: // CALL NZ,nn
                return call(!zero);
            case 0xD4: // CALL NC,nn
                return call(!carry);
            //case 0xE4: REMOVED OPCODE CALL P
            //case 0xF4: REMOVED OPCODE CALL S

            case 0x76: // HALT
                // Not sure how the HALT bug is supposed to work here
                if(interrupts)
                    machine.halt = true;
                else{
//                    if((machine.interrupts_fired & machine.interrupts_enabled & 0x1f) != 0){
//                        halt_bug = true;
//                    }
//                    else
//                        machine.halt = true;
                    machine.halt = true;
                    halt_bug = true;
                }
                return 1;
            case 0xC6: // ADD A,n
                return add8_r_rn(7, -1, false);
            case 0xD6: // SUB A,n
                return sub8_r_r(7, -1, false, true);
            case 0xE6: // AND A,n
                return and_xor8_r_rn(7, -1, false);
            case 0xF6: // OR A,n
                return or8_r_rn(7, -1);

            /* 0xX7 */
            case 0x07: // RLCA
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
            case 0xC7: // RST 0x00
                return rst(0x00);
            case 0xD7: // RST 0x10
                return rst(0x10);
            case 0xE7: // RST 0x20
                return rst(0x20);
            case 0xF7: // RST 0x30
                return rst(0x30);

            /* 0xX8 */
            case 0x08: // LD (nn),SP
                return store_sp();
            case 0x18: // JR n
                return jump_relative(true);
            case 0x28: // JR Z,n
                return jump_relative(zero);
            case 0x38: // JR c,n
                return jump_relative(carry);
            case 0xC8: // RET Z
                return return_conditional(zero);
            case 0xD8: // RET C
                return return_conditional(carry);
            case 0xE8: // ADD SP,n (byte)
            {
                return add16_sp_imm();
            }
            case 0xF8: // LDHL SP,n (byte)
            {
                int n = (byte)next8();
                zero = subtract = false;
                half  = (sp & 0xf) + (n & 0xf) > 0xf;
                carry = (sp & 0xff) + (n & 0xff) > 0xff;
                n += sp;
                n &= 0xffff;
                h = n >> 8;
                l = n & 0xff;
                return 3;
            }

            case 0xD9: // RETI
                lastInt = 0;
                interrupts = true;
                /* Fall through */
            case 0xC9: // RET
                pc = mmu.read16(sp);
                sp += 2;
                return 4;
            case 0xE9: // JP HL
                pc = (h << 8) | l;
                return 1;
            case 0xF9: // LD SP,HL
                sp = (h << 8) | l;
                return 2;

            case 0xCA: // JP Z nn
                return jump_immediate(zero);
            case 0xDA: // JP C nn
                return jump_immediate(carry);

            case 0xCB: // CB extra instruction
                return extra_cb(next8());
            //case 0xDB: REMOVED INSTRUCTION IN A,n
            //case 0xEB: REMOVED INSTRUCTION EX DE,HL
            case 0xFB: // EI
                interrupts = true;
                return 1;

            case 0xCC: // CALL Z nn
                return call(zero);
            case 0xDC: // CALL C nn
                return call(carry);
            //case 0xEC REMOVED INSTRUCTION
            //case 0xFC REMOVED INSTRUCTION

            case 0xCD: // CALL nn
                return call(true);

            //case 0xDD REMOVED IX INSTRUCTIONS
            //case 0xED REMOVED EXTD INSTRUCTIONS
            //case 0xFD REMOVED IY INSTRUCTIONS


            case 0xCE: // ADC A,n
                return add8_r_rn(7, -1, true);
            case 0xDE: // SBC A,n
                return sub8_r_r(7, -1, true, true);
            case 0xEE: // XOR A,n
                return and_xor8_r_rn(7, -1, true);
            case 0xFE: // CMP A,n
                return sub8_r_r(7, -1, false, false);

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
            case 0xCF: // RST 0x08
                return rst(0x08);
            case 0xDF: // RST 0x18
                return rst(0x18);
            case 0xEF: // RST 0x28
                return rst(0x28);
            case 0xFF: // RST 0x38
                return rst(0x38);
            default:
                unimplemented(opcode);
        }
        return 1;
    }

    private int next8() {
        int b = mmu.read8(pc);
        if (!halt_bug) {
            pc += 1;
        }
        halt_bug = false;
        return b;
    }

    private int next16() {
        int lsb = next8();
        int msb = next8();
        return (msb << 8) | lsb;
    }

    private void set_register(int id, int value){
        switch(id){
            case 0:
                b = value & 0xff; break;
            case 1:
                c = value & 0xff; break;
            case 2:
                d = value & 0xff; break;
            case 3:
                e = value & 0xff; break;
            case 4:
                h = value & 0xff; break;
            case 5:
                l = value & 0xff; break;
            case 6:
                mmu.write8((h << 8) | l, value); break;
            case 7:
                a = value & 0xff; break;
            case 8:
                b = value >> 8;
                c = value & 0xff;
                break;
            case 9:
                d = value >> 8;
                e = value & 0xff;
                break;
            case 10:
                h = value >> 8;
                l = value & 0xff;
                break;
            case 11:
                sp = value;
                break;
            case 12: // F
                set_flag_register(value);
                break;
            case 13: // AF
                a = value >> 8;
                set_flag_register(value & 0xff);
                break;
            default:
                throw new IllegalArgumentException(String.format("%d is an invalid register number", id));
        }
    }

    public void set_flag_register(int value) {
        zero = (value & 0x80) != 0;
        subtract = (value & 0x40) != 0;
        half = (value & 0x20) != 0;
        carry = (value & 0x10) != 0;
    }

    public int get_flag_register() {
        return (zero ? 0x80 : 0) | (subtract ? 0x40 : 0) | (half ? 0x20 : 0) | (carry ? 0x10 : 0);
    }

    public int get_register(int id){
        switch(id){
            case -2: return next16(); // Immediate 2 bytes
            case -1: return next8(); // Immediate 1 byte
            case 0: return b;
            case 1: return c;
            case 2: return d;
            case 3: return e;
            case 4: return h;
            case 5: return l;
            case 6: return mmu.read8((h << 8) | l); // HL
            case 7: return a;
            case 8: return (b << 8) | c;
            case 9: return (d << 8) | e;
            case 10: return (h << 8) | l;
            case 11: return sp;
            case 12: return get_flag_register();//F
            case 13: return (a << 8) | get_flag_register();// AF
        }
        throw new IllegalArgumentException(String.format("%d is an invalid register number", id));
    }

    private int add8_r_rn(int r_dst, int r_src, boolean cyclic) {
        int dst = get_register(r_dst);
        int src = get_register(r_src);
        int carry_bit = (cyclic && carry) ? 1 : 0;
        int sum = dst + src + carry_bit;
        subtract = false;
        half = ((dst & 0xf) + (src & 0xf) + carry_bit) > 0xf;
        carry = sum > 0xff;
        sum &= 0xff;
        zero = sum == 0;
        set_register(r_dst, sum);
        return (r_src == 6 || r_src == -1) ? 2 : 1;
    }

    private int add16_rp(int r_dst, int r_src) {
        int dst = get_register(r_dst);
        int src = get_register(r_src);
        int sum = dst + src;
        subtract = false;
        carry = sum > 0xffff;
        half = (dst & 0xfff) + (src & 0xfff) > 0xfff;
        set_register(r_dst, sum & 0xffff);
        return 2;
    }

    private int add16_sp_imm() {
        zero = subtract = false;
        int imm = ((byte)next8());
        half = (sp & 0xf) + (imm & 0xf) > 0xf;
        carry = (sp & 0xff) + (imm & 0xff) > 0xff;
        sp = (imm + sp) & 0xffff;
        return 4;
    }

    private int sub8_r_r(int r_dst, int r_src, boolean cyclic, boolean save) {
        int dst = get_register(r_dst);
        int src = get_register(r_src);
        int carry_bit = (cyclic && carry) ? 1 : 0;
        int sum = dst - src - carry_bit;
        subtract = true;
        half = (dst & 0xf) < ((src & 0xf) + carry_bit);
        carry = sum < 0;
        sum &= 0xff;
        zero = sum == 0;
        if (save) {
            set_register(r_dst, sum);
        }
        return (r_src == 6 || r_src == -1) ? 2 : 1;
    }

    private int and_xor8_r_rn(int r_dst, int r_src, boolean xor) {
        int dst = get_register(r_dst);
        int src = get_register(r_src);
        int result = xor ? (dst ^ src) : (dst & src);
        subtract = carry = false;
        half = !xor;
        zero = result == 0;
        set_register(r_dst, result);
        return (r_src == 6 || r_src == -1) ? 2 : 1;
    }

    private int or8_r_rn(int r_dst, int r_src) {
        int dst = get_register(r_dst);
        int src = get_register(r_src);
        int result = dst | src;
        subtract = carry = half = false;
        zero = result == 0;
        set_register(r_dst, result);
        return (r_src == 6 || r_src == -1) ? 2 : 1;
    }

    private int inc8(int r) {
        int val = (get_register(r) + 1) & 0xff;
        subtract = false;
        zero = val == 0;
        half = (val & 0xf) == 0;
        set_register(r, val);
        return (r == 6) ? 3 : 1;
    }

    private int dec8(int r) {
        int val = (get_register(r) - 1) & 0xff;
        subtract = true;
        zero = val == 0;
        half = (val & 0xf) == 0xf;
        set_register(r, val);
        return (r == 6) ? 3 : 1;
    }

    private int inc_dec16(int r, boolean dec) {
        int val = get_register(r);
        val += dec ? -1 : 1;
        set_register(r, val & 0xffff);
        return 2;
    }

    private int ld8_imm(int r) {
        int imm = next8();
        set_register(r, imm);
        return r == 6 ? 3 : 2;
    }

    private int ld8_r_r(int r_dst, int r_src) {
        int src = get_register(r_src);
        set_register(r_dst, src);
        return (r_dst == 6 || r_src == 6) ? 2 : 1;
    }

    private int ld8_r_mem(int r_dst, int r_src) {
        int addr = get_register(r_src);
        if (r_src == 1) { // C
            addr += 0xff00;
        }
        int src = mmu.read8(addr);
        set_register(r_dst, src);
        return r_src == -2 ? 4 : 2;
    }

    private int ld8_mem_r(int r_dst, int r_src) {
        int addr = get_register(r_dst);
        if (r_dst == 1) { // C
            addr += 0xff00;
        }
        int src = get_register(r_src);
        mmu.write8(addr, src);
        return r_dst == -2 ? 4 : 2;
    }

    private int ld_a_hl_di(boolean dec) {
        int addr = get_register(10);
        int src = mmu.read8(addr);
        addr += dec ? -1 : 1;
        set_register(10, addr);
        set_register(7, src);
        return 2;
    }

    private int ld_hl_a_di(boolean dec) {
        int addr = get_register(10);
        int src = get_register(7);
        mmu.write8(addr, src);
        addr += dec ? -1 : 1;
        set_register(10, addr);
        return 2;
    }

    private int ld_shadow(boolean store) {
        int addr = next8() + 0xff00;
        if (store) {
            int src = get_register(7);
            mmu.write8(addr, src);
        }
        else {
            int src = mmu.read8(addr);
            set_register(7, src);
        }
        return 3;
    }

    private int ld16_imm(int r) {
        int src = next16();
        set_register(r, src);
        return 3;
    }

    private int store_sp() {
        int addr = next16();
        mmu.write16(addr, sp);
        return 5;
    }

    private int push16(int r) {
        int src = get_register(r);
        sp -= 2;
        mmu.write16(sp, src);
        return 4;
    }

    private int pop16(int r) {
        int src = mmu.read16(sp);
        sp += 2;
        set_register(r, src);
        return 3;
    }

    private int jump_relative(boolean condition) {
        byte offset = (byte)next8();
        if (condition) {
            pc += offset;
            return 3;
        }
        return 2;
    }

    private int jump_immediate(boolean condition) {
        int target = next16();
        if (condition) {
            pc = target;
            return 4;
        }
        return 3;
    }

    private int call(boolean condition) {
        int target = next16();
        if (condition) {
            sp -= 2;
            mmu.write16(sp, pc);
            pc = target;
            return 6;
        }
        return 3;
    }

    private int return_conditional(boolean condition) {
        if (condition) {
            pc = mmu.read16(sp);
            sp += 2;
            return 5;
        }
        return 2;
    }

    private int rst(int address) {
        sp -= 2;
        mmu.write16(sp, pc);
        pc = address;
        return 4;
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
        int long_cycles = 4;
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
                long_cycles = 3;
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
        return (register == 6) ? long_cycles : 2;
    }

}
