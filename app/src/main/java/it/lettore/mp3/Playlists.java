package it.lettore.mp3;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Playlist salvate dall'app (file JSON privato, percorsi dei file). "Preferiti" è sempre la prima. */
public final class Playlists {
    public static final class Playlist {
        public String name;
        public boolean fav;
        public final ArrayList<String> paths = new ArrayList<String>();
    }

    public static final ArrayList<Playlist> all = new ArrayList<Playlist>();
    private static boolean loaded;

    private static File file(Context c) {
        return new File(c.getFilesDir(), "playlists.json");
    }

    public static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        all.clear();
        File f = file(c);
        if (f.exists()) {
            try {
                FileInputStream in = new FileInputStream(f);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                JSONArray arr = new JSONArray(new String(bo.toByteArray(), "UTF-8"));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Playlist p = new Playlist();
                    p.name = o.optString("name", "Playlist");
                    p.fav = o.optBoolean("fav", false);
                    JSONArray ps = o.optJSONArray("paths");
                    if (ps != null) for (int j = 0; j < ps.length(); j++) p.paths.add(ps.getString(j));
                    all.add(p);
                }
            } catch (Exception e) {
                // file illeggibile: si riparte vuoti
            }
        }
        favorites();
    }

    /** La playlist "Preferiti" (creata se manca). */
    public static synchronized Playlist favorites() {
        for (Playlist p : all) if (p.fav) return p;
        Playlist p = new Playlist();
        p.name = "Preferiti";
        p.fav = true;
        all.add(0, p);
        return p;
    }

    public static boolean isFavorite(Song s) {
        return s != null && favorites().paths.contains(s.path);
    }

    /** Ritorna true se ora è tra i preferiti. */
    public static boolean toggleFavorite(Context c, Song s) {
        Playlist f = favorites();
        boolean now;
        if (f.paths.remove(s.path)) {
            now = false;
        } else {
            f.paths.add(0, s.path);
            now = true;
        }
        save(c);
        return now;
    }

    public static synchronized void save(Context c) {
        try {
            JSONArray arr = new JSONArray();
            for (Playlist p : all) {
                JSONObject o = new JSONObject();
                o.put("name", p.name);
                if (p.fav) o.put("fav", true);
                JSONArray ps = new JSONArray();
                for (String s : p.paths) ps.put(s);
                o.put("paths", ps);
                arr.put(o);
            }
            File tmp = new File(c.getFilesDir(), "playlists.json.tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(arr.toString().getBytes("UTF-8"));
            out.close();
            tmp.renameTo(file(c));
        } catch (Exception e) {
            // ignora
        }
    }

    public static Playlist create(Context c, String name) {
        Playlist p = new Playlist();
        p.name = name;
        all.add(p);
        save(c);
        return p;
    }

    /** Aggiunge i brani (senza duplicati). Ritorna quanti sono stati aggiunti. */
    public static int addAll(Context c, Playlist p, List<Song> songs) {
        int n = 0;
        for (Song s : songs) {
            if (!p.paths.contains(s.path)) {
                p.paths.add(s.path);
                n++;
            }
        }
        save(c);
        return n;
    }

    public static void remove(Context c, Playlist p, Song s) {
        p.paths.remove(s.path);
        save(c);
    }

    /** Sposta un brano di una posizione (dir = -1 su, +1 giù), saltando i file non più presenti. */
    public static void move(Context c, Playlist p, Song s, int dir, Library lib) {
        int i = p.paths.indexOf(s.path);
        if (i < 0) return;
        int j = i + dir;
        while (j >= 0 && j < p.paths.size() && !lib.byPath.containsKey(p.paths.get(j))) j += dir;
        if (j < 0 || j >= p.paths.size()) return;
        String t = p.paths.get(j);
        p.paths.set(j, p.paths.get(i));
        p.paths.set(i, t);
        save(c);
    }

    public static Group toGroup(Playlist p, Library lib) {
        Group g = new Group(Group.PLAYLIST, p.name, p.name);
        g.playlist = p;
        if (lib != null) {
            for (String path : p.paths) {
                Song s = lib.byPath.get(path);
                if (s != null) g.songs.add(s);
            }
        }
        g.subtitle = Library.count(g.songs.size());
        long t = g.totalDuration();
        g.info = g.subtitle + (t > 0 ? " · " + Ui.longTime(t) : "");
        g.searchKey = Ui.norm(g.title);
        return g;
    }
}
