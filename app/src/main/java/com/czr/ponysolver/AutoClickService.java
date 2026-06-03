package com.czr.ponysolver;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class AutoClickService extends AccessibilityService {
    public interface ClickCallback {
        void onDone(boolean ok, String message);
    }

    private static AutoClickService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final long TAP_DURATION_MS = 45;
    private static final long DOUBLE_TAP_GAP_MS = 80;
    private static final long POINT_GAP_MS = 160;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override public void onInterrupt() {}

    @Override public boolean onUnbind(android.content.Intent intent) {
        if (instance == this) instance = null;
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isReady() {
        return instance != null;
    }

    public static void doubleTapImagePoints(float[] xs, float[] ys, int imageW, int imageH, ClickCallback callback) {
        AutoClickService svc = instance;
        if (svc == null) {
            if (callback != null) callback.onDone(false, "无障碍自动点击服务未开启。");
            return;
        }
        if (xs == null || ys == null || xs.length == 0 || xs.length != ys.length) {
            if (callback != null) callback.onDone(false, "没有可点击的答案点。");
            return;
        }
        svc.runDoubleTapSequence(xs, ys, Math.max(1, imageW), Math.max(1, imageH), callback);
    }

    private void runDoubleTapSequence(float[] xs, float[] ys, int imageW, int imageH, ClickCallback callback) {
        Point realSize = getRealDisplaySize();
        float sx = realSize.x / (float) imageW;
        float sy = realSize.y / (float) imageH;
        clickPoint(xs, ys, sx, sy, 0, 0, callback);
    }

    private Point getRealDisplaySize() {
        Point p = new Point();
        try {
            WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
            Display d = wm.getDefaultDisplay();
            d.getRealSize(p);
        } catch (Exception e) {
            p.x = getResources().getDisplayMetrics().widthPixels;
            p.y = getResources().getDisplayMetrics().heightPixels;
        }
        if (p.x <= 0) p.x = getResources().getDisplayMetrics().widthPixels;
        if (p.y <= 0) p.y = getResources().getDisplayMetrics().heightPixels;
        return p;
    }

    private void clickPoint(float[] xs, float[] ys, float sx, float sy, int index, int tapIndex, ClickCallback callback) {
        if (index >= xs.length) {
            if (callback != null) callback.onDone(true, "自动双击完成。");
            return;
        }

        float x = xs[index] * sx;
        float y = ys[index] * sy;

        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();

        boolean dispatched = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                if (tapIndex == 0) {
                    handler.postDelayed(() -> clickPoint(xs, ys, sx, sy, index, 1, callback), DOUBLE_TAP_GAP_MS);
                } else {
                    handler.postDelayed(() -> clickPoint(xs, ys, sx, sy, index + 1, 0, callback), POINT_GAP_MS);
                }
            }

            @Override public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                if (callback != null) callback.onDone(false, "自动点击被系统取消，请确认游戏在前台且无障碍服务仍开启。");
            }
        }, handler);

        if (!dispatched && callback != null) {
            callback.onDone(false, "手势下发失败，请重新开启无障碍服务后再试。");
        }
    }
}
