package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.theme.ThemeUtils;
import com.termux.terminal.TerminalSession;

import java.util.List;

public class TermuxSessionsListViewController extends ArrayAdapter<TermuxSession> implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    final TermuxActivity mActivity;

    final StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
    final StyleSpan italicSpan = new StyleSpan(Typeface.ITALIC);

    public TermuxSessionsListViewController(TermuxActivity activity, List<TermuxSession> sessionList) {
        super(activity.getApplicationContext(), R.layout.item_terminal_sessions_list, sessionList);
        this.mActivity = activity;
    }

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View sessionRowView = convertView;
        if (sessionRowView == null) {
            LayoutInflater inflater = mActivity.getLayoutInflater();
            sessionRowView = inflater.inflate(R.layout.item_terminal_sessions_list, parent, false);
        }

        TextView sessionTitleView = sessionRowView.findViewById(R.id.session_title);
        TextView sessionNumberView = sessionRowView.findViewById(R.id.session_number);

        TerminalSession sessionAtRow = getItem(position).getTerminalSession();
        if (sessionAtRow == null) {
            sessionTitleView.setText(R.string.error_termux_service_start_failed_general);
            sessionNumberView.setText("?");
            return sessionRowView;
        }

        boolean isActiveSession = sessionAtRow == mActivity.getCurrentSession();
        sessionRowView.setBackground(ContextCompat.getDrawable(mActivity,
            isActiveSession ? R.drawable.m3_session_pill_active : R.drawable.m3_session_pill));

        sessionNumberView.setText(String.valueOf(position + 1));

        String name = sessionAtRow.mSessionName;
        String sessionTitle = sessionAtRow.getTitle();
        String sessionNamePart = TextUtils.isEmpty(name) ? "" : name;
        String sessionTitlePart = TextUtils.isEmpty(sessionTitle) ? "" : ((sessionNamePart.isEmpty() ? "" : " • ") + sessionTitle);
        String fullSessionTitle = sessionNamePart + sessionTitlePart;
        if (TextUtils.isEmpty(fullSessionTitle)) fullSessionTitle = mActivity.getString(R.string.action_new_session);

        SpannableString fullSessionTitleStyled = new SpannableString(fullSessionTitle);
        if (!sessionNamePart.isEmpty())
            fullSessionTitleStyled.setSpan(boldSpan, 0, sessionNamePart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!sessionTitlePart.isEmpty())
            fullSessionTitleStyled.setSpan(italicSpan, sessionNamePart.length(), fullSessionTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sessionTitleView.setText(fullSessionTitleStyled);

        boolean sessionRunning = sessionAtRow.isRunning();
        sessionTitleView.setPaintFlags(sessionRunning ? (sessionTitleView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG) : (sessionTitleView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG));

        int onContainer = ThemeUtils.getSystemAttrColor(mActivity, R.attr.m3OnPrimaryContainer, Color.BLACK);
        int onSurface = ThemeUtils.getSystemAttrColor(mActivity, R.attr.colorOnSurface, Color.BLACK);
        int errorColor = ThemeUtils.getSystemAttrColor(mActivity, R.attr.colorError, Color.RED);
        int textColor = isActiveSession ? onContainer : (sessionRunning || sessionAtRow.getExitStatus() == 0 ? onSurface : errorColor);
        sessionTitleView.setTextColor(textColor);
        sessionNumberView.setTextColor(ThemeUtils.getSystemAttrColor(mActivity, R.attr.colorOnPrimary, Color.WHITE));
        return sessionRowView;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        TermuxSession clickedSession = getItem(position);
        mActivity.getTermuxTerminalSessionClient().setCurrentSession(clickedSession.getTerminalSession());
        mActivity.getDrawer().closeDrawers();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        final TermuxSession selectedSession = getItem(position);
        mActivity.showSessionContextMenu(selectedSession.getTerminalSession());
        return true;
    }

}
