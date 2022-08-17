package com.funguscow.gb;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The APU to play sounds
 */
public class SoundBoard {

    private static final int BUFFER_SIZE = 2048;
    private static final byte[] DUTY = {(byte)1, (byte)0x81, (byte)0x87, (byte)0x7E};
    private static final int CYCLES_PER_TIMER_TICK = 1 << (20 - 9);

    public static class SpeakerFormat {
        public int sampleRate;
        public boolean leftChannel, rightChannel;

        public SpeakerFormat(int sampleRate, boolean leftChannel, boolean rightChannel) {
            this.sampleRate = sampleRate;
            this.leftChannel = leftChannel;
            this.rightChannel = rightChannel;
        }
    }

    public interface Speaker {

        /**
         * Does whatever it needs to with the provided samples
         * All samples are to be interpreted as unsigned 8-bit
         *
         * If the speaker must wait before it can accept more sound
         * input, it is responsible for blocking within this method!!!!
         *
         * Either left or right may be null if getFormat returns
         * a format indicating that channel is unused
         * @param left Samples for left channel
         * @param right Samples for right channel
         * @param numSamples Number of samples in each channel
         */
        void consume(byte[] left, byte[] right, int numSamples);

        /**
         *
         * @return The details necessary to properly pass data
         * to this speaker
         */
        SpeakerFormat getFormat();

    }

    public Speaker speaker;

    private SpeakerFormat format;
    private long latentCycles; // Could maybe just be int
    private final int bufferSize;

    private byte[] leftBuffer, rightBuffer;

    // Channel 1
    // 0xFF10 - NR10
    private int sweepFrequency1; // Bits 4-6
    private boolean sweepAscending1; // 3
    private int sweepShift1; // 0-2
    private int sweepCounter1; // Internal
    // 0xFF11 - NR11
    private int duty1; // 6-7
    private int length1; // 0-5, WO
    private int waveCounter1; // Internal
    // 0xFF12 - NR12
    private int initialEnvelope1; // 4-7
    private int envelope1; // Internal
    private boolean envelopeAscending1; // 3
    private int envelopeSweep1; // 0-2
    private int envelopeCounter1; // Internal
    // 0xFF13 - NR13, 0xFF14 - NR14
    private int initialFrequencyDivisor1; //0xFF13, 0xFF14[0-2], WO
    private int frequencyDivisor1; // Internal
    private int frequencyCounter1; // Intenral
    private boolean useLength1; // 6
    private boolean enable1; // 7, WO

    // Channel 2
    // 0xFF16 - NR21
    private int duty2; // 6-7
    private int length2; // 0-5, WO
    private int waveCounter2; // Internal
    // 0xFF17 - NR22
    private int initialEnvelope2; // 4-7
    private int envelope2; // Internal
    private boolean envelopeAscending2; // 3
    private int envelopeSweep2; // 0-2
    private int envelopeCounter2; // Internal
    // 0xFF18 - NR23, 0xFF19 - NR24
    private int frequencyDivisor2; // 0xFF18, 0xFF19[0-2], WO
    private int frequencyCounter2; // Internal
    private boolean useLength2; // 6
    private boolean enable2; // 7, WO

    // Channel 3
    // 0xFF1A - NR30
    private boolean on3; // 7
    // 0xFF1B - NR31
    private int length3; // WO
    // 0xFF1C - NR32
    private int volume3; // 5-6
    // 0xFF1D - NR33, 0xFF1E - NR34
    private int frequencyDivisor3; // 0xFF1D, 0xFF1E[0-2], WO
    private int frequencyCounter3; // Internal
    private boolean useLength3; // 6
    private boolean enable3; // 7, WO
    // 0xFF30 - 0xFF3F
    private final byte[] waveform = new byte[16];
    private int wavePtr; // Internal

