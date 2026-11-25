package it.lettore.mp3;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.SectionIndexer;
import android.widget.TextView;

import java.util.ArrayList;

/** Griglia di copertine per la scheda Album. */
final class AlbumGridAdapter extends BaseAdapter implements SectionIndexer {
    private final Context c;
    private ArrayList<Group> items = new ArrayList<Group>();
    long playingAlbum = Long.MIN_VALUE;

    private String[] sections = new String[0];
    private int[] sectionStart = new int[0];
    private int[] posSection = new int[0];

    private static final class Holder {
        Views.SquareImageView img;
        TextView title, sub;
    }

    AlbumGridAdapter(Context c) {
        this.c = c;
    }

    void set(ArrayList<Group> l) {
        items = l;
        ArrayList<String> secs = new ArrayList<String>();
        ArrayList<Integer> starts = new ArrayList<Integer>();
        posSection = new int[l.size()];
        String last = null;
        for (int i = 0; i < l.size(); i++) {
            String s = Ui.sectionOf(l.get(i).title);
            if (!s.equals(last)) {
                secs.add(s);
                starts.add(i);
                last = s;
            }
            posSection[i] = secs.size() - 1;
        }
        sections = secs.toArray(new String[0]);
        sectionStart = new int[starts.size()];
        for (int i = 0; i < starts.size(); i++) sectionStart[i] = starts.get(i);
        notifyDataSetChanged();
    }

    @Override public Object[] getSections() { return sections; }

    @Override
    public int getPositionForSection(int s) {
        if (sectionStart.length == 0) return 0;
        return sectionStart[Math.max(0, Math.min(s, sectionStart.length - 1))];
    }

    @Override
    public int getSectionForPosition(int p) {
        if (posSection.length == 0) return 0;
        return posSection[Math.max(0, Math.min(p, posSection.length - 1))];
    }

    @Override public int getCount() { return items.size(); }
    @Override public Group getItem(int p) { return items.get(p); }
    @Override public long getItemId(int p) { return p; }

    @Override
    public View getView(int position, View cv, ViewGroup parent) {
        Holder h;
        if (cv == null) {
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            int p = Ui.dp(c, 8);
            card.setPadding(p, p, p, Ui.dp(c, 12));
            card.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            h = new Holder();
            h.img = new Views.SquareImageView(c, true);
            h.img.setClipToOutline(true);
            card.addView(h.img, new LinearLayout.LayoutParams(-1, -2));
            h.title = Ui.text(c, "", 14.5f, Ui.TEXT, Ui.MEDIUM);
            h.title.setPadding(Ui.dp(c, 2), Ui.dp(c, 10), Ui.dp(c, 2), 0);
            card.addView(h.title);
            h.sub = Ui.text(c, "", 13, Ui.TEXT2, Ui.REGULAR);
            h.sub.setPadding(Ui.dp(c, 2), Ui.dp(c, 1), Ui.dp(c, 2), 0);
            card.addView(h.sub);
            card.setTag(h);
            cv = card;
        } else {
            h = (Holder) cv.getTag();
        }
        Group g = items.get(position);
        h.title.setText(g.title);
        h.title.setTextColor(g.albumId == playingAlbum ? Ui.PLAYING : Ui.TEXT);
        h.sub.setText(g.artist);
        h.img.setBackground(Ui.rounded(Ui.tileColor(g.title), Ui.dp(c, 14)));
        if (!g.songs.isEmpty()) Thumbs.bind(h.img, g.songs.get(0), R.drawable.ic_album, Ui.dp(c, 40), Thumbs.LARGE);
        return cv;
    }
}
