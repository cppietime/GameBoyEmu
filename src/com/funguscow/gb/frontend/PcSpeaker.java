package com.funguscow.gb.frontend;

import com.funguscow.gb.SoundBoard;

import javax.sound.sampled.*;

public class PcSpeaker implements SoundBoard.Speaker {

    private static final int BUFFER_SIZE = 4096;

    private AudioFormat audioFormat;
    private SoundBoard.SpeakerFormat speakerFormat;
    private SourceDataLine line;
    private int bufferSize;
    private byte[] buffer;

    private int channels = 2;

    public PcSpeaker(int bufferSize) {
        this.bufferSize = bufferSize;
        buffer = new byte[bufferSize * channels];
        audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44100, 8, channels, channels, 44100, false);
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
        this(BUFFER_SIZE);
    }

    public SoundBoard.SpeakerFormat getFormat() {
        return speakerFormat;
    }

    public void consume(byte[] left, byte[] right, int numSamples) {
        int index = 0;
        while (numSamples > 0) {
            int n = Math.min(numSamples, bufferSize);
            for (int i = 0; i < n; i++) {
                buffer[i * channels] = left[index];
                if (channels > 0)
                    buffer[i * channels + 1] = right[index];
                index += 1;
            }
            line.write(buffer, 0, n * channels);
            numSamples -= n;
        }
    }

}
