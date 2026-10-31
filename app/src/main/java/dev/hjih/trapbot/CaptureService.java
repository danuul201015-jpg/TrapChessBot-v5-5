package dev.hjih.trapbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

/** Держит трансляцию экрана (MediaProjection) и отдаёт последний кадр по запросу. */
public class CaptureService extends Service {
    static volatile CaptureService inst;

    private MediaProjection proj;
    private VirtualDisplay vd;
    private ImageReader reader;
    private HandlerThread ht;
    private final Object lock = new Object();
    private Image last;
    private volatile int seq;

    @Override public IBinder onBind(Intent i) { return null; }

    @SuppressWarnings("deprecation")
    @Override public int onStartCommand(Intent i, int flags, int startId) {
        if (i == null) { stopSelf(); return START_NOT_STICKY; }
        if (inst != null) return START_NOT_STICKY;

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("cap", "Трансляция экрана", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "cap")
                .setContentTitle("Trap Chess Bot")
                .setContentText("Трансляция экрана активна")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else
            startForeground(1, n);

        int rc = i.getIntExtra("rc", 0);
        Intent data = (Intent) i.getParcelableExtra("data");
        if (data == null) { stopSelf(); return START_NOT_STICKY; }

        MediaProjectionManager mm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        proj = mm.getMediaProjection(rc, data);
        proj.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopSelf(); }
        }, new Handler(Looper.getMainLooper()));

        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
        int w = dm.widthPixels, h = dm.heightPixels;

        ht = new HandlerThread("cap");
        ht.start();
        Handler bh = new Handler(ht.getLooper());
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3);
        reader.setOnImageAvailableListener(r -> {
            Image im = null;
            try { im = r.acquireLatestImage(); } catch (Exception ignored) { }
            if (im == null) return;
            synchronized (lock) {
                if (last != null) last.close();
                last = im;
                seq++;
            }
        }, bh);
        vd = proj.createVirtualDisplay("trapbot", w, h, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, null);
        inst = this;
        return START_NOT_STICKY;
    }

    /** номер последнего полученного кадра (растёт с каждым новым кадром) */
    int seq() { return seq; }

    /** Вырезает прямоугольник из последнего кадра экрана (координаты в пикселях экрана). */
    Recognizer.Frame grab(Rect r) {
        synchronized (lock) {
            if (last == null) return null;
            try {
                Image.Plane p = last.getPlanes()[0];
                ByteBuffer buf = p.getBuffer();
                int ps = p.getPixelStride(), rs = p.getRowStride();
                int w = last.getWidth(), h = last.getHeight();
                int x0 = Math.max(0, r.left), y0 = Math.max(0, r.top);
                int x1 = Math.min(w, r.right), y1 = Math.min(h, r.bottom);
                int cw = x1 - x0, ch = y1 - y0;
                if (cw < 64 || ch < 64) return null;
                int[] px = new int[cw * ch];
                byte[] row = new byte[cw * ps];
                for (int y = 0; y < ch; y++) {
                    buf.position((y0 + y) * rs + x0 * ps);
                    buf.get(row, 0, cw * ps);
                    for (int x = 0; x < cw; x++) {
                        int o = x * ps;
                        px[y * cw + x] = 0xFF000000 | ((row[o] & 255) << 16) | ((row[o + 1] & 255) << 8) | (row[o + 2] & 255);
                    }
                }
                return new Recognizer.Frame(px, cw, ch);
            } catch (Exception e) {
                return null;
            }
        }
    }

    @Override public void onDestroy() {
        inst = null;
        synchronized (lock) {
            if (last != null) { last.close(); last = null; }
        }
        try { if (vd != null) vd.release(); } catch (Exception ignored) { }
        try { if (reader != null) reader.close(); } catch (Exception ignored) { }
        try { if (proj != null) proj.stop(); } catch (Exception ignored) { }
        if (ht != null) ht.quitSafely();
        BotService b = BotService.inst;
        if (b != null) b.stopBot();
        super.onDestroy();
    }
}
