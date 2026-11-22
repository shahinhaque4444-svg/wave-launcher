package com.bench.wave;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Two screens in one canvas, the way the reference launcher does it:
 *
 *   HOME  clock, date, favorites column
 *   APPS  alphabetical list with section letters
 *
 * The wave on the right edge is the only door between them: a letter jumps into the app
 * list at that section, the star goes back home, the circle jumps to the tail of the list.
 * Back from the app list returns to favorites.
 */
final class HomeView extends View {

    interface Host {
        void launch(AppEntry e);
        void openSearch();
        void openAppMenu(AppEntry e);
        void openSettings();
        void lockScreen();
        void openNotifications();
    }

    private static final int MODE_HOME = 0;
    private static final int MODE_APPS = 1;

    private final AppRepo repo;
    private final IconLoader icons;
    private final Host host;

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint glyphStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final RectF fabRect = new RectF();
    private final OverScroller scroller;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> sections = new ArrayList<String>();
    private final List<Float> sectionStarts = new ArrayList<Float>();
    private final List<Integer> sectionCounts = new ArrayList<Integer>();
    private final List<AppEntry> favorites = new ArrayList<AppEntry>();

    private final IconLoader.Callback iconCallback = new IconLoader.Callback() {
        @Override public void onIconReady() { invalidate(); }
    };

    private Typeface tfClock, tfRegular, tfMedium;
    private Theme theme;
    private float clockScale = 1f;
    private float clockSpacing = 0.02f;
    private float weatherBaseline;
    private String weatherText = "";
    private boolean weatherOn;

    private boolean dark = true;
    private boolean searchMode;
    private int cFg, cFgMid, cFgDim, cLine, cFabFg;
    private float scrimBase;

    private int statusH, navH;
    private float leftMargin, labelX, headerX, iconSize, rowPitch, sectionH;
    private float clockBaseline, dateBaseline, favTop;
    private float railTop, railBottom, railX, fabRadius, fabCx, fabCy;
    private boolean clockOn, iconsOn, labelsOn, waveOn, waveLeft, dateOn;
    private String clockLayout = "classic";
    private String clockAlign = "left";
    private float contentLeft, contentRight;

    private int mode = MODE_HOME;
    private float transition;             // 0 = home, 1 = app list
    private ValueAnimator transitionAnim;
    private boolean switching;

    private float scrollHome, scrollApps;

    private float downX, downY, lastY, dragDist, upY;
    private boolean dragging, scrubbing, longPressFired;
    private int pressRow = -1, pressFav = -1, pressSection = -1;
    private int touchSlop;
    private VelocityTracker velocityTracker;
    private int lastScrub = Integer.MIN_VALUE;
    private float scrubY = -1f;   // finger position on the rail, -1 when not scrubbing
    private long lastLetterTap;
    private int lastLetterIndex = -1;

    private String clockText = "";
    private String dateText = "";
    private int lastClockMinute = -1;
    private final SimpleDateFormat timeFormat;
    private final SimpleDateFormat dateFormat;

