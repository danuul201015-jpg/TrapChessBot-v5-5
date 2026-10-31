package dev.hjih.trapbot;

/**
 * Ведёт партию сам: тип каждой фигуры известен с начальной позиции и дальше меняется только
 * законными ходами. С экрана читается лишь «пусто / белая / чёрная» (это надёжно), а вот король и ферзь
 * по силуэту похожи — поэтому тип фигуры по картинке здесь больше не нужен.
 * Силуэты используются только при включении бота посреди партии и при потере нити (тогда позиция
 * собирается заново).
 */
final class Tracker {
    static final String START_FEN = Recognizer.START + " w KQkq - 0 1";
    private static final String SYM = "PNBRQKpnbrqk";

    private final Pos p = new Pos(new TT(4));
    private final Pos scratch = new Pos(new TT(4)); // для проверки собранной позиции, не портя ведомую
    private boolean has;
    private boolean fresh; // при следующей сверке с экраном заново собрать позицию с доски
    private String target;
    private final int[] path = new int[4];

    /** По умолчанию — начальная позиция шахмат, ход белых. */
    Tracker() { reset(); }

    boolean has() { return has; }
    void invalidate() { has = false; fresh = false; }
    /** начальная позиция по умолчанию */
    void reset() { p.setFen(START_FEN); has = true; fresh = false; }
    /** заново просканировать доску при следующей сверке (старая ведомая партия — только запасной вариант) */
    void rescan() { fresh = true; }
    /** чья очередь по ведомой позиции: 0 — белые, 1 — чёрные */
    int side() { return p.side; }

    private static char sym(int code) { return SYM.charAt(code < 9 ? code - 1 : code - 9 + 6); }

    private static int code(char ch) {
        int k = SYM.indexOf(ch);
        return k < 0 ? 0 : (k < 6 ? k + 1 : k - 6 + 9);
    }

    private static char occ(int v) { return v == 0 ? '0' : (v >> 3) == 0 ? '1' : '2'; }

    private String occNow() {
        char[] c = new char[64];
        for (int r = 0; r < 8; r++) for (int f = 0; f < 8; f++) c[r * 8 + f] = occ(p.b[r * 16 + f]);
        return new String(c);
    }

    /** сколько клеток отличаются от целевой занятости */
    private int diff() {
        int n = 0;
        for (int r = 0; r < 8; r++) for (int f = 0; f < 8; f++)
            if (occ(p.b[r * 16 + f]) != target.charAt(r * 8 + f)) n++;
        return n;
    }

    private boolean dfs(int d, int depth) {
        int df = diff();
        if (d == 0) return df == 0;
        if (df > 4 * d) return false;
        int[] lm = p.legalMoves();
        for (int m : lm) {
            int pr = (m >> 14) & 7;
            if (pr != 0 && pr != 5) continue; // слабые превращения от ферзя по занятости не отличить
            p.make(m);
            path[depth] = m;
            if (dfs(d - 1, depth + 1)) return true;
            p.unmake();
        }
        return false;
    }

    /** найти 1–3 полухода (в любом порядке очередей), после которых занятость совпадёт с экраном */
    private boolean follow(Recognizer.Res r) {
        target = r.occKey;
        int side0 = p.side, ep0 = p.ep;
        for (int d = 1; d <= 3; d++) {
            for (int k = 0; k < 2; k++) {
                p.side = side0 ^ k;
                if (k == 1) p.ep = -1;
                int sp0 = p.sp;
                if (dfs(d, 0)) {
                    fixPromotions(r, d);
                    return true;
                }
                p.sp = sp0;
            }
            p.side = side0;
            p.ep = ep0;
        }
        p.side = side0;
        p.ep = ep0;
        return false;
    }

    /** слабое превращение (конь/слон/ладья) берём с картинки, иначе остаётся ферзь */
    private void fixPromotions(Recognizer.Res r, int plies) {
        for (int i = 0; i < plies; i++) {
            int m = path[i];
            if (((m >> 14) & 7) == 0) continue;
            int t = (m >> 7) & 127, v = p.b[t];
            int ty = v & 7;
            if (v == 0 || ty < 2 || ty > 5) continue;
            char c = r.b[t >> 4][t & 7];
            int cc = code(c);
            if (cc != 0 && (cc >> 3) == (v >> 3) && (cc & 7) >= 2 && (cc & 7) <= 5) p.b[t] = cc;
        }
    }

