package com.termux.app.theme;

import android.app.Activity;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.termux.R;

/** Applies the Material 3 theme, accent palette and night mode chosen by the user. */
public final class M3ThemeHelper {

    public static final String PREF_ACCENT = "m3_accent";
    public static final String PREF_MODE = "m3_theme_mode";

    private static final String[] ACCENTS = {"purple", "blue", "teal", "green", "orange", "rose"};

    private M3ThemeHelper() {}

    /** Must be called before {@code super.onCreate()} of the activity. */
    public static void apply(Activity activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        String mode = prefs.getString(PREF_MODE, "system");

        if ("material_you".equals(mode)) {
            String wallpaperAccent = resolveWallpaperAccent(activity);
            if (!wallpaperAccent.equals(prefs.getString(PREF_ACCENT, "purple"))) {
                prefs.edit().putString(PREF_ACCENT, wallpaperAccent).apply();
            }
        }

        if ("light".equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if ("dark".equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }

        activity.setTheme(getThemeResId(prefs.getString(PREF_ACCENT, "purple")));
    }

    public static int getThemeResId(String accent) {
        if ("blue".equals(accent)) return R.style.Theme_TermuxApp_M3_Blue;
        if ("teal".equals(accent)) return R.style.Theme_TermuxApp_M3_Teal;
        if ("green".equals(accent)) return R.style.Theme_TermuxApp_M3_Green;
        if ("orange".equals(accent)) return R.style.Theme_TermuxApp_M3_Orange;
        if ("rose".equals(accent)) return R.style.Theme_TermuxApp_M3_Rose;
        return R.style.Theme_TermuxApp_M3_Purple;
    }

    /** Maps the wallpaper primary colour to the closest predefined tonal palette. */
    public static String resolveWallpaperAccent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WallpaperManager manager = WallpaperManager.getInstance(context);
            WallpaperColors colors = manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (colors != null && colors.getPrimaryColor() != null) {
                int argb = colors.getPrimaryColor().toArgb();
                int targetR = (argb >> 16) & 0xFF;
                int targetG = (argb >> 8) & 0xFF;
                int targetB = argb & 0xFF;
                int[] seeds = {0x6750A4, 0x0B57D0, 0x006A6A, 0x1B6C33, 0xA85000, 0xB3265E};
                int bestIndex = 0;
                long bestDistance = Long.MAX_VALUE;
                for (int i = 0; i < seeds.length; i++) {
                    int r = (seeds[i] >> 16) & 0xFF;
                    int g = (seeds[i] >> 8) & 0xFF;
                    int b = seeds[i] & 0xFF;
                    long dr = targetR - r, dg = targetG - g, db = targetB - b;
                    long distance = dr * dr + dg * dg + db * db;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestIndex = i;
                    }
                }
                return ACCENTS[bestIndex];
            }
        }
        return "purple";
    }
}
