package it.lettore.mp3;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;

import java.util.Locale;

/** Equalizzatore, bassi e surround applicati alla musica dell'app. */
final class AudioFx {
    private final SharedPreferences p;
    private Equalizer eq;
    private BassBoost bass;
    private Virtualizer virt;

    short bands;
    short minLevel = -1500, maxLevel = 1500;
    int[] freqs = new int[0];
    String[] presets = new String[0];

    boolean enabled;
    int preset = -1;
    short[] levels = new short[0];
    int bassStrength, virtStrength;

    AudioFx(Context c, int session) {
        p = c.getSharedPreferences("fx", Context.MODE_PRIVATE);
        try {
            eq = new Equalizer(0, session);
            bands = eq.getNumberOfBands();
            short[] r = eq.getBandLevelRange();
            minLevel = r[0];
            maxLevel = r[1];
            freqs = new int[bands];
            for (short i = 0; i < bands; i++) freqs[i] = eq.getCenterFreq(i);
            short np = eq.getNumberOfPresets();
            presets = new String[np];
            for (short i = 0; i < np; i++) presets[i] = italian(eq.getPresetName(i));
            levels = new short[bands];
        } catch (Throwable t) {
            eq = null;
            bands = 0;
        }
        try {
            bass = new BassBoost(0, session);
            if (!bass.getStrengthSupported()) {
                bass.release();
                bass = null;
            }
        } catch (Throwable t) {
            bass = null;
        }
        try {
            virt = new Virtualizer(0, session);
            if (!virt.getStrengthSupported()) {
                virt.release();
                virt = null;
            }
        } catch (Throwable t) {
            virt = null;
        }

        enabled = p.getBoolean("on", false);
        preset = p.getInt("preset", -1);
        bassStrength = p.getInt("bass", 0);
        virtStrength = p.getInt("virt", 0);
        String saved = p.getString("levels", "");
        String[] parts = saved.length() == 0 ? new String[0] : saved.split(",");
        if (parts.length == bands) {
            for (int i = 0; i < bands; i++) {
                try {
                    levels[i] = clamp(Short.parseShort(parts[i]));
                } catch (NumberFormatException e) {
                    levels[i] = 0;
                }
            }
        }
        if (preset >= presets.length) preset = -1;
        apply();
    }

    boolean hasEq() { return eq != null && bands > 0; }
    boolean hasBass() { return bass != null; }
    boolean hasVirt() { return virt != null; }
    boolean available() { return hasEq() || hasBass() || hasVirt(); }

    short clamp(int v) {
        return (short) Math.max(minLevel, Math.min(maxLevel, v));
    }

    void apply() {
        try {
            if (eq != null) {
                if (preset >= 0) {
                    eq.usePreset((short) preset);
                    for (short i = 0; i < bands; i++) levels[i] = eq.getBandLevel(i);
                } else {
                    for (short i = 0; i < bands; i++) eq.setBandLevel(i, levels[i]);
                }
                eq.setEnabled(enabled);
            }
        } catch (Throwable t) {
            // ignora
        }
        applyBass();
        applyVirt();
    }

    private void applyBass() {
        try {
            if (bass != null) {
                bass.setStrength((short) bassStrength);
                bass.setEnabled(enabled && bassStrength > 0);
            }
        } catch (Throwable t) {
            // ignora
        }
    }

    private void applyVirt() {
        try {
            if (virt != null) {
                virt.setStrength((short) virtStrength);
                virt.setEnabled(enabled && virtStrength > 0);
            }
        } catch (Throwable t) {
            // ignora
        }
    }

    void setEnabled(boolean e) {
        enabled = e;
        apply();
        save();
    }

    void usePreset(int i) {
        preset = i;
        apply();
        save();
    }

    void setBand(int band, short level) {
        if (band < 0 || band >= bands) return;
        preset = -1;
        levels[band] = clamp(level);
        try {
            eq.setBandLevel((short) band, levels[band]);
        } catch (Throwable t) {
            // ignora
        }
    }

    void setBass(int s) {
        bassStrength = Math.max(0, Math.min(1000, s));
        applyBass();
    }

    void setVirt(int s) {
        virtStrength = Math.max(0, Math.min(1000, s));
        applyVirt();
    }

    void reset() {
        preset = -1;
        for (int i = 0; i < levels.length; i++) levels[i] = 0;
        bassStrength = 0;
        virtStrength = 0;
        apply();
        save();
    }

    void save() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(levels[i]);
        }
        p.edit().putBoolean("on", enabled).putInt("preset", preset).putInt("bass", bassStrength)
                .putInt("virt", virtStrength).putString("levels", sb.toString()).apply();
    }

    void release() {
        try { if (eq != null) eq.release(); } catch (Throwable t) { /* ignora */ }
        try { if (bass != null) bass.release(); } catch (Throwable t) { /* ignora */ }
        try { if (virt != null) virt.release(); } catch (Throwable t) { /* ignora */ }
        eq = null;
        bass = null;
        virt = null;
    }

    static String freqLabel(int milliHz) {
        int hz = milliHz / 1000;
        if (hz >= 1000) {
            float k = hz / 1000f;
            if (Math.abs(k - Math.round(k)) < 0.05f) return Math.round(k) + "k";
            return String.format(Locale.ITALIAN, "%.1fk", k);
        }
        return String.valueOf(hz);
    }

    static String dbLabel(short level) {
        int db = Math.round(level / 100f);
        return db > 0 ? "+" + db : String.valueOf(db);
    }

    private static String italian(String n) {
        if (n == null) return "";
        String l = n.toLowerCase(Locale.ROOT);
        if (l.equals("normal")) return "Normale";
        if (l.equals("classical")) return "Classica";
        if (l.equals("flat")) return "Piatto";
        if (l.equals("heavy metal")) return "Metal";
        if (l.equals("hip hop")) return "Hip hop";
        return n;
    }
}
