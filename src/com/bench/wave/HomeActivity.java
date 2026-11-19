package com.bench.wave;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The home screen activity. Owns the repo, the drawn home view and the search sheet. */
public class HomeActivity extends Activity implements HomeView.Host {

    private AppRepo repo;
    private HomeView home;
    private FrameLayout root;

    private FrameLayout searchPanel;
    private LinearLayout searchColumn;
    private EditText searchInput;
    private ListView searchList;
    private SearchAdapter adapter;
    private IconLoader searchIcons;
    private final List<AppEntry> results = new ArrayList<AppEntry>();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean appsLoaded;
    private boolean searchVisible;
    private boolean panelDark;
    private long resumedAt;
    private long libraryRevision;
    private boolean reorderPending;

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            appsLoaded = false;
            loadApps();
        }
    };

    private static final String ACTION_UNINSTALLED = "com.bench.wave.UNINSTALLED";

    /**
     * The package installer answers here. ACTION_DELETE is a request to the system
     * uninstaller and gives no result at all; a session callback does, so the list can be
     * refreshed the moment the package is really gone.
     */
    private final BroadcastReceiver uninstallWatcher = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
            if (status == PackageInstaller.STATUS_SUCCESS) {
                appsLoaded = false;
                loadApps();
            }
        }
    };

    private final BroadcastReceiver packageWatcher = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            main.removeCallbacks(refreshTask);
            main.postDelayed(refreshTask, 350);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        repo = new AppRepo(this);
        libraryRevision = Prefs.libraryRevision(this);
        searchIcons = new IconLoader(this, (int) dp(96));

        root = new FrameLayout(this);
        home = new HomeView(this, repo, this);
        root.addView(home, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildSearchPanel();
        root.addView(searchPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        searchPanel.setVisibility(View.GONE);

        setContentView(root);
        applyBarIcons();

        // The uninstall callback can land while the launcher is behind the system dialog,
        // so this receiver lives for the whole activity, not just the visible window.
        IntentFilter uninstallFilter = new IntentFilter(ACTION_UNINSTALLED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(uninstallWatcher, uninstallFilter, 4); // RECEIVER_NOT_EXPORTED
        } else {
            registerReceiver(uninstallWatcher, uninstallFilter);
        }

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumedAt = android.os.SystemClock.elapsedRealtime();
        applyBarIcons();
        applyPanelTheme();
        applySearchInsets();
        long revision = Prefs.libraryRevision(this);
        if (revision != libraryRevision) {
            // Settings or a package event changed the library: rebuild from the source of truth.
            libraryRevision = revision;
            appsLoaded = false;
        }
        if (reorderPending) {
            reorderPending = false;
            repo.reorder();
        }
        home.markDirty();
        if (!appsLoaded) loadApps();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(uninstallWatcher);
        } catch (Throwable ignored) {
            // never registered, or already torn down
        }
        worker.shutdownNow();
        main.removeCallbacks(refreshTask);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(packageWatcher, f);

    }

    @Override
    protected void onStop() {
        try {
            unregisterReceiver(packageWatcher);
        } catch (Throwable ignored) {
            // receiver already gone; nothing to unwind
        }
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home key and "back to the launcher" both land here. The home screen is the
        // answer, always: search is something the user asks for, not something that
        // ambushes them on the way out of an app.
        if (searchVisible) closeSearch();
        if (home != null) home.goHome();
    }

    @Override
    public void onBackPressed() {
        if (searchVisible) {
            closeSearch();
            return;
        }
        if (home != null && home.isInAppList()) {
            home.goHome();
            return;
        }
        // A launcher does not exit on back.
    }

    // ---------------------------------------------------------------- loading

    private void loadApps() {
        worker.execute(new Runnable() {
            @Override public void run() {
                repo.load();
                main.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        appsLoaded = true;
                        home.markDirty();
                        if (searchVisible && searchInput != null) filter(searchInput.getText().toString());
                    }
                });
            }
        });
    }

    private void applyBarIcons() {
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (Prefs.dark(this)) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    // ---------------------------------------------------------------- search

    private void buildSearchPanel() {
        panelDark = Prefs.dark(this);
        searchPanel = new FrameLayout(this);
        searchPanel.setBackgroundColor(Color.TRANSPARENT);
        searchPanel.setClickable(true);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int side = (int) dp(40);
        column.setPadding(side, (int) dp(12) + statusHeight(), side, 0);
        searchColumn = column;

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setBackgroundColor(Color.TRANSPARENT);
        inputRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(48)));

        searchInput = new EditText(this);
        searchInput.setTypeface(Typefaces.regular(this));
        searchInput.setSingleLine(true);
        searchInput.setHint("Search apps");
        searchInput.setTextSize(18.5f);
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        searchInput.setPadding((int) dp(8), 0, 0, 0);
        inputRow.addView(searchInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView dots = new TextView(this);
        dots.setTypeface(Typefaces.regular(this));
        dots.setText("\u22ee");
        dots.setTextSize(20f);
        dots.setGravity(Gravity.CENTER);
        dots.setPadding(0, 0, 0, (int) dp(8));
        dots.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSearchMenu(v); }
        });
        inputRow.addView(dots, new LinearLayout.LayoutParams((int) dp(46), (int) dp(48)));

        column.addView(inputRow);

        searchList = new ListView(this);
        searchList.setDivider(null);
        searchList.setDividerHeight(0);
        searchList.setSelector(android.R.color.transparent);
        searchList.setBackgroundColor(Color.TRANSPARENT);
        searchList.setCacheColorHint(Color.TRANSPARENT);
        searchList.setPadding(0, (int) dp(9), 0, (int) dp(12));
        searchList.setClipToPadding(false);
        searchList.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        column.addView(searchList, lp);

        searchPanel.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        adapter = new SearchAdapter();
        searchList.setAdapter(adapter);
        searchList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= results.size()) return;
                AppEntry e = results.get(position);
                launch(e);
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });

        searchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH && !results.isEmpty()) {
                    launch(results.get(0));
                    return true;
                }
                return false;
            }
        });

        applyPanelTheme();
    }

    /** Overflow next to the search field: the two things this screen can lead to. */
    private void showSearchMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Launcher settings");
        menu.getMenu().add("Wallpaper & style");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(MenuItem item) {
                closeSearch();
                if ("Launcher settings".equals(item.getTitle().toString())) {
                    startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
                } else {
                    try {
                        startActivity(new Intent(Intent.ACTION_SET_WALLPAPER)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (ActivityNotFoundException ignored) {
                        startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                }
                return true;
            }
        });
        menu.show();
    }

    /** The status bar dimen on this ROM is taller than the real inset; measure instead. */
    private void applySearchInsets() {
        if (searchColumn == null || root == null) return;
        root.post(new Runnable() {
            @Override public void run() {
                WindowInsets wi = root.getRootWindowInsets();
                if (wi == null) return;
                int top = wi.getSystemWindowInsetTop();
                if (top <= 0) return;
                int side = (int) dp(40);
                searchColumn.setPadding(side, top + (int) dp(12), side, 0);
            }
        });
    }

    private void applyPanelTheme() {
        if (searchPanel == null) return;
        boolean isDark = Prefs.dark(this);
        panelDark = isDark;
        searchPanel.setBackgroundColor(Color.TRANSPARENT);

        int fg = isDark ? 0xFFF2F5FA : 0xFF0C1116;
        int dim = isDark ? 0x8CF2F5FA : 0x880C1116;

        View row = searchInput == null ? null : (View) searchInput.getParent();
        if (row != null) {
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(dp(24));
            pill.setColor(isDark ? 0x14FFFFFF : 0x10000000);
            row.setBackground(pill);
        }
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setTextColor(fg);
        searchInput.setHintTextColor(dim);

        searchList.setBackgroundColor(Color.TRANSPARENT);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    public void openSearch() {
        if (searchPanel.getVisibility() == View.VISIBLE) return;
        searchVisible = true;
        applyPanelTheme();
        filter("");
        home.setSearchMode(true);
        searchPanel.setVisibility(View.VISIBLE);
        searchPanel.setAlpha(0f);
        searchPanel.setTranslationY(dp(18));
        searchPanel.animate().alpha(1f).translationY(0f).setDuration(160).start();
        searchInput.setText("");
        searchInput.post(new Runnable() {
            @Override public void run() {
                searchInput.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void closeSearch() {
        if (!searchVisible) return;
        searchVisible = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        searchPanel.animate().alpha(0f).translationY(dp(18)).setDuration(140)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        searchPanel.setVisibility(View.GONE);
                        home.setSearchMode(false);
                        home.requestFocus();
                    }
                }).start();
    }

    private void filter(String query) {
        results.clear();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        List<AppEntry> apps = repo.apps();
        for (int i = 0; i < apps.size(); i++) {
            AppEntry e = apps.get(i);
            if (needle.isEmpty() || e.label.toLowerCase(Locale.getDefault()).contains(needle)) {
                results.add(e);
                if (results.size() >= 60) break;
            }
        }
        adapter.notifyDataSetChanged();
    }

    // ---------------------------------------------------------------- actions

    @Override
    public void launch(AppEntry e) {
        if (e == null) return;
        // Logged before the app opens: if the launcher is killed behind it, the order stands.
        Prefs.markUsed(this, e.key);
        reorderPending = true;
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(e.component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
            if (searchVisible) closeSearch();
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, e.label + " is no longer installed", Toast.LENGTH_SHORT).show();
            loadApps();
        } catch (SecurityException ex) {
            Toast.makeText(this, "Cannot open " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void lockScreen() {
        if (WaveLockService.lock()) return;
        if (WaveLockService.enabled(this)) {
            Toast.makeText(this, "Lock service is waking up \u2014 try again in a second",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Turn Wave Launcher on in Settings > Accessibility to lock from here",
                Toast.LENGTH_LONG).show();
        WaveLockService.openSettings(this);
    }

    @Override
    public void openNotifications() {
        if (WaveLockService.notifications()) return;
        if (WaveLockService.enabled(this)) {
            Toast.makeText(this, "Shade is waking up \u2014 pull again in a second",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Turn Wave Launcher on in Settings > Accessibility to pull the shade",
                Toast.LENGTH_LONG).show();
        WaveLockService.openSettings(this);
    }

    @Override
    public void openAppMenu(final AppEntry e) {
        if (e == null) return;

        final boolean fav = repo.isFavorite(e);
        final boolean custom = Prefs.iconOverride(this, e.key) != null;

        final Dialog sheet = new Dialog(this);
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window win = sheet.getWindow();
        win.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        win.setDimAmount(0.6f);
        win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        win.setGravity(Gravity.BOTTOM);

        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(px(8), 0, px(8), px(8));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF171C24);
        bg.setCornerRadii(new float[]{
                px(26), px(26), px(26), px(26), px(10), px(10), px(10), px(10)});
        card.setBackground(bg);
        card.setPadding(px(18), px(10), px(18), px(12));

        View handle = new View(this);
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(0x33FFFFFF);
        hb.setCornerRadius(px(2));
        handle.setBackground(hb);
        LinearLayout.LayoutParams handleLp =
                new LinearLayout.LayoutParams(px(38), px(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = px(2);
        handleLp.bottomMargin = px(14);
        card.addView(handle, handleLp);

        // Header: the app itself, so the sheet is anchored to something recognisable.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, px(14));

        final ImageView icon = new ImageView(this);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(px(46), px(46));
        iconLp.rightMargin = px(14);
        header.addView(icon, iconLp);
        android.graphics.Bitmap ready = searchIcons.get(e, new IconLoader.Callback() {
            @Override public void onIconReady() {
                android.graphics.Bitmap b = searchIcons.get(e, null);
                if (b != null) icon.setImageBitmap(b);
            }
        });
        if (ready != null) icon.setImageBitmap(ready);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(e.label);
        name.setTextSize(17f);
        name.setTextColor(0xFFF2F5F9);
        titles.addView(name);

        TextView pkg = new TextView(this);
        pkg.setText(e.pkg);
        pkg.setTextSize(11.5f);
        pkg.setTextColor(0xFF7E899B);
        pkg.setPadding(0, px(3), 0, 0);
        titles.addView(pkg);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(header);

        sheetRow(sheet, card, fav ? "Remove from favorites" : "Add to favorites",
                fav ? "Drop it from the home column" : "Pin it to the home column",
                0, new Runnable() {
            @Override public void run() {
                repo.toggleFavorite(e);
                home.markDirty();
            }
        });

        sheetRow(sheet, card, custom ? "Change custom icon" : "Set custom icon",
                "Use any image from storage", 0, new Runnable() {
            @Override public void run() { pickIcon(e); }
        });

        sheetRow(sheet, card, "App info", "Permissions, storage, notifications", 0,
                new Runnable() {
            @Override public void run() { openAppInfo(e); }
        });

        sheetRow(sheet, card, "Hide from app list", "Keeps it installed, out of the list", 0,
                new Runnable() {
            @Override public void run() {
                repo.hide(e);
                home.markDirty();
            }
        });

        if (custom) {
            sheetRow(sheet, card, "Reset icon", "Back to the real one", 0, new Runnable() {
                @Override public void run() {
                    Prefs.clearIconOverride(HomeActivity.this, e.key);
                    refreshIcons();
                }
            });
        }

        View gap = new View(this);
        LinearLayout.LayoutParams gapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(1));
        gapLp.topMargin = px(8);
        gapLp.bottomMargin = px(8);
        gap.setBackgroundColor(0x14FFFFFF);
        card.addView(gap, gapLp);

        sheetRow(sheet, card, "Uninstall", e.label + " will be removed from this device",
                0xFFFF6B6B, new Runnable() {
            @Override public void run() { uninstall(e); }
        });

        TextView cancel = new TextView(this);
        cancel.setText("Cancel");
        cancel.setTextSize(14.5f);
        cancel.setTextColor(0xFF8C97A8);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, px(14), 0, px(10));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sheet.dismiss(); }
        });
        card.addView(cancel);

        frame.addView(card);
        sheet.setContentView(frame);

        card.setTranslationY(px(48));
        card.setAlpha(0f);
        sheet.show();
        Window shown = sheet.getWindow();
        if (shown != null) {
            shown.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
        card.animate().translationY(0f).alpha(1f).setDuration(190)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    /** One action line: title, quiet subtitle, optional accent colour. */
    private void sheetRow(final Dialog sheet, LinearLayout parent, String title,
                          String subtitle, int titleColor, final Runnable action) {
        final boolean accent = titleColor == 0xFFFF6B6B;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(px(12), px(11), px(12), px(11));

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.TRANSPARENT);
        shape.setCornerRadius(px(14));
        row.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x22FFFFFF), shape, shape));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15f);
        t.setTextColor(accent ? 0xFFFF6B6B : 0xFFEDF1F7);
        row.addView(t);

        TextView d = new TextView(this);
        d.setText(subtitle);
        d.setTextSize(11.5f);
        d.setTextColor(accent ? 0xFF9E6A6A : 0xFF7E899B);
        d.setPadding(0, px(2), 0, 0);
        row.addView(d);

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sheet.dismiss();
                v.postDelayed(new Runnable() {
                    @Override public void run() { action.run(); }
                }, 140);
            }
        });
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void openAppInfo(AppEntry e) {
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + e.pkg));
            startActivity(i);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "No app settings on this device", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Uninstall through a package-installer session.
     *
     * ACTION_DELETE only hands the job to whichever app claims that intent, and on some
     * skins that hand-off returns without doing anything — the dialog never appears and
     * the package stays put. A session call goes straight to the installer, shows the
     * system prompt, and reports back through {@link #ACTION_UNINSTALLED}.
     */
    private void uninstall(AppEntry e) {
        try {
            PackageInstaller installer = getPackageManager().getPackageInstaller();
            Intent callback = new Intent(ACTION_UNINSTALLED).setPackage(getPackageName());
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= 0x02000000; // FLAG_MUTABLE
            PendingIntent pending = PendingIntent.getBroadcast(
                    this, e.pkg.hashCode(), callback, flags);
            installer.uninstall(e.pkg, pending.getIntentSender());
        } catch (Throwable primary) {
            try {
                Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + e.pkg));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Throwable fallback) {
                Toast.makeText(this, "This device will not uninstall apps from here",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    // ------------------------------------------------------------- custom icons

    private static final int REQ_ICON = 41;
    private AppEntry pendingIcon;

    private void pickIcon(AppEntry e) {
        pendingIcon = e;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_ICON);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "No file picker on this device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_ICON || resultCode != RESULT_OK || data == null) return;
        final Uri uri = data.getData();
        final AppEntry entry = pendingIcon;
        pendingIcon = null;
        if (uri == null || entry == null) return;
        final AppEntry target = entry;
        worker.execute(new Runnable() {
            @Override public void run() {
                final boolean ok = saveIcon(target, uri);
                main.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        if (ok) refreshIcons();
                        else Toast.makeText(HomeActivity.this, "Could not read that image",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private boolean saveIcon(AppEntry e, Uri uri) {
        java.io.InputStream in = null;
        java.io.FileOutputStream out = null;
        try {
            java.io.File dir = new java.io.File(getFilesDir(), "icons");
            if (!dir.exists() && !dir.mkdirs()) return false;
            java.io.File file = new java.io.File(dir,
                    Integer.toHexString(e.key.hashCode()) + ".png");
            in = getContentResolver().openInputStream(uri);
            if (in == null) return false;
            out = new java.io.FileOutputStream(file);
            byte[] buf = new byte[16 * 1024];
            int read;
            while ((read = in.read(buf)) > 0) out.write(buf, 0, read);
            out.flush();
            Prefs.setIconOverride(this, e.key, file.getAbsolutePath());
            return true;
        } catch (Exception ex) {
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
            try { if (out != null) out.close(); } catch (Exception ignored) { }
        }
    }

    private void refreshIcons() {
        home.reloadIcons();
        if (searchIcons != null) searchIcons.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    // ---------------------------------------------------------------- helpers

    private int px(float v) { return Math.round(dp(v)); }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private int statusHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private final class SearchAdapter extends BaseAdapter {
        @Override public int getCount() { return results.size(); }
        @Override public Object getItem(int position) { return results.get(position); }
        @Override public long getItemId(int position) { return results.get(position).key.hashCode(); }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Row row;
            if (convertView == null) {
                row = new Row();
                FrameLayout line = new FrameLayout(HomeActivity.this);
                line.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(57)));

                row.icon = new ImageView(HomeActivity.this);
                FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                        (int) dp(44), (int) dp(44), Gravity.LEFT | Gravity.CENTER_VERTICAL);
                iconLp.leftMargin = (int) dp(6);
                line.addView(row.icon, iconLp);

                row.name = new TextView(HomeActivity.this);
                row.name.setTypeface(Typefaces.apps(HomeActivity.this));
                row.name.setTextSize(15f);
                row.name.setSingleLine(true);
                row.name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                FrameLayout.LayoutParams nameLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.LEFT | Gravity.CENTER_VERTICAL);
                nameLp.leftMargin = (int) dp(64);
                nameLp.rightMargin = (int) dp(8);
                line.addView(row.name, nameLp);

                row.root = line;
                line.setTag(row);
                convertView = line;
            } else {
                row = (Row) convertView.getTag();
            }
            convertView.setLayoutParams(new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(57)));

            AppEntry e = results.get(position);
            boolean isDark = Prefs.dark(HomeActivity.this);
            row.name.setTextColor(isDark ? 0xFFF2F5FA : 0xFF0C1116);
            row.name.setText(e.label);

            android.graphics.Bitmap bmp = searchIcons.get(e, new IconLoader.Callback() {
                @Override public void onIconReady() { adapter.notifyDataSetChanged(); }
            });
            if (bmp != null) row.icon.setImageBitmap(bmp);
            else row.icon.setImageDrawable(null);
            return convertView;
        }
    }

    private static final class Row {
        FrameLayout root;
        ImageView icon;
        TextView name;
    }
}
