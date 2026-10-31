package dev.hjih.trapbot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Движок «Ловушки v3» (чистая Java, без WebView).
 *
 * Фаза 1 — честный лучший ход: итеративное углубление, Lazy SMP (все ядра, общая таблица), окна ожидания.
 * Фаза 2 — кандидаты: все ходы, не хуже лучшего более чем на допуск (оценки на ОДНОЙ глубине), параллельно.
 * Фаза 3 — ловушки: для каждого кандидата ищутся ВСЕ ответы соперника на нарастающей глубине;
 *          каждому ответу присваивается вероятность по модели «живого» игрока. Из этого получаем
 *          гарантию (минимум), ожидание и шанс ошибки соперника. Плюс тактика: вилки, связки, запертые фигуры.
 */
final class TrapEngine {
    static final class Result {
        String error, over, info = "";
        boolean none;
        int ff, fr, tf, tr, pr;
    }

    static final boolean DEBUG = System.getProperty("trap.debug") != null;
    private static final String INIT = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR";
    private static final int[] INIT_GRID = grid(INIT);

    private final int threads;
    private final TT tt = TT.alloc(TT.DEFAULT_BITS);
    private final Pos main = new Pos(tt);
    private final Pos[] workers;
    private final ExecutorService pool;
    private volatile Pos.Ctl cur;

    // партия
    private boolean hasPending;
    private int pendM, pendSp0;
    // адаптация
    private double skill = .5;
    private boolean adaptOn = true;
    private Trap lastTrap;
    private Set<Integer> ban;

    private static final class Trap {
        Set<Integer> good = new HashSet<>();
        double frac;
        int n;
    }

    TrapEngine(int threads) { this(threads, null); }

    /** init выполняется один раз в начале каждого рабочего потока пула (например, чтобы поднять приоритет). */
    TrapEngine(int threads, final Runnable init) {
        this.threads = Math.max(1, threads);
        workers = new Pos[this.threads];
        for (int i = 0; i < this.threads; i++) workers[i] = new Pos(tt);
        pool = this.threads > 1 ? Executors.newFixedThreadPool(this.threads - 1, new ThreadFactory() {
            private int k;
            @Override public Thread newThread(final Runnable r) {
                Thread t = new Thread(new Runnable() {
                    @Override public void run() {
                        if (init != null) { try { init.run(); } catch (Throwable ignored) { } }
                        r.run();
                    }
                }, "trap-worker-" + (k++));
                t.setDaemon(true);
                return t;
            }
        }) : null;
        main.setFen(INIT + " w KQkq - 0 1");
    }

    int threads() { return threads; }

    void cancel() { Pos.Ctl c = cur; if (c != null) c.stop = true; }

    void shutdown() { cancel(); if (pool != null) pool.shutdownNow(); }

    // ================= разбор позиции =================
    static int[] grid(String pl) {
        int[] g = new int[64];
        int r = 7, f = 0;
        for (int i = 0; i < pl.length(); i++) {
            char ch = pl.charAt(i);
            if (ch == '/') { r--; f = 0; }
            else if (ch >= '1' && ch <= '8') f += ch - '0';
            else {
                int k = "PNBRQKpnbrqk".indexOf(ch);
                if (k < 0 || r < 0 || f > 7) return null;
                g[r * 8 + f] = k < 6 ? k + 1 : k - 6 + 9;
                f++;
            }
        }
        return g;
    }

    private static int placeCast(int[] g) {
        int c = 0;
        if (g[4] == 6) { if (g[7] == 4) c |= 1; if (g[0] == 4) c |= 2; }
        if (g[60] == 14) { if (g[63] == 12) c |= 4; if (g[56] == 12) c |= 8; }
        return c;
    }

    private static int fenCast(String[] fp) {
        if (fp.length < 3) return 15;
        int c = 0;
        for (char ch : fp[2].toCharArray()) { int x = "KQkq".indexOf(ch); if (x >= 0) c |= 1 << x; }
        return c;
    }