    // Channel 4
    // 0xFF20 - NR41
    private int length4; // 0-5, WO
    // 0xFF21 - NR42
    private int initialEnvelope4; // 4-7
    private int envelope4; // Internal
    private boolean envelopeAscending4; // 3
    private int envelopeSweep4; // 0-2
    private int envelopeCounter4; // Internal
    // 0xFF22 - NR43
    private int frequencyShift4; // 4-7
    private boolean lowBitWidth4; // 3
    private int frequencyDivisor4; // 0-2
    private int frequencyCounter4; // Internal
    // 0xFF23 - NR44
    private boolean useLength4; // 6
    private boolean enable4; // 7, WO
    private int lfsr4; // Internal

    // Control
    // 0xFF24 - NR50
    private boolean vinLeft; // 7
    private boolean vinRight; // 3
    private int volumeLeft; // 4-6
    private int volumeRight; // 0-2
    // 0xFF25 - NR51
    private int mapLeft; // Bitmasks, 4-7
    private int mapRight; // 0-3
    // 0xFF26 - NR52
    private boolean masterEnable; // 7
    // Bits 0-3 are RO status registers for when channels 1-4 are ON

    // Global
    private int cycleCounter;
    private int bufferPtr;

    private final Machine machine;

    public boolean silent;

    // Scheduling
    private long lastTimestamp;
    private static final int NUM_TASKS = 6,
        TASK_FREQ1 = 0,
        TASK_FREQ2 = 1,
        TASK_FREQ3 = 2,
        TASK_FREQ4 = 3,
        TASK_TIMER = 4,
        TASK_SAMPLE = 5;
    private final Scheduler.Task[] tasks = new Scheduler.Task[NUM_TASKS];

    public SoundBoard(Machine m, int bufferSize) {
        machine = m;
        this.bufferSize = bufferSize;
    }

    public SoundBoard(Machine m) {
        this(m, BUFFER_SIZE);
    }

    /**
     * Attach a speaker to this APU
     * @param speaker Speaker
     */
    public void setSpeaker(Speaker speaker) {
        this.speaker = speaker;
        leftBuffer = rightBuffer = null;
        if (speaker != null) {
            format = speaker.getFormat();
            if (format.leftChannel) {
                leftBuffer = new byte[bufferSize];
            }
            if (format.rightChannel) {
                rightBuffer = new byte[bufferSize];
            }
        }
    }

    /**
     *
     * @return Number of CPU cycles per sound sample
     */
    public int getCyclesPerSample() {
        return (1 << 20) / format.sampleRate;
    }

    /**
     *
     * @param address Address to read
     * @return Read value
     */
    public int read(int address){
        int value = 0;
        switch (address - 0xFF10) {
            case 0x0:
                value = (sweepAscending1 ? 0x80 : 0) | (sweepFrequency1 << 4) | sweepShift1;
                break;
            case 0x1:
                value = 0x3f | (duty1 << 6);
                break;
            case 0x2:
                value = (initialEnvelope1 << 4) | (envelopeAscending1 ? 0x8 : 0) | envelopeSweep1;
                break;
            case 0x4:
                value = 0xbf | (useLength1 ? 0x40 : 0);
                break;
            case 0x6:
                value = 0x3f | (duty2 << 6);
                break;
            case 0x7:
                value = (initialEnvelope2 << 4) | (envelopeAscending2 ? 0x8 : 0) | envelopeSweep2;
                break;
            case 0x9:
                value = 0xbf | (useLength2 ? 0x40 : 0);
                break;
            case 0xA:
                value = 0x7f | (on3 ? 0x80 : 0);
                break;
            case 0xC:
                value = 0x9f | (volume3 << 5);
                break;
            case 0xE:
                value = 0xbf | (useLength3 ? 0x40 : 0);
                break;
            case 0x11:
                value |= initialEnvelope4 << 4;
                value |= envelopeAscending4 ? 0x8 : 0;
                value |= envelopeSweep4;
                break;
            case 0x12:
                value |= frequencyShift4 << 4;
                value |= lowBitWidth4 ? 0x80 : 0;
                value |= frequencyDivisor4;
                break;
            case 0x13:
                value = 0xbf | (useLength4 ? 0x40 : 0);
                break;
            case 0x14:
                value |= vinLeft ? 0x80 : 0;
                value |= vinRight ? 0x8 : 0;
                value |= volumeLeft << 4;
                value |= volumeRight;
                break;
            case 0x15:
                value = (mapLeft << 4) | mapRight;
                break;
            case 0x16:
                value |= masterEnable ? 0x80 : 0;
                value |= enable1 && length1 > 0 ? 0x1 : 0;
                value |= enable2 && length2 > 0 ? 0x2 : 0;
                value |= enable3 && length3 > 0 ? 0x4 : 0;
                value |= enable4 && length4 > 0 ? 0x8 : 0;
                value |= 0x70;
                break;
            case 0x20:
            case 0x21:
            case 0x22:
            case 0x23:
            case 0x24:
            case 0x25:
            case 0x26:
            case 0x27:
            case 0x28:
            case 0x29:
            case 0x2A:
            case 0x2B:
            case 0x2C:
            case 0x2D:
            case 0x2E:
                return waveform[address - 0xFF30];
            default:
                value = 0xff;
        }
        return value;
    }

