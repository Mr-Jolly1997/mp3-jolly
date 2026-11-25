package it.lettore.mp3;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import it.lettore.mp3.EntryAdapter.Entry;

public class MainActivity extends Activity implements PlaybackService.Listener, EntryAdapter.Callback {

    /** Da Android 13 si chiede solo l'accesso ai file audio, non a tutta la memoria. */
    private static final String PERM = Build.VERSION.SDK_INT >= 33
            ? "android.permission.READ_MEDIA_AUDIO" : "android.permission.READ_EXTERNAL_STORAGE";
    private static final String PERM_NOTIF = "android.permission.POST_NOTIFICATIONS";
    private Object backCallback;
    private static final String[] TABS = {"Brani", "Playlist", "Cartelle", "Album", "Artisti", "Generi"};
    private static final int T_SONGS = 0, T_PL = 1, T_FOLDERS = 2, T_ALBUMS = 3, T_ARTISTS = 4, T_GENRES = 5;
    private static final String ACT_NEW_PL = "new_pl", ACT_ADD_HINT = "add_hint";

    static final String EXTRA_GOTO_TYPE = "goto_type", EXTRA_GOTO_KEY = "goto_key";

    private static final class Nav {
        int tab, pos, top;
        Group group;
    }

    private int tab;
    private int myGen;
    private ImageView sortBtn;
    private Views.FastScroll fastList, fastGrid;
    private Group openGroup;
    private boolean searching, suppressWatcher;
    private final ArrayList<Nav> stack = new ArrayList<Nav>();
    private boolean loading, permissionDenied;
    private int sortMode;
    private int pendingGotoType;
    private String pendingGotoKey;

    private LinearLayout normalBar, searchRow;
    private ImageView topBack;
    private TextView topTitle;
    private EditText searchEdit;
    private HorizontalScrollView tabScroll;
    private final TextView[] tabViews = new TextView[TABS.length];
    private Views.SwipeListView list;
    private LinearLayout snack;
    private TextView snackText;
    private final Runnable hideSnack = new Runnable() {
        public void run() {
            if (snack == null || snack.getVisibility() != View.VISIBLE) return;
            snack.animate().translationY(snack.getHeight() + dp(24)).alpha(0f).setDuration(200).withEndAction(new Runnable() {
                public void run() {
                    snack.setVisibility(View.GONE);
                }
            }).start();
        }
    };
    private GridView grid;
    private LinearLayout emptyBox;
    private ImageView emptyIcon;
    private TextView emptyText, emptyBtn;
    private FrameLayout miniWrap;
    private LinearLayout miniInfo;
    private ImageView miniThumb, miniPlay;
    private TextView miniTitle, miniSub;
    private ProgressBar miniProgress;

    private EntryAdapter adapter;
    private AlbumGridAdapter gridAdapter;
    private final ArrayList<Song> contextSongs = new ArrayList<Song>();
    private PlaybackService svc;
    private final Handler h = new Handler();
    private long shownId = Long.MIN_VALUE;
    private long miniShownId = Long.MIN_VALUE;
    private boolean shownPlaying;

    private final Runnable ticker = new Runnable() {
        public void run() {
            updateMiniProgress();
            h.postDelayed(this, 500);
        }
    };

    private final Runnable autoReload = new Runnable() {
        public void run() {
            if (hasPermission() && !loading) loadLibrary();
        }
    };

