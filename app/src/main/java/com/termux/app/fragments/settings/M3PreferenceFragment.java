package com.termux.app.fragments.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

public abstract class M3PreferenceFragment extends PreferenceFragmentCompat {

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (preference instanceof androidx.preference.ListPreference) {
            androidx.preference.ListPreference lp = (androidx.preference.ListPreference) preference;
            CharSequence[] entries = lp.getEntries();
            CharSequence[] values = lp.getEntryValues();
            int checked = lp.findIndexOfValue(lp.getValue());
            final int[] selected = {checked};
            androidx.appcompat.app.AlertDialog d = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(lp.getTitle())
                .setSingleChoiceItems(entries, checked, (dlg, which) -> selected[0] = which)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dlg, w) -> {
                    if (selected[0] >= 0 && values != null && selected[0] < values.length) {
                        String v = values[selected[0]].toString();
                        if (lp.callChangeListener(v)) lp.setValue(v);
                    }
                })
                .create();
            d.show();
            if (d.getWindow() != null) {
                d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                android.view.View decor = d.getWindow().getDecorView();
                if (decor != null) decor.setBackgroundResource(com.termux.R.drawable.m3_dialog_outer_bg);
            }
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView rv = getListView();
        if (rv != null) {
            int pad = dp(16);
            rv.setPadding(pad, dp(8), pad, dp(24));
            rv.setClipToPadding(false);
        }
    }

    @Override
    protected RecyclerView.Adapter<?> onCreateAdapter(PreferenceScreen preferenceScreen) {
        return new M3PreferenceGroupAdapter(preferenceScreen);
    }

    protected int dp(int v) {
        Context c = getContext();
        return c == null ? v : Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static class M3PreferenceGroupAdapter extends PreferenceGroupAdapter {
        M3PreferenceGroupAdapter(PreferenceScreen preferenceScreen) { super(preferenceScreen); }

        @Override
        public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);
            Preference p = getItem(position);
            View item = holder.itemView;
            boolean isCategory = p instanceof PreferenceCategory;
            if (isCategory) {
                item.setBackground(null);
                item.setPadding(0, dp(item, 14), 0, dp(item, 4));
                return;
            }
            int start = dp(item, 14), top = dp(item, 14), end = dp(item, 14), bottom = dp(item, 14);
            item.setPadding(start, top, end, bottom);
            item.setBackgroundResource(R.drawable.m3_menu_pill);
            ViewGroup.MarginLayoutParams lp;
            if (item.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                lp = (ViewGroup.MarginLayoutParams) item.getLayoutParams();
            } else {
                lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                item.setLayoutParams(lp);
            }
            lp.topMargin = dp(item, 6);
            lp.bottomMargin = dp(item, 6);
            lp.leftMargin = 0;
            lp.rightMargin = 0;
            item.setLayoutParams(lp);
            if (item instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) item;
                vg.setClipToPadding(false);
            }
        }

        static int dp(View v, int d) {
            return Math.round(d * v.getResources().getDisplayMetrics().density);
        }
    }
}
