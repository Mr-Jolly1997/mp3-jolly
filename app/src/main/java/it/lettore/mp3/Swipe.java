package it.lettore.mp3;

import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

/** Scorrimento orizzontale per cambiare brano (mini player, copertina) e tocco semplice. */
final class Swipe implements View.OnTouchListener {
    interface Callback {
        void onTap();
        void onSwipe(boolean toNext);
    }

    private final View target;
    private final Callback cb;
    private final int slop;
    private final float minFling;
    private float downX, downY;
    private boolean dragging;
    private VelocityTracker vt;

    private Swipe(View touch, View target, Callback cb) {
        this.target = target;
        this.cb = cb;
        slop = ViewConfiguration.get(touch.getContext()).getScaledTouchSlop();
        minFling = 900 * touch.getResources().getDisplayMetrics().density;
    }

    static void attach(View touch, View target, Callback cb) {
        touch.setOnTouchListener(new Swipe(touch, target, cb));
        touch.setClickable(true);
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                dragging = false;
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                v.drawableHotspotChanged(e.getX(), e.getY());
                v.setPressed(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (vt != null) vt.addMovement(e);
                float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    v.setPressed(false);
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                } else if (!dragging && Math.abs(dy) > slop) {
                    v.setPressed(false);
                }
                if (dragging) {
                    target.setTranslationX(dx * 0.85f);
                    target.setAlpha(1f - Math.min(0.7f, Math.abs(dx) / Math.max(1f, target.getWidth())));
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                v.setPressed(false);
                if (dragging) {
                    float vx = 0;
                    if (vt != null) {
                        vt.addMovement(e);
                        vt.computeCurrentVelocity(1000);
                        vx = vt.getXVelocity();
                    }
                    boolean far = Math.abs(dx) > target.getWidth() * 0.28f;
                    boolean fast = Math.abs(vx) > minFling && Math.signum(vx) == Math.signum(dx);
                    if (far || fast) slideOut(dx < 0);
                    else settle();
                } else if (Math.abs(dx) < slop && Math.abs(dy) < slop) {
                    cb.onTap();
                }
                recycle();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                v.setPressed(false);
                if (dragging) settle();
                recycle();
                return true;
        }
        return false;
    }

    private void recycle() {
        dragging = false;
        if (vt != null) {
            vt.recycle();
            vt = null;
        }
    }

    private void settle() {
        target.animate().translationX(0).alpha(1f).setDuration(180).start();
    }

    private void slideOut(final boolean toNext) {
        final float w = Math.max(target.getWidth(), 1);
        target.animate().translationX(toNext ? -w : w).alpha(0f).setDuration(150).withEndAction(new Runnable() {
            public void run() {
                cb.onSwipe(toNext);
                target.setTranslationX(toNext ? w * 0.4f : -w * 0.4f);
                target.animate().translationX(0).alpha(1f).setDuration(220).start();
            }
        }).start();
    }
}
