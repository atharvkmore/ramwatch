package com.ramwatch;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView info;
    private Button toggle;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
    private final Runnable tick = new Runnable() {
        public void run() { refresh(); h.postDelayed(this, 1000); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(48, 48, 48, 48);
        l.setBackgroundColor(Color.parseColor("#101418"));

        info = new TextView(this);
        info.setTextColor(Color.WHITE);
        info.setTextSize(22);
        info.setGravity(Gravity.CENTER);
        l.addView(info);

        toggle = new Button(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = 64;
        l.addView(toggle, lp);
        toggle.setOnClickListener(v -> {
            boolean on = !RamService.running;
            getSharedPreferences("p", 0).edit().putBoolean("on", on).apply();
            if (on) RamService.start(this); else stopService(new Intent(this, RamService.class));
            h.postDelayed(this::refresh, 300);
        });
        setContentView(l);

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);

        // Auto-start on first launch
        if (getSharedPreferences("p", 0).getBoolean("on", true) && !RamService.running) {
            getSharedPreferences("p", 0).edit().putBoolean("on", true).apply();
            RamService.start(this);
        }
    }

    private void refresh() {
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        long used = mi.totalMem - mi.availMem;
        info.setText("Available RAM\n" + RamService.gb(mi.availMem)
                + "\n\nUsed " + RamService.gb(used) + " / " + RamService.gb(mi.totalMem)
                + "\n(" + (used * 100 / mi.totalMem) + "% used)");
        toggle.setText(RamService.running ? "Unpin from notification bar" : "Pin to notification bar");
    }

    @Override protected void onResume() { super.onResume(); h.post(tick); }
    @Override protected void onPause() { h.removeCallbacks(tick); super.onPause(); }
}
