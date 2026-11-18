package com.ramwatch;

import android.content.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())
                && c.getSharedPreferences("p", 0).getBoolean("on", false))
            RamService.start(c);
    }
}
