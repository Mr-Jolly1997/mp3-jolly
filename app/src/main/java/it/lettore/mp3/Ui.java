package it.lettore.mp3;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.StateListAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Colori, misure e piccoli aiuti per costruire l'interfaccia.
 * Ogni colore dell'app è un "token" personalizzabile, separatamente per tema chiaro e scuro.
 */
final class Ui {

    // ------------------------------------------------------------ token colore personalizzabili

    static final String[] KEYS = {
            "bg", "surface", "text", "text2", "icon", "accent",
            "tabSelBg", "tabSelText", "tabBg", "tabText", "playing",
            "miniBg", "miniText", "miniPlayBg", "miniPlayIcon", "miniIcons",
            "npTitle", "npArtist", "npProgress", "npPlayBg", "npPlayIcon", "npSkip", "npToggle", "npHeart", "npTools"
    };
    static final String[] LABELS = {
            "Sfondo", "Riquadri, menu e finestre", "Testi principali", "Testi secondari", "Icone", "Colore di evidenziazione",
            "Scheda selezionata", "Testo della scheda selezionata", "Schede non selezionate", "Testo delle schede non selezionate", "Brano in ascolto",
            "Sfondo", "Testi", "Pulsante play", "Simbolo del pulsante play", "Icone coda e avanti",
            "Titolo del brano", "Artista", "Barra di avanzamento", "Pulsante play", "Simbolo del pulsante play",
            "Avanti e indietro", "Casuale e ripeti attivi", "Cuore dei preferiti", "Timer, velocità, equalizzatore, condividi"
    };
    static final String[] GROUP_NAMES = {"Generale", "Libreria", "Mini player", "In riproduzione"};
    static final int[] GROUP_START = {0, 6, 11, 16};

    static final int T_BG = 0, T_SURFACE = 1, T_TEXT = 2, T_TEXT2 = 3, T_ICON = 4, T_ACCENT = 5,
            T_TAB_SEL_BG = 6, T_TAB_SEL_TEXT = 7, T_TAB_BG = 8, T_TAB_TEXT = 9, T_PLAYING = 10,
            T_MINI_BG = 11, T_MINI_TEXT = 12, T_MINI_PLAY_BG = 13, T_MINI_PLAY_ICON = 14, T_MINI_ICONS = 15,
            T_NP_TITLE = 16, T_NP_ARTIST = 17, T_NP_PROGRESS = 18, T_NP_PLAY_BG = 19, T_NP_PLAY_ICON = 20,
            T_NP_SKIP = 21, T_NP_TOGGLE = 22, T_NP_HEART = 23, T_NP_TOOLS = 24;

    static final int[] cur = new int[KEYS.length];

    static int BG, SURFACE, TEXT, TEXT2, ICON, ACCENT;
    static int TAB_SEL_BG, TAB_SEL_TEXT, TAB_BG, TAB_TEXT, PLAYING;
    static int MINI_BG, MINI_TEXT, MINI_PLAY_BG, MINI_PLAY_ICON, MINI_ICONS;
    static int NP_TITLE, NP_ARTIST, NP_PROGRESS, NP_PLAY_BG, NP_PLAY_ICON, NP_SKIP, NP_TOGGLE, NP_HEART, NP_TOOLS;
    // colori derivati (non personalizzabili direttamente)
    static int SURFACE2, SURFACE3, TEXT3, DIVIDER, ON_ACCENT, ACCENT_SOFT, DANGER;

    static boolean dark = true;
    static boolean coverBg = true;
    /** Cambia a ogni modifica di tema/colori: le schermate aperte si ricreano. */
    static volatile int themeGen;

