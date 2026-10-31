package dev.hjih.trapbot;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Распознавание шахматной доски по скриншоту без заранее заготовленных картинок:
 * фигуры «запоминаются» с начальной позиции (кнопка «Запомнить»), после этого
 * каждая клетка сравнивается с запомненными силуэтами.
 */
final class Recognizer {
    static final int N = 28;          // сетка силуэта N x N на клетку
    static final int TH = 70;         // порог отличия пикселя от фона клетки
    static final float INSET = 0.08f; // отступ от краёв клетки
    static final String ORDER = "PNBRQK";
    static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR";

    static final class Frame {
        final int[] px; final int w, h, ox, oy, stride;
        Frame(int[] px, int w, int h) { this(px, w, h, 0, 0, w); }
        private Frame(int[] px, int w, int h, int ox, int oy, int stride) {
            this.px = px; this.w = w; this.h = h; this.ox = ox; this.oy = oy; this.stride = stride;
        }
        /** квадратный фрагмент без копирования пикселей */
        Frame view(int x, int y, int s) { return new Frame(px, s, s, ox + x, oy + y, stride); }
    }

    static final class Templates {
        @SuppressWarnings("unchecked")
        final ArrayList<boolean[]>[][] list = new ArrayList[2][6];
        float lum0, lum1, emptyThr;
        boolean valid;

        Templates() {
            for (int c = 0; c < 2; c++) for (int t = 0; t < 6; t++) list[c][t] = new ArrayList<>();
        }

        String ser() {
            StringBuilder sb = new StringBuilder();
            sb.append(lum0).append(';').append(lum1).append(';').append(emptyThr);
            for (int c = 0; c < 2; c++) for (int t = 0; t < 6; t++)
                for (boolean[] m : list[c][t]) {
                    sb.append(';').append(c).append(t).append(':');
                    for (boolean b : m) sb.append(b ? '1' : '0');
                }
            return sb.toString();
        }

