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
 * A theme is the whole look: colours, clock face and layout, icon pack, icon shape,
 * which elements are on screen. Stored as one JSON object in prefs so the editor can
 * change a single field without rewriting a file, and saved to
 * filesDir/themes/&lt;id&gt;/theme.json when it is kept as a preset or exported.
 *
 * The built-ins live in code so the launcher always has one working look, even with the
 * theme folder empty or a half-written import on disk.
 */
final class Theme {

    static final String BUILTIN = "star-trail";

    private static final int MAX_ENTRY_BYTES = 4 * 1024 * 1024;
    private static final int MAX_TOTAL_BYTES = 24 * 1024 * 1024;
    private static final int MAX_ENTRIES = 400;
    private static final String TAG = "WaveTheme";

    static final String[] SHAPES = {"none", "circle", "squircle", "rounded", "ring"};
    static final String[] SHAPE_LABELS = {"Original", "Circle", "Squircle", "Rounded square", "Ring"};
    static final String[] LAYOUTS = {"classic", "stacked", "inline", "time"};
    static final String[] LAYOUT_LABELS = {"Classic", "Stacked", "Inline", "Time only"};
    static final String[] ALIGNS = {"left", "center", "right"};
    static final String[] ALIGN_LABELS = {"Left", "Centre", "Right"};
    static final String[] FACES = {"thin", "light", "regular", "medium", "bold", "serif",
            "serif-light", "mono", "condensed"};
    static final String[] FACE_LABELS = {"Hairline", "Light", "Regular", "Medium", "Bold",
            "Serif", "Serif light", "Mono", "Condensed"};

    String id = BUILTIN;
    String name = "Star Trail";
    String author = "";
    boolean light;

    int background = 0xFF05070C;
    int foreground = 0xFFEDF1F7;
    int mid = 0xB4B4BECC;
    int dim = 0x7C7C8797;
    int accent = 0xFFAFC4DE;

    // Clock
    String clockFace = "thin";
    String clockLayout = "classic";
    String clockAlign = "left";
    float clockScale = 1f;
    float clockSpacing = 0.02f;

    // Icons
    String iconPack = "";          // package of an installed icon pack, "" = the app's own
    String iconShape = "none";
    float iconScale = 1f;          // 0.6 .. 1.0, how much of the tile the glyph fills
    int iconBg = 0xFF101620;       // fills the mask behind the glyph
    boolean iconDesaturate = false;
    int iconTint = 0xFFFFFFFF;
    float iconTintStrength = 0f;

    // Which elements are on screen
    boolean showDate = true;
    boolean showWeather = true;
    boolean showLabels = true;
    boolean showRail = true;

    File dir; // preset folder, null for built-ins and unsaved drafts

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

    Theme copy() {
        Theme t = new Theme();
        t.id = id;
        t.name = name;
        t.author = author;
        t.light = light;
        t.background = background;
        t.foreground = foreground;
        t.mid = mid;
        t.dim = dim;
        t.accent = accent;
        t.clockFace = clockFace;
        t.clockLayout = clockLayout;
        t.clockAlign = clockAlign;
        t.clockScale = clockScale;
        t.clockSpacing = clockSpacing;
        t.iconPack = iconPack;
        t.iconShape = iconShape;
        t.iconScale = iconScale;
        t.iconBg = iconBg;
        t.iconDesaturate = iconDesaturate;
        t.iconTint = iconTint;
        t.iconTintStrength = iconTintStrength;
        t.showDate = showDate;
        t.showWeather = showWeather;
        t.showLabels = showLabels;
        t.showRail = showRail;
        t.dir = dir;
        return t;
    }

    // ------------------------------------------------------------------ built-ins

