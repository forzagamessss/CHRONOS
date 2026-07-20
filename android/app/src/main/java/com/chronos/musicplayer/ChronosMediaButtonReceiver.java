package com.chronos.musicplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ChronosMediaButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ChronosNativePlugin.dispatchMediaCommand(intent.getStringExtra("command"));
    }
}
