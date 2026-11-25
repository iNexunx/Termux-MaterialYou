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
            showM3ListPreferenceDialog((androidx.preference.ListPreference) preference);
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }


    private void showM3ListPreferenceDialog(androidx.preference.ListPreference lp) {
        Context ctx = requireContext();
        View content = LayoutInflater.from(ctx).inflate(R.layout.m3_context_menu, null, false);
        android.widget.LinearLayout items = content.findViewById(R.id.m3_context_menu_items);
        // title
        com.google.android.material.textview.MaterialTextView header = new com.google.android.material.textview.MaterialTextView(ctx);
        header.setText(lp.getTitle());
        header.setTextAppearance(ctx, com.google.android.material.R.style.TextAppearance_MaterialComponents_Headline6);
        header.setTextColor(com.termux.shared.theme.ThemeUtils.getSystemAttrColor(ctx, R.attr.colorOnSurface, 0xFF222222));
        int hp = dp(4), hb = dp(12);
        header.setPadding(hp, hp, hp, hb);
        items.addView(header);

        CharSequence[] entries = lp.getEntries();
        CharSequence[] values = lp.getEntryValues();
        String current = lp.getValue();
        androidx.appcompat.app.AlertDialog[] dlg = new androidx.appcompat.app.AlertDialog[1];
        if (entries != null && values != null) {
            for (int i = 0; i < entries.length && i < values.length; i++) {
                final String v = String.valueOf(values[i]);
                View item = LayoutInflater.from(ctx).inflate(R.layout.m3_context_menu_item, items, false);
                item.findViewById(R.id.menu_item_icon).setVisibility(View.GONE);
                ((android.widget.TextView) item.findViewById(R.id.menu_item_text)).setText(entries[i]);
                com.google.android.material.switchmaterial.SwitchMaterial sw = item.findViewById(R.id.menu_item_switch);
                sw.setVisibility(View.VISIBLE);
                boolean checked = v.equals(current);
                sw.setChecked(checked);
                sw.setClickable(false);
                sw.setFocusable(false);
                item.setOnClickListener(x -> {
                    if (lp.callChangeListener(v)) lp.setValue(v);
                    if (dlg[0] != null) dlg[0].dismiss();
                });
                items.addView(item);
            }
        }
        dlg[0] = new com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx).setView(content).create();
        try { com.termux.shared.view.M3DialogUtils.applyM3Window(dlg[0], true); } catch (Throwable ignored) {}
        dlg[0].show();
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