    private static String castStr(int c) {
        String s = ((c & 1) != 0 ? "K" : "") + ((c & 2) != 0 ? "Q" : "") + ((c & 4) != 0 ? "k" : "") + ((c & 8) != 0 ? "q" : "");
        return s.isEmpty() ? "-" : s;
    }

    private void resync(String[] fp, int[] g, int prevCast) {
        boolean start = java.util.Arrays.equals(g, INIT_GRID);
        int c;
        if (start) c = 15;
        else { c = fenCast(fp) & placeCast(g); if (prevCast >= 0) c &= prevCast; }
        main.setFen(fp[0] + " " + (fp.length > 1 && fp[1].equals("b") ? "b" : "w") + " " + castStr(c) + " - 0 1");
    }

    private void feedback(int m) {
        Trap t = lastTrap;
        lastTrap = null;
        if (t == null || t.n < 4 || t.frac > .5) return;
        skill = .7 * skill + .3 * (t.good.contains(m) ? 1 : 0);
    }

    // ================= запрос =================
    /** banList — ходы {ff, fr, tf, tr}, которые нельзя играть (не получились на экране). */
    Result handle(String fen, int depth, long ms, int cun, boolean adapt, int[][] banList) {
        return handle(fen, depth, ms, cun, 0, adapt, banList);
    }

    /** cun — хитрость (ловушки, вилки, связки), agg — агрессия (атака, инициатива, игра на победу). Шкалы независимы. */
    synchronized Result handle(String fen, int depth, long ms, int cun, int agg, boolean adapt, int[][] banList) {
        Result res = new Result();
        cur = null;
        try {
            adaptOn = adapt;
            String[] fp = fen.trim().split(" ");
            int[] g = grid(fp[0]);
            if (g == null) { res.error = "не удалось разобрать позицию"; return res; }
            int stm = fp.length > 1 && fp[1].equals("b") ? 1 : 0, prevCast = -1;
            boolean cont = false;
            if (hasPending && main.sp == pendSp0 && main.sp < 1000) {
                prevCast = main.cast;
                if (main.sameGrid(g) && main.side == stm) cont = true; // наш ход не состоялся — позиция та же
                else {
                    main.make(pendM);
                    int found = -1;
                    for (int mv : main.legalMoves()) {
                        main.make(mv);
                        boolean ok = main.sameGrid(g) && main.side == stm;
                        main.unmake();
                        if (ok) { found = mv; break; }
                    }
                    if (found >= 0) { feedback(found); main.make(found); cont = true; }
                    else main.unmake();
                }
            }
            hasPending = false;
            if (!cont) resync(fp, g, prevCast);
            String bad = main.sane();
            if (!bad.isEmpty()) { res.error = "Позиция выглядит неверно: " + bad; return res; }
            String ov = main.over();
            if (!ov.isEmpty() && (ov.charAt(0) == 'М' || ov.charAt(0) == 'П' || ov.contains("материала"))) {
                res.none = true; res.over = ov; return res;
            }
            int sp0 = main.sp;
            ban = null;
            if (banList != null && banList.length > 0) {
                ban = new HashSet<>();
                for (int mv : main.legalMoves()) {
                    int mf = mv & 127, mt = (mv >> 7) & 127;
                    for (int[] x : banList)
                        if ((mf & 7) == x[0] && (mf >> 4) == x[1] && (mt & 7) == x[2] && (mt >> 4) == x[3]) ban.add(mv);
                }
            }
            tt.newGen();
            for (Pos w : workers) w.nodes = 0;
            main.nodes = 0;
            String[] info = new String[1];
            int m = think(Math.max(1, depth), Math.max(100, ms), cun, agg, info);
            if (m == 0) { res.none = true; res.over = "Нет ходов"; return res; }
            boolean legal = false;
            int[] lg = main.legalMoves();
            for (int x : lg) if (x == m) { legal = true; break; }
            if (!legal) {
                if (lg.length == 0) { res.none = true; res.over = "Нет ходов"; return res; }
                m = lg[0];
                info[0] = "Запасной ход (проверка легальности)";
            }
            if (main.sp != sp0) { hasPending = false; res.error = "внутреннее состояние движка сбилось"; return res; }
            hasPending = true; pendM = m; pendSp0 = sp0;
            int f = m & 127, t = (m >> 7) & 127;
            res.ff = f & 7; res.fr = f >> 4; res.tf = t & 7; res.tr = t >> 4; res.pr = (m >> 14) & 7;
            res.info = info[0];
        } catch (Throwable e) {
            hasPending = false;
            res.error = String.valueOf(e);
        } finally {
            cur = null;
        }
        return res;
    }

