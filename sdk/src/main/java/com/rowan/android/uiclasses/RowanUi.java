package com.rowan.android.uiclasses;

import android.util.Log;

public final class RowanUi {
    public static final String VERSION = BuildConfig.SDK_VERSION;
    private static final String TAG = "RowanUi";
    private static volatile boolean debug = false;

    private RowanUi() {}

    public static void setDebug(boolean enabled) {
        debug = enabled;
        if (enabled) Log.i(TAG, "RowanUi " + VERSION + " debug logging ON");
    }

    public static boolean isDebug() { return debug; }

    static void d(String area, String msg) { if (debug) Log.d(TAG, "[" + area + "] " + msg); }

    static void e(String area, String msg, Throwable t) { if (debug) Log.e(TAG, "[" + area + "] " + msg, t); }
}