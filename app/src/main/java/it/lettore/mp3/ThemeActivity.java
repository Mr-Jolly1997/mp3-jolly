package it.lettore.mp3;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * Personalizzazione dei colori: anteprima dal vivo in alto (toccabile), elenco completo dei colori
 * sotto, selettore colore compatto che lascia visibile l'anteprima. Ogni tema (chiaro/scuro) ha i suoi colori.
 */
public class ThemeActivity extends Activity {
    private int myGen;
    private FrameLayout previewHolder;
    private LinearLayout listBox;
    private boolean showNowPlaying;
    private TextView segLib, segNp;

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        myGen = Ui.applyTheme(this);
        super.onCreate(b);
        Compat.transitions(this, R.anim.slide_in_right, R.anim.fade_out_short, R.anim.fade_in_short, R.anim.slide_out_right);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (myGen != Ui.themeGen) {
            Ui.init(this);
            myGen = Ui.themeGen;
            build();
        }
    }

    @Override
    public void finish() {
        super.finish();
        Compat.legacyAnim(this, R.anim.fade_in_short, R.anim.slide_out_right);
    }

    private void reload() {
        Ui.init(this);
        myGen = Ui.themeGen;
        build();
    }

    /** Costruisce (o ricostruisce, dopo un cambio colore) tutta la schermata. */
    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4), 0, dp(4), 0);
        ImageView back = Ui.icon(this, R.drawable.ic_back, Ui.ICON, 48, 12);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(back);
        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.setPadding(dp(4), 0, 0, 0);
        tc.addView(Ui.text(this, "Personalizza colori", 19, Ui.TEXT, Ui.MEDIUM));
        tc.addView(Ui.text(this, (Ui.dark ? "Tema scuro" : "Tema chiaro") + " · profilo «" + Ui.PROFILE_NAMES[Ui.profile] + "»", 13, Ui.TEXT2, Ui.REGULAR));
        top.addView(tc, Ui.lpw(1f));
        ImageView reset = Ui.icon(this, R.drawable.ic_refresh, Ui.ICON, 48, 12);
        reset.setContentDescription("Ripristina tutti i colori");
        reset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                PopMenu m = new PopMenu(ThemeActivity.this, "Ripristina");
                m.add(R.drawable.ic_refresh, "Colori del profilo attuale", new Runnable() {
                    public void run() {
                        Ui.dialog(ThemeActivity.this, "Ripristinare i colori?",
                                "I colori del tema " + (Ui.dark ? "scuro" : "chiaro") + " torneranno quelli del profilo «"
                                        + Ui.PROFILE_NAMES[Ui.profile] + "».",
                                null, "Annulla", "Ripristina", new Runnable() {
                                    public void run() {
                                        Settings.clearAllColors(ThemeActivity.this, Ui.dark);
                                        reload();
                                    }
                                }).show();
                    }
                });
                m.divider();
                for (int i = 0; i < Ui.PROFILE_NAMES.length; i++) {
                    final int pi = i;
                    int[] col = Ui.dark ? Ui.PROFILE_DARK[i] : Ui.PROFILE_LIGHT[i];
                    m.add(i == Ui.profile ? R.drawable.ic_check : R.drawable.ic_palette, "Profilo «" + Ui.PROFILE_NAMES[i] + "»",
                            col[Ui.T_NP_PLAY_BG], new Runnable() {
                                public void run() {
                                    Ui.dialog(ThemeActivity.this, "Applicare «" + Ui.PROFILE_NAMES[pi] + "»?",
                                            "Tutti i colori personalizzati (tema chiaro e scuro) verranno sostituiti da quelli del profilo.",
                                            null, "Annulla", "Applica", new Runnable() {
                                                public void run() {
                                                    Settings.setProfile(ThemeActivity.this, pi);
                                                    reload();
                                                }
                                            }).show();
                                }
                            });
                }
                m.show(v);
            }
        });
        top.addView(reset);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(64)));

        // scelta dell'anteprima
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(Ui.rounded(Ui.SURFACE2, dp(20)));
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        segLib = segment("Libreria", false);
        segNp = segment("In riproduzione", true);
        seg.addView(segLib, Ui.lpw(1f));
        seg.addView(segNp, Ui.lpw(1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.leftMargin = dp(16);
        sp.rightMargin = dp(16);
        root.addView(seg, sp);

        previewHolder = new FrameLayout(this);
        previewHolder.setPadding(dp(16), dp(10), dp(16), dp(4));
        root.addView(previewHolder);

        TextView hint = Ui.text(this, "Tocca un elemento dell'anteprima o della lista per cambiarne il colore", 13, Ui.TEXT2, Ui.REGULAR);
        hint.setSingleLine(false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(24), dp(4), dp(24), dp(6));
        root.addView(hint);

        ScrollView sv = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(dp(16), 0, dp(16), dp(24));
        sv.addView(listBox);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        Ui.decorate(this, root);
        renderPreview();
        renderList();
    }

    private TextView segment(String label, final boolean np) {
        TextView t = Ui.text(this, label, 14, Ui.TEXT2, Ui.MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(8), 0, dp(8));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showNowPlaying = np;
                renderPreview();
            }
        });
        return t;
    }

    // ---------------------------------------------------------------- anteprima

    /** Rende toccabile un elemento: un solo colore apre il selettore, più colori aprono un menu. */
    private void tap(View v, final int... tokens) {
        v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (tokens.length == 1) {
                    picker(tokens[0]);
                    return;
                }
                PopMenu m = new PopMenu(ThemeActivity.this, "Quale colore?");
                for (final int t : tokens) {
                    m.add(R.drawable.ic_brush, label(t), Ui.cur[t] == Ui.SURFACE ? Ui.ICON : Ui.cur[t], new Runnable() {
                        public void run() {
                            picker(t);
                        }
                    });
                }
                m.show(view);
            }
        });
    }

    private static String label(int t) {
        int g = 0;
        for (int i = 0; i < Ui.GROUP_START.length; i++) if (t >= Ui.GROUP_START[i]) g = i;
        String l = Ui.LABELS[t];
        return g >= 2 ? Ui.GROUP_NAMES[g] + " · " + l.toLowerCase(Locale.ITALIAN) : l;
    }

    private void renderPreview() {
        segLib.setTextColor(showNowPlaying ? Ui.TEXT2 : Ui.ON_ACCENT);
        segLib.setBackground(showNowPlaying ? null : Ui.rounded(Ui.ACCENT, dp(17)));
        segNp.setTextColor(showNowPlaying ? Ui.ON_ACCENT : Ui.TEXT2);
        segNp.setBackground(showNowPlaying ? Ui.rounded(Ui.ACCENT, dp(17)) : null);
        previewHolder.removeAllViews();
        previewHolder.addView(showNowPlaying ? previewNowPlaying() : previewLibrary());
    }

    private LinearLayout frame(int bg) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable d = Ui.rounded(bg, dp(20));
        d.setStroke(Math.max(1, dp(1)), Ui.DIVIDER);
        p.setBackground(d);
        p.setPadding(dp(12), dp(10), dp(12), dp(12));
        p.setClipToOutline(true);
        return p;
    }

    private ImageView smallIcon(int res, int color, int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(color);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    private View previewLibrary() {
        LinearLayout p = frame(Ui.BG);
        tap(p, Ui.T_BG);

        // barra superiore
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.text(this, "Musica", 19, Ui.TEXT, Ui.BOLD);
        tap(t, Ui.T_TEXT);
        bar.addView(t, Ui.lpw(1f));
        LinearLayout icons = new LinearLayout(this);
        icons.addView(smallIcon(R.drawable.ic_search, Ui.ICON, 20));
        View gap = new View(this);
        icons.addView(gap, new LinearLayout.LayoutParams(dp(14), 1));
        icons.addView(smallIcon(R.drawable.ic_settings, Ui.ICON, 20));
        icons.setPadding(dp(6), dp(6), dp(2), dp(6));
        tap(icons, Ui.T_ICON);
        bar.addView(icons);
        p.addView(bar);

        // schede
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(0, dp(8), 0, dp(6));
        TextView sel = chip("Brani", Ui.TAB_SEL_BG, Ui.TAB_SEL_TEXT);
        tap(sel, Ui.T_TAB_SEL_BG, Ui.T_TAB_SEL_TEXT);
        tabs.addView(sel);
        TextView un1 = chip("Album", Ui.TAB_BG, Ui.TAB_TEXT);
        tap(un1, Ui.T_TAB_BG, Ui.T_TAB_TEXT);
        tabs.addView(un1);
        TextView un2 = chip("Artisti", Ui.TAB_BG, Ui.TAB_TEXT);
        tap(un2, Ui.T_TAB_BG, Ui.T_TAB_TEXT);
        tabs.addView(un2);
        p.addView(tabs);

        // riga normale e riga in ascolto
        p.addView(songRow(false));
        p.addView(songRow(true));

        // riquadro / menu
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.rounded(Ui.SURFACE, dp(12)));
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.addView(smallIcon(R.drawable.ic_queue_add, Ui.ICON, 18));
        TextView ct = Ui.text(this, "Menu e finestre", 13.5f, Ui.TEXT, Ui.REGULAR);
        ct.setPadding(dp(10), 0, 0, 0);
        card.addView(ct, Ui.lpw(1f));
        TextView acc = Ui.text(this, "EVIDENZIATO", 11, Ui.ACCENT, Ui.MEDIUM);
        acc.setLetterSpacing(0.08f);
        card.addView(acc);
        tap(card, Ui.T_SURFACE, Ui.T_ACCENT, Ui.T_TEXT, Ui.T_ICON);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.topMargin = dp(6);
        p.addView(card, cl);

        // mini player
        LinearLayout mini = new LinearLayout(this);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setBackground(Ui.rounded(Ui.MINI_BG, dp(14)));
        mini.setElevation(dp(3));
        mini.setPadding(dp(8), dp(6), dp(6), dp(6));
        View th = new View(this);
        th.setBackground(Ui.rounded(Ui.tileColor("anteprima"), dp(8)));
        mini.addView(th, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout mc = new LinearLayout(this);
        mc.setOrientation(LinearLayout.VERTICAL);
        mc.setPadding(dp(10), 0, dp(4), 0);
        mc.addView(Ui.text(this, "Brano in ascolto", 13.5f, Ui.MINI_TEXT, Ui.MEDIUM));
        mc.addView(Ui.text(this, "Artista", 12, Ui.blend(Ui.MINI_TEXT, Ui.MINI_BG, 0.35f), Ui.REGULAR));
        mini.addView(mc, Ui.lpw(1f));
        mini.addView(smallIcon(R.drawable.ic_queue, Ui.MINI_ICONS, 22));
        ImageView mp = smallIcon(R.drawable.ic_play, Ui.MINI_PLAY_ICON, 36);
        mp.setBackground(Ui.oval(Ui.MINI_PLAY_BG));
        mp.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams mpl = new LinearLayout.LayoutParams(dp(36), dp(36));
        mpl.leftMargin = dp(10);
        mpl.rightMargin = dp(8);
        mini.addView(mp, mpl);
        mini.addView(smallIcon(R.drawable.ic_next, Ui.MINI_ICONS, 22));
        tap(mini, Ui.T_MINI_BG, Ui.T_MINI_TEXT, Ui.T_MINI_PLAY_BG, Ui.T_MINI_PLAY_ICON, Ui.T_MINI_ICONS);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, -2);
        ml.topMargin = dp(8);
        p.addView(mini, ml);
        return p;
    }

    private TextView chip(String s, int bg, int fg) {
        TextView t = Ui.text(this, s, 13, fg, Ui.MEDIUM);
        t.setPadding(dp(14), dp(6), dp(14), dp(6));
        t.setBackground(Ui.rounded(bg, dp(16)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    private View songRow(boolean playing) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(5), 0, dp(5));
        FrameLayout th = new FrameLayout(this);
        View tile = new View(this);
        tile.setBackground(Ui.rounded(Ui.tileColor(playing ? "b" : "a"), dp(8)));
        th.addView(tile, new FrameLayout.LayoutParams(-1, -1));
        if (playing) {
            View scrim = new View(this);
            scrim.setBackground(Ui.rounded(0x99000000, dp(8)));
            th.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
            Views.PlayingBars bars = new Views.PlayingBars(this);
            bars.setColor(0xFFFFFFFF);
            bars.setPlaying(true);
            th.addView(bars, new FrameLayout.LayoutParams(-1, -1));
        }
        r.addView(th, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(10), 0, dp(6), 0);
        c.addView(Ui.text(this, playing ? "Brano in ascolto" : "Titolo del brano", 14, playing ? Ui.PLAYING : Ui.TEXT, Ui.MEDIUM));
        c.addView(Ui.text(this, "Artista · Album", 12, Ui.TEXT2, Ui.REGULAR));
        r.addView(c, Ui.lpw(1f));
        r.addView(Ui.text(this, "3:45", 12, Ui.TEXT3, Ui.REGULAR));
        ImageView more = smallIcon(R.drawable.ic_more, Ui.ICON, 20);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(dp(20), dp(20));
        ml.leftMargin = dp(10);
        r.addView(more, ml);
        if (playing) tap(r, Ui.T_PLAYING);
        else tap(r, Ui.T_TEXT, Ui.T_TEXT2, Ui.T_ICON);
        return r;
    }

    private View previewNowPlaying() {
        int mood = Ui.coverBg ? (Ui.dark ? Ui.blend(Ui.ACCENT, Ui.BG, 0.72f) : Ui.blend(Ui.ACCENT, Ui.BG, 0.85f)) : Ui.BG;
        LinearLayout p = frame(Ui.BG);
        GradientDrawable npBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{mood, Ui.BG});
        npBg.setCornerRadius(dp(20));
        npBg.setStroke(Math.max(1, dp(1)), Ui.DIVIDER);
        p.setBackground(npBg);
        p.setPadding(dp(16), dp(14), dp(16), dp(12));
        tap(p, Ui.T_BG);

        LinearLayout tr = new LinearLayout(this);
        tr.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.addView(Ui.text(this, "Titolo del brano", 19, Ui.NP_TITLE, Ui.BOLD));
        tc.addView(Ui.text(this, "Artista", 14, Ui.NP_ARTIST, Ui.MEDIUM));
        tr.addView(tc, Ui.lpw(1f));
        tr.addView(smallIcon(R.drawable.ic_heart, Ui.NP_HEART, 24));
        tap(tr, Ui.T_NP_TITLE, Ui.T_NP_ARTIST, Ui.T_NP_HEART);
        p.addView(tr);

        // barra di avanzamento disegnata
        FrameLayout prog = new FrameLayout(this);
        prog.setPadding(0, dp(12), 0, dp(10));
        View track = new View(this);
        track.setBackground(Ui.rounded(Ui.alpha(Ui.TEXT, 0x40), dp(2)));
        prog.addView(track, new FrameLayout.LayoutParams(-1, dp(3), Gravity.CENTER_VERTICAL));
        LinearLayout done = new LinearLayout(this);
        done.setGravity(Gravity.CENTER_VERTICAL);
        View fill = new View(this);
        fill.setBackground(Ui.rounded(Ui.NP_PROGRESS, dp(2)));
        done.addView(fill, new LinearLayout.LayoutParams(0, dp(3), 0.4f));
        View dot = new View(this);
        dot.setBackground(Ui.oval(Ui.NP_PROGRESS));
        done.addView(dot, new LinearLayout.LayoutParams(dp(12), dp(12)));
        done.addView(new View(this), new LinearLayout.LayoutParams(0, dp(3), 0.6f));
        prog.addView(done, new FrameLayout.LayoutParams(-1, dp(12), Gravity.CENTER_VERTICAL));
        tap(prog, Ui.T_NP_PROGRESS);
        p.addView(prog);

        // comandi
        LinearLayout ctr = new LinearLayout(this);
        ctr.setGravity(Gravity.CENTER_VERTICAL);
        ImageView sh = smallIcon(R.drawable.ic_shuffle, Ui.NP_TOGGLE, 34);
        sh.setBackground(Ui.oval(Ui.alpha(Ui.NP_TOGGLE, 0x30)));
        sh.setPadding(dp(7), dp(7), dp(7), dp(7));
        ImageView pv = smallIcon(R.drawable.ic_prev, Ui.NP_SKIP, 30);
        ImageView pl = smallIcon(R.drawable.ic_play, Ui.NP_PLAY_ICON, 52);
        pl.setBackground(Ui.oval(Ui.NP_PLAY_BG));
        pl.setPadding(dp(13), dp(13), dp(13), dp(13));
        ImageView nx = smallIcon(R.drawable.ic_next, Ui.NP_SKIP, 30);
        ImageView rp = smallIcon(R.drawable.ic_repeat, Ui.NP_TOOLS, 34);
        rp.setPadding(dp(7), dp(7), dp(7), dp(7));
        View[] vs = {sh, pv, pl, nx, rp};
        for (int i = 0; i < vs.length; i++) {
            if (i > 0) ctr.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            ctr.addView(vs[i]);
        }
        tap(sh, Ui.T_NP_TOGGLE);
        tap(pv, Ui.T_NP_SKIP);
        tap(nx, Ui.T_NP_SKIP);
        tap(pl, Ui.T_NP_PLAY_BG, Ui.T_NP_PLAY_ICON);
        tap(rp, Ui.T_NP_TOOLS);
        p.addView(ctr);

        LinearLayout tools = new LinearLayout(this);
        tools.setPadding(0, dp(12), 0, 0);
        String[] names = {"Timer", "Velocità", "Equalizzatore", "Condividi"};
        int[] ics = {R.drawable.ic_timer, R.drawable.ic_speed, R.drawable.ic_equalizer, R.drawable.ic_share};
        for (int i = 0; i < 4; i++) {
            LinearLayout tl = new LinearLayout(this);
            tl.setOrientation(LinearLayout.VERTICAL);
            tl.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView iv = smallIcon(ics[i], Ui.NP_TOOLS, 20);
            tl.addView(iv);
            TextView tv = Ui.text(this, names[i], 11, Ui.NP_TOOLS, Ui.MEDIUM);
            tv.setGravity(Gravity.CENTER);
            tv.setEllipsize(null);
            tv.setPadding(dp(1), dp(4), dp(1), 0);
            tv.setAutoSizeTextTypeUniformWithConfiguration(8, 11, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            tl.addView(tv, new LinearLayout.LayoutParams(-1, dp(19)));
            tools.addView(tl, Ui.lpw(1f));
        }
        tap(tools, Ui.T_NP_TOOLS);
        p.addView(tools);
        return p;
    }

    // ---------------------------------------------------------------- elenco

    private void renderList() {
        listBox.removeAllViews();
        for (int g = 0; g < Ui.GROUP_NAMES.length; g++) {
            TextView h = Ui.text(this, Ui.GROUP_NAMES[g].toUpperCase(Locale.ITALIAN), 13, Ui.ACCENT, Ui.MEDIUM);
            h.setLetterSpacing(0.08f);
            h.setPadding(dp(8), dp(18), 0, dp(8));
            listBox.addView(h);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.rounded(Ui.SURFACE, dp(18)));
            card.setClipToOutline(true);
            int from = Ui.GROUP_START[g];
            int to = g + 1 < Ui.GROUP_START.length ? Ui.GROUP_START[g + 1] : Ui.KEYS.length;
            for (int t = from; t < to; t++) {
                if (t > from) {
                    View d = new View(this);
                    d.setBackgroundColor(Ui.DIVIDER);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
                    lp.leftMargin = dp(66);
                    card.addView(d, lp);
                }
                card.addView(tokenRow(t));
            }
            listBox.addView(card);
        }
    }

    private View tokenRow(final int t) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(60));
        r.setPadding(dp(16), dp(8), dp(16), dp(8));
        r.setBackground(Ui.ripple(this, false));
        View sw = new View(this);
        GradientDrawable d = Ui.oval(Ui.cur[t]);
        d.setStroke(Math.max(1, dp(1)), Ui.alpha(Ui.TEXT, 0x40));
        sw.setBackground(d);
        r.addView(sw, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), 0, dp(8), 0);
        c.addView(Ui.text(this, Ui.LABELS[t], 15.5f, Ui.TEXT, Ui.MEDIUM));
        boolean custom = Settings.hasColor(this, Ui.dark, Ui.KEYS[t]);
        c.addView(Ui.text(this, Ui.hex(Ui.cur[t]) + (custom ? " · personalizzato" : " · predefinito"), 12.5f,
                custom ? Ui.ACCENT : Ui.TEXT2, Ui.REGULAR));
        r.addView(c, Ui.lpw(1f));
        ImageView ch = new ImageView(this);
        ch.setImageResource(R.drawable.ic_chevron);
        ch.setColorFilter(Ui.TEXT3);
        r.addView(ch, new LinearLayout.LayoutParams(dp(22), dp(22)));
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                picker(t);
            }
        });
        return r;
    }

    // ---------------------------------------------------------------- selettore colore

    private void picker(final int t) {
        boolean np = t >= Ui.GROUP_START[3];
        if (np != showNowPlaying) {
            showNowPlaying = np;
            renderPreview();
        }
        final float[] hsv = new float[3];
        Color.colorToHSV(Ui.cur[t], hsv);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), 0, dp(20), dp(14));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        final View big = new View(this);
        head.addView(big, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout hc = new LinearLayout(this);
        hc.setOrientation(LinearLayout.VERTICAL);
        hc.setPadding(dp(14), 0, dp(8), 0);
        hc.addView(Ui.text(this, label(t), 16, Ui.TEXT, Ui.MEDIUM));
        final TextView hex = Ui.text(this, "", 13, Ui.TEXT2, Ui.REGULAR);
        hc.addView(hex);
        head.addView(hc, Ui.lpw(1f));
        TextView def = Ui.text(this, "Predefinito", 14, Ui.ACCENT, Ui.MEDIUM);
        def.setPadding(dp(10), dp(8), dp(4), dp(8));
        head.addView(def);
        box.addView(head);

        // tavolozza
        LinearLayout pal = new LinearLayout(this);
        pal.setOrientation(LinearLayout.VERTICAL);
        pal.setPadding(0, dp(12), 0, dp(4));
        final SeekBar[] bars = new SeekBar[3];
        final Runnable[] apply = new Runnable[1];
        for (int row = 0; row < 3; row++) {
            LinearLayout rl = new LinearLayout(this);
            for (int k = 0; k < 9; k++) {
                final int col = Ui.PALETTE[row * 9 + k];
                FrameLayout cell = new FrameLayout(this);
                View dot = new View(this);
                GradientDrawable gd = Ui.oval(col);
                gd.setStroke(Math.max(1, dp(1)), Ui.alpha(Ui.TEXT, 0x30));
                dot.setBackground(gd);
                Ui.pressable(dot);
                dot.setClickable(true);
                dot.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Color.colorToHSV(col, hsv);
                        bars[0].setProgress(Math.round(hsv[0]));
                        bars[1].setProgress(Math.round(hsv[1] * 100));
                        bars[2].setProgress(Math.round(hsv[2] * 100));
                        apply[0].run();
                    }
                });
                cell.addView(dot, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
                rl.addView(cell, new LinearLayout.LayoutParams(0, dp(36), 1f));
            }
            pal.addView(rl);
        }
        box.addView(pal);

        // tonalità, intensità, luminosità
        String[] names = {"Tonalità", "Intensità", "Luminosità"};
        int[] max = {360, 100, 100};
        final GradientDrawable[] tracks = new GradientDrawable[3];
        for (int i = 0; i < 3; i++) {
            LinearLayout sr = new LinearLayout(this);
            sr.setGravity(Gravity.CENTER_VERTICAL);
            TextView n = Ui.text(this, names[i], 13, Ui.TEXT2, Ui.MEDIUM);
            sr.addView(n, new LinearLayout.LayoutParams(dp(84), -2));
            SeekBar sb = new SeekBar(this);
            sb.setMax(max[i]);
            tracks[i] = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF000000, 0xFFFFFFFF});
            tracks[i].setCornerRadius(dp(6));
            tracks[i].setSize(0, dp(12));
            sb.setProgressDrawable(tracks[i]);
            if (Build.VERSION.SDK_INT >= 29) {
                sb.setMinHeight(dp(12));
                sb.setMaxHeight(dp(12));
            }
            sb.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT));
            sb.setPadding(dp(12), dp(10), dp(12), dp(10));
            sr.addView(sb, Ui.lpw(1f));
            bars[i] = sb;
            box.addView(sr);
        }
        bars[0].setProgress(Math.round(hsv[0]));
        bars[1].setProgress(Math.round(hsv[1] * 100));
        bars[2].setProgress(Math.round(hsv[2] * 100));

        final Runnable refreshTracks = new Runnable() {
            public void run() {
                int[] hues = new int[7];
                for (int i = 0; i < 7; i++) hues[i] = Color.HSVToColor(new float[]{i * 60f, 1f, 1f});
                tracks[0].setColors(hues);
                tracks[1].setColors(new int[]{Color.HSVToColor(new float[]{hsv[0], 0f, Math.max(hsv[2], 0.15f)}),
                        Color.HSVToColor(new float[]{hsv[0], 1f, Math.max(hsv[2], 0.15f)})});
                tracks[2].setColors(new int[]{0xFF000000, Color.HSVToColor(new float[]{hsv[0], hsv[1], 1f})});
            }
        };
        final Runnable showCurrent = new Runnable() {
            public void run() {
                GradientDrawable gd = Ui.rounded(Ui.cur[t], dp(12));
                gd.setStroke(Math.max(1, dp(1)), Ui.alpha(Ui.TEXT, 0x40));
                big.setBackground(gd);
                hex.setText(Ui.hex(Ui.cur[t]).toUpperCase(Locale.ROOT)
                        + (Settings.hasColor(ThemeActivity.this, Ui.dark, Ui.KEYS[t]) ? " · personalizzato" : " · predefinito"));
            }
        };
        apply[0] = new Runnable() {
            public void run() {
                hsv[0] = bars[0].getProgress();
                hsv[1] = bars[1].getProgress() / 100f;
                hsv[2] = bars[2].getProgress() / 100f;
                int col = Color.HSVToColor(hsv);
                Settings.setColor(ThemeActivity.this, Ui.dark, Ui.KEYS[t], col);
                Ui.cur[t] = col;
                Ui.assign();
                myGen = Ui.themeGen;
                refreshTracks.run();
                showCurrent.run();
                renderPreview();
            }
        };
        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) apply[0].run();
            }

            public void onStartTrackingTouch(SeekBar s) {}

            public void onStopTrackingTouch(SeekBar s) {}
        };
        for (SeekBar sb : bars) sb.setOnSeekBarChangeListener(l);
        refreshTracks.run();
        showCurrent.run();

        def.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Settings.clearColor(ThemeActivity.this, Ui.dark, Ui.KEYS[t]);
                Ui.init(ThemeActivity.this);
                myGen = Ui.themeGen;
                Color.colorToHSV(Ui.cur[t], hsv);
                bars[0].setProgress(Math.round(hsv[0]));
                bars[1].setProgress(Math.round(hsv[1] * 100));
                bars[2].setProgress(Math.round(hsv[2] * 100));
                refreshTracks.run();
                showCurrent.run();
                renderPreview();
            }
        });

        TextView done = Ui.pill(this, "Fatto", R.drawable.ic_check, true);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = dp(10);
        box.addView(done, dl);

        final Dialog d = Ui.sheet(this, box, false, 0f);
        done.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
            }
        });
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface di) {
                // ricolora anche il resto di questa schermata
                build();
            }
        });
        d.show();
    }
}