    // ------------------------------------------------------------ profili colore predefiniti
    // Ordine dei valori = ordine di KEYS.
    static final String[] PROFILE_NAMES = {"Ambra e turchese", "Lavanda e pesca", "Salvia e albicocca", "Quattro colori", "Magenta e ciano"};
    static final String[] PROFILE_DESC = {
            "Turchese, terracotta e ambra su grafite",
            "Lavanda, pesca e menta, pastello",
            "Salvia, albicocca e cielo, pastello",
            "Blu, rosso, giallo e verde vivaci: un colore per ogni funzione",
            "Magenta, ciano e lime brillanti su blu notte"
    };
    static final int[][] PROFILE_DARK = {
            {0xFF2E2E36, 0xFF1C1C22, 0xFF6FA6A0, 0xFF944F3A, 0xFFCC871F, 0xFF4DB6AC, 0xFFCC5835, 0xFF448047, 0xFF24242B, 0xFF9C9CA8, 0xFF26A699, 0xFF24242B, 0xFFF3F3F6, 0xFF4DB6AC, 0xFF0D201E, 0xFFE4E4EA, 0xFFF3F3F6, 0xFF9C9CA8, 0xFFF3F3F6, 0xFF4DB6AC, 0xFF0D201E, 0xFFC25432, 0xFF4DB6AC, 0xFFDB5127, 0xFFDE9221},
            {0xFF25232F, 0xFF1D1B26, 0xFFF1EAF7, 0xFFB4A9C9, 0xFFF5B7A1, 0xFFC6B0F2, 0xFFF5B7A1, 0xFF3D2420, 0xFF322F40, 0xFFB4A9C9, 0xFF9EDFCF, 0xFF322F40, 0xFFF1EAF7, 0xFFC6B0F2, 0xFF2A1F45, 0xFFF5B7A1, 0xFFF1EAF7, 0xFFB4A9C9, 0xFFF5B7A1, 0xFFF5B7A1, 0xFF3D2420, 0xFFC6B0F2, 0xFF9EDFCF, 0xFFFF8FAB, 0xFFB4A9C9},
            {0xFF1E2A29, 0xFF172120, 0xFFEAF4EF, 0xFFA2BDB3, 0xFFF6C49E, 0xFF9ED8C4, 0xFFF6C49E, 0xFF3B2A1C, 0xFF293837, 0xFFA2BDB3, 0xFFA8C7F0, 0xFF293837, 0xFFEAF4EF, 0xFF9ED8C4, 0xFF163A30, 0xFFF6C49E, 0xFFEAF4EF, 0xFFA2BDB3, 0xFF9ED8C4, 0xFFF4A196, 0xFF4A1F1A, 0xFFF6C49E, 0xFFA8C7F0, 0xFFF28B9A, 0xFFA2BDB3},
            {0xFF202124, 0xFF292A2D, 0xFFE8EAED, 0xFF9AA0A6, 0xFFFDD663, 0xFF8AB4F8, 0xFF8AB4F8, 0xFF062E6F, 0xFF35363A, 0xFFBDC1C6, 0xFF81C995, 0xFF35363A, 0xFFE8EAED, 0xFF8AB4F8, 0xFF062E6F, 0xFFFDD663, 0xFFE8EAED, 0xFF9AA0A6, 0xFF8AB4F8, 0xFF8AB4F8, 0xFF062E6F, 0xFFF28B82, 0xFF81C995, 0xFFF28B82, 0xFFFDD663},
            {0xFF10131C, 0xFF1A1F2E, 0xFFE6E8F2, 0xFFA3AAC2, 0xFF7DD3FC, 0xFFFF5CA8, 0xFFFF5CA8, 0xFF3D0021, 0xFF22283A, 0xFFA3AAC2, 0xFFB5F06A, 0xFF22283A, 0xFFE6E8F2, 0xFFFF5CA8, 0xFF3D0021, 0xFF7DD3FC, 0xFFFFFFFF, 0xFFA3AAC2, 0xFF7DD3FC, 0xFFFF5CA8, 0xFF3D0021, 0xFF7DD3FC, 0xFFB5F06A, 0xFFFF5CA8, 0xFFC4B5FD},
    };
    static final int[][] PROFILE_LIGHT = {
            {0xFFF4F1EC, 0xFFFFFFFF, 0xFF2E6E67, 0xFF8E4B37, 0xFFB5721A, 0xFF1F9A8E, 0xFFCC5835, 0xFF448047, 0xFFE6E1DA, 0xFF6F6A64, 0xFF1F9A8E, 0xFFFFFFFF, 0xFF2B2B31, 0xFF1F9A8E, 0xFFFFFFFF, 0xFFB5721A, 0xFF2B2B31, 0xFF6F6A64, 0xFF2E6E67, 0xFF1F9A8E, 0xFFFFFFFF, 0xFFC25432, 0xFF1F9A8E, 0xFFDB5127, 0xFFC27F17},
            {0xFFFAF6FF, 0xFFFFFFFF, 0xFF2E2640, 0xFF776C8E, 0xFFC8705A, 0xFF8B69D6, 0xFFF6BBA8, 0xFF4A2A22, 0xFFEEE6F8, 0xFF776C8E, 0xFF2F9C84, 0xFFFFFFFF, 0xFF2E2640, 0xFF8B69D6, 0xFFFFFFFF, 0xFFC8705A, 0xFF2E2640, 0xFF776C8E, 0xFF8B69D6, 0xFFF6BBA8, 0xFF4A2A22, 0xFF8B69D6, 0xFF2F9C84, 0xFFE05A84, 0xFF776C8E},
            {0xFFF3F7F4, 0xFFFFFFFF, 0xFF1E3330, 0xFF5D746F, 0xFFCF7A49, 0xFF3D9B7F, 0xFFF6C49E, 0xFF4A2E18, 0xFFE3EDE8, 0xFF5D746F, 0xFF4A78B6, 0xFFFFFFFF, 0xFF1E3330, 0xFF3D9B7F, 0xFFFFFFFF, 0xFFCF7A49, 0xFF1E3330, 0xFF5D746F, 0xFF3D9B7F, 0xFFF29A8E, 0xFF4A1F1A, 0xFFCF7A49, 0xFF4A78B6, 0xFFE0607A, 0xFF5D746F},
            {0xFFF8F9FA, 0xFFFFFFFF, 0xFF202124, 0xFF5F6368, 0xFF1A73E8, 0xFF1A73E8, 0xFF1A73E8, 0xFFFFFFFF, 0xFFE8EAED, 0xFF5F6368, 0xFF1E8E3E, 0xFFFFFFFF, 0xFF202124, 0xFF1A73E8, 0xFFFFFFFF, 0xFFE37400, 0xFF202124, 0xFF5F6368, 0xFF1A73E8, 0xFF1A73E8, 0xFFFFFFFF, 0xFFD93025, 0xFF1E8E3E, 0xFFD93025, 0xFFE37400},
            {0xFFF7F5FF, 0xFFFFFFFF, 0xFF1A1B2E, 0xFF5D6180, 0xFF0284C7, 0xFFD6247A, 0xFFD6247A, 0xFFFFFFFF, 0xFFECE9F7, 0xFF5D6180, 0xFF4D8A12, 0xFFFFFFFF, 0xFF1A1B2E, 0xFFD6247A, 0xFFFFFFFF, 0xFF0284C7, 0xFF1A1B2E, 0xFF5D6180, 0xFF0284C7, 0xFFD6247A, 0xFFFFFFFF, 0xFF0284C7, 0xFF4D8A12, 0xFFD6247A, 0xFF7C3AED},
    };
    static int profile = 0;

