package com.bench.wave;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** All persistent state. One file, one owner, string keys. */
final class Prefs {
    private static final String FILE = "wave";

    static final String K_THEME = "theme";          // 0 system, 1 dark, 2 light
    static final String K_CLOCK = "clock";
    static final String K_ICONS = "icons";
    static final String K_LABELS = "labels";
    static final String K_SCALE = "scale";          // percent 85..135
    static final String K_DIM = "dim";              // percent 0..80
    static final String K_WAVE = "wave";
    static final String K_WAVE_LEFT = "wave_left";
    static final String K_FAVORITES = "favorites";  // ordered, comma separated pkg/cls
    static final String K_HIDDEN = "hidden";        // ordered, comma separated pkg/cls
    static final String K_ICON_OVERRIDES = "icon_overrides"; // "component<TAB>path" lines
    static final String K_FONT_CLOCK = "font_clock"; // absolute file paths, empty = bundled
    static final String K_FONT_APPS = "font_apps";
    static final String K_FONT_UI = "font_ui";
    static final String K_USAGE = "usage";          // "component<TAB>epochMillis" lines, newest first

    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean dark(Context c) {
        int mode = sp(c).getInt(K_THEME, 1);
        if (mode == 1) return true;
        if (mode == 2) return false;
        int ui = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return ui == Configuration.UI_MODE_NIGHT_YES;
    }

    static int themeMode(Context c) { return sp(c).getInt(K_THEME, 1); }

    static boolean clock(Context c) { return sp(c).getBoolean(K_CLOCK, true); }
    static boolean icons(Context c) { return sp(c).getBoolean(K_ICONS, true); }
    static boolean labels(Context c) { return sp(c).getBoolean(K_LABELS, true); }
    static boolean wave(Context c) { return sp(c).getBoolean(K_WAVE, true); }
    static boolean waveLeft(Context c) { return sp(c).getBoolean(K_WAVE_LEFT, false); }

    static float scale(Context c) { return sp(c).getInt(K_SCALE, 100) / 100f; }
    static int dim(Context c) { return sp(c).getInt(K_DIM, 34); }

    /** Ordered set encoded as "a/b,c/d". Empty string means empty set. */
    static String[] list(Context c, String key) {
        String raw = sp(c).getString(key, "");
        if (raw == null || raw.isEmpty()) return new String[0];
        return raw.split(",");
    }

    /** Path of a custom icon for one app, or null. */
    static String iconOverride(Context c, String key) {
        String raw = sp(c).getString(K_ICON_OVERRIDES, "");
        if (raw == null || raw.isEmpty()) return null;
        String[] lines = raw.split("\n");
        for (String line : lines) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            if (line.substring(0, tab).equals(key)) return line.substring(tab + 1);
        }
        return null;
    }

    static void setIconOverride(Context c, String key, String path) {
        String raw = sp(c).getString(K_ICON_OVERRIDES, "");
        StringBuilder sb = new StringBuilder();
        if (raw != null && !raw.isEmpty()) {
            for (String line : raw.split("\n")) {
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                if (line.substring(0, tab).equals(key)) continue;
                sb.append(line).append('\n');
            }
        }
        sb.append(key).append('\t').append(path);
        sp(c).edit().putString(K_ICON_OVERRIDES, sb.toString()).apply();
    }

    static void clearIconOverride(Context c, String key) {
        String raw = sp(c).getString(K_ICON_OVERRIDES, "");
        if (raw == null || raw.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            if (line.substring(0, tab).equals(key)) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        sp(c).edit().putString(K_ICON_OVERRIDES, sb.toString()).apply();
    }

    static void clearIconOverrides(Context c) {
        sp(c).edit().remove(K_ICON_OVERRIDES).apply();
    }

    /** Launch timestamps, component to millis. Anything absent was never opened. */
    static java.util.Map<String, Long> usageMap(Context c) {
        java.util.Map<String, Long> out = new java.util.HashMap<String, Long>();
        String raw = sp(c).getString(K_USAGE, "");
        if (raw == null || raw.isEmpty()) return out;
        String[] lines = raw.split("\n");
        for (int i = 0; i < lines.length; i++) {
            int tab = lines[i].indexOf('\t');
            if (tab <= 0) continue;
            try {
                out.put(lines[i].substring(0, tab),
                        Long.valueOf(Long.parseLong(lines[i].substring(tab + 1))));
            } catch (NumberFormatException ignored) {
                // A truncated line is dropped, not fatal: the map simply has no entry.
            }
        }
        return out;
    }

    /** Records a launch. Bounded at 120 entries so the file cannot grow without limit. */
    static void markUsed(Context c, String key) {
        if (key == null || key.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append(key).append('\t').append(System.currentTimeMillis()).append('\n');
        int kept = 1;
        String raw = sp(c).getString(K_USAGE, "");
        if (raw != null && !raw.isEmpty()) {
            String[] lines = raw.split("\n");
            for (int i = 0; i < lines.length && kept < 120; i++) {
                int tab = lines[i].indexOf('\t');
                if (tab <= 0) continue;
                if (lines[i].substring(0, tab).equals(key)) continue;
                sb.append(lines[i]).append('\n');
                kept++;
            }
        }
        sp(c).edit().putString(K_USAGE, sb.toString()).apply();
    }

    static void putList(Context c, String key, Iterable<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if (sb.length() > 0) sb.append(',');
            sb.append(k);
        }
        sp(c).edit().putString(key, sb.toString()).apply();
    }
}
