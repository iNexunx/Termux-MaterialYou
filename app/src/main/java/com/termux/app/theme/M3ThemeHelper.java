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
    public static final String PREF_SEED_COLOR = "m3_seed_color";
    public static final String PREF_LANGUAGE = "m3_language";

    private static final String[] ACCENTS = {"default", "purple", "blue", "teal", "green", "orange", "rose", "lavender", "red", "amber", "cyan", "indigo"};
    private static final int[] SEEDS = {0x2E7D32, 0x6750A4, 0x0B57D0, 0x006A6A, 0x1B6C33, 0xA85000, 0xB3265E, 0x7B1FA2, 0xB3261E, 0xB26A00, 0x006874, 0x3F51B5};

    private M3ThemeHelper() {}

    public static void applyMode(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = prefs.getString(PREF_MODE, "system");
        int desired = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        String nightName = "system";
        if ("light".equals(mode)) { desired = AppCompatDelegate.MODE_NIGHT_NO; nightName = "false"; }
        else if ("dark".equals(mode)) { desired = AppCompatDelegate.MODE_NIGHT_YES; nightName = "true"; }
        else if ("material_you".equals(mode)) { desired = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; nightName = "system"; }
        if (AppCompatDelegate.getDefaultNightMode() != desired)
            AppCompatDelegate.setDefaultNightMode(desired);
        if (context instanceof androidx.appcompat.app.AppCompatActivity) {
            try { ((androidx.appcompat.app.AppCompatActivity) context).getDelegate().setLocalNightMode(desired); } catch (Throwable ignored) {}
        }
        try {
            Class<?> nm = Class.forName("com.termux.shared.theme.NightMode");
            nm.getMethod("setAppNightMode", String.class).invoke(null, nightName);
        } catch (Throwable ignored) {}
    }

    public static void applyLanguage(Context context) {
        // Persisted; applied via wrapContext/attachBaseContext for AppCompat 1.3 compatibility.
    }

    public static Context wrapContext(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String lang = prefs.getString(PREF_LANGUAGE, "system");
        if (lang == null || lang.isEmpty() || "system".equals(lang)) return context;
        java.util.Locale locale = java.util.Locale.forLanguageTag(lang);
        java.util.Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        return context.createConfigurationContext(config);
    }

    public static String getLanguage(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_LANGUAGE, "system");
    }

    /** Must be called before {@code super.onCreate()} of the activity. */
    public static void apply(Activity activity) {
        applyMode(activity);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        String accent = prefs.getString(PREF_ACCENT, "default");
        String mode = prefs.getString(PREF_MODE, "system");
        boolean dynamicMode = "material_you".equals(mode);
        boolean defaultAccent = accent == null || accent.isEmpty() || "default".equals(accent);
        // Always set a base M3 theme so custom attrs (m3PrimaryContainer, etc.) exist.
        if (dynamicMode) {
            activity.setTheme(R.style.Theme_TermuxApp_M3_Default);
            if (applyDynamicColorsIfAvailable(activity)) return;
            Integer argb = getWallpaperPrimaryColor(activity);
            if (argb != null) { activity.setTheme(getThemeResId(ACCENTS[nearestAccentIndex(argb)])); }
            return;
        }
        // Respect explicit accent selection; default follows system dynamic/wallpaper if available.
        if (defaultAccent) {
            activity.setTheme(R.style.Theme_TermuxApp_M3_Default);
            if (applyDynamicColorsIfAvailable(activity)) return;
            Integer argb = getWallpaperPrimaryColor(activity);
            if (argb != null) { activity.setTheme(getThemeResId(ACCENTS[nearestAccentIndex(argb)])); }
            return;
        }
        activity.setTheme(getThemeResId(accent));
    }

    private static boolean applyDynamicColorsIfAvailable(Activity activity) {
        if (Build.VERSION.SDK_INT < 31) return false;
        try {
            Class<?> dc = Class.forName("com.google.android.material.color.DynamicColors");
            java.lang.reflect.Method m = dc.getMethod("applyToActivityIfAvailable", Activity.class);
            m.invoke(null, activity);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public interface M3ThemeAware { void onM3ThemeChanged(); }

    public static int getThemeResId(String accent) {
        if ("blue".equals(accent)) return R.style.Theme_TermuxApp_M3_Blue;
        if ("teal".equals(accent)) return R.style.Theme_TermuxApp_M3_Teal;
        if ("green".equals(accent)) return R.style.Theme_TermuxApp_M3_Green;
        if ("orange".equals(accent)) return R.style.Theme_TermuxApp_M3_Orange;
        if ("rose".equals(accent)) return R.style.Theme_TermuxApp_M3_Rose;
        if ("lavender".equals(accent)) return R.style.Theme_TermuxApp_M3_Lavender;
        if ("red".equals(accent)) return R.style.Theme_TermuxApp_M3_Red;
        if ("amber".equals(accent)) return R.style.Theme_TermuxApp_M3_Amber;
        if ("cyan".equals(accent)) return R.style.Theme_TermuxApp_M3_Cyan;
        if ("indigo".equals(accent)) return R.style.Theme_TermuxApp_M3_Indigo;
        if ("default".equals(accent)) return R.style.Theme_TermuxApp_M3_Default;
        return R.style.Theme_TermuxApp_M3_Default;
    }

    public static String getSeedColor(String accent) {
        int index = 0;
        for (int i = 0; i < ACCENTS.length; i++) {
            if (ACCENTS[i].equals(accent)) {
                index = i;
                break;
            }
        }
        return String.format("%06X", SEEDS[index]);
    }

    private static Integer getWallpaperPrimaryColor(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WallpaperColors colors = WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (colors != null && colors.getPrimaryColor() != null)
                return colors.getPrimaryColor().toArgb();
        }
        return null;
    }

    private static int nearestAccentIndex(int argb) {
        int targetR = (argb >> 16) & 0xFF;
        int targetG = (argb >> 8) & 0xFF;
        int targetB = argb & 0xFF;
        int bestIndex = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < SEEDS.length; i++) {
            long dr = targetR - ((SEEDS[i] >> 16) & 0xFF);
            long dg = targetG - ((SEEDS[i] >> 8) & 0xFF);
            long db = targetB - (SEEDS[i] & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }
}
