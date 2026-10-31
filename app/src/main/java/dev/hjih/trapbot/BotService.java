package dev.hjih.trapbot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Служба доступа: рисует плавающие кнопки и рамку поверх экрана, нажимает на клетки (ходит),
 * читает доску из трансляции экрана и спрашивает ход у движка.
 */
public class BotService extends AccessibilityService {
    static volatile BotService inst;

    private WindowManager wm;
    private final Handler h = new Handler(Looper.getMainLooper());
    private EngineBridge eng;
    private FrameView frame;
    private WindowManager.LayoutParams flp;
    private final ArrayList<View> floats = new ArrayList<>();
    private GradientDrawable autoBg;
    private Rect board;
    private Recognizer.Templates tpl;
    private boolean running, autoOn, busy, wb = true;
    private String blocked, prevKey;
    private final Tracker tr = new Tracker();
    private int sw, sh;
    private final ArrayList<GradientDrawable> floatBg = new ArrayList<>();
    private final ArrayList<TextView> floatTv = new ArrayList<>();
    private int bAlpha;
    private int fails;
    private boolean retryNext;
    private TextView sideBtn;
    private final ArrayList<int[]> banned = new ArrayList<>();
    private String banKey;
    private int[] lastTry;
    private boolean sessionActed;
    private long lastErrToast;
    private int syncFails;

    @Override protected void onServiceConnected() {
        inst = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }

    @Override public boolean onUnbind(Intent i) {
        stopBot();
        inst = null;
        return super.onUnbind(i);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ------------------------------------------------------------ запуск / остановка

    @SuppressWarnings("deprecation")
    void startBot() {
        if (running) return;
        if (CaptureService.inst == null) { toast("Нет доступа к экрану — нажмите «Запустить» в приложении"); return; }
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        sw = dm.widthPixels;
        sh = dm.heightPixels;
        running = true;
        autoOn = false;
        busy = false;
        blocked = null;
        prevKey = null;
        tr.reset(); // по умолчанию — начальная позиция
        sessionActed = false;
        banned.clear();
        banKey = null;
        wb = Prefs.white(this);
        tpl = Recognizer.Templates.parse(Prefs.tpl(this));
        int[] b = Prefs.board(this);
        board = b == null ? null : new Rect(b[0], b[1], b[0] + b[2], b[1] + b[2]);
        eng = new EngineBridge(getApplicationContext());
        addFloats();
        showFrame();
        toast(tpl == null ? "Наведите рамку на доску (начальная позиция) → «Запомнить» → «Готово»"
                : "Проверьте рамку и нажмите «Готово»");
    }

    void stopBot() {
        if (!running) return;
        running = false;
        autoOn = false;
        busy = false;
        h.removeCallbacksAndMessages(null);
        for (View v : floats) removeView(v);
        floats.clear();
        floatBg.clear();
        floatTv.clear();
        hideFrame();
        if (eng != null) { eng.destroy(); eng = null; }
        autoBg = null;
    }

    private void removeView(View v) {
        try { wm.removeView(v); } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------ рамка

    private WindowManager.LayoutParams overlayParams(int w, int hgt, int x, int y) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, hgt,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = x;
        lp.y = y;
        return lp;
    }

    private void showFrame() {
        if (frame != null) return;
        int x, y, s;
        if (board != null) { x = board.left; y = board.top; s = board.width(); }
        else { s = (int) (sw * 0.92f); x = (sw - s) / 2; y = (sh - s) / 2; }
        flp = overlayParams(s, s, x, y);
        frame = new FrameView(this, new FrameView.Cb() {
            @Override public void done() { finishFraming(); }
            @Override public void learn() { learnNow(); }
            @Override public void drag(int dx, int dy) {
                flp.x += dx;
                flp.y += dy;
                wm.updateViewLayout(frame, flp);
            }
            @Override public void resize(int ds) {
                int s2 = Math.max(240, Math.min(sw, flp.width + ds));
                flp.width = s2;
                flp.height = s2;
                wm.updateViewLayout(frame, flp);
            }
        });
        wm.addView(frame, flp);
    }

    private void hideFrame() {
        if (frame != null) { removeView(frame); frame = null; }
    }

    private Rect currentRect() {
        int[] l = new int[2];
        frame.getLocationOnScreen(l);
        return new Rect(l[0], l[1], l[0] + frame.getWidth(), l[1] + frame.getHeight());
    }

    private void finishFraming() {
        if (frame == null) return;
        board = currentRect();
        Prefs.setBoard(this, board.left, board.top, board.width());
        hideFrame();
        toast(tpl == null ? "Фигуры ещё не запомнены — откройте «▣» и нажмите «Запомнить»" : "Готово. A — авто, 1 — один ход");
    }

    private void setFloatsVisible(boolean v) {
        for (View f : floats) f.setVisibility(v ? View.VISIBLE : View.INVISIBLE);
    }

    private void learnNow() {
        if (frame == null || CaptureService.inst == null || busy) return;
        busy = true;
        final Rect r = currentRect();
        frame.setVisibility(View.INVISIBLE); // рамка и кнопки не должны попасть в кадр
        setFloatsVisible(false);
        toast("Запоминаю фигуры…");
        final CaptureService cs0 = CaptureService.inst;
        final int seq0 = cs0.seq();
        final long t0 = System.currentTimeMillis();
        h.postDelayed(new Runnable() {
            @Override public void run() {
                CaptureService cs = CaptureService.inst;
                // ждём, пока придёт кадр уже БЕЗ рамки и кнопок (но не дольше 2 с)
                if (cs != null && cs.seq() == seq0 && System.currentTimeMillis() - t0 < 2000) { h.postDelayed(this, 100); return; }
                final CaptureService fcs = cs;
                final boolean white = wb;
                new Thread(new Runnable() {
                    @Override public void run() {
                        final String[] err = new String[1];
                        final int[] bd = new int[3];
                        Recognizer.Templates t = null;
                        String note = "";
                        int pad = 48;
                        Rect big = new Rect(Math.max(0, r.left - pad), Math.max(0, r.top - pad),
                                Math.min(sw, r.right + pad), Math.min(sh, r.bottom + pad));
                        Recognizer.Frame bf = fcs == null ? null : fcs.grab(big);
                        if (bf == null) err[0] = "Не удалось получить кадр экрана";
                        else {
                            int[] loc = Recognizer.locate(bf, r.left - big.left, r.top - big.top, r.width(), pad);
                            int bx, by, bs;
                            if (loc != null) { bx = loc[0]; by = loc[1]; bs = loc[2]; note = " (рамка подогнана)"; }
                            else { bx = r.left - big.left; by = r.top - big.top; bs = r.width(); }
                            bs = Math.min(bs, Math.min(bf.w - bx, bf.h - by));
                            t = Recognizer.learn(bf.view(bx, by, bs), white, err);
                            bd[0] = big.left + bx; bd[1] = big.top + by; bd[2] = bs;
                        }
                        final Recognizer.Templates ft = t;
                        final String fnote = note;
                        h.post(new Runnable() {
                            @Override public void run() {
                                busy = false;
                                if (!running) return;
                                if (frame != null) frame.setVisibility(View.VISIBLE);
                                setFloatsVisible(true);
                                if (ft == null) { toastLong(err[0]); return; }
                                board = new Rect(bd[0], bd[1], bd[0] + bd[2], bd[1] + bd[2]);
                                Prefs.setBoard(BotService.this, bd[0], bd[1], bd[2]);
                                tpl = ft;
                                Prefs.setTpl(BotService.this, ft.ser());
                                if (frame != null) { // сдвигаем рамку на найденную доску
                                    flp.x = bd[0]; flp.y = bd[1]; flp.width = bd[2]; flp.height = bd[2];
                                    wm.updateViewLayout(frame, flp);
                                }
                                toast("Фигуры запомнены ✓" + fnote + " Нажмите «Готово»");
                                warnFloatsOnBoard();
                            }
                        });
                    }
                }).start();
            }
        }, 400);
    }

    private void toastLong(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    /** видимые кнопки поверх доски мешают распознаванию */
    private void warnFloatsOnBoard() {
        if (bAlpha <= 0 || board == null) return;
        Rect fr = new Rect();
        for (View v : floats) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
            fr.set(lp.x, lp.y, lp.x + lp.width, lp.y + lp.height);
            if (Rect.intersects(fr, board)) {
                toastLong("Кнопка лежит на доске — бот будет путаться. Перетащите её за доску или сделайте невидимой в настройках.");
                return;
            }
        }
    }

    // ------------------------------------------------------------ плавающие кнопки

    private void addFloats() {
        bAlpha = Prefs.btnAlpha(this);
        int size = dp(54);
        int gap = dp(64);
        int x0 = sw - size - dp(10), y0 = sh - dp(190) - 4 * gap; // по умолчанию — правый нижний угол, вне доски
        TextView a = floatBtn("A", "A", x0, y0, size, new Runnable() { @Override public void run() { setAuto(!autoOn); } });
        autoBg = (GradientDrawable) a.getBackground();
        floatBtn("1", "1", x0, y0 + gap, size, new Runnable() { @Override public void run() { oneMove(); } });
        floatBtn("▣", "F", x0, y0 + 2 * gap, size, new Runnable() { @Override public void run() { showFrame(); } });
        sideBtn = floatBtn(wb ? "Б" : "Ч", "S", x0, y0 + 3 * gap, size, new Runnable() { @Override public void run() { switchSide(); } });
        applyFloatLook(false);
        // подсказка: первые 4 секунды кнопки видны, чтобы было ясно, где они
        if (bAlpha < 60) {
            applyFloatLook(true);
            h.postDelayed(new Runnable() { @Override public void run() { applyFloatLook(false); } }, 4000);
        }
    }

    /** bAlpha 0 = невидимы (но нажимаются), 100 = как есть */
    private void applyFloatLook(boolean flash) {
        float k = flash ? 0.8f : bAlpha / 100f;
        for (int i = 0; i < floatBg.size(); i++) {
            GradientDrawable g = floatBg.get(i);
            boolean on = g == autoBg && autoOn;
            int base = on ? 0xCC2E7D32 : 0x66202428;
            // фон никогда не бывает строго прозрачным (1/255), чтобы кнопка гарантированно принимала касания
            int a = k <= 0 ? 1 : Math.max(1, Math.round(((base >>> 24) * k)));
            g.setColor((a << 24) | (base & 0xFFFFFF));
            g.setStroke(dp(2), k <= 0 ? 0x00FFFFFF : ((Math.round(0x99 * k) << 24) | 0xFFFFFF));
            floatTv.get(i).setTextColor(k <= 0 ? 0x00FFFFFF : ((Math.round(255 * k) << 24) | 0xFFFFFF));
        }
    }

    private TextView floatBtn(String txt, final String id, int x, int y, int size, final Runnable click) {
        final TextView t = new TextView(this);
        t.setText(txt);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        t.setBackground(g);
        floatBg.add(g);
        floatTv.add(t);
        int[] saved = Prefs.btnPos(this, id);
        if (saved != null) {
            x = Math.max(0, Math.min(sw - size, saved[0]));
            y = Math.max(0, Math.min(sh - size, saved[1]));
        }
        final WindowManager.LayoutParams lp = overlayParams(size, size, x, y);
        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        t.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy;
            int sx, sy;
            boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX(); dy = e.getRawY(); sx = lp.x; sy = lp.y; moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float mx = e.getRawX() - dx, my = e.getRawY() - dy;
                        if (!moved && Math.hypot(mx, my) > slop) moved = true;
                        if (moved) {
                            lp.x = sx + (int) mx;
                            lp.y = sy + (int) my;
                            wm.updateViewLayout(t, lp);
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (moved) Prefs.setBtnPos(BotService.this, id, lp.x, lp.y);
                        else click.run();
                        return true;
                    default:
                        return false;
                }
            }
        });
        wm.addView(t, lp);
        floats.add(t);
        return t;
    }

    // ------------------------------------------------------------ логика игры

    private boolean ready() {
        if (frame != null) { toast("Сначала нажмите «Готово» на рамке"); return false; }
        if (board == null || tpl == null || !tpl.valid) {
            toast("Сначала «▣» → рамка на доску → «Запомнить» в начальной позиции");
            return false;
        }
        return true;
    }

    private Recognizer.Res read() {
        CaptureService cs = CaptureService.inst;
        if (cs == null || board == null || tpl == null) return null;
        Recognizer.Frame f = cs.grab(board);
        if (f == null) return null;
        return Recognizer.recognize(f, tpl, wb);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running || !autoOn) return;
            if (!busy) autoStep();
            h.postDelayed(this, 150);
        }
    };

    private void setAuto(boolean on) {
        if (on && !ready()) return;
        autoOn = on;
        applyFloatLook(false);
        h.removeCallbacks(tick);
        if (on) { tr.rescan(); blocked = null; syncFails = 0; prevKey = null; fails = 0; retryNext = false; sessionActed = false; banned.clear(); banKey = null; h.postDelayed(tick, 100); }
        toast(on ? "Авто-режим включён" : "Авто-режим выключен");
    }

    private void autoStep() {
        Recognizer.Res r = read();
        if (r == null || !r.occOk) { prevKey = null; return; }
        if (r.occKey.equals(Recognizer.START_OCC)) blocked = wb ? null : r.occKey;
        boolean stable = r.occKey.equals(prevKey);
        prevKey = r.occKey;
        if (!stable) return;                  // фигуры ещё двигаются
        if (r.occKey.equals(blocked)) return; // ждём хода соперника
        if (!tr.sync(r, wb)) {                // позицию пока не удалось определить
            if (++syncFails >= 20) {
                syncFails = 0;
                long now = System.currentTimeMillis();
                if (now - lastErrToast > 4000) { lastErrToast = now; toast("Не удаётся определить позицию на доске — проверьте рамку и сторону"); }
            }
            return;
        }
        syncFails = 0;
        if (tr.side() != (wb ? 0 : 1)) return; // по партии сейчас ход соперника
        act(r);
    }

    private void oneMove() {
        if (busy) { toast("Движок ещё думает…"); return; }
        if (!ready()) return;
        Recognizer.Res r = read();
        if (r == null) { toast("Не удалось прочитать экран"); return; }
        if (!r.occOk || !tr.sync(r, wb)) { toast("Доска не распознана: " + (r.msg.isEmpty() ? "позиция не сходится" : r.msg)); return; }
        act(r);
    }

    private void act(final Recognizer.Res r) {
        busy = true;
        final String oldKey = r.occKey;
        String fen = tr.fen(wb);
        if (!oldKey.equals(banKey)) { banned.clear(); banKey = oldKey; }
        org.json.JSONArray ban = new org.json.JSONArray();
        for (int[] bm : banned) {
            org.json.JSONArray a = new org.json.JSONArray();
            for (int v : bm) a.put(v);
            ban.put(a);
        }
        eng.think(fen, Prefs.depth(this), Prefs.timeSec(this), Prefs.cun(this), Prefs.agg(this), Prefs.adapt(this), ban, new EngineBridge.CB() {
            @Override public void done(JSONObject o) {
                if (!running) { busy = false; return; }
                if (o.has("error")) {
                    String er = o.optString("error");
                    busy = false;
                    prevKey = null;
                    if (er.startsWith("Позиция")) { // плохое распознавание — пересоберём позицию заново
                        tr.invalidate();
                        long now = System.currentTimeMillis();
                        if (now - lastErrToast > 4000) { lastErrToast = now; toast(er); }
                        return;
                    }
                    toast("Движок: " + er);
                    setAuto(false);
                    return;
                }
                if (o.optBoolean("none")) {
                    toast(o.optString("over", "Нет ходов"));
                    blocked = oldKey;
                    busy = false;
                    setAuto(false);
                    return;
                }
                int ff = o.optInt("ff"), fr = o.optInt("fr"), tf = o.optInt("tf"), trk = o.optInt("tr"), pr = o.optInt("pr");
                String info = o.optString("info", "");
                if (!info.isEmpty()) toast(info);
                final String expKey = tr.occAfter(ff, fr, tf, trk, pr);
                final int mFf = ff, mFr = fr, mTf = tf, mTr = trk, mPr = pr;
                final boolean retry = retryNext;
                lastTry = new int[]{ff, fr, tf, trk};
                int mode = Prefs.drag(BotService.this) ? 2 : (fails == 0 ? 0 : fails == 1 ? 1 : (fails % 2 == 0 ? 2 : 0));
                doMove(ff, fr, tf, trk, pr > 0, mode, new Runnable() {
                    @Override public void run() { verify(oldKey, expKey, new int[]{mFf, mFr, mTf, mTr, mPr}, 0, null); }
                });
            }
        });
    }

    /** ждём, пока ход появится на доске (приложение может отрисовать его с задержкой); при неудаче — повторяем */
    private void verify(final String oldKey, final String expKey, final int[] mv, final int n, final String lastKey) {
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (!running) { busy = false; return; }
                Recognizer.Res r2 = read();
                String k = (r2 != null && r2.occOk) ? r2.occKey : null;
                if (k != null && !k.equals(oldKey) && k.equals(lastKey)) {
                    fails = 0;
                    retryNext = false;
                    sessionActed = true;
                    if (k.equals(expKey)) { tr.commit(mv[0], mv[1], mv[2], mv[3], mv[4]); blocked = expKey; }
                    else blocked = null; // иначе соперник уже ответил — позицию догонит трекер
                    prevKey = null;
                    busy = false;
                    return;
                }
                if (n >= 20) { // ~2.5 с без изменений: нажатие не сработало
                    fails++;
                    prevKey = null;
                    if (!sessionActed && !Recognizer.START_OCC.equals(oldKey) && fails == 1) {
                        // партия уже шла, когда бот включили: скорее всего сейчас ход соперника
                        fails = 0;
                        retryNext = false;
                        blocked = oldKey;
                        toast("Похоже, ход соперника — жду его хода (кнопка «1» — ходить сразу)");
                    } else if (fails >= 6) {
                        fails = 0;
                        retryNext = false;
                        blocked = oldKey;
                        toast("Ход не получается. Проверьте рамку/сторону или нажмите «1»");
                    } else {
                        retryNext = true;
                        // со второй неудачи этот ход считаем невозможным и просим движок выбрать другой
                        if (fails >= 2 && lastTry != null) banned.add(lastTry);
                    }
                    busy = false;
                    return;
                }
                verify(oldKey, expKey, mv, n + 1, k);
            }
        }, 120);
    }

    private void switchSide() {
        wb = !wb;
        Prefs.sp(this).edit().putBoolean("white", wb).apply();
        blocked = null;
        prevKey = null;
        tr.rescan();
        fails = 0;
        retryNext = false;
        sessionActed = false;
        banned.clear();
        banKey = null;
        if (sideBtn != null) sideBtn.setText(wb ? "Б" : "Ч");
        toast(wb ? "Играю белыми (белые внизу)" : "Играю чёрными (чёрные внизу)");
    }

    // ------------------------------------------------------------ нажатия

    private PointF center(int file, int rank) {
        int col = wb ? file : 7 - file, row = wb ? 7 - rank : rank;
        float cell = board.height() / 8f;
        float x = board.left + (col + .5f) * board.width() / 8f;
        float y = board.top + (row + .5f) * cell;
        if (row == 7) y -= 0.12f * cell;                 // нижний ряд: чуть выше центра клетки
        y = Math.min(y, sh - dp(28));                    // не попадать в зону системных жестов внизу
        y = Math.max(y, dp(8));
        return new PointF(x, y);
    }

    private void doMove(int ff, int fr, int tf, int tr, final boolean promo, int mode, final Runnable done) {
        final PointF a = center(ff, fr), b = center(tf, tr);
        final Runnable fin = new Runnable() {
            @Override public void run() {
                if (promo) h.postDelayed(new Runnable() { @Override public void run() { tap(b.x, b.y, done); } }, 350); // ферзь
                else done.run();
            }
        };
        if (mode == 2) swipe(a, b, fin);
        else if (mode == 1) {
            // прошлая попытка могла оставить фигуру выбранной: сперва «клетка назначения», потом заново
            tap(b.x, b.y, new Runnable() { @Override public void run() {
                tap(a.x, a.y, new Runnable() { @Override public void run() { tap(b.x, b.y, fin); } });
            } });
        }
        else tap(a.x, a.y, new Runnable() { @Override public void run() { tap(b.x, b.y, fin); } });
    }

    private void tap(float x, float y, Runnable then) {
        Path p = new Path();
        p.moveTo(x, y);
        gesture(new GestureDescription.StrokeDescription(p, 0, 50), then);
    }

    private void swipe(PointF a, PointF b, Runnable then) {
        Path p = new Path();
        p.moveTo(a.x, a.y);
        p.lineTo(b.x, b.y);
        gesture(new GestureDescription.StrokeDescription(p, 0, 160), then);
    }

    private void gesture(GestureDescription.StrokeDescription sd, final Runnable then) {
        GestureDescription g = new GestureDescription.Builder().addStroke(sd).build();
        boolean ok = dispatchGesture(g, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription d) { if (then != null) h.postDelayed(then, 40); }
            @Override public void onCancelled(GestureDescription d) { if (then != null) h.postDelayed(then, 40); }
        }, null);
        if (!ok && then != null) h.postDelayed(then, 40);
    }
}
