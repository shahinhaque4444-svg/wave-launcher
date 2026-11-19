package com.bench.wave;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

/**
 * The only thing this service does is lock the screen. Android offers no other way for a
 * launcher to turn the display off, so the gesture is gated behind the accessibility
 * toggle the user flips once in system settings.
 */
public class WaveLockService extends AccessibilityService {

    private static volatile WaveLockService connected;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = this;
    }

    @Override public boolean onUnbind(Intent intent) {
        if (connected == this) connected = null;
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        if (connected == this) connected = null;
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Nothing to observe; the service exists for performGlobalAction only.
    }

    @Override public void onInterrupt() { }

    /** Pulls the notification shade down. Same gate as the lock: no other door exists. */
    static boolean notifications() {
        WaveLockService service = connected;
        if (service == null) return false;
        try {
            return service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True when the gesture actually locked the device. */
    static boolean lock() {
        WaveLockService service = connected;
        if (service == null || Build.VERSION.SDK_INT < 28) return false;
        try {
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Whether Wave is ticked in Settings > Accessibility. */
    static boolean enabled(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || enabled.isEmpty()) return false;
        String shortName = c.getPackageName() + "/." + WaveLockService.class.getSimpleName();
        String fullName = c.getPackageName() + "/" + WaveLockService.class.getName();
        return enabled.contains(shortName) || enabled.contains(fullName);
    }

    static void openSettings(Context c) {
        try {
            c.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
            // No accessibility screen on this build; the toggle simply stays off.
        }
    }
}
