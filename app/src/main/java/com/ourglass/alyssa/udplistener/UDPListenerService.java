package com.ourglass.alyssa.udplistener;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Created by atorres on 5/10/16.
 */
public class UDPListenerService extends Service {

    static final String TAG = "UDP_LISTENER";

    String MCAST_GROUP = "224.1.1.1";
    int MCAST_PORT = 5007;

    MulticastSocket mSocket = null;
    InetAddress mAddr = null;

    //private static int port = 9090;
    //private DatagramSocket socket;
    private Boolean listening = false;

    private WifiManager.MulticastLock multicastLock;
    //private PowerManager.WakeLock wakeLock;

    private void getMulticastPacket() {
        byte[] recvBuf = new byte[256];

        if (mAddr == null) {
            try {
                mAddr = InetAddress.getByName(MCAST_GROUP);
            } catch (UnknownHostException e) {
                Log.e(TAG, e.getLocalizedMessage());
                return;
            }
        }

        if (mSocket == null || mSocket.isClosed()) {
            try {
                mSocket = new MulticastSocket(MCAST_PORT);
                mSocket.joinGroup(mAddr);
            } catch (IOException e) {
                Log.e(TAG, e.getLocalizedMessage());
                return;
            }
        }

        DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
        Log.i(TAG, "Waiting for UDP broadcast...");

        try {
            mSocket.receive(packet);
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
            return;
        }

        String senderIp = packet.getAddress().getHostAddress();
        String message = new String(packet.getData()).trim();

        Log.i(TAG, "Got UDP broadcast from " + senderIp + "\n" + message);
    }

    private void startListening() {
        listening = true;

        Thread UDPBeaconThread = new Thread(new Runnable() {
            public void run() {
                while (listening) {
                    getMulticastPacket();
                }
                if (mSocket != null) {
                    mSocket.close();
                }
            }
        });

        UDPBeaconThread.start();
    }

    private void stopListening() {
        Log.i(TAG, "Stopping listening.");
        mSocket.close();
        listening = false;
    }

    // Service functions

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
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
        Log.i(TAG, "Service starting.");

        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        multicastLock = wifi.createMulticastLock(TAG);
        multicastLock.acquire();

        //PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        //wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        //wakeLock.acquire();

        Log.i(TAG, "mulitcast lock held: " + multicastLock.isHeld());
        //Log.i(TAG, "wake lock held: " + wakeLock.isHeld());

        startListening();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service stopping.");
        multicastLock.release();
        //wakeLock.release();
        stopListening();
    }
}
