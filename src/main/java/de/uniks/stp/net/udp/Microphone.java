package de.uniks.stp.net.udp;

import de.uniks.stp.builder.ModelBuilder;

import javax.sound.sampled.*;
import java.util.HashMap;
import java.util.Map;

import static de.uniks.stp.util.Constants.*;

public class Microphone {
    private final ModelBuilder builder;
    private AudioFormat format;
    private TargetDataLine microphone;
    private byte[] data;
    private DataLine.Info info;
    private Mixer mixer;

    public Microphone(ModelBuilder builder) {
        this.builder = builder;
    }

    public void init() {
        // audio format
        format = new AudioFormat(AUDIO_BITRATE, AUDIO_SAMPLE_SIZE, AUDIO_CHANNELS, AUDIO_SIGNING, AUDIO_BYTE_ORDER);

        // audio object (microphone information?)
        info = new DataLine.Info(TargetDataLine.class, format);

        microphone = builder.getSelectedMicrophone();


//
//        try {
//            // get microphoneLine
//            microphone = (TargetDataLine) AudioSystem.getLine(info);
//
//        } catch (LineUnavailableException e) {
//            e.printStackTrace();
//        }

        data = new byte[1024];
    }

    /**
     * the method start reading with the microphone
     */
    public void startRecording() {
        try {
            // open microphone line
            microphone.open(format);
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }

        // start reading
        microphone.start();
    }

    /**
     * the method returns the data which the microphone is reading
     */
    public byte[] readData() {
        // store audio in data
        microphone.read(data, 0, data.length);
        return data;
    }

    /**
     * the method stops the microphone
     */
    public void stopRecording() {
        microphone.stop();
        microphone.close();
    }
}
