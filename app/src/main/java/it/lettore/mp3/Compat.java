package it.lettore.mp3;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/** Adattamenti per le versioni recenti di Android, chiamati solo sulle versioni che li supportano. */
final class Compat {
    private Compat() {}

    /**
     * Da Android 15 le app vengono disegnate sotto barra di stato e barra di navigazione (edge-to-edge).
     * Qui lo attiviamo da Android 11 in su e spostiamo il contenuto quanto serve, tastiera compresa.
     */
    static void edgeToEdge(Activity a, View root) {
        if (Build.VERSION.SDK_INT < 30) return;
        Api30.apply(a, root);
    }

    /** Icone della barra di stato/navigazione scure (sfondo chiaro) o chiare (sfondo scuro). */
    @SuppressWarnings("deprecation")
    static void systemBars(Activity a, boolean lightStatus, boolean lightNav, int legacyColor) {
        if (Build.VERSION.SDK_INT >= 30) {
            Api30.appearance(a, lightStatus, lightNav);
            return;
        }
        Window w = a.getWindow();
        w.setStatusBarColor(legacyColor);
        w.setNavigationBarColor(legacyColor);
        View decor = w.getDecorView();
        int f = decor.getSystemUiVisibility();
        f = lightStatus ? f | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : f & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        f = lightNav ? f | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : f & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(f);
    }

    /** Gesto "indietro" (indietro predittivo da Android 13; obbligatorio con target Android 16). */
    static Object registerBack(Activity a, Runnable r) {
        if (Build.VERSION.SDK_INT < 33) return null;
        return Api33.register(a, r);
    }

    static void unregisterBack(Activity a, Object cb) {
        if (Build.VERSION.SDK_INT < 33 || cb == null) return;
        Api33.unregister(a, cb);
    }

    /** Animazioni di apertura/chiusura della schermata (API 34+); sotto si usa overridePendingTransition. */
    static void transitions(Activity a, int openEnter, int openExit, int closeEnter, int closeExit) {
        if (Build.VERSION.SDK_INT < 34) return;
        Api34.apply(a, openEnter, openExit, closeEnter, closeExit);
    }

    @SuppressWarnings("deprecation")
    static void legacyAnim(Activity a, int enter, int exit) {
        if (Build.VERSION.SDK_INT < 34) a.overridePendingTransition(enter, exit);
    }

    private static final class Api30 {
        @SuppressWarnings("deprecation")
        static void apply(Activity a, View root) {
            Window w = a.getWindow();
            w.setDecorFitsSystemWindows(false);
            if (Build.VERSION.SDK_INT < 35) {
                w.setStatusBarColor(0);
                w.setNavigationBarColor(0);
            }
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
            final int l = root.getPaddingLeft(), t = root.getPaddingTop(), r = root.getPaddingRight(), b = root.getPaddingBottom();
            root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                    Insets bars = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    Insets ime = in.getInsets(WindowInsets.Type.ime());
                    v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + Math.max(bars.bottom, ime.bottom));
                    return WindowInsets.CONSUMED;
                }
            });
            root.requestApplyInsets();
        }

        static void appearance(Activity a, boolean lightStatus, boolean lightNav) {
            WindowInsetsController c = a.getWindow().getInsetsController();
            if (c == null) return;
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            int v = (lightStatus ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0)
                    | (lightNav ? WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0);
            c.setSystemBarsAppearance(v, mask);
        }
    }

    private static final class Api33 {
        static Object register(Activity a, final Runnable r) {
            OnBackInvokedCallback cb = new OnBackInvokedCallback() {
                public void onBackInvoked() {
                    r.run();
                }
            };
            a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            return cb;
        }

        static void unregister(Activity a, Object cb) {
            a.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) cb);
        }
    }

    private static final class Api34 {
        static void apply(Activity a, int oe, int ox, int ce, int cx) {
            a.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, oe, ox);
            a.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, ce, cx);
        }
    }
}
