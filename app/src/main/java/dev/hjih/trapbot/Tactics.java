package dev.hjih.trapbot;

/**
 * Тактические признаки для выбора ловушек. Все функции вызываются в корне (для 5–15 кандидатов),
 * поэтому могут позволить себе make/unmake и небольшие аллокации.
 */
final class Tactics {
    private Tactics() { }

    static final class Bonus {
        int total, risk;
        String tags = "";
    }

    /** Цели стороны us: фигуры соперника под боем, которые она выиграла бы (с учётом защиты). Возвращает число целей. */
    static int collect(Pos p, int us, int[] gains, int[] sqs) {
        int n = 0, them = us ^ 1;
        for (int q = 0; q < 128 && n < gains.length; q++) {
            if ((q & 0x88) != 0) { q += 7; continue; }
            int c = p.b[q];
            if (c == 0 || (c >> 3) != them) continue;
            int pt = c & 7;
            if (pt == 6) continue;
            int a = p.att(q, us, true);
            if (a >= Pos.NA) continue;
            int g = p.att(q, them, false) >= Pos.NA ? Pos.VAL[pt] : Pos.VAL[pt] - a;
            if (g <= 0) continue;
            gains[n] = g;
            sqs[n] = q;
            n++;
        }
        return n;
    }

    /**
     * Вилка: после хода m новая фигура/вскрытая атака создаёт ≥2 угрозы (или шах + угроза),
     * а сама ходящая фигура не может быть выгодно взята. Оценка ≈ то, что соперник потеряет
     * (он спасёт самую дорогую цель).
     */
    static int fork(Pos p, int m) {
        if ((p.b[m & 127] & 7) == 6) return 0;
        int us = p.side;
        int[] g0 = new int[16], s0 = new int[16];
        int n0 = collect(p, us, g0, s0);
        p.make(m);
        try {
            int them = p.side, to = (m >> 7) & 127, pc = p.b[to], pv = Pos.VAL[pc & 7];
            int ba = p.att(to, them, true);
            if (ba < Pos.NA && (ba < pv || p.att(to, us, false) >= Pos.NA)) return 0; // вилочная фигура висит
            int[] g = new int[16], s = new int[16];
            int n = collect(p, us, g, s);
            boolean chk = p.isAtt(p.ksq[them], us);
            int nNew = 0;
            for (int i = 0; i < n; i++) {
                boolean old = false;
                for (int j = 0; j < n0; j++) if (s0[j] == s[i]) { old = true; break; }
                if (!old) nNew++;
            }
            if (nNew == 0) return 0;
            int g1 = 0, g2 = 0, g3 = 0;
            for (int i = 0; i < n; i++) {
                int x = g[i];
                if (x > g1) { g3 = g2; g2 = g1; g1 = x; }
                else if (x > g2) { g3 = g2; g2 = x; }
                else if (x > g3) g3 = x;
            }
            if (chk) return Math.min(120, (int) (g1 * 0.65) + 15);
            if (n >= 2) return Math.min(120, (int) (g2 * 0.55 + g3 * 0.15) + 12);
            return 6;
        } finally {
            p.unmake();
        }
    }

    /** Ход m запирает фигуру соперника: она под боем, безопасных полей нет и снять атаку взятием нельзя. */
    static int trapped(Pos p, int m) {
        p.make(m);
        int res = 0;
        try {
            int us = p.side ^ 1, th = p.side;
            int[] rl = p.legalMoves();
            for (int q = 0; q < 128; q++) {
                if ((q & 0x88) != 0) { q += 7; continue; }
                int c = p.b[q];
                if (c == 0 || (c >> 3) != th) continue;
                int pt = c & 7;
                if (pt < 2 || pt > 5) continue;
                int aa = p.att(q, us, true);
                if (aa >= Pos.NA) continue;
                if (!(aa < Pos.VAL[pt] || p.att(q, th, false) >= Pos.NA)) continue;
                boolean safe = false;
                for (int r : rl) {
                    if ((r & 127) != q) continue;
                    p.make(r);
                    int t = (r >> 7) & 127;
                    boolean ok = p.att(t, us, true) >= Pos.VAL[pt] && !(p.isAtt(t, us) && p.att(t, th, false) >= Pos.NA);
                    p.unmake();
                    if (ok) { safe = true; break; }
                }
                if (safe) continue;
                boolean canTake = false;
                for (int r : rl) {
                    int t = (r >> 7) & 127;
                    if (t == q) continue;
                    if (p.b[t] != 0 && (r & 127) != q) {
                        p.make(r);
                        boolean still = p.att(q, us, true) < Pos.NA;
                        p.unmake();
                        if (!still) { canTake = true; break; }
                    }
                }
                if (!canTake) res = Math.max(res, Math.min(60, Pos.VAL[pt] / 7));
            }
        } finally {
            p.unmake();
        }
        return res;
    }

