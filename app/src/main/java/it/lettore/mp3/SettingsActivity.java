package it.lettore.mp3;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/** Impostazioni: esclusioni della libreria, riproduzione, aspetto. */
public class SettingsActivity extends Activity {
    private LinearLayout content;
    private PlaybackService svc;
    private boolean bound;
    private TextView eqSummary;
    private int myGen;

    private final ServiceConnection conn = new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder b) {
            svc = ((PlaybackService.LocalBinder) b).get();
            updateEqSummary();
        }

        public void onServiceDisconnected(ComponentName n) {
            svc = null;
        }
    };

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        myGen = Ui.applyTheme(this);
        super.onCreate(b);
        Compat.transitions(this, R.anim.slide_in_right, R.anim.fade_out_short, R.anim.fade_in_short, R.anim.slide_out_right);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), 0, dp(16), 0);
        ImageView back = Ui.icon(this, R.drawable.ic_back, Ui.ICON, 48, 12);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(back);
        TextView t = Ui.text(this, "Impostazioni", 20, Ui.TEXT, Ui.MEDIUM);
        t.setPadding(dp(6), 0, 0, 0);
        top.addView(t, Ui.lpw(1f));
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(64)));

        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), 0, dp(16), dp(28));
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        Ui.decorate(this, root);
        build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        bound = bindService(new Intent(this, PlaybackService.class), conn, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (myGen != Ui.themeGen) {
            recreate();
            return;
        }
        updateEqSummary();
    }

    @Override
    protected void onStop() {
        if (bound) unbindService(conn);
        bound = false;
        svc = null;
        super.onStop();
    }

    @Override
    public void finish() {
        super.finish();
        Compat.legacyAnim(this, R.anim.fade_in_short, R.anim.slide_out_right);
    }

    private void build() {
        content.removeAllViews();
        final Library lib = Library.current;

        // ----- Libreria
        content.addView(sectionLabel("Libreria"));
        LinearLayout c1 = card();
        switchRow(c1, R.drawable.ic_whatsapp_off, "Escludi audio di WhatsApp",
                "Note vocali e file audio ricevuti nelle chat", Settings.exclWhatsApp(this),
                new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Settings.setLibraryBool(SettingsActivity.this, "excl_wa", v);
                        changed();
                    }
                });
        divider(c1);
        switchRow(c1, R.drawable.ic_whatsapp_off, "Escludi audio di Telegram",
                "Audio e messaggi vocali scaricati da Telegram", Settings.exclTelegram(this),
                new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Settings.setLibraryBool(SettingsActivity.this, "excl_tg", v);
                        changed();
                    }
                });
        divider(c1);
        ArrayList<String> ex = Settings.excludedFolders(this);
        String exSummary;
        if (ex.isEmpty()) exSummary = "Nessuna. Tocca per scegliere le cartelle da nascondere";
        else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ex.size() && i < 3; i++) {
                if (i > 0) sb.append(", ");
                String f = ex.get(i);
                sb.append(f.substring(f.lastIndexOf('/') + 1));
            }
            if (ex.size() > 3) sb.append(" e altre ").append(ex.size() - 3);
            exSummary = sb.toString();
        }
        navRow(c1, R.drawable.ic_block, "Cartelle escluse", exSummary, new Runnable() {
            public void run() {
                foldersSheet();
            }
        });
        divider(c1);
        int md = Settings.minDuration(this);
        int mdi = 0;
        for (int i = 0; i < Settings.MIN_DURATIONS.length; i++) if (Settings.MIN_DURATIONS[i] == md) mdi = i;
        navRow(c1, R.drawable.ic_history, "Ignora i file brevi", Settings.MIN_DURATION_NAMES[mdi]
                + (mdi == 0 ? "" : " vengono nascosti"), new Runnable() {
            public void run() {
                minDurationSheet();
            }
        });
        divider(c1);
        String libInfo = lib == null ? "Rileggi la musica presente sul telefono"
                : Library.count(lib.songs.size()) + " in libreria" + (lib.excludedCount > 0
                ? " · " + lib.excludedCount + " file esclusi" : "");
        navRow(c1, R.drawable.ic_refresh, "Aggiorna libreria", libInfo, new Runnable() {
            public void run() {
                Settings.forceReload();
                Toast.makeText(SettingsActivity.this, "La libreria verrà aggiornata", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        content.addView(c1);

        // ----- Riproduzione
        content.addView(sectionLabel("Riproduzione"));
        LinearLayout c2 = card();
        eqSummary = navRow(c2, R.drawable.ic_equalizer, "Equalizzatore", "Bassi, alti ed effetto spaziale", new Runnable() {
            public void run() {
                startActivity(new Intent(SettingsActivity.this, EqualizerActivity.class));
                Compat.legacyAnim(SettingsActivity.this, R.anim.slide_in_right, R.anim.fade_out_short);
            }
        });
        divider(c2);
        switchRow(c2, R.drawable.ic_play, "Riprendi da dove avevi lasciato",
                "All'apertura ritrovi l'ultimo brano, fermo nel punto in cui eri", Settings.resume(this),
                new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Settings.setResume(SettingsActivity.this, v);
                    }
                });
        content.addView(c2);

        // ----- Aspetto
        content.addView(sectionLabel("Aspetto"));
        LinearLayout c3 = card();

        // tema: sistema / chiaro / scuro
        LinearLayout trow = new LinearLayout(this);
        trow.setOrientation(LinearLayout.VERTICAL);
        trow.setPadding(dp(16), dp(14), dp(16), dp(16));
        LinearLayout th = new LinearLayout(this);
        th.setOrientation(LinearLayout.HORIZONTAL);
        th.setGravity(Gravity.CENTER_VERTICAL);
        th.addView(tile(R.drawable.ic_contrast));
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        tcol.setPadding(dp(14), 0, 0, 0);
        tcol.addView(Ui.text(this, "Tema", 16, Ui.TEXT, Ui.MEDIUM));
        tcol.addView(Ui.text(this, "«Sistema» segue l'impostazione del telefono", 13.5f, Ui.TEXT2, Ui.REGULAR));
        th.addView(tcol, Ui.lpw(1f));
        trow.addView(th);
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(Ui.rounded(Ui.SURFACE2, dp(22)));
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        int mode = Settings.themeMode(this);
        for (int i = 0; i < Settings.THEME_NAMES.length; i++) {
            final int m = i;
            boolean sel = i == mode;
            TextView o = Ui.text(this, Settings.THEME_NAMES[i], 14.5f, sel ? Ui.ON_ACCENT : Ui.TEXT2, Ui.MEDIUM);
            o.setGravity(Gravity.CENTER);
            o.setPadding(0, dp(9), 0, dp(9));
            o.setBackground(sel ? Ui.rounded(Ui.ACCENT, dp(18)) : Ui.rounded(0, dp(18)));
            o.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (m == Settings.themeMode(SettingsActivity.this)) return;
                    Settings.setThemeMode(SettingsActivity.this, m);
                    recreate();
                }
            });
            seg.addView(o, Ui.lpw(1f));
        }
        LinearLayout.LayoutParams sgp = new LinearLayout.LayoutParams(-1, -2);
        sgp.topMargin = dp(14);
        trow.addView(seg, sgp);
        c3.addView(trow);
        divider(c3);

        // profili colore
        LinearLayout prow = new LinearLayout(this);
        prow.setOrientation(LinearLayout.VERTICAL);
        prow.setPadding(dp(16), dp(14), dp(16), dp(10));
        LinearLayout ph = new LinearLayout(this);
        ph.setOrientation(LinearLayout.HORIZONTAL);
        ph.setGravity(Gravity.CENTER_VERTICAL);
        ph.addView(tile(R.drawable.ic_palette));
        LinearLayout pcol = new LinearLayout(this);
        pcol.setOrientation(LinearLayout.VERTICAL);
        pcol.setPadding(dp(14), 0, 0, 0);
        pcol.addView(Ui.text(this, "Profilo colori", 16, Ui.TEXT, Ui.MEDIUM));
        pcol.addView(Ui.text(this, "Ognuno ha una versione chiara e una scura", 13.5f, Ui.TEXT2, Ui.REGULAR));
        ph.addView(pcol, Ui.lpw(1f));
        prow.addView(ph);
        for (int i = 0; i < Ui.PROFILE_NAMES.length; i++) prow.addView(profileRow(i));
        c3.addView(prow);
        divider(c3);

        switchRow(c3, R.drawable.ic_album, "Sfondo dal colore della copertina",
                "Nella schermata del brano lo sfondo prende la tinta della copertina", Settings.coverBg(this),
                new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        Settings.setCoverBg(SettingsActivity.this, v);
                        myGen = Ui.themeGen;
                        Ui.init(SettingsActivity.this);
                    }
                });
        divider(c3);
        navRow(c3, R.drawable.ic_brush, "Personalizza ogni colore",
                "Pulsanti, scritte e sfondi uno per uno, con anteprima dal vivo", new Runnable() {
                    public void run() {
                        startActivity(new Intent(SettingsActivity.this, ThemeActivity.class));
                        Compat.legacyAnim(SettingsActivity.this, R.anim.slide_in_right, R.anim.fade_out_short);
                    }
                });
        content.addView(c3);

        // ----- Informazioni
        content.addView(sectionLabel("Informazioni"));
        LinearLayout c4 = card();
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(16), dp(14), dp(16), dp(14));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        info.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout icol = new LinearLayout(this);
        icol.setOrientation(LinearLayout.VERTICAL);
        icol.setPadding(dp(14), 0, 0, 0);
        icol.addView(Ui.text(this, getString(R.string.app_name) + " " + versionName(), 16, Ui.TEXT, Ui.MEDIUM));
        TextView it = Ui.text(this, "Software libero e open source. Niente pubblicità, niente account, nessuna connessione a internet.", 13.5f, Ui.TEXT2, Ui.REGULAR);
        it.setSingleLine(false);
        icol.addView(it);
        info.addView(icol, Ui.lpw(1f));
        c4.addView(info);
        content.addView(c4);
    }

    @SuppressWarnings("deprecation")
    private String versionName() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private void changed() {
        Toast.makeText(this, "Libreria aggiornata", Toast.LENGTH_SHORT).show();
    }

    private void updateEqSummary() {
        if (eqSummary == null) return;
        AudioFx fx = svc != null ? svc.fx() : null;
        if (fx == null) return;
        if (!fx.available()) eqSummary.setText("Non disponibile su questo telefono");
        else if (!fx.enabled) eqSummary.setText("Disattivato");
        else eqSummary.setText("Attivo · " + (fx.preset >= 0 && fx.preset < fx.presets.length ? fx.presets[fx.preset] : "Personalizzato"));
    }

    // ---------------------------------------------------------------- pezzi dell'interfaccia

    private TextView sectionLabel(String s) {
        TextView t = Ui.text(this, s.toUpperCase(Locale.ITALIAN), 13, Ui.ACCENT, Ui.MEDIUM);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(8), dp(22), 0, dp(8));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.rounded(Ui.SURFACE, dp(20)));
        c.setClipToOutline(true);
        return c;
    }

    private void divider(LinearLayout parent) {
        View d = new View(this);
        d.setBackgroundColor(Ui.DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
        lp.leftMargin = dp(70);
        parent.addView(d, lp);
    }

    private ImageView tile(int icon) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Ui.ACCENT);
        iv.setBackground(Ui.rounded(Ui.ACCENT_SOFT, dp(12)));
        iv.setPadding(dp(9), dp(9), dp(9), dp(9));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return iv;
    }

    private LinearLayout baseRow(LinearLayout parent, int icon, String title, String sub, TextView[] subOut) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(72));
        row.setPadding(dp(16), dp(10), dp(12), dp(10));
        row.setBackground(Ui.ripple(this, false));
        row.addView(tile(icon));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, dp(8), 0);
        col.addView(Ui.text(this, title, 16, Ui.TEXT, Ui.MEDIUM));
        TextView s = Ui.text(this, sub, 13.5f, Ui.TEXT2, Ui.REGULAR);
        s.setSingleLine(false);
        s.setMaxLines(2);
        s.setPadding(0, dp(2), 0, 0);
        col.addView(s);
        subOut[0] = s;
        row.addView(col, Ui.lpw(1f));
        parent.addView(row);
        return row;
    }

    private void switchRow(LinearLayout parent, int icon, String title, String sub, boolean value,
                           CompoundButton.OnCheckedChangeListener l) {
        TextView[] so = new TextView[1];
        LinearLayout row = baseRow(parent, icon, title, sub, so);
        final Switch sw = new Switch(this);
        Ui.tint(sw);
        sw.setChecked(value);
        sw.setClickable(false);
        sw.setFocusable(false);
        sw.setOnCheckedChangeListener(l);
        row.addView(sw);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sw.toggle();
            }
        });
    }

    private TextView navRow(LinearLayout parent, int icon, String title, String sub, final Runnable r) {
        TextView[] so = new TextView[1];
        LinearLayout row = baseRow(parent, icon, title, sub, so);
        ImageView ch = new ImageView(this);
        ch.setImageResource(R.drawable.ic_chevron);
        ch.setColorFilter(Ui.TEXT3);
        row.addView(ch, new LinearLayout.LayoutParams(dp(22), dp(22)));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                r.run();
            }
        });
        return so[0];
    }

    /** Riga di un profilo: anteprima dei suoi colori, nome, spunta se attivo. */
    private View profileRow(final int i) {
        final boolean sel = i == Ui.profile;
        int[] col = Ui.dark ? Ui.PROFILE_DARK[i] : Ui.PROFILE_LIGHT[i];
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(10), dp(10), dp(12), dp(10));
        GradientDrawable bgd = Ui.rounded(sel ? Ui.ACCENT_SOFT : 0, dp(16));
        if (sel) bgd.setStroke(Math.max(1, dp(1.5f)), Ui.ACCENT);
        r.setBackground(bgd);
        r.setForeground(Ui.ripple(this, false));
        // mini anteprima: sfondo del profilo con cinque pallini dei suoi colori principali
        LinearLayout sw = new LinearLayout(this);
        sw.setGravity(Gravity.CENTER);
        GradientDrawable sbg = Ui.rounded(col[Ui.T_BG], dp(12));
        sbg.setStroke(Math.max(1, dp(1)), Ui.DIVIDER);
        sw.setBackground(sbg);
        sw.setPadding(dp(8), 0, dp(8), 0);
        int[] dots = {col[Ui.T_TAB_SEL_BG], col[Ui.T_TEXT], col[Ui.T_NP_PLAY_BG], col[Ui.T_ICON], col[Ui.T_NP_HEART]};
        for (int k = 0; k < dots.length; k++) {
            View d = new View(this);
            d.setBackground(Ui.oval(dots[k]));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(14), dp(14));
            if (k > 0) lp.leftMargin = dp(4);
            sw.addView(d, lp);
        }
        r.addView(sw, new LinearLayout.LayoutParams(dp(110), dp(40)));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), 0, dp(6), 0);
        c.addView(Ui.text(this, Ui.PROFILE_NAMES[i], 15.5f, Ui.TEXT, Ui.MEDIUM));
        TextView ds = Ui.text(this, Ui.PROFILE_DESC[i], 12.5f, Ui.TEXT2, Ui.REGULAR);
        ds.setSingleLine(false);
        ds.setMaxLines(2);
        c.addView(ds);
        r.addView(c, Ui.lpw(1f));
        if (sel) {
            ImageView ck = new ImageView(this);
            ck.setImageResource(R.drawable.ic_check);
            ck.setColorFilter(Ui.ACCENT);
            r.addView(ck, new LinearLayout.LayoutParams(dp(22), dp(22)));
        }
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = dp(8);
        r.setLayoutParams(rl);
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (sel && !Settings.hasCustomColors(SettingsActivity.this)) return;
                applyProfile(i);
            }
        });
        return r;
    }

    private void applyProfile(final int i) {
        Runnable go = new Runnable() {
            public void run() {
                Settings.setProfile(SettingsActivity.this, i);
                recreate();
            }
        };
        if (Settings.hasCustomColors(this)) {
            Ui.dialog(this, "Applicare «" + Ui.PROFILE_NAMES[i] + "»?",
                    "I colori che hai personalizzato a mano verranno sostituiti da quelli del profilo.",
                    null, "Annulla", "Applica", go).show();
        } else {
            go.run();
        }
    }

    // ---------------------------------------------------------------- schede

    private void minDurationSheet() {
        ActionSheet a = new ActionSheet(this, "Ignora i file brevi",
                "Utile per nascondere suonerie, notifiche e note vocali");
        int cur = Settings.minDuration(this);
        for (int i = 0; i < Settings.MIN_DURATIONS.length; i++) {
            final int sec = Settings.MIN_DURATIONS[i];
            boolean sel = sec == cur;
            a.add(sel ? R.drawable.ic_check : R.drawable.ic_history, Settings.MIN_DURATION_NAMES[i],
                    sel ? Ui.ACCENT : Ui.TEXT2, new Runnable() {
                        public void run() {
                            Settings.setMinDuration(SettingsActivity.this, sec);
                            changed();
                            build();
                        }
                    });
        }
        a.show();
    }

    /** Elenco di tutte le cartelle con audio, con casella per escluderle. */
    private void foldersSheet() {
        Library lib = Library.current;
        if (lib == null) {
            Toast.makeText(this, "Apri prima la libreria", Toast.LENGTH_SHORT).show();
            return;
        }
        final ArrayList<Library.FolderInfo> folders = new ArrayList<Library.FolderInfo>();
        for (Library.FolderInfo f : lib.allFolders) if (!f.auto) folders.add(f);
        final ArrayList<String> excluded = Settings.excludedFolders(this);
        final ArrayList<String> original = new ArrayList<String>(excluded);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.VERTICAL);
        hdr.setPadding(dp(20), dp(4), dp(20), dp(10));
        hdr.addView(Ui.text(this, "Cartelle escluse", 18, Ui.TEXT, Ui.BOLD));
        TextView hs = Ui.text(this, "Spunta le cartelle da nascondere: i loro brani (e quelli delle sottocartelle) non compariranno nella libreria. I file non vengono toccati.",
                13.5f, Ui.TEXT2, Ui.REGULAR);
        hs.setSingleLine(false);
        hs.setPadding(0, dp(4), 0, 0);
        hdr.addView(hs);
        box.addView(hdr);

        final ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(Ui.ripple(this, false));
        lv.setDrawSelectorOnTop(true);
        final BaseAdapter ad = new BaseAdapter() {
            public int getCount() { return folders.size(); }
            public Object getItem(int p) { return folders.get(p); }
            public long getItemId(int p) { return p; }

            public View getView(int p, View cv, ViewGroup parent) {
                LinearLayout row;
                if (cv == null) {
                    row = new LinearLayout(SettingsActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setMinimumHeight(dp(64));
                    row.setPadding(dp(18), dp(6), dp(12), dp(6));
                    row.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
                    row.addView(tile(R.drawable.ic_folder));
                    LinearLayout col = new LinearLayout(SettingsActivity.this);
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.setPadding(dp(14), 0, dp(8), 0);
                    col.addView(Ui.text(SettingsActivity.this, "", 15.5f, Ui.TEXT, Ui.MEDIUM));
                    col.addView(Ui.text(SettingsActivity.this, "", 12.5f, Ui.TEXT2, Ui.REGULAR));
                    row.addView(col, Ui.lpw(1f));
                    CheckBox cb = new CheckBox(SettingsActivity.this);
                    Ui.tint(cb);
                    cb.setClickable(false);
                    cb.setFocusable(false);
                    row.addView(cb);
                    cv = row;
                }
                row = (LinearLayout) cv;
                Library.FolderInfo f = folders.get(p);
                LinearLayout col = (LinearLayout) row.getChildAt(1);
                boolean ex = Library.inFolders(f.path, excluded);
                ((TextView) col.getChildAt(0)).setText(f.name);
                ((TextView) col.getChildAt(0)).setTextColor(ex ? Ui.TEXT3 : Ui.TEXT);
                ((TextView) col.getChildAt(1)).setText(Library.count(f.count) + " · " + Library.prettyPath(f.path));
                ((CheckBox) row.getChildAt(2)).setChecked(ex);
                ImageView ic = (ImageView) row.getChildAt(0);
                ic.setImageResource(ex ? R.drawable.ic_block : R.drawable.ic_folder);
                ic.setColorFilter(ex ? Ui.DANGER : Ui.ACCENT);
                ic.setBackground(Ui.rounded(ex ? Ui.alpha(Ui.DANGER, 0x2E) : Ui.ACCENT_SOFT, dp(12)));
                return cv;
            }
        };
        lv.setAdapter(ad);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                String path = folders.get(pos).path;
                if (excluded.contains(path)) {
                    excluded.remove(path);
                } else if (Library.inFolders(path, excluded)) {
                    Toast.makeText(SettingsActivity.this, "È dentro una cartella già esclusa", Toast.LENGTH_SHORT).show();
                } else {
                    excluded.add(path);
                }
                ad.notifyDataSetChanged();
            }
        });
        box.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));

        FrameLayout foot = new FrameLayout(this);
        foot.setPadding(dp(16), dp(10), dp(16), dp(14));
        final Dialog d = Ui.sheet(this, box, true);
        TextView done = Ui.pill(this, "Fatto", R.drawable.ic_check, true);
        done.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
            }
        });
        foot.addView(done, new FrameLayout.LayoutParams(-1, -2));
        box.addView(foot);
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface di) {
                if (!excluded.equals(original)) {
                    Settings.setExcludedFolders(SettingsActivity.this, excluded);
                    changed();
                    build();
                }
            }
        });
        if (folders.isEmpty()) {
            Toast.makeText(this, "Nessuna cartella con musica trovata", Toast.LENGTH_SHORT).show();
            return;
        }
        d.show();
    }
}
