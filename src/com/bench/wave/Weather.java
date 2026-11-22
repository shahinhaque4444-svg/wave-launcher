package com.bench.wave;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Current temperature in Celsius, cached in prefs.
 *
 * Two requests: a coarse position from the network (no location permission, no GPS wake),
 * then Open-Meteo for the reading. Both are small, both time out quickly, and a failure
 * leaves the previous value on screen rather than blanking it.
 */
final class Weather {

    private static final String TAG = "WaveWeather";
    private static final long MAX_AGE_MS = 30L * 60L * 1000L;
    private static final int TIMEOUT_MS = 8000;

    private Weather() {}

    /** "23°C" from the cache, or "" when nothing has been fetched yet. */
    static String cached(Context c) {
        String raw = Prefs.sp(c).getString(Prefs.K_WEATHER, "");
        if (raw == null || raw.isEmpty()) return "";
        String[] parts = raw.split("\\|");
        if (parts.length < 1 || parts[0].isEmpty()) return "";
        return parts[0] + "\u00b0C";
    }

    static String place(Context c) {
        String raw = Prefs.sp(c).getString(Prefs.K_WEATHER, "");
        if (raw == null) return "";
        String[] parts = raw.split("\\|");
        return parts.length >= 2 ? parts[1] : "";
    }

    static boolean isStale(Context c) {
        String raw = Prefs.sp(c).getString(Prefs.K_WEATHER, "");
        if (raw == null || raw.isEmpty()) return true;
        String[] parts = raw.split("\\|");
        if (parts.length < 3) return true;
        try {
            return System.currentTimeMillis() - Long.parseLong(parts[2]) > MAX_AGE_MS;
        } catch (NumberFormatException ex) {
            return true;
        }
    }

    interface Done {
        void onWeather(String text);
    }

    /** Fetches off the main thread and answers on it. No-op when the cache is fresh. */
    static void refresh(final Context ctx, final boolean force, final Done done) {
        final Context app = ctx.getApplicationContext();
        if (!force && !isStale(app)) {
            if (done != null) done.onWeather(cached(app));
            return;
        }

        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                final String value = fetch(app);
                main.post(new Runnable() {
                    @Override public void run() {
                        if (done != null) done.onWeather(value == null ? cached(app) : value);
                    }
                });
            }
        }, "wave-weather").start();
    }

    private static String fetch(Context c) {
        // wttr.in answers with just "+27°C" for the caller's IP. No key, no coordinates.
        String quick = get("https://wttr.in/?format=%t");
        if (quick != null) {
            String value = degrees(quick);
            if (value != null) return store(c, value);
        }

        // Fallback: coarsely locate the IP, then ask Open-Meteo for the reading.
        double[] at = position(c);
        if (at == null) return null;

        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + at[0]
                + "&longitude=" + at[1] + "&current=temperature_2m";
        String body = get(url);
        if (body == null) return null;

        try {
            JSONObject root = new JSONObject(body);
            JSONObject current = root.optJSONObject("current");
            if (current == null) return null;
            double temp = current.optDouble("temperature_2m", Double.NaN);
            if (Double.isNaN(temp)) return null;
            return store(c, String.valueOf(Math.round(temp)));
        } catch (Exception ex) {
            Log.w(TAG, "bad forecast", ex);
            return null;
        }
    }

    /** Pulls the number out of "+27°C" or "27 °C". */
    private static String degrees(String raw) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < raw.length() && digits.length() < 4; i++) {
            char ch = raw.charAt(i);
            if (ch == '-' || (ch >= '0' && ch <= '9')) digits.append(ch);
            else if (digits.length() > 0) break;
        }
        if (digits.length() == 0) return null;
        try {
            Integer.parseInt(digits.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
        return digits.toString();
    }

    private static String store(Context c, String value) {
        String place = Prefs.sp(c).getString(Prefs.K_WEATHER_PLACE, "");
        Prefs.sp(c).edit()
                .putString(Prefs.K_WEATHER, value + "|" + place + "|"
                        + System.currentTimeMillis())
                .apply();
        return value + "\u00b0C";
    }

    /** Coarse latitude and longitude, or null. Cached with the reading's timestamp. */
    private static double[] position(Context c) {
        String body = get("https://ipwho.is/");
        if (body == null) return null;
        try {
            JSONObject root = new JSONObject(body);
            double lat = root.optDouble("latitude", Double.NaN);
            double lon = root.optDouble("longitude", Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) return null;
            String city = root.optString("city", "");
            if (!city.isEmpty()) {
                Prefs.sp(c).edit().putString(Prefs.K_WEATHER_PLACE, city).apply();
            }
            return new double[]{lat, lon};
        } catch (Exception ex) {
            return null;
        }
    }

    private static String get(String url) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "WaveLauncher/1.2");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return null;
            in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8 * 1024];
            int read;
            int total = 0;
            while ((read = in.read(buf)) > 0) {
                total += read;
                if (total > 256 * 1024) return null; // a weather reply is never this big
                out.write(buf, 0, read);
            }
            return out.toString("UTF-8");
        } catch (Exception ex) {
            Log.w(TAG, "request failed: " + url + " -> " + ex, ex);
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
            if (conn != null) conn.disconnect();
        }
    }
}
