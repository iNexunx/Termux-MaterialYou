package com.termux.app.fragments.settings;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.theme.M3ThemeHelper;

@Keep
public class M3AppearancePreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.m3_appearance_preferences, rootKey);

        bindSummary("m3_theme_mode");
        bindSummary("m3_accent");
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
            if ("m3_accent".equals(key) && isAdded()) {
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putString(M3ThemeHelper.PREF_SEED_COLOR, M3ThemeHelper.getSeedColor((String) newValue))
                    .apply();
            }
            if (isAdded()) applyThemeNow(String.valueOf(newValue), "m3_theme_mode".equals(key));
            return true;
        });
    }

    private void applyThemeNow(String value, boolean isModeChange) {
        if (getActivity() == null) return;
        androidx.appcompat.app.AppCompatActivity a = (androidx.appcompat.app.AppCompatActivity) getActivity();
        if (isModeChange) {
            int mode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if ("light".equals(value)) mode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
            else if ("dark".equals(value)) mode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
            a.getDelegate().setLocalNightMode(mode);
        }
        M3ThemeHelper.apply(a);
        // Activity recreate preserves TermuxService sessions; required for full attr re-resolution.
        a.recreate();
    }
}