    private boolean dirty = true;

    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (dragging || scrubbing) return;
            longPressFired = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (pressFav >= 0 && pressFav < favorites.size()) {
                host.openAppMenu(favorites.get(pressFav));
            } else if (pressRow >= 0 && pressRow < repo.apps().size()) {
                host.openAppMenu(repo.apps().get(pressRow));
            } else if (mode == MODE_HOME) {
                host.openSettings();
            }
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateClock();
            invalidate();
            handler.postDelayed(this, 20000);
        }
    };

    HomeView(Context ctx, AppRepo repo, Host host) {
        super(ctx);
        this.repo = repo;
        this.host = host;
        this.icons = new IconLoader(ctx, (int) dp(96));
        this.scroller = new OverScroller(ctx);
        this.touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        boolean h24 = DateFormat.is24HourFormat(ctx);
        this.timeFormat = new SimpleDateFormat(h24 ? "H:mm" : "h:mm", Locale.getDefault());
        this.dateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    // ---------------------------------------------------------------- geometry

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity * Prefs.scale(getContext());
    }

    private int systemBarHeight(String name) {
        Resources r = getResources();
        int id = r.getIdentifier(name, "dimen", "android");
        return id > 0 ? r.getDimensionPixelSize(id) : 0;
    }

    /** Nothing but the wallpaper shows while the search sheet is up. */
    void setSearchMode(boolean on) {
        searchMode = on;
        invalidate();
    }

    /**
     * Clock, date and temperature. The arrangement and alignment come from the theme, so
     * a saved look keeps its own typography without touching this code.
     */
    private void drawClock(Canvas canvas, Paint text, float alpha) {
        Paint.Align align = "center".equals(clockAlign) ? Paint.Align.CENTER
                : "right".equals(clockAlign) ? Paint.Align.RIGHT : Paint.Align.LEFT;
        float anchor = "center".equals(clockAlign) ? (contentLeft + contentRight) / 2f
                : "right".equals(clockAlign) ? contentRight : contentLeft;

        text.setTypeface(tfClock);
        text.setTextAlign(align);
        text.setTextSize(sp(52 * clockScale));
        text.setLetterSpacing(clockSpacing);
        text.setColor(cFg);
        text.setAlpha((int) (246 * alpha));

        if ("stacked".equals(clockLayout) && dateOn) {
            // Date above the time, small and spaced out, the way a stacked face reads.
            text.setTypeface(tfRegular);
            text.setTextSize(sp(12));
            text.setLetterSpacing(0.18f);
            text.setAlpha((int) (185 * alpha));
            canvas.drawText(dateText.toUpperCase(Locale.ENGLISH), anchor, dateBaseline, text);

            text.setTypeface(tfClock);
            text.setTextSize(sp(58 * clockScale));
            text.setLetterSpacing(clockSpacing);
            text.setAlpha((int) (246 * alpha));
            canvas.drawText(clockText, anchor, clockBaseline, text);
            text.setLetterSpacing(0f);
        } else {
            canvas.drawText(clockText, anchor, clockBaseline, text);
            text.setLetterSpacing(0f);

            if (dateOn && "inline".equals(clockLayout)) {
                // Same baseline, pushed past the time with a fixed gap.
                float width = text.measureText(clockText);
                float gap = dp(14);
                float x = "center".equals(clockAlign) ? anchor + width / 2f + gap
                        : "right".equals(clockAlign) ? anchor - width - gap : anchor + width + gap;
                text.setTypeface(tfRegular);
                text.setTextSize(sp(14));
                text.setAlpha((int) (200 * alpha));
                canvas.drawText(dateText, x, dateBaseline, text);
            } else if (dateOn && !"time".equals(clockLayout)) {
                text.setTypeface(tfRegular);
                text.setTextSize(sp(15));
                text.setAlpha((int) (205 * alpha));
                canvas.drawText(dateText, anchor, dateBaseline, text);
            }
        }

        if (weatherOn && !weatherText.isEmpty() && !"time".equals(clockLayout)) {
            text.setTypeface(tfRegular);
            text.setTextSize(sp(13));
            text.setLetterSpacing(0f);
            text.setColor(theme.accent);
            text.setAlpha((int) (215 * alpha));
            canvas.drawText(weatherText, anchor, weatherBaseline, text);
            text.setColor(cFg);
        }
    }

    /** Temperature line under the date. Empty string hides it. */
    void setWeather(String value) {
        String next = value == null ? "" : value;
        if (next.equals(weatherText)) return;
        weatherText = next;
        invalidate();
    }

    /** Re-reads the theme and throws away every cached icon drawn in the old one. */
    void refreshTheme() {
        icons.setTheme(Theme.current(getContext()));
        dirty = true;
        invalidate();
    }

    void markDirty() {
        dirty = true;
        rebuildIfNeeded();
        invalidate();
    }

    /** Drops the icon cache so a freshly picked image shows up. */
    void reloadIcons() {
        icons.clear();
        invalidate();
    }

    void reloadApps() {
        repo.load();
        dirty = true;
        rebuildIfNeeded();
        invalidate();
    }

    boolean isInAppList() { return mode == MODE_APPS; }

    void goHome() {
        if (mode == MODE_APPS) setMode(MODE_HOME);
    }

    private void rebuildIfNeeded() {
        if (!dirty && getWidth() > 0) return;
        rebuild();
    }

    private void rebuild() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        Context ctx = getContext();
        theme = Theme.current(ctx);
        dark = !theme.light;
        cFg = theme.foreground;
        cFgMid = theme.mid;
        cFgDim = theme.dim;
        cLine = dark ? 0x14FFFFFF : 0x10000000;
        cFabFg = dark ? 0xFF22262C : 0xFF2A2F36;
        scrimBase = Prefs.dim(ctx) / 100f;
        clockScale = theme.clockScale;
        clockSpacing = theme.clockSpacing;
        weatherOn = Prefs.weather(ctx);

        statusH = systemBarHeight("status_bar_height");
        navH = systemBarHeight("navigation_bar_height");
        clockOn = Prefs.clock(ctx);
        dateOn = Prefs.date(ctx);
        iconsOn = Prefs.icons(ctx);
        labelsOn = Prefs.labels(ctx);
        waveOn = Prefs.wave(ctx);
        waveLeft = Prefs.waveLeft(ctx);

        // Geometry taken from the reference layout, in dp at 440dpi:
        //   content grid 40dp, icons 46..90dp, labels 110dp, rows 56dp,
        //   list section block 43dp, wave letters 52px apart at 26dp from the edge.
        float scale = Prefs.scale(ctx);
        rowPitch = dp(56) * Math.max(0.94f, Math.min(1.16f, scale));
        sectionH = dp(43);
        iconSize = dp(44);
        leftMargin = waveLeft ? dp(66) : dp(46);
        labelX = leftMargin + dp(64);
        headerX = dp(48);

        clockLayout = theme.clockLayout == null ? "classic" : theme.clockLayout;
        clockAlign = theme.clockAlign == null ? "left" : theme.clockAlign;
        contentLeft = leftMargin;
        contentRight = waveLeft ? w - dp(46) : w - dp(72);

        // Four clock arrangements, each with its own stack of lines underneath.
        if ("stacked".equals(clockLayout)) {
            dateBaseline = statusH + dp(150);
            clockBaseline = statusH + dp(212);
            weatherBaseline = statusH + dp(240);
            favTop = statusH + dp(262);
        } else if ("inline".equals(clockLayout)) {
            clockBaseline = statusH + dp(197);
            dateBaseline = clockBaseline;
            weatherBaseline = statusH + dp(226);
            favTop = statusH + dp(242.5f);
        } else if ("time".equals(clockLayout)) {
            clockBaseline = statusH + dp(197);
            dateBaseline = clockBaseline;
            weatherBaseline = statusH + dp(226);
            favTop = statusH + dp(242.5f);
        } else {
            clockBaseline = statusH + dp(197);
            dateBaseline = statusH + dp(226);
            weatherBaseline = statusH + dp(251);
            favTop = statusH + dp(weatherOn ? 267 : 242.5f);
        }

        railX = w - dp(26);
        railTop = statusH + dp(295);
        railBottom = h - navH - dp(68.4f);
        fabRadius = dp(27);
        fabCx = w - dp(87);
        fabCy = h - navH - dp(50.5f);

        // Roles are re-read here, not in the constructor: a font picked in settings has
        // to land the next time the launcher comes forward.
        tfClock = Theme.clockTypeface(ctx);
        tfRegular = Typefaces.apps(ctx);
        tfMedium = Typeface.create(tfRegular, Typeface.BOLD);

        favorites.clear();
        favorites.addAll(repo.favorites());

        rebuildSections();
        scrollHome = clampHome(scrollHome);
        scrollApps = clampApps(scrollApps);
        dirty = false;
    }

    private void rebuildSections() {
        sections.clear();
        sectionStarts.clear();
        sectionCounts.clear();

        List<AppEntry> apps = repo.apps();
        String last = null;
        int count = 0;
        float y = 0;
        for (int i = 0; i < apps.size(); i++) {
            AppEntry e = apps.get(i);
            if (!e.section.equals(last)) {
                if (last != null) sectionCounts.set(sectionCounts.size() - 1, Integer.valueOf(count));
                sections.add(e.section);
                sectionStarts.add(Float.valueOf(y));
                sectionCounts.add(Integer.valueOf(0));
                count = 0;
                y += sectionH;
                last = e.section;
            }
            count++;
            y += rowPitch;
        }
        if (!sections.isEmpty()) sectionCounts.set(sectionCounts.size() - 1, Integer.valueOf(count));
    }

    private List<AppEntry> appsInSection(int index) {
        List<AppEntry> apps = repo.apps();
        List<AppEntry> out = new ArrayList<AppEntry>();
        int start = 0;
        for (int i = 0; i < index; i++) start += sectionCounts.get(i).intValue();
        int count = index < sectionCounts.size() ? sectionCounts.get(index).intValue() : 0;
        for (int i = start; i < start + count && i < apps.size(); i++) out.add(apps.get(i));
        return out;
    }

    private float homeViewport() {
        return getHeight() - favTop - navH - dp(26);
    }

    private float maxScrollHome() {
        return Math.max(0, favorites.size() * rowPitch - homeViewport());
    }

    private float maxScrollApps() {
        float content = appsContentHeight() + sectionH + rowPitch * 2;
        float viewport = getHeight() - dp(8) - statusH - navH - dp(8);
        return Math.max(0, content - viewport);
    }

    private float appsContentHeight() {
        if (sections.isEmpty()) return 0;
        float height = 0;
        for (int i = 0; i < sections.size(); i++) height = sectionStarts.get(i).floatValue() + sectionH;
        int last = sections.size() - 1;
        return sectionStarts.get(last).floatValue() + sectionH
                + sectionCounts.get(last).intValue() * rowPitch;
    }

    /**
     * Top of the app-list content. Measured off the reference: its first section header
     * starts at 96px, so the first row of a section centres on 291 — not on our status-bar
     * constant, which is 58px lower and showed up as a low landing on the first sections.
     */
    private float listTop() { return dp(35); }

    private float clampHome(float v) { return Math.max(0, Math.min(maxScrollHome(), v)); }
    private float clampApps(float v) { return Math.max(0, Math.min(maxScrollApps(), v)); }

    /**
     * Scroll offset that centres a whole section on the screen: header and its rows sit in
     * the middle band, not pinned under the top edge and not chasing the finger. Sections
     * near either end clamp, because the list cannot scroll past itself.
     */
    private float sectionScrollTarget(int index) {
        if (index < 0 || index >= sectionStarts.size()) return 0;
        float blockTop = listTop() + sectionStarts.get(index).floatValue();
        float blockHeight = sectionH + sectionCounts.get(index).intValue() * rowPitch;
        float screenMiddle = getHeight() * 0.5f;
        return clampApps(blockTop + blockHeight / 2f - screenMiddle);
    }

    // ------------------------------------------------------------------ clock

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int minute = now.get(Calendar.MINUTE) + now.get(Calendar.HOUR_OF_DAY) * 60;
        if (minute == lastClockMinute) return;
        lastClockMinute = minute;
        clockText = timeFormat.format(new Date());
        dateText = dateFormat.format(new Date());
    }

    // ------------------------------------------------------------- mode switch

    private void setMode(final int target) {
        if (mode == target && transition == (target == MODE_APPS ? 1f : 0f)) return;
        mode = target;
        switching = true;

        if (transitionAnim != null) transitionAnim.cancel();
        final float from = transition;
        final float to = (target == MODE_APPS) ? 1f : 0f;

        transitionAnim = ValueAnimator.ofFloat(from, to);
        transitionAnim.setDuration(230);
        transitionAnim.setInterpolator(new DecelerateInterpolator(1.6f));
        transitionAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                transition = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        transitionAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                transition = to;
                switching = false;
                invalidate();
            }
        });
        transitionAnim.start();

        if (target == MODE_APPS) {
            scrollApps = clampApps(scrollApps);
        }
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    private void openAppListAt(int sectionIndex) {
        setMode(MODE_APPS);
        scrollApps = sectionScrollTarget(sectionIndex);
    }

    // ------------------------------------------------------------------- draw

    @Override
    protected void onDraw(Canvas canvas) {
        rebuildIfNeeded();
        updateClock();

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        drawScrim(canvas, w, h);
        if (searchMode) return;

        if (transition < 1f) {
            int save = canvas.save();
            canvas.translate(0, -transition * h * 0.18f);
            drawHome(canvas, w, h, 1f - transition);
            canvas.restoreToCount(save);
        }
        if (transition > 0f) {
            int save = canvas.save();
            canvas.translate(0, (1f - transition) * h * 0.22f);
            drawApps(canvas, w, h, transition);
            canvas.restoreToCount(save);
        }

        if (waveOn) drawWave(canvas, w);
        drawFab(canvas);
    }

    private void drawScrim(Canvas canvas, int w, int h) {
        int base = dark ? 0 : 255;
        int a = (int) (Math.max(0f, Math.min(0.88f, scrimBase)) * 255);
        int aTop = Math.min(255, a + 44);
        scrimPaint.setShader(new LinearGradient(0, 0, 0, h * 0.6f,
                new int[]{Color.argb(aTop, base, base, base), Color.argb(a, base, base, base)},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, scrimPaint);
        scrimPaint.setShader(null);
    }

    private void drawHome(Canvas canvas, int w, int h, float alpha) {
        if (alpha <= 0.01f) return;

        if (clockOn) {
            drawClock(canvas, text, alpha);
        }

        if (favorites.isEmpty()) {
            text.setTypeface(tfRegular);
            text.setTextSize(sp(13));
            text.setColor(cFgMid);
            text.setAlpha((int) (200 * alpha));
            text.setTextAlign(Paint.Align.LEFT);
            float y = Math.max(favTop, clockBaseline + dp(40)) + dp(6);
            canvas.drawText("No favorites yet", labelX, y, text);
            text.setTextSize(sp(12));
            text.setColor(cFgDim);
            canvas.drawText("Long-press an app in the list to pin it", labelX, y + dp(24), text);
            text.setAlpha(255);
            return;
        }

        canvas.save();
        float homeBottom = h - navH;
        canvas.clipRect(0, favTop - dp(4), w, homeBottom);
        for (int i = 0; i < favorites.size(); i++) {
            float rowTop = favTop + i * rowPitch - scrollHome;
            if (rowTop > homeBottom) break;
            if (rowTop < favTop - rowPitch) continue;
            drawAppRow(canvas, favorites.get(i), rowTop, true, alpha);
        }
        canvas.restore();
    }

    private void drawApps(Canvas canvas, int w, int h, float alpha) {
        if (alpha <= 0.01f) return;

        float top = listTop();
        float bottom = h - navH - dp(8);
        float origin = top - scrollApps;
        float clipTop = 0;
        int scrubSection = (scrubbing && scrubY >= 0 && mode == MODE_APPS) ? sectionAt(scrubY) : -1;
        if (scrubSection >= 0 && scrubSection < sectionStarts.size()) {
            // Only the section under the finger is on screen while scrubbing. Everything
            // above its header is wallpaper, exactly like the reference.
            clipTop = origin + sectionStarts.get(scrubSection).floatValue() - dp(2);
        }

        canvas.save();
        canvas.clipRect(0, clipTop, w, bottom);
        for (int s = 0; s < sections.size(); s++) {
            if (scrubSection >= 0 && s != scrubSection) continue;
            float headerTop = origin + sectionStarts.get(s).floatValue();
            float sectionEnd = headerTop + sectionH + sectionCounts.get(s).intValue() * rowPitch;
            if (headerTop > bottom + sectionH) break;
            if (sectionEnd < top - rowPitch) continue;

            text.setTypeface(tfMedium);
            text.setTextSize(sp(20));
            text.setColor(cFg);
            text.setAlpha((int) (222 * alpha));
            text.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(sections.get(s), headerX, headerTop + sectionH * 0.84f, text);

            List<AppEntry> rows = appsInSection(s);
            float rowTop = headerTop + sectionH;
            for (int i = 0; i < rows.size(); i++) {
                if (rowTop > bottom + rowPitch) break;
                drawAppRow(canvas, rows.get(i), rowTop, iconsOn, alpha, iconSize);
                rowTop += rowPitch;
            }
        }

        // trailing block, the reference puts its own entries down here too
        float tail = origin + appsContentHeight();
        if (scrubSection < 0 && tail < bottom + dp(120)) {
            text.setTypeface(tfMedium);
            text.setTextSize(sp(20));
            text.setColor(cFg);
            text.setAlpha((int) (226 * alpha));
            text.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Wave", headerX, tail + sectionH * 0.84f, text);
            drawTailRow(canvas, "Wave Launcher settings", tail + sectionH, alpha);
            drawTailRow(canvas, "Wallpaper & style", tail + sectionH + rowPitch, alpha);
        }
        canvas.restore();

        if (repo.apps().isEmpty()) {
            text.setTypeface(tfRegular);
            text.setTextSize(sp(14));
            text.setColor(cFgMid);
            text.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("No apps found", leftMargin, top + dp(60), text);
        }
        text.setAlpha(255);
    }

    private void drawTailRow(Canvas canvas, String label, float rowTop, float alpha) {
        float cy = rowTop + rowPitch / 2f;
        text.setTypeface(tfRegular);
        text.setTextSize(sp(16));
        text.setColor(cFg);
        text.setAlpha((int) (232 * alpha));
        text.setTextAlign(Paint.Align.LEFT);
        Paint.FontMetrics fm = text.getFontMetrics();
        canvas.drawText(label, labelX, cy - (fm.ascent + fm.descent) / 2f, text);
    }

    private void drawAppRow(Canvas canvas, AppEntry e, float rowTop, boolean withIcon, float alpha) {
        drawAppRow(canvas, e, rowTop, withIcon, alpha, iconSize);
    }

    private void drawAppRow(Canvas canvas, AppEntry e, float rowTop, boolean withIcon,
                            float alpha, float size) {
        float cy = rowTop + rowPitch / 2f;

        if (withIcon) {
            float x = leftMargin;
            float y = cy - size / 2f;
            Bitmap bmp = icons.get(e, iconCallback);
            if (bmp != null) {
                iconPaint.setAlpha((int) (255 * Math.max(0f, Math.min(1f, alpha))));
                tmp.set(x, y, x + size, y + size);
                canvas.drawBitmap(bmp, null, tmp, iconPaint);
                iconPaint.setAlpha(255);
            } else {
                drawIconPlaceholder(canvas, x, y, e.label, alpha);
            }
        }

        text.setTypeface(tfRegular);
        text.setTextSize(sp(15));
        text.setColor(cFg);
        text.setAlpha((int) (238 * alpha));
        text.setTextAlign(Paint.Align.LEFT);
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        float maxW = railX - dp(26) - labelX;
        String label = ellipsize(e.label, text, maxW);
        canvas.drawText(label, labelX, baseline, text);
    }

    private void drawIconPlaceholder(Canvas canvas, float x, float y, String label, float alpha) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(dark ? 0x22FFFFFF : 0x14000000);
        tmp.set(x, y, x + iconSize, y + iconSize);
        canvas.drawRoundRect(tmp, iconSize * 0.28f, iconSize * 0.28f, fill);

        text.setTypeface(tfMedium);
        text.setTextSize(sp(17));
        text.setColor(cFgMid);
        text.setAlpha((int) (220 * alpha));
        text.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = text.getFontMetrics();
        String letter = label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase(Locale.getDefault());
        canvas.drawText(letter, x + iconSize / 2f,
                y + iconSize / 2f - (fm.ascent + fm.descent) / 2f, text);
    }

    private void drawWave(Canvas canvas, int w) {
        // Star above the letters, small ring below them, letters 16sp a little under
        // 19dp apart, column 26dp in from the edge.
        text.setTypeface(tfRegular);
        text.setTextSize(sp(16));
        text.setColor(cFg);
        text.setAlpha(232);
        text.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = text.getFontMetrics();
        float starY = railTop - dp(20);
        canvas.drawText("\u2606", railX, starY - (fm.ascent + fm.descent) / 2f, text);

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(cFg);
        fill.setAlpha(170);
        float ringY = railBottom + dp(16);
        canvas.drawCircle(railX, ringY, dp(2.6f), fill);

        if (sections.isEmpty()) return;
        int n = sections.size();
        String active = activeSection();
        float span = railBottom - railTop;

        // While a finger is on the rail the column bows away from it and the letter under
        // the finger leaves the column for a bubble beside the list.
        boolean engaged = scrubbing && scrubY >= 0;
        int focused = engaged ? sectionAt(scrubY) : -1;
        float dir = waveLeft ? 1f : -1f;

        for (int k = 0; k < n; k++) {
            float t = n == 1 ? 0.5f : k / (float) (n - 1);
            float y = railTop + t * span;
            float curve = (float) Math.sin(t * Math.PI) * dp(3);
            float x = waveLeft ? dp(26) + curve : w - dp(26) - curve;
            String letter = sections.get(k);
            boolean isActive = mode == MODE_APPS && letter.equals(active);

            if (engaged) {
                if (k == focused) continue;
                float dy = y - scrubY;
                float pull = (float) Math.exp(-(dy * dy) / (dp(120) * dp(120)));
                x += dir * (dp(8) + dp(88) * pull);
            }

            text.setTypeface(isActive ? tfMedium : tfRegular);
            text.setTextSize(sp(16));
            text.setColor(cFg);
            text.setAlpha(isActive ? 250 : 205);
            text.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics lfm = text.getFontMetrics();
            canvas.drawText(letter, x, y - (lfm.ascent + lfm.descent) / 2f, text);
        }

        if (engaged && focused >= 0 && focused < n) {
            float cy = Math.max(railTop, Math.min(railBottom, scrubY));
            float cx = (waveLeft ? dp(26) : w - dp(26)) + dir * dp(126);
            float r = dp(21.5f);

            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xFFFFFFFF);
            fill.setAlpha(248);
            canvas.drawCircle(cx, cy, r, fill);

            text.setTypeface(tfMedium);
            text.setTextSize(sp(20));
            text.setColor(0xFF111111);
            text.setAlpha(255);
            text.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics bfm = text.getFontMetrics();
            canvas.drawText(sections.get(focused), cx,
                    cy - (bfm.ascent + bfm.descent) / 2f, text);
        }
        text.setAlpha(255);
    }

    private void drawFab(Canvas canvas) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(dark ? 0xF2FFFFFF : 0xFAFFFFFF);
        canvas.drawCircle(fabCx, fabCy, fabRadius, fill);
        fabRect.set(fabCx - fabRadius, fabCy - fabRadius, fabCx + fabRadius, fabCy + fabRadius);

        glyphStroke.setStyle(Paint.Style.STROKE);
        glyphStroke.setStrokeWidth(dp(2.1f));
        glyphStroke.setColor(cFabFg);
        glyphStroke.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawCircle(fabCx - dp(1.5f), fabCy - dp(1.5f), dp(6.2f), glyphStroke);
        canvas.drawLine(fabCx + dp(3.4f), fabCy + dp(3.4f),
                fabCx + dp(8.5f), fabCy + dp(8.5f), glyphStroke);
    }

    private static String ellipsize(String src, Paint p, float maxWidth) {
        if (src == null) return "";
        if (maxWidth <= 0) return "";
        if (p.measureText(src) <= maxWidth) return src;
        String ell = "\u2026";
        int lo = 0, hi = src.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (p.measureText(src.substring(0, mid) + ell) <= maxWidth) lo = mid;
            else hi = mid - 1;
        }
        return src.substring(0, Math.max(0, lo)) + ell;
    }

    // ------------------------------------------------------------------ state

    private String activeSection() {
        if (sections.isEmpty()) return "";
        float probe = scrollApps + listTop() + dp(70);
        int active = 0;
        for (int i = 0; i < sectionStarts.size(); i++) {
            if (sectionStarts.get(i).floatValue() <= probe) active = i;
            else break;
        }
        return sections.get(active);
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                lastY = downY;
                dragDist = 0;
                dragging = false;
                scrubbing = false;
                longPressFired = false;
                pressRow = -1;
                pressFav = -1;
                pressSection = -1;

                if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
                else velocityTracker.clear();
                velocityTracker.addMovement(ev);

                // Touch lands on a moving list: stop it dead. A launcher that keeps
                // coasting under the finger feels broken on the way back up.
                if (!scroller.isFinished()) scroller.abortAnimation();

                // The rail answers even while the screen is animating: a second tap on the
                // same letter has to land while the first tap's transition is still running.
                if (hitWave(downX, downY) && downY >= railTop - dp(4)) {
                    int index = sectionAt(downY);
                    if (index >= 0) {
                        long now = android.os.SystemClock.uptimeMillis();
                        if (index == lastLetterIndex
                                && now - lastLetterTap < ViewConfiguration.getDoubleTapTimeout()) {
                            lastLetterTap = 0L;
                            lastLetterIndex = -1;
                            scrubbing = false;
                            host.lockScreen();
                            return true;
                        }
                        lastLetterTap = now;
                        lastLetterIndex = index;
                    }
                    scrubbing = true;
                    lastScrub = Integer.MIN_VALUE;
                    scrubTo(downY);
                    return true;
                }

                if (switching) return true;

                if (fabRect.contains(downX, downY)) return true;

                if (mode == MODE_HOME) {
                    // Long press always arms: on a favorite it opens the app sheet, on empty
                    // space it opens settings. Arming only on empty space left the sheet
                    // reachable from the app list and nowhere else.
                    pressFav = favoriteAt(downY);
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                } else {
                    pressRow = appRowAt(downY);
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (velocityTracker != null) velocityTracker.addMovement(ev);
                if (switching) return true;
                if (scrubbing) {
                    scrubTo(ev.getY());
                    invalidate();
                    return true;
                }
                dragDist = Math.max(dragDist, Math.abs(ev.getY() - downY) + Math.abs(ev.getX() - downX));
                if (dragDist > touchSlop && !dragging) {
                    dragging = true;
                    handler.removeCallbacks(longPress);
                }
                if (dragging) {
                    if (!scroller.isFinished()) scroller.abortAnimation();
                    float delta = lastY - ev.getY();
                    if (mode == MODE_HOME) {
                        scrollHome = clampHome(scrollHome + delta);
                    } else {
                        scrollApps = clampApps(scrollApps + delta);
                    }
                    lastY = ev.getY();
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPress);
                if (velocityTracker != null) velocityTracker.addMovement(ev);
                upY = ev.getY();

                if (scrubbing) {
                    // A tap means "take me to this letter": the list settles with the
                    // section's first row level with the letter. A drag that ends on a
                    // letter leaves the header where the drag put it, as the reference does.
                    boolean tapped = Math.abs(ev.getY() - downY) <= touchSlop * 2;
                    int index = lastScrub;
                    scrubbing = false;
                    scrubY = -1f;
                    lastScrub = Integer.MIN_VALUE;
                    if (tapped && index != Integer.MIN_VALUE && mode == MODE_APPS) {
                        scrollToSection(index, 170);
                    }
                    invalidate();
                    return true;
                }
                if (longPressFired) {
                    longPressFired = false;
                    return true;
                }
                if (dragging) {
                    dragging = false;
                    int vy = 0;
                    if (velocityTracker != null) {
                        velocityTracker.computeCurrentVelocity(1000);
                        vy = (int) velocityTracker.getYVelocity();
                    }
                    boolean upGesture = downY - upY > dp(60);
                    boolean noMoreContent = mode == MODE_HOME && scrollHome >= maxScrollHome() - 1;
                    if (mode == MODE_HOME && upGesture && noMoreContent) {
                        // Nothing left to scroll: the swipe means search, not a fling.
                        host.openSearch();
                        return true;
                    }
                    if (mode == MODE_HOME && downY >= favTop - dp(28)
                            && upY - downY > dp(80) && scrollHome <= 0f) {
                        // Pull down off the favorites, as the reference does, for the shade.
                        host.openNotifications();
                        return true;
                    }
                    if (Math.abs(vy) > dp(420)) {
                        startFling(-vy);
                    } else if (mode == MODE_HOME && dragDist > dp(70)
                            && downY - upY > dp(70) && maxScrollHome() == 0) {
                        host.openSearch();
                    }
                    return true;
                }
                handleTap(downX, downY);
                return true;

            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPress);
                dragging = false;
                scrubbing = false;
                scrubY = -1f;
                lastScrub = Integer.MIN_VALUE;
                longPressFired = false;
                return true;
        }
        return super.onTouchEvent(ev);
    }

    private int flingMode = MODE_HOME;

    /**
     * Starts a fling from the current offset.
     *
     * The fling has to be seeded with the offset the list is already at, because
     * OverScroller refuses to travel outside {@code 0..limit}. Seeded with a constant
     * 0 it could only ever coast downward: an upward fling started on the lower bound
     * and was clamped to zero distance on the first frame, which is why the list
     * fell smoothly downwards and stopped dead on the way back up.
     */
    private void startFling(float velocityY) {
        int limit = (int) (mode == MODE_HOME ? maxScrollHome() : maxScrollApps());
        int current = (int) (mode == MODE_HOME ? scrollHome : scrollApps);
        int velocity = Math.round(velocityY);
        scroller.fling(0, current, 0, velocity, 0, 0, 0, limit);
        flingMode = mode;
        postInvalidateOnAnimation();
    }

    private int favoriteAt(float y) {
        if (mode != MODE_HOME || favorites.isEmpty()) return -1;
        if (y < favTop) return -1;
        int i = (int) ((y - favTop + scrollHome) / rowPitch);
        if (i < 0 || i >= favorites.size()) return -1;
        return i;
    }

    private int appRowAt(float y) {
        if (mode != MODE_APPS) return -1;
        float contentY = y - listTop() + scrollApps;
        if (contentY < 0) return -1;
        List<AppEntry> apps = repo.apps();
        int index = 0;
        for (int s = 0; s < sections.size(); s++) {
            float start = sectionStarts.get(s).floatValue();
            float rowsTop = start + sectionH;
            float rowsBottom = rowsTop + sectionCounts.get(s).intValue() * rowPitch;
            if (contentY >= rowsTop && contentY < rowsBottom) {
                int row = (int) ((contentY - rowsTop) / rowPitch);
                int absolute = index + row;
                return absolute < apps.size() ? absolute : -1;
            }
            index += sectionCounts.get(s).intValue();
        }
        return -1;
    }

    private boolean hitWave(float x, float y) {
        if (!waveOn) return false;
        if (y < railTop - dp(46) || y > railBottom + dp(46)) return false;
        if (waveLeft) return x <= dp(56);
        return x >= getWidth() - dp(56);
    }

    private void handleTap(float x, float y) {
        if (fabRect.contains(x, y)) {
            host.openSearch();
            return;
        }

        if (hitWave(x, y)) {
            if (y < railTop - dp(4)) {
                setMode(MODE_HOME);
                return;
            }
            if (y > railBottom + dp(8)) {
                setMode(MODE_APPS);
                scrollApps = clampApps(maxScrollApps());
                return;
            }
            int index = sectionAt(y);
            if (index >= 0 && mode == MODE_HOME) openAppListAt(index);
            else if (index >= 0) jumpToSection(index);
            return;
        }

        if (mode == MODE_HOME) {
            int fav = favoriteAt(y);
            if (fav >= 0 && fav < favorites.size()) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                host.launch(favorites.get(fav));
            }
            return;
        }

        int row = appRowAt(y);
        if (row >= 0 && row < repo.apps().size()) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            host.launch(repo.apps().get(row));
        }
    }

    private int sectionAt(float y) {
        if (sections.isEmpty()) return -1;
        float span = Math.max(1f, railBottom - railTop);
        float t = Math.max(0f, Math.min(1f, (y - railTop) / span));
        int index = Math.round(t * (sections.size() - 1));
        return Math.max(0, Math.min(sections.size() - 1, index));
    }

    private void scrubTo(float y) {
        scrubY = y;
        if (sections.isEmpty()) return;
        int index = sectionAt(y);
        if (index != lastScrub) {
            lastScrub = index;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (mode == MODE_HOME) setMode(MODE_APPS);
        }
        if (mode == MODE_APPS) {
            scrollApps = clampApps(sectionTopTarget(index));
            invalidate();
        }
    }

    /**
     * Scrubbing parks the section header at a fixed height instead of lining it up with the
     * finger: the finger is on the rail, the eye is on the list, and a list that chases the
     * finger under it is unreadable.
     */
    private float sectionTopTarget(int index) {
        if (index < 0 || index >= sectionStarts.size()) return 0;
        return listTop() + sectionStarts.get(index).floatValue() - dp(250);
    }

    private void jumpToSection(int index) {
        if (index < 0 || index >= sectionStarts.size()) return;
        scrollToSection(index, 140);
    }

    private void scrollToSection(int index, int duration) {
        float target = sectionScrollTarget(index);
        if (duration <= 0) {
            scrollApps = target;
            invalidate();
            return;
        }
        int current = (int) scrollApps;
        scroller.startScroll(0, current, 0, (int) target - current, duration);
        flingMode = MODE_APPS;
        postInvalidateOnAnimation();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            float value = scroller.getCurrY();
            if (flingMode == MODE_HOME) scrollHome = clampHome(value);
            else scrollApps = clampApps(value);
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode >= android.view.KeyEvent.KEYCODE_A && keyCode <= android.view.KeyEvent.KEYCODE_Z) {
            char c = (char) ('A' + (keyCode - android.view.KeyEvent.KEYCODE_A));
            for (int i = 0; i < sections.size(); i++) {
                if (sections.get(i).equals(String.valueOf(c))) {
                    if (mode == MODE_HOME) openAppListAt(i);
                    else jumpToSection(i);
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        dirty = true;
        rebuildIfNeeded();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.post(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(longPress);
        super.onDetachedFromWindow();
    }
}
