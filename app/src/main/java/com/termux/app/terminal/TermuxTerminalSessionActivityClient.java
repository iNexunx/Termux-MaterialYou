package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.text.TextUtils;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.shared.termux.TermuxConstants;
import com.termux.app.TermuxService;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.terminal.io.BellHandler;
import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.terminal.TextStyle;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/** The {@link TerminalSessionClient} implementation that may require an {@link Activity} for its interface methods. */
public class TermuxTerminalSessionActivityClient extends TermuxTerminalSessionClientBase {

    private final TermuxActivity mActivity;

    private static final int MAX_SESSIONS = 8;

    private SoundPool mBellSoundPool;

    private int mBellSoundId;

    private static final String LOG_TAG = "TermuxTerminalSessionActivityClient";

    public TermuxTerminalSessionActivityClient(TermuxActivity activity) {
        this.mActivity = activity;
    }

    /**
     * Should be called when mActivity.onCreate() is called
     */
    public void onCreate() {
        // Set terminal fonts and colors
        checkForFontAndColors();
    }

    /**
     * Should be called when mActivity.onStart() is called
     */
    public void onStart() {
        // The service has connected, but data may have changed since we were last in the foreground.
        // Get the session stored in shared preferences stored by {@link #onStop} if its valid,
        // otherwise get the last session currently running.
        if (mActivity.getTermuxService() != null) {
            setCurrentSession(getCurrentStoredSessionOrLast());
            termuxSessionListNotifyUpdated();
        }

        // The current terminal session may have changed while being away, force
        // a refresh of the displayed terminal.
        mActivity.getTerminalView().onScreenUpdated();
    }

    /**
     * Should be called when mActivity.onResume() is called
     */
    public void onResume() {
        // Just initialize the mBellSoundPool and load the sound, otherwise bell might not run
        // the first time bell key is pressed and play() is called, since sound may not be loaded
        // quickly enough before the call to play(). https://stackoverflow.com/questions/35435625
        loadBellSoundPool();
    }

    /**
     * Should be called when mActivity.onStop() is called
     */
    public void onStop() {
        // Store current session in shared preferences so that it can be restored later in
        // {@link #onStart} if needed.
        setCurrentStoredSession();

        // Release mBellSoundPool resources, specially to prevent exceptions like the following to be thrown
        // java.util.concurrent.TimeoutException: android.media.SoundPool.finalize() timed out after 10 seconds
        // Bell is not played in background anyways
        // Related: https://stackoverflow.com/a/28708351/14686958
        releaseBellSoundPool();
    }

    /**
     * Should be called when mActivity.reloadActivityStyling() is called
     */
    public void onReloadActivityStyling() {
        // Set terminal fonts and colors
        checkForFontAndColors();
    }



    @Override
    public void onTextChanged(@NonNull TerminalSession changedSession) {
        if (!mActivity.isVisible()) return;
        mActivity.notifyPipUpdate(changedSession);
        if (mActivity.getCurrentSession() == changedSession) mActivity.getTerminalView().onScreenUpdated();
    }

    @Override
    public void onTitleChanged(@NonNull TerminalSession updatedSession) {
        if (!mActivity.isVisible()) return;
        mActivity.refreshSessionStrip();
        if (updatedSession != mActivity.getCurrentSession()) {
            mActivity.showToast(toToastTitle(updatedSession), true);
        }
        termuxSessionListNotifyUpdated();
    }

