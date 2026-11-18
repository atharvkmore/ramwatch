package com.ramwatch;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    static final int BG = 0xFF0E1116, CARD = 0xFF161B22, ACC = 0xFF3DDC84, DIM = 0xFF8B949E;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
    private ActivityManager am;
    private BatteryManager bm;
    private LinearLayout root;
    private Button toggle;
    private TextView vRamFree, vRamUsed, vCpuLoad, vCpuTemp, vGpuLoad, vBat, vBatTemp, vSto, vUp,
            vBatStat, vBatHealth, vBatV, vBatCur, vBatTech;
    private TextView[] vCore;
    private final Runnable tick = new Runnable() {
        public void run() { refresh(); h.postDelayed(this, 1000); }
    };

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        final SharedPreferences p = getSharedPreferences("p", 0);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(16), dp(14), dp(28));
        sv.addView(root);

        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        hd.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(54)));
        TextView t = new TextView(this);
        t.setText("RAM Watch");
        t.setTextColor(Color.WHITE);
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(12), 0, 0, 0);
        hd.addView(t);
        root.addView(hd);

        toggle = new Button(this);
        toggle.setTextColor(Color.BLACK);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ACC);
        bg.setCornerRadius(dp(12));
        toggle.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.topMargin = dp(14);
        root.addView(toggle, lp);
        toggle.setOnClickListener(v -> {
            boolean on = !RamService.running;
            p.edit().putBoolean("on", on).apply();
            if (on) RamService.start(this); else stopService(new Intent(this, RamService.class));
            h.postDelayed(this::refresh, 300);
        });

        LinearLayout c = card("Live");
        vRamFree = row(c, "RAM free"); vRamUsed = row(c, "RAM used");
        vCpuLoad = row(c, "CPU load"); vCpuTemp = row(c, "CPU temp"); vGpuLoad = row(c, "GPU load");
        vBat = row(c, "Battery"); vBatTemp = row(c, "Battery temp");
        vSto = row(c, "Storage free"); vUp = row(c, "Uptime");

        c = card("Device");
        row(c, "Model", Build.MANUFACTURER + " " + Build.MODEL);
        row(c, "Codename", Build.DEVICE);
        row(c, "Board", Build.BOARD);
        row(c, "Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        row(c, "Security patch", Build.VERSION.SECURITY_PATCH);
        row(c, "Kernel", System.getProperty("os.version"));

        c = card("CPU / SoC");
        if (Build.VERSION.SDK_INT >= 31) row(c, "SoC", Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL);
        row(c, "Hardware", Build.HARDWARE);
        row(c, "Cores", String.valueOf(Stats.cores()));
        row(c, "ABI", Build.SUPPORTED_ABIS[0]);
        row(c, "Clock range", Stats.clockRange());
        int n = Stats.cores();
        vCore = new TextView[n];
        for (int i = 0; i < n; i++) vCore[i] = row(c, "Core " + i);

        c = card("GPU");
        String[] g = Stats.gpu();
        row(c, "Renderer", g == null ? null : g[0]);
        row(c, "Vendor", g == null ? null : g[1]);
        row(c, "OpenGL ES", g == null ? null : g[2]);

        DisplayMetrics dm = new DisplayMetrics();
        Display d = getWindowManager().getDefaultDisplay();
        d.getRealMetrics(dm);
        c = card("Display");
        row(c, "Resolution", dm.widthPixels + " × " + dm.heightPixels);
        row(c, "Density", dm.densityDpi + " dpi");
        row(c, "Refresh rate", Math.round(d.getRefreshRate()) + " Hz");

        am.getMemoryInfo(mi);
        c = card("Memory");
        row(c, "Total RAM", Stats.gb(mi.totalMem));
        row(c, "Total storage", Stats.gb(Stats.storage()[0]));

        c = card("Battery");
        vBatStat = row(c, "Status"); vBatHealth = row(c, "Health"); vBatV = row(c, "Voltage");
        vBatCur = row(c, "Current"); vBatTech = row(c, "Technology");

        c = card("Show in notification");
        for (int i = 0; i < Stats.OPTS.length; i++) {
            final String key = Stats.OPTS[i][0];
            CheckBox cb = new CheckBox(this);
            cb.setText(Stats.OPTS[i][1]);
            cb.setTextColor(Color.WHITE);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(ACC));
            cb.setChecked(Stats.on(p, i));
            cb.setOnCheckedChangeListener((x, on) -> p.edit().putBoolean(key, on).apply());
            c.addView(cb);
        }
        TextView note = new TextView(this);
        note.setText("First checked item is the notification title. Changes apply within 2 s.");
        note.setTextColor(DIM);
        note.setTextSize(12);
        c.addView(note);

        setContentView(sv);

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        if (p.getBoolean("on", true) && !RamService.running) {
            p.edit().putBoolean("on", true).apply();
            RamService.start(this);
        }
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(14));
        c.setBackground(g);
        TextView t = new TextView(this);
        t.setText(title.toUpperCase());
        t.setTextColor(ACC);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, 0, 0, dp(6));
        c.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        root.addView(c, lp);
        return c;
    }

    private TextView row(LinearLayout c, String k) { return row(c, k, "…"); }

    private TextView row(LinearLayout c, String k, String v) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, dp(4), 0, dp(4));
        TextView a = new TextView(this);
        a.setText(k);
        a.setTextColor(DIM);
        a.setTextSize(14);
        TextView b = new TextView(this);
        b.setText(v == null || v.trim().isEmpty() ? "n/a" : v);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setGravity(Gravity.END);
        r.addView(a, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(b, new LinearLayout.LayoutParams(0, -2, 1.5f));
        c.addView(r);
        return b;
    }

    private void refresh() {
        am.getMemoryInfo(mi);
        long used = mi.totalMem - mi.availMem;
        vRamFree.setText(Stats.gb(mi.availMem) + " (" + (mi.availMem * 100 / mi.totalMem) + "%)");
        vRamUsed.setText(Stats.gb(used) + " / " + Stats.gb(mi.totalMem));
        int l = Stats.cpuLoad();
        vCpuLoad.setText(l < 0 ? "n/a" : l + "%");
        float ct = Stats.cpuTemp();
        vCpuTemp.setText(ct <= 0 ? "n/a (restricted)" : Stats.fmt1(ct) + " °C");
        int gl = Stats.gpuLoad();
        vGpuLoad.setText(gl < 0 ? "n/a (restricted)" : gl + "%");
        for (int i = 0; i < vCore.length; i++) {
            long f = Stats.curFreq(i);
            vCore[i].setText(f > 0 ? (f / 1000) + " MHz" : "idle/offline");
        }
        Intent bi = Stats.battery(this);
        if (bi != null) {
            vBat.setText(Stats.batPct(bi) + "%" + (Stats.charging(bi) ? " ⚡" : ""));
            vBatTemp.setText(Stats.fmt1(Stats.batTemp(bi)) + " °C");
            vBatStat.setText(Stats.status(bi));
            vBatHealth.setText(Stats.health(bi));
            vBatV.setText(bi.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) + " mV");
            String tech = bi.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
            vBatTech.setText(tech == null ? "n/a" : tech);
        }
        int cur = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        vBatCur.setText(cur == Integer.MIN_VALUE || cur == 0 ? "n/a"
                : (Math.abs(cur) > 10000 ? cur / 1000 : cur) + " mA");
        long[] s = Stats.storage();
        vSto.setText(Stats.gb(s[1]) + " / " + Stats.gb(s[0]));
        vUp.setText(Stats.uptime());
        toggle.setText(RamService.running ? "Unpin from notification bar" : "Pin to notification bar");
    }

    @Override protected void onResume() { super.onResume(); h.post(tick); }
    @Override protected void onPause() { h.removeCallbacks(tick); super.onPause(); }
}
