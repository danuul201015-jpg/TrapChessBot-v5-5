package dev.hjih.trapbot;

/**
 * Общая таблица транспозиций для всех потоков поиска (Lazy SMP).
 * Запись = два long: key^data и data. Если поток успел записать только половину
 * (гонка), проверка key^data не совпадёт и запись просто игнорируется — блокировки не нужны.
 */
final class TT {
    private final long[] k, v;
    private final int mask;
    int gen;

    /** Размер таблицы по умолчанию: 2^22 записей × 16 байт = 64 МБ. */
    static final int DEFAULT_BITS = 22;

    /** Выделяет таблицу на bits бит; если памяти не хватает — уменьшает вдвое, пока не поместится. */
    static TT alloc(int bits) {
        for (int b = bits; ; b--) {
            try { return new TT(b); }
            catch (OutOfMemoryError e) { if (b <= 16) throw e; }
        }
    }

    TT(int bits) {
        k = new long[1 << bits];
        v = new long[1 << bits];
        mask = (1 << bits) - 1;
    }

    void newGen() { gen = (gen + 1) & 63; }

    void clear() { java.util.Arrays.fill(k, 0); java.util.Arrays.fill(v, 0); }

    long probe(long key) {
        int i = (int) key & mask;
        long d = v[i];
        if (d != 0 && (k[i] ^ d) == key) return d;
        return 0;
    }

    /** flag: 0 — точная оценка, 1 — нижняя граница, 2 — верхняя граница */
    void store(long key, int move, int val, int depth, int flag) {
        int i = (int) key & mask;
        long old = v[i];
        if (old != 0 && (int) ((old >>> 52) & 63) == gen && depth(old) > depth + 2) return;
        if (depth < 0) depth = 0;
        if (depth > 255) depth = 255;
        long d = (move & 0xFFFFFL) | ((long) (val + (1 << 21)) << 20) | ((long) depth << 42)
                | ((long) flag << 50) | ((long) gen << 52) | (1L << 62);
        k[i] = key ^ d;
        v[i] = d;
    }

    static int move(long d) { return (int) (d & 0xFFFFFL); }
    static int val(long d) { return (int) ((d >>> 20) & 0x3FFFFFL) - (1 << 21); }
    static int depth(long d) { return (int) ((d >>> 42) & 0xFF); }
    static int flag(long d) { return (int) ((d >>> 50) & 3); }
}