    private final ContentObserver mediaObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            h.removeCallbacks(autoReload);
            h.postDelayed(autoReload, 3000);
        }
    };

    private final ServiceConnection conn = new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder b) {
            svc = ((PlaybackService.LocalBinder) b).get();
            svc.addListener(MainActivity.this);
            if (Library.current != null) svc.restore(Library.current);
            onStateChanged();
        }

        public void onServiceDisconnected(ComponentName n) {
            svc = null;
        }
    };

    // ---------------------------------------------------------------- ciclo di vita

    @Override
    protected void onCreate(Bundle b) {
        myGen = Ui.applyTheme(this);
        super.onCreate(b);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        tab = getSharedPreferences("ui", MODE_PRIVATE).getInt("tab", T_SONGS);
        if (tab < 0 || tab >= TABS.length) tab = T_SONGS;
        sortMode = Settings.sort(this);
        buildUi();
        styleTabs(false);
        readGoto(getIntent());
        if (Library.current != null) refresh(false);
        checkPermissionAndLoad();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        readGoto(i);
        applyPendingGoto();
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, PlaybackService.class), conn, BIND_AUTO_CREATE);
        h.post(ticker);
        try {
            getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
        } catch (Exception e) {
            // ignora
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (myGen != Ui.themeGen) {
            recreate();
            return;
        }
        Library lib = Library.current;
        if (lib == null) {
            if (!loading && hasPermission()) loadLibrary();
        } else if (lib.generation != Settings.generation && !loading) {
            loadLibrary();
        } else {
            refresh(true); // playlist o preferiti modificati altrove
        }
    }

    @Override
    protected void onStop() {
        h.removeCallbacks(ticker);
        h.removeCallbacks(autoReload);
        try {
            getContentResolver().unregisterContentObserver(mediaObserver);
        } catch (Exception e) {
            // ignora
        }
        if (svc != null) {
            svc.removeListener(this);
            svc.saveState();
        }
        unbindService(conn);
        svc = null;
        getSharedPreferences("ui", MODE_PRIVATE).edit().putInt("tab", tab).apply();
        super.onStop();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // usato solo fino ad Android 12; da Android 13 vale il callback registrato in updateBack()
        if (!handleBack()) super.onBackPressed();
    }

    private boolean handleBack() {
        if (searching) {
            closeSearch();
            return true;
        }
        if (openGroup != null) {
            goBack();
            return true;
        }
        return false;
    }

    /** Intercetta "indietro" solo quando serve, così altrimenti funziona l'animazione di sistema. */
    private void updateBack() {
        boolean need = searching || openGroup != null;
        if (need && backCallback == null) {
            backCallback = Compat.registerBack(this, new Runnable() {
                public void run() {
                    handleBack();
                }
            });
        } else if (!need && backCallback != null) {
            Compat.unregisterBack(this, backCallback);
            backCallback = null;
        }
    }

    // ---------------------------------------------------------------- permessi e caricamento

    private boolean hasPermission() {
        return checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkPermissionAndLoad() {
        if (hasPermission()) loadLibrary();
        else requestPermissions(new String[]{PERM}, 7);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != 7) return; // 8 = notifiche: facoltativo, nessuna azione
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            permissionDenied = false;
            loadLibrary();
        } else {
            permissionDenied = true;
            refresh(false);
        }
    }

    private void loadLibrary() {
        if (loading) return;
        loading = true;
        if (Library.current == null) refresh(false);
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final Library lib = Library.load(app);
                Playlists.load(app);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (isFinishing()) return;
                        Library.current = lib;
                        loading = false;
                        Thumbs.forget();
                        stack.clear();
                        if (openGroup != null && openGroup.type != Group.PLAYLIST) {
                            openGroup = lib.find(openGroup.type, openGroup.key);
                        }
                        refresh(true);
                        if (svc != null) svc.restore(lib);
                        applyPendingGoto();
                    }
                });
            }
        }).start();
    }

    private void readGoto(Intent i) {
        if (i == null) return;
        int t = i.getIntExtra(EXTRA_GOTO_TYPE, 0);
        if (t != 0) {
            pendingGotoType = t;
            pendingGotoKey = i.getStringExtra(EXTRA_GOTO_KEY);
            i.removeExtra(EXTRA_GOTO_TYPE);
        }
    }

    private void applyPendingGoto() {
        Library lib = Library.current;
        if (pendingGotoType == 0 || lib == null) return;
        Group g = lib.find(pendingGotoType, pendingGotoKey);
        int t = tabFor(pendingGotoType);
        pendingGotoType = 0;
        if (g != null) {
            if (searching) closeSearch();
            showGroupInTab(t, g);
        }
    }

    private static int tabFor(int type) {
        switch (type) {
            case Group.ALBUM: return T_ALBUMS;
            case Group.ARTIST: return T_ARTISTS;
            case Group.FOLDER: return T_FOLDERS;
            case Group.GENRE: return T_GENRES;
            default: return T_PL;
        }
    }

    // ---------------------------------------------------------------- costruzione interfaccia

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        // --- barra superiore
        FrameLayout top = new FrameLayout(this);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(64)));

        normalBar = new LinearLayout(this);
        normalBar.setOrientation(LinearLayout.HORIZONTAL);
        normalBar.setGravity(Gravity.CENTER_VERTICAL);
        normalBar.setPadding(dp(4), 0, dp(4), 0);
        top.addView(normalBar, new FrameLayout.LayoutParams(-1, -1));
        topBack = Ui.icon(this, R.drawable.ic_back, Ui.ICON, 48, 12);
        topBack.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                goBack();
            }
        });
        normalBar.addView(topBack);
        topTitle = Ui.text(this, "Musica", 28, Ui.TEXT, Ui.BOLD);
        topTitle.setPadding(dp(12), 0, 0, 0);
        normalBar.addView(topTitle, Ui.lpw(1f));
        sortBtn = Ui.icon(this, R.drawable.ic_sort, Ui.ICON, 48, 12);
        sortBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showSortMenu(v);
            }
        });
        normalBar.addView(sortBtn);
        ImageView searchBtn = Ui.icon(this, R.drawable.ic_search, Ui.ICON, 48, 12);
        searchBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openSearch();
            }
        });
        normalBar.addView(searchBtn);
        ImageView settingsBtn = Ui.icon(this, R.drawable.ic_settings, Ui.ICON, 48, 12);
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                Compat.legacyAnim(MainActivity.this, R.anim.slide_in_right, R.anim.fade_out_short);
            }
        });
        normalBar.addView(settingsBtn);

        searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(6), 0, dp(12), 0);
        searchRow.setVisibility(View.GONE);
        top.addView(searchRow, new FrameLayout.LayoutParams(-1, -1));
        ImageView sBack = Ui.icon(this, R.drawable.ic_back, Ui.ICON, 48, 12);
        sBack.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closeSearch();
            }
        });
        searchRow.addView(sBack);
        searchEdit = new EditText(this);
        searchEdit.setHint("Cerca brani, album, artisti");
        searchEdit.setHintTextColor(Ui.TEXT3);
        searchEdit.setTextColor(Ui.TEXT);
        searchEdit.setTextSize(16);
        searchEdit.setSingleLine(true);
        searchEdit.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchEdit.setBackground(Ui.rounded(Ui.SURFACE2, dp(24)));
        searchEdit.setPadding(dp(16), dp(10), dp(16), dp(10));
        Drawable sIcon = getDrawable(R.drawable.ic_search).mutate();
        sIcon.setBounds(0, 0, dp(20), dp(20));
        sIcon.setTint(Ui.TEXT3);
        searchEdit.setCompoundDrawables(sIcon, null, null, null);
        searchEdit.setCompoundDrawablePadding(dp(10));
        searchEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                if (suppressWatcher) return;
                refresh(false);
                list.setSelection(0);
            }
        });
        searchRow.addView(searchEdit, Ui.lpw(1f));

        // --- schede
        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(16), dp(2), dp(16), dp(10));
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = Ui.text(this, TABS[i], 15, Ui.TEXT2, Ui.MEDIUM);
            t.setPadding(dp(18), dp(9), dp(18), dp(9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    selectTab(idx);
                }
            });
            Ui.pressable(t);
            tabViews[i] = t;
            tabs.addView(t, lp);
        }
        tabScroll.addView(tabs);
        root.addView(tabScroll);

        // --- contenuto
        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        list = new Views.SwipeListView(this);
        list.setSwipeListener(new Views.SwipeListView.SwipeListener() {
            public boolean canSwipe(int position) {
                return position >= 0 && position < adapter.getCount()
                        && adapter.getItem(position).kind == EntryAdapter.SONG;
            }

            public void onSwiped(int position) {
                Entry e = adapter.getItem(position);
                if (svc == null || e.song == null) return;
                svc.enqueue(Collections.singletonList(e.song));
                showQueued("«" + e.song.title + "» aggiunto alla coda");
            }
        });
        list.setDivider(null);
        list.setFastScrollEnabled(false);
        list.setVerticalScrollBarEnabled(false);
        list.setSelector(Ui.ripple(this, false));
        list.setDrawSelectorOnTop(true);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(16));
        list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        adapter = new EntryAdapter(this, this);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                onEntryClick(adapter.getItem(pos));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                Entry e = adapter.getItem(pos);
                if (e.kind != EntryAdapter.SONG && e.kind != EntryAdapter.GROUP) return false;
                onMore(e, v);
                return true;
            }
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView v, int state) {
                if (searching && state == SCROLL_STATE_TOUCH_SCROLL) hideKeyboard();
            }

            public void onScroll(AbsListView v, int first, int visible, int total) {
                if (fastList != null) fastList.onListScrolled();
            }
        });
        content.addView(list, new FrameLayout.LayoutParams(-1, -1));

        grid = new GridView(this);
        int wdp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        grid.setNumColumns(Math.max(2, wdp / 180));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setPadding(dp(10), 0, dp(10), dp(16));
        grid.setClipToPadding(false);
        grid.setFastScrollEnabled(false);
        grid.setVerticalScrollBarEnabled(false);
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView v, int state) {}

            public void onScroll(AbsListView v, int first, int visible, int total) {
                if (fastGrid != null) fastGrid.onListScrolled();
            }
        });
        grid.setSelector(Ui.ripple(this, false));
        grid.setDrawSelectorOnTop(true);
        gridAdapter = new AlbumGridAdapter(this);
        grid.setAdapter(gridAdapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                openGroup(gridAdapter.getItem(pos));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                groupMenu(gridAdapter.getItem(pos), v);
                return true;
            }
        });
        grid.setVisibility(View.GONE);
        content.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        fastList = new Views.FastScroll(this);
        fastList.attach(list);
        content.addView(fastList, new FrameLayout.LayoutParams(-1, -1));
        fastGrid = new Views.FastScroll(this);
        fastGrid.attach(grid);
        content.addView(fastGrid, new FrameLayout.LayoutParams(-1, -1));

        emptyBox = new LinearLayout(this);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        emptyBox.setPadding(dp(40), dp(24), dp(40), dp(60));
        emptyIcon = new ImageView(this);
        emptyIcon.setColorFilter(Ui.TEXT3);
        emptyIcon.setBackground(Ui.oval(Ui.SURFACE2));
        emptyIcon.setPadding(dp(22), dp(22), dp(22), dp(22));
        emptyBox.addView(emptyIcon, new LinearLayout.LayoutParams(dp(88), dp(88)));
        emptyText = Ui.text(this, "", 15, Ui.TEXT2, Ui.REGULAR);
        emptyText.setSingleLine(false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setLineSpacing(0, 1.15f);
        emptyText.setPadding(0, dp(18), 0, dp(18));
        emptyBox.addView(emptyText);
        emptyBtn = Ui.pill(this, "", 0, true);
        emptyBox.addView(emptyBtn);
        emptyBox.setVisibility(View.GONE);
        content.addView(emptyBox, new FrameLayout.LayoutParams(-1, -1));

        // --- messaggio "aggiunto alla coda" con pulsante per aprirla
        int snackBg = Ui.dark ? 0xFFEDEDF2 : 0xFF2B2B31;
        int snackFg = Ui.dark ? 0xFF1A1A1F : 0xFFFFFFFF;
        snack = new LinearLayout(this);
        snack.setOrientation(LinearLayout.HORIZONTAL);
        snack.setGravity(Gravity.CENTER_VERTICAL);
        snack.setBackground(Ui.rounded(snackBg, dp(14)));
        snack.setElevation(dp(8));
        snack.setPadding(dp(16), dp(6), dp(6), dp(6));
        snack.setMinimumHeight(dp(52));
        ImageView sIc = new ImageView(this);
        sIc.setImageResource(R.drawable.ic_queue_add);
        sIc.setColorFilter(snackFg);
        snack.addView(sIc, new LinearLayout.LayoutParams(dp(20), dp(20)));
        snackText = Ui.text(this, "", 14, snackFg, Ui.REGULAR);
        snackText.setSingleLine(false);
        snackText.setMaxLines(2);
        snackText.setPadding(dp(12), 0, dp(8), 0);
        snack.addView(snackText, Ui.lpw(1f));
        int actionCol = Ui.isLight(snackBg) ? Ui.blend(Ui.ACCENT, 0xFF000000, 0.45f) : Ui.ACCENT;
        TextView snackAction = Ui.text(this, "APRI CODA", 14, actionCol, Ui.BOLD);
        snackAction.setPadding(dp(12), dp(10), dp(12), dp(10));
        snackAction.setBackground(Ui.ripple(this, false));
        snackAction.setClickable(true);
        snackAction.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                h.removeCallbacks(hideSnack);
                snack.setVisibility(View.GONE);
                QueueSheet.show(MainActivity.this, svc);
            }
        });
        snack.addView(snackAction);
        snack.setVisibility(View.GONE);
        FrameLayout.LayoutParams snp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        snp.leftMargin = dp(12);
        snp.rightMargin = dp(12);
        snp.bottomMargin = dp(10);
        content.addView(snack, snp);

        // --- mini lettore
        miniWrap = new FrameLayout(this);
        miniWrap.setPadding(dp(8), dp(2), dp(8), dp(8));
        miniWrap.setClipToPadding(false);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(Ui.MINI_BG, dp(18)));
        card.setElevation(dp(10));
        card.setClipToOutline(true);
        miniWrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(6), dp(8));
        row.setBackground(Ui.ripple(this, false));
        card.addView(row);
        miniInfo = new LinearLayout(this);
        miniInfo.setOrientation(LinearLayout.HORIZONTAL);
        miniInfo.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(miniInfo, Ui.lpw(1f));
        miniThumb = new ImageView(this);
        miniThumb.setClipToOutline(true);
        miniInfo.addView(miniThumb, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout mcol = new LinearLayout(this);
        mcol.setOrientation(LinearLayout.VERTICAL);
        mcol.setPadding(dp(12), 0, dp(4), 0);
        miniInfo.addView(mcol, Ui.lpw(1f));
        miniTitle = Ui.text(this, "", 15, Ui.MINI_TEXT, Ui.MEDIUM);
        miniSub = Ui.text(this, "", 13, Ui.blend(Ui.MINI_TEXT, Ui.MINI_BG, 0.35f), Ui.REGULAR);
        mcol.addView(miniTitle);
        mcol.addView(miniSub);
        ImageView miniQueue = Ui.icon(this, R.drawable.ic_queue, Ui.MINI_ICONS, 44, 11);
        miniQueue.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                QueueSheet.show(MainActivity.this, svc);
            }
        });
        row.addView(miniQueue);
        miniPlay = Ui.icon(this, R.drawable.ic_play, Ui.MINI_PLAY_ICON, 46, 11);
        miniPlay.setBackground(Ui.oval(Ui.MINI_PLAY_BG));
        Ui.pressable(miniPlay);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(46), dp(46));
        pp.leftMargin = dp(4);
        pp.rightMargin = dp(2);
        miniPlay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (svc != null) svc.toggle();
            }
        });
        row.addView(miniPlay, pp);
        ImageView miniNext = Ui.icon(this, R.drawable.ic_next, Ui.MINI_ICONS, 44, 11);
        miniNext.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (svc != null) svc.next();
            }
        });
        row.addView(miniNext);
        miniProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        miniProgress.setMax(1000);
        miniProgress.setProgressTintList(ColorStateList.valueOf(Ui.MINI_PLAY_BG));
        miniProgress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.alpha(Ui.MINI_TEXT, 0x30)));
        card.addView(miniProgress, new LinearLayout.LayoutParams(-1, dp(2)));
        Swipe.attach(row, miniInfo, new Swipe.Callback() {
            public void onTap() {
                openNowPlaying();
            }

            public void onSwipe(boolean toNext) {
                if (svc == null) return;
                if (toNext) svc.next();
                else svc.prev();
            }
        });
        miniWrap.setVisibility(View.GONE);
        root.addView(miniWrap);

        setContentView(root);
        Ui.decorate(this, root);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration cfg) {
        super.onConfigurationChanged(cfg);
        int wdp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        grid.setNumColumns(Math.max(2, wdp / 180));
    }

    private void openNowPlaying() {
        startActivity(new Intent(this, NowPlayingActivity.class));
        Compat.legacyAnim(this, R.anim.slide_up, R.anim.stay);
    }

    private void styleTabs(boolean animate) {
        for (int i = 0; i < tabViews.length; i++) {
            boolean sel = i == tab;
            tabViews[i].setTextColor(sel ? Ui.TAB_SEL_TEXT : Ui.TAB_TEXT);
            tabViews[i].setBackground(Ui.rounded(sel ? Ui.TAB_SEL_BG : Ui.TAB_BG, dp(20)));
        }
        final TextView t = tabViews[tab];
        final boolean anim = animate;
        tabScroll.post(new Runnable() {
            public void run() {
                int x = t.getLeft() - (tabScroll.getWidth() - t.getWidth()) / 2;
                if (anim) tabScroll.smoothScrollTo(Math.max(0, x), 0);
                else tabScroll.scrollTo(Math.max(0, x), 0);
            }
        });
    }

    // ---------------------------------------------------------------- navigazione

    private void selectTab(int i) {
        if (i == tab && openGroup == null) {
            if (i == T_ALBUMS) grid.smoothScrollToPosition(0);
            else list.smoothScrollToPosition(0);
            return;
        }
        tab = i;
        openGroup = null;
        stack.clear();
        styleTabs(true);
        refresh(false);
        list.setSelection(0);
        grid.setSelection(0);
        animateIn(0);
    }

    private AbsListView scroller() {
        return grid.getVisibility() == View.VISIBLE ? grid : list;
    }

    private Nav snapshot() {
        Nav n = new Nav();
        n.tab = tab;
        n.group = openGroup;
        AbsListView v = scroller();
        n.pos = v.getFirstVisiblePosition();
        View ch = v.getChildAt(0);
        n.top = ch == null ? 0 : ch.getTop();
        return n;
    }

    private void openGroup(Group g) {
        if (g == null) return;
        boolean wasSearching = searching;
        if (!wasSearching) stack.add(snapshot());
        if (searching) closeSearchQuietly();
        openGroup = g;
        refresh(false);
        list.setSelection(0);
        animateIn(1);
    }

    private void showGroupInTab(int t, Group g) {
        if (g == null) return;
        if (!searching) stack.add(snapshot());
        else closeSearchQuietly();
        tab = t;
        styleTabs(false);
        openGroup = g;
        refresh(false);
        list.setSelection(0);
        animateIn(1);
    }

    private void goBack() {
        if (stack.isEmpty()) {
            openGroup = null;
            refresh(false);
            animateIn(-1);
            return;
        }
        Nav n = stack.remove(stack.size() - 1);
        tab = n.tab;
        styleTabs(false);
        openGroup = n.group;
        if (openGroup != null && openGroup.type == Group.PLAYLIST && !Playlists.all.contains(openGroup.playlist)) {
            openGroup = null;
        }
        refresh(false);
        if (scroller() == list) list.setSelectionFromTop(n.pos, n.top);
        else grid.setSelection(n.pos);
        animateIn(-1);
    }

    private void animateIn(int dir) {
        View v = scroller();
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationX(dir * dp(28));
        v.setTranslationY(dir == 0 ? dp(10) : 0);
        v.animate().alpha(1f).translationX(0).translationY(0).setDuration(220).start();
    }

    private void openSearch() {
        if (searching) return;
        searching = true;
        refresh(false);
        searchEdit.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.showSoftInput(searchEdit, InputMethodManager.SHOW_IMPLICIT);
        animateIn(0);
    }

    private void closeSearchQuietly() {
        hideKeyboard();
        searching = false;
        suppressWatcher = true;
        searchEdit.setText("");
        suppressWatcher = false;
    }

    private void closeSearch() {
        closeSearchQuietly();
        refresh(false);
        animateIn(0);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchEdit.getWindowToken(), 0);
    }

    // ---------------------------------------------------------------- contenuto

    private void updateChrome() {
        boolean inGroup = openGroup != null && !searching;
        normalBar.setVisibility(searching ? View.GONE : View.VISIBLE);
        searchRow.setVisibility(searching ? View.VISIBLE : View.GONE);
        topBack.setVisibility(inGroup ? View.VISIBLE : View.GONE);
        if (inGroup) {
            topTitle.setText(Group.typeName(openGroup.type));
            topTitle.setTextSize(19);
            topTitle.setTypeface(Ui.MEDIUM);
            topTitle.setPadding(dp(4), 0, 0, 0);
        } else {
            topTitle.setText("Musica");
            topTitle.setTextSize(28);
            topTitle.setTypeface(Ui.BOLD);
            topTitle.setPadding(dp(12), 0, 0, 0);
        }
        sortBtn.setVisibility(!inGroup && !searching && tab == T_SONGS ? View.VISIBLE : View.GONE);
        tabScroll.setVisibility(inGroup || searching ? View.GONE : View.VISIBLE);
        updateBack();
    }

    private void refresh(boolean keepPos) {
        updateChrome();
        Library lib = Library.current;
        ArrayList<Entry> out = new ArrayList<Entry>();
        contextSongs.clear();

        if (lib == null) {
            list.setVisibility(View.GONE);
            grid.setVisibility(View.GONE);
            adapter.set(out, false);
            if (loading) {
                showEmpty(R.drawable.ic_note, "Sto cercando la tua musica…", null, null);
            } else if (permissionDenied || !hasPermission()) {
                showEmpty(R.drawable.ic_note, "Per mostrare la tua musica l'app deve poter leggere i file audio del telefono.",
                        "Consenti l'accesso", new Runnable() {
                            public void run() {
                                askPermissionAgain();
                            }
                        });
            } else {
                showEmpty(R.drawable.ic_note, "", null, null);
            }
            return;
        }

        boolean showGrid = false, index = false, empty = false;
        int emptyIconRes = R.drawable.ic_note;
        String emptyMsg = null, emptyAction = null;
        Runnable emptyRun = null;

        if (searching) {
            String raw = searchEdit.getText().toString().trim();
            String q = Ui.norm(raw);
            emptyIconRes = R.drawable.ic_search;
            if (q.length() == 0) {
                empty = true;
                emptyMsg = "Cerca tra brani, album e artisti";
            } else {
                int n = 0;
                ArrayList<Entry> part = new ArrayList<Entry>();
                for (Group g : lib.artists) {
                    if (g.searchKey.contains(q)) {
                        part.add(Entry.group(g));
                        if (++n >= 4) break;
                    }
                }
                if (!part.isEmpty()) {
                    out.add(Entry.section("Artisti"));
                    out.addAll(part);
                }
                part.clear();
                n = 0;
                for (Group g : lib.albums) {
                    if (g.searchKey.contains(q)) {
                        part.add(Entry.group(g));
                        if (++n >= 6) break;
                    }
                }
                if (!part.isEmpty()) {
                    out.add(Entry.section("Album"));
                    out.addAll(part);
                }
                part.clear();
                for (Song s : lib.songs) {
                    if (s.searchKey.contains(q)) {
                        part.add(Entry.song(s, contextSongs.size(), s.artist + " · " + s.album));
                        contextSongs.add(s);
                        if (contextSongs.size() >= 400) break;
                    }
                }
                if (!part.isEmpty()) {
                    out.add(Entry.section("Brani"));
                    out.addAll(part);
                }
                empty = out.isEmpty();
                emptyMsg = "Nessun risultato per «" + raw + "»";
            }
        } else if (openGroup != null) {
            if (openGroup.type == Group.PLAYLIST) openGroup = Playlists.toGroup(openGroup.playlist, lib);
            out.add(Entry.header(openGroup));
            addSongs(out, openGroup.songs, openGroup.type);
            if (openGroup.type == Group.PLAYLIST && openGroup.songs.isEmpty()) {
                boolean fav = openGroup.playlist.fav;
                Entry a = Entry.action(fav ? "Ancora nessun preferito" : "Aggiungi brani dalla libreria",
                        fav ? R.drawable.ic_heart_outline : R.drawable.ic_add);
                a.sub = fav ? "Tocca il cuore nella schermata del brano" : "Tocca ⋮ accanto a un brano › Aggiungi a playlist";
                a.label = ACT_ADD_HINT;
                out.add(a);
            }
        } else {
            switch (tab) {
                case T_SONGS: {
                    ArrayList<Song> l = lib.sorted(sortMode);
                    addSongs(out, l, -1);
                    index = sortMode != Settings.SORT_RECENT;
                    empty = l.isEmpty();
                    emptyMsg = lib.excludedCount > 0
                            ? "Nessun brano da mostrare.\n" + lib.excludedCount + " file sono esclusi dalle impostazioni."
                            : "Non ho trovato musica sul telefono.\nCopia i tuoi MP3 nella memoria (ad esempio nella cartella Music): appariranno qui da soli.";
                    emptyAction = "Impostazioni libreria";
                    emptyRun = new Runnable() {
                        public void run() {
                            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                        }
                    };
                    break;
                }
                case T_PL: {
                    Entry a = Entry.action("Nuova playlist", R.drawable.ic_add);
                    a.label = ACT_NEW_PL;
                    out.add(a);
                    for (Playlists.Playlist p : Playlists.all) out.add(Entry.group(Playlists.toGroup(p, lib)));
                    break;
                }
                case T_FOLDERS:
                    addGroups(out, lib.folders);
                    index = true;
                    empty = out.isEmpty();
                    emptyIconRes = R.drawable.ic_folder;
                    emptyMsg = "Nessuna cartella con musica.";
                    break;
                case T_ALBUMS:
                    showGrid = true;
                    gridAdapter.set(lib.albums);
                    empty = lib.albums.isEmpty();
                    emptyIconRes = R.drawable.ic_album;
                    emptyMsg = "Nessun album.";
                    break;
                case T_ARTISTS:
                    addGroups(out, lib.artists);
                    index = true;
                    empty = out.isEmpty();
                    emptyIconRes = R.drawable.ic_person;
                    emptyMsg = "Nessun artista.";
                    break;
                default:
                    addGroups(out, lib.genres);
                    index = true;
                    empty = out.isEmpty();
                    emptyIconRes = R.drawable.ic_genre;
                    emptyMsg = "Nessun genere.";
                    break;
            }
        }

        if (showGrid) {
            grid.setVisibility(empty ? View.GONE : View.VISIBLE);
            list.setVisibility(View.GONE);
        } else {
            grid.setVisibility(View.GONE);
            list.setVisibility(empty ? View.GONE : View.VISIBLE);
            int pos = list.getFirstVisiblePosition();
            View ch = list.getChildAt(0);
            int top = ch == null ? 0 : ch.getTop();
            adapter.set(out, index);
            if (keepPos) list.setSelectionFromTop(pos, top);
        }
        if (empty) showEmpty(emptyIconRes, emptyMsg, emptyAction, emptyRun);
        else emptyBox.setVisibility(View.GONE);
        syncPlaying(true);
    }

    private void showEmpty(int icon, String msg, String action, final Runnable run) {
        emptyBox.setVisibility(View.VISIBLE);
        emptyIcon.setImageResource(icon);
        emptyText.setText(msg);
        if (action == null) {
            emptyBtn.setVisibility(View.GONE);
        } else {
            emptyBtn.setVisibility(View.VISIBLE);
            emptyBtn.setText(action);
            emptyBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    run.run();
                }
            });
        }
    }

    /** Una sola volta, al primo play: permesso per mostrare i comandi nella tendina (Android 13+). */
    private void maybeAskNotifications() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(PERM_NOTIF) == PackageManager.PERMISSION_GRANTED) return;
        android.content.SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
        if (p.getBoolean("notif_asked", false)) return;
        p.edit().putBoolean("notif_asked", true).apply();
        requestPermissions(new String[]{PERM_NOTIF}, 8);
    }

    private void askPermissionAgain() {
        if (permissionDenied && !shouldShowRequestPermissionRationale(PERM)) {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
            Toast.makeText(this, "Apri «Autorizzazioni» e consenti «Musica e audio»", Toast.LENGTH_LONG).show();
        } else {
            requestPermissions(new String[]{PERM}, 7);
        }
    }

    private void addSongs(ArrayList<Entry> out, List<Song> songs, int groupType) {
        int i = 0;
        for (Song s : songs) {
            String sub;
            if (groupType == Group.ALBUM) sub = s.artist;
            else if (groupType == Group.ARTIST) sub = s.album;
            else sub = s.artist + " · " + s.album;
            Entry e = Entry.song(s, contextSongs.size(), sub);
            if (groupType == Group.ALBUM) {
                e.thumb = EntryAdapter.T_NUMBER;
                int tr = s.track % 1000;
                e.number = String.valueOf(tr > 0 ? tr : i + 1);
            }
            if (groupType == -1) {
                if (sortMode == Settings.SORT_TITLE) e.section = Ui.sectionOf(s.title);
                else if (sortMode == Settings.SORT_ARTIST) e.section = Ui.sectionOf(s.artist);
                else if (sortMode == Settings.SORT_ALBUM) e.section = Ui.sectionOf(s.album);
            }
            out.add(e);
            contextSongs.add(s);
            i++;
        }
    }

    private void addGroups(ArrayList<Entry> out, List<Group> groups) {
        for (Group g : groups) {
            Entry e = Entry.group(g);
            e.section = Ui.sectionOf(g.title);
            out.add(e);
        }
    }

    private void onEntryClick(Entry e) {
        switch (e.kind) {
            case EntryAdapter.SONG:
                if (searching) hideKeyboard();
                playContext(e.songIndex, false);
                break;
            case EntryAdapter.GROUP:
                openGroup(e.group);
                break;
            case EntryAdapter.ACTION:
                if (ACT_NEW_PL.equals(e.label)) {
                    newPlaylistDialog(null);
                } else if (ACT_ADD_HINT.equals(e.label)) {
                    if (openGroup != null && openGroup.playlist != null && !openGroup.playlist.fav) {
                        selectTab(T_SONGS);
                        Toast.makeText(this, "Tocca ⋮ accanto a un brano e scegli «Aggiungi a playlist»", Toast.LENGTH_LONG).show();
                    }
                }
                break;
        }
    }

    private void playContext(int index, boolean shuffle) {
        if (svc == null || contextSongs.isEmpty()) return;
        svc.playQueue(new ArrayList<Song>(contextSongs), index, shuffle);
    }

    // ---------------------------------------------------------------- callback dell'adapter

    @Override
    public void onPlayAll(boolean shuffle) {
        playContext(shuffle ? -1 : 0, shuffle);
    }

    @Override
    public void onSort(View anchor) {
        showSortMenu(anchor);
    }

    private void showSortMenu(View anchor) {
        PopMenu m = new PopMenu(this, "Ordina i brani per");
        for (int i = 0; i < Settings.SORT_NAMES.length; i++) {
            final int mode = i;
            boolean sel = i == sortMode;
            m.add(sel ? R.drawable.ic_check : (i == Settings.SORT_RECENT ? R.drawable.ic_history : R.drawable.ic_sort),
                    Settings.SORT_NAMES[i], sel ? Ui.ACCENT : Ui.ICON, new Runnable() {
                        public void run() {
                            sortMode = mode;
                            Settings.setSort(MainActivity.this, mode);
                            refresh(false);
                            list.setSelection(0);
                            animateIn(0);
                        }
                    });
        }
        m.show(anchor);
    }

    @Override
    public void onMore(Entry e, View anchor) {
        if (e.kind == EntryAdapter.SONG) songMenu(e.song, anchor);
        else if (e.kind == EntryAdapter.GROUP) groupMenu(e.group, anchor);
    }

    /** Menu ⋮ di un brano. */
    private void songMenu(final Song s, final View anchor) {
        final boolean inPl = !searching && openGroup != null && openGroup.type == Group.PLAYLIST;
        final Playlists.Playlist pl = inPl ? openGroup.playlist : null;
        final Library lib = Library.current;
        PopMenu m = new PopMenu(this);
        m.add(R.drawable.ic_queue_add, "Aggiungi alla coda", new Runnable() {
            public void run() {
                if (svc == null) return;
                svc.enqueue(Collections.singletonList(s));
                showQueued("«" + s.title + "» aggiunto alla coda");
            }
        });
        m.add(R.drawable.ic_queue, "Apri la coda", new Runnable() {
            public void run() {
                QueueSheet.show(MainActivity.this, svc);
            }
        });
        m.add(R.drawable.ic_share, "Condividi", new Runnable() {
            public void run() {
                QueueSheet.share(MainActivity.this, s);
            }
        });
        if (lib != null && (searching || openGroup == null || openGroup.type != Group.ALBUM)) {
            m.add(R.drawable.ic_album, "Vai all'album", new Runnable() {
                public void run() {
                    showGroupInTab(T_ALBUMS, lib.albumByKey.get(String.valueOf(s.albumId)));
                }
            });
        }
        if (lib != null && (searching || openGroup == null || openGroup.type != Group.ARTIST)) {
            m.add(R.drawable.ic_person, "Vai all'artista", new Runnable() {
                public void run() {
                    showGroupInTab(T_ARTISTS, lib.artistByKey.get(s.artist.toLowerCase(Locale.ROOT)));
                }
            });
        }
        m.add(R.drawable.ic_playlist, "Aggiungi a playlist", new Runnable() {
            public void run() {
                playlistMenu(Collections.singletonList(s), anchor);
            }
        });
        if (inPl) {
            m.divider();
            m.add(R.drawable.ic_up, "Sposta su", new Runnable() {
                public void run() {
                    if (lib != null) Playlists.move(MainActivity.this, pl, s, -1, lib);
                    refresh(true);
                }
            });
            m.add(R.drawable.ic_downarrow, "Sposta giù", new Runnable() {
                public void run() {
                    if (lib != null) Playlists.move(MainActivity.this, pl, s, 1, lib);
                    refresh(true);
                }
            });
            m.add(R.drawable.ic_delete, "Rimuovi dalla playlist", Ui.DANGER, new Runnable() {
                public void run() {
                    Playlists.remove(MainActivity.this, pl, s);
                    refresh(true);
                }
            });
        }
        m.show(anchor);
    }

    /** Menu ⋮ di album, artisti, cartelle, generi, playlist. */
    private void groupMenu(final Group g, final View anchor) {
        PopMenu m = new PopMenu(this);
        if (!g.songs.isEmpty()) {
            m.add(R.drawable.ic_play, "Riproduci", new Runnable() {
                public void run() {
                    if (svc != null) svc.playQueue(g.songs, 0, false);
                }
            });
            m.add(R.drawable.ic_shuffle, "Riproduci in ordine casuale", new Runnable() {
                public void run() {
                    if (svc != null) svc.playQueue(g.songs, -1, true);
                }
            });
            m.add(R.drawable.ic_queue_add, "Aggiungi alla coda", new Runnable() {
                public void run() {
                    if (svc == null) return;
                    svc.enqueue(new ArrayList<Song>(g.songs));
                    showQueued(Library.count(g.songs.size()) + " aggiunti alla coda");
                }
            });
            if (g.type != Group.PLAYLIST) {
                m.add(R.drawable.ic_playlist, "Aggiungi a playlist", new Runnable() {
                    public void run() {
                        playlistMenu(new ArrayList<Song>(g.songs), anchor);
                    }
                });
            }
        }
        if (g.type == Group.FOLDER) {
            m.divider();
            m.add(R.drawable.ic_block, "Escludi dalla libreria", Ui.DANGER, new Runnable() {
                public void run() {
                    confirmExcludeFolder(g);
                }
            });
        }
        if (g.type == Group.PLAYLIST && g.playlist != null && !g.playlist.fav) {
            m.divider();
            m.add(R.drawable.ic_edit, "Rinomina", new Runnable() {
                public void run() {
                    renamePlaylistDialog(g.playlist);
                }
            });
            m.add(R.drawable.ic_delete, "Elimina playlist", Ui.DANGER, new Runnable() {
                public void run() {
                    deletePlaylistDialog(g.playlist);
                }
            });
        }
        m.show(anchor);
    }

    private void playlistMenu(final List<Song> songs, View anchor) {
        if (songs.isEmpty()) return;
        PopMenu m = new PopMenu(this, "Aggiungi a playlist");
        m.add(R.drawable.ic_add, "Nuova playlist…", Ui.ACCENT, new Runnable() {
            public void run() {
                newPlaylistDialog(songs);
            }
        });
        for (final Playlists.Playlist p : new ArrayList<Playlists.Playlist>(Playlists.all)) {
            m.add(p.fav ? R.drawable.ic_heart : R.drawable.ic_playlist, p.name, p.fav ? Ui.NP_HEART : Ui.ICON, new Runnable() {
                public void run() {
                    int n = Playlists.addAll(MainActivity.this, p, songs);
                    toast(n == 0 ? "Già presente in «" + p.name + "»"
                            : (n == 1 ? "Aggiunto a «" : Library.count(n) + " aggiunti a «") + p.name + "»");
                    refresh(true);
                }
            });
        }
        m.show(anchor);
    }

    private void confirmExcludeFolder(final Group g) {
        Ui.dialog(this, "Escludere «" + g.title + "»?",
                "I " + Library.count(g.songs.size()) + " di questa cartella (e delle sottocartelle) non compariranno più nella libreria. I file restano sul telefono.\n\nPuoi riattivarla da Impostazioni › Cartelle escluse.",
                null, "Annulla", "Escludi", new Runnable() {
                    public void run() {
                        Settings.addExcludedFolder(MainActivity.this, g.key);
                        if (openGroup == g) {
                            openGroup = null;
                            stack.clear();
                        }
                        toast("Cartella esclusa");
                        loadLibrary();
                    }
                }).show();
    }

    private void newPlaylistDialog(final List<Song> songs) {
        final EditText et = Ui.field(this, "Nome della playlist", null);
        Ui.dialog(this, "Nuova playlist", null, et, "Annulla", "Crea", new Runnable() {
            public void run() {
                String name = et.getText().toString().trim();
                if (name.length() == 0) name = "Playlist " + Playlists.all.size();
                Playlists.Playlist p = Playlists.create(MainActivity.this, name);
                if (songs != null) {
                    int n = Playlists.addAll(MainActivity.this, p, songs);
                    toast("Playlist «" + name + "» creata con " + Library.count(n));
                } else {
                    toast("Playlist «" + name + "» creata");
                }
                refresh(true);
            }
        }).show();
        et.requestFocus();
    }

    private void renamePlaylistDialog(final Playlists.Playlist p) {
        if (p == null) return;
        final EditText et = Ui.field(this, "Nome della playlist", p.name);
        Ui.dialog(this, "Rinomina playlist", null, et, "Annulla", "Salva", new Runnable() {
            public void run() {
                String name = et.getText().toString().trim();
                if (name.length() == 0) return;
                p.name = name;
                Playlists.save(MainActivity.this);
                refresh(true);
            }
        }).show();
        et.requestFocus();
    }

    private void deletePlaylistDialog(final Playlists.Playlist p) {
        if (p == null) return;
        Ui.dialog(this, "Eliminare «" + p.name + "»?", "Verrà eliminata solo la playlist: i brani restano sul telefono.",
                null, "Annulla", "Elimina", new Runnable() {
                    public void run() {
                        Playlists.all.remove(p);
                        Playlists.save(MainActivity.this);
                        if (openGroup != null && openGroup.playlist == p) {
                            openGroup = null;
                            stack.clear();
                        }
                        refresh(false);
                    }
                }).show();
    }

    static void details(Activity a, Song s) {
        String msg = "Titolo: " + s.title
                + "\nArtista: " + s.artist
                + "\nAlbum: " + s.album
                + "\nGenere: " + (s.genre != null ? s.genre : "—")
                + "\nDurata: " + Ui.time(s.duration)
                + "\n\nFile:\n" + Library.prettyPath(s.path);
        Ui.dialog(a, "Dettagli", msg, null, null, "OK", null).show();
    }

    /** Messaggio in basso con pulsante "Apri coda"; sparisce da solo dopo qualche secondo. */
    private void showQueued(String msg) {
        h.removeCallbacks(hideSnack);
        snackText.setText(msg);
        if (snack.getVisibility() != View.VISIBLE) {
            snack.setVisibility(View.VISIBLE);
            snack.setAlpha(0f);
            snack.setTranslationY(dp(40));
        }
        snack.animate().cancel();
        snack.animate().translationY(0).alpha(1f).setDuration(200).start();
        h.postDelayed(hideSnack, 3500);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- stato del lettore

    @Override
    public void onStateChanged() {
        Song s = svc != null ? svc.current() : null;
        if (s == null) {
            miniWrap.setVisibility(View.GONE);
        } else {
            if (miniWrap.getVisibility() != View.VISIBLE) {
                miniWrap.setVisibility(View.VISIBLE);
                miniWrap.setTranslationY(dp(90));
                miniWrap.animate().translationY(0).setDuration(260).start();
            }
            miniTitle.setText(s.title);
            miniSub.setText(s.artist);
            if (svc.isPlaying()) maybeAskNotifications();
            miniPlay.setImageResource(svc.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
            if (s.id != miniShownId) {
                miniShownId = s.id;
                miniThumb.setBackground(Ui.rounded(Ui.tileColor(s.album), dp(10)));
                Thumbs.bind(miniThumb, s, R.drawable.ic_note, dp(12));
            }
        }
        syncPlaying(false);
        updateMiniProgress();
    }

    private void syncPlaying(boolean force) {
        Song s = svc != null ? svc.current() : null;
        long id = s != null ? s.id : Long.MIN_VALUE;
        boolean playing = svc != null && svc.isPlaying();
        if (force || id != shownId || playing != shownPlaying) {
            shownId = id;
            shownPlaying = playing;
            adapter.playingId = id;
            adapter.playing = playing;
            adapter.notifyDataSetChanged();
            long alb = s != null ? s.albumId : Long.MIN_VALUE;
            if (gridAdapter.playingAlbum != alb) {
                gridAdapter.playingAlbum = alb;
                gridAdapter.notifyDataSetChanged();
            }
        }
    }

    private void updateMiniProgress() {
        if (svc == null || svc.current() == null) return;
        int d = svc.duration();
        int p = svc.position();
        miniProgress.setProgress(d > 0 ? (int) (1000L * p / d) : 0);
    }
}
