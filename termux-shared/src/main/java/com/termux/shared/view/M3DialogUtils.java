package com.termux.shared.view;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;

public final class M3DialogUtils {

    private M3DialogUtils() {}

    public static void applyM3Window(AlertDialog dialog, boolean movableResizable) {
        if (dialog == null) return;
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                WindowManager.LayoutParams lp = window.getAttributes();
                int margin = dp(dialog.getContext(), 12);
                int screenW = dialog.getContext().getResources().getDisplayMetrics().widthPixels;
                if (lp.width == WindowManager.LayoutParams.MATCH_PARENT || lp.width > screenW - margin*2) {
                    lp.width = screenW - margin*2;
                }
                window.setAttributes(lp);
            }
            try { com.termux.shared.termux.interact.TextInputDialogUtils.stackDialogButtons(dialog); } catch (Throwable ignored) {}
            if (movableResizable) makeMovableResizable(dialog, null);
        });
    }

    public static void makeMovableResizable(AlertDialog dialog, View dragHandle) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        dialog.setCanceledOnTouchOutside(true);
        final boolean[] resizing = new boolean[1];
        View handle = dragHandle;
        if (handle == null) handle = window.getDecorView().findViewById(android.R.id.title);
        if (handle != null) {
            final float[] last = new float[4];
            handle.setOnTouchListener((v, ev) -> {
                WindowManager.LayoutParams wlp = window.getAttributes();
                if (ev.getPointerCount() >= 2 || resizing[0]) return false;
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
        }
        View decor = window.getDecorView();
        decor.post(() -> installEdgeResize(window, decor, resizing));
    }

    private static void installEdgeResize(Window window, View decor, boolean[] resizing) {
        Context c = decor.getContext();
        final int edge = dp(c,18), minW = dp(c,280), minH = dp(c,180);
        decor.setOnTouchListener((v, ev) -> {
            int action = ev.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                if (resizing[0]) { resizing[0]=false; WindowManager.LayoutParams p=window.getAttributes(); p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL; window.setAttributes(p); window.setDimAmount(0.45f); }
                return false;
            }
            WindowManager.LayoutParams wlp = window.getAttributes();
            int w=wlp.width>0?wlp.width:decor.getWidth(), h=wlp.height>0?wlp.height:decor.getHeight();
            float x=ev.getX(), y=ev.getY();
            boolean left=x<=edge,right=x>=w-edge,top=y<=edge,bottom=y>=h-edge;
            if (!left&&!right&&!top&&!bottom&&!resizing[0]) return false;
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                resizing[0]=true; WindowManager.LayoutParams p=window.getAttributes(); p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL; window.setAttributes(p); window.setDimAmount(0.15f);
                v.setTag(new float[]{ev.getRawX(),ev.getRawY(),w,h,wlp.x,wlp.y,left?1:0,right?1:0,top?1:0,bottom?1:0}); return true;
            }
            if (action == android.view.MotionEvent.ACTION_MOVE && v.getTag() instanceof float[]) {
                float[] s=(float[])v.getTag(); float dx=ev.getRawX()-s[0], dy=ev.getRawY()-s[1];
                int nw=(int)s[2],nh=(int)s[3],nx=(int)s[4],ny=(int)s[5], maxW=c.getResources().getDisplayMetrics().widthPixels-dp(c,8);
                if (s[6]==1){nw=clamp((int)s[2]-(int)dx,minW,maxW); nx=(int)s[4]+((int)s[2]-nw)/2;}
                if (s[7]==1){nw=clamp((int)s[2]+(int)dx,minW,maxW); nx=(int)s[4]-((int)s[2]-nw)/2;}
                if (s[8]==1){nh=Math.max(minH,(int)s[3]-(int)dy); ny=(int)s[5]+((int)s[3]-nh)/2;}
                if (s[9]==1){nh=Math.max(minH,(int)s[3]+(int)dy); ny=(int)s[5]-((int)s[3]-nh)/2;}
                wlp.width=nw; wlp.height=nh; wlp.x=nx; wlp.y=ny; window.setAttributes(wlp); return true;
            }
            return resizing[0];
        });
    }

    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    private static int dp(Context c, int dp){ return (int)(dp * c.getResources().getDisplayMetrics().density); }
}
