package com.termux.app;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.theme.M3ThemeHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import java.util.Arrays;
import java.util.List;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection, M3ThemeHelper.M3ThemeAware {

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    TerminalView mTerminalView;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxActivity}.
     */
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /**
     * Termux app shared preferences manager.
     */
    private TermuxAppSharedPreferences mPreferences;

    /**
     * Termux app SharedProperties loaded from termux.properties
     */
    private TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TermuxActivity}.
     */
    TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TermuxActivity}.
     */
    View mTermuxActivityBottomSpaceView;

    /**
     * The terminal extra keys view.
     */
    ExtraKeysView mExtraKeysView;

    /**
     * The client for the {@link #mExtraKeysView}.
     */
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;

    /**
     * The termux sessions list controller.
     */
    TermuxSessionsListViewController mTermuxSessionListViewController;

    /**
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private boolean mIsVisible;

    /**
     * If onResume() was called after onCreate().
     */
    private boolean mIsOnResumeAfterOnCreate = false;

    /**
     * If activity was restarted like due to call to {@link #recreate()} after receiving
     * {@link TERMUX_ACTIVITY#ACTION_RELOAD_STYLE}, system dark night mode was changed or activity
     * was killed by android.
     */
    private boolean mIsActivityRecreated = false;

    /**
     * The {@link TermuxActivity} is in an invalid state and must not be run.
     */
    private boolean mIsInvalidState;

    private int mNavBarHeight;

    private float mTerminalToolbarDefaultHeight;


    private String mAppliedM3Accent;
    private String mAppliedM3Mode;
    private String mAppliedM3Language;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

    private static final String LOG_TAG = "TermuxActivity";

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(com.termux.app.theme.M3ThemeHelper.wrapContext(newBase));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;

        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);

        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);

        // Load Termux app SharedProperties from disk
        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();

        android.content.SharedPreferences m3p = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        mAppliedM3Accent = m3p.getString(M3ThemeHelper.PREF_ACCENT, "default");
        mAppliedM3Mode = m3p.getString(M3ThemeHelper.PREF_MODE, "system");
        mAppliedM3Language = m3p.getString(M3ThemeHelper.PREF_LANGUAGE, "system");
        setActivityTheme();

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_termux);

        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running activity code
            mIsInvalidState = true;
            return;
        }

        setMargins();

        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());

        View content = findViewById(android.R.id.content);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            androidx.core.graphics.Insets sys = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            androidx.core.graphics.Insets ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
            mNavBarHeight = Math.max(sys.bottom, ime.bottom);
            // Left/right handled per-view; bottom handled by TermuxActivityRootView bottom space + IME.
            v.setPadding(0, 0, 0, 0);
            return insets;
        });
        View drawerLayout = findViewById(R.id.drawer_layout);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, insets) -> {
            androidx.core.graphics.Insets sys = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, 0);
            View terminalContent = findViewById(R.id.terminal_content_container);
            if (terminalContent != null) terminalContent.setPadding(0, sys.top, 0, 0);
            View leftDrawer = findViewById(R.id.left_drawer);
            if (leftDrawer != null) {
                int d = (int) (12 * getResources().getDisplayMetrics().density);
                android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) leftDrawer.getLayoutParams();
                lp.topMargin = sys.top + d;
                lp.bottomMargin = sys.bottom + d;
                lp.leftMargin = sys.left + d;
                leftDrawer.setLayoutParams(lp);
            }
            View toolbarContainer = findViewById(R.id.terminal_toolbar_container);
            if (toolbarContainer != null) {
                android.view.ViewGroup.MarginLayoutParams mlp = (android.view.ViewGroup.MarginLayoutParams) toolbarContainer.getLayoutParams();
                int base = (int) (8 * getResources().getDisplayMetrics().density);
                boolean floating = mPreferences != null && mPreferences.isExtraKeysFloating();
                mlp.leftMargin = base + (floating ? sys.left : 0);
                mlp.rightMargin = base + (floating ? sys.right : 0);
                mlp.bottomMargin = base;
                toolbarContainer.setLayoutParams(mlp);
                if (!floating) toolbarContainer.setPadding(sys.left, 0, sys.right, 0);
                else toolbarContainer.setPadding(0,0,0,0);
            }
            return insets;
        });

        if (mProperties.isUsingFullScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setTermuxTerminalViewAndClients();

        setTerminalToolbarView(savedInstanceState);

        setSettingsButtonView();

        setNewSessionButtonView();

        setToggleKeyboardView();

        setDrawerThemeAndColorControls();
        setPackageManagerButtonView();

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        try {
            // Start the {@link TermuxService} and make it run regardless of who is bound to it
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);

            // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
            // callback if it succeeds.
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG,"TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this,
                getString(e.getMessage() != null && e.getMessage().contains("app is in background") ?
                    R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general),
                true);
            mIsInvalidState = true;
            return;
        }

        // Send the {@link TermuxConstants#BROADCAST_TERMUX_OPENED} broadcast to notify apps that Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState) return;

        mIsVisible = true;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();

        if (mPreferences.isTerminalMarginAdjustmentEnabled())
            addTermuxActivityRootViewGlobalLayoutListener();

        registerTermuxActivityBroadcastReceiver();
        applyM3ThemeIfChanged(false);
        applyExtraKeysFloatingMode();
        refreshSessionStrip();
        reloadTerminalStyling();
        if (mTermuxService != null) try { mTermuxService.updateNotificationFromActivity(); } catch (Throwable ignored) {}
    }

    @Override
    public void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState) return;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();

        // Check if a crash happened on last run of the app or if a plugin crashed and show a
        // notification with the crash details if it did
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);

        mIsOnResumeAfterOnCreate = false;
    }

    @Override
    protected void onStop() {
        super.onStop();

        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState) return;

        mIsVisible = false;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();

        removeTermuxActivityRootViewGlobalLayoutListener();

        unregisterTermuxActivityBroadcastReceiver();
        getDrawer().closeDrawers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Logger.logDebug(LOG_TAG, "onDestroy");

        if (mIsInvalidState) return;

        if (mTermuxService != null) {
            // Do not leave service and session clients with references to activity.
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }

        try {
            unbindService(this);
        } catch (Exception e) {
            // ignore.
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");

        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }





    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");

        mTermuxService = ((TermuxService.LocalBinder) service).service;

        setTermuxSessionsListView();

        final Intent intent = getIntent();
        setIntent(null);

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
                    if (mTermuxService == null) return; // Activity might have been destroyed.
                    try {
                        boolean launchFailsafe = false;
                        if (intent != null && intent.getExtras() != null) {
                            launchFailsafe = intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                        }
                        mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
                    } catch (WindowManager.BadTokenException e) {
                        // Activity finished - ignore.
                    }
                });
            } else {
                // The service connected while not in foreground - just bail out.
                finishActivityIfNotFinishing();
            }
        } else {
            // If termux was started from launcher "New session" shortcut and activity is recreated,
            // then the original intent will be re-delivered, resulting in a new session being re-added
            // each time.
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                boolean isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }

        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");

        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }






    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadProperties();
    }



    private void setActivityTheme() {
        // Material 3 controls theme mode/accent; keep Termux NightMode in sync for compat.
        M3ThemeHelper.apply(this);
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }



    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }



    private void setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);

        // Set termux terminal view
        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onCreate();

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onCreate();
    }

    private void setTermuxSessionsListView() {
        ListView termuxSessionsListView = findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    }



    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
            mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);

        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        View toolbarContainer = findViewById(R.id.terminal_toolbar_container);
        if (mPreferences.shouldShowTerminalToolbar() && toolbarContainer != null) toolbarContainer.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        setTerminalToolbarHeight();
        applyExtraKeysFloatingMode();

        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight *
            (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length) *
            mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public void toggleTerminalToolbar() {
        View toolbarContainer = findViewById(R.id.terminal_toolbar_container);
        if (toolbarContainer == null) return;

        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        toolbarContainer.setVisibility(showNow ? View.VISIBLE : View.GONE);
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            View v = findViewById(R.id.terminal_toolbar_text_input);
            if (v != null) v.requestFocus();
        }
    }

    public void applyExtraKeysFloatingMode() {
        View toolbarContainer = findViewById(R.id.terminal_toolbar_container);
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (toolbarContainer == null || terminalToolbarViewPager == null || mPreferences == null) return;
        boolean floating = mPreferences.isExtraKeysFloating();
        com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) toolbarContainer;
        card.setRadius(floating ? dp(24) : 0);
        card.setCardElevation(floating ? dp(6) : 0);
        card.setStrokeWidth(floating ? dp(1) : 0);
        android.view.ViewGroup.MarginLayoutParams mlp = (android.view.ViewGroup.MarginLayoutParams) card.getLayoutParams();
        int base = floating ? dp(8) : 0;
        mlp.leftMargin = base; mlp.rightMargin = base; mlp.bottomMargin = base; mlp.topMargin = 0;
        card.setLayoutParams(mlp);
        setTerminalToolbarHeight();
        androidx.core.view.ViewCompat.requestApplyInsets(findViewById(R.id.drawer_layout));
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        final EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }



    private void setSettingsButtonView() {
        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
        });
    }

    private void setNewSessionButtonView() {
        View newSessionButton = findViewById(R.id.new_session_button);
        newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionActivityClient.addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            showNamedSessionDialog();
            return true;
        });
    }

    private void addM3DialogAction(LinearLayout items, int iconRes, String text, Runnable action) {
        addContextMenuItem(items, iconRes, text, true, false, false, action);
    }

    private void makeM3DialogMovableResizable(androidx.appcompat.app.AlertDialog dialog, View dragHandle) {
        if (dialog.getWindow() == null || dragHandle == null) return;
        android.view.Window window = dialog.getWindow();
        android.view.WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = getResources().getDisplayMetrics().widthPixels - dp(16);
        lp.gravity = android.view.Gravity.CENTER;
        window.setAttributes(lp);

        final float[] last = new float[4];
        dragHandle.setOnTouchListener((v, ev) -> {
            android.view.WindowManager.LayoutParams wlp = window.getAttributes();
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    last[0]=ev.getRawX(); last[1]=ev.getRawY(); last[2]=wlp.x; last[3]=wlp.y; return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    wlp.x = (int)(last[2] + ev.getRawX() - last[0]);
                    wlp.y = (int)(last[3] + ev.getRawY() - last[1]);
                    window.setAttributes(wlp); return true;
            }
            return false;
        });

        View resizer = new View(this);
        resizer.setBackgroundResource(R.drawable.ic_m3_more);
        int sz = dp(24);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(sz, sz);
        rlp.gravity = android.view.Gravity.END;
        resizer.setLayoutParams(rlp);
        if (dragHandle.getParent() instanceof LinearLayout) ((LinearLayout) dragHandle.getParent()).addView(resizer);
        resizer.setOnTouchListener((v, ev) -> {
            android.view.WindowManager.LayoutParams wlp = window.getAttributes();
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    last[0]=ev.getRawX(); last[1]=ev.getRawY(); last[2]=wlp.width; last[3]=wlp.height; return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    int w = (int)(last[2] + ev.getRawX() - last[0]);
                    int h = (int)(last[3] + ev.getRawY() - last[1]);
                    wlp.width = Math.max(dp(280), Math.min(getResources().getDisplayMetrics().widthPixels - dp(8), w));
                    wlp.height = Math.max(dp(200), h);
                    window.setAttributes(wlp); return true;
            }
            return false;
        });
    }

    private void showNamedSessionDialog() {
        View content = getLayoutInflater().inflate(R.layout.m3_context_menu, null, false);
        LinearLayout items = content.findViewById(R.id.m3_context_menu_items);

        com.google.android.material.textview.MaterialTextView title = new com.google.android.material.textview.MaterialTextView(this);
        title.setText(R.string.title_create_named_session);
        title.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_MaterialComponents_Headline6);
        title.setTextColor(com.termux.shared.theme.ThemeUtils.getSystemAttrColor(this, R.attr.colorOnSurface, 0xFF222222));
        int p4 = dp(4), p12 = dp(12);
        title.setPadding(p4, p4, p4, p12);
        items.addView(title);

        com.google.android.material.textfield.TextInputLayout inputLayout = new com.google.android.material.textfield.TextInputLayout(this, null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
        inputLayout.setHint(getString(R.string.title_create_named_session));
        inputLayout.setBoxCornerRadii(dp(24),dp(24),dp(24),dp(24));
        com.google.android.material.textfield.TextInputEditText input = new com.google.android.material.textfield.TextInputEditText(inputLayout.getContext());
        input.setSingleLine(true);
        inputLayout.addView(input);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.bottomMargin = dp(8);
        inputLayout.setLayoutParams(ilp);
        items.addView(inputLayout);

        androidx.appcompat.app.AlertDialog[] dlg = new androidx.appcompat.app.AlertDialog[1];
        addM3DialogAction(items, R.drawable.ic_m3_new_session, getString(R.string.action_create_named_session_confirm), () -> { mTermuxTerminalSessionActivityClient.addNewSession(false, String.valueOf(input.getText())); if (dlg[0]!=null) dlg[0].dismiss(); });
        addM3DialogAction(items, R.drawable.ic_m3_terminal, getString(R.string.action_new_session_failsafe), () -> { mTermuxTerminalSessionActivityClient.addNewSession(true, String.valueOf(input.getText())); if (dlg[0]!=null) dlg[0].dismiss(); });
        addM3DialogAction(items, R.drawable.ic_m3_close, getString(android.R.string.cancel), () -> { if (dlg[0]!=null) dlg[0].dismiss(); });

        dlg[0] = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setView(content).create();
        dlg[0].show();
        if (dlg[0].getWindow() != null) {
            dlg[0].getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            makeM3DialogMovableResizable(dlg[0], title);
        }
    }

    private void setToggleKeyboardView() {
        findViewById(R.id.toggle_keyboard_button).setOnClickListener(v -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });

        findViewById(R.id.toggle_keyboard_button).setOnLongClickListener(v -> {
            toggleTerminalToolbar();
            return true;
        });
    }

    private void setDrawerThemeAndColorControls() {
        com.google.android.material.switchmaterial.SwitchMaterial themeSwitch = findViewById(R.id.drawer_theme_switch);
        android.widget.ImageButton colorButton = findViewById(R.id.drawer_color_button);
        if (themeSwitch != null) {
            String mode = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .getString(com.termux.app.theme.M3ThemeHelper.PREF_MODE, "system");
            boolean dark = "dark".equals(mode) || ("system".equals(mode) && (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES);
            themeSwitch.setOnCheckedChangeListener(null);
            themeSwitch.setChecked(dark);
            themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                String newMode = isChecked ? "dark" : "light";
                String current = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                    .getString(com.termux.app.theme.M3ThemeHelper.PREF_MODE, "system");
                if (newMode.equals(current)) return;
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                    .edit().putString(com.termux.app.theme.M3ThemeHelper.PREF_MODE, newMode).apply();
                com.termux.app.theme.M3ThemeHelper.applyMode(this);
                mAppliedM3Accent = null; mAppliedM3Mode = null;
                getWindow().setWindowAnimations(0);
                recreate();
            });
        }
        if (colorButton != null) {
            colorButton.setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(this, SettingsActivity.class);
                i.putExtra("m3_open_appearance", true);
                startActivity(i);
            });
        }
    }

    private void setPackageManagerButtonView() {
        android.widget.ImageButton b = findViewById(R.id.package_manager_button);
        if (b != null) b.setOnClickListener(v -> showPackageManagerDialog());
    }

    private void refreshSessionStrip() {
        View container = findViewById(R.id.session_strip_container);
        LinearLayout strip = findViewById(R.id.session_strip);
        if (container == null || strip == null) return;
        if (mTermuxService == null) { container.setVisibility(View.GONE); return; }
        List<TermuxSession> sessions = mTermuxService.getTermuxSessions();
        if (sessions == null || sessions.isEmpty()) { container.setVisibility(View.GONE); strip.removeAllViews(); return; }
        container.setVisibility(View.VISIBLE);
        strip.removeAllViews();
        TerminalSession current = getCurrentSession();
        int primary = com.termux.shared.theme.ThemeUtils.getSystemAttrColor(this, com.google.android.material.R.attr.colorPrimary, 0xFF2E7D32);
        int onPrimary = com.termux.shared.theme.ThemeUtils.getSystemAttrColor(this, com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF);
        int onSurface = com.termux.shared.theme.ThemeUtils.getSystemAttrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFF222222);
        int surface = com.termux.shared.theme.ThemeUtils.getSystemAttrColor(this, com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF);
        for (int i = 0; i < sessions.size(); i++) {
            TermuxSession ts = sessions.get(i);
            TerminalSession s = ts.getTerminalSession();
            com.google.android.material.card.MaterialCardView chip = (com.google.android.material.card.MaterialCardView) getLayoutInflater().inflate(R.layout.m3_session_chip, strip, false);
            TextView title = chip.findViewById(R.id.session_chip_title);
            TextView sub = chip.findViewById(R.id.session_chip_sub);
            String name = (s.mSessionName != null && !s.mSessionName.trim().isEmpty()) ? s.mSessionName : ("Session " + (i + 1));
            title.setText(name);
            String t = s.getTitle();
            sub.setText((t == null || t.trim().isEmpty()) ? getString(R.string.m3_session_running) : t);
            boolean active = s == current;
            chip.setCardBackgroundColor(active ? primary : surface);
            chip.setStrokeColor(primary);
            title.setTextColor(active ? onPrimary : onSurface);
            sub.setTextColor(active ? onPrimary : onSurface);
            chip.setOnClickListener(v -> {
                if (mTermuxTerminalSessionActivityClient != null) mTermuxTerminalSessionActivityClient.setCurrentSession(s);
                getDrawer().closeDrawers();
            });
            strip.addView(chip);
        }
    }

    private void showPackageManagerDialog() {
        android.view.View view = getLayoutInflater().inflate(R.layout.m3_package_manager_dialog, null, false);
        android.widget.EditText search = view.findViewById(R.id.m3_pkg_search);
        android.widget.ProgressBar progress = view.findViewById(R.id.m3_pkg_progress);
        android.widget.TextView status = view.findViewById(R.id.m3_pkg_status);
        android.widget.LinearLayout list = view.findViewById(R.id.m3_pkg_list);
        com.google.android.material.button.MaterialButton refresh = view.findViewById(R.id.m3_pkg_refresh);
        com.google.android.material.button.MaterialButton openRepo = view.findViewById(R.id.m3_pkg_open_repo);

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(view)
            .create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        Runnable load = () -> loadPackagesIntoDialog(search, progress, status, list);
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) { load.run(); }
        });
        refresh.setOnClickListener(v -> load.run());
        openRepo.setOnClickListener(v -> com.termux.shared.interact.ShareUtils.openUrl(this, "https://packages.termux.dev/"));
        dialog.show();
        load.run();
    }

    private void loadPackagesIntoDialog(android.widget.EditText search, android.widget.ProgressBar progress, android.widget.TextView status, android.widget.LinearLayout list) {
        String q = search.getText() == null ? "" : search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        progress.setVisibility(View.VISIBLE);
        status.setText(R.string.m3_packages_loading);
        list.removeAllViews();
        new Thread(() -> {
            java.util.ArrayList<String> pkgs = new java.util.ArrayList<>();
            java.io.File statusFile = new java.io.File(com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/var/lib/dpkg/status");
            if (statusFile.isFile()) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(statusFile))) {
                    String line; String name = null; boolean installed = false;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("Package: ")) { name = line.substring(9).trim(); installed = false; }
                        else if (line.startsWith("Status: ") && line.contains("installed")) installed = true;
                        else if (line.isEmpty()) { if (name != null && installed) pkgs.add(name); name = null; installed = false; }
                    }
                    if (name != null && installed) pkgs.add(name);
                } catch (Exception ignored) {}
            }
            java.util.Collections.sort(pkgs);
            java.util.ArrayList<String> filtered = new java.util.ArrayList<>();
            for (String p : pkgs) if (q.isEmpty() || p.toLowerCase(java.util.Locale.ROOT).contains(q)) { filtered.add(p); if (filtered.size() >= 120) break; }
            runOnUiThread(() -> {
                if (isFinishing()) return;
                progress.setVisibility(View.GONE);
                status.setText(getString(R.string.m3_packages_installed_count, pkgs.size()));
                if (filtered.isEmpty()) {
                    TextView tv = new TextView(this);
                    tv.setText(q.isEmpty() ? R.string.m3_packages_none : R.string.m3_packages_no_match);
                    tv.setPadding(0, dp(8), 0, dp(8));
                    list.addView(tv);
                } else {
                    for (String p : filtered) {
                        TextView tv = new TextView(this);
                        tv.setText(p);
                        tv.setTextColor(com.termux.shared.theme.ThemeUtils.getSystemAttrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFF222222));
                        tv.setPadding(dp(12), dp(10), dp(12), dp(10));
                        tv.setBackgroundResource(R.drawable.m3_menu_pill);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        lp.bottomMargin = dp(8);
                        tv.setLayoutParams(lp);
                        tv.setOnClickListener(v -> com.termux.shared.interact.ShareUtils.openUrl(this, "https://packages.termux.dev/search?term=" + android.net.Uri.encode(p)));
                        list.addView(tv);
                    }
                }
            });
        }).start();
    }





    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().closeDrawers();
        } else {
            finishActivityIfNotFinishing();
        }
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!TermuxActivity.this.isFinishing()) {
            finish();
        }
    }

    /** Show M3 styled toast and dismiss the last one if still visible. */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        android.view.View v = getLayoutInflater().inflate(R.layout.m3_toast, null);
        ((TextView) v.findViewById(R.id.m3_toast_text)).setText(text);
        mLastToast = new Toast(getApplicationContext());
        mLastToast.setDuration(longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(96));
        mLastToast.setView(v);
        mLastToast.show();
    }




    private androidx.appcompat.app.AlertDialog mContextMenuDialog;

    private void addContextMenuItem(LinearLayout container, int iconRes, String text, boolean enabled, boolean checkable, boolean checked, Runnable action) {
        View item = getLayoutInflater().inflate(R.layout.m3_context_menu_item, container, false);
        ((android.widget.ImageView) item.findViewById(R.id.menu_item_icon)).setImageResource(iconRes);
        ((TextView) item.findViewById(R.id.menu_item_text)).setText(text);
        com.google.android.material.switchmaterial.SwitchMaterial sw = item.findViewById(R.id.menu_item_switch);
        if (checkable) { sw.setVisibility(View.VISIBLE); sw.setChecked(checked); sw.setClickable(false); }
        item.setEnabled(enabled); item.setAlpha(enabled ? 1f : 0.5f);
        item.setOnClickListener(v -> { if (mContextMenuDialog != null) mContextMenuDialog.dismiss(); action.run(); });
        container.addView(item);
    }

    public void showM3MessageDialog(String title, String message, int iconRes) {
        View content = getLayoutInflater().inflate(R.layout.m3_context_menu, null, false);
        LinearLayout items = content.findViewById(R.id.m3_context_menu_items);
        View header = getLayoutInflater().inflate(R.layout.m3_context_menu_item, items, false);
        ((android.widget.ImageView) header.findViewById(R.id.menu_item_icon)).setImageResource(iconRes);
        ((TextView) header.findViewById(R.id.menu_item_text)).setText(title);
        header.setClickable(false); header.setFocusable(false);
        items.addView(header);
        TextView body = new TextView(this);
        body.setText(message);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad/2, pad, pad);
        body.setTextColor(com.termux.shared.theme.ThemeUtils.getSystemAttrColor(this, R.attr.colorOnSurface, 0xFF222222));
        items.addView(body);
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(this).setView(content).create();
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int margin = (int) (8 * getResources().getDisplayMetrics().density);
            d.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - margin * 2, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    public void showSessionContextMenu(TerminalSession session) {
        if (session == null) return;
        View content = getLayoutInflater().inflate(R.layout.m3_context_menu, null, false);
        LinearLayout items = content.findViewById(R.id.m3_context_menu_items);
        boolean isCurrent = session == getCurrentSession();
        if (!isCurrent) addContextMenuItem(items, R.drawable.ic_m3_terminal, getString(R.string.m3_session_switch), true, false, false, () -> {
            mTermuxTerminalSessionActivityClient.setCurrentSession(session);
            getDrawer().closeDrawers();
        });
        addContextMenuItem(items, R.drawable.ic_m3_edit, getString(R.string.title_rename_session), true, false, false, () -> mTermuxTerminalSessionActivityClient.renameSession(session));
        addContextMenuItem(items, R.drawable.ic_m3_restart, getString(R.string.action_reset_terminal), true, false, false, () -> onResetTerminalSession(session));
        addContextMenuItem(items, R.drawable.ic_m3_power, getResources().getString(R.string.action_kill_process, session.getPid()), session.isRunning(), false, false, () -> showKillSessionDialog(session));
        mContextMenuDialog = new MaterialAlertDialogBuilder(this).setView(content).create();
        mContextMenuDialog.show();
        if (mContextMenuDialog.getWindow() != null) {
            mContextMenuDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int margin = (int) (8 * getResources().getDisplayMetrics().density);
            mContextMenuDialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - margin * 2, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    public void showTermuxContextMenu() {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;
        View content = getLayoutInflater().inflate(R.layout.m3_context_menu, null, false);
        LinearLayout items = content.findViewById(R.id.m3_context_menu_items);
        boolean autoFillEnabled = mTerminalView.isAutoFillEnabled();
        addContextMenuItem(items, R.drawable.ic_m3_link, getString(R.string.action_select_url), true, false, false, () -> mTermuxTerminalViewClient.showUrlSelection());
        addContextMenuItem(items, R.drawable.ic_m3_share, getString(R.string.action_share_transcript), true, false, false, () -> mTermuxTerminalViewClient.shareSessionTranscript());
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            addContextMenuItem(items, R.drawable.ic_m3_share, getString(R.string.action_share_selected_text), true, false, false, () -> mTermuxTerminalViewClient.shareSelectedText());
        if (autoFillEnabled) addContextMenuItem(items, R.drawable.ic_m3_person, getString(R.string.action_autofill_username), true, false, false, () -> mTerminalView.requestAutoFillUsername());
        if (autoFillEnabled) addContextMenuItem(items, R.drawable.ic_m3_person, getString(R.string.action_autofill_password), true, false, false, () -> mTerminalView.requestAutoFillPassword());
        addContextMenuItem(items, R.drawable.ic_m3_restart, getString(R.string.action_reset_terminal), true, false, false, () -> onResetTerminalSession(currentSession));
        addContextMenuItem(items, R.drawable.ic_m3_power, getResources().getString(R.string.action_kill_process, currentSession.getPid()), currentSession.isRunning(), false, false, () -> showKillSessionDialog(currentSession));
        addContextMenuItem(items, R.drawable.ic_m3_palette, getString(R.string.action_style_terminal), true, false, false, this::showStylingDialog);
        addContextMenuItem(items, R.drawable.ic_m3_lightbulb, getString(R.string.action_toggle_keep_screen_on), true, true, mPreferences.shouldKeepScreenOn(), this::toggleKeepScreenOn);
        addContextMenuItem(items, R.drawable.ic_m3_help, getString(R.string.action_open_help), true, false, false, () -> ActivityUtils.startActivity(this, new Intent(this, HelpActivity.class)));
        addContextMenuItem(items, R.drawable.ic_m3_settings, getString(R.string.action_open_settings), true, false, false, () -> ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class)));
        addContextMenuItem(items, R.drawable.ic_m3_report, getString(R.string.action_report_issue), true, false, false, () -> mTermuxTerminalViewClient.reportIssueFromTranscript());
        mContextMenuDialog = new MaterialAlertDialogBuilder(this).setView(content).create();
        mContextMenuDialog.show();
        if (mContextMenuDialog.getWindow() != null) {
            mContextMenuDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int margin = (int) (8 * getResources().getDisplayMetrics().density);
            int width = getResources().getDisplayMetrics().widthPixels - margin * 2;
            mContextMenuDialog.getWindow().setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            mContextMenuDialog.getWindow().setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        }
    }


    private void applyM3ThemeIfChanged(boolean forceRecreate) {
        android.content.SharedPreferences p = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        String accent = p.getString(M3ThemeHelper.PREF_ACCENT, "default");
        String mode = p.getString(M3ThemeHelper.PREF_MODE, "system");
        String lang = p.getString(M3ThemeHelper.PREF_LANGUAGE, "system");
        boolean first = mAppliedM3Accent == null || mAppliedM3Mode == null || mAppliedM3Language == null;
        boolean changed = !accent.equals(mAppliedM3Accent) || !mode.equals(mAppliedM3Mode) || !lang.equals(mAppliedM3Language);
        if (!forceRecreate && !changed) return;
        mAppliedM3Accent = accent; mAppliedM3Mode = mode; mAppliedM3Language = lang;
        if (forceRecreate || (!first && changed)) {
            M3ThemeHelper.applyMode(this);
            getWindow().setWindowAnimations(0);
            recreate();
            return;
        }
        applyExtraKeysFloatingMode();
        refreshSessionStrip();
    }

    @Override
    public void onM3ThemeChanged() { applyM3ThemeIfChanged(false); }

    public void reloadTerminalStyling() {
        if (mTermuxTerminalSessionActivityClient != null) mTermuxTerminalSessionActivityClient.checkForFontAndColors();
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onReloadActivityStyling();
        if (mTerminalView != null) mTerminalView.invalidate();
    }
    /** Hook system menu to show context menu instead. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        showTermuxContextMenu();
        return false;
    }


    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        View cv = getLayoutInflater().inflate(com.termux.shared.R.layout.dialog_show_message, null, false);
        ((android.widget.TextView)cv.findViewById(com.termux.shared.R.id.dialog_title)).setText(getString(R.string.action_kill_process, session.getPid()));
        ((android.widget.TextView)cv.findViewById(com.termux.shared.R.id.dialog_message)).setText(R.string.title_confirm_kill_process);
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(this).setView(cv)
            .setPositiveButton(R.string.action_kill_process_confirm, (dialog,id)->{dialog.dismiss(); session.finishIfRunning();})
            .setNegativeButton(android.R.string.cancel, null).create();
        d.show();
        if (d.getWindow()!=null) d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        com.termux.shared.termux.interact.TextInputDialogUtils.stackDialogButtons(d);
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);

            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    private void showStylingDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            View sv = getLayoutInflater().inflate(com.termux.shared.R.layout.dialog_show_message, null, false);
            ((android.widget.TextView)sv.findViewById(com.termux.shared.R.id.dialog_title)).setText(R.string.action_style_terminal);
            ((android.widget.TextView)sv.findViewById(com.termux.shared.R.id.dialog_message)).setText(R.string.error_styling_not_installed);
            androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(this).setView(sv)
                .setPositiveButton(R.string.action_styling_install, (dialog, which) -> ActivityUtils.startActivity(this, new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))))
                .setNegativeButton(android.R.string.cancel, null).create();
            d.show();
            if (d.getWindow() != null) d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            com.termux.shared.termux.interact.TextInputDialogUtils.stackDialogButtons(d);
        }
    }
    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }



    /**
     * For processes to access primary external storage (/sdcard, /storage/emulated/0, ~/storage/shared),
     * termux needs to be granted legacy WRITE_EXTERNAL_STORAGE or MANAGE_EXTERNAL_STORAGE permissions
     * if targeting targetSdkVersion 30 (android 11) and running on sdk 30 (android 11) and higher.
     */
    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {
            @Override
            public void run() {
                // Do not ask for permission again
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;

                // If permission is granted, then also setup storage symlinks.
                if(PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    TermuxActivity.this, requestCode, true, !isPermissionCallback)) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));

                    TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
                }
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: "  + resultCode + ", data: "  + IntentUtils.getIntentString(data));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: "  + Arrays.toString(permissions) + ", grantResults: "  + Arrays.toString(grantResults));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }



    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    public DrawerLayout getDrawer() {
        return (DrawerLayout) findViewById(R.id.drawer_layout);
    }


    public ViewPager getTerminalToolbarViewPager() {
        return (ViewPager) findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public boolean isTerminalViewSelected() {
        ViewPager p = getTerminalToolbarViewPager();
        return p == null || p.getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        ViewPager p = getTerminalToolbarViewPager();
        return p != null && p.getCurrentItem() == 1;
    }


    public void termuxSessionListNotifyUpdated() {
        mTermuxSessionListViewController.notifyDataSetChanged();
        refreshSessionStrip();
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }



    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (mTerminalView != null)
            return mTerminalView.getCurrentSession();
        else
            return null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }




    public static void updateTermuxActivityStyling(Context context, boolean recreateActivity) {
        // Make sure that terminal styling is always applied.
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity);
        context.sendBroadcast(stylingIntent);
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);

        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter);
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        if (intent == null) return;

        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            if (mIsVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent);

                switch (intent.getAction()) {
                    case TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH:
                        Logger.logDebug(LOG_TAG, "Received intent to notify app crash");
                        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG);
                        return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                        reloadActivityStyling(intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
                        return;
                    case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                        requestStoragePermission(false);
                        return;
                    default:
                }
            }
        }
    }

    private void reloadActivityStyling(boolean recreateActivity) {
        if (mProperties != null) {
            reloadProperties();

            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }

            // M3ThemeHelper controls night mode.
        }

        setMargins();
        setTerminalToolbarHeight();
        applyExtraKeysFloatingMode();

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadActivityStyling();

        // To change the activity and drawer theme, activity needs to be recreated.
        // It will destroy the activity, including all stored variables and views, and onCreate()
        // will be called again. Extra keys input text, terminal sessions and transcripts will be preserved.
        if (recreateActivity) {
            Logger.logDebug(LOG_TAG, "Recreating activity");
            TermuxActivity.this.recreate();
        }
    }



    public static void startTermuxActivity(@NonNull final Context context) {
        ActivityUtils.startActivity(context, newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

}
