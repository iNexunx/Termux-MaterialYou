package com.termux.app.fragments.settings;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import com.termux.app.fragments.settings.M3PreferenceFragment;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.theme.M3ThemeHelper;

@Keep
public class M3AppearancePreferencesFragment extends M3PreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.m3_appearance_preferences, rootKey);

        bindSummary("m3_accent");
        bindSummary("m3_language");
        bindSummary("terminal_font");
        bindSummary("terminal_color_scheme");
        bindTerminalViewSwitch("terminal_margin_adjustment");
        bindTerminalViewSwitch("extra_keys_floating");
    }

    private void bindTerminalViewSwitch(String key) {
        androidx.preference.SwitchPreferenceCompat p = findPreference(key);
        if (p == null) return;
        com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences prefs = com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.build(requireContext(), true);
        if (prefs == null) return;
        if ("terminal_margin_adjustment".equals(key)) p.setChecked(prefs.isTerminalMarginAdjustmentEnabled());
        if ("extra_keys_floating".equals(key)) p.setChecked(prefs.isExtraKeysFloating());
        p.setOnPreferenceChangeListener((pref, newValue) -> {
            boolean v = Boolean.TRUE.equals(newValue);
            com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences pr = com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.build(requireContext(), true);
            if (pr == null) return false;
            if ("terminal_margin_adjustment".equals(key)) pr.setTerminalMarginAdjustment(v);
            if ("extra_keys_floating".equals(key)) pr.setExtraKeysFloating(v);
            if (getActivity() instanceof com.termux.app.TermuxActivity) ((com.termux.app.TermuxActivity) getActivity()).reloadTerminalStyling();
            return true;
        });
    }

    private void bindSummary(String key) {
        ListPreference preference = findPreference(key);
        if (preference == null) return;

        preference.setSummaryProvider(new Preference.SummaryProvider<ListPreference>() {
            @Override
            public CharSequence provideSummary(ListPreference p) {
                return p.getEntry() != null ? p.getEntry() : "";
            }
        });

        preference.setOnPreferenceChangeListener((p, newValue) -> {
            if (!isAdded()) return true;
            String val = String.valueOf(newValue);
            if ("m3_accent".equals(key)) {
                String current = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(M3ThemeHelper.PREF_ACCENT, "default");
                if (val.equals(current)) return false;
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putString(M3ThemeHelper.PREF_ACCENT, val)
                    .putString(M3ThemeHelper.PREF_SEED_COLOR, M3ThemeHelper.getSeedColor(val))
                    .apply();
                applyThemeNow(val, false);
                return false;
            }
            if ("m3_language".equals(key)) {
                String current = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(M3ThemeHelper.PREF_LANGUAGE, "system");
                if (val.equals(current)) return false;
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().putString(M3ThemeHelper.PREF_LANGUAGE, val).apply();
                if (getActivity() != null) {
                android.view.Window w = getActivity().getWindow(); if (w!=null){w.setWindowAnimations(0); android.graphics.drawable.Drawable bg=w.getDecorView().getBackground(); if(bg!=null) w.setBackgroundDrawable(bg);} 
                getActivity().recreate();
                try { getActivity().overridePendingTransition(0,0);} catch(Throwable ignored){}
            }
                return false;
            }
            if ("terminal_font".equals(key) || "terminal_color_scheme".equals(key)) {
                String prefKey = "terminal_font".equals(key) ? com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.KEY_TERMINAL_FONT : com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.KEY_TERMINAL_COLOR_SCHEME;
                String current = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(prefKey, "terminal_font".equals(key) ? "monospace" : "termux");
                if (val.equals(current)) return false;
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().putString(prefKey, val).apply();
                // Apply immediately to running terminal
                if (getActivity() instanceof com.termux.app.TermuxActivity) {
                    ((com.termux.app.TermuxActivity) getActivity()).reloadTerminalStyling();
                }
                return true;
            }
            return true;
        });
    }

    private void applyThemeNow(String value, boolean isModeChange) {
        if (getActivity() == null) return;
        androidx.appcompat.app.AppCompatActivity a = (androidx.appcompat.app.AppCompatActivity) getActivity();
        if (isModeChange) {
            M3ThemeHelper.applyMode(a);
        }
        M3ThemeHelper.apply(a);
        android.view.Window w=a.getWindow(); if(w!=null){w.setWindowAnimations(0); android.graphics.drawable.Drawable bg=w.getDecorView().getBackground(); if(bg!=null) w.setBackgroundDrawable(bg);} 
        a.recreate(); try{a.overridePendingTransition(0,0);}catch(Throwable ignored){}
    }
}
