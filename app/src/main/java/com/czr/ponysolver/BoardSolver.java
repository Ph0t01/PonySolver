package com.czr.ponysolver;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BoardSolver {
    private BoardSolver() {}

    public static class Cell {
        public final int r;
        public final int c;
        public Cell(int r, int c) { this.r = r; this.c = c; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Cell)) return false;
            Cell x = (Cell)o; return r == x.r && c == x.c;
        }
        @Override public int hashCode() { return r * 97 + c; }
        @Override public String toString() { return "(" + r + "," + c + ")"; }
    }

    public static class SolveResult {
        public final int n;
        public final int[][] colorGrid;
        public final List<Cell> solution;
        public final String message;
        public SolveResult(int n, int[][] colorGrid, List<Cell> solution, String message) {
            this.n = n;
            this.colorGrid = colorGrid;
            this.solution = solution;
            this.message = message;
        }
    }

    public static SolveResult solveFromBitmap(Bitmap bitmap, RectF board, int n) throws Exception {
        if (bitmap == null) throw new Exception("未选择截图。");
        if (n < 4 || n > 15) throw new Exception("n 建议在 4 到 15 之间。");
        if (board == null || board.width() < n * 10 || board.height() < n * 10) throw new Exception("棋盘区域过小或未框选。只框选彩色棋盘，不要包含坐标数字。 ");

        float[] ratios = new float[]{0.16f, 0.20f, 0.24f, 0.28f, 0.32f};
        Map<String, Integer> vote = new HashMap<>();
        Map<String, List<Cell>> solMap = new HashMap<>();
        int uniqueCandidates = 0;
        int totalCandidates = 0;
        int[][] lastGrid = null;

        for (float ratio : ratios) {
            double[][] features = extractLabFeatures(bitmap, board, n, ratio);
            for (int seed = 0; seed < 8; seed++) {
                int[][] grid = kmeansGrid(features, n, seed);
                lastGrid = grid;
                List<List<Cell>> sols = solveAll(grid, 2);
                totalCandidates++;
                if (sols.size() == 1) {
                    uniqueCandidates++;
                    String key = solutionKey(sols.get(0));
                    vote.put(key, vote.containsKey(key) ? vote.get(key) + 1 : 1);
                    solMap.put(key, sols.get(0));
                }
            }
        }

        if (vote.isEmpty()) {
            throw new Exception("未得到唯一解。请检查棋盘框选是否准确，或把 n 设置正确。候选数=" + totalCandidates);
        }

        String bestKey = null;
        int bestVote = -1;
        for (Map.Entry<String, Integer> e : vote.entrySet()) {
            if (e.getValue() > bestVote) {
                bestVote = e.getValue();
                bestKey = e.getKey();
            }
        }

        if (bestVote < 3) {
            throw new Exception("识别不稳定，拒绝给出自动结果。请微调棋盘框选区域。唯一解候选=" + uniqueCandidates + "，最高票=" + bestVote);
        }

        return new SolveResult(n, lastGrid, solMap.get(bestKey), "候选唯一解 " + uniqueCandidates + "/" + totalCandidates + "，最高一致票 " + bestVote);
    }

    public static RectF autoDetectBoard(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;

        // Avoid top title bars and bottom controls if possible.
        int yStart = (int)(h * 0.12f);
        int yEnd = (int)(h * 0.92f);
        int xStart = (int)(w * 0.02f);
        int xEnd = (int)(w * 0.98f);

        for (int y = yStart; y < yEnd; y += 2) {
            for (int x = xStart; x < xEnd; x += 2) {
                int p = bitmap.getPixel(x, y);
                float[] hsv = new float[3];
                Color.colorToHSV(p, hsv);
                float s = hsv[1];
                float v = hsv[2];
                if (s > 0.12f && v > 0.45f) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX <= minX || maxY <= minY) {
            return new RectF(w * 0.05f, h * 0.28f, w * 0.95f, h * 0.78f);
        }

        float padX = Math.max(4, (maxX - minX) * 0.01f);
        float padY = Math.max(4, (maxY - minY) * 0.01f);
        RectF r = new RectF(minX - padX, minY - padY, maxX + padX, maxY + padY);
        r.left = clamp(r.left, 0, w - 1);
        r.top = clamp(r.top, 0, h - 1);
        r.right = clamp(r.right, r.left + 10, w);
        r.bottom = clamp(r.bottom, r.top + 10, h);
        return r;
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static double[][] extractLabFeatures(Bitmap bitmap, RectF board, int n, float sampleRatio) {
        double[][] feats = new double[n * n][3];
        float cellW = board.width() / n;
        float cellH = board.height() / n;
        int idx = 0;
        int radius = Math.max(2, Math.round(Math.min(cellW, cellH) * sampleRatio));

        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                int cx = Math.round(board.left + (c + 0.5f) * cellW);
                int cy = Math.round(board.top + (r + 0.5f) * cellH);
                int x0 = Math.max(0, cx - radius);
                int x1 = Math.min(bitmap.getWidth() - 1, cx + radius);
                int y0 = Math.max(0, cy - radius);
                int y1 = Math.min(bitmap.getHeight() - 1, cy + radius);
                double l = 0, a = 0, b = 0;
                int count = 0;
                for (int y = y0; y <= y1; y += 1) {
                    for (int x = x0; x <= x1; x += 1) {
                        int p = bitmap.getPixel(x, y);
                        int rr = Color.red(p), gg = Color.green(p), bb = Color.blue(p);
                        double[] lab = rgbToLab(rr, gg, bb);
                        l += lab[0]; a += lab[1]; b += lab[2]; count++;
                    }
                }
                if (count == 0) count = 1;
                feats[idx][0] = l / count;
                feats[idx][1] = a / count;
                feats[idx][2] = b / count;
                idx++;
            }
        }
        return feats;
    }

    private static int[][] kmeansGrid(double[][] feats, int n, int seed) {
        int k = n;
        int m = feats.length;
        double[][] centers = new double[k][3];

        // Deterministic farthest-first initialization with seed offset.
        int first = Math.abs(seed * 7) % m;
        centers[0] = Arrays.copyOf(feats[first], 3);
        boolean[] chosen = new boolean[m];
        chosen[first] = true;
        for (int ci = 1; ci < k; ci++) {
            double bestD = -1;
            int bestIdx = 0;
            for (int i = 0; i < m; i++) {
                if (chosen[i]) continue;
                double dmin = Double.MAX_VALUE;
                for (int j = 0; j < ci; j++) dmin = Math.min(dmin, dist2(feats[i], centers[j]));
                double bias = ((i + 31 * seed) % 17) * 1e-6;
                if (dmin + bias > bestD) { bestD = dmin + bias; bestIdx = i; }
            }
            centers[ci] = Arrays.copyOf(feats[bestIdx], 3);
            chosen[bestIdx] = true;
        }

        int[] labels = new int[m];
        Arrays.fill(labels, -1);
        for (int it = 0; it < 50; it++) {
            boolean changed = false;
            for (int i = 0; i < m; i++) {
                int best = 0;
                double bestD = Double.MAX_VALUE;
                for (int ci = 0; ci < k; ci++) {
                    double d = dist2(feats[i], centers[ci]);
                    if (d < bestD) { bestD = d; best = ci; }
                }
                if (labels[i] != best) { labels[i] = best; changed = true; }
            }
            double[][] next = new double[k][3];
            int[] counts = new int[k];
            for (int i = 0; i < m; i++) {
                int lab = labels[i];
                counts[lab]++;
                next[lab][0] += feats[i][0]; next[lab][1] += feats[i][1]; next[lab][2] += feats[i][2];
            }
            for (int ci = 0; ci < k; ci++) {
                if (counts[ci] == 0) continue;
                centers[ci][0] = next[ci][0] / counts[ci];
                centers[ci][1] = next[ci][1] / counts[ci];
                centers[ci][2] = next[ci][2] / counts[ci];
            }
            if (!changed) break;
        }
        int[][] grid = new int[n][n];
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) grid[r][c] = labels[r * n + c];
        return grid;
    }

    private static double dist2(double[] x, double[] y) {
        double d0 = x[0] - y[0], d1 = x[1] - y[1], d2 = x[2] - y[2];
        return d0*d0 + d1*d1 + d2*d2;
    }

    private static List<List<Cell>> solveAll(int[][] grid, int maxSolutions) {
        int n = grid.length;
        Map<Integer, List<Cell>> colorToCells = new HashMap<>();
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                int color = grid[r][c];
                if (!colorToCells.containsKey(color)) colorToCells.put(color, new ArrayList<Cell>());
                colorToCells.get(color).add(new Cell(r, c));
            }
        }
        if (colorToCells.size() != n) return new ArrayList<>();
        List<Integer> colors = new ArrayList<>(colorToCells.keySet());
        List<List<Cell>> solutions = new ArrayList<>();
        Map<Integer, Cell> placed = new HashMap<>();
        backtrack(n, colors, colorToCells, placed, new HashSet<Integer>(), new HashSet<Integer>(), new HashSet<Cell>(), solutions, maxSolutions);
        return solutions;
    }

    private static void backtrack(int n, List<Integer> colors, Map<Integer, List<Cell>> colorToCells,
                                  Map<Integer, Cell> placed, Set<Integer> usedRows, Set<Integer> usedCols,
                                  Set<Cell> blocked, List<List<Cell>> solutions, int maxSolutions) {
        if (solutions.size() >= maxSolutions) return;
        if (placed.size() == n) {
            List<Cell> sol = new ArrayList<>(placed.values());
            sol.sort((a, b) -> a.c != b.c ? a.c - b.c : a.r - b.r);
            solutions.add(sol);
            return;
        }
        Integer bestColor = null;
        List<Cell> bestCandidates = null;
        for (Integer color : colors) {
            if (placed.containsKey(color)) continue;
            List<Cell> cand = new ArrayList<>();
            for (Cell cell : colorToCells.get(color)) {
                if (usedRows.contains(cell.r)) continue;
                if (usedCols.contains(cell.c)) continue;
                if (blocked.contains(cell)) continue;
                cand.add(cell);
            }
            if (bestCandidates == null || cand.size() < bestCandidates.size()) {
                bestCandidates = cand;
                bestColor = color;
            }
        }
        if (bestCandidates == null || bestCandidates.isEmpty()) return;
        for (Cell cell : bestCandidates) {
            placed.put(bestColor, cell);
            Set<Integer> nr = new HashSet<>(usedRows); nr.add(cell.r);
            Set<Integer> nc = new HashSet<>(usedCols); nc.add(cell.c);
            Set<Cell> nb = new HashSet<>(blocked); nb.addAll(neighborCells(n, cell));
            backtrack(n, colors, colorToCells, placed, nr, nc, nb, solutions, maxSolutions);
            placed.remove(bestColor);
            if (solutions.size() >= maxSolutions) return;
        }
    }

    private static List<Cell> neighborCells(int n, Cell cell) {
        List<Cell> out = new ArrayList<>();
        for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
            int rr = cell.r + dr, cc = cell.c + dc;
            if (rr >= 0 && rr < n && cc >= 0 && cc < n) out.add(new Cell(rr, cc));
        }
        return out;
    }

    private static String solutionKey(List<Cell> sol) {
        List<Cell> s = new ArrayList<>(sol);
        s.sort((a, b) -> a.c != b.c ? a.c - b.c : a.r - b.r);
        StringBuilder sb = new StringBuilder();
        for (Cell cell : s) sb.append(cell.r).append(',').append(cell.c).append(';');
        return sb.toString();
    }

    public static List<PointF> solutionToImagePoints(List<Cell> sol, RectF board, int n) {
        List<PointF> pts = new ArrayList<>();
        float cellW = board.width() / n;
        float cellH = board.height() / n;
        for (Cell cell : sol) {
            pts.add(new PointF(board.left + (cell.c + 0.5f) * cellW, board.top + (cell.r + 0.5f) * cellH));
        }
        return pts;
    }

    public static String solutionText(List<Cell> sol, int n) {
        List<Cell> s = new ArrayList<>(sol);
        s.sort((a, b) -> a.c != b.c ? a.c - b.c : a.r - b.r);
        StringBuilder sb = new StringBuilder();
        sb.append("点击坐标，左下角为 (1,1)：\n");
        for (Cell cell : s) {
            int x = cell.c + 1;
            int y = n - cell.r;
            sb.append("(").append(x).append(", ").append(y).append(")\n");
        }
        return sb.toString();
    }

    // RGB -> CIE Lab conversion, D65 reference white.
    private static double[] rgbToLab(int r, int g, int b) {
        double R = pivotRgb(r / 255.0);
        double G = pivotRgb(g / 255.0);
        double B = pivotRgb(b / 255.0);
        double X = R * 0.4124564 + G * 0.3575761 + B * 0.1804375;
        double Y = R * 0.2126729 + G * 0.7151522 + B * 0.0721750;
        double Z = R * 0.0193339 + G * 0.1191920 + B * 0.9503041;
        X /= 0.95047;
        Y /= 1.00000;
        Z /= 1.08883;
        double fx = pivotXyz(X), fy = pivotXyz(Y), fz = pivotXyz(Z);
        double L = 116 * fy - 16;
        double A = 500 * (fx - fy);
        double BB = 200 * (fy - fz);
        return new double[]{L, A, BB};
    }

    private static double pivotRgb(double v) {
        return v > 0.04045 ? Math.pow((v + 0.055) / 1.055, 2.4) : v / 12.92;
    }

    private static double pivotXyz(double v) {
        return v > 0.008856 ? Math.cbrt(v) : 7.787 * v + 16.0 / 116.0;
    }
}