    // ================= параллельный раннер =================
    private interface Task { void run(Pos p, int i); }

    private void parallel(final int n, final Pos.Ctl ctl, final Task t) {
        if (n <= 0) return;
        final AtomicInteger next = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        int w = Math.min(threads, n);
        for (int k = 1; k < w; k++) {
            final Pos p = workers[k];
            p.copyFrom(main);
            p.ctl = ctl;
            fs.add(pool.submit(new Runnable() { @Override public void run() { work(p, next, n, t, ctl); } }));
        }
        Pos p0 = workers[0];
        p0.copyFrom(main);
        p0.ctl = ctl;
        work(p0, next, n, t, ctl);
        for (Future<?> f : fs) { try { f.get(); } catch (Exception ignored) { } }
    }

    private static void work(Pos p, AtomicInteger next, int n, Task t, Pos.Ctl ctl) {
        while (!ctl.stop) {
            int i = next.getAndIncrement();
            if (i >= n) break;
            int base = p.sp;
            try { t.run(p, i); }
            catch (Pos.Stop ignored) { }
            catch (RuntimeException e) { if (DEBUG) e.printStackTrace(); ctl.stop = true; }
            finally { while (p.sp > base) p.unmake(); }
        }
    }

    // ================= фаза 1: лучший ход (Lazy SMP) =================
    private void helper(final Pos h, int[] rl0, int D, int id, Pos.Ctl c) {
        int base = h.sp;
        try {
            int[] rl = rl0.clone();
            for (int d = 1 + (id & 1); d <= D && !c.stop; d++) {
                h.rootSearch(d, rl, -Pos.INF, Pos.INF);
                int b = h.rootBest;
                for (int i = 0; i < rl.length; i++) if (rl[i] == b) { System.arraycopy(rl, 0, rl, 1, i); rl[0] = b; break; }
            }
        } catch (Pos.Stop ignored) {
        } catch (RuntimeException e) {
            if (DEBUG) e.printStackTrace();
            c.stop = true;
        } finally {
            while (h.sp > base) h.unmake();
        }
    }

    private static String nodesStr(long n) {
        return n >= 1_000_000 ? String.format(java.util.Locale.US, "%.1fM", n / 1e6) : n >= 1000 ? (n / 1000) + "k" : String.valueOf(n);
    }

    private long allNodes() {
        long n = main.nodes;
        for (Pos w : workers) n += w.nodes;
        return n;
    }

    /** Возвращает ход (0 — нет ходов); текст пояснения кладёт в info[0]. */
    /** Проверка ловушки: наш ход, затем самый выгодный взятием ответ соперника, затем поиск до 8 полуходов всего.
     *  Бонус — насколько эта линия лучше для нас, чем оценка после хода без ответа. Ограничено по времени. */
    private double deepTrap(Pos p, int m, double base, long until) {
        if (System.nanoTime() > until) return 0;
        p.copyFrom(main);
        Pos.Ctl saved = p.ctl;
        p.make(m);
        int g = -1, gv = 0;
        for (int x : p.legalMoves()) {
            int t = (x >> 7) & 127;
            int v = p.b[t] != 0 ? Pos.VAL[p.b[t] & 7] : 0;
            if (v > gv) { gv = v; g = x; }
        }
        if (g < 0) { p.unmake(); return 0; }
        p.make(g);
        double bonus = 0;
        try {
            Pos.Ctl tc = new Pos.Ctl();
            tc.deadline = until;
            p.ctl = tc;
            int sc = p.search(6, -Pos.MATE, Pos.MATE, 0, true);
            bonus = sc - base;
        } catch (Pos.Stop e) {
            bonus = 0;
        } finally {
            p.ctl = saved;
            p.unmake();
            p.unmake();
        }
        return Math.max(0, Math.min(60, bonus));
    }

