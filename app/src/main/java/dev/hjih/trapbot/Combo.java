package dev.hjih.trapbot;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Форсированный поиск: атакующий ходит только «острыми» ходами (шахи, взятия,
 * вилки-угрозы), защитник перебирает ВСЕ ответы. Значит результат — честная
 * нижняя оценка: если линия найдена, соперник не может от неё уйти.
 * Глубина идёт много полуходов вперёд, потому что ветвление у атакующего узкое.
 */
final class Combo {
    private static final int INFV = 1000000000;
    private static final int[] DEPTHS = {2, 4, 6, 8, 10, 12};
    private static final int NODE_LIMIT = 24000;   // узлов на одну итерацию
    private static final int THREAT_PLY = 6;       // тихие вилки только в первых ходах
    private static final int VERIFY_MIN = 25;      // меньше этого прироста — не считаем

    private static final ConcurrentHashMap<Long, Integer> CACHE = new ConcurrentHashMap<>();

    private static final class Abort extends RuntimeException {
        Abort() {
            super(null, null, false, false);
        }
    }

    private static final Abort ABORT = new Abort();

    private final Pos p;
    private final int us;
    private int cnt;
    private int limit;

    private Combo(Pos p) {
        this.p = p;
        this.us = p.side;
    }

    // ------------------------------------------------------------------ API

    /** Берёт значение v из обычного поиска и поднимает его, если найдена форсированная линия. */
    static int boost(Pos p, int m, int v) {
        try {
            if (v > 90000 || v < -90000) {
                return v;
            }
            int pv = prove(p, m, 0);
            return pv > v + VERIFY_MIN ? pv : v;
        } catch (Throwable t) {
            return v;
        }
    }

    /** Поднимает «гарантию» кандидата mm[i], если форсированная линия доказана. */
    static void lift(Pos p, int[] cm, int i, double[] mm, double[] ev) {
        try {
            if (mm[i] > 2000 || mm[i] < -2000) {
                return;
            }
            int m = cm[i];
            int pv = prove(p, m, 60);
            if (pv > mm[i] + VERIFY_MIN) {
                double x = Math.min(2500, pv);
                mm[i] = x;
                if (ev[i] < x) {
                    ev[i] = x;
                }
            }
        } catch (Throwable t) {
            // не мешаем движку
        }
    }

    /**
     * Доказанная оценка хода m для стороны, которая ходит (в сантипешках), либо
     * Integer.MIN_VALUE если ход не «острый» / поиск не успел.
     * extraMs — сколько мс можно потратить, если результата нет в кэше (0 — только кэш/общий дедлайн).
     */
    static int prove(Pos p, int m, int extraMs) {
        long key = p.hash * 1000003L + m;
        Integer c = CACHE.get(key);
        if (c != null) {
            return c;
        }
        Pos.Ctl saved = p.ctl;
        boolean own = false;
        if (extraMs > 0) {
            Pos.Ctl nc = new Pos.Ctl();
            nc.deadline = System.nanoTime() + extraMs * 1000000L;
            p.ctl = nc;
            own = true;
        } else if (saved.stop) {
            return Integer.MIN_VALUE;
        }
        int base = p.sp;
        int res = Integer.MIN_VALUE;
        try {
            Combo s = new Combo(p);
            if (!s.eligible(m)) {
                res = Integer.MIN_VALUE + 1; // «не острый»: запомним, чтобы не пересчитывать
            } else {
                p.make(m);
                for (int d : DEPTHS) {
                    s.cnt = 0;
                    s.limit = NODE_LIMIT;
                    try {
                        res = s.node(d, -INFV, INFV, 1);
                    } catch (Abort a) {
                        break;
                    }
                    if (res > 90000 || res < -90000) {
                        break;
                    }
                }
            }
        } catch (Pos.Stop st) {
            res = Integer.MIN_VALUE;
        } finally {
            while (p.sp > base) {
                p.unmake();
            }
            if (own) {
                p.ctl = saved;
            }
        }
        if (res != Integer.MIN_VALUE) {
            if (CACHE.size() > 4096) {
                CACHE.clear();
            }
            CACHE.put(key, res);
        }
        return res == Integer.MIN_VALUE + 1 ? Integer.MIN_VALUE : res;
    }

    // --------------------------------------------------------------- search

    private int leaf() {
        int q = p.qs(-INFV, INFV, 60, 0);
        return p.side == us ? q : -q;
    }

    private int node(int depth, int alpha, int beta, int ply) {
        if (++cnt > limit) {
            throw ABORT;
        }
        if ((cnt & 63) == 0) {
            p.chk();
        }
        if (p.side != us) {
            return defender(depth, alpha, beta, ply);
        }
        return attacker(depth, alpha, beta, ply);
    }

    private int defender(int depth, int alpha, int beta, int ply) {
        int[] L = p.legalMoves();
        if (L.length == 0) {
            return p.inCheck() ? Pos.MATE - ply : 0;
        }
        if (depth <= 0) {
            return leaf();
        }
        int n = L.length;
        int[] sc = new int[n];
        for (int i = 0; i < n; i++) {
            int mv = L[i];
            int t = (mv >> 7) & 127;
            int v = p.b[t] & 7;
            sc[i] = v != 0 ? 10000 + v * 100 - (p.b[mv & 127] & 7) : (((mv >> 14) & 7) != 0 ? 9000 : 0);
        }
        sortDesc(L, sc, n);
        int best = INFV;
        for (int i = 0; i < n; i++) {
            p.make(L[i]);
            int v;
            try {
                v = node(depth - 1, alpha, beta, ply + 1);
            } finally {
                p.unmake();
            }
            if (v < best) {
                best = v;
            }
            if (best < beta) {
                beta = best;
            }
            if (best <= alpha) {
                break;
            }
        }
        return best;
    }

