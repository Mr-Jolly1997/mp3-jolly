package it.lettore.mp3;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SectionIndexer;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

/** Righe delle liste: intestazione, separatori, brani, gruppi e azioni. Con indice alfabetico. */
final class EntryAdapter extends BaseAdapter implements SectionIndexer {

    interface Callback {
        void onMore(Entry e, View anchor);
        void onPlayAll(boolean shuffle);
        void onSort(View anchor);
    }

    static final int SONG = 0, GROUP = 1, ACTION = 2, HEADER = 3, SECTION = 4;
    static final int T_NONE = 0, T_ART = 1, T_ICON = 2, T_LETTER = 3, T_NUMBER = 4;

    static final class Entry {
        int kind;
        String title, sub, right, info, label, number, section, sortLabel;
        Song song, artSong;
        Group group;
        int songIndex;
        int thumb = T_NONE, iconRes, tile, iconTint = 0xB3FFFFFF;
        boolean round, compact, enabled = true;

        static Entry song(Song s, int idx, String sub) {
            Entry e = new Entry();
            e.kind = SONG;
            e.song = s;
            e.artSong = s;
            e.songIndex = idx;
            e.title = s.title;
            e.sub = sub;
            e.right = Ui.time(s.duration);
            e.thumb = T_ART;
            e.iconRes = R.drawable.ic_note;
            e.tile = Ui.tileColor(s.album);
            return e;
        }

        static Entry group(Group g) {
            Entry e = new Entry();
            e.kind = GROUP;
            e.group = g;
            e.title = g.title;
            e.sub = g.subtitle;
            styleFor(e, g);
            return e;
        }

        static Entry action(String title, int icon) {
            Entry e = new Entry();
            e.kind = ACTION;
            e.title = title;
            e.thumb = T_ICON;
            e.iconRes = icon;
            e.tile = Ui.ACCENT_SOFT;
            e.iconTint = Ui.ACCENT;
            return e;
        }

        static Entry section(String title) {
            Entry e = new Entry();
            e.kind = SECTION;
            e.title = title;
            return e;
        }

        static Entry header(Group g) {
            Entry e = new Entry();
            e.kind = HEADER;
            e.group = g;
            e.title = g.title;
            e.label = Group.typeName(g.type);
            e.sub = g.type == Group.ALBUM ? g.artist : g.type == Group.FOLDER ? Library.prettyPath(g.key) : null;
            e.info = g.info;
            e.enabled = !g.songs.isEmpty();
            styleFor(e, g);
            return e;
        }

        static Entry compactHeader(String count, String sortLabel, boolean enabled) {
            Entry e = new Entry();
            e.kind = HEADER;
            e.compact = true;
            e.title = count;
            e.sortLabel = sortLabel;
            e.enabled = enabled;
            return e;
        }

        /** Aspetto della miniatura in base al tipo di gruppo. */
        static void styleFor(Entry e, Group g) {
            switch (g.type) {
                case Group.ALBUM:
                    e.thumb = T_ART;
                    e.artSong = g.songs.isEmpty() ? null : g.songs.get(0);
                    e.iconRes = R.drawable.ic_album;
                    e.tile = Ui.tileColor(g.title);
                    if (e.artSong == null) e.thumb = T_ICON;
                    break;
                case Group.ARTIST:
                    e.thumb = T_LETTER;
                    e.round = true;
                    e.tile = Ui.tileColor(g.title);
                    break;
                case Group.FOLDER:
                    e.thumb = T_ICON;
                    e.iconRes = R.drawable.ic_folder;
                    e.tile = Ui.ACCENT_SOFT;
                    e.iconTint = Ui.ACCENT;
                    break;
                case Group.GENRE:
                    e.thumb = T_ICON;
                    e.iconRes = R.drawable.ic_genre;
                    e.tile = Ui.tileColor(g.title);
                    break;
                default:
                    e.thumb = T_ICON;
                    boolean fav = g.playlist != null && g.playlist.fav;
                    e.iconRes = fav ? R.drawable.ic_heart : R.drawable.ic_playlist;
                    e.tile = fav ? 0xFF8E2450 : Ui.ACCENT_SOFT;
                    e.iconTint = fav ? 0xFFFFC1D9 : Ui.ACCENT;
                    break;
            }
        }
    }

    private static final class RowHolder {
        FrameLayout thumbBox;
        ImageView img, more;
        TextView letter, title, sub, right;
        View scrim;
        Views.PlayingBars bars;
    }

