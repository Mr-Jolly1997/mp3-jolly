package it.lettore.mp3;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Preferenze dell'utente. "generation" cambia quando la libreria va ricaricata. */
final class Settings {
    static volatile int generation;

    static final int SORT_TITLE = 0, SORT_ARTIST = 1, SORT_ALBUM = 2, SORT_RECENT = 3;
    static final String[] SORT_NAMES = {"Titolo", "Artista", "Album", "Aggiunti di recente"};
    static final int[] MIN_DURATIONS = {0, 10, 30, 60};
    static final String[] MIN_DURATION_NAMES = {"Nessun limite", "Meno di 10 secondi", "Meno di 30 secondi", "Meno di 1 minuto"};

    private Settings() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static boolean exclWhatsApp(Context c) { return p(c).getBoolean("excl_wa", true); }
    static boolean exclTelegram(Context c) { return p(c).getBoolean("excl_tg", true); }
    static int minDuration(Context c) { return p(c).getInt("min_dur", 0); }
    static boolean resume(Context c) { return p(c).getBoolean("resume", true); }
    static int accent(Context c) { return p(c).getInt("accent", 0); }
    static int sort(Context c) { return p(c).getInt("sort", SORT_TITLE); }

    // ---- aspetto
    static final int THEME_SYSTEM = 0, THEME_LIGHT = 1, THEME_DARK = 2;
    static final String[] THEME_NAMES = {"Sistema", "Chiaro", "Scuro"};

    static int themeMode(Context c) { return p(c).getInt("theme", THEME_SYSTEM); }
    static boolean coverBg(Context c) { return p(c).getBoolean("cover_bg", true); }
    static void setThemeMode(Context c, int m) { p(c).edit().putInt("theme", m).apply(); Ui.bump(); }
    static void setCoverBg(Context c, boolean v) { p(c).edit().putBoolean("cover_bg", v).apply(); Ui.bump(); }

    /** Colori personalizzati: chiave "d.<token>" per il tema scuro, "l.<token>" per il chiaro. */
    static SharedPreferences colors(Context c) {
        return c.getApplicationContext().getSharedPreferences("colors", Context.MODE_PRIVATE);
    }

    static void setColor(Context c, boolean dark, String token, int color) {
        colors(c).edit().putInt((dark ? "d." : "l.") + token, color).apply();
        Ui.bump();
    }

    static void clearColor(Context c, boolean dark, String token) {
        colors(c).edit().remove((dark ? "d." : "l.") + token).apply();
        Ui.bump();
    }

    static boolean hasColor(Context c, boolean dark, String token) {
        return colors(c).contains((dark ? "d." : "l.") + token);
    }

    static void clearAllColors(Context c, boolean dark) {
        SharedPreferences.Editor e = colors(c).edit();
        String pre = dark ? "d." : "l.";
        for (String k : colors(c).getAll().keySet()) if (k.startsWith(pre)) e.remove(k);
        e.apply();
        Ui.bump();
    }

    /** Impostazioni che cambiano il contenuto della libreria. */
    static void setLibraryBool(Context c, String key, boolean v) {
        p(c).edit().putBoolean(key, v).apply();
        generation++;
    }

    static void setMinDuration(Context c, int sec) {
        p(c).edit().putInt("min_dur", sec).apply();
        generation++;
    }

    static void setResume(Context c, boolean v) { p(c).edit().putBoolean("resume", v).apply(); }
    static int profile(Context c) { return p(c).getInt("profile", 0); }

    /** Applica un profilo colore: i colori personalizzati (di entrambi i temi) vengono sostituiti. */
    static void setProfile(Context c, int i) {
        p(c).edit().putInt("profile", i).apply();
        colors(c).edit().clear().apply();
        Ui.bump();
    }

    static boolean hasCustomColors(Context c) {
        return !colors(c).getAll().isEmpty();
    }

    /**
     * Versione 5: i colori scelti dall'utente sono diventati il profilo predefinito "Ambra e turchese",
     * quindi le vecchie personalizzazioni (identiche) si azzerano una volta sola.
     */
    static void migrate(Context c) {
        SharedPreferences sp = p(c);
        if (sp.getBoolean("migr5", false)) return;
        colors(c).edit().clear().apply();
        sp.edit().putBoolean("migr5", true).putInt("profile", 0).apply();
    }
    static void setSort(Context c, int s) { p(c).edit().putInt("sort", s).apply(); }

    static ArrayList<String> excludedFolders(Context c) {
        ArrayList<String> out = new ArrayList<String>();
        try {
            JSONArray a = new JSONArray(p(c).getString("excl_folders", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception e) {
            // ignora
        }
        return out;
    }

    static void setExcludedFolders(Context c, List<String> folders) {
        JSONArray a = new JSONArray();
        for (String f : folders) a.put(f);
        p(c).edit().putString("excl_folders", a.toString()).apply();
        generation++;
    }

    static void addExcludedFolder(Context c, String folder) {
        ArrayList<String> l = excludedFolders(c);
        if (!l.contains(folder)) l.add(folder);
        setExcludedFolders(c, l);
    }

    static void forceReload() {
        generation++;
    }

    static boolean isWhatsApp(String path) {
        String l = path.toLowerCase(Locale.ROOT);
        return l.contains("/whatsapp/media/") || l.contains("/whatsapp business/media/")
                || l.contains("/com.whatsapp/") || l.contains("/com.whatsapp.w4b/");
    }

    static boolean isTelegram(String path) {
        String l = path.toLowerCase(Locale.ROOT);
        return l.contains("/telegram/") || l.contains("/org.telegram.messenger");
    }
}
