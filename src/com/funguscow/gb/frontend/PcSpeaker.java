package com.funguscow.gb.frontend;

import com.funguscow.gb.SoundBoard;

import javax.sound.sampled.*;

public class PcSpeaker implements SoundBoard.Speaker {

    private static final int BUFFER_SIZE = 2048;

    private final SoundBoard.SpeakerFormat speakerFormat;
    private SourceDataLine line;
    private final int bufferSize;
    private final byte[] buffer;
    private int bufferPtr;

    // Can be constructor-supplied later
    private final int channels;

    public PcSpeaker(int bufferSize, int channels) {
        this.channels = channels;
        this.bufferSize = bufferSize;
        buffer = new byte[bufferSize * channels];
        AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44100, 8, channels, channels, 44100, false);
        speakerFormat = new SoundBoard.SpeakerFormat(44100, true, true);
        Line.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
        try {
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(audioFormat, bufferSize * channels);
            line.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    public PcSpeaker() {
        this(BUFFER_SIZE, 2);
    }

    public SoundBoard.SpeakerFormat getFormat() {
        return speakerFormat;
    }

    public void consume(byte[] left, byte[] right, int numSamples) {
        for (int i = 0; i < numSamples; i++) {
            buffer[bufferPtr * channels] = left[i];
            if (channels > 1)
                buffer[bufferPtr * channels + 1] = right[i];
            if(++bufferPtr == bufferSize) {
                bufferPtr = 0;
                line.write(buffer, 0, bufferSize * channels);
            }
        }
    }

}
