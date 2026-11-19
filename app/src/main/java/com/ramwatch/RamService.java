package com.ramwatch;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.graphics.drawable.Icon;
import android.os.*;
import android.text.TextUtils;
import android.view.Choreographer;
import java.util.ArrayList;
import java.util.Locale;

public class RamService extends Service {
    static final String CH = "ram";
    static final int ID = 1;
    static boolean running = false;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
    private final Stats.Net net = new Stats.Net();
    private ActivityManager am;
    private NotificationManager nm;
    private Notification.Builder nb;
    private SharedPreferences p;
    private Bitmap bmp;
    private Canvas cv;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Overlay overlay;

    // Signals for helpful suggestions; kept in memory, saved once a minute
    private long hotSince;
    private int sigTicks, sigRamHigh;

    // FPS = frames delivered by the display per second (vsync pace); runs only while shown
    private int frames, fps;
    private long fpsStart;
    private boolean fpsOn;
    private final Choreographer.FrameCallback frameCb = new Choreographer.FrameCallback() {
        public void doFrame(long t) {
            frames++;
            if (fpsStart == 0) fpsStart = t;
            if (t - fpsStart >= 1_000_000_000L) {
                fps = (int) (frames * 1_000_000_000L / (t - fpsStart));
                frames = 0;
                fpsStart = t;
            }
            if (fpsOn) Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private void setFps(boolean on) {
        if (on == fpsOn) return;
        fpsOn = on;
        frames = 0; fpsStart = 0;
        if (on) Choreographer.getInstance().postFrameCallback(frameCb);
        else Choreographer.getInstance().removeFrameCallback(frameCb);
    }

    private final Runnable tick = new Runnable() {
        public void run() { update(); h.postDelayed(this, p.getInt("iv", 2) * 1000L); }
    };

    // Pause updates while the screen is off -> near-zero battery use
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            h.removeCallbacks(tick);
            if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) h.post(tick);
            else setFps(false);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        p = getSharedPreferences("p", 0);
        overlay = new Overlay(this);
        am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "System monitor", NotificationManager.IMPORTANCE_LOW);
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

        bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        cv = new Canvas(bmp);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);

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
        ArrayList<String> parts = new ArrayList<>();
        int sb = p.getInt("sb", 0);
        net.sample();
        am.getMemoryInfo(mi);
        long total = mi.totalMem, avail = mi.availMem;
        int usedPct = (int) ((total - avail) * 100 / total);
        boolean ov = p.getBoolean("ov", false) && Overlay.allowed(this);
        int cpu = (Stats.on(p, 2) || sb == 3 || (ov && Stats.on(p, Stats.OV, 1))) ? Stats.cpuLoad() : -1;
        Intent bat = (Stats.on(p, 1) || Stats.on(p, 3) || sb == 4
                || (ov && (Stats.on(p, Stats.OV, 5) || Stats.on(p, Stats.OV, 6)))) ? Stats.battery(this) : null;
        setFps(ov && Stats.on(p, Stats.OV, 0));
        trackSignals(bat, usedPct);
        if (ov) overlay.show(overlayItems(usedPct, cpu, bat), p.getInt("ov_pos", 2), p.getInt("ov_x", 0), p.getInt("ov_y", 0)); else overlay.hide();

        if (Stats.on(p, 0)) parts.add("RAM " + Stats.gb(avail) + " free (" + (100 - usedPct) + "%)" + (mi.lowMemory ? " ⚠" : ""));
        if (bat != null && Stats.on(p, 1)) parts.add("Battery " + Stats.batPct(bat) + "%" + (Stats.charging(bat) ? " ⚡" : ""));
        if (Stats.on(p, 2) && cpu >= 0) parts.add("CPU " + cpu + "%");
        if (bat != null && Stats.on(p, 3)) parts.add("Batt " + Stats.fmt1(Stats.batTemp(bat)) + "°C");
        if (Stats.on(p, 4)) { float t = Stats.cpuTemp(); if (t > 0) parts.add("CPU " + Stats.fmt1(t) + "°C"); }
        if (Stats.on(p, 5)) { long f = Stats.avgFreqMHz(); if (f > 0) parts.add(f + " MHz"); }
        if (Stats.on(p, 6)) parts.add("Storage " + Stats.gb(Stats.storage()[1]) + " free");
        if (Stats.on(p, 7)) parts.add("Up " + Stats.uptime());
        if (Stats.on(p, 8)) parts.add("↓ " + Stats.speed(net.down) + "  ↑ " + Stats.speed(net.up));
        if (Stats.on(p, 9)) { int[] w = Stats.wifi(this); if (w != null) parts.add("Wi-Fi " + w[0] + " dBm " + Stats.bars(w[1])); }
        if (Stats.on(p, 10)) {
            int[] c = Stats.cell(this);
            if (c != null) parts.add("Cell " + (c[1] != Integer.MAX_VALUE ? c[1] + " dBm " : "") + Stats.bars(c[0]));
        }

        String title = parts.isEmpty() ? "RAM Watch running" : parts.remove(0);
        String text = TextUtils.join("  •  ", parts);
        nb.setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text));
        if (Stats.on(p, 0)) nb.setProgress(100, usedPct, false); else nb.setProgress(0, 0, false);

        switch (sb) {
            case 1: nb.setSmallIcon(icon(usedPct + "%", "RAM")); break;
            case 2: nb.setSmallIcon(icon(String.format(Locale.US, "%.1f", avail / 1073741824.0), "GB")); break;
            case 3: nb.setSmallIcon(icon(cpu < 0 ? "--" : cpu + "%", "CPU")); break;
            case 4: nb.setSmallIcon(icon(bat == null ? "--" : Math.round(Stats.batTemp(bat)) + "°", "BAT")); break;
            case 5: nb.setSmallIcon(icon(Stats.shortSpeed(net.down), "↓/s")); break;
            case 6: nb.setSmallIcon(icon("↓" + Stats.shortSpeed(net.down), "↑" + Stats.shortSpeed(net.up))); break;
            default: nb.setSmallIcon(R.drawable.ic_ram);
        }

        String st = statusText(sb, usedPct, avail, cpu, bat);
        if (p.getBoolean("sbt", false) && st != null)
            overlay.showStatus(st, p.getInt("sbt_pos", 1), p.getInt("sbt_off", 12));
        else overlay.hideStatus();
    }

    private void trackSignals(Intent bat, int usedPct) {
        if (!BuildConfig.SUGGESTIONS || !p.getBoolean("sugg", true)) return;
        if (bat == null) bat = Stats.battery(this);
        long now = System.currentTimeMillis();
        boolean hot = bat != null && Stats.batTemp(bat) >= 42;
        if (!hot) hotSince = 0;
        else if (hotSince == 0) hotSince = now;
        else if (now - hotSince >= 5 * 60_000L) { p.edit().putLong("sig_hot", now).apply(); hotSince = now; }
        sigTicks++;
        if (usedPct >= 90) sigRamHigh++;
        if (sigTicks >= 30) {
            int n = p.getInt("sig_n", 0) + sigTicks, h = p.getInt("sig_ram", 0) + sigRamHigh;
            if (n > 50000) { n /= 2; h /= 2; } // slowly forget old data
            p.edit().putInt("sig_n", n).putInt("sig_ram", h).apply();
            sigTicks = 0; sigRamHigh = 0;
        }
    }

    private String statusText(int sb, int usedPct, long avail, int cpu, Intent bat) {
        switch (sb) {
            case 1: return "RAM " + usedPct + "%";
            case 2: return "RAM " + String.format(Locale.US, "%.1fG", avail / 1073741824.0);
            case 3: return "CPU " + (cpu < 0 ? "--" : cpu + "%");
            case 4: return bat == null ? null : Stats.fmt1(Stats.batTemp(bat)) + "°C";
            case 5: return "↓" + Stats.shortSpeed(net.down) + "/s";
            case 6: return "↓" + Stats.shortSpeed(net.down) + " ↑" + Stats.shortSpeed(net.up);
            default: return null;
        }
    }

    private ArrayList<Overlay.Item> overlayItems(int ramPct, int cpu, Intent bat) {
        ArrayList<Overlay.Item> o = new ArrayList<>();
        String[][] k = Stats.OV;
        if (Stats.on(p, k, 0)) o.add(new Overlay.Item("FPS", fps > 0 ? String.valueOf(fps) : "--", fps >= 55 ? Overlay.GOOD : fps >= 30 ? Overlay.WARN : Overlay.BAD));
        if (Stats.on(p, k, 1)) o.add(new Overlay.Item("CPU", cpu < 0 ? "--" : cpu + "%", Overlay.level(cpu, 60, 85)));
        if (Stats.on(p, k, 2)) o.add(new Overlay.Item("RAM", ramPct + "%", Overlay.level(ramPct, 75, 90)));
        if (Stats.on(p, k, 3)) { int g = Stats.gpuLoad(); o.add(new Overlay.Item("GPU", g < 0 ? "--" : g + "%", Overlay.level(g, 60, 85))); }
        if (Stats.on(p, k, 4)) { float t = Stats.cpuTemp(); o.add(new Overlay.Item("CPU", t <= 0 ? "--" : Math.round(t) + "°", Overlay.level(t, 45, 60))); }
        if (bat != null && Stats.on(p, k, 5)) { int b = Stats.batPct(bat); o.add(new Overlay.Item("BAT", b + "%", b <= 15 ? Overlay.BAD : b <= 30 ? Overlay.WARN : Overlay.GOOD)); }
        if (bat != null && Stats.on(p, k, 6)) { float t = Stats.batTemp(bat); o.add(new Overlay.Item("BAT", Stats.fmt1(t) + "°", Overlay.level(t, 38, 43))); }
        if (Stats.on(p, k, 7)) o.add(new Overlay.Item("↓", Stats.shortSpeed(net.down) + "/s", Overlay.NEUTRAL));
        if (Stats.on(p, k, 8)) o.add(new Overlay.Item("", android.text.format.DateFormat.format("HH:mm", System.currentTimeMillis()).toString(), Overlay.NEUTRAL));
        return o;
    }

    // Draws two lines of text as the status-bar icon (only the alpha channel is used by Android)
    private Icon icon(String top, String bottom) {
        bmp.eraseColor(Color.TRANSPARENT);
        fit(top, 50);
        cv.drawText(top, 48, 46, paint);
        fit(bottom, 38);
        cv.drawText(bottom, 48, 90, paint);
        return Icon.createWithBitmap(bmp);
    }

    private void fit(String s, float max) {
        paint.setTextSize(max);
        float w = paint.measureText(s);
        if (w > 94) paint.setTextSize(max * 94 / w);
    }

    private void update() { build(); nm.notify(ID, nb.build()); }

    @Override public void onDestroy() {
        running = false;
        h.removeCallbacks(tick);
        setFps(false);
        overlay.hide();
        overlay.hideStatus();
        unregisterReceiver(screen);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    static void start(Context c) {
        Intent i = new Intent(c, RamService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }
}
