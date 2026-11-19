package com.bench.wave;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** All knobs, programmatic. No preference XML, no fragments, no deps. */
public class SettingsActivity extends Activity {

    private static final int FG = 0xFFEDF1F7;
    private static final int DIM = 0xFF8A94A6;
    private static final int SURFACE = 0xFF161C24;
    private static final int ACCENT = 0xFF7FB3FF;

    private static final int REQ_FONT = 51;

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

        scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0F1318);
        scroll.setFillViewport(true);

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(28), dp(20), dp(40));
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        build();
        setContentView(scroll);
        Typefaces.apply(this, scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repo != null) repo.load();
        if (column != null) {
            build();
            Typefaces.apply(this, scroll);
        }
    }

    private void build() {
        column.removeAllViews();

        TextView title = new TextView(this);
        title.setText("Wave Launcher");
        title.setTextSize(30f);
        title.setTextColor(FG);
        column.addView(title);

        TextView sub = new TextView(this);
        sub.setText("A single-column launcher. Own package, own signature.");
        sub.setTextSize(12.5f);
        sub.setTextColor(DIM);
        sub.setPadding(0, dp(6), 0, dp(18));
        column.addView(sub);

        section("Appearance");

        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeRow.setGravity(Gravity.CENTER_VERTICAL);
        themeRow.setPadding(0, dp(4), 0, dp(4));

        TextView themeLabel = new TextView(this);
        themeLabel.setText("Theme");
        themeLabel.setTextSize(15f);
        themeLabel.setTextColor(FG);
        themeRow.addView(themeLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Spinner spinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        ArrayAdapter<String> ta = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Follow system", "Dark", "Light"});
        spinner.setAdapter(ta);
        spinner.setSelection(Prefs.themeMode(this));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == Prefs.themeMode(SettingsActivity.this)) return;
                prefs.edit().putInt(Prefs.K_THEME, position).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        themeRow.addView(spinner, new LinearLayout.LayoutParams(dp(150),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(themeRow);

        seek("Text size", Prefs.K_SCALE, 85, 135, 100, "%");
        seek("Wallpaper dim", Prefs.K_DIM, 0, 80, 34, "%");

        section("Home screen");

        toggle("Clock", "Big clock above your favorites", Prefs.K_CLOCK, true);
        toggle("App icons", "Show the icon of the focused app", Prefs.K_ICONS, true);
        toggle("Favorite labels", "Name under every favorite bubble", Prefs.K_LABELS, true);
        toggle("Wave alphabet", "Letter rail for fast scrolling", Prefs.K_WAVE, true);
        toggle("Alphabet on the left", "Left-handed rail", Prefs.K_WAVE_LEFT, false);

        section("Favorites");

        button("Clear favorites", new View.OnClickListener() {
            @Override public void onClick(View v) {
                repo.resetFavorites();
                toast("Favorites cleared");
            }
        });
        button("Restore hidden apps", new View.OnClickListener() {
            @Override public void onClick(View v) {
                repo.resetHidden();
                toast("Hidden apps restored");
            }
        });

        section("Fonts");
        fontRow("Clock font", Prefs.K_FONT_CLOCK);
        fontRow("App names", Prefs.K_FONT_APPS);
        fontRow("Interface", Prefs.K_FONT_UI);

        section("Lock screen");
        accessibilityBlock();

        section("Launcher");
        button("Reset all custom icons", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.clearIconOverrides(SettingsActivity.this);
                toast("Custom icons cleared");
            }
        });

        button("Set Wave Launcher as default", new View.OnClickListener() {
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

        TextView about = new TextView(this);
        about.setText("com.bench.wave \u00b7 v1.0\nOwn package, own signature.");
        about.setTextSize(11f);
        about.setTextColor(DIM);
        about.setPadding(0, dp(26), 0, 0);
        column.addView(about);
    }

    private void section(String name) {
        TextView header = new TextView(this);
        header.setText(name.toUpperCase());
        header.setTextSize(10.5f);
        header.setLetterSpacing(0.14f);
        header.setTextColor(ACCENT);
        header.setPadding(dp(2), dp(26), 0, dp(8));
        column.addView(header);

        View rule = new View(this);
        rule.setBackgroundColor(0x1AFFFFFF);
        column.addView(rule, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private void toggle(String name, String summary, final String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(15f);
        title.setTextColor(FG);
        labels.addView(title);

        TextView desc = new TextView(this);
        desc.setText(summary);
        desc.setTextSize(11.5f);
        desc.setTextColor(DIM);
        desc.setPadding(0, dp(2), dp(8), 0);
        labels.addView(desc);

        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(key, isChecked).apply();
            }
        });
        row.addView(sw);
        column.addView(row);
    }

    private void seek(String name, final String key, final int min, final int max, int def, final String suffix) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(12), 0, dp(6));

        final TextView title = new TextView(this);
        title.setText(name + "  \u00b7  " + prefs.getInt(key, def) + suffix);
        title.setTextSize(15f);
        title.setTextColor(FG);
        block.addView(title);

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, prefs.getInt(key, def) - min));
        GradientDrawable track = new GradientDrawable();
        track.setColor(SURFACE);
        bar.setBackground(track);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                title.setText(name + "  \u00b7  " + value + suffix);
                prefs.edit().putInt(key, value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        block.addView(bar);
        column.addView(block);
    }

    private void button(String label, View.OnClickListener listener) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(14.5f);
        b.setTextColor(FG);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SURFACE);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        column.addView(b, lp);
    }

    /** One font slot: current file, picker and reset. */
    private void fontRow(String name, final String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(15f);
        title.setTextColor(FG);
        labels.addView(title);

        String path = prefs.getString(key, "");
        String file = (path == null || path.isEmpty())
                ? "Roboto (bundled)"
                : new java.io.File(path).getName().replaceAll("\\.[A-Za-z0-9]+$", "");

        TextView desc = new TextView(this);
        desc.setText(file);
        desc.setTextSize(11.5f);
        desc.setTextColor(DIM);
        desc.setPadding(0, dp(2), 0, 0);
        labels.addView(desc);

        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView action = new TextView(this);
        action.setText(path == null || path.isEmpty() ? "Choose" : "Change");
        action.setTextSize(13f);
        action.setTextColor(ACCENT);
        action.setPadding(dp(12), dp(6), dp(4), dp(6));
        row.addView(action);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { fontDialog(key); }
        });
        column.addView(row);

        View rule = new View(this);
        rule.setBackgroundColor(0x12FFFFFF);
        column.addView(rule, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private void fontDialog(final String key) {
        final boolean custom = prefs.getString(key, "") != null
                && !prefs.getString(key, "").isEmpty();
        String[] items = custom
                ? new String[]{"Choose a font file", "Reset to bundled Roboto"}
                : new String[]{"Choose a font file"};
        new android.app.AlertDialog.Builder(this)
                .setTitle(key.equals(Prefs.K_FONT_CLOCK) ? "Clock font"
                        : key.equals(Prefs.K_FONT_APPS) ? "App name font" : "Interface font")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) pickFont(key);
                        else resetFont(key);
                    }
                }).show();
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
        if (requestCode != REQ_FONT || resultCode != RESULT_OK || data == null) return;
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

    private void accessibilityBlock() {
        boolean on = WaveLockService.enabled(this);

        TextView row = new TextView(this);
        row.setText(on ? "Accessibility service \u00b7 on" : "Accessibility service \u00b7 off");
        row.setTextSize(14.5f);
        row.setTextColor(on ? ACCENT : FG);
        row.setPadding(0, dp(14), 0, dp(4));
        column.addView(row);

        TextView desc = new TextView(this);
        desc.setText(on
                ? "Double-tap a letter on the wave to lock the screen."
                : "Double-tap a letter on the wave to lock the screen. Needs Wave Launcher switched on in"
                        + " Settings > Accessibility \u2014 that is the only way an app may turn the"
                        + " display off.");
        desc.setTextSize(11.5f);
        desc.setTextColor(DIM);
        desc.setPadding(0, dp(2), 0, dp(6));
        column.addView(desc);

        button(on ? "Accessibility settings" : "Turn on in accessibility settings",
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        WaveLockService.openSettings(SettingsActivity.this);
                    }
                });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