    /** Tavolozza rapida del selettore colori. */
    static final int[] PALETTE = {
            0xFFFFFFFF, 0xFFE0E0E0, 0xFF9E9E9E, 0xFF616161, 0xFF2D2D35, 0xFF18181C, 0xFF0E0E11, 0xFF000000, 0xFFF5F5F7,
            0xFFEF5350, 0xFFEC407A, 0xFFAB47BC, 0xFF7E57C2, 0xFF5C6BC0, 0xFF42A5F5, 0xFF26C6DA, 0xFF26A69A, 0xFF4DB6AC,
            0xFF66BB6A, 0xFF9CCC65, 0xFFD4E157, 0xFFFFEE58, 0xFFFFCA28, 0xFFFFA726, 0xFFFF7043, 0xFF8D6E63, 0xFF78909C
    };

    /** Colori "tessera" per segnaposto di copertine, generi, artisti. */
    private static final int[] TILES = {0xFF3F51B5, 0xFF00897B, 0xFFC62828, 0xFF8E24AA, 0xFFEF6C00, 0xFF2E7D32,
            0xFF1565C0, 0xFFAD1457, 0xFF6D4C41, 0xFF546E7A, 0xFF5E35B1, 0xFF00838F};

    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);

    private Ui() {}

    static void bump() {
        themeGen++;
    }

    static boolean systemDark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Colori predefiniti: quelli del profilo scelto, per tema scuro o chiaro. */
    static int[] defaults(boolean dark, int prof) {
        int[][] set = dark ? PROFILE_DARK : PROFILE_LIGHT;
        return set[prof < 0 || prof >= set.length ? 0 : prof].clone();
    }

    static void init(Context c) {
        int mode = Settings.themeMode(c);
        dark = mode == Settings.THEME_DARK || (mode == Settings.THEME_SYSTEM && systemDark(c));
        Settings.migrate(c);
        int pr = Settings.profile(c);
        profile = pr < 0 || pr >= PROFILE_NAMES.length ? 0 : pr;
        coverBg = Settings.coverBg(c);
        int[] def = defaults(dark, profile);
        SharedPreferences p = Settings.colors(c);
        String pre = dark ? "d." : "l.";
        for (int i = 0; i < KEYS.length; i++) cur[i] = p.getInt(pre + KEYS[i], def[i]);
        assign();
    }

    /** Copia i valori correnti nei campi usati dall'interfaccia. */
    static void assign() {
        BG = cur[T_BG]; SURFACE = cur[T_SURFACE]; TEXT = cur[T_TEXT]; TEXT2 = cur[T_TEXT2]; ICON = cur[T_ICON];
        ACCENT = cur[T_ACCENT];
        TAB_SEL_BG = cur[T_TAB_SEL_BG]; TAB_SEL_TEXT = cur[T_TAB_SEL_TEXT]; TAB_BG = cur[T_TAB_BG];
        TAB_TEXT = cur[T_TAB_TEXT]; PLAYING = cur[T_PLAYING];
        MINI_BG = cur[T_MINI_BG]; MINI_TEXT = cur[T_MINI_TEXT]; MINI_PLAY_BG = cur[T_MINI_PLAY_BG];
        MINI_PLAY_ICON = cur[T_MINI_PLAY_ICON]; MINI_ICONS = cur[T_MINI_ICONS];
        NP_TITLE = cur[T_NP_TITLE]; NP_ARTIST = cur[T_NP_ARTIST]; NP_PROGRESS = cur[T_NP_PROGRESS];
        NP_PLAY_BG = cur[T_NP_PLAY_BG]; NP_PLAY_ICON = cur[T_NP_PLAY_ICON]; NP_SKIP = cur[T_NP_SKIP];
        NP_TOGGLE = cur[T_NP_TOGGLE]; NP_HEART = cur[T_NP_HEART]; NP_TOOLS = cur[T_NP_TOOLS];

        SURFACE2 = blend(SURFACE, TEXT, dark ? 0.07f : 0.05f);
        SURFACE3 = blend(SURFACE, TEXT, dark ? 0.12f : 0.09f);
        TEXT3 = blend(TEXT2, BG, 0.3f);
        DIVIDER = (TEXT & 0x00FFFFFF) | 0x1A000000;
        ON_ACCENT = contrast(ACCENT);
        ACCENT_SOFT = (ACCENT & 0x00FFFFFF) | 0x2E000000;
        DANGER = dark ? 0xFFE57373 : 0xFFD32F2F;
    }

    /** Da chiamare prima di super.onCreate() in ogni schermata. */
    static int applyTheme(Activity a) {
        init(a);
        a.setTheme(dark ? R.style.Theme_Lettore_Dark : R.style.Theme_Lettore_Light);
        return themeGen;
    }

    /** Dopo setContentView: sfondo della finestra, edge-to-edge e colore delle icone di sistema. */
    static void decorate(Activity a, View root) {
        a.getWindow().setBackgroundDrawable(new ColorDrawable(BG));
        Compat.edgeToEdge(a, root);
        Compat.systemBars(a, isLight(BG), isLight(BG), BG);
    }

    static double luminance(int c) {
        return (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255.0;
    }

    static boolean isLight(int c) {
        return luminance(c) > 0.6;
    }

    /** Colore leggibile sopra c (bianco oppure una versione scurissima di c). */
    static int contrast(int c) {
        return luminance(c) > 0.55 ? blend(c, 0xFF000000, 0.82f) : 0xFFFFFFFF;
    }

    /** 1×, 1,25×, 0,75×… */
    static String speed(float v) {
        String s = String.format(Locale.ITALIAN, "%.2f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(",")) s = s.substring(0, s.length() - 1);
        return s + "×";
    }

    static int blend(int a, int b, float t) {
        float u = 1f - t;
        return Color.argb(255,
                (int) (Color.red(a) * u + Color.red(b) * t),
                (int) (Color.green(a) * u + Color.green(b) * t),
                (int) (Color.blue(a) * u + Color.blue(b) * t));
    }

    static int alpha(int c, int a) {
        return (c & 0x00FFFFFF) | (a << 24);
    }

    static int tileColor(String key) {
        int h = key == null ? 0 : key.hashCode();
        return TILES[(h & 0x7fffffff) % TILES.length];
    }

    static String hex(int c) {
        return String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF);
    }

    // ------------------------------------------------------------ misure e testi

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static String time(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, ss = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, ss)
                : String.format(Locale.ROOT, "%d:%02d", m, ss);
    }

    static String longTime(long ms) {
        long min = Math.round(ms / 60000.0);
        if (min < 60) return min + " min";
        long h = min / 60, m = min % 60;
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT);
    }

    static String sectionOf(String s) {
        String n = norm(s).trim();
        if (n.startsWith("the ") && n.length() > 4) n = n.substring(4);
        if (n.length() == 0) return "#";
        char c = Character.toUpperCase(n.charAt(0));
        return c >= 'A' && c <= 'Z' ? String.valueOf(c) : "#";
    }

    static String initial(String s) {
        String n = s == null ? "" : s.trim();
        if (n.toLowerCase(Locale.ROOT).startsWith("the ") && n.length() > 4) n = n.substring(4);
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.isLetterOrDigit(c)) return String.valueOf(Character.toUpperCase(c));
        }
        return "♪";
    }

    // ------------------------------------------------------------ disegni e viste

    static Drawable ripple(Context c, boolean borderless) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(borderless ? android.R.attr.selectableItemBackgroundBorderless
                : android.R.attr.selectableItemBackground, tv, true);
        return c.getDrawable(tv.resourceId);
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable roundedTop(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadii(new float[]{radiusPx, radiusPx, radiusPx, radiusPx, 0, 0, 0, 0});
        return d;
    }

    static GradientDrawable outline(int strokeColor, int strokePx, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0);
        d.setStroke(strokePx, strokeColor);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    static ImageView icon(Context c, int res, int tint, int sizeDp, int padDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setColorFilter(tint);
        int p = dp(c, padDp);
        v.setPadding(p, p, p, p);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        v.setBackground(ripple(c, true));
        v.setClickable(true);
        return v;
    }

    static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTypeface(tf == null ? REGULAR : tf);
        return t;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        return text(c, s, sp, color, bold ? BOLD : REGULAR);
    }

    /** Pulsante a pillola: pieno (colore di evidenziazione) o tonale. */
    static TextView pill(Context c, String label, int iconRes, boolean filled) {
        return pill(c, label, iconRes, filled ? ACCENT : SURFACE3, filled ? ON_ACCENT : TEXT);
    }

    static TextView pill(Context c, String label, int iconRes, int bg, int fg) {
        TextView t = text(c, label, 15, fg, MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 18), dp(c, 11), dp(c, 20), dp(c, 11));
        t.setBackground(rounded(bg, dp(c, 24)));
        if (iconRes != 0) {
            Drawable d = c.getDrawable(iconRes).mutate();
            int s = dp(c, 20);
            d.setBounds(0, 0, s, s);
            d.setTint(fg);
            t.setCompoundDrawables(d, null, null, null);
            t.setCompoundDrawablePadding(dp(c, 8));
        }
        pressable(t);
        t.setClickable(true);
        return t;
    }

    static void pressable(View v) {
        StateListAnimator sla = new StateListAnimator();
        sla.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat("scaleX", 0.94f), PropertyValuesHolder.ofFloat("scaleY", 0.94f))
                .setDuration(90));
        sla.addState(new int[]{}, ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat("scaleX", 1f), PropertyValuesHolder.ofFloat("scaleY", 1f))
                .setDuration(140));
        v.setStateListAnimator(sla);
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lpw(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    // ------------------------------------------------------------ controlli colorati con i token

    static void tint(Switch sw) {
        int[][] st = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(st, new int[]{ACCENT, dark ? 0xFFBDBDC6 : 0xFFFAFAFA}));
        sw.setTrackTintList(new ColorStateList(st, new int[]{alpha(ACCENT, 0x80), alpha(TEXT2, 0x50)}));
    }

    static void tint(CheckBox cb) {
        int[][] st = {{android.R.attr.state_checked}, {}};
        cb.setButtonTintList(new ColorStateList(st, new int[]{ACCENT, TEXT2}));
    }

    static void tint(SeekBar sb, int color) {
        sb.setProgressTintList(ColorStateList.valueOf(color));
        sb.setThumbTintList(ColorStateList.valueOf(color));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(alpha(TEXT, 0x40)));
    }

    static EditText field(Context c, String hint, String initial) {
        EditText et = new EditText(c);
        et.setSingleLine(true);
        et.setHint(hint);
        et.setTextColor(TEXT);
        et.setHintTextColor(TEXT3);
        et.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (initial != null) {
            et.setText(initial);
            et.setSelection(initial.length());
        }
        return et;
    }

    // ------------------------------------------------------------ finestre

    private static int dialogTheme() {
        return dark ? android.R.style.Theme_Material_Dialog_NoActionBar : android.R.style.Theme_Material_Light_Dialog_NoActionBar;
    }

    /** Finestra a scheda che sale dal basso, con angoli arrotondati. */
    static Dialog sheet(Context c, View content, boolean tall) {
        return sheet(c, content, tall, 0.5f);
    }

    static Dialog sheet(Context c, View content, boolean tall, float dim) {
        Dialog d = new Dialog(c, dialogTheme());
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(roundedTop(SURFACE, dp(c, 22)));
        View handle = new View(c);
        handle.setBackground(rounded(alpha(TEXT, 0x40), dp(c, 3)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(c, 36), dp(c, 4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.topMargin = dp(c, 10);
        hp.bottomMargin = dp(c, 6);
        box.addView(handle, hp);
        box.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                tall ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT, tall ? 1f : 0f));
        d.setContentView(box);
        d.setCanceledOnTouchOutside(true);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            int h = tall ? (int) (c.getResources().getDisplayMetrics().heightPixels * 0.75f)
                    : ViewGroup.LayoutParams.WRAP_CONTENT;
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, h);
            w.setGravity(Gravity.BOTTOM);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
            if (dim > 0) {
                w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                w.setDimAmount(dim);
            } else {
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
        }
        return d;
    }

    /** Finestra di dialogo con i colori dell'app. onOk può essere null (solo "OK"). */
    static Dialog dialog(Context c, String title, CharSequence message, View content,
                         String cancel, String ok, final Runnable onOk) {
        final Dialog d = new Dialog(c, dialogTheme());
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(rounded(SURFACE, dp(c, 24)));
        box.setPadding(dp(c, 24), dp(c, 22), dp(c, 16), dp(c, 10));
        TextView t = text(c, title, 20, TEXT, MEDIUM);
        t.setSingleLine(false);
        t.setPadding(0, 0, dp(c, 8), dp(c, 10));
        box.addView(t);
        if (message != null) {
            TextView m = text(c, message.toString(), 15, TEXT2, REGULAR);
            m.setSingleLine(false);
            m.setLineSpacing(0, 1.15f);
            m.setTextIsSelectable(false);
            m.setPadding(0, 0, dp(c, 8), dp(c, 6));
            box.addView(m);
        }
        if (content != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.rightMargin = dp(c, 8);
            box.addView(content, lp);
        }
        LinearLayout btns = new LinearLayout(c);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.END);
        btns.setPadding(0, dp(c, 14), 0, 0);
        if (cancel != null) {
            TextView b = textButton(c, cancel, TEXT2);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    d.dismiss();
                }
            });
            btns.addView(b);
        }
        TextView okb = textButton(c, ok == null ? "OK" : ok, ACCENT);
        okb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
                if (onOk != null) onOk.run();
            }
        });
        btns.addView(okb);
        box.addView(btns);
        d.setContentView(box);
        d.setCanceledOnTouchOutside(true);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            int sw = c.getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min(sw - dp(c, 40), dp(c, 420)), ViewGroup.LayoutParams.WRAP_CONTENT);
            if (content instanceof EditText || (content != null && content.findFocus() instanceof EditText)) {
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            }
        }
        return d;
    }

    private static TextView textButton(Context c, String label, int color) {
        TextView b = text(c, label, 15, color, MEDIUM);
        b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        b.setBackground(rounded(0, dp(c, 20)));
        b.setForeground(ripple(c, false));
        b.setClickable(true);
        return b;
    }
}
