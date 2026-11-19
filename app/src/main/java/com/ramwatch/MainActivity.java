package com.ramwatch;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import java.net.URLEncoder;

public class MainActivity extends Activity {
    static final int BLUE = 0xFF0A84FF, GREEN = 0xFF30D158, ORANGE = 0xFFFF9F0A, PURPLE = 0xFFBF5AF2,
            PINK = 0xFFFF375F, TEAL = 0xFF64D2FF, DIM = 0x99FFFFFF;
    static final String[] TAB_ICON = {"◉", "▣", "⚡", "⇅", "⚙"};
    static final String[] TAB_NAME = {"Home", "Hardware", "Battery", "Network", "Settings"};

    static final class Tile { TextView val, sub; ProgressBar bar; }

    private final Handler h = new Handler(Looper.getMainLooper());
    private final ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
    private final Stats.Net net = new Stats.Net();
    private ActivityManager am;
    private BatteryManager bm;
    private SharedPreferences p;
    private final ScrollView[] scrolls = new ScrollView[5];
    private final LinearLayout[] pages = new LinearLayout[5];
    private final TextView[] tabs = new TextView[5];
    private LinearLayout page;
    private int current = 0;

    private TextView toggle, vUp, vSto, vCpuTemp, vGpuLoad, vBatStat, vBatHealth, vBatV, vBatCur, vBatTech,
            vNetType, vOp, vBwD, vBwU, vRx, vTx, vWifiSig, vWifiLink, vWifiFreq, vCellSig;
    private TextView[] vCore;
    private Switch ovSwitch, sbtSwitch;
    private LinearLayout sugCard;
    private TextView sugTitle, sugText;
    private String sugId, sugQuery;
    private Tile tRam, tCpu, tBat, tNet, tBatLvl, tBatTemp, tDown, tUp;

