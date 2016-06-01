package com.ourglass.alyssa.udplistener;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;

/**
 * Created by atorres on 5/10/16.
 */
public class MainActivity extends Activity {
    static final String LOG_TAG = "AUDIO_RECEIVER";

    static final String INET_ADDR = "224.0.0.3";
    static final int AUDIO_PORT = 8888;
    static final int SAMPLE_RATE = 44100;
    static final int SAMPLE_INTERVAL = 20; // milliseconds
    static final int SAMPLE_SIZE = 2; // bytes per sample
    static final int BUF_SIZE = SAMPLE_INTERVAL*SAMPLE_INTERVAL*SAMPLE_SIZE*2;

    private AudioTrack mTrack = null;
    private Boolean mReceiving = false;
    private DatagramSocket socket = null;
    private MediaCodec mDecoder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock multicastLock = wifi.createMulticastLock("main");
        multicastLock.acquire();

        Button btnRecv = (Button) findViewById(R.id.btnRecv);
        btnRecv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                receiveAudio();
            }
        });

        Button btnPause = (Button) findViewById(R.id.btnPause);
        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mTrack != null) {
                    mTrack.pause();
                }
            }
        });

        Button btnPlay = (Button) findViewById(R.id.btnPlay);
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mTrack != null) {
                    mTrack.play();
                }
            }
        });

        Button btnStop = (Button) findViewById(R.id.btnStop);
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mReceiving = false;
                socket.close();
                mDecoder.stop();
            }
        });

        //startService(new Intent(getBaseContext(), UDPListenerService.class));
    }

    public void receiveAudio() {
        mReceiving = true;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run()
            {
                Log.d(LOG_TAG, "start receive thread, thread id: "
                        + Thread.currentThread().getId());

                mTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, BUF_SIZE,
                        AudioTrack.MODE_STREAM);
                mTrack.play();

                try {
                    InetAddress address = InetAddress.getByName(INET_ADDR);
                    MulticastSocket mcastSocket = new MulticastSocket(AUDIO_PORT);
                    mcastSocket.joinGroup(address);

                    byte[] receiveBuf = new byte[BUF_SIZE];

                    setDecoder();
                    mDecoder.start();

                    while (mReceiving) {
                        DatagramPacket packet = new DatagramPacket(receiveBuf, receiveBuf.length);
                        mcastSocket.receive(packet);
                        Log.d(LOG_TAG, "received packet: " + packet.getLength());

                        byte[] data = new byte[packet.getLength()];
                        System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

                        ByteBuffer[] inputBuffers = null, outputBuffers = null;
                        ByteBuffer inputBuffer = null, outputBuffer = null;

                        if (Build.VERSION.SDK_INT < 21) {
                            inputBuffers = mDecoder.getInputBuffers();
                            outputBuffers = mDecoder.getOutputBuffers();
                        }

                        int inputBufferIdx = mDecoder.dequeueInputBuffer(-1);

                        if (inputBufferIdx >= 0) {

                            if (Build.VERSION.SDK_INT >= 21) {
                                inputBuffer = mDecoder.getInputBuffer(inputBufferIdx);
                            } else if (inputBuffers != null) {
                                inputBuffer = inputBuffers[inputBufferIdx];
                            }

                            if (inputBuffer != null) {
                                inputBuffer.clear();
                                inputBuffer.put(data);
                                mDecoder.queueInputBuffer(inputBufferIdx, 0, data.length, 0, 0);
                            }
                        }

                        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                        int outputBufferIdx = mDecoder.dequeueOutputBuffer(bufferInfo, 0);

                        while (outputBufferIdx >= 0) {

                            if (Build.VERSION.SDK_INT >= 21) {
                                outputBuffer = mDecoder.getOutputBuffer(outputBufferIdx);
                            } else if (outputBuffers != null) {
                                outputBuffer = outputBuffers[outputBufferIdx];
                            }

                            if (outputBuffer != null) {
                                outputBuffer.position(bufferInfo.offset);
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                                byte[] outData = new byte[bufferInfo.size];
                                outputBuffer.get(outData);

                                mTrack.write(outData, 0, outData.length);
                            }

                            mDecoder.releaseOutputBuffer(outputBufferIdx, false);
                            outputBufferIdx = mDecoder.dequeueOutputBuffer(bufferInfo, 0);
                        }
                    }

                } catch (Exception e) {
                    Log.e(LOG_TAG, "Exception: " + e);
                    e.printStackTrace();

                } finally {
                    mDecoder.stop();
                    mTrack.stop();

                    if (socket != null) {
                        socket.close();
                    }
                }
            }
        });

        thread.start();
    }

    private void setDecoder() throws IOException {
        mDecoder = MediaCodec.createDecoderByType("audio/mp4a-latm");
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1024);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE);
        mDecoder.configure(format, null, null, 0);
    }
}
