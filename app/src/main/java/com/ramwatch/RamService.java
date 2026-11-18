package com.ramwatch;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;

public class RamService extends Service {
    static final String CH = "ram";
    static final int ID = 1;
    static final long INTERVAL_MS = 2000;
    static boolean running = false;

    private final Handler h = new Handler(Looper.getMainLooper());
    private ActivityManager am;
    private NotificationManager nm;
    private Notification.Builder nb;
    private final ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();

    private final Runnable tick = new Runnable() {
        public void run() { update(); h.postDelayed(this, INTERVAL_MS); }
    };

    // Pause updates while screen is off -> near-zero battery use
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            h.removeCallbacks(tick);
            if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) h.post(tick);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "RAM monitor", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
            nb = new Notification.Builder(this, CH);
        } else {
            nb = new Notification.Builder(this).setPriority(Notification.PRIORITY_LOW);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        nb.setSmallIcon(R.drawable.ic_ram).setOngoing(true).setOnlyAlertOnce(true)
          .setShowWhen(false).setContentIntent(open).setCategory(Notification.CATEGORY_STATUS);

        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screen, f);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        build();
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(ID, nb.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else
            startForeground(ID, nb.build());
        running = true;
        h.removeCallbacks(tick);
        h.post(tick);
        return START_STICKY;
    }

    private void build() {
        am.getMemoryInfo(mi);
        long total = mi.totalMem, avail = mi.availMem, used = total - avail;
        int usedPct = (int) (used * 100 / total);
        nb.setContentTitle("Free RAM: " + gb(avail) + " (" + (100 - usedPct) + "%)")
          .setContentText("Used " + gb(used) + " of " + gb(total) + (mi.lowMemory ? "  ⚠ LOW" : ""))
          .setProgress(100, usedPct, false);
    }

    private void update() { build(); nm.notify(ID, nb.build()); }

    static String gb(long b) {
        double g = b / 1073741824.0;
        return g >= 1 ? String.format("%.2f GB", g) : String.format("%d MB", b / 1048576);
    }

    @Override public void onDestroy() {
        running = false;
        h.removeCallbacks(tick);
        unregisterReceiver(screen);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    static void start(Context c) {
        Intent i = new Intent(c, RamService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }
}