    static Theme builtin(String id) {
        Theme t = new Theme();
        t.id = id == null ? BUILTIN : id;
        if ("paper".equals(t.id)) {
            t.name = "Paper";
            t.light = true;
            t.background = 0xFFF4F1EA;
            t.foreground = 0xFF15171C;
            t.mid = 0xB015171C;
            t.dim = 0x7015171C;
            t.accent = 0xFF6D5B45;
            t.clockFace = "serif";
            t.clockLayout = "stacked";
            t.clockSpacing = 0f;
            t.iconShape = "rounded";
            t.iconBg = 0xFFE6DFD2;
            t.iconScale = 0.86f;
        } else if ("slate".equals(t.id)) {
            t.name = "Slate";
            t.background = 0xFF0F1418;
            t.foreground = 0xFFE6EDF3;
            t.mid = 0xB0E6EDF3;
            t.dim = 0x6EE6EDF3;
            t.accent = 0xFF7FE3C0;
            t.clockFace = "mono";
            t.clockLayout = "inline";
            t.clockScale = 0.96f;
            t.clockSpacing = -0.01f;
            t.iconShape = "circle";
            t.iconBg = 0xFF1A2329;
            t.iconScale = 0.88f;
        } else if ("mono".equals(t.id)) {
            t.name = "Mono";
            t.background = 0xFF000000;
            t.foreground = 0xFFFFFFFF;
            t.mid = 0xB3FFFFFF;
            t.dim = 0x66FFFFFF;
            t.accent = 0xFFFFFFFF;
            t.clockFace = "bold";
            t.clockLayout = "classic";
            t.clockSpacing = -0.02f;
            t.iconShape = "circle";
            t.iconBg = 0xFF000000;
            t.iconScale = 0.82f;
            t.iconDesaturate = true;
            t.iconTint = 0xFFFFFFFF;
            t.iconTintStrength = 0f;
        } else {
            // Star Trail: the ink-black starfield. Cool white type, thin clock.
            t.name = "Star Trail";
            t.background = 0xFF05070C;
            t.foreground = 0xFFEDF1F7;
            t.mid = 0xB4B4BECC;
            t.dim = 0x7C7C8797;
            t.accent = 0xFFAFC4DE;
            t.clockFace = "thin";
            t.clockLayout = "classic";
            t.clockSpacing = 0.02f;
            t.iconShape = "none";
            t.iconScale = 1f;
            t.iconDesaturate = false;
            t.iconTintStrength = 0f;
        }
        return t;
    }

    static List<String> builtinIds() {
        List<String> out = new ArrayList<String>();
        out.add("star-trail");
        out.add("slate");
        out.add("paper");
        out.add("mono");
        return out;
    }

    // ------------------------------------------------------------------ storage

