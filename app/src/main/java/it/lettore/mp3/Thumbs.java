package it.lettore.mp3;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Miniature delle copertine per liste e griglie, caricate in background con cache. */
final class Thumbs {
    static final int SMALL = 160, LARGE = 384;

    private static long key(Song s, int size) {
        return s.id * 2 + (size > SMALL ? 1 : 0);
    }

    private static final LruCache<Long, Bitmap> cache = new LruCache<Long, Bitmap>(
            (int) Math.min(Runtime.getRuntime().maxMemory() / 7, 64L * 1024 * 1024)) {
        @Override
        protected int sizeOf(Long k, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static final HashSet<Long> none = new HashSet<Long>();
    private static final ExecutorService exec = Executors.newFixedThreadPool(3);
    private static final Handler main = new Handler(Looper.getMainLooper());

    private Thumbs() {}

    static Bitmap cached(long key) {
        synchronized (cache) {
            return cache.get(key);
        }
    }

    /** Copertina piccola (righe delle liste). */
    static void bind(ImageView iv, Song s, int placeholderIcon, int placeholderPad) {
        bind(iv, s, placeholderIcon, placeholderPad, SMALL);
    }

    /** Mostra la copertina del brano "s" nell'ImageView; nel frattempo un'icona segnaposto. */
    static void bind(final ImageView iv, final Song s, int placeholderIcon, int placeholderPad, final int size) {
        final Long key = key(s, size);
        iv.setTag(key);
        Bitmap b = cached(key);
        if (b != null) {
            show(iv, b, false);
            return;
        }
        placeholder(iv, placeholderIcon, placeholderPad);
        synchronized (none) {
            if (none.contains(key)) return;
        }
        final Context app = iv.getContext().getApplicationContext();
        exec.execute(new Runnable() {
            public void run() {
                if (!key.equals(iv.getTag())) return; // la riga è già stata riciclata
                Bitmap r = cached(key);
                if (r == null) {
                    r = ArtLoader.fetch(app, s, size);
                    if (r == null) {
                        synchronized (none) {
                            none.add(key);
                        }
                        return;
                    }
                    synchronized (cache) {
                        cache.put(key, r);
                    }
                }
                final Bitmap fr = r;
                main.post(new Runnable() {
                    public void run() {
                        if (key.equals(iv.getTag())) show(iv, fr, true);
                    }
                });
            }
        });
    }

    static void unbind(ImageView iv) {
        iv.setTag(null);
    }

    static void forget() {
        synchronized (none) {
            none.clear();
        }
    }

    private static void show(ImageView iv, Bitmap b, boolean fade) {
        iv.animate().cancel();
        iv.clearColorFilter();
        iv.setPadding(0, 0, 0, 0);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(b);
        if (fade) {
            iv.setAlpha(0f);
            iv.animate().alpha(1f).setDuration(200).start();
        } else {
            iv.setAlpha(1f);
        }
    }

    static void placeholder(ImageView iv, int icon, int pad) {
        iv.animate().cancel();
        iv.setAlpha(1f);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageResource(icon);
        iv.setColorFilter(0xB3FFFFFF);
        iv.setPadding(pad, pad, pad, pad);
    }
}
