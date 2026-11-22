package com.bench.wave;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Settings, grouped into cards.
 *
 * Everything is built in code: no preference XML, no fragments, no dependencies. The
 * screen is one column of rounded cards, one card per subject, each row a single idea
 * with its control on the right.
 */
public class SettingsActivity extends Activity {

    private int BG, CARD, FG, DIM, ACCENT;
    private static final int DANGER = 0xFFFF7A7A;
    private int HAIRLINE;

    private static final int REQ_FONT = 51;
    private static final int REQ_THEME = 52;

    private AppRepo repo;
    private SharedPreferences prefs;
    private LinearLayout column;
    private ScrollView scroll;
    private String pendingFontKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = new AppRepo(this);
        repo.load();
        prefs = Prefs.sp(this);
        palette();

        scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(32));

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(18), dp(38), dp(18), dp(40));
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        build();
        setContentView(scroll);
        Typefaces.apply(this, scroll);
    }

    /** Screen colours come from the active theme, so settings match the launcher. */
    private void palette() {
        Theme t = Theme.current(this);
        BG = t.background;
        CARD = t.light ? 0xFFFBF9F5 : blend(t.background, 0xFFFFFFFF, 0.06f);
        FG = t.foreground;
        DIM = t.mid;
        ACCENT = t.accent;
        HAIRLINE = t.light ? 0x14000000 : 0x14FFFFFF;
    }

    private static int blend(int base, int other, float amount) {
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int or_ = (other >> 16) & 0xFF, og = (other >> 8) & 0xFF, ob = other & 0xFF;
        int r = Math.round(br + (or_ - br) * amount);
        int g = Math.round(bg + (og - bg) * amount);
        int b = Math.round(bb + (ob - bb) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void onResume() {
        super.onResume();
        palette();
        if (repo != null) repo.load();
        if (column != null) {
            build();
            Typefaces.apply(this, scroll);
        }
    }

    // ------------------------------------------------------------------ screen

    private void build() {
        column.removeAllViews();

        TextView title = new TextView(this);
        title.setText("Wave Launcher");
        title.setTextSize(28f);
        title.setTextColor(FG);
        title.setLetterSpacing(-0.01f);
        column.addView(title);

        TextView sub = new TextView(this);
        sub.setText("v1.3  \u00b7  com.bench.wave");
        sub.setTextSize(11.5f);
        sub.setTextColor(DIM);
        sub.setPadding(0, dp(6), 0, 0);
        column.addView(sub);

        buildAppearance();
        buildHome();
        buildAppList();
        buildLibrary();
        buildFonts();
        buildLock();
        buildAbout();
    }

    private void buildAppearance() {
        header("Appearance");
        LinearLayout card = card();

        row(card, "Theme", Theme.current(this).name, new View.OnClickListener() {
            @Override public void onClick(View v) { pickTheme(); }
        });
        divider(card);
        action(card, "Edit theme", "Icons, clock, colours, what is on screen", false,
                new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, ThemeEditorActivity.class));
            }
        });
        divider(card);
        action(card, "Import theme", "A .zip with a theme.json inside", false,
                new View.OnClickListener() {
            @Override public void onClick(View v) { pickThemeZip(); }
        });
        divider(card);
        slider(card, "Text size", Prefs.K_SCALE, 85, 135, 100, "%");
        divider(card);
        slider(card, "Wallpaper dim", Prefs.K_DIM, 0, 80, 34, "%");
    }

    private void buildHome() {
        header("Home screen");
        LinearLayout card = card();
        toggle(card, "Clock", "Big time and date above favorites", Prefs.K_CLOCK, true);
        divider(card);
        toggle(card, "App icons", "Draw the icon beside each name", Prefs.K_ICONS, true);
        divider(card);
        toggle(card, "Favorite labels", "Show the name under a favorite", Prefs.K_LABELS, true);
        divider(card);
        toggle(card, "Weather", "Temperature in Celsius under the clock", "weather", true);
    }

    private void buildAppList() {
        header("App list");
        LinearLayout card = card();
        toggle(card, "Wave alphabet", "Letter rail for jumping through the list",
                Prefs.K_WAVE, true);
        divider(card);
        toggle(card, "Alphabet on the left", "Rail on the left for one-handed use",
                Prefs.K_WAVE_LEFT, false);
    }

    private void buildLibrary() {
        header("Library");
        LinearLayout card = card();
        action(card, "Clear favorites", "Remove every pinned app", false,
                new View.OnClickListener() {
            @Override public void onClick(View v) {
                repo.resetFavorites();
                toast("Favorites cleared");
            }
        });
        divider(card);
        action(card, "Restore hidden apps", hiddenSummary(), false,
                new View.OnClickListener() {
            @Override public void onClick(View v) {
                repo.resetHidden();
                toast("Hidden apps are back in the list");
            }
        });
    }

    private void buildFonts() {
        header("Fonts");
        LinearLayout card = card();
        fontRow(card, "Clock", Prefs.K_FONT_CLOCK);
        divider(card);
        fontRow(card, "App names", Prefs.K_FONT_APPS);
        divider(card);
        fontRow(card, "Interface", Prefs.K_FONT_UI);
    }

    private void buildLock() {
        header("Gestures & lock");
        LinearLayout card = card();

        boolean on = WaveLockService.enabled(this);
        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(16), dp(14), dp(16), dp(14));

        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(on ? 0xFF4ADE80 : 0xFF5A6270);
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.rightMargin = dp(10);
        status.addView(dot, dotLp);

        TextView statusText = new TextView(this);
        statusText.setText(on ? "Accessibility service is on" : "Accessibility service is off");
        statusText.setTextSize(14f);
        statusText.setTextColor(on ? FG : DIM);
        status.addView(statusText);
        card.addView(status);

        TextView note = new TextView(this);
        note.setText("Double-tap a letter to lock the screen, pull down from favorites for "
                + "the shade. Both need this service; it is the only door Android gives a "
                + "launcher.");
        note.setTextSize(11.5f);
        note.setTextColor(DIM);
        note.setPadding(dp(16), 0, dp(16), dp(12));
        note.setLineSpacing(dp(2), 1f);
        card.addView(note);

        divider(card);
        action(card, on ? "Open accessibility settings" : "Turn the service on",
                "System settings", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                WaveLockService.openSettings(SettingsActivity.this);
            }
        });
    }

    private void buildAbout() {
        header("About");
        LinearLayout card = card();

        action(card, "Set as default launcher", "Home app for this device", false,
                new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
                } catch (ActivityNotFoundException ex) {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } catch (ActivityNotFoundException ex2) {
                        toast("Open Settings > Apps > Default apps");
                    }
                }
            }
        });
        divider(card);
        action(card, "Reset custom icons", "Back to the real icons", false,
                new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.clearIconOverrides(SettingsActivity.this);
                toast("Custom icons cleared");
            }
        });

        TextView foot = new TextView(this);
        foot.setText("Own package, own signature. Built from source.");
        foot.setTextSize(11f);
        foot.setTextColor(0xFF5C6577);
        foot.setPadding(dp(4), dp(22), dp(4), 0);
        column.addView(foot);
    }

    // ------------------------------------------------------------------ pieces

    private void header(String name) {
        TextView h = new TextView(this);
        h.setText(name.toUpperCase());
        h.setTextSize(10.5f);
        h.setLetterSpacing(0.16f);
        h.setTextColor(ACCENT);
        h.setPadding(dp(6), dp(26), 0, dp(9));
        column.addView(h);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(18));
        c.setBackground(bg);
        c.setClipToOutline(true);
        column.addView(c, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    private void divider(LinearLayout card) {
        View rule = new View(this);
        rule.setBackgroundColor(HAIRLINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        card.addView(rule, lp);
    }

    private LinearLayout rowShell(LinearLayout card) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setMinimumHeight(dp(58));
        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private LinearLayout labels(LinearLayout parent, String title, String summary) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14.5f);
        t.setTextColor(FG);
        box.addView(t);

        if (summary != null) {
            TextView s = new TextView(this);
            s.setText(summary);
            s.setTextSize(11.5f);
            s.setTextColor(DIM);
            s.setPadding(0, dp(2), 0, 0);
            box.addView(s);
        }
        parent.addView(box, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return box;
    }

    private void toggle(LinearLayout card, String name, String summary,
                        final String key, boolean def) {
        LinearLayout row = rowShell(card);
        labels(row, name, summary);

        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(key, def));
        int[][] states = {{android.R.attr.state_checked}, {}};
        int[] track = {0x667FB3FF, 0xFF2A323D};
        sw.setTrackTintList(new ColorStateList(states, track));
        sw.setThumbTintList(new ColorStateList(states, new int[]{ACCENT, 0xFF7A8496}));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                prefs.edit().putBoolean(key, on).apply();
            }
        });
        row.addView(sw);
    }

    private void slider(LinearLayout card, String name, final String key,
                        final int min, final int max, int def, final String suffix) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(16), dp(13), dp(16), dp(10));
        card.addView(block);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(14.5f);
        title.setTextColor(FG);
        top.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView value = new TextView(this);
        value.setText(prefs.getInt(key, def) + suffix);
        value.setTextSize(11.5f);
        value.setTextColor(ACCENT);
        value.setGravity(Gravity.CENTER);
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0x1A7FB3FF);
        chip.setCornerRadius(dp(8));
        value.setBackground(chip);
        value.setPadding(dp(9), dp(3), dp(9), dp(3));
        top.addView(value);
        block.addView(top);

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, prefs.getInt(key, def) - min));
        bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        bar.setThumbTintList(ColorStateList.valueOf(ACCENT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF2A323D));
        bar.setPadding(dp(2), dp(10), dp(2), dp(6));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                int v = min + progress;
                value.setText(v + suffix);
                prefs.edit().putInt(key, v).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { }
        });
        block.addView(bar);
    }

    /** A row whose whole line opens a chooser: value on the right, then a chevron. */
    private void row(LinearLayout card, String name, String value,
                     View.OnClickListener listener) {
        LinearLayout row = rowShell(card);
        labels(row, name, null);

        TextView shown = new TextView(this);
        shown.setText(value);
        shown.setTextSize(13f);
        shown.setTextColor(ACCENT);
        row.addView(shown);

        TextView chevron = new TextView(this);
        chevron.setText(" \u203A");
        chevron.setTextSize(17f);
        chevron.setTextColor(0xFF5C6577);
        row.addView(chevron);

        row.setBackground(ripple());
        row.setOnClickListener(listener);
    }

    private void action(LinearLayout card, String name, String summary,
                        boolean danger, View.OnClickListener listener) {
        LinearLayout row = rowShell(card);
        LinearLayout box = labels(row, name, summary);
        if (danger) {
            ((TextView) box.getChildAt(0)).setTextColor(DANGER);
        }

        TextView chevron = new TextView(this);
        chevron.setText("\u203A");
        chevron.setTextSize(20f);
        chevron.setTextColor(danger ? 0xFF9E6A6A : 0xFF5C6577);
        row.addView(chevron);

        row.setBackground(ripple());
        row.setOnClickListener(listener);
    }

    private void fontRow(LinearLayout card, String name, final String key) {
        LinearLayout row = rowShell(card);
        String path = prefs.getString(key, "");
        boolean custom = path != null && !path.isEmpty();
        String current = custom
                ? new java.io.File(path).getName().replaceAll("\\.[A-Za-z0-9]+$", "")
                : "Roboto";
        LinearLayout box = labels(row, name, current);
        ((TextView) box.getChildAt(1)).setTextColor(custom ? ACCENT : DIM);

        TextView chevron = new TextView(this);
        chevron.setText(custom ? "Change" : "Choose");
        chevron.setTextSize(12.5f);
        chevron.setTextColor(ACCENT);
        row.addView(chevron);

        row.setBackground(ripple());
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { fontDialog(key); }
        });
    }

    private RippleDrawable ripple() {
        GradientDrawable content = new GradientDrawable();
        content.setColor(Color.TRANSPARENT);
        return new RippleDrawable(ColorStateList.valueOf(0x1AFFFFFF), content, null);
    }

    // ------------------------------------------------------------------ themes

    private void pickTheme() {
        final java.util.List<Theme.Entry> entries = Theme.available(this);
        String[] names = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            Theme.Entry e = entries.get(i);
            names[i] = e.name + (e.builtin ? "" : "  (imported)");
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Theme");
        b.setItems(names, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                Theme.Entry e = entries.get(which);
                Theme.apply(SettingsActivity.this, e.id);
                palette();
                build();
                Typefaces.apply(SettingsActivity.this, scroll);
                toast(e.name + " applied");
            }
        });
        b.show();
    }

    private void pickThemeZip() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_THEME);
        } catch (ActivityNotFoundException ex) {
            toast("No file picker on this device");
        }
    }

    private void importTheme(Uri uri) {
        try {
            Theme imported = Theme.importZip(this, uri, uri.getLastPathSegment());
            Theme.apply(this, imported.id);
            palette();
            build();
            Typefaces.apply(this, scroll);
            toast(imported.name + " imported and applied");
        } catch (Exception ex) {
            toast("That zip is not a theme: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ fonts

    private void fontDialog(final String key) {
        boolean custom = prefs.getString(key, "") != null && !prefs.getString(key, "").isEmpty();
        final String[] items = custom
                ? new String[]{"Choose a font file", "Reset to Roboto"}
                : new String[]{"Choose a font file"};
        choose(key.equals(Prefs.K_FONT_CLOCK) ? "Clock font"
                        : key.equals(Prefs.K_FONT_APPS) ? "App name font" : "Interface font",
                items, -1, new Choice() {
            @Override public void onPick(int which) {
                if (which == 0) pickFont(key);
                else resetFont(key);
            }
        });
    }

    private void pickFont(String key) {
        pendingFontKey = key;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_FONT);
        } catch (ActivityNotFoundException ex) {
            toast("No file picker on this device");
        }
    }

    private void resetFont(String key) {
        prefs.edit().remove(key).apply();
        Typefaces.invalidate();
        build();
        Typefaces.apply(this, scroll);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_THEME) {
            Uri themeUri = data.getData();
            if (themeUri != null) importTheme(themeUri);
            return;
        }

        if (requestCode != REQ_FONT) return;
        Uri uri = data.getData();
        String key = pendingFontKey;
        pendingFontKey = null;
        if (uri == null || key == null) return;

        java.io.File dir = new java.io.File(getFilesDir(), "fonts");
        if (!dir.exists() && !dir.mkdirs()) { toast("Cannot write font here"); return; }
        String slot = key.equals(Prefs.K_FONT_CLOCK) ? "clock"
                : key.equals(Prefs.K_FONT_APPS) ? "apps" : "ui";
        java.io.File target = new java.io.File(dir, slot + ".ttf");

        java.io.InputStream in = null;
        java.io.FileOutputStream out = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) throw new java.io.IOException("no stream");
            out = new java.io.FileOutputStream(target);
            byte[] buf = new byte[16 * 1024];
            int read;
            while ((read = in.read(buf)) > 0) out.write(buf, 0, read);
            out.flush();
        } catch (Exception ex) {
            toast("Could not copy that file");
            return;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
            try { if (out != null) out.close(); } catch (Exception ignored) { }
        }

        try {
            Typeface test = Typeface.createFromFile(target);
            if (test == null) throw new IllegalStateException("not a font");
        } catch (RuntimeException ex) {
            target.delete();
            toast("That file is not a usable font");
            return;
        }

        prefs.edit().putString(key, target.getAbsolutePath()).apply();
        Typefaces.invalidate();
        build();
        Typefaces.apply(this, scroll);
        toast("Font applied");
    }

    // ------------------------------------------------------------------ helpers

    private interface Choice { void onPick(int which); }

    private void choose(String title, String[] items, int checked, final Choice pick) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(title);
        b.setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                pick.onPick(which);
            }
        });
        b.show();
    }

    private String themeName(int mode) {
        return mode == 1 ? "Dark" : mode == 2 ? "Light" : "System";
    }

    private String hiddenSummary() {
        int n = Prefs.list(this, Prefs.K_HIDDEN).length;
        if (n == 0) return "Nothing is hidden";
        return n + (n == 1 ? " app hidden" : " apps hidden");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