    /** Угроза вилки: если соперник «пропустит» ход, у нас есть безопасная вилка — тихий ход, который трудно заметить. */
    static int forkThreat(Pos p, int m) {
        p.make(m);
        int best = 0;
        try {
            if (p.inCheck()) return 0;
            if (p.isAtt(p.ksq[p.side ^ 1], p.side)) return 0;
            p.makeNull();
            try {
                int[] ml = p.legalMoves();
                for (int x : ml) {
                    int f = fork(p, x);
                    if (f > best) best = f;
                }
            } finally {
                p.unmake();
            }
        } finally {
            p.unmake();
        }
        return Math.min(45, best / 2);
    }

    /** Связки/сквозные удары: [выигрыш, риск]. */
    static int[] pins(Pos p, int m) {
        if ((p.b[m & 127] & 7) == 6 && ((m >> 17) & 2) == 0) return new int[]{0, 0};
        boolean w = p.side == 0;
        int ps0 = p.pinsOf(w), po0 = p.pinsOf(!w);
        p.make(m);
        try {
            int ps1 = p.pinsOf(w), po1 = p.pinsOf(!w);
            int risk = 0;
            for (int r : p.legalMoves()) {
                int fb = fork(p, r);
                if (fb > risk) risk = fb;
            }
            int gain = Math.max(0, Math.min(70, ps1 - ps0));
            int exp = Math.max(0, Math.min(70, po1 - po0));
            return new int[]{gain, (int) Math.round(.8 * risk) + exp};
        } finally {
            p.unmake();
        }
    }

    static Bonus eval(Pos p, int m) {
        Bonus r = new Bonus();
        int fb = fork(p, m), tb = trapped(p, m);
        int[] pn = pins(p, m);
        int ft = fb == 0 ? forkThreat(p, m) : 0;
        int total = Math.min(170, fb + tb + pn[0] + ft);
        r.risk = pn[1];
        r.total = total - pn[1];
        StringBuilder sb = new StringBuilder();
        if (fb > 0) sb.append("вилка +").append(fb).append(' ');
        if (ft > 0) sb.append("угроза вилки +").append(ft).append(' ');
        if (tb > 0) sb.append("запертая фигура +").append(tb).append(' ');
        if (pn[0] > 0) sb.append("связка +").append(pn[0]).append(' ');
        if (pn[1] > 0) sb.append("риск −").append(pn[1]).append(' ');
        r.tags = sb.toString().trim();
        return r;
    }

    // ================= агрессия =================
    /** Давление на зону короля соперника: сколько полей вокруг него бьёт сторона us (+ шах). */
    private static int pressure(Pos p, int us) {
        int k = p.ksq[us ^ 1], pr = 0, back = us == 0 ? -16 : 16;
        for (int dr = -1; dr <= 1; dr++)
            for (int df = -1; df <= 1; df++) {
                int q = k + dr * 16 + df;
                if ((q & 0x88) != 0) continue;
                int a = p.att(q, us, true);
                if (a < Pos.NA) pr += a <= Pos.VAL[3] ? 9 : 6;
            }
        for (int df = -1; df <= 1; df++) {
            int q = k + 2 * back + df;
            if ((q & 0x88) == 0 && p.isAtt(q, us)) pr += 3;
        }
        if (p.isAtt(k, us)) pr += 25;
        // сколько наших фигур (N,B,R,Q) давит на зону короля — коллективная атака важнее одиночной
        int heavy = 0, flee = 0;
        for (int q = 0; q < 128; q++) {
            if ((q & 0x88) != 0) { q += 7; continue; }
            int c = p.b[q];
            if (c == 0 || (c >> 3) != us) continue;
            int pt = c & 7;
            if (pt < 2 || pt > 5) continue;
            int dr = Math.abs((q >> 4) - (k >> 4)), df = Math.abs((q & 7) - (k & 7));
            boolean near = Math.max(dr, df) <= 3;
            boolean line = pt >= 3 && (pt != 3 || false) && (df == 0 || dr == 0 || df == dr);
            if (near || line) heavy++;
        }
        if (heavy >= 3) pr += 8 * (heavy - 2);
        // клетки, куда король может уйти: чем меньше, тем ближе мат
        for (int dr = -1; dr <= 1; dr++)
            for (int df = -1; df <= 1; df++) {
                if (dr == 0 && df == 0) continue;
                int q = k + dr * 16 + df;
                if ((q & 0x88) != 0) continue;
                int c = p.b[q];
                if ((c != 0 && (c >> 3) != us) || p.isAtt(q, us)) continue;
                flee++;
            }
        if (flee <= 1) pr += 12; else if (flee == 2) pr += 5;
        return pr;
    }