    /**
     *
     * @param address Address to write
     * @param value Value to write
     */
    public void write(int address, int value){
        switch (address - 0xFF10) {
            case 0x0:
                sweepFrequency1 = (value >> 4) & 7;
                sweepAscending1 = (value & 0x80) == 0;
                sweepShift1 = value & 7;
                break;
            case 0x1:
                duty1 = (value >> 6) & 7;
                length1 = 64 - (value & 63);
                break;
            case 0x2:
                initialEnvelope1 = (value >> 4) & 0xf;
                envelopeAscending1 = (value & 0x8) != 0;
                envelopeSweep1 = value & 7;
                break;
            case 0x3:
                initialFrequencyDivisor1 &= ~0xff;
                initialFrequencyDivisor1 |= value & 0xff;
                break;
            case 0x4:
                initialFrequencyDivisor1 &= 0xff;
                initialFrequencyDivisor1 |= (value & 7) << 8;
                useLength1 = (value & 0x40) != 0;
                enable1 = (value & 0x80) != 0;
                if (enable1) {
                    frequencyDivisor1 = initialFrequencyDivisor1;
                    frequencyCounter1 = 2048 - frequencyDivisor1;
                    envelope1 = initialEnvelope1;
                    sweepCounter1 = 0;
                    waveCounter1 = 0;
                    envelopeCounter1 = 0;
                    if (length1 == 0) {
                        length1 = 64;
                    }
                }
                break;
            case 0x6:
                duty2 = (value >> 6) & 7;
                length2 = 64 - (value & 63);
                break;
            case 0x7:
                initialEnvelope2 = (value >> 4) & 0xf;
                envelopeAscending2 = (value & 0x8) != 0;
                envelopeSweep2 = value & 7;
                break;
            case 0x8:
                frequencyDivisor2 &= ~0xff;
                frequencyDivisor2 |= value & 0xff;
                break;
            case 0x9:
                frequencyDivisor2 &= 0xff;
                frequencyDivisor2 |= (value & 7) << 8;
                useLength2 = (value & 0x40) != 0;
                enable2 = (value & 0x80) != 0;
                if (enable2) {
                    envelope2 = initialEnvelope2;
                    frequencyCounter2 = 2048 - frequencyDivisor2;
                    waveCounter2 = 0;
                    envelopeCounter2 = 0;
                }
                break;
            case 0xA:
                on3 = (value & 0x80) != 0;
                break;
            case 0xB:
                length3 = 256 - (value & 0xff);
                break;
            case 0xC:
                volume3 = (value >> 5) & 3;
                break;
            case 0xD:
                frequencyDivisor3 &= ~0xff;
                frequencyDivisor3 |= value & 0xff;
                break;
            case 0xE:
                frequencyDivisor3 &= 0xff;
                frequencyDivisor3 |= (value & 7) << 8;
                useLength3 = (value & 0x40) != 0;
                enable3 = (value & 0x80) != 0;
                if (enable3) {
                    frequencyCounter3 = 1024 - frequencyDivisor3 / 2;
                    wavePtr = 0;
                }
                break;
            case 0x10:
                length4 = 64 - (value & 63);
                break;
            case 0x11:
                initialEnvelope4 = (value >> 4) & 0xf;
                envelopeAscending4 = (value & 0x8) != 0;
                envelopeSweep4 = value & 7;
                break;
            case 0x12:
                frequencyShift4 = (value >> 4) & 0xf;
                lowBitWidth4 = (value & 0x8) != 0;
                frequencyDivisor4 = value & 7;
                break;
            case 0x13:
                useLength4 = (value & 0x40) != 0;
                enable4 = (value & 0x80) != 0;
                if (enable4) {
                    lfsr4 = 0x7fff;
                    frequencyCounter4 = (frequencyDivisor4 == 0 ? 8 : (frequencyDivisor4 << 4)) << frequencyShift4;
                    envelope4 = initialEnvelope4;
                    envelopeCounter4 = 0;
                }
                break;
            case 0x14:
                vinLeft = (value & 0x80) != 0;
                vinRight = (value & 0x8) != 0;
                volumeLeft = (value >> 4) & 7;
                volumeRight = value & 7;
                break;
            case 0x15:
                mapLeft = (value >> 4) & 0xf;
                mapRight = value & 0xf;
                break;
            case 0x16:
                masterEnable = (value & 0x80) != 0;
                break;
            case 0x20:
            case 0x21:
            case 0x22:
            case 0x23:
            case 0x24:
            case 0x25:
            case 0x26:
            case 0x27:
            case 0x28:
            case 0x29:
            case 0x2A:
            case 0x2B:
            case 0x2C:
            case 0x2D:
            case 0x2E:
                waveform[address - 0xFF30] = (byte)value;
                break;
        }
    }

