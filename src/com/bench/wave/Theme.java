package com.bench.wave;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A theme is a directory with a theme.json and, optionally, icons and fonts.
 *
 *   filesDir/themes/&lt;id&gt;/theme.json
 *   filesDir/themes/&lt;id&gt;/icons/&lt;package&gt;.png
 *   filesDir/themes/&lt;id&gt;/fonts/clock.ttf | apps.ttf | ui.ttf
 *
 * The built-ins live in code so the launcher always has one working look, even with the
 * theme folder empty or a half-written import on disk.
 */
final class Theme {

    static final String BUILTIN = "builtin";

    private static final int MAX_ENTRY_BYTES = 4 * 1024 * 1024;
    private static final int MAX_TOTAL_BYTES = 24 * 1024 * 1024;
    private static final int MAX_ENTRIES = 400;
    private static final String TAG = "WaveTheme";

    String id = BUILTIN;
    String name = "Star Trail";
    String author = "";
    boolean light;

    int background = 0xFF05070C;
    int foreground = 0xFFEDF1F7;
    int mid = 0xB4B4BECC;
    int dim = 0x7C7C8797;
    int accent = 0xFFAFC4DE;

    String clockStyle = "thin";   // thin | serif | mono | bold | condensed
    float clockScale = 1f;
    float clockSpacing = 0.02f;

    boolean iconDesaturate = false;
    int iconTint = 0xFFFFFFFF;
    float iconTintStrength = 0.12f;

    File dir; // null for built-ins

    static final class Entry {
        final String id;
        final String name;
        final boolean builtin;
        Entry(String id, String name, boolean builtin) {
            this.id = id;
            this.name = name;
            this.builtin = builtin;
        }
    }

    // ------------------------------------------------------------------ built-ins

    static Theme builtin(String id) {
        Theme t = new Theme();
        t.id = id == null ? "star-trail" : id;
        if ("paper".equals(t.id)) {
            t.name = "Paper";
            t.light = true;
            t.background = 0xFFF4F1EA;
            t.foreground = 0xFF15171C;
            t.mid = 0xB015171C;
            t.dim = 0x7015171C;
            t.accent = 0xFF6D5B45;
            t.clockStyle = "serif";
            t.clockScale = 1f;
            t.clockSpacing = 0f;
            t.iconDesaturate = false;
            t.iconTintStrength = 0f;
        } else if ("slate".equals(t.id)) {
            t.name = "Slate";
            t.background = 0xFF0F1418;
            t.foreground = 0xFFE6EDF3;
            t.mid = 0xB0E6EDF3;
            t.dim = 0x6EE6EDF3;
            t.accent = 0xFF7FE3C0;
            t.clockStyle = "mono";
            t.clockScale = 0.96f;
            t.clockSpacing = -0.01f;
            t.iconDesaturate = false;
            t.iconTintStrength = 0f;
        } else {
            // Star Trail: the ink-black starfield. Cool white type, thin clock, icons
            // pulled to grey so the wallpaper stays the only colour on screen.
            t.name = "Star Trail";
            t.background = 0xFF05070C;
            t.foreground = 0xFFEDF1F7;
            t.mid = 0xB4B4BECC;
            t.dim = 0x7C7C8797;
            t.accent = 0xFFAFC4DE;
            t.clockStyle = "thin";
            t.clockScale = 1f;
            t.clockSpacing = 0.02f;
            // Icons keep their own colour; the wallpaper is the only other colour source.
            t.iconDesaturate = false;
            t.iconTintStrength = 0f;
        }
        return t;
    }

    // ------------------------------------------------------------------ storage