    static File themesDir(Context c) {
        File dir = new File(c.getFilesDir(), "themes");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static List<Entry> available(Context c) {
        List<Entry> out = new ArrayList<Entry>();
        List<String> ids = builtinIds();
        for (int i = 0; i < ids.size(); i++) {
            Theme t = builtin(ids.get(i));
            out.add(new Entry(ids.get(i), t.name, true));
        }

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

    /** The theme in use. Falls back to the built-in look on a corrupt or absent draft. */
    static Theme current(Context c) {
        String raw = Prefs.sp(c).getString(Prefs.K_THEME_JSON, "");
        if (raw != null && !raw.isEmpty()) {
            Theme t = fromJson(raw);
            if (t != null) return t;
        }
        return builtin(BUILTIN);
    }

    /** Writes the draft straight into prefs. Used by the editor on every change. */
    static void store(Context c, Theme t) {
        Prefs.sp(c).edit().putString(Prefs.K_THEME_JSON, t.toJson().toString()).commit();
    }

    /** Applies a built-in or preset: colours, fonts, and which elements are visible. */
    static void apply(Context c, String id) {
        Theme t;
        if (builtinIds().contains(id)) {
            t = builtin(id);
        } else {
            t = fromDir(new File(themesDir(c), id));
            if (t == null) t = builtin(BUILTIN);
        }
        store(c, t);
        applyFonts(c, t);
        Typefaces.invalidate();
        syncVisibility(c, t);
        Prefs.bumpLibrary(c);
    }

    /** Pushes the theme's element switches into the prefs the home view reads. */
    static void syncVisibility(Context c, Theme t) {
        Prefs.sp(c).edit()
                .putBoolean("date", t.showDate)
                .putBoolean("weather", t.showWeather)
                .putBoolean(Prefs.K_LABELS, t.showLabels)
                .putBoolean(Prefs.K_WAVE, t.showRail)
                .apply();
    }

    /** Reads the element switches back off the prefs into a draft. */
    static void pullVisibility(Context c, Theme t) {
        SharedPreferences p = Prefs.sp(c);
        t.showDate = p.getBoolean("date", t.showDate);
        t.showWeather = p.getBoolean("weather", t.showWeather);
        t.showLabels = p.getBoolean(Prefs.K_LABELS, t.showLabels);
        t.showRail = p.getBoolean(Prefs.K_WAVE, t.showRail);
    }

    /** Saves the draft as a preset folder and makes it the active theme. */
    static Theme save(Context c, Theme t, String presetName) {
        Theme out = t.copy();
        out.name = presetName;
        out.id = slug(presetName);
        File target = new File(themesDir(c), out.id);
        deleteTree(target);
        if (!target.mkdirs()) return null;
        out.dir = target;
        if (!write(new File(target, "theme.json"), out.toJson().toString())) return null;
        store(c, out);
        syncVisibility(c, out);
        Prefs.bumpLibrary(c);
        return out;
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
        return faceTypeface(current(c).clockFace);
    }

    static Typeface faceTypeface(String face) {
        if ("serif".equals(face)) return Typeface.create("serif", Typeface.NORMAL);
        if ("serif-light".equals(face)) return Typeface.create("serif", Typeface.NORMAL);
        if ("mono".equals(face)) return Typeface.create("monospace", Typeface.NORMAL);
        if ("bold".equals(face)) return Typeface.create("sans-serif", Typeface.BOLD);
        if ("medium".equals(face)) return Typeface.create("sans-serif-medium", Typeface.NORMAL);
        if ("regular".equals(face)) return Typeface.create("sans-serif", Typeface.NORMAL);
        if ("light".equals(face)) return Typeface.create("sans-serif-light", Typeface.NORMAL);
        if ("condensed".equals(face)) return Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        return Typeface.create("sans-serif-thin", Typeface.NORMAL);
    }

    // ------------------------------------------------------------------ json

    JSONObject toJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("name", name);
            root.put("author", author);
            root.put("light", light);

            JSONObject colors = new JSONObject();
            colors.put("background", hex(background));
            colors.put("foreground", hex(foreground));
            colors.put("mid", hex(mid));
            colors.put("dim", hex(dim));
            colors.put("accent", hex(accent));
            root.put("colors", colors);

            JSONObject clock = new JSONObject();
            clock.put("face", clockFace);
            clock.put("style", clockFace); // older files used "style"
            clock.put("layout", clockLayout);
            clock.put("align", clockAlign);
            clock.put("scale", round(clockScale));
            clock.put("spacing", round(clockSpacing));
            root.put("clock", clock);

            JSONObject icons = new JSONObject();
            icons.put("pack", iconPack);
            icons.put("shape", iconShape);
            icons.put("scale", round(iconScale));
            icons.put("background", hex(iconBg));
            icons.put("desaturate", iconDesaturate);
            icons.put("tint", hex(iconTint));
            icons.put("tintStrength", round(iconTintStrength));
            root.put("icons", icons);

            JSONObject show = new JSONObject();
            show.put("date", showDate);
            show.put("weather", showWeather);
            show.put("labels", showLabels);
            show.put("rail", showRail);
            root.put("show", show);
        } catch (Exception ignored) {
            // JSONObject.put only throws on non-finite doubles; nothing here can.
        }
        return root;
    }

    private static double round(float v) { return Math.round(v * 1000d) / 1000d; }

    static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    private static Theme fromJson(String raw) {
        try {
            return parse(new JSONObject(raw));
        } catch (Exception ex) {
            Log.w(TAG, "bad theme json", ex);
            return null;
        }
    }