    private static final class HeaderHolder {
        LinearLayout top, compactRow;
        FrameLayout imgBox;
        ImageView img;
        TextView letter, label, title, sub, info, count, sort, play, shuffle;
    }

    private final Context c;
    private final Callback cb;
    private ArrayList<Entry> items = new ArrayList<Entry>();
    long playingId = Long.MIN_VALUE;
    boolean playing;
    boolean indexed;
    int moreIcon = R.drawable.ic_more;

    private String[] sections = new String[0];
    private int[] sectionStart = new int[0];
    private int[] posSection = new int[0];

    EntryAdapter(Context c, Callback cb) {
        this.c = c;
        this.cb = cb;
    }

    private int dp(float v) {
        return Ui.dp(c, v);
    }

    void set(ArrayList<Entry> l, boolean index) {
        items = l;
        indexed = index;
        buildSections();
        notifyDataSetChanged();
    }

    private void buildSections() {
        ArrayList<String> secs = new ArrayList<String>();
        ArrayList<Integer> starts = new ArrayList<Integer>();
        posSection = new int[items.size()];
        int cur = 0;
        if (indexed) {
            String last = null;
            for (int i = 0; i < items.size(); i++) {
                String s = items.get(i).section;
                if (s != null && !s.equals(last)) {
                    secs.add(s);
                    starts.add(i);
                    last = s;
                }
                posSection[i] = Math.max(0, secs.size() - 1);
            }
        }
        sections = secs.toArray(new String[0]);
        sectionStart = new int[starts.size()];
        for (int i = 0; i < starts.size(); i++) sectionStart[i] = starts.get(i);
        if (sections.length == 0) {
            for (int i = 0; i < posSection.length; i++) posSection[i] = cur;
        }
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
    @Override public Entry getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }
    @Override public int getViewTypeCount() { return 3; }

    @Override
    public int getItemViewType(int position) {
        int k = items.get(position).kind;
        return k == HEADER ? 1 : k == SECTION ? 2 : 0;
    }

    @Override public boolean areAllItemsEnabled() { return false; }

    @Override
    public boolean isEnabled(int position) {
        int k = items.get(position).kind;
        return k != HEADER && k != SECTION;
    }

    @Override
    public View getView(int position, View cv, ViewGroup parent) {
        Entry e = items.get(position);
        if (e.kind == HEADER) return headerView(e, cv);
        if (e.kind == SECTION) return sectionView(e, cv);
        return rowView(e, cv);
    }

    // ---------------------------------------------------------------- separatore

    private View sectionView(Entry e, View cv) {
        TextView t = (TextView) cv;
        if (t == null) {
            t = Ui.text(c, "", 13, Ui.ACCENT, Ui.MEDIUM);
            t.setLetterSpacing(0.08f);
            t.setPadding(dp(20), dp(20), dp(20), dp(6));
            t.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        t.setText(e.title.toUpperCase(Locale.ITALIAN));
        return t;
    }

    // ---------------------------------------------------------------- riga

    private View rowView(Entry e, View cv) {
        RowHolder h;
        if (cv == null) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(68));
            row.setPadding(dp(16), dp(6), dp(18), dp(6));
            row.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            h = new RowHolder();
            h.thumbBox = new FrameLayout(c);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(50), dp(50));
            tp.rightMargin = dp(14);
            row.addView(h.thumbBox, tp);
            h.img = new ImageView(c);
            h.img.setClipToOutline(true);
            h.thumbBox.addView(h.img, new FrameLayout.LayoutParams(-1, -1));
            h.letter = Ui.text(c, "", 20, 0xFFFFFFFF, Ui.BOLD);
            h.letter.setGravity(Gravity.CENTER);
            h.thumbBox.addView(h.letter, new FrameLayout.LayoutParams(-1, -1));
            h.scrim = new View(c);
            h.thumbBox.addView(h.scrim, new FrameLayout.LayoutParams(-1, -1));
            h.bars = new Views.PlayingBars(c);
            h.thumbBox.addView(h.bars, new FrameLayout.LayoutParams(-1, -1));

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            row.addView(col, Ui.lpw(1f));
            h.title = Ui.text(c, "", 16, Ui.TEXT, Ui.MEDIUM);
            h.sub = Ui.text(c, "", 13.5f, Ui.TEXT2, Ui.REGULAR);
            h.sub.setPadding(0, dp(2), 0, 0);
            col.addView(h.title);
            col.addView(h.sub);

            h.right = Ui.text(c, "", 13, Ui.TEXT3, Ui.REGULAR);
            h.right.setPadding(dp(8), 0, 0, 0);
            row.addView(h.right);

