package com.bench.wave;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The theme editor. One screen, live preview at the top, every part of the look below:
 * presets, icon pack, icon shape and size, clock face, layout and alignment, colours, and
 * which elements are on screen.
 *
 * Every control writes straight into the active theme in prefs, so backing out of this
 * screen leaves the launcher already changed. Nothing here is a form to submit.
 */
public class ThemeEditorActivity extends Activity {

    private static final String[] ACCENTS = {
            "#AFC4DE", "#7FB3FF", "#7FE3C0", "#FFD479", "#FF9E7A", "#E0A6FF",
            "#FFFFFF", "#6D5B45", "#8FA8C8", "#9BE86B"};

    private static final String[] BACKGROUNDS = {
            "#05070C", "#000000", "#0F1418", "#12100E", "#101B16", "#1A1220",
            "#F4F1EA", "#FFFFFF"};

    private static final int REQ_ICONS = 61;
    private static final int REQ_EXPORT = 62;

    private Theme draft;
    private AppRepo repo;
    private IconLoader previewIcons;
    private LinearLayout column;
    private ScrollView scroll;
    private Preview preview;
    private int BG, CARD, FG, DIM, ACCENT, HAIRLINE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = new AppRepo(this);
        repo.load();
        draft = Theme.current(this);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(40));

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(18), dp(14), dp(18), dp(40));
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(scroll);
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        draft = Theme.current(this);
        rebuild();
    }

    /** Rounded to whole pixels: every use here is a view size, a pad or a paint value. */
    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ screen

    private void palette() {
        BG = draft.background;
        CARD = draft.light ? 0xFFFBF9F5 : blend(draft.background, 0xFFFFFFFF, 0.06f);
        FG = draft.foreground;
        DIM = draft.mid;
        ACCENT = draft.accent;
        HAIRLINE = draft.light ? 0x14000000 : 0x14FFFFFF;
    }

    private static int blend(int base, int other, float amount) {
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int or_ = (other >> 16) & 0xFF, og = (other >> 8) & 0xFF, ob = other & 0xFF;
        int r = Math.round(br + (or_ - br) * amount);
        int g = Math.round(bg + (og - bg) * amount);
        int b = Math.round(bb + (ob - bb) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void rebuild() {
        // The preview view is reused across rebuilds, so detach it from the card that
        // held it before the column is emptied.
        if (preview != null && preview.getParent() instanceof ViewGroup) {
            ((ViewGroup) preview.getParent()).removeView(preview);
        }
        palette();
        scroll.setBackgroundColor(BG);
        column.removeAllViews();

        column.addView(topBar());
        previewCard();

        buildPresets();
        buildIcons();
        buildClock();
        buildColours();
        buildElements();
        buildActions();
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(10), 0, dp(6));

        TextView back = new TextView(this);
        back.setText("\u2190");
        back.setTextSize(22f);
        back.setTextColor(FG);
        back.setPadding(dp(4), dp(4), dp(16), dp(4));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        bar.addView(back);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Edit theme");
        title.setTextSize(22f);
        title.setTextColor(FG);
        titles.addView(title);

        TextView sub = new TextView(this);
        sub.setText(draft.name);
        sub.setTextSize(11.5f);
        sub.setTextColor(DIM);
        sub.setPadding(0, dp(2), 0, 0);
        titles.addView(sub);

        bar.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    private void previewCard() {
        LinearLayout card = card();
        card.setPadding(0, 0, 0, 0);

        if (preview == null) preview = new Preview(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(196));
        card.addView(preview, lp);
    }

    // ------------------------------------------------------------------ presets

    private void buildPresets() {
        header("Presets");
        LinearLayout card = card();

        final List<Theme.Entry> entries = Theme.available(this);
        String[] names = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            Theme.Entry e = entries.get(i);
            names[i] = e.name + (e.builtin ? "" : "  \u00b7 saved");
        }

        row(card, "Start from", draft.name, new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(ThemeEditorActivity.this)
                        .setTitle("Start from")
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                Theme.apply(ThemeEditorActivity.this, entries.get(which).id);
                                draft = Theme.current(ThemeEditorActivity.this);
                                previewIcons = null;
                                rebuild();
                                Typefaces.apply(ThemeEditorActivity.this, scroll);
                            }
                        })
                        .show();
            }
        });
        divider(card);
        action(card, "Import a theme", "A .zip with theme.json inside", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivityForResult(i, REQ_ICONS);
                } catch (Exception ex) {
                    toast("No file picker on this device");
                }
            }
        });
    }

    // ------------------------------------------------------------------ icons

    private void buildIcons() {
        header("Icons");
        LinearLayout card = card();

        row(card, "Icon pack", iconPackName(), new View.OnClickListener() {
            @Override public void onClick(View v) { pickIconPack(); }
        });
        divider(card);

        row(card, "Shape", label(Theme.SHAPE_LABELS, Theme.SHAPES, draft.iconShape),
                new View.OnClickListener() {
            @Override public void onClick(View v) {
                choose("Icon shape", Theme.SHAPE_LABELS, Theme.SHAPES, draft.iconShape,
                        new Picked() {
                    @Override public void onPick(String value) {
                        draft.iconShape = value;
                        commit();
                    }
                });
            }
        });
        divider(card);

        slider(card, "Icon size", Math.round(draft.iconScale * 100f), 50, 100, "%",
                new SliderPicked() {
            @Override public void onPick(int value) {
                draft.iconScale = value / 100f;
                commit();
            }
        });
        divider(card);

        swatches(card, "Plate colour", BACKGROUNDS, draft.iconBg, new SwatchPicked() {
            @Override public void onPick(int colour) {
                draft.iconBg = colour;
                commit();
            }
        });
        divider(card);

        toggle(card, "Monochrome icons", "Drop the colour out of every icon",
                draft.iconDesaturate, new TogglePicked() {
            @Override public void onPick(boolean on) {
                draft.iconDesaturate = on;
                if (!on) draft.iconTintStrength = 0f;
                commit();
            }
        });
    }

    private String iconPackName() {
        if (draft.iconPack == null || draft.iconPack.isEmpty()) return "System";
        List<IconPack> packs = IconPack.installed(this);
        for (int i = 0; i < packs.size(); i++) {
            if (packs.get(i).pkg.equals(draft.iconPack)) return packs.get(i).label;
        }
        return "Missing pack";
    }

    private void pickIconPack() {
        final List<IconPack> packs = IconPack.installed(this);
        final List<String> names = new ArrayList<String>();
        final List<String> ids = new ArrayList<String>();
        names.add("System");
        ids.add("");
        for (int i = 0; i < packs.size(); i++) {
            IconPack p = packs.get(i);
            names.add(p.label + "  (" + p.size() + " icons)");
            ids.add(p.pkg);
        }
        if (packs.isEmpty()) {
            names.add("No icon packs installed");
        }

        new AlertDialog.Builder(this)
                .setTitle("Icon pack")
                .setItems(names.toArray(new String[names.size()]),
                        new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which >= ids.size()) return;
                        draft.iconPack = ids.get(which);
                        commit();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ clock

    private void buildClock() {
        header("Clock");
        LinearLayout card = card();

        row(card, "Face", label(Theme.FACE_LABELS, Theme.FACES, draft.clockFace),
                new View.OnClickListener() {
            @Override public void onClick(View v) {
                choose("Clock face", Theme.FACE_LABELS, Theme.FACES, draft.clockFace,
                        new Picked() {
                    @Override public void onPick(String value) {
                        draft.clockFace = value;
                        commit();
                    }
                });
            }
        });
        divider(card);

        row(card, "Layout", label(Theme.LAYOUT_LABELS, Theme.LAYOUTS, draft.clockLayout),
                new View.OnClickListener() {
            @Override public void onClick(View v) {
                choose("Clock layout", Theme.LAYOUT_LABELS, Theme.LAYOUTS, draft.clockLayout,
                        new Picked() {
                    @Override public void onPick(String value) {
                        draft.clockLayout = value;
                        commit();
                    }
                });
            }
        });
        divider(card);

        row(card, "Alignment", label(Theme.ALIGN_LABELS, Theme.ALIGNS, draft.clockAlign),
                new View.OnClickListener() {
            @Override public void onClick(View v) {
                choose("Clock alignment", Theme.ALIGN_LABELS, Theme.ALIGNS, draft.clockAlign,
                        new Picked() {
                    @Override public void onPick(String value) {
                        draft.clockAlign = value;
                        commit();
                    }
                });
            }
        });
        divider(card);

        slider(card, "Clock size", Math.round(draft.clockScale * 100f), 70, 140, "%",
                new SliderPicked() {
            @Override public void onPick(int value) {
                draft.clockScale = value / 100f;
                commit();
            }
        });
        divider(card);

        slider(card, "Letter spacing", Math.round(draft.clockSpacing * 1000f), -50, 300, "",
                new SliderPicked() {
            @Override public void onPick(int value) {
                draft.clockSpacing = value / 1000f;
                commit();
            }
        });
    }

    // ------------------------------------------------------------------ colours

    private void buildColours() {
        header("Colours");
        LinearLayout card = card();

        swatches(card, "Accent", ACCENTS, draft.accent, new SwatchPicked() {
            @Override public void onPick(int colour) {
                draft.accent = colour;
                commit();
            }
        });
        divider(card);

        swatches(card, "Background", BACKGROUNDS, draft.background, new SwatchPicked() {
            @Override public void onPick(int colour) {
                draft.background = colour;
                draft.light = luminance(colour) > 0.55f;
                draft.foreground = draft.light ? 0xFF15171C : 0xFFEDF1F7;
                draft.mid = draft.light ? 0xB015171C : 0xB4B4BECC;
                draft.dim = draft.light ? 0x7015171C : 0x7C7C8797;
                commit();
            }
        });
        divider(card);

        row(card, "Light theme", draft.light ? "On" : "Off", new View.OnClickListener() {
            @Override public void onClick(View v) {
                draft.light = !draft.light;
                draft.foreground = draft.light ? 0xFF15171C : 0xFFEDF1F7;
                draft.mid = draft.light ? 0xB015171C : 0xB4B4BECC;
                draft.dim = draft.light ? 0x7015171C : 0x7C7C8797;
                commit();
            }
        });
    }

    private static float luminance(int c) {
        return (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF))
                / 255f;
    }

    // ------------------------------------------------------------------ elements

    private void buildElements() {
        header("On screen");
        LinearLayout card = card();

        toggle(card, "Date", "Line under the clock", draft.showDate, new TogglePicked() {
            @Override public void onPick(boolean on) {
                draft.showDate = on;
                commit();
            }
        });
        divider(card);
        toggle(card, "Weather", "Temperature in Celsius", draft.showWeather, new TogglePicked() {
            @Override public void onPick(boolean on) {
                draft.showWeather = on;
                commit();
            }
        });
        divider(card);
        toggle(card, "Favorite labels", "Name under each pinned app", draft.showLabels,
                new TogglePicked() {
            @Override public void onPick(boolean on) {
                draft.showLabels = on;
                commit();
            }
        });
        divider(card);
        toggle(card, "Alphabet rail", "The letters on the edge", draft.showRail,
                new TogglePicked() {
            @Override public void onPick(boolean on) {
                draft.showRail = on;
                commit();
            }
        });
    }

    private void buildActions() {
        header("Keep it");
        LinearLayout card = card();

        action(card, "Save as theme", "Name it and keep it in the picker",
                new View.OnClickListener() {
            @Override public void onClick(View v) { askName(); }
        });
        divider(card);
        action(card, "Export as .zip", "Write it to Download", new View.OnClickListener() {
            @Override public void onClick(View v) { export(); }
        });
        divider(card);
        action(card, "Reset to Star Trail", "Back to the shipped look",
                new View.OnClickListener() {
            @Override public void onClick(View v) {
                Theme.apply(ThemeEditorActivity.this, Theme.BUILTIN);
                draft = Theme.current(ThemeEditorActivity.this);
                previewIcons = null;
                rebuild();
                Typefaces.apply(ThemeEditorActivity.this, scroll);
            }
        });
    }

    private void askName() {
        final EditText input = new EditText(this);
        input.setText(draft.name);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setTextColor(FG);
        input.setPadding(dp(20), dp(12), dp(20), dp(12));

        new AlertDialog.Builder(this)
                .setTitle("Save as theme")
                .setView(input)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) name = "My theme";
                        Theme saved = Theme.save(ThemeEditorActivity.this, draft, name);
                        if (saved == null) {
                            toast("Could not write the theme");
                            return;
                        }
                        draft = saved;
                        rebuild();
                        toast(name + " saved");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void export() {
        File dir = new File("/storage/emulated/0/Download");
        if (!dir.isDirectory()) dir = getExternalFilesDir(null);
        if (dir == null) {
            toast("No writable folder");
            return;
        }
        File out = new File(dir, slug(draft.name) + "-theme.zip");
        if (!Theme.exportZip(this, draft, out)) {
            toast("Export failed");
            return;
        }
        toast("Written to " + out.getAbsolutePath());
    }

    private static String slug(String name) {
        StringBuilder sb = new StringBuilder();
        String base = name == null ? "theme" : name.toLowerCase(Locale.ENGLISH);
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) sb.append(c);
            else if (c == ' ' || c == '-' || c == '_') sb.append('-');
        }
        return sb.length() == 0 ? "theme" : sb.toString();
    }

    // ------------------------------------------------------------------ commit

    /** Every control lands here: prefs, preview, and the home view behind this screen. */
    private void commit() {
        Theme.store(this, draft);
        Theme.syncVisibility(this, draft);
        palette();
        if (preview != null) preview.invalidate();
        scroll.setBackgroundColor(BG);
        rebuild();
    }

    // ------------------------------------------------------------------ results

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            Theme imported = Theme.importZip(this, uri, uri.getLastPathSegment());
            Theme.apply(this, imported.id);
            draft = Theme.current(this);
            previewIcons = null;
            rebuild();
            Typefaces.apply(this, scroll);
            toast(imported.name + " imported and applied");
        } catch (Exception ex) {
            toast("That zip is not a theme: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ pieces

    private void header(String name) {
        TextView h = new TextView(this);
        h.setText(name.toUpperCase(Locale.ENGLISH));
        h.setTextSize(10.5f);
        h.setLetterSpacing(0.16f);
        h.setTextColor(ACCENT);
        h.setPadding(dp(6), dp(24), 0, dp(9));
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
        row.setMinimumHeight(dp(56));
        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void labels(LinearLayout parent, String title, String summary) {
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
    }

    private void row(LinearLayout card, String name, String value, View.OnClickListener click) {
        LinearLayout row = rowShell(card);
        labels(row, name, null);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(12.5f);
        v.setTextColor(DIM);
        v.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(v);

        TextView chev = new TextView(this);
        chev.setText("\u203A");
        chev.setTextSize(16f);
        chev.setTextColor(0xFF5C6577);
        chev.setPadding(dp(8), 0, 0, 0);
        row.addView(chev);

        row.setOnClickListener(click);
    }

    private void action(LinearLayout card, String name, String summary,
                        View.OnClickListener click) {
        LinearLayout row = rowShell(card);
        labels(row, name, summary);
        row.setOnClickListener(click);
    }

    private void toggle(LinearLayout card, String name, String summary, boolean value,
                        final TogglePicked picked) {
        LinearLayout row = rowShell(card);
        labels(row, name, summary);

        Switch sw = new Switch(this);
        sw.setChecked(value);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setTrackTintList(new ColorStateList(states, new int[]{0x66FFFFFF, 0xFF2A323D}));
        sw.setThumbTintList(new ColorStateList(states, new int[]{ACCENT, 0xFF7A8496}));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                picked.onPick(on);
            }
        });
        row.addView(sw);
    }

    private void slider(LinearLayout card, String name, int value, final int min, final int max,
                        final String suffix, final SliderPicked picked) {
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

        final TextView chip = new TextView(this);
        chip.setText(value + suffix);
        chip.setTextSize(11.5f);
        chip.setTextColor(ACCENT);
        chip.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1AFFFFFF);
        bg.setCornerRadius(dp(8));
        chip.setBackground(bg);
        chip.setPadding(dp(9), dp(3), dp(9), dp(3));
        top.addView(chip);
        block.addView(top);

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, value - min));
        bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        bar.setThumbTintList(ColorStateList.valueOf(ACCENT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF2A323D));
        bar.setPadding(dp(2), dp(10), dp(2), dp(6));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                int v = min + progress;
                chip.setText(v + suffix);
                if (fromUser) picked.onPick(v);
            }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { }
        });
        block.addView(bar);
    }

    private void swatches(LinearLayout card, String name, final String[] palette,
                          int current, final SwatchPicked picked) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(16), dp(13), dp(16), dp(14));
        card.addView(block);

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(14.5f);
        title.setTextColor(FG);
        block.addView(title);

        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(0, dp(10), 0, 0);

        for (int i = 0; i < palette.length; i++) {
            final int colour = Color.parseColor(palette[i]);
            View dot = new View(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(colour);
            bg.setStroke(dp(2), colour == current ? FG : 0x33FFFFFF);
            dot.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.rightMargin = dp(9);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { picked.onPick(colour); }
            });
            strip.addView(dot, lp);
        }
        block.addView(strip);
    }

    private void choose(String title, String[] names, final String[] values, String current,
                        final Picked picked) {
        int at = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) at = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(names, at, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        picked.onPick(values[which]);
                        d.dismiss();
                    }
                })
                .show();
    }

    private static String label(String[] labels, String[] values, String value) {
        for (int i = 0; i < values.length && i < labels.length; i++) {
            if (values[i].equals(value)) return labels[i];
        }
        return labels.length > 0 ? labels[0] : value;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ interfaces

    private interface Picked { void onPick(String value); }
    private interface TogglePicked { void onPick(boolean on); }
    private interface SliderPicked { void onPick(int value); }
    private interface SwatchPicked { void onPick(int colour); }

    // ------------------------------------------------------------------ preview

    /**
     * The top card: the draft theme drawn small, with the real icons of the first few
     * apps so an icon pack and a shape show up before anything is applied.
     */
    private final class Preview extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<AppEntry> samples = new ArrayList<AppEntry>();
        private String appliedKey = "";

        Preview(Activity a) {
            super(a);
            for (int i = 0; i < repo.apps().size() && samples.size() < 4; i++) {
                samples.add(repo.apps().get(i));
            }
        }

        /**
         * One loader for the whole editor. The theme is pushed into it only when the
         * draft actually changed: clearing the icon cache on every frame would make each
         * draw schedule the next one and spin.
         */
        private IconLoader loader() {
            if (previewIcons == null) {
                previewIcons = new IconLoader(ThemeEditorActivity.this, dp(40));
                appliedKey = "";
            }
            String key = draft.toJson().toString();
            if (!key.equals(appliedKey)) {
                appliedKey = key;
                previewIcons.setTheme(draft);
            }
            return previewIcons;
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(draft.background);
            canvas.drawRoundRect(new RectF(0, 0, w, h), dp(18), dp(18), paint);

            String time = new java.text.SimpleDateFormat("HH:mm", Locale.ENGLISH)
                    .format(new java.util.Date());
            String date = new java.text.SimpleDateFormat("EEE, MMM d", Locale.ENGLISH)
                    .format(new java.util.Date());

            boolean centre = "center".equals(draft.clockAlign);
            boolean right = "right".equals(draft.clockAlign);
            float anchor = centre ? w / 2f : right ? w - dp(16) : dp(18);
            Paint.Align align = centre ? Paint.Align.CENTER
                    : right ? Paint.Align.RIGHT : Paint.Align.LEFT;

            text.setTypeface(Theme.faceTypeface(draft.clockFace));
            text.setTextAlign(align);
            text.setTextSize(dp(34 * draft.clockScale));
            text.setLetterSpacing(draft.clockSpacing);
            text.setColor(draft.foreground);
            text.setAlpha(246);

            float clockY;
            if ("stacked".equals(draft.clockLayout)) {
                if (draft.showDate) {
                    text.setTextSize(dp(9));
                    text.setLetterSpacing(0.18f);
                    text.setAlpha(180);
                    canvas.drawText(date.toUpperCase(Locale.ENGLISH), anchor, dp(34), text);
                }
                text.setTypeface(Theme.faceTypeface(draft.clockFace));
                text.setTextSize(dp(38 * draft.clockScale));
                text.setLetterSpacing(draft.clockSpacing);
                text.setAlpha(246);
                clockY = dp(76);
                canvas.drawText(time, anchor, clockY, text);
            } else {
                clockY = dp(62);
                canvas.drawText(time, anchor, clockY, text);
                if (draft.showDate && "inline".equals(draft.clockLayout)) {
                    float tw = text.measureText(time);
                    float x = centre ? anchor + tw / 2f + dp(9)
                            : right ? anchor - tw - dp(9) : anchor + tw + dp(9);
                    text.setTextSize(dp(11));
                    text.setLetterSpacing(0f);
                    text.setAlpha(200);
                    canvas.drawText(date, x, clockY, text);
                } else if (draft.showDate && !"time".equals(draft.clockLayout)) {
                    text.setTextSize(dp(11));
                    text.setLetterSpacing(0f);
                    text.setAlpha(200);
                    canvas.drawText(date, anchor, clockY + dp(20), text);
                }
            }
            text.setLetterSpacing(0f);

            if (draft.showWeather && !"time".equals(draft.clockLayout)) {
                text.setTextSize(dp(10));
                text.setColor(draft.accent);
                text.setAlpha(215);
                canvas.drawText("27\u00b0C", anchor,
                        ("stacked".equals(draft.clockLayout) ? dp(100) : clockY + dp(38)), text);
            }

            // A row of real icons under the clock, so pack and shape are visible.
            IconLoader l = loader();
            int size = Math.round(dp(34));
            float x = dp(18);
            float y = h - dp(46);
            for (int i = 0; i < samples.size(); i++) {
                android.graphics.Bitmap bmp = l.get(samples.get(i), new IconLoader.Callback() {
                    @Override public void onIconReady() { postInvalidateOnAnimation(); }
                });
                if (bmp != null) {
                    canvas.drawBitmap(bmp, null,
                            new RectF(x, y, x + size, y + size), paint);
                } else {
                    paint.setColor(0x22FFFFFF);
                    canvas.drawRoundRect(new RectF(x, y, x + size, y + size), dp(8), dp(8), paint);
                }
                x += size + dp(12);
            }
        }
    }
}
