package com.termux.app.activities;

import android.os.Bundle;

import androidx.annotation.Keep;

import com.termux.app.theme.M3ThemeHelper;

@Keep
public class M3ReportActivity extends com.termux.shared.activities.ReportActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        M3ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);
    }
}
