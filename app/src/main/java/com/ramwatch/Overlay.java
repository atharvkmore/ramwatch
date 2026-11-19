package com.ramwatch;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

// Floating glass pill shown over games/other apps. Touches pass straight through it.
final class Overlay {
    static final int GOOD = 0xFF30D158, WARN = 0xFFFFD60A, BAD = 0xFFFF453A, NEUTRAL = Color.WHITE;

    static final class Item {
        final String label, value; final int color;
        Item(String l, String v, int c) { label = l; value = v; color = c; }
    }

    private final Context c;
    private final WindowManager wm;
    private LinearLayout root;
    private int pos = -1;
    private String sig = "";
    private final ArrayList<TextView> vals = new ArrayList<>();

    Overlay(Context c) { this.c = c; wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE); }

    static boolean allowed(Context c) { return Settings.canDrawOverlays(c); }

    private int dp(int v) { return (int) (v * c.getResources().getDisplayMetrics().density); }

    void show(ArrayList<Item> items, int position) {
        if (!allowed(c) || items.isEmpty()) { hide(); return; }
        if (root == null || position != pos) { hide(); create(position); if (root == null) return; }
        StringBuilder s = new StringBuilder();
        for (Item i : items) s.append(i.label).append('|');
        if (!s.toString().equals(sig)) { sig = s.toString(); rebuild(items); }
        for (int i = 0; i < items.size(); i++) {
            vals.get(i).setText(items.get(i).value);
            vals.get(i).setTextColor(items.get(i).color);
        }
    }

    private void create(int position) {
        pos = position;
        root = new LinearLayout(c);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD90B0D12);
        bg.setStroke(dp(1), 0x40FFFFFF);
        bg.setCornerRadius(dp(18));
        root.setBackground(bg);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | (pos == 0 ? Gravity.START : pos == 2 ? Gravity.END : Gravity.CENTER_HORIZONTAL);
        lp.x = dp(12);
        lp.y = dp(8);
        if (Build.VERSION.SDK_INT >= 28)
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        if (Build.VERSION.SDK_INT >= 31) lp.alpha = 0.8f; // Android 12+: lets touches pass through to the game
        try { wm.addView(root, lp); } catch (Throwable e) { root = null; }
    }

    private void rebuild(ArrayList<Item> items) {
        root.removeAllViews();
        vals.clear();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                View d = new View(c);
                d.setBackgroundColor(0x33FFFFFF);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(1), dp(14));
                dl.setMargins(dp(8), 0, dp(8), 0);
                root.addView(d, dl);
            }
            TextView l = new TextView(c);
            l.setText(items.get(i).label);
            l.setTextSize(10);
            l.setTextColor(0x99FFFFFF);
            l.setTypeface(Typeface.DEFAULT_BOLD);
            l.setPadding(0, 0, dp(4), 0);
            root.addView(l);
            TextView v = new TextView(c);
            v.setTextSize(13);
            v.setTypeface(Typeface.DEFAULT_BOLD);
            v.setFontFeatureSettings("tnum"); // fixed-width digits, no jitter
            root.addView(v);
            vals.add(v);
        }
    }

    // ---- Text label drawn in the status-bar area (for OEMs that ignore custom notification icons) ----
    private TextView sbv;
    private int sbCfg = -1;

    void showStatus(String txt, int position, int offPct) {
        if (!allowed(c) || txt == null) { hideStatus(); return; }
        int cfg = position * 1000 + offPct;
        if (sbv == null || cfg != sbCfg) {
            hideStatus();
            sbCfg = cfg;
            sbv = new TextView(c);
            sbv.setTextColor(Color.WHITE);
            sbv.setTextSize(12);
            sbv.setTypeface(Typeface.DEFAULT_BOLD);
            sbv.setGravity(Gravity.CENTER_VERTICAL);
            sbv.setSingleLine(true);
            sbv.setShadowLayer(3, 0, 0, 0x99000000);
            sbv.setFontFeatureSettings("tnum");
            int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
            int h = id > 0 ? c.getResources().getDimensionPixelSize(id) : dp(24);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, h,
                    Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | (position == 0 ? Gravity.START : position == 2 ? Gravity.END : Gravity.CENTER_HORIZONTAL);
            lp.x = c.getResources().getDisplayMetrics().widthPixels * offPct / 100;
            lp.y = 0;
            if (Build.VERSION.SDK_INT >= 28)
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            if (Build.VERSION.SDK_INT >= 31) lp.alpha = 0.8f;
            try { wm.addView(sbv, lp); } catch (Throwable e) { sbv = null; }
        }
        if (sbv != null) sbv.setText(txt);
    }

    void hideStatus() {
        if (sbv != null) { try { wm.removeView(sbv); } catch (Throwable ignored) { } }
        sbv = null;
        sbCfg = -1;
    }

    void hide() {
        if (root != null) { try { wm.removeView(root); } catch (Throwable ignored) { } }
        root = null;
        sig = "";
        pos = -1;
    }

    static int level(float v, float warn, float bad) { return v >= bad ? BAD : v >= warn ? WARN : GOOD; }
}