    @Override
    public void onSessionFinished(@NonNull TerminalSession finishedSession) {
        TermuxService service = mActivity.getTermuxService();

        if (service == null || service.wantsToStop()) {
            // The service wants to stop as soon as possible.
            mActivity.finishActivityIfNotFinishing();
            return;
        }

        int index = service.getIndexOfSession(finishedSession);

        // For plugin commands that expect the result back, we should immediately close the session
        // and send the result back instead of waiting fo the user to press enter.
        // The plugin can handle/show errors itself.
        boolean isPluginExecutionCommandWithPendingResult = false;
        TermuxSession termuxSession = service.getTermuxSession(index);
        if (termuxSession != null) {
            isPluginExecutionCommandWithPendingResult = termuxSession.getExecutionCommand().isPluginExecutionCommandWithPendingResult();
            if (isPluginExecutionCommandWithPendingResult)
                Logger.logVerbose(LOG_TAG, "The \"" + finishedSession.mSessionName + "\" session will be force finished automatically since result in pending.");
        }

        if (mActivity.isVisible() && finishedSession != mActivity.getCurrentSession()) {
            // Show toast for non-current sessions that exit.
            // Verify that session was not removed before we got told about it finishing:
            if (index >= 0)
                mActivity.showToast(toToastTitle(finishedSession) + " - exited", true);
        }

        if (mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            // On Android TV devices we need to use older behaviour because we may
            // not be able to have multiple launcher icons.
            if (service.getTermuxSessionsSize() > 1 || isPluginExecutionCommandWithPendingResult) {
                removeFinishedSession(finishedSession);
            }
        } else {
            // Once we have a separate launcher icon for the failsafe session, it
            // should be safe to auto-close session on exit code '0' or '130'.
            if (finishedSession.getExitStatus() == 0 || finishedSession.getExitStatus() == 130 || isPluginExecutionCommandWithPendingResult) {
                removeFinishedSession(finishedSession);
            }
        }
        mActivity.refreshSessionStrip();
    }

    @Override
    public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
        if (!mActivity.isVisible()) return;

