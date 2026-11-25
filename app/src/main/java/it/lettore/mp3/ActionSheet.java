package it.lettore.mp3;

import android.app.Dialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Menu a scheda dal basso: intestazione con copertina e voci con icona. */
final class ActionSheet {
    private final Context c;
    private final LinearLayout items;
    private final Dialog dialog;

    ActionSheet(Context c, EntryAdapter.Entry head) {
        this(c, head, head.title, head.sub);
    }

    ActionSheet(Context c, String title, String sub) {
        this(c, null, title, sub);
    }

    private ActionSheet(Context c, EntryAdapter.Entry head, String title, String sub) {
        this.c = c;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(12));

        LinearLayout hdr = new LinearLayout(c);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.setPadding(dp(20), dp(6), dp(20), dp(14));
        if (head != null && head.thumb != EntryAdapter.T_NONE && head.thumb != EntryAdapter.T_NUMBER) {
            FrameLayout box = new FrameLayout(c);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(52), dp(52));
            bp.rightMargin = dp(14);
            hdr.addView(box, bp);
            ImageView img = new ImageView(c);
            img.setClipToOutline(true);
            box.addView(img, new FrameLayout.LayoutParams(-1, -1));
            if (head.thumb == EntryAdapter.T_LETTER) {
                TextView l = Ui.text(c, Ui.initial(head.title), 20, 0xFFFFFFFF, Ui.BOLD);
                l.setGravity(Gravity.CENTER);
                l.setBackground(Ui.oval(head.tile));
                box.addView(l, new FrameLayout.LayoutParams(-1, -1));
            } else {
                img.setBackground(head.round ? Ui.oval(head.tile) : Ui.rounded(head.tile, dp(10)));
                if (head.thumb == EntryAdapter.T_ART && head.artSong != null) {
                    Thumbs.bind(img, head.artSong, head.iconRes, dp(13));
                } else {
                    img.setImageResource(head.iconRes);
                    img.setColorFilter(head.iconTint);
                    img.setPadding(dp(13), dp(13), dp(13), dp(13));
                }
            }
        }
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        hdr.addView(col, Ui.lpw(1f));
        TextView t = Ui.text(c, title, 17, Ui.TEXT, Ui.BOLD);
        col.addView(t);
        if (!TextUtils.isEmpty(sub)) {
            TextView s = Ui.text(c, sub, 14, Ui.TEXT2, Ui.REGULAR);
            s.setPadding(0, dp(2), 0, 0);
            col.addView(s);
        }
        root.addView(hdr);
        View div = new View(c);
        div.setBackgroundColor(Ui.DIVIDER);
        root.addView(div, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));

        items = new LinearLayout(c);
        items.setOrientation(LinearLayout.VERTICAL);
        items.setPadding(0, dp(6), 0, 0);
        ScrollView sv = new ScrollView(c);
        sv.addView(items);
        root.addView(sv);

        dialog = Ui.sheet(c, root, false);
    }

    private int dp(float v) {
        return Ui.dp(c, v);
    }

    ActionSheet add(int icon, String label, Runnable r) {
        return add(icon, label, Ui.ICON, r);
    }

    ActionSheet add(int icon, String label, int iconTint, final Runnable r) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(54));
        row.setPadding(dp(22), 0, dp(20), 0);
        row.setBackground(Ui.ripple(c, false));
        ImageView iv = new ImageView(c);
        iv.setImageResource(icon);
        iv.setColorFilter(iconTint);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(24), dp(24));
        ip.rightMargin = dp(22);
        row.addView(iv, ip);
        TextView tv = Ui.text(c, label, 16, Ui.TEXT, Ui.REGULAR);
        row.addView(tv, Ui.lpw(1f));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
                if (r != null) r.run();
            }
        });
        items.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return this;
    }

    void show() {
        dialog.show();
    }
}
