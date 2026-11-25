package it.lettore.mp3;

import java.util.ArrayList;

/** Un insieme di brani: album, artista, cartella, genere o playlist. */
public final class Group {
    public static final int ALBUM = 1, ARTIST = 2, FOLDER = 3, GENRE = 4, PLAYLIST = 5;

    public final int type;
    public final String key;
    public String title;
    public String subtitle = "";
    public String info = "";
    public String artist;
    public String searchKey = "";
    public long albumId = -1;
    public Playlists.Playlist playlist;
    public final ArrayList<Song> songs = new ArrayList<Song>();

    public Group(int type, String key, String title) {
        this.type = type;
        this.key = key;
        this.title = title;
    }

    public long totalDuration() {
        long t = 0;
        for (Song s : songs) t += s.duration;
        return t;
    }

    public static String typeName(int type) {
        switch (type) {
            case ALBUM: return "Album";
            case ARTIST: return "Artista";
            case FOLDER: return "Cartella";
            case GENRE: return "Genere";
            case PLAYLIST: return "Playlist";
            default: return "";
        }
    }
}