            h.more = Ui.icon(c, moreIcon, Ui.ICON, 44, 11);
            h.more.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (cb != null) cb.onMore((Entry) v.getTag(), v);
                }
            });
            row.addView(h.more);
            row.setTag(h);
            cv = row;
        } else {
            h = (RowHolder) cv.getTag();
        }

        boolean cur = e.kind == SONG && e.song.id == playingId;
        h.title.setText(e.title);
        h.title.setTextColor(cur ? Ui.PLAYING : e.kind == ACTION ? Ui.ACCENT : Ui.TEXT);
        if (TextUtils.isEmpty(e.sub)) {
            h.sub.setVisibility(View.GONE);
        } else {
            h.sub.setVisibility(View.VISIBLE);
            h.sub.setText(e.sub);
        }
        if (e.right == null) {
            h.right.setVisibility(View.GONE);
        } else {
            h.right.setVisibility(View.VISIBLE);
            h.right.setText(e.right);
        }
        h.more.setVisibility(cb != null && e.kind != ACTION ? View.VISIBLE : View.GONE);
        h.more.setTag(e);

        bindThumb(e, h.thumbBox, h.img, h.letter, dp(e.round ? 25 : 10), dp(13));

        // indicatore "in riproduzione"
        if (cur && e.thumb != T_NONE) {
            h.bars.setVisibility(View.VISIBLE);
            h.bars.setPlaying(playing);
            if (e.thumb == T_NUMBER) {
                h.letter.setVisibility(View.INVISIBLE);
                h.scrim.setVisibility(View.GONE);
                h.bars.setColor(Ui.PLAYING);
            } else {
                h.scrim.setVisibility(View.VISIBLE);
                h.scrim.setBackground(e.round ? Ui.oval(0x99000000) : Ui.rounded(0x99000000, dp(10)));
                h.bars.setColor(0xFFFFFFFF);
            }
        } else {
            h.bars.setVisibility(View.GONE);
            h.bars.setPlaying(false);
            h.scrim.setVisibility(View.GONE);
        }
        return cv;
    }

    /** Miniatura comune a righe e intestazione. */
    private void bindThumb(Entry e, FrameLayout box, ImageView img, TextView letter, int radius, int iconPad) {
        bindThumb(e, box, img, letter, radius, iconPad, Thumbs.SMALL);
    }

    private void bindThumb(Entry e, FrameLayout box, ImageView img, TextView letter, int radius, int iconPad, int size) {
        switch (e.thumb) {
            case T_ART:
                box.setVisibility(View.VISIBLE);
                img.setVisibility(View.VISIBLE);
                letter.setVisibility(View.GONE);
                img.setBackground(e.round ? Ui.oval(e.tile) : Ui.rounded(e.tile, radius));
                if (e.artSong != null) Thumbs.bind(img, e.artSong, e.iconRes, iconPad, size);
                else {
                    Thumbs.unbind(img);
                    Thumbs.placeholder(img, e.iconRes, iconPad);
                }
                break;
            case T_ICON:
                box.setVisibility(View.VISIBLE);
                img.setVisibility(View.VISIBLE);
                letter.setVisibility(View.GONE);
                Thumbs.unbind(img);
                img.animate().cancel();
                img.setAlpha(1f);
                img.setBackground(e.round ? Ui.oval(e.tile) : Ui.rounded(e.tile, radius));
                img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                img.setImageResource(e.iconRes);
                img.setColorFilter(e.iconTint);
                img.setPadding(iconPad, iconPad, iconPad, iconPad);
                break;
            case T_LETTER:
                box.setVisibility(View.VISIBLE);
                Thumbs.unbind(img);
                img.setVisibility(View.GONE);
                letter.setVisibility(View.VISIBLE);
                letter.setTextColor(0xFFFFFFFF);
                letter.setText(Ui.initial(e.title));
                letter.setBackground(e.round ? Ui.oval(e.tile) : Ui.rounded(e.tile, radius));
                break;
            case T_NUMBER:
                box.setVisibility(View.VISIBLE);
                Thumbs.unbind(img);
                img.setVisibility(View.GONE);
                letter.setVisibility(View.VISIBLE);
                letter.setTextColor(Ui.TEXT3);
                letter.setText(e.number);
                letter.setBackground(null);
                break;
            default:
                Thumbs.unbind(img);
                box.setVisibility(View.GONE);
                break;
        }
    }

    // ---------------------------------------------------------------- intestazione

    private View headerView(final Entry e, View cv) {
        HeaderHolder h;
        if (cv == null) {
            LinearLayout root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(20), dp(6), dp(20), dp(10));
            root.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            h = new HeaderHolder();

            h.top = new LinearLayout(c);
            h.top.setOrientation(LinearLayout.HORIZONTAL);
            h.top.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(h.top);
            h.imgBox = new FrameLayout(c);
            h.imgBox.setElevation(dp(6));
            h.top.addView(h.imgBox, new LinearLayout.LayoutParams(dp(128), dp(128)));
            h.img = new ImageView(c);
            h.img.setClipToOutline(true);
            h.imgBox.addView(h.img, new FrameLayout.LayoutParams(-1, -1));
            h.letter = Ui.text(c, "", 48, 0xFFFFFFFF, Ui.BOLD);
            h.letter.setGravity(Gravity.CENTER);
            h.imgBox.addView(h.letter, new FrameLayout.LayoutParams(-1, -1));

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(18), 0, 0, 0);
            h.top.addView(col, Ui.lpw(1f));
            h.label = Ui.text(c, "", 12, Ui.ACCENT, Ui.MEDIUM);
            h.label.setLetterSpacing(0.12f);
            col.addView(h.label);
            h.title = Ui.text(c, "", 22, Ui.TEXT, Ui.BOLD);
            h.title.setSingleLine(false);
            h.title.setMaxLines(3);
            h.title.setPadding(0, dp(4), 0, dp(4));
            col.addView(h.title);
            h.sub = Ui.text(c, "", 15, Ui.TEXT2, Ui.MEDIUM);
            col.addView(h.sub);
            h.info = Ui.text(c, "", 13, Ui.TEXT3, Ui.REGULAR);
            h.info.setPadding(0, dp(2), 0, 0);
            col.addView(h.info);

            h.compactRow = new LinearLayout(c);
            h.compactRow.setOrientation(LinearLayout.HORIZONTAL);
            h.compactRow.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(h.compactRow);
            h.count = Ui.text(c, "", 14, Ui.TEXT2, Ui.MEDIUM);
            h.compactRow.addView(h.count, Ui.lpw(1f));
            h.sort = Ui.pill(c, "", R.drawable.ic_sort, false);
            h.sort.setTextSize(13);
            h.sort.setPadding(dp(12), dp(7), dp(14), dp(7));
            h.sort.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (cb != null) cb.onSort(v);
                }
            });
            h.compactRow.addView(h.sort);

            LinearLayout btns = new LinearLayout(c);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
            bp.topMargin = dp(16);
            root.addView(btns, bp);
            h.play = Ui.pill(c, "Riproduci", R.drawable.ic_play, true);
            h.shuffle = Ui.pill(c, "Casuale", R.drawable.ic_shuffle, false);
            LinearLayout.LayoutParams p1 = Ui.lpw(1f);
            p1.rightMargin = dp(6);
            LinearLayout.LayoutParams p2 = Ui.lpw(1f);
            p2.leftMargin = dp(6);
            btns.addView(h.play, p1);
            btns.addView(h.shuffle, p2);
            h.play.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (cb != null) cb.onPlayAll(false);
                }
            });
            h.shuffle.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (cb != null) cb.onPlayAll(true);
                }
            });
            root.setTag(h);
            cv = root;
        } else {
            h = (HeaderHolder) cv.getTag();
        }

        if (e.compact) {
            h.top.setVisibility(View.GONE);
            h.compactRow.setVisibility(View.VISIBLE);
            h.count.setText(e.title);
            h.sort.setText(e.sortLabel);
            h.sort.setVisibility(e.sortLabel == null ? View.GONE : View.VISIBLE);
        } else {
            h.top.setVisibility(View.VISIBLE);
            h.compactRow.setVisibility(View.GONE);
            h.label.setText(e.label == null ? "" : e.label.toUpperCase(Locale.ITALIAN));
            h.title.setText(e.title);
            h.sub.setVisibility(TextUtils.isEmpty(e.sub) ? View.GONE : View.VISIBLE);
            h.sub.setText(e.sub);
            h.info.setText(e.info);
            h.letter.setTextSize(48);
            bindThumb(e, h.imgBox, h.img, h.letter, dp(e.round ? 64 : 16), dp(34), Thumbs.LARGE);
            h.imgBox.setOutlineProvider(null);
        }
        h.play.setEnabled(e.enabled);
        h.shuffle.setEnabled(e.enabled);
        h.play.setAlpha(e.enabled ? 1f : 0.4f);
        h.shuffle.setAlpha(e.enabled ? 1f : 0.4f);
        return cv;
    }
}
