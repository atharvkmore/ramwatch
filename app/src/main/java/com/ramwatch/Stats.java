package com.ramwatch;

import android.content.*;
import android.opengl.*;
import android.os.*;
import java.io.*;
import java.util.Locale;

final class Stats {
    // key, label, default-on
    static final String[][] OPTS = {
        {"n_ram", "RAM free", "1"}, {"n_bat", "Battery %", "1"}, {"n_cpu", "CPU load", "1"},
        {"n_bt", "Battery temperature", "0"}, {"n_ct", "CPU temperature", "0"},
        {"n_cf", "CPU clock (average)", "0"}, {"n_sto", "Storage free", "0"}, {"n_up", "Uptime", "0"}};

    static boolean on(SharedPreferences p, int i) { return p.getBoolean(OPTS[i][0], "1".equals(OPTS[i][2])); }

    static String read(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String l = r.readLine();
            return l == null ? null : l.trim();
        } catch (Throwable e) { return null; }
    }

    static long readLong(String path) {
        String s = read(path);
        try { return s == null ? -1 : Long.parseLong(s); } catch (Throwable e) { return -1; }
    }

    static String gb(long b) {
        double g = b / 1073741824.0;
        return g >= 1 ? String.format(Locale.US, "%.2f GB", g) : (b / 1048576) + " MB";
    }

    static int cores() { return Runtime.getRuntime().availableProcessors(); }

    private static String cpu(int c, String f) { return "/sys/devices/system/cpu/cpu" + c + "/cpufreq/" + f; }

    static long curFreq(int c) { return readLong(cpu(c, "scaling_cur_freq")); }

    static String clockRange() {
        long mn = Long.MAX_VALUE, mx = 0;
        for (int i = 0; i < cores(); i++) {
            long a = readLong(cpu(i, "cpuinfo_min_freq")), b = readLong(cpu(i, "cpuinfo_max_freq"));
            if (a > 0) mn = Math.min(mn, a);
            if (b > 0) mx = Math.max(mx, b);
        }
        return mx == 0 ? "n/a" : (mn / 1000) + " – " + (mx / 1000) + " MHz";
    }

    static long avgFreqMHz() {
        long s = 0; int n = 0;
        for (int i = 0; i < cores(); i++) { long f = curFreq(i); if (f > 0) { s += f; n++; } }
        return n == 0 ? -1 : s / n / 1000;
    }

    private static long lastIdle = -1, lastTotal = -1;

    // Real load from /proc/stat when readable, else clock-based estimate
    static int cpuLoad() {
        String l = read("/proc/stat");
        if (l != null && l.startsWith("cpu ")) {
            try {
                String[] t = l.split("\\s+");
                long idle = Long.parseLong(t[4]) + Long.parseLong(t[5]), total = 0;
                for (int i = 1; i < t.length; i++) total += Long.parseLong(t[i]);
                int r = -1;
                if (lastTotal > 0 && total > lastTotal)
                    r = (int) (100 - 100 * (idle - lastIdle) / (total - lastTotal));
                lastIdle = idle; lastTotal = total;
                if (r >= 0) return r;
            } catch (Throwable ignored) { }
        }
        double s = 0; int n = 0;
        for (int i = 0; i < cores(); i++) {
            long cur = curFreq(i), mx = readLong(cpu(i, "cpuinfo_max_freq"));
            if (cur > 0 && mx > 0) { s += (double) cur / mx; n++; }
        }
        return n == 0 ? -1 : (int) (s * 100 / n);
    }

    private static int tz = -2;

    static float cpuTemp() {
        if (tz == -2) {
            tz = -1;
            for (int i = 0; i < 60; i++) {
                String t = read("/sys/class/thermal/thermal_zone" + i + "/type");
                if (t == null) continue;
                t = t.toLowerCase(Locale.US);
                if ((t.contains("cpu") || t.contains("soc") || t.contains("tsens")) && readLong("/sys/class/thermal/thermal_zone" + i + "/temp") > 0) { tz = i; break; }
            }
        }
        if (tz < 0) return -1;
        long v = readLong("/sys/class/thermal/thermal_zone" + tz + "/temp");
        return v <= 0 ? -1 : (v > 1000 ? v / 1000f : v);
    }

    static int gpuLoad() {
        String s = read("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage");
        try {
            if (s != null) return Integer.parseInt(s.replaceAll("[^0-9]", ""));
            s = read("/sys/class/kgsl/kgsl-3d0/gpubusy");
            if (s != null) {
                String[] t = s.split("\\s+");
                long b = Long.parseLong(t[0]), tot = Long.parseLong(t[1]);
                return tot > 0 ? (int) (b * 100 / tot) : 0;
            }
            s = read("/sys/kernel/gpu/gpu_busy");
            if (s != null) return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Throwable ignored) { }
        return -1;
    }

    static String[] gpu() {
        try {
            EGLDisplay d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] v = new int[2];
            EGL14.eglInitialize(d, v, 0, v, 1);
            EGLConfig[] cf = new EGLConfig[1];
            int[] n = new int[1];
            EGL14.eglChooseConfig(d, new int[]{EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE}, 0, cf, 0, 1, n, 0);
            EGLContext ctx = EGL14.eglCreateContext(d, cf[0], EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            EGLSurface s = EGL14.eglCreatePbufferSurface(d, cf[0], new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            EGL14.eglMakeCurrent(d, s, s, ctx);
            String[] r = {GLES20.glGetString(GLES20.GL_RENDERER), GLES20.glGetString(GLES20.GL_VENDOR), GLES20.glGetString(GLES20.GL_VERSION)};
            EGL14.eglMakeCurrent(d, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(d, s);
            EGL14.eglDestroyContext(d, ctx);
            EGL14.eglTerminate(d);
            return r;
        } catch (Throwable t) { return null; }
    }

    static Intent battery(Context c) { return c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); }

    static int batPct(Intent b) {
        int l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), s = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return l < 0 ? -1 : l * 100 / Math.max(s, 1);
    }

    static float batTemp(Intent b) { return b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f; }

    static boolean charging(Intent b) {
        int s = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL;
    }

    static String status(Intent b) {
        switch (b.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not charging";
            default: return "Unknown";
        }
    }

    static String health(Intent b) {
        switch (b.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over voltage";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Cold";
            default: return "Unknown";
        }
    }

    static long[] storage() {
        StatFs s = new StatFs(Environment.getDataDirectory().getPath());
        return new long[]{s.getTotalBytes(), s.getAvailableBytes()};
    }

    static String uptime() {
        long s = SystemClock.elapsedRealtime() / 1000;
        return String.format(Locale.US, "%dd %02dh %02dm", s / 86400, s / 3600 % 24, s / 60 % 60);
    }

    static String fmt1(float f) { return String.format(Locale.US, "%.1f", f); }
}
