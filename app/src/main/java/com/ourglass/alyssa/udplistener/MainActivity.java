package com.ourglass.alyssa.udplistener;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Created by atorres on 5/10/16.
 */
public class MainActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startService(new Intent(getBaseContext(), UDPListenerService.class));
    }
}
