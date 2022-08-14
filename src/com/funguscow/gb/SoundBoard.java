package com.funguscow.gb;

public class SoundBoard {

    private static final int BUFFER_SIZE = 2048;
    private static final byte[] DUTY = {(byte)1, (byte)0x81, (byte)0x87, (byte)0x7E};

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
    private boolean useLength3; // 6
    private boolean enable3; // 7, WO
    // 0xFF30 - 0xFF3F
    private final byte[] waveform = new byte[16];

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
    private int sampleCounter;
    private int bufferPtr;

    public SoundBoard(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public SoundBoard() {
        this(BUFFER_SIZE);
    }

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
                value = (initialEnvelope2 << 4) | (envelopeAscending2 ? 0x8 : 0) | envelopeSweep1;
                break;
            case 0x9:
                value = 0xbf | (useLength2 ? 0x40 : 0);
                break;
            case 0xA:
                value = 0x3f | (on3 ? 0x80 : 0);
                break;
            case 0xC:
                value = 0x9f | (volume3 << 5);
                break;
            case 0xE:
                value = 0xbf | (useLength3 ? 0x40 : 0);
                break;
            case 0x10:
                value = 0xC0 | length4;
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
                value |= enable1 ? 0x1 : 0;
                value |= enable2 ? 0x2 : 0;
                value |= enable3 ? 0x4 : 0;
                value |= enable4 ? 0x8 : 0;
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
                frequencyDivisor3 |= (value & 3) << 8;
                useLength3 = (value & 0x40) != 0;
                enable3 = (value & 0x80) != 0;
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
                lowBitWidth4 = (value & 0x80) != 0;
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

    private void incrementTimer(int cycles) {
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
                        int deltaFreq = (sweepAscending1 ? 1 : -1) * (frequencyDivisor1 >> sweepShift1);
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

    private int channel1() {
        if (!enable1 || length1 <= 0 || envelope1 == 0)
            return 0;
        int s = ((DUTY[duty1] >>> waveCounter1) & 1) == 0 ? 0 : 63;
        return (s * envelope1) >> 4;
    }

    private int channel2() {
        if (!enable2 || length2 <= 0 || envelope2 == 0)
            return 0;
        int s = ((DUTY[duty2] >>> waveCounter2) & 1) == 0 ? 0 : 63;
        return (s * envelope2) >> 4;
    }

    private int channel3() {
        return 0;
    }

    private int channel4() {
        if (!enable4 || length4 <= 0 || envelope4 == 0)
            return 0;
        return (((~lfsr4) & 1) * 63 * envelope4) >> 4;
    }

    private void writeSample() {
        sampleCounter++;
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
        leftBuffer[bufferPtr] = (byte)left;
        rightBuffer[bufferPtr] = (byte)right;
        bufferPtr++;
    }

    /**
     * Called periodically from the machine to play sound
     * @param cycles Number of m-cycles that have passed
     */
    public void step(int cycles) {
        if (speaker == null) {
            return;
        }
        incrementTimer(cycles);
        latentCycles += (long) cycles * format.sampleRate;
        int numSamples = (int)(latentCycles >> 20);
        for (int i = 0; i < numSamples; i++) {
            latentCycles -= (long) 1 << 20;
            writeSample();
            if (bufferPtr == bufferSize) {
                bufferPtr = 0;
                speaker.consume(leftBuffer, rightBuffer, bufferSize);
            }
        }
    }

}
