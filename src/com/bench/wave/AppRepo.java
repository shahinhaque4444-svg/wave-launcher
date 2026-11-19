package com.bench.wave;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the installed-app list, favorites order and hidden set.
 *
 * The lists are published as fresh immutable snapshots rather than mutated in place, so the
 * drawing thread can render while a background reload is in flight without seeing a half-built
 * list.
 */
final class AppRepo {
    private final Context ctx;
    private volatile List<AppEntry> apps = Collections.emptyList();
    private volatile List<AppEntry> favorites = Collections.emptyList();
    private volatile Map<String, AppEntry> byKey = Collections.emptyMap();
    private volatile boolean loaded;
    private volatile Map<String, Long> usage = Collections.emptyMap();

    AppRepo(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    List<AppEntry> apps() { return apps; }
    List<AppEntry> favorites() { return favorites; }
    boolean isLoaded() { return loaded; }

    AppEntry byKey(String key) { return byKey.get(key); }

    boolean isFavorite(AppEntry e) {
        return e != null && contains(favorites, e);
    }

    private static boolean contains(List<AppEntry> list, AppEntry e) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).key.equals(e.key)) return true;
        }
        return false;
    }

    void load() {
        PackageManager pm = ctx.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved;
        try {
            resolved = pm.queryIntentActivities(intent, 0);
        } catch (Throwable t) {
            resolved = new ArrayList<ResolveInfo>();
        }

        Set<String> hidden = new HashSet<String>();
        Collections.addAll(hidden, Prefs.list(ctx, Prefs.K_HIDDEN));

        List<AppEntry> fresh = new ArrayList<AppEntry>();
        Set<String> seen = new HashSet<String>();
        String own = ctx.getPackageName();

        for (int i = 0; i < resolved.size(); i++) {
            ResolveInfo ri = resolved.get(i);
            if (ri == null || ri.activityInfo == null) continue;
            if (!ri.activityInfo.enabled) continue;
            if (own.equals(ri.activityInfo.packageName)) continue;

            String label;
            try {
                CharSequence cs = ri.loadLabel(pm);
                label = cs == null ? ri.activityInfo.packageName : cs.toString().trim();
            } catch (Throwable t) {
                label = ri.activityInfo.packageName;
            }
            if (label.isEmpty()) label = ri.activityInfo.packageName;

            AppEntry e = new AppEntry(label,
                    new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
            if (!seen.add(e.key)) continue;
            if (hidden.contains(e.key)) continue;
            fresh.add(e);
        }

        final Map<String, Long> recent = Prefs.usageMap(ctx);
        this.usage = recent;
        Collections.sort(fresh, order(recent));

        Map<String, AppEntry> index = new HashMap<String, AppEntry>();
        for (int i = 0; i < fresh.size(); i++) index.put(fresh.get(i).key, fresh.get(i));

        List<AppEntry> favs = buildFavorites(index);

        this.byKey = index;
        this.apps = Collections.unmodifiableList(fresh);
        this.favorites = Collections.unmodifiableList(favs);
        this.loaded = true;
    }

    /** Section first, then whichever app in that section was opened most recently. */
    private Comparator<AppEntry> order(final Map<String, Long> recent) {
        final Collator collator = Collator.getInstance();
        return new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) {
                boolean aHash = "#".equals(a.section);
                boolean bHash = "#".equals(b.section);
                if (aHash != bHash) return aHash ? 1 : -1;
                int bySection = a.section.compareTo(b.section);
                if (bySection != 0) return bySection;

                Long au = recent.get(a.key);
                Long bu = recent.get(b.key);
                long at = au == null ? 0L : au.longValue();
                long bt = bu == null ? 0L : bu.longValue();
                if (at != bt) return at > bt ? -1 : 1;

                int byLabel = collator.compare(a.label, b.label);
                if (byLabel != 0) return byLabel;
                return a.pkg.compareTo(b.pkg);
            }
        };
    }

    /**
     * Re-sorts the list it already holds against the launch log. No package-manager query,
     * so it is cheap enough to run every time the launcher comes forward.
     */
    void reorder() {
        if (!loaded) return;
        Map<String, Long> recent = Prefs.usageMap(ctx);
        this.usage = recent;
        List<AppEntry> sorted = new ArrayList<AppEntry>(byKey.values());
        Collections.sort(sorted, order(recent));
        Map<String, AppEntry> index = new HashMap<String, AppEntry>();
        for (int i = 0; i < sorted.size(); i++) index.put(sorted.get(i).key, sorted.get(i));
        this.byKey = index;
        this.apps = Collections.unmodifiableList(sorted);
        this.favorites = Collections.unmodifiableList(buildFavorites(index));
    }

    private List<AppEntry> buildFavorites(Map<String, AppEntry> index) {
        List<AppEntry> favs = new ArrayList<AppEntry>();
        String[] keys = Prefs.list(ctx, Prefs.K_FAVORITES);
        for (int i = 0; i < keys.length; i++) {
            AppEntry e = index.get(keys[i]);
            if (e != null && !contains(favs, e)) favs.add(e);
        }
        return favs;
    }

    void toggleFavorite(AppEntry e) {
        if (e == null) return;
        LinkedHashSet<String> keys = new LinkedHashSet<String>();
        Collections.addAll(keys, Prefs.list(ctx, Prefs.K_FAVORITES));

        boolean wasFavorite = false;
        for (int i = 0; i < favorites.size(); i++) {
            if (favorites.get(i).key.equals(e.key)) {
                wasFavorite = true;
                break;
            }
        }
        if (wasFavorite) keys.remove(e.key);
        else keys.add(e.key);

        Prefs.putList(ctx, Prefs.K_FAVORITES, keys);
        reloadFavorites();
    }

    void hide(AppEntry e) {
        if (e == null) return;
        LinkedHashSet<String> keys = new LinkedHashSet<String>();
        Collections.addAll(keys, Prefs.list(ctx, Prefs.K_HIDDEN));
        keys.add(e.key);
        Prefs.putList(ctx, Prefs.K_HIDDEN, keys);

        LinkedHashSet<String> favs = new LinkedHashSet<String>();
        Collections.addAll(favs, Prefs.list(ctx, Prefs.K_FAVORITES));
        favs.remove(e.key);
        Prefs.putList(ctx, Prefs.K_FAVORITES, favs);

        load();
    }

    void resetFavorites() {
        Prefs.sp(ctx).edit().putString(Prefs.K_FAVORITES, "").apply();
        this.favorites = Collections.emptyList();
    }

    void resetHidden() {
        Prefs.sp(ctx).edit().putString(Prefs.K_HIDDEN, "").apply();
        load();
    }

    /** Re-reads the favorites order without re-querying the package manager. */
    void reloadFavorites() {
        this.favorites = Collections.unmodifiableList(buildFavorites(byKey));
    }
}
