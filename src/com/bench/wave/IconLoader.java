package com.bench.wave;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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
    private volatile IconPack pack;
    private volatile String packPkg = "";

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
        this.pack = null;
        this.packPkg = "";
        clear();
    }

    /** The theme's icon pack, parsed once and kept. Null when it is unset or unreadable. */
    private IconPack pack() {
        Theme t = theme;
        if (t == null || t.iconPack == null || t.iconPack.isEmpty()) return null;
        if (t.iconPack.equals(packPkg)) return pack;
        IconPack loaded = IconPack.load(app, t.iconPack);
        packPkg = t.iconPack;
        pack = loaded;
        return loaded;
    }

    private Bitmap render(AppEntry e) {
        Bitmap custom = custom(e);
        if (custom != null) return custom;

        Theme t = theme;

        // 1. A single icon shipped in the theme folder wins over everything.
        if (t != null) {
            java.io.File themed = t.icon(e.pkg);
            if (themed != null) {
                Bitmap bmp = BitmapFactory.decodeFile(themed.getAbsolutePath());
                if (bmp != null) return finish(bmp, t);
            }
        }

        // 2. The chosen icon pack, then a backing plate for apps it does not cover.
        IconPack p = pack();
        if (p != null) {
            Drawable packIcon = null;
            try {
                packIcon = p.iconFor(e.pkg, e.cls);
            } catch (Throwable ignored) { }
            if (packIcon != null) return finish(raster(packIcon, p.scale), t);

            Drawable back = p.back();
            if (back != null) {
                Bitmap plate = raster(back, 1f);
                Drawable own = null;
                try {
                    own = pm.getActivityIcon(e.component);
                } catch (Throwable ignored) { }
                if (own != null) plate = compose(plate, raster(own, p.scale * 0.62f));
                return finish(plate, t);
            }
        }

        // 3. The app's own icon.
        try {
            Drawable d = pm.getActivityIcon(e.component);
            if (d == null) return null;
            return finish(raster(d, 1f), t);
        } catch (Throwable ex) {
            return null;
        }
    }

    /** Draws a drawable into a square bitmap at the icon size. */
    private Bitmap raster(Drawable d, float scale) {
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float s = Math.max(0.1f, scale);
        int inset = Math.round((sizePx - sizePx * s) / 2f);
        d.setBounds(inset, inset, sizePx - inset, sizePx - inset);
        d.draw(c);
        return bmp;
    }

    /** Glyph centred on a plate, used for apps the pack does not name. */
    private Bitmap compose(Bitmap plate, Bitmap glyph) {
        Canvas c = new Canvas(plate);
        c.drawBitmap(glyph, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        glyph.recycle();
        return plate;
    }

    /**
     * Shape, then colour. The mask clips the glyph to the chosen silhouette and fills the
     * plate behind it; tint and desaturation are applied to the result so a washed-out
     * theme still reads as one set.
     */
    private Bitmap finish(Bitmap src, Theme t) {
        if (t == null) return src;
        Bitmap shaped = shaped(src, t);
        if (shaped != src) src.recycle();

        boolean desaturate = t.iconDesaturate;
        float tintAlpha = t.iconTintStrength;
        if (!desaturate && tintAlpha <= 0f) return shaped;

        Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        if (desaturate) p.setColorFilter(new ColorMatrixColorFilter(saturation(0f)));
        c.drawBitmap(shaped, 0, 0, p);
        shaped.recycle();

        if (tintAlpha > 0f) {
            Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
            wash.setColor(t.iconTint);
            wash.setAlpha(Math.max(0, Math.min(255, Math.round(tintAlpha * 255f))));
            wash.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            c.drawRect(0, 0, sizePx, sizePx, wash);
        }
        return out;
    }

    private Bitmap shaped(Bitmap src, Theme t) {
        String shape = t.iconShape == null ? "none" : t.iconShape;
        boolean mask = !"none".equals(shape);
        float inner = Math.max(0.5f, Math.min(1f, t.iconScale));
        if (!mask && inner >= 0.999f) return src;

        Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Path clip = mask ? shapePath(shape) : null;

        if (mask) {
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(t.iconBg);
            c.drawPath(clip, bg);
            c.save();
            c.clipPath(clip);
        }

        float box = sizePx * inner;
        float inset = (sizePx - box) / 2f;
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        c.drawBitmap(src, null, new RectF(inset, inset, inset + box, inset + box), p);

        if (mask) c.restore();

        if ("ring".equals(shape)) {
            Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(Math.max(2f, sizePx * 0.045f));
            edge.setColor(t.foreground);
            edge.setAlpha(70);
            float r = sizePx / 2f - edge.getStrokeWidth() / 2f;
            c.drawCircle(sizePx / 2f, sizePx / 2f, r, edge);
        }
        return out;
    }

    /** Silhouette for the mask, sized to the icon square. */
    private Path shapePath(String shape) {
        Path path = new Path();
        float h = sizePx / 2f;
        if ("circle".equals(shape) || "ring".equals(shape)) {
            path.addCircle(h, h, h, Path.Direction.CW);
        } else if ("rounded".equals(shape)) {
            float r = sizePx * 0.24f;
            path.addRoundRect(new RectF(0, 0, sizePx, sizePx), r, r, Path.Direction.CW);
        } else if ("squircle".equals(shape)) {
            // Superellipse |x|^4 + |y|^4 = 1, sampled. Reads as an iOS tile, not a circle.
            float n = 4f;
            int steps = 96;
            for (int i = 0; i < steps; i++) {
                double a = 2 * Math.PI * i / steps;
                double cos = Math.cos(a);
                double sin = Math.sin(a);
                float x = (float) (Math.signum(cos) * Math.pow(Math.abs(cos), 2 / n)) * h + h;
                float y = (float) (Math.signum(sin) * Math.pow(Math.abs(sin), 2 / n)) * h + h;
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            path.close();
        } else {
            path.addRect(0, 0, sizePx, sizePx, Path.Direction.CW);
        }
        return path;
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