    static File themesDir(Context c) {
        File dir = new File(c.getFilesDir(), "themes");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static List<Entry> available(Context c) {
        List<Entry> out = new ArrayList<Entry>();
        out.add(new Entry("star-trail", "Star Trail", true));
        out.add(new Entry("slate", "Slate", true));
        out.add(new Entry("paper", "Paper", true));

        File[] dirs = themesDir(c).listFiles();
        if (dirs == null) return out;
        for (int i = 0; i < dirs.length; i++) {
            if (!dirs[i].isDirectory()) continue;
            File json = new File(dirs[i], "theme.json");
            if (!json.isFile()) continue;
            Theme t = fromDir(dirs[i]);
            if (t == null) continue;
            out.add(new Entry(dirs[i].getName(), t.name, false));
        }
        return out;
    }

    static String idFor(Context c) {
        return Prefs.sp(c).getString(Prefs.K_THEME_ID, "star-trail");
    }

    static Theme current(Context c) {
        String id = idFor(c);
        if ("star-trail".equals(id) || "slate".equals(id) || "paper".equals(id)) {
            return builtin(id);
        }
        Theme t = fromDir(new File(themesDir(c), id));
        return t != null ? t : builtin("star-trail");
    }

    static void apply(Context c, String id) {
        Prefs.sp(c).edit().putString(Prefs.K_THEME_ID, id).apply();
        Theme t = current(c);
        applyFonts(c, t);
        Typefaces.invalidate();
        Prefs.bumpLibrary(c);
    }

    /** Copies a theme's font files into the launcher's own font slots. */
    private static void applyFonts(Context c, Theme t) {
        SharedPreferences.Editor e = Prefs.sp(c).edit();
        e.remove(Prefs.K_FONT_CLOCK);
        e.remove(Prefs.K_FONT_APPS);
        e.remove(Prefs.K_FONT_UI);
        e.commit();

        if (t.dir == null) return;
        File fonts = new File(t.dir, "fonts");
        if (!fonts.isDirectory()) return;
        copyFont(c, fonts, "clock", Prefs.K_FONT_CLOCK);
        copyFont(c, fonts, "apps", Prefs.K_FONT_APPS);
        copyFont(c, fonts, "ui", Prefs.K_FONT_UI);
    }

    private static void copyFont(Context c, File fonts, String slot, String key) {
        File src = find(fonts, slot);
        if (src == null) return;
        File outDir = new File(c.getFilesDir(), "fonts");
        if (!outDir.exists() && !outDir.mkdirs()) return;
        File out = new File(outDir, "theme-" + slot + ".ttf");
        if (!copy(src, out)) return;
        try {
            if (Typeface.createFromFile(out) == null) return;
        } catch (RuntimeException ex) {
            out.delete();
            return;
        }
        Prefs.sp(c).edit().putString(key, out.getAbsolutePath()).commit();
    }

    /** clock.ttf, clock.otf, clock — first match wins. */
    private static File find(File dir, String slot) {
        String[] exts = {".ttf", ".otf", ""};
        for (int i = 0; i < exts.length; i++) {
            File f = new File(dir, slot + exts[i]);
            if (f.isFile()) return f;
        }
        return null;
    }

    static Typeface clockTypeface(Context c) {
        String path = Prefs.sp(c).getString(Prefs.K_FONT_CLOCK, "");
        if (path != null && !path.isEmpty()) {
            Typeface f = Typefaces.forRole(c, Prefs.K_FONT_CLOCK);
            if (f != null) return f;
        }
        Theme t = current(c);
        if ("serif".equals(t.clockStyle)) return Typeface.create("serif", Typeface.NORMAL);
        if ("mono".equals(t.clockStyle)) return Typeface.create("monospace", Typeface.NORMAL);
        if ("bold".equals(t.clockStyle)) return Typeface.create("sans-serif", Typeface.BOLD);
        if ("condensed".equals(t.clockStyle)) {
            return Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        }
        return Typeface.create("sans-serif-thin", Typeface.NORMAL);
    }

    // ------------------------------------------------------------------ parse

    private static Theme fromDir(File dir) {
        File json = new File(dir, "theme.json");
        if (!json.isFile()) return null;
        String raw = readText(json, 64 * 1024);
        if (raw == null) return null;
        try {
            JSONObject root = new JSONObject(raw);
            Theme t = new Theme();
            t.id = dir.getName();
            t.dir = dir;
            t.name = root.optString("name", dir.getName());
            t.author = root.optString("author", "");
            t.light = root.optBoolean("light", false);

            JSONObject colors = root.optJSONObject("colors");
            if (colors != null) {
                t.background = colour(colors, "background", t.background);
                t.foreground = colour(colors, "foreground", t.foreground);
                t.mid = colour(colors, "mid", t.mid);
                t.dim = colour(colors, "dim", t.dim);
                t.accent = colour(colors, "accent", t.accent);
            }

            JSONObject clock = root.optJSONObject("clock");
            if (clock != null) {
                t.clockStyle = clock.optString("style", t.clockStyle);
                t.clockScale = clamp((float) clock.optDouble("scale", t.clockScale), 0.7f, 1.4f);
                t.clockSpacing = clamp((float) clock.optDouble("spacing", t.clockSpacing),
                        -0.05f, 0.3f);
            }

            JSONObject icons = root.optJSONObject("icons");
            if (icons != null) {
                t.iconDesaturate = icons.optBoolean("desaturate", t.iconDesaturate);
                t.iconTint = colour(icons, "tint", t.iconTint);
                t.iconTintStrength = clamp(
                        (float) icons.optDouble("tintStrength", t.iconTintStrength), 0f, 1f);
            }
            return t;
        } catch (Exception ex) {
            Log.w(TAG, "bad theme.json in " + dir.getName(), ex);
            return null;
        }
    }

    private static int colour(JSONObject o, String key, int fallback) {
        String v = o.optString(key, null);
        if (v == null) return fallback;
        try {
            return (int) Long.parseLong(v.replace("#", ""), 16)
                    | (v.length() <= 7 ? 0xFF000000 : 0);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    // ------------------------------------------------------------------ import

    /**
     * Unpacks a theme zip into filesDir/themes/&lt;slug&gt;. Entries are written one at a
     * time into a temporary directory first: a rejected archive leaves no half-theme
     * behind, and every path is checked so a "../" entry cannot escape.
     */
    static Theme importZip(Context c, Uri uri, String fallbackName) throws Exception {
        String slug = slug(fallbackName);
        File tmp = new File(themesDir(c), ".incoming-" + System.currentTimeMillis());
        if (!tmp.mkdirs()) throw new java.io.IOException("cannot create theme folder");

        int total = 0;
        int count = 0;
        InputStream raw = null;
        ZipInputStream zip = null;
        try {
            raw = c.getContentResolver().openInputStream(uri);
            if (raw == null) throw new java.io.IOException("cannot open the archive");
            zip = new ZipInputStream(new java.io.BufferedInputStream(raw));

            ZipEntry entry;
            byte[] buf = new byte[16 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) throw new java.io.IOException("too many files");
                String path = clean(entry.getName());
                if (path == null) continue;
                if (entry.isDirectory()) {
                    new File(tmp, path).mkdirs();
                    continue;
                }
                File out = new File(tmp, path);
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new java.io.IOException("cannot create " + path);
                }
                long written = 0;
                OutputStream os = new FileOutputStream(out);
                try {
                    int read;
                    while ((read = zip.read(buf)) > 0) {
                        written += read;
                        total += read;
                        if (written > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                            throw new java.io.IOException("archive too large");
                        }
                        os.write(buf, 0, read);
                    }
                } finally {
                    os.close();
                }
            }
        } finally {
            try { if (zip != null) zip.close(); } catch (Exception ignored) { }
            try { if (raw != null) raw.close(); } catch (Exception ignored) { }
        }

        // Some archives wrap everything in one top folder; unwrap it.
        File root = tmp;
        File[] kids = tmp.listFiles();
        if (kids != null && kids.length == 1 && kids[0].isDirectory()
                && new File(kids[0], "theme.json").isFile()) {
            root = kids[0];
        }

        if (!new File(root, "theme.json").isFile()) {
            deleteTree(tmp);
            throw new java.io.IOException("theme.json missing");
        }

        Theme parsed = fromDir(root);
        if (parsed == null) {
            deleteTree(tmp);
            throw new java.io.IOException("theme.json is not valid");
        }

        File target = new File(themesDir(c), slug);
        deleteTree(target);
        if (!root.renameTo(target)) {
            if (!copyTree(root, target)) {
                deleteTree(tmp);
                deleteTree(target);
                throw new java.io.IOException("cannot store the theme");
            }
            deleteTree(tmp);
        } else if (!tmp.equals(root)) {
            deleteTree(tmp);
        }

        Theme out = fromDir(target);
        if (out == null) throw new java.io.IOException("theme vanished after import");
        return out;
    }

    private static String clean(String name) {
        if (name == null) return null;
        String path = name.replace('\\', '/');
        while (path.startsWith("./")) path = path.substring(2);
        if (path.startsWith("/") || path.contains("..")) return null;
        if (path.startsWith("__MACOSX") || path.endsWith(".DS_Store")) return null;
        if (path.isEmpty()) return null;
        return path;
    }

    private static String slug(String name) {
        StringBuilder sb = new StringBuilder();
        String base = name == null ? "" : name.toLowerCase();
        for (int i = 0; i < base.length() && sb.length() < 40; i++) {
            char ch = base.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) sb.append(ch);
            else if (ch == ' ' || ch == '-' || ch == '_') sb.append('-');
        }
        String out = sb.toString();
        while (out.startsWith("-")) out = out.substring(1);
        while (out.endsWith("-")) out = out.substring(0, out.length() - 1);
        return out.isEmpty() ? "theme-" + System.currentTimeMillis() : out;
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (int i = 0; i < kids.length; i++) deleteTree(kids[i]);
        }
        f.delete();
    }

    private static boolean copyTree(File from, File to) {
        if (!to.exists() && !to.mkdirs()) return false;
        File[] kids = from.listFiles();
        if (kids == null) return true;
        for (int i = 0; i < kids.length; i++) {
            File target = new File(to, kids[i].getName());
            if (kids[i].isDirectory()) {
                if (!copyTree(kids[i], target)) return false;
            } else if (!copy(kids[i], target)) {
                return false;
            }
        }
        return true;
    }

    private static boolean copy(File from, File to) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new java.io.FileInputStream(from);
            out = new FileOutputStream(to);
            byte[] buf = new byte[16 * 1024];
            int read;
            while ((read = in.read(buf)) > 0) out.write(buf, 0, read);
            return true;
        } catch (Exception ex) {
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
            try { if (out != null) out.close(); } catch (Exception ignored) { }
        }
    }

    private static String readText(File f, int limit) {
        InputStream in = null;
        try {
            in = new java.io.FileInputStream(f);
            byte[] buf = new byte[limit];
            int read = in.read(buf);
            if (read <= 0) return null;
            return new String(buf, 0, read, "UTF-8");
        } catch (Exception ex) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
        }
    }

    /** Icon supplied by the theme for one package, or null. */
    File icon(String pkg) {
        if (dir == null) return null;
        File f = new File(new File(dir, "icons"), pkg + ".png");
        return f.isFile() ? f : null;
    }

    File iconDir() {
        return dir == null ? null : new File(dir, "icons");
    }
}
