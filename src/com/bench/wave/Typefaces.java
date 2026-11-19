package com.bench.wave;

import android.content.Context;
import android.graphics.Typeface;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Typefaces by role. The bundled Roboto is the floor: OEM theme engines replace the
 * system "sans-serif" family, which once dragged every string into a handwriting face.
 * The clock, the app names and the interface can each be pointed at a font file the
 * user picked; anything unset falls back to the bundled face.
 */
final class Typefaces {

    private static Typeface bundled;
    private static final Map<String, Typeface> PICKED = new HashMap<String, Typeface>();

    private Typefaces() {}

    static synchronized Typeface regular(Context c) {
        if (bundled == null) {
            try {
                bundled = c.getApplicationContext().getResources().getFont(R.font.roboto);
            } catch (RuntimeException e) {
                bundled = Typeface.create("sans-serif", Typeface.NORMAL);
            }
        }
        return bundled;
    }

    /** Font for one role, or the bundled face when the slot is empty. */
    static synchronized Typeface forRole(Context c, String key) {
        String path = Prefs.sp(c).getString(key, "");
        if (path == null || path.isEmpty()) return regular(c);
        Typeface face = PICKED.get(path);
        if (face == null) {
            try {
                face = Typeface.createFromFile(new File(path));
            } catch (RuntimeException e) {
                face = null;
            }
            if (face == null) return regular(c);
            PICKED.put(path, face);
        }
        return face;
    }

    static Typeface clock(Context c) { return forRole(c, Prefs.K_FONT_CLOCK); }
    static Typeface apps(Context c) { return forRole(c, Prefs.K_FONT_APPS); }
    static Typeface ui(Context c) { return forRole(c, Prefs.K_FONT_UI); }

    static Typeface medium(Context c, Typeface base) {
        return Typeface.create(base, Typeface.BOLD);
    }

    /** Call after a font file changes on disk. */
    static synchronized void invalidate() {
        PICKED.clear();
    }

    /** Applies the interface face to a view tree. */
    static void apply(Context c, android.view.View root) {
        if (root instanceof android.widget.TextView) {
            ((android.widget.TextView) root).setTypeface(ui(c));
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) apply(c, g.getChildAt(i));
        }
    }
}
