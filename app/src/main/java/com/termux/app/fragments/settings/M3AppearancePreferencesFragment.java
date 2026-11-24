package com.termux.app.fragments.settings;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.R;

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
            if (isAdded() && getActivity() != null) {
                getActivity().recreate();
            }
            return true;
        });
    }
}
