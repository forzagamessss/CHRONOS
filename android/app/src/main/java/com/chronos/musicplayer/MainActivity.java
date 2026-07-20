package com.chronos.musicplayer;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ChronosNativePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