    private int attacker(int depth, int alpha, int beta, int ply) {
        boolean inChk = p.inCheck();
        int[] L = p.legalMoves();
        if (L.length == 0) {
            return inChk ? -(Pos.MATE - ply) : 0;
        }
        int best = -INFV;
        if (!inChk) {
            best = leaf();
            if (best >= beta) {
                return best;
            }
            if (best > alpha) {
                alpha = best;
            }
        }
        if (depth <= 0) {
            return inChk ? leaf() : best;
        }
        int n = L.length;
        int[] cand = new int[n];
        int[] sc = new int[n];
        int k = 0;
        boolean threats = ply <= THREAT_PLY && !inChk;
        int before = threats ? threatCount(us) : 0;
        int pins0 = threats ? p.pinsOf(us == 0) : 0;
        for (int i = 0; i < n; i++) {
            int mv = L[i];
            int t = (mv >> 7) & 127;
            int victim = p.b[t] & 7;
            boolean cap = victim != 0 || ((mv >> 17) & 1) != 0;
            boolean promo = ((mv >> 14) & 7) != 0;
            int mt = p.b[mv & 127] & 7;
            int s;
            if (inChk) {
                s = cap ? 8000 + victim * 10 - mt : 100;
            } else {
                p.make(mv);
                boolean chk = p.inCheck();
                int after = (!cap && !promo && !chk && threats) ? threatCount(us) : 0;
                boolean safe = !cap && !promo && !chk && threats && ((after > before && after >= 2) || pinUp(pins0) || netTight()) && safeLanding(t, mt);
                p.unmake();
                if (cap || promo) {
                    s = 10000 + victim * 100 - mt + (promo ? 800 : 0) + (chk ? 300 : 0);
                } else if (chk) {
                    s = 5000;
                } else if (safe) {
                    s = 3000 + after;
                } else {
                    continue;
                }
            }
            cand[k] = mv;
            sc[k] = s;
            k++;
        }
        sortDesc(cand, sc, k);
        for (int i = 0; i < k; i++) {
            p.make(cand[i]);
            int v;
            try {
                v = node(depth - 1, alpha, beta, ply + 1);
            } finally {
                p.unmake();
            }
            if (v > best) {
                best = v;
            }
            if (best > alpha) {
                alpha = best;
            }
            if (alpha >= beta) {
                break;
            }
        }
        return best;
    }

    // ------------------------------------------------------------- helpers

    /** Может ли ход быть «острым» на корне (взятие, шах, вилка). */
    private boolean eligible(int m) {
        int t = (m >> 7) & 127;
        if (p.b[t] != 0 || ((m >> 17) & 1) != 0 || ((m >> 14) & 7) != 0) {
            return true;
        }
        int mt = p.b[m & 127] & 7;
        int before = threatCount(us);
        int pins0 = p.pinsOf(us == 0);
        p.make(m);
        try {
            if (p.inCheck()) {
                return true;
            }
            int after = threatCount(us);
            return ((after > before && after >= 2) || pinUp(pins0) || netTight()) && safeLanding(t, mt);
        } finally {
            p.unmake();
        }
    }

    /** Сколько фигур соперника (N,B,R,Q) под ударом «выгодно для нас» (висят или бьются меньшей). */
    private int threatCount(int by) {
        int them = by ^ 1;
        int n = 0;
        for (int q = 0; q < 128; q++) {
            if ((q & 136) != 0) {
                q += 7;
                continue;
            }
            int c = p.b[q];
            if (c == 0 || (c >> 3) != them) {
                continue;
            }
            int pt = c & 7;
            if (pt < 2 || pt > 5) {
                continue;
            }
            int a = p.att(q, by, true);
            if (a >= INFV) {
                continue;
            }
            boolean defended = p.att(q, them, false) < INFV;
            if (!defended || Pos.VAL[pt] > a) {
                n++;
            }
        }
        return n;
    }

    /** Новая связка после хода атакующего. */
    private boolean pinUp(int pins0) {
        return p.pinsOf(us == 0) > pins0;
    }

    /** Сеть вокруг короля затянулась: у короля ≤2 ходов, а на зону давят ≥2 наших фигуры. */
    private boolean netTight() {
        int k = p.ksq[us ^ 1];
        int zone = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int df = -1; df <= 1; df++) {
                int q = k + dr * 16 + df;
                if ((q & 136) == 0 && p.att(q, us, true) < INFV) {
                    zone++;
                }
            }
        }
        if (zone < 3) {
            return false;
        }
        return p.legalMoves().length <= 3;
    }

    /** После хода атакующего: не отдаётся ли ходившая фигура даром (ходит соперник). */
    private boolean safeLanding(int to, int mt) {
        int them = p.side;
        int a = p.att(to, them, true);
        if (a >= INFV) {
            return true;
        }
        int v = Pos.VAL[mt];
        if (a < v) {
            return false;
        }
        return a > v || p.att(to, us, false) < INFV;
    }

    private static void sortDesc(int[] mv, int[] sc, int n) {
        for (int i = 1; i < n; i++) {
            int m = mv[i];
            int s = sc[i];
            int j = i - 1;
            while (j >= 0 && sc[j] < s) {
                mv[j + 1] = mv[j];
                sc[j + 1] = sc[j];
                j--;
            }
            mv[j + 1] = m;
            sc[j + 1] = s;
        }
    }
}