    private final Runnable tick = new Runnable() {
        public void run() { refresh(); h.postDelayed(this, 1000); }
    };

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        p = getSharedPreferences("p", 0);

        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        // Background: deep gradient + soft colour glows (glass look without costly blur)
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0B1026, 0xFF1A1036, 0xFF06202B}));
        glow(root, 0x990A84FF, Gravity.TOP | Gravity.START, -90, -60);
        glow(root, 0x80BF5AF2, Gravity.CENTER_VERTICAL | Gravity.END, -120, 0);
        glow(root, 0x6630D158, Gravity.BOTTOM | Gravity.START, -100, -40);

        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(dp(20), dp(12), dp(20), dp(8));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        hd.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(12), 0, 0, 0);
        tt.addView(text("RAM Watch", 26, Color.WHITE, true));
        tt.addView(text(Build.MANUFACTURER + " " + Build.MODEL, 13, DIM, false));
        hd.addView(tt);
        col.addView(hd);

        FrameLayout pager = new FrameLayout(this);
        col.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1f));
        for (int i = 0; i < 5; i++) {
            ScrollView sv = new ScrollView(this);
            sv.setVerticalScrollBarEnabled(false);
            sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            LinearLayout l = new LinearLayout(this);
            l.setOrientation(LinearLayout.VERTICAL);
            l.setPadding(dp(16), 0, dp(16), dp(16));
            sv.addView(l);
            pager.addView(sv);
            scrolls[i] = sv;
            pages[i] = l;
        }

        LinearLayout bar = new LinearLayout(this);
        bar.setBackground(glass(30, 0x2EFFFFFF));
        bar.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(66));
        blp.setMargins(dp(16), dp(4), dp(16), dp(12));
        col.addView(bar, blp);
        for (int i = 0; i < 5; i++) {
            TextView t = text(TAB_ICON[i] + "\n" + TAB_NAME[i], 11, DIM, false);
            t.setGravity(Gravity.CENTER);
            final int k = i;
            t.setOnClickListener(v -> select(k));
            bar.addView(t, new LinearLayout.LayoutParams(0, -1, 1f));
            tabs[i] = t;
        }

        root.setOnApplyWindowInsetsListener((v, ins) -> {
            col.setPadding(0, ins.getSystemWindowInsetTop(), 0, ins.getSystemWindowInsetBottom());
            return ins;
        });

        buildHome();
        buildHardware();
        buildBattery();
        buildNetwork();
        buildSettings();
        setContentView(root);
        select(p.getInt("tab", 0));

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        if (p.getBoolean("on", true) && !RamService.running) {
            p.edit().putBoolean("on", true).apply();
            RamService.start(this);
        }
    }

    // ---------- Pages ----------

    private void buildHome() {
        page = pages[0];
        toggle = text("", 16, Color.WHITE, true);
        toggle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(8);
        page.addView(toggle, lp);
        toggle.setOnClickListener(v -> {
            boolean on = !RamService.running;
            p.edit().putBoolean("on", on).apply();
            if (on) RamService.start(this); else stopService(new Intent(this, RamService.class));
            h.postDelayed(this::refresh, 300);
        });

        LinearLayout r = hrow();
        tRam = tile(r, "Memory", BLUE, true);
        tCpu = tile(r, "CPU", ORANGE, false);
        r = hrow();
        tBat = tile(r, "Battery", GREEN, true);
        tNet = tile(r, "Network", PURPLE, false);

        if (BuildConfig.SUGGESTIONS) buildSuggestion();

        LinearLayout c = card("At a glance");
        vSto = row(c, "Storage free");
        vUp = row(c, "Uptime");
        row(c, "Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
    }

    private void buildHardware() {
        page = pages[1];
        LinearLayout c = card("Device");
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
        vCpuTemp = row(c, "Temperature");
        int n = Stats.cores();
        vCore = new TextView[n];
        for (int i = 0; i < n; i++) vCore[i] = row(c, "Core " + i);

        c = card("GPU");
        String[] g = Stats.gpu();
        row(c, "Renderer", g == null ? null : g[0]);
        row(c, "Vendor", g == null ? null : g[1]);
        row(c, "OpenGL ES", g == null ? null : g[2]);
        vGpuLoad = row(c, "Load");

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
    }

    private void buildBattery() {
        page = pages[2];
        LinearLayout r = hrow();
        tBatLvl = tile(r, "Level", GREEN, true);
        tBatTemp = tile(r, "Temperature", ORANGE, false);
        LinearLayout c = card("Details");
        vBatStat = row(c, "Status");
        vBatHealth = row(c, "Health");
        vBatV = row(c, "Voltage");
        vBatCur = row(c, "Current");
        vBatTech = row(c, "Technology");
    }

    private void buildNetwork() {
        page = pages[3];
        LinearLayout r = hrow();
        tDown = tile(r, "Download", TEAL, true);
        tUp = tile(r, "Upload", PINK, false);
        LinearLayout c = card("Connection");
        vNetType = row(c, "Type");
        vBwD = row(c, "Est. downlink");
        vBwU = row(c, "Est. uplink");
        vRx = row(c, "Received since boot");
        vTx = row(c, "Sent since boot");
        c = card("Wi-Fi");
        vWifiSig = row(c, "Signal");
        vWifiLink = row(c, "Link speed");
        vWifiFreq = row(c, "Band");
        c = card("Cellular");
        vCellSig = row(c, "Signal");
        vOp = row(c, "Operator");
    }

    private void buildSettings() {
        page = pages[4];
        LinearLayout c = card("Status bar icon");
        c.addView(text("Shows a live value next to the clock, as a small icon.", 12, DIM, false));
        radios(c, "sb", Stats.SB, 0, null);
        c.addView(text("OnePlus, Oppo, Realme and Xiaomi show the app logo instead. Turn this on to draw the value as text in the status bar:", 12, DIM, false));
        sbtSwitch = overlaySwitch("Show as text in status bar", "sbt");
        c.addView(sbtSwitch);
        c.addView(text("Text position", 13, DIM, true));
        radios(c, "sbt_pos", new String[]{"Left", "Centre", "Right"}, 1, null);
        final TextView offL = text("", 13, DIM, true);
        c.addView(offL);
        SeekBar off = new SeekBar(this);
        off.setMax(45);
        off.setProgress(p.getInt("sbt_off", 12));
        off.setProgressTintList(ColorStateList.valueOf(BLUE));
        off.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        offL.setText("Shift right: " + off.getProgress() + "% (move it clear of the camera and icons)");
        off.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean u) {
                offL.setText("Shift right: " + v + "% (move it clear of the camera and icons)");
                p.edit().putInt("sbt_off", v).apply();
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        c.addView(off);

        c = card("Floating overlay (over games & apps)");
        ovSwitch = overlaySwitch("Show overlay", "ov");
        c.addView(ovSwitch);
        c.addView(text("Position", 13, DIM, true));
        radios(c, "ov_pos", Stats.OV_POS, 2, null);
        c.addView(text("Show", 13, DIM, true));
        checks(c, Stats.OV);
        c.addView(text("FPS shows how fast the screen is refreshing. Android only lets privileged (root/Shizuku) apps read a game's exact frame rate.", 12, DIM, false));

        c = card("Notification shows");
        checks(c, Stats.OPTS);
        c.addView(text("The first ticked item becomes the notification title.", 12, DIM, false));

        c = card("Update interval");
        radios(c, "iv", new String[]{"1 second", "2 seconds (recommended)", "5 seconds (battery saver)"}, 2, new int[]{1, 2, 5});

        if (BuildConfig.SUGGESTIONS) {
            c = card("Suggestions");
            CheckBox sg = new CheckBox(this);
            sg.setText("Show helpful product suggestions when a problem is detected");
            sg.setTextColor(Color.WHITE);
            sg.setButtonTintList(ColorStateList.valueOf(BLUE));
            sg.setChecked(p.getBoolean("sugg", true));
            sg.setOnCheckedChangeListener((x, on) -> p.edit().putBoolean("sugg", on).apply());
            c.addView(sg);
            c.addView(text("At most one card, only on the Home tab. Links may earn us a small commission at no cost to you. Nothing is tracked.", 12, DIM, false));
        }

        c = card("About");
        row(c, "Version", BuildConfig.VERSION_NAME);
        row(c, "Licence", "GPL-3.0 (open source)");
        link(c, "View source code on GitHub", Config.REPO);
        link(c, "Report a bug or request a feature", Config.ISSUES);
        link(c, "Need a custom app or overlay? Get in touch", Config.CUSTOM_APP);
        if (!Config.SUPPORT_URL.isEmpty()) link(c, "Support the developer", Config.SUPPORT_URL);
        c.addView(text("Free forever. No ads, no subscriptions, no tracking.", 12, DIM, false));
    }

    // ---------- Live refresh ----------

    private void refresh() {
        toggle.setText(RamService.running ? "●  Pinned to status bar — tap to unpin" : "○  Pin to status bar");
        toggle.setBackground(glass(26, RamService.running ? 0x4030D158 : 0x330A84FF));
        switch (current) {
            case 0: refreshHome(); break;
            case 1: refreshHardware(); break;
            case 2: refreshBattery(); break;
            case 3: refreshNetwork(); break;
        }
    }

    private void refreshHome() {
        am.getMemoryInfo(mi);
        int usedPct = (int) ((mi.totalMem - mi.availMem) * 100 / mi.totalMem);
        set(tRam, Stats.gb(mi.availMem), "free of " + Stats.gb(mi.totalMem), usedPct);
        int l = Stats.cpuLoad();
        long f = Stats.avgFreqMHz();
        set(tCpu, l < 0 ? "n/a" : l + "%", f > 0 ? f + " MHz avg" : Stats.cores() + " cores", Math.max(l, 0));
        Intent bi = Stats.battery(this);
        if (bi != null) {
            int pct = Stats.batPct(bi);
            set(tBat, pct + "%", Stats.status(bi) + " · " + Stats.fmt1(Stats.batTemp(bi)) + "°C", pct);
        }
        net.sample();
        set(tNet, Stats.shortSpeed(net.down) + "/s", "↑ " + Stats.speed(net.up), speedPct(net.down));
        if (sugCard != null) updateSuggestion();
        long[] s = Stats.storage();
        vSto.setText(Stats.gb(s[1]) + " / " + Stats.gb(s[0]));
        vUp.setText(Stats.uptime());
    }

    private void refreshHardware() {
        float ct = Stats.cpuTemp();
        vCpuTemp.setText(ct <= 0 ? "n/a (restricted)" : Stats.fmt1(ct) + " °C");
        int gl = Stats.gpuLoad();
        vGpuLoad.setText(gl < 0 ? "n/a (restricted)" : gl + "%");
        for (int i = 0; i < vCore.length; i++) {
            long f = Stats.curFreq(i);
            vCore[i].setText(f > 0 ? (f / 1000) + " MHz" : "idle/offline");
        }
    }

    private void refreshBattery() {
        Intent bi = Stats.battery(this);
        if (bi == null) return;
        int pct = Stats.batPct(bi);
        float t = Stats.batTemp(bi);
        set(tBatLvl, pct + "%", Stats.charging(bi) ? "Charging ⚡" : "On battery", pct);
        set(tBatTemp, Stats.fmt1(t) + "°", t >= 40 ? "Hot" : t >= 35 ? "Warm" : "Normal", (int) Math.min(100, t * 2));
        vBatStat.setText(Stats.status(bi));
        vBatHealth.setText(Stats.health(bi));
        vBatV.setText(bi.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) + " mV");
        String tech = bi.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        vBatTech.setText(tech == null ? "n/a" : tech);
        int cur = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        vBatCur.setText(cur == Integer.MIN_VALUE || cur == 0 ? "n/a"
                : (Math.abs(cur) > 10000 ? cur / 1000 : cur) + " mA");
    }

    private void refreshNetwork() {
        net.sample();
        set(tDown, Stats.shortSpeed(net.down) + "/s", Stats.speed(net.down), speedPct(net.down));
        set(tUp, Stats.shortSpeed(net.up) + "/s", Stats.speed(net.up), speedPct(net.up));
        NetworkCapabilities nc = Stats.caps(this);
        vNetType.setText(Stats.netType(nc));
        vBwD.setText(nc == null ? "n/a" : kbps(nc.getLinkDownstreamBandwidthKbps()));
        vBwU.setText(nc == null ? "n/a" : kbps(nc.getLinkUpstreamBandwidthKbps()));
        long rx = TrafficStats.getTotalRxBytes(), tx = TrafficStats.getTotalTxBytes();
        vRx.setText(rx < 0 ? "n/a" : Stats.gb(rx));
        vTx.setText(tx < 0 ? "n/a" : Stats.gb(tx));
        int[] wf = Stats.wifi(this);
        if (wf == null) {
            vWifiSig.setText("Not connected");
            vWifiLink.setText("—");
            vWifiFreq.setText("—");
        } else {
            vWifiSig.setText(wf[0] + " dBm  " + Stats.bars(wf[1]));
            vWifiLink.setText(wf[2] > 0 ? wf[2] + " Mbps" : "n/a");
            vWifiFreq.setText(wf[3] > 4900 ? "5 GHz+ (" + wf[3] + " MHz)" : "2.4 GHz (" + wf[3] + " MHz)");
        }
        int[] ce = Stats.cell(this);
        vCellSig.setText(ce == null ? "n/a" : (ce[1] != Integer.MAX_VALUE ? ce[1] + " dBm  " : "") + Stats.bars(ce[0]));
        String op = Stats.operator(this);
        vOp.setText(op == null || op.isEmpty() ? "n/a" : op);
    }

    // ---------- UI helpers ----------

    private void select(int i) {
        current = i;
        for (int j = 0; j < 5; j++) {
            scrolls[j].setVisibility(j == i ? View.VISIBLE : View.GONE);
            tabs[j].setTextColor(j == i ? Color.WHITE : DIM);
            tabs[j].setBackground(j == i ? glass(24, 0x33FFFFFF) : null);
        }
        p.edit().putInt("tab", i).apply();
        refresh();
    }

    private GradientDrawable glass(int radius, int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), 0x33FFFFFF);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private void glow(FrameLayout r, int color, int gravity, int mx, int my) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{color, color & 0x00FFFFFF});
        g.setGradientRadius(dp(180));
        v.setBackground(g);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(360), dp(360), gravity);
        lp.setMargins(dp(mx), dp(my), dp(mx), dp(my));
        r.addView(v, lp);
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout hrow() {
        LinearLayout r = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        page.addView(r, lp);
        return r;
    }

    private Tile tile(LinearLayout parent, String label, int color, boolean first) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        c.setBackground(glass(24, 0x1FFFFFFF));
        TextView l = text(label.toUpperCase(), 11, color, true);
        l.setLetterSpacing(0.1f);
        c.addView(l);
        Tile t = new Tile();
        t.val = text("…", 26, Color.WHITE, true);
        t.val.setSingleLine(true);
        c.addView(t.val);
        t.sub = text("", 12, DIM, false);
        t.sub.setSingleLine(true);
        c.addView(t.sub);
        t.bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        t.bar.setMax(100);
        t.bar.setProgressTintList(ColorStateList.valueOf(color));
        t.bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x40FFFFFF));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(6));
        bp.topMargin = dp(10);
        c.addView(t.bar, bp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        if (!first) lp.leftMargin = dp(12);
        parent.addView(c, lp);
        return t;
    }

    private void set(Tile t, String val, String sub, int pct) {
        t.val.setText(val);
        t.sub.setText(sub);
        t.bar.setProgress(Math.max(0, Math.min(100, pct)));
    }

    // iOS-style grouped section: grey caps header above a glass card
    private LinearLayout card(String title) {
        TextView hdr = text(title.toUpperCase(), 12, DIM, true);
        hdr.setLetterSpacing(0.08f);
        hdr.setPadding(dp(14), dp(20), 0, dp(6));
        page.addView(hdr);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(10), dp(16), dp(10));
        c.setBackground(glass(22, 0x1FFFFFFF));
        page.addView(c, new LinearLayout.LayoutParams(-1, -2));
        return c;
    }

    private TextView row(LinearLayout c, String k) { return row(c, k, "…"); }

    private TextView row(LinearLayout c, String k, String v) {
        if (c.getChildCount() > 0 && c.getChildAt(c.getChildCount() - 1) instanceof LinearLayout) {
            View div = new View(this);
            div.setBackgroundColor(0x1AFFFFFF);
            c.addView(div, new LinearLayout.LayoutParams(-1, 1));
        }
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, dp(9), 0, dp(9));
        TextView a = text(k, 15, DIM, false);
        TextView b = text(v == null || v.trim().isEmpty() ? "n/a" : v, 15, Color.WHITE, false);
        b.setGravity(Gravity.END);
        r.addView(a, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(b, new LinearLayout.LayoutParams(0, -2, 1.5f));
        c.addView(r);
        return b;
    }

    private void radios(LinearLayout c, final String key, String[] labels, int def, int[] values) {
        RadioGroup g = new RadioGroup(this);
        int cur = p.getInt(key, def);
        c.addView(g);
        for (int i = 0; i < labels.length; i++) {
            RadioButton b = new RadioButton(this);
            b.setId(View.generateViewId());
            b.setText(labels[i]);
            b.setTextColor(Color.WHITE);
            b.setButtonTintList(ColorStateList.valueOf(BLUE));
            final int v = values == null ? i : values[i];
            b.setOnClickListener(x -> p.edit().putInt(key, v).apply());
            g.addView(b);
            if (v == cur) g.check(b.getId());
        }
    }

    // ---------- Helpful suggestions (one card max, only when a real problem is seen) ----------

    private void buildSuggestion() {
        TextView hdr = text("SUGGESTION", 12, DIM, true);
        hdr.setLetterSpacing(0.08f);
        hdr.setPadding(dp(14), dp(20), 0, dp(6));
        sugCard = new LinearLayout(this);
        sugCard.setOrientation(LinearLayout.VERTICAL);
        sugCard.setPadding(dp(16), dp(12), dp(16), dp(12));
        sugCard.setBackground(glass(22, 0x26FF9F0A));
        sugCard.addView(hdr);
        sugTitle = text("", 16, Color.WHITE, true);
        sugText = text("", 13, DIM, false);
        sugText.setPadding(0, dp(4), 0, dp(10));
        sugCard.addView(sugTitle);
        sugCard.addView(sugText);
        LinearLayout btns = new LinearLayout(this);
        TextView view = text("View options", 14, Color.BLACK, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable vb = new GradientDrawable();
        vb.setColor(ORANGE);
        vb.setCornerRadius(dp(16));
        view.setBackground(vb);
        view.setOnClickListener(v -> {
            try {
                String url = "https://www.amazon.in/s?k=" + URLEncoder.encode(sugQuery, "UTF-8")
                        + (Config.AMAZON_TAG.isEmpty() ? "" : "&tag=" + Config.AMAZON_TAG);
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
            } catch (Exception ignored) { }
        });
        btns.addView(view);
        TextView no = text("Not useful", 14, DIM, false);
        no.setPadding(dp(16), dp(8), dp(8), dp(8));
        no.setOnClickListener(v -> { p.edit().putLong("dis_" + sugId, System.currentTimeMillis()).apply(); updateSuggestion(); });
        btns.addView(no);
        TextView off = text("Hide all", 14, DIM, false);
        off.setPadding(dp(8), dp(8), dp(8), dp(8));
        off.setOnClickListener(v -> { p.edit().putBoolean("sugg", false).apply(); updateSuggestion(); });
        btns.addView(off);
        sugCard.addView(btns);
        sugCard.addView(text("We may earn a commission from these links.", 11, DIM, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        page.addView(sugCard, lp);
        sugCard.setVisibility(View.GONE);
    }

    private boolean fresh(String id) {
        return System.currentTimeMillis() - p.getLong("dis_" + id, 0) > 30L * 86_400_000L;
    }

    private void updateSuggestion() {
        String id = null, title = null, body = null, q = null;
        if (p.getBoolean("sugg", true)) {
            Intent bi = Stats.battery(this);
            int health = bi == null ? -1 : bi.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            int n = p.getInt("sig_n", 0);
            if (System.currentTimeMillis() - p.getLong("sig_hot", 0) < 3L * 86_400_000L && fresh("hot")) {
                id = "hot"; title = "Your phone has been running hot";
                body = "Battery stayed above 42°C for 5+ minutes. A clip-on cooling fan keeps games smooth and protects the battery.";
                q = "mobile cooling fan for gaming";
            } else if ((health == BatteryManager.BATTERY_HEALTH_DEAD || health == BatteryManager.BATTERY_HEALTH_OVERHEAT
                    || health == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE) && fresh("bat")) {
                id = "bat"; title = "Battery health: " + Stats.health(bi);
                body = "Your battery is reporting a problem. A power bank keeps you going until it is serviced.";
                q = "power bank 20000mah fast charging";
            } else if (n >= 1800 && p.getInt("sig_ram", 0) * 2 >= n && fresh("ram")) {
                id = "ram"; title = "RAM is almost always full";
                body = "Memory was above 90% for most of the time we checked. Closing unused apps helps; for heavy gaming, a phone with more RAM is the real fix.";
                q = "12GB RAM smartphone gaming";
            }
        }
        sugId = id;
        sugQuery = q;
        sugCard.setVisibility(id == null ? View.GONE : View.VISIBLE);
        if (id != null) { sugTitle.setText(title); sugText.setText(body); }
    }

    private void link(LinearLayout c, String label, final String url) {
        TextView t = text(label + "  ›", 15, BLUE, false);
        t.setPadding(0, dp(10), 0, dp(10));
        t.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))); } catch (Exception ignored) { }
        });
        c.addView(t);
    }

    // Switch that needs "display over other apps"; opens the permission screen when missing
    private Switch overlaySwitch(String label, final String key) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(Color.WHITE);
        sw.setTextSize(16);
        sw.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        sw.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{GREEN, 0x55FFFFFF}));
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setChecked(p.getBoolean(key, false) && Overlay.allowed(this));
        sw.setOnCheckedChangeListener((x, on) -> {
            if (on && !Overlay.allowed(this)) {
                p.edit().putString("ov_pending_key", key).apply();
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())));
                x.setChecked(false);
                return;
            }
            p.edit().putBoolean(key, on).apply();
            if (on && !RamService.running) { p.edit().putBoolean("on", true).apply(); RamService.start(this); }
        });
        return sw;
    }

    private void checks(LinearLayout c, String[][] opts) {
        for (int i = 0; i < opts.length; i++) {
            final String key = opts[i][0];
            CheckBox cb = new CheckBox(this);
            cb.setText(opts[i][1]);
            cb.setTextColor(Color.WHITE);
            cb.setButtonTintList(ColorStateList.valueOf(BLUE));
            cb.setChecked(Stats.on(p, opts, i));
            cb.setOnCheckedChangeListener((x, on) -> p.edit().putBoolean(key, on).apply());
            c.addView(cb);
        }
    }

    private static int speedPct(long bps) { return (int) Math.min(100, Math.log10(bps + 1) / 7 * 100); }

    private static String kbps(int k) { return k <= 0 ? "n/a" : k >= 1000 ? (k / 1000) + " Mbps" : k + " kbps"; }

    @Override protected void onResume() {
        super.onResume();
        // Returning from the overlay-permission screen: switch it on if granted
        String pk = p.getString("ov_pending_key", null);
        if (pk != null) {
            p.edit().remove("ov_pending_key").apply();
            if (Overlay.allowed(this)) ("sbt".equals(pk) ? sbtSwitch : ovSwitch).setChecked(true);
        }
        h.post(tick);
    }
    @Override protected void onPause() { h.removeCallbacks(tick); super.onPause(); }
}
