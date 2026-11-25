package it.lettore.mp3;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Schermata "In riproduzione".
 * Tutto è allineato agli stessi margini laterali (24dp): le icone hanno un'area di tocco più grande del
 * disegno, quindi le file di icone usano margini negativi per allineare il DISEGNO al bordo del contenuto.
 */
public class NowPlayingActivity extends Activity implements PlaybackService.Listener {

    private static final int MARGIN = 24;      // margine laterale del contenuto
    private static final int ICON_INSET = 12;  // spazio tra bordo del pulsante e disegno dell'icona (48dp, padding 12)

    private PlaybackService svc;
    private final Handler h = new Handler();
    private int myGen;
    private GradientDrawable bg;
    private int mood;
    private ValueAnimator moodAnim;
    private Views.SquareImageView art;
    private TextView title, sub, elapsed, total, sleepLabel, timerLabel;
    private ImageView heart, timerIcon, speedIcon;
    private TextView speedLabel;
    private SeekBar seek;
    private ImageView shuffleB, playB, repeatB;
    private boolean dragging;
    private Bitmap shownArt;
    private boolean placeholderShown;
    private long shownSongId = Long.MIN_VALUE;

    private final Runnable tick = new Runnable() {
        public void run() {
            updateProgress();
            h.postDelayed(this, 500);
        }
    };