    /** слишком много расхождений типов фигур с картинкой (кроме пары король/ферзь) — значит, нить потеряна */
    private boolean drift(Recognizer.Res r) {
        if (!r.ok) return false;
        int bad = 0;
        for (int rk = 0; rk < 8; rk++) for (int f = 0; f < 8; f++) {
            int v = p.b[rk * 16 + f];
            char seen = r.b[rk][f];
            if (v == 0 || seen == ' ' || seen == '?') continue;
            char mine = sym(v);
            char a = Character.toUpperCase(mine), b = Character.toUpperCase(seen);
            if (a == 'K' || a == 'Q' || b == 'K' || b == 'Q') continue;
            if (mine != seen) bad++;
        }
        return bad >= 3;
    }

    /**
     * Сопоставляет экран с ведомой позицией. true — позиция известна и согласована с экраном.
     * ourWhite нужен только при пересборке из силуэтов (кто ходит — считаем, что мы).
     */
    boolean sync(Recognizer.Res r, boolean ourWhite) {
        if (r == null || !r.occOk) return false;
        if (r.occKey.equals(Recognizer.START_OCC)) { reset(); return true; }
        if (fresh) {
            // позиция на экране совпала с ведомой партией — типы фигур уже известны точно, оставляем
            if (has && r.occKey.equals(occNow())) fresh = false;
            // иначе собираем позицию заново с доски (партия могла продолжиться без бота)
            else if (r.ok && rebuild(r, ourWhite)) fresh = false;
            if (!fresh) return true;
            // силуэты не читаются — пробуем догнать по ведомой партии
        }
        if (has) {
            boolean same = r.occKey.equals(occNow());
            if (same || follow(r)) {
                if (!drift(r)) { fresh = false; return true; }
            }
        }
        if (!r.ok) { has = false; return false; }
        fresh = false;
        if (rebuild(r, ourWhite)) return true;
        has = false;
        return false;
    }

    /** собрать позицию из силуэтов на экране (ход — нашей стороны); ведомая партия меняется только при успехе */
    private boolean rebuild(Recognizer.Res r, boolean ourWhite) {
        int cs = Recognizer.possibleRights(r.b);
        for (int k = 0; k < 2; k++) {
            boolean w = k == 0 ? ourWhite : !ourWhite;
            String fen = r.key + " " + (w ? "w" : "b") + " " + Recognizer.castle(cs) + " - 0 1";
            scratch.setFen(fen);
            if (scratch.sane().isEmpty()) { p.setFen(fen); has = true; return true; }
        }
        return false;
    }

    /** FEN для движка: ход нашей стороны; права рокировки и взятие на проходе — из ведомой партии */
    String fen(boolean ourWhite) {
        int want = ourWhite ? 0 : 1;
        if (p.side != want) { p.side = want; p.ep = -1; }
        StringBuilder sb = new StringBuilder();
        for (int rk = 7; rk >= 0; rk--) {
            int e = 0;
            for (int f = 0; f < 8; f++) {
                int v = p.b[rk * 16 + f];
                if (v == 0) { e++; continue; }
                if (e > 0) { sb.append(e); e = 0; }
                sb.append(sym(v));
            }
            if (e > 0) sb.append(e);
            if (rk > 0) sb.append('/');
        }
        String cs = ((p.cast & 1) != 0 ? "K" : "") + ((p.cast & 2) != 0 ? "Q" : "")
                + ((p.cast & 4) != 0 ? "k" : "") + ((p.cast & 8) != 0 ? "q" : "");
        return sb + " " + (ourWhite ? "w" : "b") + " " + (cs.isEmpty() ? "-" : cs) + " "
                + (p.ep >= 0 ? Pos.sqn(p.ep) : "-") + " 0 1";
    }

    private int find(int ff, int fr, int tf, int tr, int pr) {
        int want = pr > 0 ? 5 : 0;
        for (int m : p.legalMoves()) {
            int f = m & 127, t = (m >> 7) & 127;
            if ((f & 7) == ff && (f >> 4) == fr && (t & 7) == tf && (t >> 4) == tr && ((m >> 14) & 7) == want) return m;
        }
        return -1;
    }

    /** занятость после нашего хода (для проверки, что ход появился на доске); null — ход нелегален */
    String occAfter(int ff, int fr, int tf, int tr, int pr) {
        int m = find(ff, fr, tf, tr, pr);
        if (m < 0) return null;
        p.make(m);
        String s = occNow();
        p.unmake();
        return s;
    }

    /** ход сделан на доске — применяем его к ведомой позиции */
    void commit(int ff, int fr, int tf, int tr, int pr) {
        int m = find(ff, fr, tf, tr, pr);
        if (m < 0) { has = false; return; }
        p.make(m);
    }
}
