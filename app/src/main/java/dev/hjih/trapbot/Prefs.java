package dev.hjih.trapbot;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static SharedPreferences sp(Context c) { return c.getSharedPreferences("bot", Context.MODE_PRIVATE); }

    static boolean white(Context c) { return sp(c).getBoolean("white", true); }
    static int depth(Context c) { return Math.max(1, Math.min(100, sp(c).getInt("depth", 8))); }
    /**
     * Время на ход: одно число ("3") или диапазон ("0.1-1", "5-6", "0.1-50").
     * Для диапазона на каждый ход берётся случайное время из него. Возвращает нормализованный текст.
     */
    static String timeText(Context c) {
        SharedPreferences p = sp(c);
        String t = p.getString("timeText", null);
        if (t == null) t = fmt(p.getFloat("time", 3f)); // старая настройка — одно число
        float[] r = parseTime(t);
        return r == null ? "3" : fmtRange(r);
    }

    /** {мин, макс} в секундах (0.1–100) или null, если текст не разобрался */
    static float[] parseTime(String s) {
        if (s == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:[-–—]\\s*(\\d+(?:[.,]\\d+)?))?\\s*$").matcher(s);
        if (!m.matches()) return null;
        try {
            float a = Float.parseFloat(m.group(1).replace(',', '.'));
            float b = m.group(2) == null ? a : Float.parseFloat(m.group(2).replace(',', '.'));
            a = Math.max(0.1f, Math.min(100f, a));
            b = Math.max(0.1f, Math.min(100f, b));
            return a <= b ? new float[]{a, b} : new float[]{b, a};
        } catch (NumberFormatException e) { return null; }
    }

    static String fmtRange(float[] r) { return r[0] == r[1] ? fmt(r[0]) : fmt(r[0]) + "-" + fmt(r[1]); }

    private static String fmt(float v) {
        String s = String.format(java.util.Locale.US, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** время на ближайший ход, секунд: фиксированное или случайное из диапазона (с шагом 0.1) */
    static float timeSec(Context c) {
        float[] r = parseTime(timeText(c));
        if (r == null) return 3f;
        if (r[0] == r[1]) return r[0];
        int lo = Math.round(r[0] * 10), hi = Math.round(r[1] * 10);
        return (lo + new java.util.Random().nextInt(hi - lo + 1)) / 10f;
    }
    /** потоков движка: 0 = авто (все ядра, до 8), иначе 1–16 */
    static int threads(Context c) { return Math.max(0, Math.min(16, sp(c).getInt("threads", 0))); }
    static int cun(Context c) { return sp(c).getInt("cun", 150); }
    static int agg(Context c) { return Math.max(0, Math.min(300, sp(c).getInt("agg", 100))); }
    static boolean adapt(Context c) { return sp(c).getBoolean("adapt", true); }
    static boolean drag(Context c) { return sp(c).getBoolean("drag", false); }

    /** видимость плавающих кнопок, % (0 = полностью невидимы, но нажимаются) */
    static int btnAlpha(Context c) { return Math.max(0, Math.min(100, sp(c).getInt("balpha", 0))); }

    /** положение плавающей кнопки (пиксели экрана) или null */
    static int[] btnPos(Context c, String name) {
        SharedPreferences p = sp(c);
        if (!p.contains("fx_" + name)) return null;
        return new int[]{p.getInt("fx_" + name, 0), p.getInt("fy_" + name, 0)};
    }
    static void setBtnPos(Context c, String name, int x, int y) {
        sp(c).edit().putInt("fx_" + name, x).putInt("fy_" + name, y).apply();
    }

    static String tpl(Context c) { return sp(c).getString("tpl", null); }
    static void setTpl(Context c, String s) { sp(c).edit().putString("tpl", s).apply(); }

    /** x, y, size доски в пикселях экрана, либо null */
    static int[] board(Context c) {
        SharedPreferences p = sp(c);
        int s = p.getInt("bs", 0);
        if (s <= 0) return null;
        return new int[]{p.getInt("bx", 0), p.getInt("by", 0), s};
    }
    static void setBoard(Context c, int x, int y, int s) {
        sp(c).edit().putInt("bx", x).putInt("by", y).putInt("bs", s).apply();
    }
}