        ShareUtils.copyTextToClipboard(mActivity, text);
    }

    @Override
    public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
        if (!mActivity.isVisible()) return;

        String text = ShareUtils.getTextStringFromClipboardIfSet(mActivity, true);
        if (text != null)
            mActivity.getTerminalView().mEmulator.paste(text);
    }

    @Override
    public void onBell(@NonNull TerminalSession session) {
        if (!mActivity.isVisible()) return;

        switch (mActivity.getProperties().getBellBehaviour()) {
            case TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_VIBRATE:
                BellHandler.getInstance(mActivity).doBell();
                break;
            case TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_BEEP:
                loadBellSoundPool();
                if (mBellSoundPool != null)
                    mBellSoundPool.play(mBellSoundId, 1.f, 1.f, 1, 0, 1.f);
                break;
            case TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_IGNORE:
                // Ignore the bell character.
                break;
        }
    }

    @Override
    public void onColorsChanged(@NonNull TerminalSession changedSession) {
        if (mActivity.getCurrentSession() == changedSession)
            updateBackgroundColor();
    }

    @Override
    public void onTerminalCursorStateChange(boolean enabled) {
        // Do not start cursor blinking thread if activity is not visible
        if (enabled && !mActivity.isVisible()) {
            Logger.logVerbose(LOG_TAG, "Ignoring call to start cursor blinking since activity is not visible");
            return;
        }

        // If cursor is to enabled now, then start cursor blinking if blinking is enabled
        // otherwise stop cursor blinking
        mActivity.getTerminalView().setTerminalCursorBlinkerState(enabled, false);
    }

    @Override
    public void setTerminalShellPid(@NonNull TerminalSession terminalSession, int pid) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;
        
        TermuxSession termuxSession = service.getTermuxSessionForTerminalSession(terminalSession);
        if (termuxSession != null)
            termuxSession.getExecutionCommand().mPid = pid;
    }


    /**
     * Should be called when mActivity.onResetTerminalSession() is called
     */
    public void onResetTerminalSession() {
        // Ensure blinker starts again after reset if cursor blinking was disabled before reset like
        // with "tput civis" which would have called onTerminalCursorStateChange()
        mActivity.getTerminalView().setTerminalCursorBlinkerState(true, true);
    }



    @Override
    public Integer getTerminalCursorStyle() {
        return mActivity.getProperties().getTerminalCursorStyle();
    }



    /** Load mBellSoundPool */
    private synchronized void loadBellSoundPool() {
        if (mBellSoundPool == null) {
            mBellSoundPool = new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
                new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()).build();

            try {
                mBellSoundId = mBellSoundPool.load(mActivity, R.raw.bell, 1);
            } catch (Exception e){
                // Catch java.lang.RuntimeException: Unable to resume activity {com.termux/com.termux.app.TermuxActivity}: android.content.res.Resources$NotFoundException: File res/raw/bell.ogg from drawable resource ID
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load bell sound pool", e);
            }
        }
    }

    /** Release mBellSoundPool resources */
    private synchronized void releaseBellSoundPool() {
        if (mBellSoundPool != null) {
            mBellSoundPool.release();
            mBellSoundPool = null;
        }
    }



    /** Try switching to session. */
    public void setCurrentSession(TerminalSession session) {
        if (session == null) return;

        if (mActivity.getTerminalView().attachSession(session)) {
            // notify about switched session if not already displaying the session
            notifyOfSessionChange();
        }

        // We call the following even when the session is already being displayed since config may
        // be stale, like current session not selected or scrolled to.
        checkAndScrollToSession(session);
        updateBackgroundColor();
        mActivity.termuxSessionListNotifyUpdated();
    }

    void notifyOfSessionChange() {
        if (!mActivity.isVisible()) return;

        if (!mActivity.getProperties().areTerminalSessionChangeToastsDisabled()) {
            TerminalSession session = mActivity.getCurrentSession();
            mActivity.showToast(toToastTitle(session), false);
        }
    }

    public void switchToSession(boolean forward) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;

        TerminalSession currentTerminalSession = mActivity.getCurrentSession();
        int index = service.getIndexOfSession(currentTerminalSession);
        int size = service.getTermuxSessionsSize();
        if (forward) {
            if (++index >= size) index = 0;
        } else {
            if (--index < 0) index = size - 1;
        }

        TermuxSession termuxSession = service.getTermuxSession(index);
        if (termuxSession != null)
            setCurrentSession(termuxSession.getTerminalSession());
    }

    public void switchToSession(int index) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;

        TermuxSession termuxSession = service.getTermuxSession(index);
        if (termuxSession != null)
            setCurrentSession(termuxSession.getTerminalSession());
    }

    @SuppressLint("InflateParams")
    public void renameSession(final TerminalSession sessionToRename) {
        if (sessionToRename == null) return;
        android.view.View content = android.view.LayoutInflater.from(mActivity).inflate(R.layout.m3_context_menu, null, false);
        android.widget.LinearLayout items = content.findViewById(R.id.m3_context_menu_items);

        com.google.android.material.textview.MaterialTextView title = new com.google.android.material.textview.MaterialTextView(mActivity);
        title.setText(R.string.title_rename_session);
        title.setTextAppearance(mActivity, com.google.android.material.R.style.TextAppearance_MaterialComponents_Headline6);
        title.setTextColor(com.termux.shared.theme.ThemeUtils.getSystemAttrColor(mActivity, R.attr.colorOnSurface, 0xFF222222));
        int dp4 = (int)(4*mActivity.getResources().getDisplayMetrics().density), dp12=(int)(12*mActivity.getResources().getDisplayMetrics().density);
        title.setPadding(dp4, dp4, dp4, dp12);
        items.addView(title);

        com.google.android.material.textfield.TextInputLayout inputLayout = new com.google.android.material.textfield.TextInputLayout(mActivity, null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
        inputLayout.setHint(mActivity.getString(R.string.title_rename_session));
        int r=(int)(24*mActivity.getResources().getDisplayMetrics().density); inputLayout.setBoxCornerRadii(r,r,r,r);
        final com.google.android.material.textfield.TextInputEditText input = new com.google.android.material.textfield.TextInputEditText(inputLayout.getContext());
        input.setSingleLine(true);
        if (sessionToRename.mSessionName != null) { input.setText(sessionToRename.mSessionName); android.text.Selection.setSelection(input.getText(), sessionToRename.mSessionName.length()); }
        inputLayout.addView(input);
        android.widget.LinearLayout.LayoutParams ilp = new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.bottomMargin = (int)(8*mActivity.getResources().getDisplayMetrics().density); inputLayout.setLayoutParams(ilp);
        items.addView(inputLayout);

        final androidx.appcompat.app.AlertDialog[] dlg = new androidx.appcompat.app.AlertDialog[1];
        java.lang.Runnable add = null;
        addRow(items, R.drawable.ic_m3_edit, mActivity.getString(R.string.action_rename_session_confirm), () -> { renameSession(sessionToRename, String.valueOf(input.getText())); termuxSessionListNotifyUpdated(); if(dlg[0]!=null) dlg[0].dismiss(); });
        addRow(items, R.drawable.ic_m3_power, mActivity.getString(R.string.action_kill_process_confirm), () -> { if(dlg[0]!=null) dlg[0].dismiss(); TermuxService service = mActivity.getTermuxService(); if (service != null) service.removeTermuxSession(sessionToRename); });
        addRow(items, R.drawable.ic_m3_close, mActivity.getString(android.R.string.cancel), () -> { if(dlg[0]!=null) dlg[0].dismiss(); });

        dlg[0] = new com.google.android.material.dialog.MaterialAlertDialogBuilder(mActivity).setView(content).create();
        try { com.termux.shared.view.M3DialogUtils.applyM3Window(dlg[0], true); } catch (Throwable ignored) {}
        dlg[0].show();
        makeDialogMovableResizable(dlg[0], title, content);
    }

    private void addRow(android.widget.LinearLayout items, int iconRes, String text, Runnable action) {
        android.view.View item = android.view.LayoutInflater.from(mActivity).inflate(R.layout.m3_context_menu_item, items, false);
        ((android.widget.ImageView)item.findViewById(R.id.menu_item_icon)).setImageResource(iconRes);
        ((android.widget.TextView)item.findViewById(R.id.menu_item_text)).setText(text);
        item.setOnClickListener(v -> action.run());
        items.addView(item);
    }

    private void makeDialogMovableResizable(androidx.appcompat.app.AlertDialog dialog, android.view.View dragHandle, android.view.View content) {
        try { com.termux.shared.view.M3DialogUtils.applyM3Window(dialog, true); } catch (Throwable ignored) {}
        try { com.termux.shared.view.M3DialogUtils.makeMovableResizable(dialog, dragHandle); } catch (Throwable ignored) {}
    }

    private void renameSession(TerminalSession sessionToRename, String text) {
        if (sessionToRename == null) return;
        sessionToRename.mSessionName = text;
        TermuxService service = mActivity.getTermuxService();
        if (service != null) {
            TermuxSession termuxSession = service.getTermuxSessionForTerminalSession(sessionToRename);
            if (termuxSession != null)
                termuxSession.getExecutionCommand().shellName = text;
        }
    }

    public void addNewSession(boolean isFailSafe, String sessionName) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;

        if (service.getTermuxSessionsSize() >= MAX_SESSIONS) {
            android.view.View mv = android.view.LayoutInflater.from(mActivity).inflate(com.termux.shared.R.layout.dialog_show_message, null, false);
            ((android.widget.TextView)mv.findViewById(com.termux.shared.R.id.dialog_title)).setText(R.string.title_max_terminals_reached);
            ((android.widget.TextView)mv.findViewById(com.termux.shared.R.id.dialog_message)).setText(R.string.msg_max_terminals_reached);
            androidx.appcompat.app.AlertDialog d = new com.google.android.material.dialog.MaterialAlertDialogBuilder(mActivity).setView(mv)
                .setPositiveButton(android.R.string.ok, null).create();
            try { com.termux.shared.view.M3DialogUtils.applyM3Window(d, true); } catch (Throwable ignored) {}
            d.show();
        } else {
            TerminalSession currentSession = mActivity.getCurrentSession();

            String workingDirectory;
            if (currentSession == null) {
                workingDirectory = mActivity.getProperties().getDefaultWorkingDirectory();
            } else {
                workingDirectory = currentSession.getCwd();
            }

            TermuxSession newTermuxSession = service.createTermuxSession(null, null, null, workingDirectory, isFailSafe, sessionName);
            if (newTermuxSession == null) return;

            TerminalSession newTerminalSession = newTermuxSession.getTerminalSession();
            setCurrentSession(newTerminalSession);

            mActivity.getDrawer().closeDrawers();
        }
    }

    public void setCurrentStoredSession() {
        TerminalSession currentSession = mActivity.getCurrentSession();
        if (currentSession != null)
            mActivity.getPreferences().setCurrentSession(currentSession.mHandle);
        else
            mActivity.getPreferences().setCurrentSession(null);
    }

    /** The current session as stored or the last one if that does not exist. */
    public TerminalSession getCurrentStoredSessionOrLast() {
        TerminalSession stored = getCurrentStoredSession();

        if (stored != null) {
            // If a stored session is in the list of currently running sessions, then return it
            return stored;
        } else {
            // Else return the last session currently running
            TermuxService service = mActivity.getTermuxService();
            if (service == null) return null;

            TermuxSession termuxSession = service.getLastTermuxSession();
            if (termuxSession != null)
                return termuxSession.getTerminalSession();
            else
                return null;
        }
    }

    private TerminalSession getCurrentStoredSession() {
        String sessionHandle = mActivity.getPreferences().getCurrentSession();

        // If no session is stored in shared preferences
        if (sessionHandle == null)
            return null;

        // Check if the session handle found matches one of the currently running sessions
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return null;

        return service.getTerminalSessionForHandle(sessionHandle);
    }

    public void removeFinishedSession(TerminalSession finishedSession) {
        // Return pressed with finished session - remove it.
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;

        int index = service.removeTermuxSession(finishedSession);

        int size = service.getTermuxSessionsSize();
        if (size == 0) {
            // There are no sessions to show, so finish the activity.
            mActivity.finishActivityIfNotFinishing();
        } else {
            if (index >= size) {
                index = size - 1;
            }
            TermuxSession termuxSession = service.getTermuxSession(index);
            if (termuxSession != null)
                setCurrentSession(termuxSession.getTerminalSession());
        }
        mActivity.refreshSessionStrip();
    }

    public void termuxSessionListNotifyUpdated() {
        mActivity.termuxSessionListNotifyUpdated();
    }

    public void checkAndScrollToSession(TerminalSession session) {
        if (!mActivity.isVisible()) return;
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;

        final int indexOfSession = service.getIndexOfSession(session);
        if (indexOfSession < 0) return;
        final ListView termuxSessionsListView = mActivity.findViewById(R.id.terminal_sessions_list);
        if (termuxSessionsListView == null) return;

        termuxSessionsListView.setItemChecked(indexOfSession, true);
        // Delay is necessary otherwise sometimes scroll to newly added session does not happen
        termuxSessionsListView.postDelayed(() -> termuxSessionsListView.smoothScrollToPosition(indexOfSession), 1000);
    }


    String toToastTitle(TerminalSession session) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return null;

        final int indexOfSession = service.getIndexOfSession(session);
        if (indexOfSession < 0) return null;
        StringBuilder toastTitle = new StringBuilder("[" + (indexOfSession + 1) + "]");
        if (!TextUtils.isEmpty(session.mSessionName)) {
            toastTitle.append(" ").append(session.mSessionName);
        }
        String title = session.getTitle();
        if (!TextUtils.isEmpty(title)) {
            // Space to "[${NR}] or newline after session name:
            toastTitle.append(session.mSessionName == null ? " " : "\n");
            toastTitle.append(title);
        }
        return toastTitle.toString();
    }


    public void checkForFontAndColors() {
        try {
            File colorsFile = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
            File fontFile = TermuxConstants.TERMUX_FONT_FILE;

            final Properties props = new Properties();
            if (colorsFile.isFile()) {
                try (InputStream in = new FileInputStream(colorsFile)) {
                    props.load(in);
                }
            }
            applyM3TerminalColorScheme(props);
            TerminalColors.COLOR_SCHEME.updateWith(props);
            TerminalSession session = mActivity.getCurrentSession();
            if (session != null && session.getEmulator() != null) {
                session.getEmulator().mColors.reset();
            }
            updateBackgroundColor();

            Typeface newTypeface = (fontFile.exists() && fontFile.length() > 0) ? Typeface.createFromFile(fontFile) : getM3Typeface();
            mActivity.getTerminalView().setTypeface(newTypeface);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error in checkForFontAndColors()", e);
        }
    }

    private Typeface getM3Typeface() {
        String font = mActivity.getPreferences().getTerminalFont();
        if ("sans-serif".equals(font)) return Typeface.SANS_SERIF;
        if ("serif".equals(font)) return Typeface.SERIF;
        if ("casual".equals(font)) return Typeface.create("casual", Typeface.NORMAL);
        if ("cursive".equals(font)) return Typeface.create("cursive", Typeface.NORMAL);
        if ("monospace-bold".equals(font)) return Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        if ("small-caps".equals(font)) return Typeface.create("sans-serif-smallcaps", Typeface.NORMAL);
        if ("firacode".equals(font)) {
            try { return Typeface.createFromAsset(mActivity.getAssets(), "fonts/FiraCode-Regular.ttf"); } catch (Exception ignored) {}
        }
        if ("jetbrains".equals(font)) {
            try { return Typeface.createFromAsset(mActivity.getAssets(), "fonts/JetBrainsMono-Regular.ttf"); } catch (Exception ignored) {}
        }
        return Typeface.MONOSPACE;
    }

    private void applyM3TerminalColorScheme(Properties props) {
        if (props.containsKey("foreground") || props.containsKey("background")) return;
        String scheme = mActivity.getPreferences().getTerminalColorScheme();
        if ("black".equals(scheme)) { props.put("background","#000000"); props.put("foreground","#FFFFFF"); props.put("cursor","#FFFFFF"); }
        else if ("solarized_dark".equals(scheme)) { props.put("background","#002B36"); props.put("foreground","#839496"); props.put("cursor","#93A1A1"); }
        else if ("solarized_light".equals(scheme)) { props.put("background","#FDF6E3"); props.put("foreground","#657B83"); props.put("cursor","#586E75"); }
        else if ("dracula".equals(scheme)) { props.put("background","#282A36"); props.put("foreground","#F8F8F2"); props.put("cursor","#F8F8F2"); }
        else if ("nord".equals(scheme)) { props.put("background","#2E3440"); props.put("foreground","#D8DEE9"); props.put("cursor","#D8DEE9"); }
        else if ("gruvbox_dark".equals(scheme) || "gruvbox".equals(scheme)) { props.put("background","#282828"); props.put("foreground","#EBDBB2"); props.put("cursor","#EBDBB2"); }
        else if ("gruvbox_light".equals(scheme)) { props.put("background","#FBF1C7"); props.put("foreground","#3C3836"); props.put("cursor","#3C3836"); }
        else if ("monokai".equals(scheme)) { props.put("background","#272822"); props.put("foreground","#F8F8F2"); props.put("cursor","#F8F8F0"); }
        else if ("one_dark".equals(scheme)) { props.put("background","#282C34"); props.put("foreground","#ABB2BF"); props.put("cursor","#528BFF"); }
        else if ("tokyo_night".equals(scheme)) { props.put("background","#1A1B26"); props.put("foreground","#C0CAF5"); props.put("cursor","#C0CAF5"); }
        else if ("catppuccin".equals(scheme)) { props.put("background","#1E1E2E"); props.put("foreground","#CDD6F4"); props.put("cursor","#F5E0DC"); }
    }

    public void updateBackgroundColor() {
        if (!mActivity.isVisible()) return;
        TerminalSession session = mActivity.getCurrentSession();
        if (session != null && session.getEmulator() != null) {
            mActivity.getWindow().getDecorView().setBackgroundColor(session.getEmulator().mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]);
        }
    }

}
