package it.lettore.mp3;

import android.content.ContentUris;
import android.net.Uri;
import android.provider.MediaStore;

public final class Song {
    public long id;
    public long albumId;
    public long duration;
    public int track;
    public long dateAdded;
    public String title;
    public String artist;
    public String album;
    public String genre;
    public String path;
    public String folder;
    public String folderPath;
    public String searchKey = "";

    public Uri uri() {
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    }
}
