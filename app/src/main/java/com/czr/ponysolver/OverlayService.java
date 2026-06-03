package com.czr.ponysolver;

import android.app.Service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.content.Context;
import android.graphics.PixelFormat;

public class OverlayService extends Service {
    public static final String ACTION_SHOW = "com.czr.ponysolver.SHOW";
    public static final String ACTION_STOP = "com.czr.ponysolver.STOP";
    public static final String EXTRA_XS = "xs";
    public static final String EXTRA_YS = "ys";
    public static final String EXTRA_IMAGE_W = "image_w";
    public static final String EXTRA_IMAGE_H = "image_h";

    private WindowManager windowManager;
    private View overlayView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            removeOverlay();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_SHOW.equals(intent.getAction())) {
            show(intent);
        }
        return START_NOT_STICKY;
    }

    private void show(Intent intent) {
        if (!Settings.canDrawOverlays(this)) return;
        removeOverlay();
        float[] xs = intent.getFloatArrayExtra(EXTRA_XS);
        float[] ys = intent.getFloatArrayExtra(EXTRA_YS);
        int imgW = intent.getIntExtra(EXTRA_IMAGE_W, 1);
        int imgH = intent.getIntExtra(EXTRA_IMAGE_H, 1);
        if (xs == null || ys == null || xs.length == 0 || xs.length != ys.length) return;

        windowManager = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        overlayView = new AnswerOverlayView(this, xs, ys, imgW, imgH);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        windowManager.addView(overlayView, lp);

        // Auto-hide after 90 seconds to avoid a stale overlay.
        handler.postDelayed(() -> {
            removeOverlay();
            stopSelf();
        }, 90_000);
    }

    private void removeOverlay() {
        handler.removeCallbacksAndMessages(null);
        if (windowManager != null && overlayView != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
        }
        overlayView = null;
    }

    @Override public void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }

    static class AnswerOverlayView extends View {
        private final float[] xs;
        private final float[] ys;
        private final int imageW;
        private final int imageH;
        private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public AnswerOverlayView(Context context, float[] xs, float[] ys, int imageW, int imageH) {
            super(context);
            this.xs = xs;
            this.ys = ys;
            this.imageW = Math.max(1, imageW);
            this.imageH = Math.max(1, imageH);
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(Color.argb(115, 255, 0, 0));
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(5f);
            ringPaint.setColor(Color.argb(230, 255, 0, 0));
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(32f);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float sx = getWidth() / (float) imageW;
            float sy = getHeight() / (float) imageH;
            float r = Math.max(22f, Math.min(getWidth(), getHeight()) * 0.022f);
            for (int i = 0; i < xs.length; i++) {
                float x = xs[i] * sx;
                float y = ys[i] * sy;
                canvas.drawCircle(x, y, r, circlePaint);
                canvas.drawCircle(x, y, r, ringPaint);
                canvas.drawText(String.valueOf(i + 1), x, y + 11f, textPaint);
            }
        }
    }
}
