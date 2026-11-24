package com.termux.shared.termux.interact;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.text.Selection;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.shared.R;

public final class TextInputDialogUtils {

    public interface TextSetListener {
        void onTextSet(String text);
    }

    public static void textInput(Activity activity, int titleText, String initialText,
                                 int positiveButtonText, final TextSetListener onPositive,
                                 int neutralButtonText, final TextSetListener onNeutral,
                                 int negativeButtonText, final TextSetListener onNegative,
                                 final DialogInterface.OnDismissListener onDismiss) {
        textInput(activity, titleText, initialText,
            positiveButtonText, onPositive,
            neutralButtonText, onNeutral,
            negativeButtonText, onNegative,
            onDismiss, false);
    }

    public static void textInput(Activity activity, int titleText, String initialText,
                                 int positiveButtonText, final TextSetListener onPositive,
                                 int neutralButtonText, final TextSetListener onNeutral,
                                 int negativeButtonText, final TextSetListener onNegative,
                                 final DialogInterface.OnDismissListener onDismiss,
                                 boolean stackedButtons) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_m3_text_input, null, false);
        final EditText input = view.findViewById(R.id.dialog_text_input);
        TextInputLayout inputLayout = view.findViewById(R.id.dialog_text_input_layout);
        input.setSingleLine();
        if (initialText != null) {
            input.setText(initialText);
            Selection.setSelection(input.getText(), initialText.length());
        }

        final Dialog[] dialogHolder = new Dialog[1];
        input.setImeActionLabel(activity.getResources().getString(positiveButtonText), KeyEvent.KEYCODE_ENTER);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (onPositive != null) onPositive.onTextSet(input.getText().toString());
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            return true;
        });

        android.widget.TextView titleView = view.findViewById(R.id.dialog_m3_title);
        if (titleView != null) titleView.setText(titleText);
        else inputLayout.setHint(activity.getString(titleText));
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton(positiveButtonText, (d, whichButton) -> {
                if (onPositive != null) onPositive.onTextSet(input.getText().toString());
            });

        if (neutralButtonText != -1)
            builder.setNeutralButton(neutralButtonText, (dialog, which) -> {
                if (onNeutral != null) onNeutral.onTextSet(input.getText().toString());
            });

        if (negativeButtonText != -1)
            builder.setNegativeButton(negativeButtonText, (dialog, which) -> {
                if (onNegative != null) onNegative.onTextSet(input.getText().toString());
            });

        if (onDismiss != null)
            builder.setOnDismissListener(onDismiss);

        AlertDialog dialog = builder.create();
        dialogHolder[0] = dialog;
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int margin = dp(activity, 12);
            android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.width = activity.getResources().getDisplayMetrics().widthPixels - margin * 2;
            dialog.getWindow().setAttributes(lp);
        }
        if (stackedButtons) stackDialogButtons(dialog);
    }

    public static void stackDialogButtons(AlertDialog dialog) {
        if (dialog == null) return;
        View buttonBar = dialog.findViewById(androidx.appcompat.R.id.buttonPanel);
        if (!(buttonBar instanceof LinearLayout)) return;
        LinearLayout layout = (LinearLayout) buttonBar;
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        int padH = dp(dialog.getContext(), 16);
        int padB = dp(dialog.getContext(), 12);
        layout.setPadding(padH, layout.getPaddingTop(), padH, padB);

        // Collect visible buttons in original order and re-add in reverse so first added appears top.
        java.util.ArrayList<View> buttons = new java.util.ArrayList<>();
        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            if (child instanceof android.widget.Space) { child.setVisibility(View.GONE); continue; }
            if (child instanceof android.widget.Button) buttons.add(child);
        }
        for (View b : buttons) layout.removeView(b);
        for (int i = buttons.size() - 1; i >= 0; i--) {
            View child = buttons.get(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.leftMargin = 0;
            lp.rightMargin = 0;
            lp.bottomMargin = i == 0 ? 0 : dp(dialog.getContext(), 8);
            child.setLayoutParams(lp);
            child.setMinimumHeight(dp(dialog.getContext(), 48));
            if (child instanceof com.google.android.material.button.MaterialButton) {
                com.google.android.material.button.MaterialButton mb = (com.google.android.material.button.MaterialButton) child;
                mb.setCornerRadius(dp(dialog.getContext(), 24));
                mb.setAllCaps(false);
                mb.setInsetTop(0);
                mb.setInsetBottom(0);
            }
            layout.addView(child);
        }
    }

    private static int dp(android.content.Context c, int dp) {
        return (int) (dp * c.getResources().getDisplayMetrics().density);
    }

}
