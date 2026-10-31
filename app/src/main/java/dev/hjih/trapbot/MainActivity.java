package dev.hjih.trapbot;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;

public class MainActivity extends Activity {
    private EditText depth, time, threadsEt;
    private SeekBar cun, agg, balpha;
    private TextView cunTxt, aggTxt, balphaTxt, status;
    private RadioButton rw, rb;
    private CheckBox adapt, drag;

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView tv(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(8), 0, dp(4));
        return t;
    }

    private Button btn(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setOnClickListener(l);
        return b;
    }

    @Override protected void onCreate(Bundle sb) {
        super.onCreate(sb);
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);

        ScrollView sv = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(16), dp(24), dp(16), dp(24));
        sv.addView(ll);
        setContentView(sv);

        ll.addView(tv("Trap Chess Bot", 22, true));
        ll.addView(tv("1. Включите службу доступа «Trap Chess Bot» (нужна для кнопок поверх экрана и нажатий по доске). "
                + "Android 13+: если пункт серый — Настройки → Приложения → Trap Chess Bot → ⋮ → «Разрешить ограниченные настройки».\n"
                + "2. Задайте настройки ниже.\n"
                + "3. Нажмите «Запустить» и разрешите трансляцию ВСЕГО экрана.\n"
                + "4. Откройте шахматное приложение (начальная позиция на экране), наведите жёлтую рамку на доску, "
                + "нажмите «Запомнить», затем «Готово».\n"
                + "5. A — авто-режим, 1 — один ход, ▣ — показать рамку. Кнопки можно перетаскивать; ставьте их за пределами доски.", 14, false));

        ll.addView(btn("Включить службу доступа", v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));

        ll.addView(tv("Я играю (цвет, который внизу доски на экране)", 15, true));
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        rw = new RadioButton(this); rw.setText("Белыми"); rw.setId(View.generateViewId());
        rb = new RadioButton(this); rb.setText("Чёрными"); rb.setId(View.generateViewId());
        rg.addView(rw); rg.addView(rb);
        ll.addView(rg);

        ll.addView(tv("Глубина (1–100)", 15, true));
        depth = new EditText(this);
        depth.setInputType(InputType.TYPE_CLASS_NUMBER);
        ll.addView(depth);

        ll.addView(tv("Время на ход, секунд: число или диапазон (3 · 0.1-1 · 5-6 · 0.1-50), от 0.1 до 100", 15, true));
        time = new EditText(this);
        time.setInputType(InputType.TYPE_CLASS_TEXT);
        ll.addView(time);

        ll.addView(tv("Потоки движка (0 = авто: все ядра, до 8; применяется при запуске)", 15, true));
        threadsEt = new EditText(this);
        threadsEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        ll.addView(threadsEt);

        cunTxt = tv("", 15, true);
        ll.addView(cunTxt);
        cun = new SeekBar(this);
        cun.setMax(300);
        cun.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { cunTxt.setText("Хитрость: " + p); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        ll.addView(cun);

        aggTxt = tv("", 15, true);
        ll.addView(aggTxt);
        agg = new SeekBar(this);
        agg.setMax(300);
        agg.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { aggTxt.setText("Агрессия: " + p); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        ll.addView(agg);
        ll.addView(tv("Хитрость — ловушки, вилки, связки. Агрессия — атака на короля, инициатива, игра на победу. "
                + "Шкалы независимы; жертва допускается, только если оправдана ловушкой или атакой.", 13, false));

        adapt = new CheckBox(this); adapt.setText("Адаптация под мою игру");
        drag = new CheckBox(this); drag.setText("Ходить перетаскиванием (вместо двух нажатий)");
        ll.addView(adapt);
        ll.addView(drag);

        balphaTxt = tv("", 15, true);
        ll.addView(balphaTxt);
        balpha = new SeekBar(this);
        balpha.setMax(100);
        balpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { balphaTxt.setText(balLabel(p)); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        ll.addView(balpha);
        ll.addView(tv("При 0% кнопки невидимы, но нажимаются; первые 4 секунды после запуска они подсвечены, "
                + "а их место запоминается после перетаскивания.", 13, false));

        ll.addView(btn("Запустить", v -> start()));
        ll.addView(btn("Остановить", v -> stop()));
        status = tv("", 14, false);
        ll.addView(status);

        rw.setChecked(Prefs.white(this));
        rb.setChecked(!Prefs.white(this));
        depth.setText(String.valueOf(Prefs.depth(this)));
        time.setText(Prefs.timeText(this));
        threadsEt.setText(String.valueOf(Prefs.threads(this)));
        cun.setProgress(Prefs.cun(this));
        cunTxt.setText("Хитрость: " + Prefs.cun(this));
        agg.setProgress(Prefs.agg(this));
        aggTxt.setText("Агрессия: " + Prefs.agg(this));
        adapt.setChecked(Prefs.adapt(this));
        drag.setChecked(Prefs.drag(this));
        balpha.setProgress(Prefs.btnAlpha(this));
        balphaTxt.setText(balLabel(Prefs.btnAlpha(this)));
    }

    private String balLabel(int p) {
        return "Видимость кнопок: " + p + "%" + (p == 0 ? " (невидимы)" : "");
    }

    @Override protected void onResume() {
        super.onResume();
        if (rw != null) { rw.setChecked(Prefs.white(this)); rb.setChecked(!Prefs.white(this)); } // сторону могла сменить кнопка «Б/Ч»
        refresh();
    }

    @Override protected void onPause() {
        save();
        super.onPause();
    }

    private void refresh() {
        status.setText("Служба доступа: " + (BotService.inst != null ? "включена" : "выключена")
                + "\nТрансляция экрана: " + (CaptureService.inst != null ? "идёт" : "нет"));
    }

    private void save() {
        int d;
        try { d = Integer.parseInt(depth.getText().toString().trim()); } catch (Exception e) { d = 8; }
        d = Math.max(1, Math.min(100, d));
        float[] tr = Prefs.parseTime(time.getText().toString());
        String t = tr == null ? "3" : Prefs.fmtRange(tr);
        int th;
        try { th = Integer.parseInt(threadsEt.getText().toString().trim()); } catch (Exception e) { th = 0; }
        th = Math.max(0, Math.min(16, th));
        threadsEt.setText(String.valueOf(th));
        Prefs.sp(this).edit()
                .putInt("threads", th).putInt("depth", d).putString("timeText", t).putInt("cun", cun.getProgress()).putInt("agg", agg.getProgress())
                .putBoolean("white", rw.isChecked()).putBoolean("adapt", adapt.isChecked())
                .putBoolean("drag", drag.isChecked()).putInt("balpha", balpha.getProgress()).apply();
        depth.setText(String.valueOf(d));
        time.setText(t);
    }

    private void start() {
        save();
        if (BotService.inst == null) {
            Toast.makeText(this, "Сначала включите службу доступа", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        MediaProjectionManager mm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mm.createScreenCaptureIntent(), 100);
    }

    private void stop() {
        BotService b = BotService.inst;
        if (b != null) b.stopBot();
        stopService(new Intent(this, CaptureService.class));
        refresh();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 100) return;
        if (res == RESULT_OK && data != null) {
            Intent i = new Intent(this, CaptureService.class);
            i.putExtra("rc", res);
            i.putExtra("data", data);
            startForegroundService(i);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                BotService b = BotService.inst;
                if (b == null) Toast.makeText(this, "Служба доступа не включена", Toast.LENGTH_LONG).show();
                else b.startBot();
                refresh();
            }, 900);
        } else {
            Toast.makeText(this, "Доступ к экрану не выдан", Toast.LENGTH_LONG).show();
        }
    }
}
