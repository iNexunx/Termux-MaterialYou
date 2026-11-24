package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceDataStore;
import com.termux.app.fragments.settings.M3PreferenceFragment;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

@Keep
public class TerminalViewPreferencesFragment extends M3PreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TerminalViewPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_terminal_view_preferences, rootKey);
    }

}

class TerminalViewPreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;
    private final TermuxAppSharedPreferences mPreferences;

    private static TerminalViewPreferencesDataStore mInstance;

    private TerminalViewPreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    public static synchronized TerminalViewPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TerminalViewPreferencesDataStore(context);
        }
        return mInstance;
    }



    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null) return;
        if (key == null) return;

        switch (key) {
            case "terminal_margin_adjustment":
                    mPreferences.setTerminalMarginAdjustment(value);
                break;
            case "extra_keys_floating":
                    mPreferences.setExtraKeysFloating(value);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mPreferences == null) return false;

        switch (key) {
            case "terminal_margin_adjustment":
                return mPreferences.isTerminalMarginAdjustmentEnabled();
            case "extra_keys_floating":
                return mPreferences.isExtraKeysFloating();
            default:
                return false;
        }
    }

    @Override
    public void putString(String key, String value) {
        if (mPreferences == null || key == null) return;
        switch (key) {
            case "terminal_font":
                mPreferences.setTerminalFont(value);
                if (getActivity() instanceof com.termux.app.TermuxActivity) ((com.termux.app.TermuxActivity) getActivity()).reloadTerminalStyling();
                break;
            case "terminal_color_scheme":
                mPreferences.setTerminalColorScheme(value);
                if (getActivity() instanceof com.termux.app.TermuxActivity) ((com.termux.app.TermuxActivity) getActivity()).reloadTerminalStyling();
                break;
            default: break;
        }
    }

    @Override
    public String getString(String key, String defValue) {
        if (mPreferences == null) return defValue;
        switch (key) {
            case "terminal_font":
                return mPreferences.getTerminalFont();
            case "terminal_color_scheme":
                return mPreferences.getTerminalColorScheme();
            default:
                return defValue;
        }
    }

}
