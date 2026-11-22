package com.bench.wave;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Icons are pulled off the main thread, flattened to a fixed-size bitmap and kept in an
 * LRU so a 300-app list never holds 300 drawables.
 */
final class IconLoader {
    interface Callback {
        void onIconReady();
    }

    private static final int CACHE_KB = 8 * 1024;

    private final Context app;
    private final PackageManager pm;
    private final int sizePx;
    private final LruCache<String, Bitmap> cache;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> pending = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> failed = Collections.synchronizedSet(new HashSet<String>());
    private volatile Theme theme;

    IconLoader(Context ctx, int sizePx) {
        this.app = ctx.getApplicationContext();
        this.pm = ctx.getPackageManager();
        this.sizePx = sizePx;
        this.theme = Theme.current(ctx);
        this.cache = new LruCache<String, Bitmap>(CACHE_KB) {
            @Override protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    /** Returns a cached bitmap, or null and schedules the load. */
    Bitmap get(final AppEntry e, final Callback cb) {
        Bitmap cached = cache.get(e.key);
        if (cached != null) return cached;
        if (failed.contains(e.key)) return null;
        if (!pending.add(e.key)) return null;

        pool.execute(new Runnable() {
            @Override public void run() {
                Bitmap out = render(e);
                pending.remove(e.key);
                if (out == null) {
                    // Package gone or icon unreadable: stop retrying on every frame.
                    failed.add(e.key);
                    return;
                }
                final Bitmap bmp = out;
                main.post(new Runnable() {
                    @Override public void run() {
                        cache.put(e.key, bmp);
                        if (cb != null) cb.onIconReady();
                    }
                });
            }
        });
        return null;
    }

    void evict(String key) {
        cache.remove(key);
        failed.remove(key);
    }

    /** Drops everything; used after an icon override changes. */
    void clear() {
        cache.evictAll();
        failed.clear();
        pending.clear();
    }

    /** Swaps the look without touching the list: used when a theme is applied. */
    void setTheme(Theme t) {
        if (t == null) return;
        this.theme = t;
        clear();
    }

    private Bitmap render(AppEntry e) {
        Bitmap custom = custom(e);
        if (custom != null) return custom;

        Theme t = theme;
        if (t != null) {
            java.io.File themed = t.icon(e.pkg);
            if (themed != null) {
                Bitmap bmp = BitmapFactory.decodeFile(themed.getAbsolutePath());
                if (bmp != null) return treated(bmp, t);
            }
        }

        try {
            Drawable d = pm.getActivityIcon(e.component);
            if (d == null) return null;
            Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, sizePx, sizePx);
            d.draw(c);
            return treated(bmp, t);
        } catch (Throwable ex) {
            return null;
        }
    }

    /**
     * Pulls an icon toward the theme: saturation off, then a thin tint wash so the wall of
     * app icons reads as one set. Both are no-ops when the theme asks for neither.
     */
    private Bitmap treated(Bitmap src, Theme t) {
        if (t == null) return src;
        boolean desaturate = t.iconDesaturate;
        float tintAlpha = t.iconTintStrength;
        if (!desaturate && tintAlpha <= 0f) return src;

        Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        if (desaturate) p.setColorFilter(new ColorMatrixColorFilter(saturation(0f)));
        c.drawBitmap(src, null, new android.graphics.RectF(0, 0, sizePx, sizePx), p);
        if (src != out) src.recycle();

        if (tintAlpha > 0f) {
            Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
            wash.setColor(t.iconTint);
            wash.setAlpha(Math.max(0, Math.min(255, Math.round(tintAlpha * 255f))));
            wash.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            c.drawRect(0, 0, sizePx, sizePx, wash);
        }
        return out;
    }

    private static ColorMatrix saturation(float value) {
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(value);
        return cm;
    }

    /** A user-picked image, letterboxed into the icon square. */
    private Bitmap custom(AppEntry e) {
        String path = Prefs.iconOverride(app, e.key);
        if (path == null) return null;
        Bitmap src = BitmapFactory.decodeFile(path);
        if (src == null) return null;
        Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float scale = Math.min(sizePx / (float) src.getWidth(), sizePx / (float) src.getHeight());
        float w = src.getWidth() * scale;
        float h = src.getHeight() * scale;
        c.drawBitmap(src, null, new android.graphics.RectF((sizePx - w) / 2f, (sizePx - h) / 2f,
                (sizePx + w) / 2f, (sizePx + h) / 2f), new android.graphics.Paint(
                android.graphics.Paint.FILTER_BITMAP_FLAG));
        src.recycle();
        return out;
    }
}