    /**
     * Атакующая ценность хода m (p — позиция до хода, ходит «мы»): рост давления на короля, шахи,
     * продвижение вперёд; штраф за пассивные отходы, когда фигуре ничего не грозит.
     * Не материальная оценка — материал по-прежнему контролируется минимаксом в корне.
     */
    static int attack(Pos p, int m) {
        int us = p.side, f = m & 127, t = (m >> 7) & 127, pt = p.b[f] & 7;
        boolean cap = p.b[t] != 0 || ((m >> 17) & 1) != 0, castle = ((m >> 17) & 2) != 0;
        boolean fromHit = p.isAtt(f, us ^ 1);
        int adv = us == 0 ? (t >> 4) - (f >> 4) : (f >> 4) - (t >> 4);
        int ekf = p.ksq[us ^ 1] & 7;
        int before = pressure(p, us), s;
        p.make(m);
        try {
            s = pressure(p, us) - before;
            if (pt != 6 && !fromHit && adv > 0) s += 3 * adv;
            if (pt == 1 && adv > 0 && Math.abs((t & 7) - ekf) <= 2) s += 5;      // пешечный штурм у короля
            if (adv < 0 && !fromHit && !cap) s += 6 * adv;                        // пассивный отход
            if (pt == 6 && !castle) s -= 8;
            if (castle) s += 4;
            if (cap) s += 3;
            if (p.inCheck() && p.att(t, us ^ 1, true) >= Pos.VAL[pt]) s += 10; // безопасный шах
        } finally {
            p.unmake();
        }
        return Math.max(-60, Math.min(160, s));
    }

    /** Ход m сразу ведёт к ничьей (пат, повтор, 50 ходов, нехватка материала). */
    static boolean drawAfter(Pos p, int m) {
        p.make(m);
        try {
            String o = p.over();
            return o.startsWith("Пат") || o.startsWith("Ничья");
        } finally {
            p.unmake();
        }
    }

    /**
     * Модель «живого» соперника. p стоит в позиции ПОСЛЕ хода кандидата m (ходит соперник).
     * Вес ответа = exp((h − hmax)/T) × множитель «очевидности»: взятия, перезахват, шахи и уход
     * атакованной фигуры люди видят первыми; ходы на заведомо плохие поля — реже.
     * h — оценка ответа глазами соперника с глубиной 1 + взятия (то, что он видит сразу).
     */
    static double[] replyWeights(Pos p, int m, int[] rl, double temp) {
        int to = (m >> 7) & 127, n = rl.length;
        double[] h = new double[n], mult = new double[n], w = new double[n];
        double hmax = -1e18;
        int me = p.side ^ 1; // сторона, сделавшая ход m (мы)
        for (int i = 0; i < n; i++) {
            int r = rl[i], rf = r & 127, rt = (r >> 7) & 127;
            boolean cap = p.b[rt] != 0 || ((r >> 17) & 1) != 0;
            boolean fromAttacked = p.isAtt(rf, me);
            int pieceV = Pos.VAL[p.b[rf] & 7];
            p.make(r);
            int vUs = p.qs(-Pos.INF, Pos.INF, 1, 0);
            boolean givesChk = p.isAtt(p.ksq[p.side], p.side ^ 1);
            boolean landsAttacked = p.att(rt, p.side, true) < pieceV && !cap;
            p.unmake();
            h[i] = -vUs;
            if (h[i] > hmax) hmax = h[i];
            double mu = 1;
            if (cap && rt == to) mu *= 3.0;
            else if (cap) mu *= 1.7;
            if (givesChk) mu *= 1.5;
            if (((r >> 17) & 2) != 0) mu *= 1.2;
            if (fromAttacked) mu *= 1.4;
            if (landsAttacked) mu *= 0.5;
            mult[i] = mu;
        }
        double sum = 0;
        for (int i = 0; i < n; i++) {
            w[i] = Math.exp((h[i] - hmax) / temp) * mult[i] + 0.01;
            sum += w[i];
        }
        for (int i = 0; i < n; i++) w[i] /= sum;
        return w;
    }
}
