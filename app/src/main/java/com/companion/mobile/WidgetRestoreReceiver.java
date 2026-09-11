package com.companion.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WidgetRestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        WidgetRestorer.restoreAll(context);
        ControlSatelliteService.startAll(context);
    }
}