    /**
     * Increment the internal timer and update values
     * @param cycles Number of cycles since last call to this method
     */
    private void incrementTimer(int cycles) {
        /*
         * TODO Convert this all to be scheduler-based
         * The events that will need to be scheduled to replace this:
         * Increment waveCounter1 (reset frequencyCounter1)
         * Increment waveCounter2 (reset frequencyCounter2)
         * Increment wavePtr (reset frequencyCounter3)
         * Advance lfsr4 (reset frequencyCounter4)
         * Tick the frame sequencer, which will do each of the following:
         *      Decrement all length counters
         *      Advance frequency sweep
         *      Advance all envelopes
         *
         * Not part of this function, but I will also need an event for generating a sample
         */
        while (cycles > 0) {
            cycleCounter++;
            cycles --;
            if (--frequencyCounter1 <= 0) {
                frequencyCounter1 = 2048 - frequencyDivisor1;
                waveCounter1 = (waveCounter1 + 1) & 7;
            }
            if (--frequencyCounter2 <= 0) {
                frequencyCounter2 = 2048 - frequencyDivisor2;
                waveCounter2 = (waveCounter2 + 1) & 7;
            }
            frequencyCounter3 -= 2;
            if (frequencyCounter3 <= 0) {
                frequencyCounter3 = 2048 - frequencyDivisor3;
                wavePtr = (wavePtr + 1) & 31;
            }
            if (--frequencyCounter4 <= 0) {
                frequencyCounter4 = (frequencyDivisor4 == 0 ? 8 : (frequencyDivisor4 << 4)) << frequencyShift4;
                int xor = (lfsr4 & 1) ^ ((lfsr4 >> 1) & 1);
                lfsr4 = (lfsr4 >> 1) | (xor << 14);
                if (lowBitWidth4) {
                    lfsr4 &= ~0x40;
                    lfsr4 |= xor << 6;
                }
            }
            if ((cycleCounter & 0xfff) == 0) {
                if (length1 > 0 && useLength1)
                    length1 --;
                if (length2 > 0 && useLength2)
                    length2 --;
                if (length3 > 0 && useLength3)
                    length3 --;
                if (length4 > 0 && useLength4)
                    length4 --;
                if ((cycleCounter & 0x1fff) == 0x1000 && sweepFrequency1 != 0) {
                    sweepCounter1++;
                    if (sweepCounter1 == sweepFrequency1) {
                        sweepCounter1 = 0;
                        int deltaFreq = (sweepAscending1 ? -1 : 1) * (frequencyDivisor1 >> sweepShift1);
                        int newFreq = frequencyDivisor1 + deltaFreq;
                        if (newFreq > 0x7ff) {
                            enable1 = false;
                        } else {
                            frequencyDivisor1 = newFreq;
                        }
                    }
                }
            }
            else if ((cycleCounter & 0x3fff) == 0x3fff) {
                if (envelopeSweep1 != 0) {
                    envelopeCounter1++;
                    if (envelopeCounter1 == envelopeSweep1) {
                        envelopeCounter1 = 0;
                        envelope1 += (envelopeAscending1 ? 1 : -1);
                        envelope1 = Math.min(0xf, Math.max(0, envelope1));
                    }
                }
                if (envelopeSweep2 != 0) {
                    envelopeCounter2++;
                    if (envelopeCounter2 == envelopeSweep2) {
                        envelopeCounter2 = 0;
                        envelope2 += (envelopeAscending2 ? 1 : -1);
                        envelope2 = Math.min(0xf, Math.max(0, envelope2));
                    }
                }
                if (envelopeSweep4 != 0) {
                    envelopeCounter4++;
                    if (envelopeCounter4 == envelopeSweep4) {
                        envelopeCounter4 = 0;
                        envelope4 += (envelopeAscending4 ? 1 : -1);
                        envelope4 = Math.min(0xf, Math.max(0, envelope4));
                    }
                }
            }
        }
    }