        static Templates parse(String s) {
            if (s == null) return null;
            try {
                String[] p = s.split(";");
                Templates t = new Templates();
                t.lum0 = Float.parseFloat(p[0]);
                t.lum1 = Float.parseFloat(p[1]);
                t.emptyThr = Float.parseFloat(p[2]);
                for (int i = 3; i < p.length; i++) {
                    int c = p[i].charAt(0) - '0', ty = p[i].charAt(1) - '0';
                    String bits = p[i].substring(3);
                    boolean[] m = new boolean[N * N];
                    for (int j = 0; j < N * N; j++) m[j] = bits.charAt(j) == '1';
                    t.list[c][ty].add(m);
                }
                t.valid = true;
                return t;
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** занятость клеток в начальной позиции (0 — пусто, 1 — белая, 2 — чёрная), индекс rank*8+file */
    static final String START_OCC;
    static {
        char[] c = new char[64];
        for (int r = 0; r < 8; r++) for (int f = 0; f < 8; f++)
            c[r * 8 + f] = r < 2 ? '1' : r > 5 ? '2' : '0';
        START_OCC = new String(c);
    }

    static final class Res {
        final char[][] b = new char[8][8]; // b[rank][file], rank 0 = первая горизонталь белых
        /** только «пусто / белая / чёрная» — читается надёжнее, чем тип фигуры (король и ферзь похожи) */
        String occKey = "";
        boolean occOk;
        boolean ok;
        String msg = "";
        String key = "";
    }

    // ---------- низкий уровень ----------

    private static int px(Frame f, int x, int y) {
        x = Math.max(0, Math.min(f.w - 1, x));
        y = Math.max(0, Math.min(f.h - 1, y));
        return f.px[(y + f.oy) * f.stride + x + f.ox];
    }

    /** цвет фона клетки: медиана по четырём точкам у середин краёв */
    private static int[] bg(Frame f, float x0, float y0, float sw, float sh) {
        float[][] pts = {{.5f, .04f}, {.5f, .96f}, {.04f, .5f}, {.96f, .5f}};
        int[][] c = new int[3][4];
        for (int i = 0; i < 4; i++) {
            int cx = (int) (x0 + pts[i][0] * sw), cy = (int) (y0 + pts[i][1] * sh);
            int sr = 0, sg = 0, sb = 0, n = 0;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int p = px(f, cx + dx, cy + dy);
                sr += (p >> 16) & 255; sg += (p >> 8) & 255; sb += p & 255; n++;
            }
            c[0][i] = sr / n; c[1][i] = sg / n; c[2][i] = sb / n;
        }
        int[] o = new int[3];
        for (int k = 0; k < 3; k++) {
            int[] a = c[k].clone();
            Arrays.sort(a);
            o[k] = (a[1] + a[2]) / 2;
        }
        return o;
    }

    /** закрашивает «дырки» внутри контура: силуэт не зависит от того, совпадает ли заливка с фоном */
    private static boolean[] fill(boolean[] m) {
        boolean[] vis = new boolean[N * N];
        int[] q = new int[N * N];
        int qh = 0, qt = 0;
        for (int i = 0; i < N; i++) {
            int[] border = {i, (N - 1) * N + i, i * N, i * N + N - 1};
            for (int idx : border) if (!m[idx] && !vis[idx]) { vis[idx] = true; q[qt++] = idx; }
        }
        while (qh < qt) {
            int idx = q[qh++], x = idx % N, y = idx / N;
            if (x > 0 && !m[idx - 1] && !vis[idx - 1]) { vis[idx - 1] = true; q[qt++] = idx - 1; }
            if (x < N - 1 && !m[idx + 1] && !vis[idx + 1]) { vis[idx + 1] = true; q[qt++] = idx + 1; }
            if (y > 0 && !m[idx - N] && !vis[idx - N]) { vis[idx - N] = true; q[qt++] = idx - N; }
            if (y < N - 1 && !m[idx + N] && !vis[idx + N]) { vis[idx + N] = true; q[qt++] = idx + N; }
        }
        boolean[] r = new boolean[N * N];
        for (int i = 0; i < N * N; i++) r[i] = m[i] || !vis[i];
        return r;
    }

    /** силуэт клетки; st[0] = доля закрашенных ячеек, st[1] = средняя яркость внутри силуэта */
    static boolean[] sil(Frame f, int col, int row, float[] st) {
        float sw = f.w / 8f, sh = f.h / 8f, x0 = col * sw, y0 = row * sh;
        int[] bg = bg(f, x0, y0, sw, sh);
        float ix = sw * INSET, iy = sh * INSET, cw = (sw - 2 * ix) / N, ch = (sh - 2 * iy) / N;
        boolean[] m = new boolean[N * N];
        float[] ox = {0, -1 / 3f, 1 / 3f, -1 / 3f, 1 / 3f}, oy = {0, -1 / 3f, -1 / 3f, 1 / 3f, 1 / 3f};
        for (int j = 0; j < N; j++) for (int i = 0; i < N; i++) {
            float cx = x0 + ix + (i + .5f) * cw, cy = y0 + iy + (j + .5f) * ch;
            for (int s = 0; s < 5; s++) {
                int p = px(f, (int) (cx + ox[s] * cw), (int) (cy + oy[s] * ch));
                int d = Math.abs(((p >> 16) & 255) - bg[0]) + Math.abs(((p >> 8) & 255) - bg[1]) + Math.abs((p & 255) - bg[2]);
                if (d > TH) { m[j * N + i] = true; break; }
            }
        }
        boolean[] fm = fill(m);
        int cnt = 0;
        double lum = 0;
        for (int j = 0; j < N; j++) for (int i = 0; i < N; i++) if (fm[j * N + i]) {
            cnt++;
            int p = px(f, (int) (x0 + ix + (i + .5f) * cw), (int) (y0 + iy + (j + .5f) * ch));
            lum += 0.299 * ((p >> 16) & 255) + 0.587 * ((p >> 8) & 255) + 0.114 * (p & 255);
        }
        st[0] = cnt / (float) (N * N);
        st[1] = cnt > 0 ? (float) (lum / cnt) : 0;
        return fm;
    }

    static float iou(boolean[] a, boolean[] b) {
        int in = 0, un = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] && b[i]) in++;
            if (a[i] || b[i]) un++;
        }
        return un == 0 ? 0 : in / (float) un;
    }

    // ---------- обучение и распознавание ----------

    /** какая фигура должна стоять на экранной клетке (col,row) в начальной позиции */
    static char expected(int col, int row, boolean wb) {
        int file = wb ? col : 7 - col, rank = wb ? 7 - row : row;
        String back = "RNBQKBNR";
        if (rank == 0) return back.charAt(file);
        if (rank == 1) return 'P';
        if (rank == 6) return 'p';
        if (rank == 7) return Character.toLowerCase(back.charAt(file));
        return ' ';
    }

    private static float lum(int p) {
        return 0.299f * ((p >> 16) & 255) + 0.587f * ((p >> 8) & 255) + 0.114f * (p & 255);
    }

    /** сила края (смена яркости) в позиции i профиля p, ищем максимум в окрестности ±2 */
    private static float edge(float[] p, int i) {
        float best = 0;
        for (int d = -2; d <= 2; d++) {
            int c = i + d;
            if (c < 4 || c + 4 > p.length) continue;
            float l = 0, r = 0;
            for (int k = 1; k <= 4; k++) { l += p[c - k]; r += p[c + k - 1]; }
            best = Math.max(best, Math.abs(l - r) / 4f);
        }
        return best;
    }

    /**
     * Находит доску по сетке клеток (границы между светлыми и тёмными клетками в пустых рядах 3–6).
     * f — кадр с запасом вокруг рамки; (nx,ny,ns) — где пользователь приблизительно поставил рамку.
     * Возвращает {x, y, size} в координатах f либо null.
     */
    static int[] locate(Frame f, int nx, int ny, int ns, int pad) {
        float q0 = ns / 8f;
        // вертикальные границы: средняя яркость столбцов в полосе пустых рядов
        int ya = Math.max(0, Math.round(ny + 3.35f * q0)), yb = Math.min(f.h, Math.round(ny + 3.65f * q0)); // одна пустая горизонталь
        if (yb - ya < 8) return null;
        float[] col = new float[f.w];
        for (int x = 0; x < f.w; x++) {
            float s = 0;
            for (int y = ya; y < yb; y++) s += lum(px(f, x, y));
            col[x] = s / (yb - ya);
        }
        float bs = -1, bx = nx, bq = q0;
        for (float q = q0 - pad / 4f; q <= q0 + pad / 4f; q += 0.25f)
            for (int x0 = nx - pad; x0 <= nx + pad; x0++) {
                float sc = 0;
                for (int k = 1; k <= 7; k++) sc += edge(col, Math.round(x0 + k * q));
                if (sc > bs) { bs = sc; bx = x0; bq = q; }
            }
        // горизонтальные границы между пустыми рядами (k = 3,4,5), период уже известен
        int xa = Math.max(0, Math.round(bx + 3.35f * bq)), xb = Math.min(f.w, Math.round(bx + 3.65f * bq)); // одна вертикаль
        if (xb - xa < 8) return null;
        float[] row = new float[f.h];
        for (int y = 0; y < f.h; y++) {
            float s = 0;
            for (int x = xa; x < xb; x++) s += lum(px(f, x, y));
            row[y] = s / (xb - xa);
        }
        float bs2 = -1, by = ny;
        for (int y0 = ny - pad; y0 <= ny + pad; y0++) {
            float sc = 0;
            for (int k = 3; k <= 5; k++) sc += edge(row, Math.round(y0 + k * bq));
            if (sc > bs2) { bs2 = sc; by = y0; }
        }
        if (bs < 7 * 20 || bs2 < 3 * 20) return null; // сетки не видно (контраст клеток слишком мал)
        return new int[]{Math.round(bx), Math.round(by), Math.round(8 * bq)};
    }

    static String sqName(int col, int row, boolean wb) {
        int file = wb ? col : 7 - col, rank = wb ? 7 - row : row;
        return "" + (char) ('a' + file) + (rank + 1);
    }

    static Templates learn(Frame f, boolean wb, String[] err) {
        Templates t = new Templates();
        float[] st = new float[2];
        float[][] fill = new float[8][8];
        double[] ls = new double[2];
        int[] ln = new int[2];
        float emptyMax = 0, pieceMin = 1, pieceMax = 0;
        ArrayList<Float> pf = new ArrayList<>();
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
            char ex = expected(col, row, wb);
            boolean[] m = sil(f, col, row, st);
            fill[row][col] = st[0];
            if (ex == ' ') {
                emptyMax = Math.max(emptyMax, st[0]);
            } else {
                pieceMin = Math.min(pieceMin, st[0]);
                pieceMax = Math.max(pieceMax, st[0]);
                pf.add(st[0]);
                int c = Character.isUpperCase(ex) ? 0 : 1;
                int ty = ORDER.indexOf(Character.toUpperCase(ex));
                t.list[c][ty].add(m);
                ls[c] += st[1];
                ln[c]++;
            }
        }
        String nums = String.format(java.util.Locale.US, " (пустые до %.2f, фигуры от %.2f)", emptyMax, pieceMin);
        if (pieceMax < 0.08f) {
            err[0] = "Кадр пустой или однотонный: бот не видит доску. Проверьте, что рамка стоит на доске и что трансляция — «весь экран».";
            return null;
        }
        java.util.Collections.sort(pf);
        float med = pf.get(pf.size() / 2);
        // клетки, где должна быть фигура, а их нет (рамка сбита / не начальная позиция)
        StringBuilder miss = new StringBuilder(), junk = new StringBuilder();
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
            char ex = expected(col, row, wb);
            if (ex != ' ' && fill[row][col] < 0.45f * med) miss.append(miss.length() > 0 ? ", " : "").append(sqName(col, row, wb));
            if (ex == ' ' && fill[row][col] >= 0.8f * pieceMin) junk.append(junk.length() > 0 ? ", " : "").append(sqName(col, row, wb));
        }
        if (junk.length() > 0) {
            err[0] = "Должны быть пустыми, но там что-то есть: " + junk + ". Уберите с доски кнопки/подсказки/рамку и проверьте, что рамка точно по доске." + nums;
            return null;
        }
        if (miss.length() > 0) {
            err[0] = "Не вижу фигуры на: " + miss + ". Проверьте рамку и начальную позицию." + nums;
            return null;
        }
        t.lum0 = (float) (ls[0] / ln[0]);
        t.lum1 = (float) (ls[1] / ln[1]);
        if (Math.abs(t.lum0 - t.lum1) < 25) {
            err[0] = "Белые и чёрные фигуры не отличаются по яркости. Проверьте, что выбрано верное «белыми/чёрными снизу».";
            return null;
        }
        t.emptyThr = Math.max(0.04f, Math.min(0.25f, emptyMax + 0.6f * (pieceMin - emptyMax)));
        t.valid = true;
        return t;
    }

    static Res recognize(Frame f, Templates t, boolean wb) {
        Res r = new Res();
        float[] st = new float[2];
        int unk = 0, wk = 0, bk = 0;
        boolean badPawn = false;
        float[][][] ts = new float[8][8][6]; // лучшее совпадение с каждым типом фигуры
        char[] occ = new char[64];
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
            boolean[] m = sil(f, col, row, st);
            char ch;
            int orow = wb ? 7 - row : row, ocol = wb ? col : 7 - col;
            if (st[0] < t.emptyThr) {
                ch = ' ';
                occ[orow * 8 + ocol] = '0';
            } else {
                int c = Math.abs(st[1] - t.lum0) <= Math.abs(st[1] - t.lum1) ? 0 : 1;
                occ[orow * 8 + ocol] = c == 0 ? '1' : '2';
                int bt = -1;
                float bs = 0;
                int rk0 = wb ? 7 - row : row, fl0 = wb ? col : 7 - col;
                for (int ty = 0; ty < 6; ty++) for (boolean[] tm : t.list[c][ty]) {
                    float s = iou(m, tm);
                    if (s > ts[rk0][fl0][ty]) ts[rk0][fl0][ty] = s;
                    if (s > bs) { bs = s; bt = ty; }
                }
                if (bt < 0 || bs < 0.40f) { ch = '?'; unk++; }
                else { ch = ORDER.charAt(bt); if (c == 1) ch = Character.toLowerCase(ch); }
            }
            int file = wb ? col : 7 - col, rank = wb ? 7 - row : row;
            r.b[rank][file] = ch;
        }
        // Король ровно один у каждой стороны: силуэты короля и ферзя похожи, поэтому среди клеток,
        // распознанных как K/Q, королём делаем ту, что больше похожа на короля, остальные — ферзи.
        for (int c = 0; c < 2; c++) {
            char K = c == 0 ? 'K' : 'k', Q = c == 0 ? 'Q' : 'q';
            int br = -1, bf = -1;
            float bd = -9;
            for (int rk = 0; rk < 8; rk++) for (int fl = 0; fl < 8; fl++) {
                if (r.b[rk][fl] != K && r.b[rk][fl] != Q) continue;
                float d = ts[rk][fl][5] - ts[rk][fl][4];
                if (d > bd) { bd = d; br = rk; bf = fl; }
            }
            if (br < 0) continue;
            for (int rk = 0; rk < 8; rk++) for (int fl = 0; fl < 8; fl++)
                if (r.b[rk][fl] == K || r.b[rk][fl] == Q) r.b[rk][fl] = (rk == br && fl == bf) ? K : Q;
        }
        for (int rank = 0; rank < 8; rank++) for (int file = 0; file < 8; file++) {
            char ch = r.b[rank][file];
            if (ch == 'K') wk++;
            if (ch == 'k') bk++;
            if ((ch == 'P' || ch == 'p') && (rank == 0 || rank == 7)) badPawn = true;
        }
        r.occKey = new String(occ);
        r.occOk = true;
        r.key = place(r.b);
        r.ok = unk == 0 && wk == 1 && bk == 1 && !badPawn;
        if (!r.ok) r.msg = unk > 0 ? "Не распознано клеток: " + unk : "Позиция выглядит неверно (короли/пешки)";
        return r;
    }

    // ---------- вспомогательное ----------

    static String place(char[][] b) {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int e = 0;
            for (int file = 0; file < 8; file++) {
                char c = b[rank][file];
                if (c == ' ') e++;
                else { if (e > 0) { sb.append(e); e = 0; } sb.append(c); }
            }
            if (e > 0) sb.append(e);
            if (rank > 0) sb.append('/');
        }
        return sb.toString();
    }

    /** какие права на рокировку вообще возможны при такой расстановке */
    static int possibleRights(char[][] b) {
        int r = 0;
        if (b[0][4] == 'K') { if (b[0][7] == 'R') r |= 1; if (b[0][0] == 'R') r |= 2; }
        if (b[7][4] == 'k') { if (b[7][7] == 'r') r |= 4; if (b[7][0] == 'r') r |= 8; }
        return r;
    }

    static String castle(int rights) {
        String s = "";
        if ((rights & 1) != 0) s += "K";
        if ((rights & 2) != 0) s += "Q";
        if ((rights & 4) != 0) s += "k";
        if ((rights & 8) != 0) s += "q";
        return s.isEmpty() ? "-" : s;
    }

    /** позиция после хода (с рокировкой, взятием на проходе и превращением) */
    static char[][] apply(char[][] b, int ff, int fr, int tf, int tr, int promo) {
        char[][] n = new char[8][8];
        for (int i = 0; i < 8; i++) n[i] = b[i].clone();
        char p = n[fr][ff];
        if ((p == 'P' || p == 'p') && ff != tf && n[tr][tf] == ' ') n[fr][tf] = ' ';
        if ((p == 'K' || p == 'k') && Math.abs(tf - ff) == 2) {
            if (tf > ff) { n[tr][5] = n[tr][7]; n[tr][7] = ' '; }
            else { n[tr][3] = n[tr][0]; n[tr][0] = ' '; }
        }
        n[tr][tf] = p;
        n[fr][ff] = ' ';
        if (promo > 0) {
            char pc = "..NBRQ".charAt(promo);
            n[tr][tf] = Character.isUpperCase(p) ? pc : Character.toLowerCase(pc);
        }
        return n;
    }
}
