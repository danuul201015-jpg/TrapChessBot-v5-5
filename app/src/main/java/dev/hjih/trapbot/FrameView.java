package dev.hjih.trapbot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Рамка для наведения на доску: перетаскивается, тянется за правый нижний угол. */
final class FrameView extends View {
    interface Cb { void done(); void learn(); void drag(int dx, int dy); void resize(int ds); }

    private final Cb cb;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), grid = new Paint(Paint.ANTI_ALIAS_FLAG),
            btn = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG), hnd = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rDone = new RectF(), rLearn = new RectF();
    private final float d;
    private float lx, ly;
    private int mode; // 1 двигать, 2 тянуть, 3 «Готово», 4 «Запомнить»

    FrameView(Context c, Cb cb) {
        super(c);
        this.cb = cb;
        d = c.getResources().getDisplayMetrics().density;
        line.setColor(0xFFFFC107); line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(3 * d);
        grid.setColor(0x88FFC107); grid.setStyle(Paint.Style.STROKE); grid.setStrokeWidth(1);
        btn.setColor(0xCC202428);
        txt.setColor(0xFFFFFFFF); txt.setTextSize(14 * d); txt.setTextAlign(Paint.Align.CENTER);
        hnd.setColor(0xFFFFC107);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        c.drawRect(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d, line);
        for (int i = 1; i < 8; i++) {
            c.drawLine(i * w / 8f, 0, i * w / 8f, h, grid);
            c.drawLine(0, i * h / 8f, w, i * h / 8f, grid);
        }
        rLearn.set(8 * d, 8 * d, w / 2f - 4 * d, 56 * d);
        rDone.set(w / 2f + 4 * d, 8 * d, w - 8 * d, 56 * d);
        c.drawRoundRect(rLearn, 8 * d, 8 * d, btn);
        c.drawRoundRect(rDone, 8 * d, 8 * d, btn);
        float ty = 38 * d;
        c.drawText("Запомнить", rLearn.centerX(), ty, txt);
        c.drawText("Готово", rDone.centerX(), ty, txt);
        Path p = new Path();
        float s = 40 * d;
        p.moveTo(w, h - s); p.lineTo(w, h); p.lineTo(w - s, h); p.close();
        c.drawPath(p, hnd);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lx = e.getRawX(); ly = e.getRawY();
                if (rDone.contains(x, y)) mode = 3;
                else if (rLearn.contains(x, y)) mode = 4;
                else if (x > getWidth() - 48 * d && y > getHeight() - 48 * d) mode = 2;
                else mode = 1;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - lx, dy = e.getRawY() - ly;
                lx = e.getRawX(); ly = e.getRawY();
                if (mode == 1) cb.drag(Math.round(dx), Math.round(dy));
                else if (mode == 2) cb.resize(Math.round((dx + dy) / 2f));
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (mode == 3 && rDone.contains(x, y)) cb.done();
                else if (mode == 4 && rLearn.contains(x, y)) cb.learn();
                mode = 0;
                return true;
            default:
                return true;
        }
    }
}
