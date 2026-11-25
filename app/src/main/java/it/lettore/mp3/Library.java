package it.lettore.mp3;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.provider.MediaStore;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Legge la musica presente sul telefono (MediaStore), applica le esclusioni e la raggruppa. */
public final class Library {
    public static volatile Library current;

    public static final String UNKNOWN_ARTIST = "Artista sconosciuto";
    public static final String UNKNOWN_ALBUM = "Album sconosciuto";
    public static final String NO_GENRE = "Senza genere";

    /** Una cartella che contiene audio (prima delle esclusioni), per la scelta delle cartelle da escludere. */
    public static final class FolderInfo {
        public String path, name;
        public int count;
        public boolean auto; // esclusa automaticamente (WhatsApp/Telegram)
    }

    public final ArrayList<Song> songs = new ArrayList<Song>();
    public final ArrayList<Group> albums = new ArrayList<Group>();
    public final ArrayList<Group> artists = new ArrayList<Group>();
    public final ArrayList<Group> folders = new ArrayList<Group>();
    public final ArrayList<Group> genres = new ArrayList<Group>();
    public final ArrayList<FolderInfo> allFolders = new ArrayList<FolderInfo>();
    public final HashMap<String, Song> byPath = new HashMap<String, Song>();
    public final HashMap<Long, Song> byId = new HashMap<Long, Song>();
    public final HashMap<String, Group> albumByKey = new HashMap<String, Group>();
    public final HashMap<String, Group> artistByKey = new HashMap<String, Group>();
    public final HashMap<String, Group> folderByKey = new HashMap<String, Group>();
    public final HashMap<String, Group> genreByKey = new HashMap<String, Group>();
    public int excludedCount;
    public int generation;

    @SuppressWarnings("unchecked")
    private final ArrayList<Song>[] sortCache = new ArrayList[4];

    private static Collator collator() {
        Collator c = Collator.getInstance(Locale.ITALIAN);
        c.setStrength(Collator.PRIMARY);
        return c;
    }

    private static boolean empty(String s) {
        return s == null || s.trim().length() == 0;
    }

    /** Brani ordinati secondo la modalità scelta (Settings.SORT_*). */
    public synchronized ArrayList<Song> sorted(int mode) {
        if (mode <= Settings.SORT_TITLE || mode > Settings.SORT_RECENT) return songs;
        if (sortCache[mode] != null) return sortCache[mode];
        ArrayList<Song> l = new ArrayList<Song>(songs);
        final Collator col = collator();
        Comparator<Song> cmp;
        if (mode == Settings.SORT_ARTIST) {
            cmp = new Comparator<Song>() {
                public int compare(Song a, Song b) {
                    int r = col.compare(a.artist, b.artist);
                    if (r != 0) return r;
                    r = col.compare(a.album, b.album);
                    if (r != 0) return r;
                    if (a.track != b.track) return a.track < b.track ? -1 : 1;
                    return col.compare(a.title, b.title);
                }
            };
        } else if (mode == Settings.SORT_ALBUM) {
            cmp = new Comparator<Song>() {
                public int compare(Song a, Song b) {
                    int r = col.compare(a.album, b.album);
                    if (r != 0) return r;
                    if (a.track != b.track) return a.track < b.track ? -1 : 1;
                    return col.compare(a.title, b.title);
                }
            };
        } else {
            cmp = new Comparator<Song>() {
                public int compare(Song a, Song b) {
                    if (a.dateAdded != b.dateAdded) return a.dateAdded > b.dateAdded ? -1 : 1;
                    return col.compare(a.title, b.title);
                }
            };
        }
        Collections.sort(l, cmp);
        sortCache[mode] = l;
        return l;
    }

