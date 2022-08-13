package com.funguscow.gb;

public class SoundBoard {

    public interface Speaker {

        /**
         *
         * @return true iff this Speaker is ready to receive more sound
         */
        boolean ready();

        /**
         * Request that the Speaker plays this soundboard
         * @param board this
         */
        void supply(SoundBoard board);
    }

    public Speaker speaker;

    private byte[] waveform;

    public int read(int address){
        /* TODO read registers */
        return 0;
    }

    public void write(int address, int value){
        /* TODO write registers */
    }

    /**
     * Called periodically from the machine to play sound
     */
    public void step() {
        if (speaker == null) {
            return;
        }
        if (speaker.ready()) {
            speaker.supply(this);
        }
    }

    /**
     * Called by a speaker to request bytes of sound to play
     * Should always output the same number of bytes to left and right
     * Either left or right may be null if the speaker only uses one channel
     * If not null, left/right is expected to be at lest of length
     * offset[Left/Right] + requested
     * @param left Output bytes for left channel are written here
     * @param right Output bytes for right channel are written here
     * @param offsetLeft Offset into left
     * @param offsetRight Offset into right
     * @param requested Maximum number of bytes to generated per channel
     * @return Actual number of bytes generated (per channel)
     */
    public int play(byte[] left, byte[] right, int offsetLeft, int offsetRight, int requested) {

        return 0;
    }

}
