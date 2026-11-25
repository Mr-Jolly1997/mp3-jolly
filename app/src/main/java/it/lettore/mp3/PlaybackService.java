package it.lettore.mp3;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Servizio che riproduce la musica anche a schermo spento. */
public class PlaybackService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    public static final int REPEAT_OFF = 0, REPEAT_ALL = 1, REPEAT_ONE = 2;

    static final String ACT_TOGGLE = "it.lettore.mp3.TOGGLE";
    static final String ACT_NEXT = "it.lettore.mp3.NEXT";
    static final String ACT_PREV = "it.lettore.mp3.PREV";
    static final String ACT_CLOSE = "it.lettore.mp3.CLOSE";
    private static final int NOTIF_ID = 42;
    private static final String CHANNEL = "playback";

    public interface Listener {
        void onStateChanged();
    }

    public final class LocalBinder extends Binder {
        public PlaybackService get() {
            return PlaybackService.this;
        }
    }

    private final IBinder binder = new LocalBinder();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ArrayList<Listener> listeners = new ArrayList<Listener>();
    private final Random rnd = new Random();

    private MediaPlayer mp;
    private boolean prepared, preparing, wantPlay;
    private int pendingSeek;
    private ArrayList<Song> queue = new ArrayList<Song>();
    private ArrayList<Song> original = new ArrayList<Song>();
    private int index = -1;
    private boolean shuffle;
    private int repeat = REPEAT_OFF;

    private MediaSession session;
    private AudioManager am;
    private NotificationManager nm;
    private boolean foreground, noisyRegistered, transientLoss, started, restored;
    private int errorStreak;
    private Bitmap art;
    private long artFor = Long.MIN_VALUE;
    private long sleepAt;
    private AudioFx fx;
    private AudioFocusRequest focusRequest;
    private float speed = 1f;

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) pause();
        }
    };

    private final Runnable sleepRunnable = new Runnable() {
        public void run() {
            sleepAt = 0;
            pause();
            notifyListeners();
        }
    };

    // ---------------------------------------------------------------- ciclo di vita

    @Override
    public void onCreate() {
        super.onCreate();
        Ui.init(this);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Riproduzione musica", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Comandi del brano in ascolto");
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(this, h)
                .setWillPauseWhenDucked(false)
                .build();
        mp = new MediaPlayer();
        mp.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mp.setAudioAttributes(attrs);
        mp.setOnPreparedListener(this);
        mp.setOnCompletionListener(this);
        mp.setOnErrorListener(this);
        fx = new AudioFx(this, mp.getAudioSessionId());

        session = new MediaSession(this, "MP3Jolly");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { play(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { next(); }
            @Override public void onSkipToPrevious() { prev(); }
            @Override public void onStop() { pause(); }
            @Override public void onSeekTo(long pos) { seekTo((int) pos); }
        });
        session.setSessionActivity(PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), piFlags()));

        SharedPreferences p = prefs();
        shuffle = p.getBoolean("shuffle", false);
        repeat = p.getInt("repeat", REPEAT_OFF);
        speed = clampSpeed(p.getFloat("speed", 1f));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent != null ? intent.getAction() : null;
        if (ACT_TOGGLE.equals(a)) toggle();
        else if (ACT_NEXT.equals(a)) next();
        else if (ACT_PREV.equals(a)) prev();
        else if (ACT_CLOSE.equals(a)) closeAll();
        if (current() == null) {
            started = false;
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (!isPlaying()) closeAll();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        saveState();
        h.removeCallbacksAndMessages(null);
        unregisterNoisy();
        am.abandonAudioFocusRequest(focusRequest);
        stopForeground(STOP_FOREGROUND_REMOVE);
        nm.cancel(NOTIF_ID);
        session.setActive(false);
        session.release();
        fx.release();
        mp.release();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- API per l'interfaccia

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public AudioFx fx() {
        return fx;
    }

    public Song current() {
        return index >= 0 && index < queue.size() ? queue.get(index) : null;
    }

    public ArrayList<Song> queue() {
        return new ArrayList<Song>(queue);
    }

    public int index() {
        return index;
    }

    public Bitmap art() {
        Song s = current();
        return s != null && artFor == s.id ? art : null;
    }

    public boolean isPlaying() {
        try {
            return (prepared && mp.isPlaying()) || (preparing && wantPlay);
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public int position() {
        try {
            return prepared ? mp.getCurrentPosition() : pendingSeek;
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public int duration() {
        try {
            if (prepared) return mp.getDuration();
        } catch (IllegalStateException e) {
            // ignora
        }
        Song s = current();
        return s != null ? (int) s.duration : 0;
    }

    // ---------------------------------------------------------------- velocità

    public float speed() {
        return speed;
    }

    private static float clampSpeed(float v) {
        if (Float.isNaN(v)) return 1f;
        return Math.max(0.5f, Math.min(2f, v));
    }

    /** Velocità di riproduzione (0,5×–2×). Il tono della voce resta invariato. */
    public void setSpeed(float v) {
        speed = clampSpeed(Math.round(v * 20f) / 20f);
        prefs().edit().putFloat("speed", speed).apply();
        // su un lettore in pausa setPlaybackParams farebbe ripartire la musica: si applica al prossimo play
        if (prepared && isPlaying()) applySpeed();
        updateSession();
        notifyListeners();
    }

    private void applySpeed() {
        try {
            PlaybackParams pp = new PlaybackParams();
            pp.setSpeed(speed);
            pp.setPitch(1f);
            mp.setPlaybackParams(pp);
        } catch (Exception e) {
            // alcuni formati/dispositivi non lo supportano: si resta a velocità normale
        }
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public int repeatMode() {
        return repeat;
    }

    public void playQueue(List<Song> list, int start, boolean shuffleNow) {
        if (list == null || list.isEmpty()) return;
        original = new ArrayList<Song>(list);
        if (shuffleNow) shuffle = true;
        if (start < 0 || start >= list.size()) start = shuffle ? rnd.nextInt(list.size()) : 0;
        if (shuffle) {
            queue = shuffled(original, original.get(start));
            index = 0;
        } else {
            queue = new ArrayList<Song>(original);
            index = start;
        }
        errorStreak = 0;
        loadCurrent(true, 0);
    }

    public void playIndex(int i) {
        if (i < 0 || i >= queue.size()) return;
        index = i;
        errorStreak = 0;
        loadCurrent(true, 0);
    }

    public void playNext(List<Song> songs) {
        if (songs.isEmpty()) return;
        if (current() == null) {
            playQueue(songs, 0, false);
            return;
        }
        queue.addAll(index + 1, songs);
        if (shuffle) {
            int oi = indexOfId(original, current());
            original.addAll(oi < 0 ? original.size() : oi + 1, songs);
        } else {
            original = new ArrayList<Song>(queue);
        }
        saveState();
        notifyListeners();
    }

    public void enqueue(List<Song> songs) {
        if (songs.isEmpty()) return;
        if (current() == null) {
            playQueue(songs, 0, false);
            return;
        }
        queue.addAll(songs);
        original.addAll(songs);
        saveState();
        notifyListeners();
    }

    /** Svuota la coda: resta solo il brano attuale, che finisce normalmente. */
    public void clearUpcoming() {
        Song cur = current();
        if (cur == null) return;
        queue = new ArrayList<Song>();
        queue.add(cur);
        original = new ArrayList<Song>(queue);
        index = 0;
        saveState();
        notifyListeners();
    }

    /** Toglie un brano dalla coda (non quello in riproduzione). */
    public boolean removeFromQueue(int i) {
        if (i < 0 || i >= queue.size() || i == index) return false;
        Song s = queue.remove(i);
        if (i < index) index--;
        int oi = indexOfId(original, s);
        if (oi >= 0) original.remove(oi);
        saveState();
        notifyListeners();
        return true;
    }

    public void toggle() {
        if (isPlaying()) pause();
        else play();
    }

    public void play() {
        if (current() == null) return;
        if (prepared) startPlayback();
        else if (!preparing) loadCurrent(true, pendingSeek);
        else wantPlay = true;
        notifyListeners();
    }

    public void pause() {
        transientLoss = false;
        pauseInternal();
        am.abandonAudioFocusRequest(focusRequest);
    }

    public void next() {
        if (queue.isEmpty()) return;
        boolean play = isPlaying();
        if (index < queue.size() - 1) {
            index++;
        } else {
            index = 0;
            if (repeat != REPEAT_ALL) play = false;
        }
        errorStreak = 0;
        loadCurrent(play, 0);
    }

    public void prev() {
        if (queue.isEmpty()) return;
        if (position() > 3000) {
            seekTo(0);
            return;
        }
        boolean play = isPlaying();
        if (index > 0) index--;
        else if (repeat == REPEAT_ALL) index = queue.size() - 1;
        else index = 0;
        errorStreak = 0;
        loadCurrent(play, 0);
    }

    public void seekTo(int ms) {
        try {
            if (prepared) mp.seekTo(ms);
            else pendingSeek = ms;
        } catch (IllegalStateException e) {
            // ignora
        }
        updateSession();
        notifyListeners();
    }

    public void toggleShuffle() {
        shuffle = !shuffle;
        Song cur = current();
        if (cur != null) {
            if (shuffle) {
                original = new ArrayList<Song>(queue);
                queue = shuffled(original, cur);
                index = 0;
            } else {
                queue = new ArrayList<Song>(original);
                index = indexOfId(queue, cur);
                if (index < 0) {
                    queue.add(0, cur);
                    index = 0;
                }
            }
        }
        saveState();
        notifyListeners();
    }

    public void cycleRepeat() {
        repeat = (repeat + 1) % 3;
        saveState();
        notifyListeners();
    }

    public void setSleepTimer(int minutes) {
        h.removeCallbacks(sleepRunnable);
        if (minutes <= 0) {
            sleepAt = 0;
        } else {
            sleepAt = SystemClock.elapsedRealtime() + minutes * 60000L;
            h.postDelayed(sleepRunnable, minutes * 60000L);
        }
        notifyListeners();
    }

    public long sleepRemaining() {
        return sleepAt == 0 ? 0 : Math.max(0, sleepAt - SystemClock.elapsedRealtime());
    }

    /** Ripristina l'ultima coda ascoltata (in pausa), una sola volta. */
    public void restore(Library lib) {
        if (restored || index >= 0 || lib == null) return;
        restored = true;
        if (!Settings.resume(this)) return;
        SharedPreferences p = prefs();
        ArrayList<Song> q = parseIds(p.getString("queue", ""), lib);
        if (q.isEmpty()) return;
        ArrayList<Song> o = parseIds(p.getString("orig", ""), lib);
        queue = q;
        original = o.isEmpty() ? new ArrayList<Song>(q) : o;
        long cur = p.getLong("cur", -1);
        int pos = p.getInt("pos", 0);
        index = -1;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).id == cur) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            index = 0;
            pos = 0;
        }
        loadCurrent(false, pos);
    }

    public void saveState() {
        StringBuilder q = new StringBuilder(), o = new StringBuilder();
        for (Song s : queue) q.append(s.id).append(',');
        for (Song s : original) o.append(s.id).append(',');
        Song cur = current();
        SharedPreferences.Editor e = prefs().edit();
        e.putString("queue", q.toString());
        e.putString("orig", o.toString());
        e.putLong("cur", cur != null ? cur.id : -1);
        e.putInt("pos", position());
        e.putBoolean("shuffle", shuffle);
        e.putInt("repeat", repeat);
        e.apply();
    }

    // ---------------------------------------------------------------- interno

    private SharedPreferences prefs() {
        return getSharedPreferences("player", MODE_PRIVATE);
    }

    private static ArrayList<Song> parseIds(String s, Library lib) {
        ArrayList<Song> out = new ArrayList<Song>();
        if (s == null || s.length() == 0) return out;
        for (String part : s.split(",")) {
            if (part.length() == 0) continue;
            try {
                Song song = lib.byId.get(Long.parseLong(part));
                if (song != null) out.add(song);
            } catch (NumberFormatException e) {
                // ignora
            }
        }
        return out;
    }

    private ArrayList<Song> shuffled(List<Song> src, Song first) {
        ArrayList<Song> q = new ArrayList<Song>(src);
        int i = indexOfId(q, first);
        if (i >= 0) q.remove(i);
        Collections.shuffle(q, rnd);
        q.add(0, first);
        return q;
    }

    private static int indexOfId(List<Song> l, Song s) {
        if (s == null) return -1;
        for (int i = 0; i < l.size(); i++) if (l.get(i).id == s.id) return i;
        return -1;
    }

    private void ensureStarted() {
        if (!started) {
            started = true;
            try {
                startService(new Intent(this, PlaybackService.class));
            } catch (RuntimeException e) {
                // avvio non consentito in background: si resta legati all'interfaccia
                started = false;
            }
        }
    }

    private void loadCurrent(boolean play, int seek) {
        Song s = current();
        if (s == null) return;
        prepared = false;
        preparing = true;
        wantPlay = play;
        pendingSeek = seek;
        if (play) ensureStarted();
        try {
            mp.reset();
            mp.setDataSource(getApplicationContext(), s.uri());
            mp.prepareAsync();
        } catch (Exception e) {
            preparing = false;
            handleError();
        }
        loadArt(s);
        updateMeta();
        updateSession();
        updateNotification();
        notifyListeners();
        saveState();
    }

    @Override
    public void onPrepared(MediaPlayer p) {
        prepared = true;
        preparing = false;
        if (pendingSeek > 0) p.seekTo(pendingSeek);
        pendingSeek = 0;
        if (wantPlay) {
            startPlayback();
        } else {
            updateSession();
            notifyListeners();
        }
    }

    private void startPlayback() {
        int r = am.requestAudioFocus(focusRequest);
        if (r != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            wantPlay = false;
            notifyListeners();
            return;
        }
        try {
            mp.setVolume(1f, 1f);
            mp.start();
            if (speed != 1f) applySpeed();
        } catch (IllegalStateException e) {
            return;
        }
        errorStreak = 0;
        wantPlay = true;
        ensureStarted();
        registerNoisy();
        session.setActive(true);
        updateSession();
        updateNotification();
        notifyListeners();
    }

    private void pauseInternal() {
        wantPlay = false;
        try {
            if (prepared && mp.isPlaying()) mp.pause();
        } catch (IllegalStateException e) {
            // ignora
        }
        unregisterNoisy();
        updateSession();
        updateNotification();
        notifyListeners();
        saveState();
    }

    @Override
    public void onCompletion(MediaPlayer p) {
        if (!prepared) return;
        if (repeat == REPEAT_ONE) {
            p.seekTo(0);
            p.start();
            return;
        }
        if (index < queue.size() - 1) {
            index++;
            loadCurrent(true, 0);
        } else if (repeat == REPEAT_ALL) {
            index = 0;
            loadCurrent(true, 0);
        } else {
            // fine della coda: si ferma sull'ultimo brano
            try {
                p.seekTo(0);
            } catch (IllegalStateException e) {
                // ignora
            }
            pause();
        }
    }

    @Override
    public boolean onError(MediaPlayer p, int what, int extra) {
        prepared = false;
        preparing = false;
        handleError();
        return true;
    }

    private void handleError() {
        errorStreak++;
        Song s = current();
        if (s != null && wantPlay) {
            Toast.makeText(this, "Impossibile riprodurre: " + s.title, Toast.LENGTH_SHORT).show();
        }
        if (errorStreak >= queue.size() || errorStreak > 10) {
            wantPlay = false;
            updateSession();
            updateNotification();
            notifyListeners();
            return;
        }
        final boolean play = wantPlay;
        h.postDelayed(new Runnable() {
            public void run() {
                if (queue.isEmpty()) return;
                index = (index + 1) % queue.size();
                loadCurrent(play, 0);
            }
        }, 300);
    }

    @Override
    public void onAudioFocusChange(int f) {
        switch (f) {
            case AudioManager.AUDIOFOCUS_LOSS:
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (isPlaying()) {
                    transientLoss = true;
                    pauseInternal();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                try {
                    if (prepared) mp.setVolume(0.25f, 0.25f);
                } catch (IllegalStateException e) {
                    // ignora
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                try {
                    if (prepared) mp.setVolume(1f, 1f);
                } catch (IllegalStateException e) {
                    // ignora
                }
                if (transientLoss) {
                    transientLoss = false;
                    play();
                }
                break;
        }
    }

    private void registerNoisy() {
        if (!noisyRegistered) {
            IntentFilter f = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(noisy, f);
            noisyRegistered = true;
        }
    }

    private void unregisterNoisy() {
        if (noisyRegistered) {
            try {
                unregisterReceiver(noisy);
            } catch (Exception e) {
                // ignora
            }
            noisyRegistered = false;
        }
    }

    private void closeAll() {
        pause();
        saveState();
        stopForeground(STOP_FOREGROUND_REMOVE);
        foreground = false;
        nm.cancel(NOTIF_ID);
        started = false;
        session.setActive(false);
        stopSelf();
        notifyListeners();
    }

    private void loadArt(final Song s) {
        if (artFor == s.id) return;
        artFor = s.id;
        art = null;
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final Bitmap b = ArtLoader.load(app, s, 640);
                h.post(new Runnable() {
                    public void run() {
                        if (artFor == s.id) {
                            art = b;
                            updateMeta();
                            updateNotification();
                            notifyListeners();
                        }
                    }
                });
            }
        }).start();
    }

    private void updateMeta() {
        Song s = current();
        if (s == null) return;
        MediaMetadata.Builder b = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, s.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, s.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, s.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, s.duration);
        if (art != null && artFor == s.id) b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        session.setMetadata(b.build());
    }

    private void updateSession() {
        Song s = current();
        int state = s == null ? PlaybackState.STATE_NONE
                : (isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO
                        | PlaybackState.ACTION_STOP)
                .setState(state, position(), state == PlaybackState.STATE_PLAYING ? speed : 0f)
                .build());
    }

    static int piFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }

    private PendingIntent action(String a, int req) {
        Intent i = new Intent(this, PlaybackService.class).setAction(a);
        return PendingIntent.getService(this, req, i, piFlags());
    }

    @SuppressWarnings("deprecation")
    private void updateNotification() {
        Song s = current();
        if (!started || s == null) return;
        boolean playing = isPlaying();
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle(s.title)
                .setContentText(s.artist)
                .setSubText(s.album)
                .setColor(Ui.ACCENT)
                .setShowWhen(false)
                .setOngoing(playing)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), piFlags()))
                .setDeleteIntent(action(ACT_CLOSE, 4))
                .addAction(R.drawable.ic_prev, "Precedente", action(ACT_PREV, 1))
                .addAction(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                        playing ? "Pausa" : "Play", action(ACT_TOGGLE, 2))
                .addAction(R.drawable.ic_next, "Successivo", action(ACT_NEXT, 3))
                .addAction(R.drawable.ic_close, "Chiudi", action(ACT_CLOSE, 4))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        Bitmap a = art();
        if (a != null) b.setLargeIcon(a);
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        Notification n = b.build();
        // durante una pausa breve (es. telefonata) il servizio resta in primo piano:
        // Android 12+ non permette di tornarci da solo stando in background
        if (playing || transientLoss) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    startForeground(NOTIF_ID, n);
                }
                foreground = true;
            } catch (RuntimeException e) {
                nm.notify(NOTIF_ID, n);
            }
        } else {
            if (foreground) {
                stopForeground(STOP_FOREGROUND_DETACH);
                foreground = false;
            }
            nm.notify(NOTIF_ID, n);
        }
    }

    private void notifyListeners() {
        for (Listener l : new ArrayList<Listener>(listeners)) l.onStateChanged();
    }
}
