package com.termux.shared.termux.interact;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.text.Selection;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

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
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_m3_text_input, null, false);
        final EditText input = view.findViewById(R.id.dialog_text_input);
        TextInputLayout inputLayout = view.findViewById(R.id.dialog_text_input_layout);
        inputLayout.setHint(activity.getString(titleText));
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

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
            .setTitle(titleText)
            .setView(view)
            .setPositiveButton(positiveButtonText, (d, whichButton) -> {
                if (onPositive != null) onPositive.onTextSet(input.getText().toString());
            });

        if (onNeutral != null)
            builder.setNeutralButton(neutralButtonText, (dialog, which) -> onNeutral.onTextSet(input.getText().toString()));

        if (onNegative == null)
            builder.setNegativeButton(android.R.string.cancel, null);
        else
            builder.setNegativeButton(negativeButtonText, (dialog, which) -> onNegative.onTextSet(input.getText().toString()));

        if (onDismiss != null)
            builder.setOnDismissListener(onDismiss);

        dialogHolder[0] = builder.show();
        dialogHolder[0].setCanceledOnTouchOutside(false);
    }

}