    private int think(int D, long tlMs, int cun, int agg, String[] info) {
        final long t0 = System.nanoTime(), tl = tlMs * 1_000_000L;
        final int sp0 = main.sp;
        int[] all = main.legalMoves();
        if (all.length == 0) return 0;
        int[] rl = all;
        if (ban != null) {
            int c = 0;
            int[] f = new int[all.length];
            for (int m : all) if (!ban.contains(m)) f[c++] = m;
            if (c > 0) rl = java.util.Arrays.copyOf(f, c);
        }
        final java.util.HashMap<Integer, String> nm = new java.util.HashMap<>();
        for (int m : rl) nm.put(m, main.name(m));
        if (rl.length == 1) { info[0] = "Единственный ход " + nm.get(rl[0]); return rl[0]; }
        lastTrap = null;
        if (main.hm == 0 && main.sp < 2) { java.util.Arrays.fill(main.K1, 0); java.util.Arrays.fill(main.K2, 0); }

        // ---------- фаза 1 ----------
        final Pos.Ctl c1 = new Pos.Ctl();
        cur = c1;
        main.ctl = c1;
        List<Future<?>> hf = new ArrayList<>();
        final int[] rlF = rl;
        final int DD = D;
        for (int k = 1; k < threads; k++) {
            final Pos h = workers[k];
            final int id = k;
            h.copyFrom(main);
            h.ctl = c1;
            hf.add(pool.submit(new Runnable() { @Override public void run() { helper(h, rlF, DD, id, c1); } }));
        }
        int best = rl[0], bs = 0, done = 0, dCur = 0;
        int[] order = rl.clone();
        // без хитрости и агрессии (чистый поиск) тратим почти всё время хода; с ловушками — 42%, остальное отдано фазам 2–3
        final boolean pure = cun <= 0 && agg <= 0;
        try {
            for (int d = 1; d <= D; d++) {
                dCur = d;
                c1.deadline = d == 1 ? Long.MAX_VALUE : t0 + tl * (pure ? 94 : 42) / 100;
                int A = -Pos.INF, B = Pos.INF, delta = 40;
                if (d >= 5) { A = bs - delta; B = bs + delta; }
                while (true) {
                    main.rootSearch(d, order, A, B);
                    int s = main.rootScore;
                    if (s <= A && A > -Pos.INF) { delta *= 3; A = delta > 600 ? -Pos.INF : A - delta; }
                    else if (s >= B && B < Pos.INF) { delta *= 3; B = delta > 600 ? Pos.INF : B + delta; }
                    else break;
                }
                best = main.rootBest; bs = main.rootScore; done = d;
                for (int i = 0; i < order.length; i++) if (order[i] == best) { System.arraycopy(order, 0, order, 1, i); order[0] = best; break; }
                if (Math.abs(bs) > Pos.MATE - 100 || System.nanoTime() - t0 > tl * (pure ? 62 : 50) / 100) break;
            }
        } catch (Pos.Stop ignored) {
            while (main.sp > sp0) main.unmake();
            // итерация прервана по времени: если уже нашёлся ход, точно лучший прежнего, берём его
            if (dCur > 1 && main.rootPartialOk) { best = main.rootPartialMove; bs = main.rootPartialScore; }
        }
        c1.stop = true;
        for (Future<?> f : hf) { try { f.get(); } catch (Exception ignored) { } }
        final String base = "глубина " + done + ", узлов " + nodesStr(allNodes()) + ", потоков " + threads + ", оценка " + (bs > 0 ? "+" : "") + bs;
        if (done < 3 || Math.abs(bs) > Pos.MATE - 100 || (cun <= 0 && agg <= 0)) {
            info[0] = "Лучший ход " + nm.get(best) + " (" + base + ")";
            return best;
        }

        // ---------- фаза 2: кандидаты на одной глубине ----------
        final double af = adaptOn ? .5 + (1 - skill) : 1;
        // Два независимых бюджета жертвы. Хитрость оправдывает жертву только проверенной ловушкой/тактикой,
        // агрессия — только реальным давлением на короля. Друг друга они не расширяют.
        final double sitC = bs < -150 ? 1.3 : bs > 300 ? .25 : 1;
        final double sitA = bs < -150 ? 1.25 : bs > 300 ? .5 : 1;
        final int cunB = cun > 0 ? (int) Math.round(45 * sitC * cun / 150.0 * af) : 0;
        final int aggB = agg > 0 ? (int) Math.round(35 * sitA * agg / 150.0) : 0;
        final int margin = cunB + aggB;
        final int cd = Math.max(2, done - 1);
        final int floor = bs - margin - 25;
        final Pos.Ctl c2 = new Pos.Ctl();
        cur = c2;
        c2.deadline = t0 + tl * 40 / 100;
        rl = order;
        final int n = rl.length;
        final int[] sc = new int[n];
        final boolean[] ok = new boolean[n];
        final int[] rlC = rl;
        // сначала лучший, чтобы его оценка была точной
        parallel(n, c2, new Task() {
            @Override public void run(Pos p, int i) {
                p.make(rlC[i]);
                int v = -p.search(cd, -Pos.INF, -floor, 1, true);
                p.unmake();
                v = Combo.boost(p, rlC[i], v);
                sc[i] = v; ok[i] = true;
            }
        });
        int best2 = -Pos.INF;
        for (int i = 0; i < n; i++) if (ok[i] && sc[i] > best2) best2 = sc[i];
        final int floor2 = best2 - margin - 1;
        List<int[]> cl = new ArrayList<>();
        for (int i = 0; i < n; i++) if (ok[i] && sc[i] > floor2 && sc[i] > floor) cl.add(new int[]{sc[i], rl[i]});
        java.util.Collections.sort(cl, new java.util.Comparator<int[]>() {
            @Override public int compare(int[] x, int[] y) { return y[0] - x[0]; }
        });
        boolean hasBest = false;
        for (int[] x : cl) if (x[1] == best) hasBest = true;
        if (cl.size() > 12) {
            // 6 лучших по оценке + до 6 «форсирующих» (шах, взятие) из остальных — жертвы не должны отсеиваться по оценке
            Pos fp = workers[0];
            fp.copyFrom(main);
            List<int[]> keep = new ArrayList<>(cl.subList(0, 6));
            for (int i = 6; i < cl.size() && keep.size() < 12; i++) {
                int mv = cl.get(i)[1], to = (mv >> 7) & 127;
                boolean forcing = main.b[to] != 0 || ((mv >> 17) & 1) != 0;
                if (!forcing) { fp.make(mv); forcing = fp.inCheck(); fp.unmake(); }
                if (forcing) keep.add(cl.get(i));
            }
            cl = keep;
        }
        hasBest = false;
        for (int[] x : cl) if (x[1] == best) hasBest = true;
        if (!hasBest) cl.add(new int[]{bs, best});
        if (cl.size() < 2) {
            info[0] = "Единственный разумный ход " + nm.get(best) + " (" + base + ")";
            return best;
        }

        // ---------- фаза 3: ловушки ----------
        final int nc = cl.size();
        final int[] cm = new int[nc];
        for (int i = 0; i < nc; i++) cm[i] = cl.get(i)[1];
        final Pos.Ctl c3 = new Pos.Ctl();
        cur = c3;
        c3.deadline = t0 + tl * 80 / 100;
        Pos sp = workers[0];
        sp.copyFrom(main);
        sp.ctl = c3;
        final int[][] R = new int[nc][];
        final double[][] W = new double[nc][];
        final double temp = 35 + 120 * (1 - skill);
        int total = 0;
        try {
            for (int i = 0; i < nc; i++) {
                sp.make(cm[i]);
                R[i] = sp.legalMoves();
                W[i] = R[i].length > 0 ? Tactics.replyWeights(sp, cm[i], R[i], temp) : new double[0];
                sp.unmake();
                total += R[i].length;
            }
        } catch (Pos.Stop e) {
            while (sp.sp > sp0) sp.unmake();
            info[0] = "Лучший ход " + nm.get(best) + " (" + base + ")";
            return best;
        }
        final int[] tc = new int[total], tr = new int[total];
        for (int i = 0, k = 0; i < nc; i++) for (int j = 0; j < R[i].length; j++, k++) { tc[k] = i; tr[k] = j; }
        int[][] V = null;
        int rdDone = 0;
        long lastRound = 0;
        final int rdMax = Math.min(30, Math.max(2, done - 1));
        for (int rd = 1; rd <= rdMax && total > 0; rd++) {
            long now = System.nanoTime();
            if (now >= c3.deadline) break;
            if (rd > 2 && lastRound > 0 && now + lastRound * 2 > c3.deadline) break;
            final int rdf = rd;
            final int[][] T = new int[nc][];
            for (int i = 0; i < nc; i++) T[i] = new int[R[i].length];
            final boolean[] dn = new boolean[total];
            parallel(total, c3, new Task() {
                @Override public void run(Pos p, int i) {
                    int ci = tc[i], ri = tr[i];
                    p.make(cm[ci]);
                    p.make(R[ci][ri]);
                    int v = p.search(rdf, -Pos.INF, Pos.INF, 2, true);
                    p.unmake();
                    p.unmake();
                    T[ci][ri] = v;
                    dn[i] = true;
                }
            });
            boolean all2 = true;
            for (boolean x : dn) if (!x) { all2 = false; break; }
            if (!all2) break;
            V = T;
            rdDone = rd;
            lastRound = System.nanoTime() - now;
        }
        c3.stop = true;
        if (V == null) {
            info[0] = "Лучший ход " + nm.get(best) + " (" + base + ")";
            return best;
        }

        // ---------- итоговый выбор ----------
        double[] mm = new double[nc], ev = new double[nc], perr = new double[nc];
        double mmMax = -1e18;
        for (int i = 0; i < nc; i++) {
            if (R[i].length == 0) {
                sp.make(cm[i]);
                boolean mate = sp.inCheck();
                sp.unmake();
                mm[i] = ev[i] = mate ? 2500 : 0;
                perr[i] = mate ? 1 : 0;
            } else {
                double lo = 1e18, e = 0;
                for (int j = 0; j < R[i].length; j++) {
                    double v = Math.max(-2500, Math.min(2500, V[i][j]));
                    if (v < lo) lo = v;
                    e += W[i][j] * v;
                }
                double pe = 0;
                for (int j = 0; j < R[i].length; j++)
                    if (Math.max(-2500, Math.min(2500, V[i][j])) >= lo + 150) pe += W[i][j];
                mm[i] = lo; ev[i] = e; perr[i] = pe;
            }
            Combo.lift(sp, cm, i, mm, ev);
            if (mm[i] > mmMax) mmMax = mm[i];
        }
        double kappa = cun > 0 ? Math.min(.85, cun / 190.0) * (adaptOn ? .6 + .8 * (1 - skill) : 1) : 0;
        kappa = Math.min(.9, kappa);
        double bscale = cun > 0 ? Math.min(1.6, cun / 150.0) : 0;
        double ascale = agg > 0 ? Math.min(1.6, agg / 120.0) : 0;
        final long trapUntil = t0 + tl * 95 / 100;
        int pi = -1;
        double bf = -1e18, sacLoss = 0;
        int[] atk = new int[nc];
        Tactics.Bonus[] tb = new Tactics.Bonus[nc];
        for (int i = 0; i < nc; i++) {
            double loss = mmMax - mm[i];
            if (loss > margin) continue;
            sp.copyFrom(main);
            tb[i] = cun > 0 ? Tactics.eval(sp, cm[i]) : new Tactics.Bonus();
            sp.copyFrom(main);
            atk[i] = agg > 0 ? Tactics.attack(sp, cm[i]) : 0;
            sp.copyFrom(main);
            boolean draw = Tactics.drawAfter(sp, cm[i]);
            double trapX = kappa * (ev[i] - mm[i]) + bscale * tb[i].total;
            if (loss <= 0 && cun > 0) trapX += deepTrap(sp, cm[i], mm[i], trapUntil);
            if (loss > 0) trapX = 0;                               // хитрость выбирает только среди ходов без потери по поиску
            trapX = Math.min(trapX, 60);
            double attX = ascale * atk[i];
            if (loss > 0) attX = 0;                                // атака только среди ходов без потери по поиску
            attX = Math.min(attX, 60);
            if (loss > 12) {
                // настоящая жертва: каждый источник покрывает потерю только в пределах своего бюджета и своего выигрыша
                double cj = Math.min(cunB, Math.max(0, trapX)), aj = Math.min(aggB, Math.max(0, attX));
                if (loss > cj + aj) continue;
            }
            if (draw && cm[i] != best && bs > -100) continue;      // не сводим выигранное/ровное к ничьей
            if (draw && agg > 0) attX -= 30 + agg / 2.0;           // агрессия не любит ничью
            double f = mm[i] + trapX + attX;
            if (f > bf) { bf = f; pi = i; sacLoss = loss; }
        }
        if (pi < 0) { info[0] = "Лучший ход " + nm.get(best) + " (" + base + ")"; return best; }
        int pm = cm[pi];
        // запоминаем форму ловушки для адаптации
        if (R[pi].length >= 4) {
            Trap tr2 = new Trap();
            tr2.n = R[pi].length;
            for (int j = 0; j < R[pi].length; j++) if (V[pi][j] <= mm[pi] + 60) tr2.good.add(R[pi][j]);
            tr2.frac = tr2.good.size() / (double) tr2.n;
            lastTrap = tr2;
        }
        String tail = " [" + base + ", анализ ловушек " + nc + " из " + cl.size() + ", ответы на глубину " + rdDone
                + (adaptOn ? ", точность игрока " + Math.round(skill * 100) + "%" : "") + "]";
        if (pm == best) {
            info[0] = "Лучший ход " + nm.get(best) + " совпал с ловушкой"
                    + (perr[pi] >= .1 ? ": шанс ошибки соперника ~" + Math.round(perr[pi] * 100) + "%" : "")
                    + (tb[pi].tags.isEmpty() ? "" : ", " + tb[pi].tags) + (atk[pi] > 0 ? ", атака +" + atk[pi] : "") + tail;
        } else {
            info[0] = "Ловушка: " + nm.get(pm) + " вместо " + nm.get(best) + ". Шанс ошибки соперника ~" + Math.round(perr[pi] * 100)
                    + "% (гарантия " + fmt(mm[pi]) + ", ожидание " + fmt(ev[pi]) + ")"
                    + (tb[pi].tags.isEmpty() ? "" : ", " + tb[pi].tags) + (atk[pi] > 0 ? ", атака +" + atk[pi] : "")
                    + (sacLoss > 12 ? ", жертва " + Math.round(sacLoss) : "") + " (бюджет: хитрость " + cunB + ", агрессия " + aggB + ")" + tail;
        }
        return pm;
    }

    private static String fmt(double v) { return (v >= 0 ? "+" : "") + Math.round(v); }
}
