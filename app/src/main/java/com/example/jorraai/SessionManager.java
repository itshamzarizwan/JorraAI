package com.example.jorraai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class SessionManager {
    private static final String PREFS = "app_prefs";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_TRY_ONS = "try_ons";

    public static void saveSession(Context ctx, String token, int tryOns) {
        String trimmed = token == null ? null : token.trim();
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_TOKEN, trimmed)   // store trimmed token
                .putInt(KEY_TRY_ONS, tryOns)
                .apply();
        Log.d("SESSION", "Saved token: " + trimmed + ", tryOns: " + tryOns);
    }

    public static String getToken(Context ctx) {
        String token = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TOKEN, null);
        String trimmed = token == null ? null : token.trim();
        Log.d("SESSION", "Retrieved token: " + trimmed);
        return trimmed;
    }

    public static int getTryOns(Context ctx) {
        int tryOns = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_TRY_ONS, 0);
        Log.d("SESSION", "Retrieved tryOns: " + tryOns);
        return tryOns;
    }

    public static void setTryOns(Context ctx, int tryOns) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_TRY_ONS, tryOns).apply();
        Log.d("SESSION", "Updated tryOns: " + tryOns);
    }

    public static void clear(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        Log.d("SESSION", "Session cleared");
    }
}
