package it.lettore.mp3;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import it.lettore.mp3.EntryAdapter.Entry;

/** Coda di riproduzione (dalla libreria e dalla schermata del brano) + condivisione di un brano. */
final class QueueSheet {
    private QueueSheet() {}

    static void show(final Activity a, final PlaybackService svc) {
        if (svc == null || svc.queue().isEmpty()) {
            Toast.makeText(a, "La coda è vuota: scegli un brano da ascoltare", Toast.LENGTH_SHORT).show();
            return;
        }
        int pad = Ui.dp(a, 20);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout hdr = new LinearLayout(a);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.setPadding(pad, Ui.dp(a, 2), Ui.dp(a, 16), Ui.dp(a, 10));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(a, "Coda di riproduzione", 18, Ui.TEXT, Ui.BOLD));
        final TextView hsub = Ui.text(a, "", 13, Ui.TEXT2, Ui.REGULAR);
        col.addView(hsub);
        hdr.addView(col, Ui.lpw(1f));
        final TextView clear = Ui.pill(a, "Svuota", R.drawable.ic_clear_all, false);
        clear.setTextSize(14);
        clear.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8));
        hdr.addView(clear);
        box.addView(hdr);

        final ListView lv = new ListView(a);
        lv.setDivider(null);
        lv.setSelector(Ui.ripple(a, false));
        lv.setDrawSelectorOnTop(true);
        lv.setVerticalScrollBarEnabled(false);
        final EntryAdapter[] holder = new EntryAdapter[1];
        final EntryAdapter ad = new EntryAdapter(a, new EntryAdapter.Callback() {
            public void onMore(Entry e, View anchor) {
                if (svc.removeFromQueue(e.songIndex)) fill(svc, holder[0], hsub, clear);
                else Toast.makeText(a, "È il brano in riproduzione", Toast.LENGTH_SHORT).show();
            }

            public void onPlayAll(boolean shuffle) {}

            public void onSort(View anchor) {}
        });
        holder[0] = ad;
        ad.moreIcon = R.drawable.ic_close;
        lv.setAdapter(ad);
        box.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
        final Dialog d = Ui.sheet(a, box, true);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                svc.playIndex(pos);
                d.dismiss();
            }
        });
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Ui.dialog(a, "Svuotare la coda?",
                        "Verranno tolti tutti i brani in attesa. Quello in ascolto finisce normalmente, poi la musica si ferma.",
                        null, "Annulla", "Svuota", new Runnable() {
                            public void run() {
                                svc.clearUpcoming();
                                fill(svc, ad, hsub, clear);
                                Toast.makeText(a, "Coda svuotata", Toast.LENGTH_SHORT).show();
                            }
                        }).show();
            }
        });
        fill(svc, ad, hsub, clear);
        d.show();
        lv.setSelection(Math.max(0, svc.index() - 1));
    }

    private static void fill(PlaybackService svc, EntryAdapter ad, TextView hsub, View clear) {
        ArrayList<Song> q = svc.queue();
        ArrayList<Entry> out = new ArrayList<Entry>();
        long tot = 0;
        for (int i = 0; i < q.size(); i++) {
            out.add(Entry.song(q.get(i), i, q.get(i).artist));
            if (i > svc.index()) tot += q.get(i).duration;
        }
        Song cur = svc.current();
        ad.playingId = cur != null ? cur.id : Long.MIN_VALUE;
        ad.playing = svc.isPlaying();
        ad.set(out, false);
        int left = q.size() - svc.index() - 1;
        hsub.setText(left > 0 ? "In attesa: " + Library.count(left) + " · " + Ui.longTime(tot) : "Nessun brano in attesa");
        clear.setEnabled(left > 0);
        clear.setAlpha(left > 0 ? 1f : 0.4f);
    }

    /** Condivide il file del brano (WhatsApp, Bluetooth, email, …) tramite il menu di sistema. */
    static void share(Activity a, Song s) {
        try {
            String type = a.getContentResolver().getType(s.uri());
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(type != null ? type : "audio/*");
            send.putExtra(Intent.EXTRA_STREAM, s.uri());
            send.setClipData(ClipData.newUri(a.getContentResolver(), s.title, s.uri()));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(send, "Condividi «" + s.title + "»"));
        } catch (Exception e) {
            Toast.makeText(a, "Impossibile condividere questo brano", Toast.LENGTH_SHORT).show();
        }
    }
}
