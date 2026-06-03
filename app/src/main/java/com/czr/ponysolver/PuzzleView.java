package com.czr.ponysolver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class PuzzleView extends View {
    private Bitmap bitmap;
    private final Matrix imageToView = new Matrix();
    private final Matrix viewToImage = new Matrix();
    private RectF boardRectImage;
    private int n = 10;
    private List<BoardSolver.Cell> solution = new ArrayList<>();

    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint rectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int dragMode = 0; // 0 none, 1 move, 2 LT, 3 RT, 4 RB, 5 LB
    private float lastX, lastY;
    private static final float HANDLE_R = 34f;

    public PuzzleView(Context context) { super(context); init(); }
    public PuzzleView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setBackgroundColor(Color.rgb(245,245,245));
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(5f);
        rectPaint.setColor(Color.rgb(255, 64, 64));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2f);
        gridPaint.setColor(Color.argb(150, 0, 0, 0));
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.argb(190, 255, 0, 0));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(34f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(Color.argb(200, 30, 144, 255));
    }

    public void setBitmap(Bitmap b) {
        this.bitmap = b;
        this.solution.clear();
        if (b != null) this.boardRectImage = BoardSolver.autoDetectBoard(b);
        updateMatrices();
        invalidate();
    }

    public Bitmap getBitmap() { return bitmap; }

    public void setN(int n) { this.n = n; invalidate(); }

    public RectF getBoardRectImage() { return boardRectImage == null ? null : new RectF(boardRectImage); }

    public void setSolution(List<BoardSolver.Cell> sol) {
        this.solution = sol == null ? new ArrayList<>() : new ArrayList<>(sol);
        invalidate();
    }

    public void clearSolution() {
        this.solution.clear();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        updateMatrices();
    }

    private void updateMatrices() {
        imageToView.reset();
        if (bitmap == null || getWidth() <= 0 || getHeight() <= 0) return;
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();
        float vw = getWidth();
        float vh = getHeight();
        float scale = Math.min(vw / bw, vh / bh);
        float dx = (vw - bw * scale) / 2f;
        float dy = (vh - bh * scale) / 2f;
        imageToView.postScale(scale, scale);
        imageToView.postTranslate(dx, dy);
        imageToView.invert(viewToImage);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.DKGRAY);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(40f);
            canvas.drawText("导入抖音小游戏截图", getWidth()/2f, getHeight()/2f, p);
            return;
        }
        updateMatrices();
        canvas.drawBitmap(bitmap, imageToView, imagePaint);
        if (boardRectImage != null) drawBoardOverlay(canvas);
    }

    private void drawBoardOverlay(Canvas canvas) {
        RectF rv = mapRect(boardRectImage, imageToView);
        canvas.drawRect(rv, rectPaint);
        if (n >= 2) {
            for (int i = 1; i < n; i++) {
                float x = rv.left + rv.width() * i / n;
                float y = rv.top + rv.height() * i / n;
                canvas.drawLine(x, rv.top, x, rv.bottom, gridPaint);
                canvas.drawLine(rv.left, y, rv.right, y, gridPaint);
            }
        }
        drawHandle(canvas, rv.left, rv.top);
        drawHandle(canvas, rv.right, rv.top);
        drawHandle(canvas, rv.right, rv.bottom);
        drawHandle(canvas, rv.left, rv.bottom);

        if (!solution.isEmpty()) {
            float cw = rv.width() / n;
            float ch = rv.height() / n;
            int idx = 1;
            for (BoardSolver.Cell cell : solution) {
                float x = rv.left + (cell.c + 0.5f) * cw;
                float y = rv.top + (cell.r + 0.5f) * ch;
                canvas.drawCircle(x, y, Math.min(cw, ch) * 0.26f, dotPaint);
                canvas.drawText(String.valueOf(idx++), x, y + 12f, textPaint);
            }
        }
    }

    private void drawHandle(Canvas canvas, float x, float y) { canvas.drawCircle(x, y, HANDLE_R * 0.45f, handlePaint); }

    private static RectF mapRect(RectF src, Matrix m) {
        RectF dst = new RectF(src);
        m.mapRect(dst);
        return dst;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null || boardRectImage == null) return true;
        float x = event.getX(), y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragMode = hitTest(x, y);
                lastX = x; lastY = y;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragMode != 0) {
                    float dx = x - lastX;
                    float dy = y - lastY;
                    moveBoardByViewDelta(dx, dy, dragMode);
                    lastX = x; lastY = y;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragMode = 0;
                return true;
        }
        return true;
    }

    private int hitTest(float x, float y) {
        RectF rv = mapRect(boardRectImage, imageToView);
        if (dist(x, y, rv.left, rv.top) < HANDLE_R) return 2;
        if (dist(x, y, rv.right, rv.top) < HANDLE_R) return 3;
        if (dist(x, y, rv.right, rv.bottom) < HANDLE_R) return 4;
        if (dist(x, y, rv.left, rv.bottom) < HANDLE_R) return 5;
        if (rv.contains(x, y)) return 1;
        return 0;
    }

    private float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float)Math.sqrt(dx*dx + dy*dy);
    }

    private void moveBoardByViewDelta(float dxV, float dyV, int mode) {
        float[] a = new float[]{0, 0, dxV, dyV};
        viewToImage.mapVectors(a);
        float dx = a[2], dy = a[3];
        RectF r = boardRectImage;
        if (mode == 1) {
            r.offset(dx, dy);
        } else if (mode == 2) {
            r.left += dx; r.top += dy;
        } else if (mode == 3) {
            r.right += dx; r.top += dy;
        } else if (mode == 4) {
            r.right += dx; r.bottom += dy;
        } else if (mode == 5) {
            r.left += dx; r.bottom += dy;
        }
        normalizeRect(r);
    }

    private void normalizeRect(RectF r) {
        float minSize = 80f;
        if (r.left > r.right - minSize) r.left = r.right - minSize;
        if (r.top > r.bottom - minSize) r.top = r.bottom - minSize;
        if (r.right < r.left + minSize) r.right = r.left + minSize;
        if (r.bottom < r.top + minSize) r.bottom = r.top + minSize;
        r.left = Math.max(0, Math.min(r.left, bitmap.getWidth() - minSize));
        r.top = Math.max(0, Math.min(r.top, bitmap.getHeight() - minSize));
        r.right = Math.max(r.left + minSize, Math.min(r.right, bitmap.getWidth()));
        r.bottom = Math.max(r.top + minSize, Math.min(r.bottom, bitmap.getHeight()));
    }

    public List<PointF> getSolutionImagePoints() {
        if (solution == null || solution.isEmpty() || boardRectImage == null) return new ArrayList<>();
        return BoardSolver.solutionToImagePoints(solution, boardRectImage, n);
    }
}
