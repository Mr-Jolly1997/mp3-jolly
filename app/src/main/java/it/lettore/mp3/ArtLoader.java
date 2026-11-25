package it.lettore.mp3;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.util.LruCache;
import android.util.Size;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;

/**
 * Carica la copertina DEL SINGOLO BRANO: miniatura di sistema del file (Android 10+, comprende
 * l'immagine incorporata o cover.jpg/folder.jpg nella cartella) oppure l'immagine incorporata nel file.
 * La cache è per brano: molti file senza tag "album" vengono raggruppati da Android in un album
 * finto (es. "Music") e usare la copertina dell'album darebbe a tutti la stessa immagine sbagliata.
 */
final class ArtLoader {
    private static final LruCache<Long, Bitmap> big = new LruCache<Long, Bitmap>(8);
    private static final HashSet<Long> missing = new HashSet<Long>();

    private ArtLoader() {}

    /** Copertina grande (schermata In riproduzione, notifica). */
    static Bitmap load(Context c, Song s, int maxPx) {
        synchronized (big) {
            Bitmap b = big.get(s.id);
            if (b != null) return b;
            if (missing.contains(s.id)) return null;
        }
        Bitmap b = fetch(c, s, maxPx);
        synchronized (big) {
            if (b != null) big.put(s.id, b);
            else missing.add(s.id);
        }
        return b;
    }

    /** Lettura senza cache. */
    static Bitmap fetch(Context c, Song s, int maxPx) {
        Bitmap b = null;
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                b = c.getContentResolver().loadThumbnail(s.uri(), new Size(maxPx, maxPx), null);
            } catch (Throwable e) {
                b = null;
            }
        }
        if (b == null) {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(c, s.uri());
                byte[] d = r.getEmbeddedPicture();
                if (d != null) b = decode(d, maxPx);
            } catch (Throwable e) {
                b = null;
            } finally {
                try {
                    r.release();
                } catch (Throwable e) {
                    // ignora
                }
            }
        }
        // Solo su Android < 10 e solo per album "veri": la copertina a livello di album.
        if (b == null && Build.VERSION.SDK_INT < 29 && looksLikeRealAlbum(s)) {
            try {
                Uri u = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), s.albumId);
                InputStream in = c.getContentResolver().openInputStream(u);
                if (in != null) {
                    byte[] data = readAll(in);
                    in.close();
                    b = decode(data, maxPx);
                }
            } catch (Throwable e) {
                b = null;
            }
        }
        return b;
    }

    /** Android usa il nome della cartella come album quando il file non ha il tag. */
    static boolean looksLikeRealAlbum(Song s) {
        if (s.album == null || Library.UNKNOWN_ALBUM.equals(s.album)) return false;
        return !(Library.UNKNOWN_ARTIST.equals(s.artist)
                && s.folder != null && s.album.toLowerCase(Locale.ROOT).equals(s.folder.toLowerCase(Locale.ROOT)));
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    private static Bitmap decode(byte[] d, int maxPx) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(d, 0, d.length, o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= maxPx && o.outHeight / (sample * 2) >= maxPx) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(d, 0, d.length, o2);
    }

    /** Colore dominante "scurito" per lo sfondo della schermata In riproduzione. */
    static int moodColor(Bitmap b, int fallback) {
        return moodColor(b, fallback, true);
    }

    /** Tema scuro: colore profondo; tema chiaro: tinta pastello. */
    static int moodColor(Bitmap b, int fallback, boolean dark) {
        if (b == null) return fallback;
        try {
            Bitmap s = Bitmap.createScaledBitmap(b, 12, 12, true);
            float bestScore = -1;
            float[] best = null;
            float sumR = 0, sumG = 0, sumB = 0;
            float[] hsv = new float[3];
            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 12; x++) {
                    int px = s.getPixel(x, y);
                    sumR += (px >> 16) & 0xFF;
                    sumG += (px >> 8) & 0xFF;
                    sumB += px & 0xFF;
                    android.graphics.Color.colorToHSV(px, hsv);
                    float score = hsv[1] * (0.3f + hsv[2]);
                    if (hsv[2] > 0.15f && score > bestScore) {
                        bestScore = score;
                        best = hsv.clone();
                    }
                }
            }
            float[] out = new float[3];
            if (best != null && best[1] > 0.2f) {
                out[0] = best[0];
                out[1] = Math.min(0.75f, Math.max(0.45f, best[1]));
            } else {
                android.graphics.Color.colorToHSV(android.graphics.Color.rgb((int) (sumR / 144), (int) (sumG / 144),
                        (int) (sumB / 144)), out);
                out[1] = Math.min(out[1], 0.25f);
            }
            if (dark) {
                out[2] = 0.36f;
            } else {
                out[1] = Math.min(out[1], 0.28f);
                out[2] = 0.93f;
            }
            return android.graphics.Color.HSVToColor(out);
        } catch (Throwable t) {
            return fallback;
        }
    }
}
