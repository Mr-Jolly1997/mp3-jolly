package it.lettore.mp3;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

/** Piccolo menu che si apre accanto al pulsante ⋮ (o alla riga tenuta premuta). */
final class PopMenu {
    private final Context c;
    private final LinearLayout items;
    private PopupWindow pw;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    PopMenu(Context c) {
        this(c, null);
    }

    PopMenu(Context c, String title) {
        this.c = c;
        items = new LinearLayout(c);
        items.setOrientation(LinearLayout.VERTICAL);
        items.setPadding(0, dp(6), 0, dp(6));
        if (title != null) {
            TextView t = Ui.text(c, title, 13, Ui.TEXT2, Ui.MEDIUM);
            t.setPadding(dp(18), dp(8), dp(18), dp(6));
            items.addView(t);
        }
    }

    private int dp(float v) {
        return Ui.dp(c, v);
    }

    PopMenu add(int icon, String label, Runnable r) {
        return add(icon, label, Ui.ICON, r);
    }

    PopMenu add(int icon, String label, int tint, final Runnable r) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPadding(dp(18), 0, dp(20), 0);
        row.setBackground(Ui.ripple(c, false));
        ImageView iv = new ImageView(c);
        iv.setImageResource(icon);
        iv.setColorFilter(tint);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(22), dp(22));
        ip.rightMargin = dp(16);
        row.addView(iv, ip);
        row.addView(Ui.text(c, label, 15.5f, tint == Ui.DANGER ? Ui.DANGER : Ui.TEXT, Ui.REGULAR), Ui.lpw(1f));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (pw != null) pw.dismiss();
                // la riga del menu viene staccata dalla finestra: si esegue sul thread principale
                if (r != null) MAIN.post(r);
            }
        });
        items.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return this;
    }

    PopMenu divider() {
        View d = new View(c);
        d.setBackgroundColor(Ui.DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        items.addView(d, lp);
        return this;
    }

    /** Apre il menu allineato al bordo destro dell'elemento toccato. */
    void show(View anchor) {
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(items);
        int width = dp(252);
        int maxH = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.6f);
        items.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int h = Math.min(items.getMeasuredHeight(), maxH);
        pw = new PopupWindow(sv, width, h, true);
        pw.setBackgroundDrawable(Ui.rounded(Ui.SURFACE, dp(16)));
        pw.setElevation(dp(14));
        pw.setOutsideTouchable(true);
        pw.setAnimationStyle(android.R.style.Animation_Dialog);
        sv.setClipToOutline(true);
        sv.setBackground(Ui.rounded(Ui.SURFACE, dp(16)));
        pw.showAsDropDown(anchor, 0, -anchor.getHeight() / 2, Gravity.END);
    }

    static ColorDrawable none() {
        return new ColorDrawable(0);
    }
}
