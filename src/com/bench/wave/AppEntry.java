package com.bench.wave;

import android.content.ComponentName;

/** One launchable activity, flattened to what the home view draws. */
final class AppEntry {
    final String label;
    final String pkg;
    final String cls;
    final String key;
    final String section;
    final ComponentName component;

    AppEntry(String label, ComponentName component) {
        this.label = label;
        this.component = component;
        this.pkg = component.getPackageName();
        this.cls = component.getClassName();
        this.key = pkg + "/" + cls;
        this.section = sectionOf(label);
    }

    private static String sectionOf(String label) {
        if (label == null || label.isEmpty()) return "#";
        char c = Character.toUpperCase(label.charAt(0));
        if (c >= 'A' && c <= 'Z') return String.valueOf(c);
        return "#";
    }

    @Override public String toString() { return label; }
}
