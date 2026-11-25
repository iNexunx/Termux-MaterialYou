package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;
import com.termux.view.TerminalRenderer;

/** Tiny live-preview pip of a TerminalSession. */
public class MiniTerminalPipView extends View {

    private TerminalSession mSession;
    private final TerminalRenderer mRenderer;
    private final Paint mBorderPaint = new Paint();
    private final Paint mBgPaint = new Paint();
    private boolean mIsActive = false;

    public MiniTerminalPipView(Context context) {
        super(context);
        mRenderer = new TerminalRenderer(12, Typeface.MONOSPACE);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(dp(2f));
        mBorderPaint.setAntiAlias(false);
        mBgPaint.setStyle(Paint.Style.FILL);
    }

    public void setSession(TerminalSession session) { mSession = session; invalidate(); }
    public TerminalSession getSession() { return mSession; }
    public void setActive(boolean active) { if (mIsActive == active) return; mIsActive = active; invalidate(); }
    public void notifyUpdate() { postInvalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        TerminalEmulator emulator = (mSession != null) ? mSession.getEmulator() : null;
        if (emulator == null) {
            mBgPaint.setColor(0xFF1A1A1A);
            canvas.drawRect(0, 0, getWidth(), getHeight(), mBgPaint);
        } else {
            int bgColor = emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND];
            mBgPaint.setColor(bgColor);
            canvas.drawRect(0, 0, getWidth(), getHeight(), mBgPaint);
            float rendererW = mRenderer.getFontWidth() * emulator.mColumns;
            float rendererH = mRenderer.getFontLineSpacing() * emulator.mRows;
            if (rendererW > 0 && rendererH > 0) {
                canvas.save();
                canvas.scale(getWidth() / rendererW, getHeight() / rendererH);
                mRenderer.render(emulator, canvas, 0, -1, -1, -1, -1);
                canvas.restore();
            }
        }
        int accent;
        try {
            accent = com.termux.shared.theme.ThemeUtils.getSystemAttrColor(getContext(), com.google.android.material.R.attr.colorPrimary, 0xFFBB86FC);
        } catch (Throwable t) { accent = 0xFFBB86FC; }
        mBorderPaint.setColor(mIsActive ? accent : 0xFF444444);
        float half = mBorderPaint.getStrokeWidth() / 2f;
        canvas.drawRect(half, half, getWidth() - half, getHeight() - half, mBorderPaint);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
