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

        bindSummary("m3_theme_mode");
        bindSummary("m3_accent");
        bindSummary("m3_language");
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
            if ("m3_theme_mode".equals(key)) {
                String current = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(M3ThemeHelper.PREF_MODE, "system");
                if (val.equals(current)) return false;
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().putString(M3ThemeHelper.PREF_MODE, val).apply();
                applyThemeNow(val, true);
                return false;
            }
            if ("m3_language".equals(key)) {
                String current = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(M3ThemeHelper.PREF_LANGUAGE, "system");
                if (val.equals(current)) return false;
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().putString(M3ThemeHelper.PREF_LANGUAGE, val).apply();
                if (getActivity() != null) { getActivity().getWindow().setWindowAnimations(0); getActivity().recreate(); }
                return false;
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
        a.getWindow().setWindowAnimations(0);
        a.recreate();
    }
}
