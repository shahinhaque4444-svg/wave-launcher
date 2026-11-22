package com.bench.wave;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An installed icon pack: the ADW/Go format every launcher on Play Store ships.
 *
 *   res/xml/appfilter.xml   &lt;item component="ComponentInfo{pkg/cls}" drawable="name"/&gt;
 *                           &lt;iconback drawable="..."/&gt; &lt;iconmask .../&gt; &lt;scale factor="0.9"/&gt;
 *
 * The file can be several thousand entries, so it is parsed once and held as two maps:
 * exact component, then package-wide fallback. Nothing is loaded eagerly; a drawable is
 * resolved when a row actually asks for it.
 */
final class IconPack {

    private static final String TAG = "WaveIconPack";
    private static final int MAX_ITEMS = 20000;

    final String pkg;
    final String label;

    private final Resources res;
    private final Map<String, String> byComponent;
    private final Map<String, String> byPackage;
    private final List<String> backs;
    private final String mask;
    private final String upon;
    final float scale;

    private IconPack(String pkg, String label, Resources res,
                     Map<String, String> byComponent, Map<String, String> byPackage,
                     List<String> backs, String mask, String upon, float scale) {
        this.pkg = pkg;
        this.label = label;
        this.res = res;
        this.byComponent = byComponent;
        this.byPackage = byPackage;
        this.backs = backs;
        this.mask = mask;
        this.upon = upon;
        this.scale = scale;
    }

    /** Every installed pack, alphabetically. Empty when the device has none. */
    static List<IconPack> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, ResolveInfo> found = new HashMap<String, ResolveInfo>();

        String[] actions = {"org.adw.launcher.THEMES", "com.gau.go.launcherex.theme",
                "com.novalauncher.THEME"};
        for (int a = 0; a < actions.length; a++) {
            Intent i = new Intent(actions[a]);
            i.addCategory(Intent.CATEGORY_DEFAULT);
            List<ResolveInfo> list;
            try {
                list = pm.queryIntentActivities(i, 0);
            } catch (Throwable t) {
                continue;
            }
            if (list == null) continue;
            for (int k = 0; k < list.size(); k++) {
                ResolveInfo ri = list.get(k);
                if (ri.activityInfo == null) continue;
                String p = ri.activityInfo.packageName;
                if (!found.containsKey(p)) found.put(p, ri);
            }
        }

        List<IconPack> out = new ArrayList<IconPack>();
        for (Map.Entry<String, ResolveInfo> e : found.entrySet()) {
            IconPack pack = load(c, pm, e.getKey());
            if (pack != null) out.add(pack);
        }
        Collections.sort(out, new java.util.Comparator<IconPack>() {
            @Override public int compare(IconPack a, IconPack b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    static IconPack load(Context c, String pkg) {
        return load(c, c.getPackageManager(), pkg);
    }

    private static IconPack load(Context c, PackageManager pm, String pkg) {
        Resources res;
        String label;
        try {
            res = pm.getResourcesForApplication(pkg);
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            label = String.valueOf(pm.getApplicationLabel(ai));
        } catch (Throwable t) {
            return null;
        }

        Map<String, String> byComponent = new HashMap<String, String>();
        Map<String, String> byPackage = new HashMap<String, String>();
        List<String> backs = new ArrayList<String>();
        String mask = null;
        String upon = null;
        float scale = 1f;

        try {
            int id = res.getIdentifier("appfilter", "xml", pkg);
            if (id != 0) {
                XmlResourceParser xp = res.getXml(id);
                int items = 0;
                int event;
                while ((event = xp.next()) != XmlPullParser.END_DOCUMENT) {
                    if (event != XmlPullParser.START_TAG) continue;
                    String tag = xp.getName();
                    if ("item".equals(tag)) {
                        if (++items > MAX_ITEMS) break;
                        String comp = attr(xp, "component");
                        String drawable = attr(xp, "drawable");
                        if (comp == null || drawable == null) continue;
                        String key = componentKey(comp);
                        if (key == null) continue;
                        byComponent.put(key, drawable);
                        int slash = key.indexOf('/');
                        if (slash > 0) {
                            String p = key.substring(0, slash);
                            if (!byPackage.containsKey(p)) byPackage.put(p, drawable);
                        }
                    } else if ("iconback".equals(tag)) {
                        String d = firstAttr(xp, "img", "drawable");
                        if (d != null) backs.add(d);
                    } else if ("iconmask".equals(tag)) {
                        mask = firstAttr(xp, "img", "drawable");
                    } else if ("iconupon".equals(tag)) {
                        upon = firstAttr(xp, "img", "drawable");
                    } else if ("scale".equals(tag)) {
                        String f = attr(xp, "factor");
                        if (f != null) {
                            try {
                                float v = Float.parseFloat(f);
                                if (v > 0.3f && v <= 2f) scale = v;
                            } catch (NumberFormatException ignored) { }
                        }
                    }
                }
                xp.close();
            }
        } catch (Throwable t) {
            Log.w(TAG, "cannot read appfilter from " + pkg, t);
        }

        // A pack with no appfilter at all is not usable as a pack.
        if (byComponent.isEmpty() && backs.isEmpty()) return null;
        return new IconPack(pkg, label, res, byComponent, byPackage, backs, mask, upon, scale);
    }

    private static String firstAttr(XmlResourceParser xp, String a, String b) {
        String v = attr(xp, a);
        return v != null ? v : attr(xp, b);
    }

    private static String attr(XmlResourceParser xp, String name) {
        for (int i = 0; i < xp.getAttributeCount(); i++) {
            String n = xp.getAttributeName(i);
            if (n != null && n.endsWith(name)) return xp.getAttributeValue(i);
        }
        return xp.getAttributeValue(null, name);
    }

    /** "ComponentInfo{com.foo/com.foo.Main}" and "com.foo/com.foo.Main" both parse. */
    private static String componentKey(String raw) {
        String s = raw.trim();
        if (s.startsWith("ComponentInfo{")) s = s.substring(14);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);
        int slash = s.indexOf('/');
        if (slash <= 0 || slash == s.length() - 1) return null;
        return s;
    }

    int size() { return byComponent.size(); }

    boolean hasIconFor(String pkgName, String cls) {
        return byComponent.containsKey(pkgName + "/" + cls) || byPackage.containsKey(pkgName);
    }

    /** The pack's drawable for one app, or null when the pack has nothing for it. */
    Drawable iconFor(String pkgName, String cls) {
        String name = byComponent.get(pkgName + "/" + cls);
        if (name == null) name = byPackage.get(pkgName);
        if (name == null) return null;
        return drawable(name);
    }

    /** A backing plate for apps the pack does not cover, or null. */
    Drawable back() {
        if (backs.isEmpty()) return null;
        return drawable(backs.get(0));
    }

    Drawable mask() { return mask == null ? null : drawable(mask); }

    Drawable upon() { return upon == null ? null : drawable(upon); }

    private Drawable drawable(String name) {
        try {
            int id = res.getIdentifier(name, "drawable", pkg);
            if (id == 0) return null;
            return res.getDrawable(id, null);
        } catch (Throwable t) {
            return null;
        }
    }
}