    private static Theme fromDir(File dir) {
        File json = new File(dir, "theme.json");
        if (!json.isFile()) return null;
        String raw = readText(json, 64 * 1024);
        if (raw == null) return null;
        Theme t = fromJson(raw);
        if (t == null) return null;
        t.id = dir.getName();
        t.dir = dir;
        return t;
    }

    private static Theme parse(JSONObject root) throws Exception {
        Theme t = new Theme();
        t.name = root.optString("name", "Untitled");
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
            t.clockFace = clock.optString("face", clock.optString("style", t.clockFace));
            t.clockLayout = clock.optString("layout", t.clockLayout);
            t.clockAlign = clock.optString("align", t.clockAlign);
            t.clockScale = clamp((float) clock.optDouble("scale", t.clockScale), 0.7f, 1.4f);
            t.clockSpacing = clamp((float) clock.optDouble("spacing", t.clockSpacing),
                    -0.05f, 0.3f);
        }

        JSONObject icons = root.optJSONObject("icons");
        if (icons != null) {
            t.iconPack = icons.optString("pack", t.iconPack);
            t.iconShape = icons.optString("shape", t.iconShape);
            t.iconScale = clamp((float) icons.optDouble("scale", t.iconScale), 0.5f, 1f);
            t.iconBg = colour(icons, "background", t.iconBg);
            t.iconDesaturate = icons.optBoolean("desaturate", t.iconDesaturate);
            t.iconTint = colour(icons, "tint", t.iconTint);
            t.iconTintStrength = clamp(
                    (float) icons.optDouble("tintStrength", t.iconTintStrength), 0f, 1f);
        }

        JSONObject show = root.optJSONObject("show");
        if (show != null) {
            t.showDate = show.optBoolean("date", t.showDate);
            t.showWeather = show.optBoolean("weather", t.showWeather);
            t.showLabels = show.optBoolean("labels", t.showLabels);
            t.showRail = show.optBoolean("rail", t.showRail);
        }
        return t;
    }

    private static int colour(JSONObject o, String key, int fallback) {
        String v = o.optString(key, null);
        if (v == null) return fallback;
        try {
            String body = v.replace("#", "");
            long parsed = Long.parseLong(body, 16);
            if (body.length() <= 6) parsed |= 0xFF000000L;
            return (int) parsed;
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

    /** Writes the active theme out as a zip: theme.json plus any preset icons and fonts. */
    static boolean exportZip(Context c, Theme t, File target) {
        FileOutputStream fos = null;
        java.util.zip.ZipOutputStream zos = null;
        try {
            fos = new FileOutputStream(target);
            zos = new java.util.zip.ZipOutputStream(fos);
            zos.putNextEntry(new ZipEntry("theme.json"));
            zos.write(t.toJson().toString(2).getBytes("UTF-8"));
            zos.closeEntry();

            if (t.dir != null) {
                addTree(zos, t.dir, "", "theme.json");
            }
            zos.finish();
            return true;
        } catch (Exception ex) {
            Log.w(TAG, "export failed", ex);
            return false;
        } finally {
            try { if (zos != null) zos.close(); } catch (Exception ignored) { }
            try { if (fos != null) fos.close(); } catch (Exception ignored) { }
        }
    }

    private static void addTree(java.util.zip.ZipOutputStream zos, File dir, String prefix,
                                String skip) throws Exception {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (int i = 0; i < kids.length; i++) {
            String path = prefix + kids[i].getName();
            if (path.equals(skip)) continue;
            if (kids[i].isDirectory()) {
                addTree(zos, kids[i], path + "/", skip);
                continue;
            }
            zos.putNextEntry(new ZipEntry(path));
            InputStream in = new java.io.FileInputStream(kids[i]);
            try {
                byte[] buf = new byte[16 * 1024];
                int read;
                while ((read = in.read(buf)) > 0) zos.write(buf, 0, read);
            } finally {
                in.close();
            }
            zos.closeEntry();
        }
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

    private static boolean write(File f, String text) {
        OutputStream out = null;
        try {
            out = new FileOutputStream(f);
            out.write(text.getBytes("UTF-8"));
            return true;
        } catch (Exception ex) {
            return false;
        } finally {
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