    /**
     *
     * @return Current output of channel 1
     */
    private int channel1() {
        if (!enable1 || length1 <= 0 || envelope1 == 0)
            return 0;
        int s = ((DUTY[duty1] >>> waveCounter1) & 1) == 0 ? 0 : 63;
        return (s * envelope1) >> 4;
    }

    /**
     *
     * @return Current output of channel 2
     */
    private int channel2() {
        if (!enable2 || length2 <= 0 || envelope2 == 0)
            return 0;
        int s = ((DUTY[duty2] >>> waveCounter2) & 1) == 0 ? 0 : 63;
        return (s * envelope2) >> 4;
    }

    /**
     *
     * @return Current output of channel 3
     */
    private int channel3() {
        if (!enable3 || !on3 || volume3 == 0)
            return 0;
        int b = waveform[wavePtr >> 1];
        if ((wavePtr & 1) == 0) {
            b >>= 4;
        }
        b &= 0xf;
        return b << (3 - volume3);
    }

    /**
     *
     * @return Current output of channel 4
     */
    private int channel4() {
        if (!enable4 || length4 <= 0 || envelope4 == 0)
            return 0;
        return (((~lfsr4) & 1) * 63 * envelope4) >> 4;
    }

    /**
     * Write one sample to the speaker
     */
    private void writeSample() {
        int c1 = channel1();
        int c2 = channel2();
        int c3 = channel3();
        int c4 = channel4();
        int left = 0, right = 0;
        if ((mapLeft & 1) != 0)
            left += c1;
        if ((mapLeft & 2) != 0)
            left += c2;
        if ((mapLeft & 3) != 0)
            left += c3;
        if ((mapLeft & 4) != 0)
            left += c4;
        if ((mapRight & 1) != 0)
            right += c1;
        if ((mapRight & 2) != 0)
            right += c2;
        if ((mapRight & 3) != 0)
            right += c3;
        if ((mapRight & 4) != 0)
            right += c4;
        left = (left * volumeLeft) >> 3;
        right = (right * volumeRight) >> 3;
        leftBuffer[bufferPtr] = (byte)left;
        rightBuffer[bufferPtr] = (byte)right;
        bufferPtr++;
    }

