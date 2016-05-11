package com.ourglass.alyssa.udplistener;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * Created by atorres on 5/10/16.
 */
public class UDPListenerService extends Service {
    private static int port = 9090;
    private DatagramSocket socket;
    private Boolean listening = false;

    private WifiManager.MulticastLock lock;

    private void getUdpPacket() {
        byte[] recvBuf = new byte[576];

        if (socket == null || socket.isClosed()) {
            try {
                socket = new DatagramSocket(port);
                socket.setBroadcast(true);
            } catch (SocketException e) {
                Log.e("UDP_SOCK", e.getLocalizedMessage());
                return;
            }
        }

        DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
        Log.i("UDP_LISTENER", "Waiting for UDP broadcast...");

        try {
            socket.receive(packet);
        } catch (IOException e) {
            Log.e("UDP_SOCK", e.getLocalizedMessage());
            return;
        }

        String senderIp = packet.getAddress().getHostAddress();
        String message = new String(packet.getData()).trim();

        Log.i("UDP_LISTENER", "Got UDP broadcast from " + senderIp + "\n" + message);
    }

    private void startListening() {
        listening = true;

        Thread UDPBeaconThread = new Thread(new Runnable() {
            public void run() {
                while (listening) {
                    Log.i("UDP_LISTENER", "mulitcast lock held: " + lock.isHeld());
                    getUdpPacket();
                }
                socket.close();
            }
        });

        UDPBeaconThread.start();
    }

    private void stopListening() {
        Log.i("UDP_LISTENER", "Stopping listening.");
        socket.close();
        listening = false;
    }

    // Service functions

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("UDP_LISTENER", "onStartCommand");
        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        listening = true;
        Log.i("UDP_LISTENER", "Service starting.");

        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        lock = wifi.createMulticastLock("main");
        lock.acquire();

        Log.i("UDP_LISTENER", "mulitcast lock held: " + lock.isHeld());

        startListening();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("UDP_LISTENER", "Service stopping.");
        lock.release();
        stopListening();
    }
}
