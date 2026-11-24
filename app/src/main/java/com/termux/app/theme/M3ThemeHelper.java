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

    private static final String[] ACCENTS = {"purple", "blue", "teal", "green", "orange", "rose"};
    private static final int[] SEEDS = {0x6750A4, 0x0B57D0, 0x006A6A, 0x1B6C33, 0xA85000, 0xB3265E};

    private M3ThemeHelper() {}

    public static void applyMode(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = prefs.getString(PREF_MODE, "system");
        int desired = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if ("light".equals(mode)) desired = AppCompatDelegate.MODE_NIGHT_NO;
        else if ("dark".equals(mode)) desired = AppCompatDelegate.MODE_NIGHT_YES;
        if (AppCompatDelegate.getDefaultNightMode() != desired)
            AppCompatDelegate.setDefaultNightMode(desired);
    }

    /** Must be called before {@code super.onCreate()} of the activity. */
    public static void apply(Activity activity) {
        applyMode(activity);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        String accent = prefs.getString(PREF_ACCENT, "purple");
        String mode = prefs.getString(PREF_MODE, "system");
        if ("material_you".equals(mode)) {
            String seed = prefs.getString(PREF_SEED_COLOR, null);
            Integer argb = null;
            if (seed != null) { try { argb = (int) Long.parseLong(seed, 16); } catch (NumberFormatException ignored) {} }
            if (argb == null) argb = getWallpaperPrimaryColor(activity);
            if (argb != null) accent = ACCENTS[nearestAccentIndex(argb)];
        }
        activity.setTheme(getThemeResId(accent));
    }

    public interface M3ThemeAware { void onM3ThemeChanged(); }

    public static int getThemeResId(String accent) {
        if ("blue".equals(accent)) return R.style.Theme_TermuxApp_M3_Blue;
        if ("teal".equals(accent)) return R.style.Theme_TermuxApp_M3_Teal;
        if ("green".equals(accent)) return R.style.Theme_TermuxApp_M3_Green;
        if ("orange".equals(accent)) return R.style.Theme_TermuxApp_M3_Orange;
        if ("rose".equals(accent)) return R.style.Theme_TermuxApp_M3_Rose;
        return R.style.Theme_TermuxApp_M3_Purple;
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