    /**
     * Called periodically from the machine to play sound
     * @param cycles Number of m-cycles that have passed
     * @param timeDivisor Speedup factor
     * @param doubleSpeed If true, half as many samples are to be generated
     */
    public void step(int cycles, int timeDivisor, boolean doubleSpeed) {
        incrementTimer(cycles);
        if (speaker == null || silent) {
            return;
        }
        latentCycles += (long) cycles * format.sampleRate;
        int numSamples = (int)(latentCycles >> 20) / timeDivisor;
        if (doubleSpeed) {
            numSamples >>= 1;
        }
        for (int i = 0; i < numSamples; i++) {
            latentCycles -= (long) timeDivisor << 20;
            writeSample();
            if (bufferPtr == bufferSize) {
                bufferPtr = 0;
                speaker.consume(leftBuffer, rightBuffer, bufferSize);
            }
        }
    }

    /**
     * Save state of the APU
     * @param dos Dest stream
     * @throws IOException Errors writing
     */
    public void save(DataOutputStream dos) throws IOException {
        dos.write("APU ".getBytes(StandardCharsets.UTF_8));
        dos.writeLong(latentCycles);
        dos.writeInt(sweepFrequency1);
        dos.writeBoolean(sweepAscending1);
        dos.writeInt(sweepShift1);
        dos.writeInt(sweepCounter1);
        dos.writeInt(duty1);
        dos.writeInt(length1);
        dos.writeInt(waveCounter1);
        dos.writeInt(initialEnvelope1);
        dos.writeInt(envelope1);
        dos.writeBoolean(envelopeAscending1);
        dos.writeInt(envelopeSweep1);
        dos.writeInt(envelopeCounter1);
        dos.writeInt(initialFrequencyDivisor1);
        dos.writeInt(frequencyDivisor1);
        dos.writeInt(frequencyCounter1);
        dos.writeBoolean(useLength1);
        dos.writeBoolean(enable1);
        dos.writeInt(duty2);
        dos.writeInt(length2);
        dos.writeInt(initialEnvelope2);
        dos.writeInt(envelope2);
        dos.writeBoolean(envelopeAscending2);
        dos.writeInt(envelopeSweep2);
        dos.writeInt(envelopeCounter2);
        dos.writeInt(frequencyDivisor2);
        dos.writeInt(frequencyCounter2);
        dos.writeBoolean(useLength2);
        dos.writeBoolean(enable2);
        dos.writeBoolean(on3);
        dos.writeInt(length3);
        dos.writeInt(volume3);
        dos.writeInt(frequencyDivisor3);
        dos.writeInt(frequencyCounter3);
        dos.writeBoolean(useLength3);
        dos.writeBoolean(enable3);
        dos.write(waveform);
        dos.writeInt(wavePtr);
        dos.writeInt(length4);
        dos.writeInt(initialEnvelope4);
        dos.writeInt(envelope4);
        dos.writeBoolean(envelopeAscending4);
        dos.writeInt(envelopeSweep4);
        dos.writeInt(envelopeCounter4);
        dos.writeInt(frequencyShift4);
        dos.writeBoolean(lowBitWidth4);
        dos.writeInt(frequencyDivisor4);
        dos.writeInt(frequencyCounter4);
        dos.writeBoolean(useLength4);
        dos.writeBoolean(enable4);
        dos.writeInt(lfsr4);
        dos.writeBoolean(vinLeft);
        dos.writeBoolean(vinRight);
        dos.writeInt(volumeLeft);
        dos.writeInt(volumeRight);
        dos.writeInt(mapLeft);
        dos.writeInt(mapRight);
        dos.writeBoolean(masterEnable);
        dos.writeInt(cycleCounter);
    }