    public static Library load(Context ctx) {
        Library lib = new Library();
        lib.generation = Settings.generation;
        boolean exWa = Settings.exclWhatsApp(ctx);
        boolean exTg = Settings.exclTelegram(ctx);
        long minMs = Settings.minDuration(ctx) * 1000L;
        ArrayList<String> exFolders = Settings.excludedFolders(ctx);
        LinkedHashMap<String, FolderInfo> folderInfo = new LinkedHashMap<String, FolderInfo>();

        ContentResolver cr = ctx.getContentResolver();
        String[] proj = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATE_ADDED
        };
        Cursor c = null;
        try {
            c = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                    MediaStore.Audio.Media.IS_MUSIC + "!=0", null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    Song s = new Song();
                    s.id = c.getLong(0);
                    s.title = c.getString(1);
                    s.artist = c.getString(2);
                    s.album = c.getString(3);
                    s.albumId = c.getLong(4);
                    s.duration = c.getLong(5);
                    s.path = c.getString(6);
                    s.track = c.getInt(7);
                    String dn = c.getString(8);
                    s.dateAdded = c.getLong(9);
                    if (s.path == null) s.path = "id:" + s.id;
                    int slash = s.path.lastIndexOf('/');
                    s.folderPath = slash > 0 ? s.path.substring(0, slash) : "/";
                    int s2 = s.folderPath.lastIndexOf('/');
                    s.folder = s2 >= 0 ? s.folderPath.substring(s2 + 1) : s.folderPath;
                    if (s.folder.length() == 0) s.folder = "/";

                    // elenco di tutte le cartelle (anche quelle escluse)
                    boolean auto = (exWa && Settings.isWhatsApp(s.path)) || (exTg && Settings.isTelegram(s.path));
                    FolderInfo fi = folderInfo.get(s.folderPath);
                    if (fi == null) {
                        fi = new FolderInfo();
                        fi.path = s.folderPath;
                        fi.name = s.folder;
                        fi.auto = auto;
                        folderInfo.put(s.folderPath, fi);
                    }
                    fi.count++;

                    // esclusioni
                    if (auto || (minMs > 0 && s.duration > 0 && s.duration < minMs) || inFolders(s.folderPath, exFolders)) {
                        lib.excludedCount++;
                        continue;
                    }

                    if (empty(s.title)) s.title = dn != null ? stripExt(dn) : "Senza titolo";
                    if (empty(s.artist) || "<unknown>".equals(s.artist)) s.artist = UNKNOWN_ARTIST;
                    if (empty(s.album)) s.album = UNKNOWN_ALBUM;
                    lib.songs.add(s);
                    lib.byPath.put(s.path, s);
                    lib.byId.put(s.id, s);
                }
            }
        } catch (Exception e) {
            // nessun accesso o errore del MediaStore: libreria vuota
        } finally {
            if (c != null) c.close();
        }

        loadGenres(cr, lib.byId);

        for (Song s : lib.songs) {
            s.searchKey = Ui.norm(s.title + " " + s.artist + " " + s.album);
        }

        final Collator col = collator();
        Collections.sort(lib.songs, new Comparator<Song>() {
            public int compare(Song a, Song b) {
                return col.compare(a.title, b.title);
            }
        });

        lib.allFolders.addAll(folderInfo.values());
        Collections.sort(lib.allFolders, new Comparator<FolderInfo>() {
            public int compare(FolderInfo a, FolderInfo b) {
                int r = col.compare(a.name, b.name);
                return r != 0 ? r : a.path.compareTo(b.path);
            }
        });

        LinkedHashMap<String, Group> albums = new LinkedHashMap<String, Group>();
        LinkedHashMap<String, Group> artists = new LinkedHashMap<String, Group>();
        LinkedHashMap<String, Group> folders = new LinkedHashMap<String, Group>();
        LinkedHashMap<String, Group> genres = new LinkedHashMap<String, Group>();

        for (Song s : lib.songs) {
            String ak = String.valueOf(s.albumId);
            Group g = albums.get(ak);
            if (g == null) {
                g = new Group(Group.ALBUM, ak, s.album);
                g.albumId = s.albumId;
                albums.put(ak, g);
            }
            g.songs.add(s);

            String rk = s.artist.toLowerCase(Locale.ROOT);
            g = artists.get(rk);
            if (g == null) {
                g = new Group(Group.ARTIST, rk, s.artist);
                artists.put(rk, g);
            }
            g.songs.add(s);

            g = folders.get(s.folderPath);
            if (g == null) {
                g = new Group(Group.FOLDER, s.folderPath, s.folder);
                folders.put(s.folderPath, g);
            }
            g.songs.add(s);

            String gn = s.genre == null ? NO_GENRE : s.genre;
            String gk = gn.toLowerCase(Locale.ROOT);
            g = genres.get(gk);
            if (g == null) {
                g = new Group(Group.GENRE, gk, gn);
                genres.put(gk, g);
            }
            g.songs.add(s);
        }

        Comparator<Song> byTrack = new Comparator<Song>() {
            public int compare(Song a, Song b) {
                if (a.track != b.track) return a.track < b.track ? -1 : 1;
                return col.compare(a.title, b.title);
            }
        };
        Comparator<Song> byAlbumTrack = new Comparator<Song>() {
            public int compare(Song a, Song b) {
                int r = col.compare(a.album, b.album);
                if (r != 0) return r;
                if (a.albumId != b.albumId) return a.albumId < b.albumId ? -1 : 1;
                if (a.track != b.track) return a.track < b.track ? -1 : 1;
                return col.compare(a.title, b.title);
            }
        };
        Comparator<Song> byFile = new Comparator<Song>() {
            public int compare(Song a, Song b) {
                return col.compare(a.path, b.path);
            }
        };

        for (Group g : albums.values()) {
            Collections.sort(g.songs, byTrack);
            HashSet<String> arts = new HashSet<String>();
            for (Song s : g.songs) arts.add(s.artist);
            g.artist = arts.size() == 1 ? g.songs.get(0).artist : "Artisti vari";
            g.subtitle = g.artist;
            g.info = Library.count(g.songs.size()) + " · " + Ui.longTime(g.totalDuration());
            lib.albums.add(g);
            lib.albumByKey.put(g.key, g);
        }
        for (Group g : artists.values()) {
            Collections.sort(g.songs, byAlbumTrack);
            HashSet<Long> al = new HashSet<Long>();
            for (Song s : g.songs) al.add(s.albumId);
            g.subtitle = count(g.songs.size()) + " · " + al.size() + " album";
            g.info = g.subtitle + " · " + Ui.longTime(g.totalDuration());
            lib.artists.add(g);
            lib.artistByKey.put(g.key, g);
        }
        for (Group g : folders.values()) {
            Collections.sort(g.songs, byFile);
            g.subtitle = count(g.songs.size()) + " · " + prettyPath(g.key);
            g.info = count(g.songs.size()) + " · " + Ui.longTime(g.totalDuration());
            lib.folders.add(g);
            lib.folderByKey.put(g.key, g);
        }
        for (Group g : genres.values()) {
            g.subtitle = count(g.songs.size());
            g.info = count(g.songs.size()) + " · " + Ui.longTime(g.totalDuration());
            lib.genres.add(g);
            lib.genreByKey.put(g.key, g);
        }

        Comparator<Group> gc = new Comparator<Group>() {
            public int compare(Group a, Group b) {
                return col.compare(a.title, b.title);
            }
        };
        Collections.sort(lib.albums, gc);
        Collections.sort(lib.artists, gc);
        Collections.sort(lib.folders, gc);
        Collections.sort(lib.genres, new Comparator<Group>() {
            public int compare(Group a, Group b) {
                boolean an = NO_GENRE.equals(a.title), bn = NO_GENRE.equals(b.title);
                if (an != bn) return an ? 1 : -1;
                return col.compare(a.title, b.title);
            }
        });
        for (ArrayList<Group> l : new ArrayList[]{lib.albums, lib.artists, lib.folders, lib.genres}) {
            for (Group g : l) g.searchKey = Ui.norm(g.title + " " + g.subtitle);
        }
        return lib;
    }

    static boolean inFolders(String folder, ArrayList<String> ex) {
        for (String f : ex) {
            if (folder.equals(f) || folder.startsWith(f + "/")) return true;
        }
        return false;
    }

    public Group find(int type, String key) {
        if (key == null) return null;
        switch (type) {
            case Group.ALBUM: return albumByKey.get(key);
            case Group.ARTIST: return artistByKey.get(key);
            case Group.FOLDER: return folderByKey.get(key);
            case Group.GENRE: return genreByKey.get(key);
            default: return null;
        }
    }

    public static String count(int n) {
        return n == 1 ? "1 brano" : String.format(Locale.ITALIAN, "%,d brani", n);
    }

    public static String prettyPath(String p) {
        if (p == null) return "";
        if (p.startsWith("/storage/emulated/0")) return "Memoria interna" + p.substring("/storage/emulated/0".length());
        if (p.startsWith("/storage/")) {
            String rest = p.substring("/storage/".length());
            int i = rest.indexOf('/');
            return "Scheda SD" + (i >= 0 ? rest.substring(i) : "");
        }
        return p;
    }

    private static String stripExt(String n) {
        int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(0, i) : n;
    }

    private static String cleanGenre(String g) {
        if (g == null) return null;
        g = g.trim();
        if (g.length() == 0) return null;
        return g;
    }

    private static void loadGenres(ContentResolver cr, HashMap<Long, Song> byId) {
        boolean ok = false;
        if (Build.VERSION.SDK_INT >= 30) {
            Cursor c = null;
            try {
                c = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[]{"_id", "genre"},
                        MediaStore.Audio.Media.IS_MUSIC + "!=0", null, null);
                if (c != null) {
                    while (c.moveToNext()) {
                        Song s = byId.get(c.getLong(0));
                        if (s != null) s.genre = cleanGenre(c.getString(1));
                    }
                    ok = true;
                }
            } catch (Exception e) {
                ok = false;
            } finally {
                if (c != null) c.close();
            }
        }
        if (ok) return;
        Cursor g = null;
        try {
            g = cr.query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME}, null, null, null);
            if (g == null) return;
            while (g.moveToNext()) {
                long gid = g.getLong(0);
                String name = cleanGenre(g.getString(1));
                if (name == null) continue;
                Cursor m = null;
                try {
                    m = cr.query(MediaStore.Audio.Genres.Members.getContentUri("external", gid),
                            new String[]{MediaStore.Audio.Genres.Members.AUDIO_ID}, null, null, null);
                    if (m != null) {
                        while (m.moveToNext()) {
                            Song s = byId.get(m.getLong(0));
                            if (s != null && s.genre == null) s.genre = name;
                        }
                    }
                } catch (Exception e) {
                    // ignora
                } finally {
                    if (m != null) m.close();
                }
            }
        } catch (Exception e) {
            // ignora
        } finally {
            if (g != null) g.close();
        }
    }
}
