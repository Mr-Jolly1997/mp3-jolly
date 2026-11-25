package it.lettore.mp3;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;

/** Equalizzatore con curva trascinabile, preset, bassi ed effetto surround. */
public class EqualizerActivity extends Activity {
    private PlaybackService svc;
    private AudioFx fx;
    private Switch onOff;
    private LinearLayout content, chips;
    private final ArrayList<TextView> chipViews = new ArrayList<TextView>();
    private Views.EqView eqView;
    private LinearLayout eqCard, fxCard;
    private SeekBar bassBar, virtBar;
    private TextView bassVal, virtVal;
    private boolean binding;
    private int myGen;

    private final ServiceConnection conn = new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder b) {
            svc = ((PlaybackService.LocalBinder) b).get();
            fx = svc.fx();
            build();
        }

        public void onServiceDisconnected(ComponentName n) {
            svc = null;
            fx = null;
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
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

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
        TextView t = Ui.text(this, "Equalizzatore", 20, Ui.TEXT, Ui.MEDIUM);
        t.setPadding(dp(6), 0, 0, 0);
        top.addView(t, Ui.lpw(1f));
        onOff = new Switch(this);
        Ui.tint(onOff);
        onOff.setEnabled(false);
        top.addView(onOff);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(64)));

        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(24));
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        Ui.decorate(this, root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (myGen != Ui.themeGen) recreate();
    }

    @Override
    protected void onStart() {
        super.onStart();
        binding = bindService(new Intent(this, PlaybackService.class), conn, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        if (fx != null) fx.save();
        if (binding) unbindService(conn);
        binding = false;
        svc = null;
        fx = null;
        super.onStop();
    }

    @Override
    public void finish() {
        super.finish();
        Compat.legacyAnim(this, R.anim.fade_in_short, R.anim.slide_out_right);
    }

    private void build() {
        content.removeAllViews();
        chipViews.clear();
        if (fx == null || !fx.available()) {
            TextView msg = Ui.text(this, "Questo telefono non mette a disposizione l'equalizzatore per le app.", 15, Ui.TEXT2, Ui.REGULAR);
            msg.setSingleLine(false);
            msg.setPadding(dp(8), dp(24), dp(8), 0);
            content.addView(msg);
            onOff.setEnabled(false);
            return;
        }
        onOff.setOnCheckedChangeListener(null);
        onOff.setChecked(fx.enabled);
        onOff.setEnabled(true);
        onOff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                if (fx == null) return;
                fx.setEnabled(on);
                updateEnabledLook();
            }
        });

        if (fx.hasEq()) {
            content.addView(sectionLabel("Stile"));
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
            chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setPadding(0, dp(4), 0, dp(12));
            hs.addView(chips);
            content.addView(hs);
            addChip("Personalizzato", -1);
            for (int i = 0; i < fx.presets.length; i++) addChip(fx.presets[i], i);

            eqCard = card();
            eqView = new Views.EqView(this);
            eqView.setData(fx.levels, fx.freqs, fx.minLevel, fx.maxLevel);
            eqView.setListener(new Views.EqView.Listener() {
                public void onBand(int band, short level) {
                    if (fx == null) return;
                    ensureOn();
                    boolean wasPreset = fx.preset >= 0;
                    fx.setBand(band, level);
                    if (wasPreset) updateChips();
                }

                public void onTouchEnd() {
                    if (fx != null) fx.save();
                }
            });
            eqCard.addView(eqView, new LinearLayout.LayoutParams(-1, dp(290)));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(6), 0, 0);
            TextView hint = Ui.text(this, "Trascina i punti su e giù", 13, Ui.TEXT3, Ui.REGULAR);
            row.addView(hint, Ui.lpw(1f));
            TextView reset = Ui.pill(this, "Azzera", R.drawable.ic_refresh, false);
            reset.setTextSize(13);
            reset.setPadding(dp(12), dp(7), dp(14), dp(7));
            reset.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (fx == null) return;
                    fx.reset();
                    eqView.setData(fx.levels, fx.freqs, fx.minLevel, fx.maxLevel);
                    if (bassBar != null) bassBar.setProgress(0);
                    if (virtBar != null) virtBar.setProgress(0);
                    updateChips();
                }
            });
            row.addView(reset);
            eqCard.addView(row);
            content.addView(eqCard);
        }

        if (fx.hasBass() || fx.hasVirt()) {
            content.addView(sectionLabel("Effetti"));
            fxCard = card();
            if (fx.hasBass()) {
                bassVal = Ui.text(this, "", 14, Ui.ACCENT, Ui.MEDIUM);
                bassBar = slider(fxCard, "Potenzia bassi", null, bassVal, fx.bassStrength, true);
            }
            if (fx.hasVirt()) {
                virtVal = Ui.text(this, "", 14, Ui.ACCENT, Ui.MEDIUM);
                virtBar = slider(fxCard, "Effetto spaziale", "Si sente meglio in cuffia", virtVal, fx.virtStrength, false);
            }
            content.addView(fxCard);
        }

        TextView note = Ui.text(this, "Le regolazioni valgono solo per la musica di questa app e restano salvate.", 13, Ui.TEXT3, Ui.REGULAR);
        note.setSingleLine(false);
        note.setPadding(dp(6), dp(18), dp(6), 0);
        content.addView(note);

        updateChips();
        updateEnabledLook();
    }

    private TextView sectionLabel(String s) {
        TextView t = Ui.text(this, s.toUpperCase(java.util.Locale.ITALIAN), 13, Ui.ACCENT, Ui.MEDIUM);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(6), dp(16), 0, dp(8));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.rounded(Ui.SURFACE, dp(20)));
        c.setPadding(dp(12), dp(14), dp(12), dp(14));
        return c;
    }

    private void addChip(String name, final int preset) {
        TextView t = Ui.text(this, name, 14, Ui.TEXT2, Ui.MEDIUM);
        t.setPadding(dp(16), dp(9), dp(16), dp(9));
        Ui.pressable(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        t.setTag(preset);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (fx == null) return;
                ensureOn();
                if (preset >= 0) {
                    fx.usePreset(preset);
                } else if (fx.preset >= 0) {
                    fx.preset = -1;
                    fx.save();
                }
                eqView.setData(fx.levels, fx.freqs, fx.minLevel, fx.maxLevel);
                updateChips();
            }
        });
        chips.addView(t, lp);
        chipViews.add(t);
    }

    private void updateChips() {
        if (fx == null) return;
        for (TextView t : chipViews) {
            boolean sel = (Integer) t.getTag() == fx.preset;
            t.setTextColor(sel ? Ui.TAB_SEL_TEXT : Ui.TAB_TEXT);
            t.setBackground(Ui.rounded(sel ? Ui.TAB_SEL_BG : Ui.TAB_BG, dp(20)));
        }
    }

    private SeekBar slider(LinearLayout parent, String title, String sub, final TextView val, int value, final boolean isBass) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(4), dp(8), 0);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, title, 16, Ui.TEXT, Ui.MEDIUM));
        if (sub != null) col.addView(Ui.text(this, sub, 13, Ui.TEXT3, Ui.REGULAR));
        row.addView(col, Ui.lpw(1f));
        row.addView(val);
        parent.addView(row);
        SeekBar sb = new SeekBar(this);
        Ui.tint(sb, Ui.ACCENT);
        sb.setMax(1000);
        sb.setProgress(value);
        sb.setPadding(dp(14), dp(12), dp(14), dp(14));
        val.setText(percent(value));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                val.setText(percent(p));
                if (!user || fx == null) return;
                if (p > 0) ensureOn();
                if (isBass) fx.setBass(p);
                else fx.setVirt(p);
            }

            public void onStartTrackingTouch(SeekBar s) {}

            public void onStopTrackingTouch(SeekBar s) {
                if (fx != null) fx.save();
            }
        });
        parent.addView(sb);
        return sb;
    }

    private static String percent(int v) {
        return v == 0 ? "No" : Math.round(v / 10f) + "%";
    }

    /** Toccare un comando accende l'equalizzatore, se era spento. */
    private void ensureOn() {
        if (fx != null && !fx.enabled) onOff.setChecked(true);
    }

    private void updateEnabledLook() {
        boolean on = fx != null && fx.enabled;
        if (eqView != null) eqView.setActive(on);
        if (eqCard != null) eqCard.setAlpha(on ? 1f : 0.65f);
        if (fxCard != null) fxCard.setAlpha(on ? 1f : 0.65f);
    }
}