    /**
     * Load the APU state
     * @param dis Source stream
     * @throws IOException Errors reading
     */
    public void load(DataInputStream dis) throws IOException {
        latentCycles = dis.readLong();
        sweepFrequency1 = dis.readInt();
        sweepAscending1 = dis.readBoolean();
        sweepShift1 = dis.readInt();
        sweepCounter1 = dis.readInt();
        duty1 = dis.readInt();
        length1 = dis.readInt();
        waveCounter1 = dis.readInt();
        initialEnvelope1 = dis.readInt();
        envelope1 = dis.readInt();
        envelopeAscending1 = dis.readBoolean();
        envelopeSweep1 = dis.readInt();
        envelopeCounter1 = dis.readInt();
        initialFrequencyDivisor1 = dis.readInt();
        frequencyDivisor1 = dis.readInt();
        frequencyCounter1 = dis.readInt();
        useLength1 = dis.readBoolean();
        enable1 = dis.readBoolean();
        duty2 = dis.readInt();
        length2 = dis.readInt();
        initialEnvelope2 = dis.readInt();
        envelope2 = dis.readInt();
        envelopeAscending2 = dis.readBoolean();
        envelopeSweep2 = dis.readInt();
        envelopeCounter2 = dis.readInt();
        frequencyDivisor2 = dis.readInt();
        frequencyCounter2 = dis.readInt();
        useLength2 = dis.readBoolean();
        enable2 = dis.readBoolean();
        on3 = dis.readBoolean();
        length3 = dis.readInt();
        volume3 = dis.readInt();
        frequencyDivisor3 = dis.readInt();
        frequencyCounter3 = dis.readInt();
        useLength3 = dis.readBoolean();
        enable3 = dis.readBoolean();
        dis.read(waveform);
        wavePtr = dis.readInt();
        length4 = dis.readInt();
        initialEnvelope4 = dis.readInt();
        envelope4 = dis.readInt();
        envelopeAscending4 = dis.readBoolean();
        envelopeSweep4 = dis.readInt();
        envelopeCounter4 = dis.readInt();
        frequencyShift4 = dis.readInt();
        lowBitWidth4 = dis.readBoolean();
        frequencyDivisor4 = dis.readInt();
        frequencyCounter4 = dis.readInt();
        useLength4 = dis.readBoolean();
        enable4 = dis.readBoolean();
        lfsr4 = dis.readInt();
        vinLeft = dis.readBoolean();
        vinRight = dis.readBoolean();
        volumeLeft = dis.readInt();
        volumeRight = dis.readInt();
        mapLeft = dis.readInt();
        mapRight = dis.readInt();
        masterEnable = dis.readBoolean();
        cycleCounter = dis.readInt();
    }

    // Scheduler methods
    private void cancel() {
        Arrays.stream(tasks).forEach(machine.scheduler::cancel);
        Arrays.fill(tasks, null);
    }

    private void invalidateTasks() {
        Arrays.fill(tasks, null);
    }

    private void cullTasks() {
        IntStream.range(0, NUM_TASKS).filter(i -> tasks[i].index < 0).forEach(i -> tasks[i] = null);
    }

    /**
     * Called when the frame sequencer issues a tick
     */
    private void scheduledTimerTick(long cycles, Object obj) {
        tasks[TASK_TIMER] = machine.scheduler.add(new Scheduler.Task(this::scheduledTimerTick, null, cycles + CYCLES_PER_TIMER_TICK));
    }

    /**
     * Called when ready to produce a sample
     */
    private void scheduledSample(long cycles, Object obj) {
        tasks[TASK_SAMPLE] = machine.scheduler.add(new Scheduler.Task(this::scheduledSample, null, cycles + getCyclesPerSample()));
    }

    private void scheduledFreq1(long cycles, Object obj) {
        tasks[TASK_FREQ1] = machine.scheduler.add(new Scheduler.Task(this::scheduledFreq1, null, cycles + 2048 - frequencyDivisor1));
    }

    private void scheduledFreq2(long cycles, Object obj) {

    }

    private void scheduledFreq3(long cycles, Object obj) {

    }

    private void scheduledFreq4(long cycles, Object obj) {

    }

}
