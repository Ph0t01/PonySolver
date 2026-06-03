package com.czr.ponysolver;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_PICK_IMAGE = 1001;
    private PuzzleView puzzleView;
    private EditText nInput;
    private TextView resultText;
    private BoardSolver.SolveResult lastResult;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        handleIncomingIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingIntent(intent);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));

        TextView title = new TextView(this);
        title.setText("找小马求解器：导入截图 → 框选彩色棋盘 → 求解 → 可选悬浮答案");
        title.setTextSize(15f);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        Button pick = new Button(this);
        pick.setText("导入截图");
        pick.setOnClickListener(v -> pickImage());
        controls.addView(pick, new LinearLayout.LayoutParams(0, -2, 1));

        nInput = new EditText(this);
        nInput.setHint("n");
        nInput.setText("10");
        nInput.setSingleLine(true);
        nInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        controls.addView(nInput, new LinearLayout.LayoutParams(dp(70), -2));

        Button solve = new Button(this);
        solve.setText("求解");
        solve.setOnClickListener(v -> solveNow());
        controls.addView(solve, new LinearLayout.LayoutParams(0, -2, 1));

        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls2 = new LinearLayout(this);
        controls2.setOrientation(LinearLayout.HORIZONTAL);

        Button overlay = new Button(this);
        overlay.setText("显示悬浮答案");
        overlay.setOnClickListener(v -> showOverlay());
        controls2.addView(overlay, new LinearLayout.LayoutParams(0, -2, 1));

        Button stopOverlay = new Button(this);
        stopOverlay.setText("隐藏悬浮");
        stopOverlay.setOnClickListener(v -> stopOverlay());
        controls2.addView(stopOverlay, new LinearLayout.LayoutParams(0, -2, 1));

        root.addView(controls2, new LinearLayout.LayoutParams(-1, -2));

        puzzleView = new PuzzleView(this);
        root.addView(puzzleView, new LinearLayout.LayoutParams(-1, 0, 1));

        ScrollView scroll = new ScrollView(this);
        resultText = new TextView(this);
        resultText.setTextSize(15f);
        resultText.setText("使用说明：\n1. 在抖音小程序截图。\n2. 导入截图，输入棋盘大小 n。\n3. 拖动红框，只框选彩色棋盘。\n4. 点击求解。\n5. 悬浮答案只画点，不自动点击。\n");
        scroll.addView(resultText);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(170)));

        setContentView(root);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null && type.startsWith("image/")) {
            Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri != null) loadBitmapFromUri(imageUri);
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择游戏截图"), REQ_PICK_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) loadBitmapFromUri(uri);
        }
    }

    private void loadBitmapFromUri(Uri uri) {
        try {
            ContentResolver cr = getContentResolver();
            InputStream is = cr.openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (is != null) is.close();
            if (bitmap == null) throw new Exception("图片解码失败");
            puzzleView.setBitmap(bitmap);
            lastResult = null;
            resultText.setText("已导入截图：" + bitmap.getWidth() + "×" + bitmap.getHeight() + "\n请确认红框只覆盖彩色棋盘，然后点击求解。\n");
        } catch (Exception e) {
            Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int readN() throws Exception {
        String s = nInput.getText().toString().trim();
        if (s.isEmpty()) throw new Exception("请填写棋盘大小 n。");
        int n = Integer.parseInt(s);
        if (n < 4 || n > 15) throw new Exception("n 建议在 4 到 15 之间。");
        puzzleView.setN(n);
        return n;
    }

    private void solveNow() {
        try {
            int n = readN();
            Bitmap bitmap = puzzleView.getBitmap();
            RectF board = puzzleView.getBoardRectImage();
            BoardSolver.SolveResult result = BoardSolver.solveFromBitmap(bitmap, board, n);
            lastResult = result;
            puzzleView.setSolution(result.solution);
            String text = result.message + "\n\n" + BoardSolver.solutionText(result.solution, n);
            text += "\n提示：如果红点明显错位，请微调红框边界后重新求解。";
            resultText.setText(text);
        } catch (Exception e) {
            lastResult = null;
            puzzleView.clearSolution();
            resultText.setText("求解失败：" + e.getMessage() + "\n\n建议：\n1. n 是否正确；\n2. 红框是否只包含彩色棋盘；\n3. 不要包含左侧/底部坐标数字；\n4. 截图要清晰完整。\n");
        }
    }

    private void showOverlay() {
        try {
            if (lastResult == null) throw new Exception("请先求解成功。 ");
            Bitmap bitmap = puzzleView.getBitmap();
            List<PointF> pts = puzzleView.getSolutionImagePoints();
            if (bitmap == null || pts.isEmpty()) throw new Exception("没有可显示的答案点。");

            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先允许悬浮窗权限。授权后返回本应用再点击显示悬浮答案。", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }
            Intent i = new Intent(this, OverlayService.class);
            i.setAction(OverlayService.ACTION_SHOW);
            i.putExtra(OverlayService.EXTRA_IMAGE_W, bitmap.getWidth());
            i.putExtra(OverlayService.EXTRA_IMAGE_H, bitmap.getHeight());
            float[] xs = new float[pts.size()];
            float[] ys = new float[pts.size()];
            for (int idx = 0; idx < pts.size(); idx++) { xs[idx] = pts.get(idx).x; ys[idx] = pts.get(idx).y; }
            i.putExtra(OverlayService.EXTRA_XS, xs);
            i.putExtra(OverlayService.EXTRA_YS, ys);
            startService(i);
            Toast.makeText(this, "悬浮答案已显示。现在切回抖音，按红点手动双击。", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopOverlay() {
        Intent i = new Intent(this, OverlayService.class);
        i.setAction(OverlayService.ACTION_STOP);
        startService(i);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