    private final ServiceConnection conn = new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder b) {
            svc = ((PlaybackService.LocalBinder) b).get();
            svc.addListener(NowPlayingActivity.this);
            onStateChanged();
        }

        public void onServiceDisconnected(ComponentName n) {
            svc = null;
        }
    };

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private int baseMood() {
        return Ui.coverBg ? (Ui.dark ? Ui.blend(Ui.ACCENT, Ui.BG, 0.72f) : Ui.blend(Ui.ACCENT, Ui.BG, 0.85f)) : Ui.BG;
    }

    @Override
    protected void onCreate(Bundle b) {
        myGen = Ui.applyTheme(this);
        super.onCreate(b);
        Compat.transitions(this, R.anim.slide_up, R.anim.stay, R.anim.stay, R.anim.slide_down);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        Playlists.load(this);
        mood = baseMood();

        FrameLayout frame = new FrameLayout(this);
        bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{mood, Ui.blend(mood, Ui.BG, 0.7f), Ui.BG});
        frame.setBackground(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(MARGIN), dp(4), dp(MARGIN), dp(12));
        root.setClipToPadding(false);
        root.setClipChildren(false);
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));

        // ---- barra superiore: chiudi | titolo centrato | coda
        FrameLayout top = new FrameLayout(this);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, dp(56));
        tlp.leftMargin = -dp(ICON_INSET);
        tlp.rightMargin = -dp(ICON_INSET);
        root.addView(top, tlp);
        ImageView down = Ui.icon(this, R.drawable.ic_down, Ui.ICON, 48, 12);
        down.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(down, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START | Gravity.CENTER_VERTICAL));
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        tcol.setGravity(Gravity.CENTER);
        TextView hdr = Ui.text(this, "IN RIPRODUZIONE", 12, Ui.NP_ARTIST, Ui.MEDIUM);
        hdr.setLetterSpacing(0.14f);
        tcol.addView(hdr);
        sleepLabel = Ui.text(this, "", 12, Ui.ACCENT, Ui.MEDIUM);
        sleepLabel.setVisibility(View.GONE);
        tcol.addView(sleepLabel);
        top.addView(tcol, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        ImageView queue = Ui.icon(this, R.drawable.ic_queue, Ui.ICON, 48, 12);
        queue.setContentDescription("Coda di riproduzione");
        queue.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                QueueSheet.show(NowPlayingActivity.this, svc);
            }
        });
        final ImageView moreTop = Ui.icon(this, R.drawable.ic_more, Ui.ICON, 48, 12);
        moreTop.setContentDescription("Altre azioni");
        moreTop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                moreMenu(v);
            }
        });
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.addView(queue);
        right.addView(moreTop);
        top.addView(right, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));

        // ---- copertina (scorri a sinistra/destra per cambiare brano, tocca per pausa)
        FrameLayout artBox = new FrameLayout(this);
        artBox.setPadding(0, dp(16), 0, dp(20));
        artBox.setClipToPadding(false);
        art = new Views.SquareImageView(this, false);
        art.setElevation(dp(12));
        art.setClipToOutline(true);
        artBox.addView(art, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        root.addView(artBox, new LinearLayout.LayoutParams(-1, 0, 1f));
        Swipe.attach(artBox, art, new Swipe.Callback() {
            public void onTap() {
                if (svc != null) svc.toggle();
            }

            public void onSwipe(boolean toNext) {
                if (svc == null) return;
                if (toNext) svc.next();
                else svc.prev();
            }
        });

        // ---- titolo + cuore (il disegno del cuore è allineato al margine destro)
        LinearLayout trow = new LinearLayout(this);
        trow.setOrientation(LinearLayout.HORIZONTAL);
        trow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(-1, -2);
        trp.rightMargin = -dp(ICON_INSET);
        root.addView(trow, trp);
        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        trow.addView(tc, Ui.lpw(1f));
        title = Ui.text(this, "", 24, Ui.NP_TITLE, Ui.BOLD);
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setHorizontalFadingEdgeEnabled(true);
        title.setSelected(true);
        tc.addView(title);
        sub = Ui.text(this, "", 16, Ui.NP_ARTIST, Ui.MEDIUM);
        sub.setPadding(0, dp(3), 0, 0);
        sub.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                gotoGroup(Group.ARTIST);
            }
        });
        tc.addView(sub);
        heart = Ui.icon(this, R.drawable.ic_heart_outline, Ui.NP_TITLE, 48, 12);
        heart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleFavorite();
            }
        });
        trow.addView(heart);

        // ---- avanzamento: la linea è allineata ai margini, il pallino sporge di metà
        seek = new SeekBar(this);
        Ui.tint(seek, Ui.NP_PROGRESS);
        int thumbR = dp(10);
        seek.setPadding(thumbR, dp(16), thumbR, dp(8));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.leftMargin = -thumbR;
        slp.rightMargin = -thumbR;
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) elapsed.setText(Ui.time(p));
            }

            public void onStartTrackingTouch(SeekBar s) {
                dragging = true;
            }

            public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                if (svc != null) svc.seekTo(s.getProgress());
            }
        });
        root.addView(seek, slp);
        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        elapsed = Ui.text(this, "0:00", 12.5f, Ui.NP_ARTIST, Ui.MEDIUM);
        times.addView(elapsed, Ui.lpw(1f));
        total = Ui.text(this, "0:00", 12.5f, Ui.NP_ARTIST, Ui.MEDIUM);
        times.addView(total);
        root.addView(times);

        // ---- comandi: dimensioni fisse + spazi uguali, niente più pulsante play tagliato
        LinearLayout ctr = new LinearLayout(this);
        ctr.setOrientation(LinearLayout.HORIZONTAL);
        ctr.setGravity(Gravity.CENTER_VERTICAL);
        ctr.setPadding(0, dp(14), 0, dp(14));
        ctr.setClipChildren(false);
        ctr.setClipToPadding(false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.leftMargin = -dp(ICON_INSET);
        clp.rightMargin = -dp(ICON_INSET);
        shuffleB = Ui.icon(this, R.drawable.ic_shuffle, Ui.NP_TOOLS, 48, 12);
        shuffleB.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (svc == null) return;
                svc.toggleShuffle();
                Toast.makeText(NowPlayingActivity.this, svc.isShuffle() ? "Ordine casuale attivo" : "Ordine casuale disattivato",
                        Toast.LENGTH_SHORT).show();
            }
        });
        ImageView prevB = Ui.icon(this, R.drawable.ic_prev, Ui.NP_SKIP, 56, 10);
        prevB.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (svc != null) svc.prev();
            }
        });
        playB = Ui.icon(this, R.drawable.ic_play, Ui.NP_PLAY_ICON, 72, 18);
        playB.setBackground(Ui.oval(Ui.NP_PLAY_BG));
        playB.setElevation(dp(6));
        Ui.pressable(playB);
        playB.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (svc != null) svc.toggle();
            }
        });
        ImageView nextB = Ui.icon(this, R.drawable.ic_next, Ui.NP_SKIP, 56, 10);
        nextB.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (svc != null) svc.next();
            }
        });
        repeatB = Ui.icon(this, R.drawable.ic_repeat, Ui.NP_TOOLS, 48, 12);
        repeatB.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (svc == null) return;
                svc.cycleRepeat();
                int r = svc.repeatMode();
                Toast.makeText(NowPlayingActivity.this,
                        r == PlaybackService.REPEAT_ALL ? "Ripeti tutta la coda"
                                : r == PlaybackService.REPEAT_ONE ? "Ripeti questo brano" : "Ripetizione disattivata",
                        Toast.LENGTH_SHORT).show();
            }
        });
        View[] btns = {shuffleB, prevB, playB, nextB, repeatB};
        for (int i = 0; i < btns.length; i++) {
            if (i > 0) ctr.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            ctr.addView(btns[i], btns[i].getLayoutParams());
        }
        root.addView(ctr, clp);

        // ---- strumenti: tre colonne uguali, icona e scritta centrate
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tolp = new LinearLayout.LayoutParams(-1, -2);
        tolp.leftMargin = -dp(ICON_INSET);
        tolp.rightMargin = -dp(ICON_INSET);
        tools.setLayoutParams(tolp);
        View timer = tool(R.drawable.ic_timer, "Timer");
        timerIcon = (ImageView) ((LinearLayout) timer).getChildAt(0);
        timerLabel = (TextView) ((LinearLayout) timer).getChildAt(1);
        timer.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sleepSheet();
            }
        });
        View spd = tool(R.drawable.ic_speed, "Velocità");
        speedIcon = (ImageView) ((LinearLayout) spd).getChildAt(0);
        speedLabel = (TextView) ((LinearLayout) spd).getChildAt(1);
        spd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                speedSheet();
            }
        });
        View eq = tool(R.drawable.ic_equalizer, "Equalizzatore");
        eq.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(NowPlayingActivity.this, EqualizerActivity.class));
                Compat.legacyAnim(NowPlayingActivity.this, R.anim.slide_in_right, R.anim.fade_out_short);
            }
        });
        View share = tool(R.drawable.ic_share, "Condividi");
        share.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Song s = svc != null ? svc.current() : null;
                if (s != null) QueueSheet.share(NowPlayingActivity.this, s);
            }
        });
        tools.addView(timer, Ui.lpw(1f));
        tools.addView(spd, Ui.lpw(1f));
        tools.addView(eq, Ui.lpw(1f));
        tools.addView(share, Ui.lpw(1f));
        root.addView(tools, tools.getLayoutParams());

        setContentView(frame);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.BG));
        Compat.edgeToEdge(this, frame);
        applyBars();
    }

    private View tool(int icon, String label) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(0, dp(8), 0, dp(8));
        t.setBackground(Ui.ripple(this, true));
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Ui.NP_TOOLS);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(24), dp(24));
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        t.addView(iv, ip);
        TextView tv = Ui.text(this, label, 12, Ui.NP_TOOLS, Ui.MEDIUM);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(2), dp(6), dp(2), 0);
        tv.setEllipsize(null);
        // la scritta si rimpicciolisce da sola se non ci sta (es. "Equalizzatore" su schermi stretti)
        tv.setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(22));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        t.addView(tv, lp);
        t.setClickable(true);
        return t;
    }

    private void applyBars() {
        Compat.systemBars(this, Ui.isLight(mood), Ui.isLight(Ui.BG), mood);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, PlaybackService.class), conn, BIND_AUTO_CREATE);
        h.post(tick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (myGen != Ui.themeGen) recreate();
    }

    @Override
    protected void onStop() {
        h.removeCallbacks(tick);
        if (svc != null) svc.removeListener(this);
        unbindService(conn);
        svc = null;
        super.onStop();
    }

    @Override
    public void finish() {
        super.finish();
        Compat.legacyAnim(this, R.anim.stay, R.anim.slide_down);
    }

    // ---------------------------------------------------------------- aggiornamento

    @Override
    public void onStateChanged() {
        Song s = svc != null ? svc.current() : null;
        if (s == null) {
            title.setText("Nessun brano");
            sub.setText("Scegli un brano dalla libreria");
            showArt(null);
            playB.setImageResource(R.drawable.ic_play);
            return;
        }
        if (!s.title.contentEquals(title.getText())) title.setText(s.title);
        sub.setText(s.artist);
        playB.setImageResource(svc.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        boolean sh = svc.isShuffle();
        shuffleB.setColorFilter(sh ? Ui.NP_TOGGLE : Ui.NP_TOOLS);
        shuffleB.setBackground(sh ? Ui.oval(Ui.alpha(Ui.NP_TOGGLE, 0x30)) : Ui.ripple(this, true));
        int r = svc.repeatMode();
        repeatB.setImageResource(r == PlaybackService.REPEAT_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        repeatB.setColorFilter(r != PlaybackService.REPEAT_OFF ? Ui.NP_TOGGLE : Ui.NP_TOOLS);
        repeatB.setBackground(r != PlaybackService.REPEAT_OFF ? Ui.oval(Ui.alpha(Ui.NP_TOGGLE, 0x30)) : Ui.ripple(this, true));
        if (s.id != shownSongId) {
            shownSongId = s.id;
            updateHeart(false);
            if (svc.art() == null) art.setBackground(Ui.rounded(Ui.tileColor(s.album), dp(20)));
        }
        showArt(svc.art());
        updateProgress();
    }

    private void showArt(Bitmap b) {
        if (b != null) {
            if (b == shownArt) return;
            shownArt = b;
            placeholderShown = false;
            art.clearColorFilter();
            art.setPadding(0, 0, 0, 0);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setBackground(Ui.rounded(Ui.SURFACE2, dp(20)));
            art.setImageBitmap(b);
            animateMood(Ui.coverBg ? ArtLoader.moodColor(b, baseMood(), Ui.dark) : Ui.BG);
        } else {
            shownArt = null;
            Song s = svc != null ? svc.current() : null;
            art.setBackground(Ui.rounded(s != null ? Ui.tileColor(s.album) : Ui.SURFACE2, dp(20)));
            if (placeholderShown) return;
            placeholderShown = true;
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageResource(R.drawable.ic_note);
            art.setColorFilter(0x66FFFFFF);
            art.post(new Runnable() {
                public void run() {
                    int p = art.getWidth() / 4;
                    art.setPadding(p, p, p, p);
                }
            });
            animateMood(baseMood());
        }
    }

    private void animateMood(int to) {
        if (to == mood) return;
        if (moodAnim != null) moodAnim.cancel();
        moodAnim = ValueAnimator.ofArgb(mood, to);
        moodAnim.setDuration(500);
        moodAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                mood = (Integer) a.getAnimatedValue();
                bg.setColors(new int[]{mood, Ui.blend(mood, Ui.BG, 0.7f), Ui.BG});
                applyBars();
            }
        });
        moodAnim.start();
    }

    private void updateProgress() {
        if (svc == null) return;
        long rem = svc.sleepRemaining();
        if (rem > 0) {
            sleepLabel.setVisibility(View.VISIBLE);
            sleepLabel.setText("Si ferma tra " + Ui.time(rem));
            timerLabel.setText(Ui.time(rem));
            timerLabel.setTextColor(Ui.ACCENT);
            timerIcon.setColorFilter(Ui.ACCENT);
        } else {
            sleepLabel.setVisibility(View.GONE);
            timerLabel.setText("Timer");
            timerLabel.setTextColor(Ui.NP_TOOLS);
            timerIcon.setColorFilter(Ui.NP_TOOLS);
        }
        float sp = svc.speed();
        boolean custom = Math.abs(sp - 1f) > 0.001f;
        speedLabel.setText(custom ? Ui.speed(sp) : "Velocità");
        speedLabel.setTextColor(custom ? Ui.ACCENT : Ui.NP_TOOLS);
        speedIcon.setColorFilter(custom ? Ui.ACCENT : Ui.NP_TOOLS);
        if (svc.current() == null) return;
        int d = svc.duration();
        int p = svc.position();
        seek.setMax(Math.max(d, 1));
        if (!dragging) {
            seek.setProgress(p);
            elapsed.setText(Ui.time(p));
        }
        total.setText(Ui.time(d));
    }

    private void updateHeart(boolean bounce) {
        Song s = svc != null ? svc.current() : null;
        boolean fav = Playlists.isFavorite(s);
        heart.setImageResource(fav ? R.drawable.ic_heart : R.drawable.ic_heart_outline);
        heart.setColorFilter(fav ? Ui.NP_HEART : Ui.NP_TITLE);
        if (bounce) {
            heart.setScaleX(1.35f);
            heart.setScaleY(1.35f);
            heart.animate().scaleX(1f).scaleY(1f).setDuration(260).start();
        }
    }

    private void toggleFavorite() {
        Song s = svc != null ? svc.current() : null;
        if (s == null) return;
        boolean now = Playlists.toggleFavorite(this, s);
        updateHeart(true);
        Toast.makeText(this, now ? "Aggiunto ai preferiti" : "Rimosso dai preferiti", Toast.LENGTH_SHORT).show();
    }

    private void gotoGroup(int type) {
        Song s = svc != null ? svc.current() : null;
        if (s == null) return;
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_GOTO_TYPE, type);
        i.putExtra(MainActivity.EXTRA_GOTO_KEY, type == Group.ALBUM ? String.valueOf(s.albumId)
                : type == Group.ARTIST ? s.artist.toLowerCase(java.util.Locale.ROOT) : s.folderPath);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    // ---------------------------------------------------------------- menu

    private void moreMenu(final View anchor) {
        final Song s = svc != null ? svc.current() : null;
        if (s == null) return;
        PopMenu m = new PopMenu(this);
        m.add(R.drawable.ic_playlist, "Aggiungi a playlist", new Runnable() {
            public void run() {
                addToPlaylist(s, anchor);
            }
        });
        m.add(R.drawable.ic_album, "Vai all'album", new Runnable() {
            public void run() {
                gotoGroup(Group.ALBUM);
            }
        });
        m.add(R.drawable.ic_person, "Vai all'artista", new Runnable() {
            public void run() {
                gotoGroup(Group.ARTIST);
            }
        });
        m.add(R.drawable.ic_folder, "Apri la cartella", new Runnable() {
            public void run() {
                gotoGroup(Group.FOLDER);
            }
        });
        m.add(R.drawable.ic_info, "Dettagli", new Runnable() {
            public void run() {
                MainActivity.details(NowPlayingActivity.this, s);
            }
        });
        m.show(anchor);
    }

    private void addToPlaylist(final Song s, View anchor) {
        PopMenu m = new PopMenu(this, "Aggiungi a playlist");
        for (final Playlists.Playlist p : new ArrayList<Playlists.Playlist>(Playlists.all)) {
            m.add(p.fav ? R.drawable.ic_heart : R.drawable.ic_playlist, p.name, p.fav ? Ui.NP_HEART : Ui.ICON, new Runnable() {
                public void run() {
                    int n = Playlists.addAll(NowPlayingActivity.this, p, Collections.singletonList(s));
                    Toast.makeText(NowPlayingActivity.this, n == 0 ? "Già presente in «" + p.name + "»"
                            : "Aggiunto a «" + p.name + "»", Toast.LENGTH_SHORT).show();
                    updateHeart(false);
                }
            });
        }
        m.show(anchor);
    }

    /** Velocità: cursore 0,5×–2× a passi di 0,05 + scorciatoie. */
    private void speedSheet() {
        if (svc == null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(2), dp(22), dp(18));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout hc = new LinearLayout(this);
        hc.setOrientation(LinearLayout.VERTICAL);
        hc.addView(Ui.text(this, "Velocità di riproduzione", 18, Ui.TEXT, Ui.BOLD));
        hc.addView(Ui.text(this, "Il tono della voce resta uguale", 13, Ui.TEXT2, Ui.REGULAR));
        head.addView(hc, Ui.lpw(1f));
        final TextView value = Ui.text(this, Ui.speed(svc.speed()), 28, Ui.ACCENT, Ui.BOLD);
        head.addView(value);
        box.addView(head);

        final SeekBar sb = new SeekBar(this);
        sb.setMax(30); // 0,50 + i·0,05
        sb.setProgress(Math.round((svc.speed() - 0.5f) / 0.05f));
        Ui.tint(sb, Ui.ACCENT);
        sb.setPadding(dp(10), dp(22), dp(10), dp(6));
        LinearLayout.LayoutParams sbl = new LinearLayout.LayoutParams(-1, -2);
        sbl.leftMargin = -dp(10);
        sbl.rightMargin = -dp(10);
        box.addView(sb, sbl);
        LinearLayout ends = new LinearLayout(this);
        ends.addView(Ui.text(this, "Più lento", 12.5f, Ui.TEXT2, Ui.REGULAR), Ui.lpw(1f));
        ends.addView(Ui.text(this, "Più veloce", 12.5f, Ui.TEXT2, Ui.REGULAR));
        box.addView(ends);

        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, dp(18), 0, 0);
        final float[] presets = {0.75f, 1f, 1.25f, 1.5f, 2f};
        final TextView[] cv = new TextView[presets.length];
        final Runnable paint = new Runnable() {
            public void run() {
                float cur = svc != null ? svc.speed() : 1f;
                value.setText(Ui.speed(cur));
                for (int i = 0; i < presets.length; i++) {
                    boolean sel = Math.abs(presets[i] - cur) < 0.001f;
                    cv[i].setTextColor(sel ? Ui.TAB_SEL_TEXT : Ui.TAB_TEXT);
                    cv[i].setBackground(Ui.rounded(sel ? Ui.TAB_SEL_BG : Ui.TAB_BG, dp(18)));
                }
                updateProgress();
            }
        };
        for (int i = 0; i < presets.length; i++) {
            final float v = presets[i];
            TextView c = Ui.text(this, v == 1f ? "Normale" : Ui.speed(v), 14, Ui.TAB_TEXT, Ui.MEDIUM);
            c.setGravity(Gravity.CENTER);
            c.setPadding(0, dp(9), 0, dp(9));
            Ui.pressable(c);
            c.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    if (svc == null) return;
                    svc.setSpeed(v);
                    sb.setProgress(Math.round((v - 0.5f) / 0.05f));
                    paint.run();
                }
            });
            LinearLayout.LayoutParams lp = Ui.lpw(1f);
            if (i > 0) lp.leftMargin = dp(6);
            chips.addView(c, lp);
            cv[i] = c;
        }
        box.addView(chips);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user || svc == null) return;
                svc.setSpeed(0.5f + p * 0.05f);
                paint.run();
            }

            public void onStartTrackingTouch(SeekBar s) {}

            public void onStopTrackingTouch(SeekBar s) {}
        });
        paint.run();
        Ui.sheet(this, box, false, 0.3f).show();
    }

    private void sleepSheet() {
        if (svc == null) return;
        long rem = svc.sleepRemaining();
        ActionSheet a = new ActionSheet(this, "Timer di spegnimento",
                rem > 0 ? "La musica si fermerà tra " + Ui.time(rem) : "Ferma la musica dopo un certo tempo");
        final int[] mins = {15, 30, 45, 60, 90, 120};
        final String[] labels = {"15 minuti", "30 minuti", "45 minuti", "1 ora", "1 ora e mezza", "2 ore"};
        for (int i = 0; i < mins.length; i++) {
            final int m = mins[i];
            final String lab = labels[i];
            a.add(R.drawable.ic_timer, labels[i], new Runnable() {
                public void run() {
                    if (svc != null) svc.setSleepTimer(m);
                    Toast.makeText(NowPlayingActivity.this, "La musica si fermerà tra " + lab, Toast.LENGTH_SHORT).show();
                    updateProgress();
                }
            });
        }
        if (rem > 0) {
            a.add(R.drawable.ic_close, "Disattiva il timer", Ui.DANGER, new Runnable() {
                public void run() {
                    if (svc != null) svc.setSleepTimer(0);
                    updateProgress();
                }
            });
        }
        a.show();
    }
}
