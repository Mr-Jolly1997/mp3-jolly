package it.lettore.mp3;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SectionIndexer;

/** Viste disegnate a mano. */
final class Views {
    private Views() {}

    /** ImageView quadrata: lato = larghezza (byWidth) oppure il minore tra larghezza e altezza. */
    static final class SquareImageView extends ImageView {
        private final boolean byWidth;

        SquareImageView(Context c, boolean byWidth) {
            super(c);
            this.byWidth = byWidth;
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, h);
            int s = byWidth ? getMeasuredWidth() : Math.min(getMeasuredWidth(), getMeasuredHeight());
            setMeasuredDimension(s, s);
        }
    }

    /** Tre barrette animate che indicano il brano in riproduzione. */
    static final class PlayingBars extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private boolean playing;

        PlayingBars(Context c) {
            super(c);
            paint.setColor(Ui.PLAYING);
        }

        void setColor(int c) {
            paint.setColor(c);
            invalidate();
        }

        void setPlaying(boolean p) {
            if (p != playing) {
                playing = p;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float size = Math.min(w, h) * 0.5f;
            float left = (w - size) / 2f, bottom = (h + size) / 2f;
            float bw = size / 4.2f, gap = (size - 3 * bw) / 2f;
            long t = SystemClock.uptimeMillis();
            for (int i = 0; i < 3; i++) {
                float f;
                if (playing) {
                    double ph = t / (170.0 + i * 55.0) + i * 1.7;
                    f = 0.25f + 0.75f * (float) Math.abs(Math.sin(ph));
                } else {
                    f = i == 1 ? 0.6f : 0.35f;
                }
                float x = left + i * (bw + gap);
                r.set(x, bottom - size * f, x + bw, bottom);
                c.drawRoundRect(r, bw / 2, bw / 2, paint);
            }
            if (playing) postInvalidateOnAnimation();
        }
    }

    /** Curva dell'equalizzatore: un punto trascinabile per ogni banda. */
    static final class EqView extends View {
        interface Listener {
            void onBand(int band, short level);
            void onTouchEnd();
        }

        private short[] levels = new short[0];
        private int[] freqs = new int[0];
        private short min = -1500, max = 1500;
        private boolean active = true;
        private int touched = -1;
        private Listener listener;

        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint zero = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint curve = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotInner = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Path fillPath = new Path();
        private final float d;

        EqView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            grid.setColor(Ui.alpha(Ui.TEXT, 0x18));
            grid.setStrokeWidth(d);
            zero.setColor(Ui.alpha(Ui.TEXT, 0x40));
            zero.setStrokeWidth(d);
            curve.setStyle(Paint.Style.STROKE);
            curve.setStrokeWidth(3 * d);
            curve.setStrokeCap(Paint.Cap.ROUND);
            dotInner.setColor(Ui.BG);
            label.setColor(Ui.TEXT2);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, c.getResources().getDisplayMetrics()));
            value.setTextAlign(Paint.Align.CENTER);
            value.setTypeface(Ui.MEDIUM);
            value.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, c.getResources().getDisplayMetrics()));
            updateColors();
        }

        void setListener(Listener l) {
            listener = l;
        }

        void setData(short[] levels, int[] freqs, short min, short max) {
            this.levels = levels.clone();
            this.freqs = freqs;
            this.min = min;
            this.max = max;
            invalidate();
        }

        void setActive(boolean a) {
            active = a;
            updateColors();
            invalidate();
        }

        private void updateColors() {
            int col = active ? Ui.ACCENT : Ui.TEXT3;
            curve.setColor(col);
            dot.setColor(col);
            halo.setColor((col & 0x00FFFFFF) | 0x40000000);
            value.setColor(active ? Ui.TEXT : Ui.TEXT2);
            fill.setShader(null);
        }

        private float padT() { return 30 * d; }
        private float padB() { return 30 * d; }
        private float padX() { return 6 * d; }

        private float xOf(int i) {
            float w = getWidth() - 2 * padX();
            return padX() + w * (i + 0.5f) / Math.max(1, levels.length);
        }

        private float yOf(short lv) {
            float h = getHeight() - padT() - padB();
            return padT() + h * (1f - (lv - min) / (float) Math.max(1, max - min));
        }

        private short levelOf(float y) {
            float h = getHeight() - padT() - padB();
            float f = 1f - (y - padT()) / h;
            int lv = Math.round(min + f * (max - min));
            lv = Math.round(lv / 100f) * 100; // passi da 1 dB
            return (short) Math.max(min, Math.min(max, lv));
        }

        @Override
        protected void onDraw(Canvas c) {
            int n = levels.length;
            if (n == 0) return;
            float left = padX(), right = getWidth() - padX();
            float top = padT(), bottom = getHeight() - padB();

            // griglia
            for (int k = 0; k <= 4; k++) {
                float y = top + (bottom - top) * k / 4f;
                c.drawLine(left, y, right, y, k == 2 ? zero : grid);
            }
            for (int i = 0; i < n; i++) c.drawLine(xOf(i), top, xOf(i), bottom, grid);

            // curva morbida
            path.reset();
            path.moveTo(left, yOf(levels[0]));
            path.lineTo(xOf(0), yOf(levels[0]));
            for (int i = 1; i < n; i++) {
                float x0 = xOf(i - 1), y0 = yOf(levels[i - 1]);
                float x1 = xOf(i), y1 = yOf(levels[i]);
                float mx = (x0 + x1) / 2f;
                path.cubicTo(mx, y0, mx, y1, x1, y1);
            }
            path.lineTo(right, yOf(levels[n - 1]));
            fillPath.set(path);
            fillPath.lineTo(right, bottom);
            fillPath.lineTo(left, bottom);
            fillPath.close();
            int col = curve.getColor();
            fill.setShader(new LinearGradient(0, top, 0, bottom, (col & 0x00FFFFFF) | 0x55000000,
                    col & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawPath(fillPath, fill);
            c.drawPath(path, curve);

            // punti, valori e frequenze
            for (int i = 0; i < n; i++) {
                float x = xOf(i), y = yOf(levels[i]);
                if (i == touched) c.drawCircle(x, y, 22 * d, halo);
                c.drawCircle(x, y, (i == touched ? 12 : 10) * d, dot);
                c.drawCircle(x, y, 4 * d, dotInner);
                c.drawText(AudioFx.dbLabel(levels[i]) + " dB", x, top - 12 * d, value);
                String f = i < freqs.length ? AudioFx.freqLabel(freqs[i]) : "";
                c.drawText(f + (f.endsWith("k") ? "Hz" : " Hz"), x, bottom + 22 * d, label);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int n = levels.length;
            if (n == 0) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    float best = Float.MAX_VALUE;
                    for (int i = 0; i < n; i++) {
                        float dd = Math.abs(e.getX() - xOf(i));
                        if (dd < best) {
                            best = dd;
                            touched = i;
                        }
                    }
                    move(e.getY());
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    move(e.getY());
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    touched = -1;
                    invalidate();
                    if (listener != null) listener.onTouchEnd();
                    return true;
            }
            return false;
        }

        private void move(float y) {
            if (touched < 0) return;
            short lv = levelOf(y);
            if (lv != levels[touched]) {
                levels[touched] = lv;
                if (listener != null) listener.onBand(touched, lv);
            }
            invalidate();
        }
    }

    /**
     * Barra di scorrimento veloce: compare mentre scorri, si trascina dalla "maniglia" e mostra la lettera.
     * A differenza di quella di sistema prende il tocco SOLO sulla maniglia visibile, così i pulsanti ⋮
     * a destra delle righe restano sempre toccabili.
     */
    static final class FastScroll extends View {
        private AbsListView list;
        private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubble = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubbleText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float d, thumbW, thumbH, touchW, margin;
        private float alphaF;
        private boolean dragging;
        private float thumbTop;
        private String label;
        private final Runnable hide = new Runnable() {
            public void run() {
                if (!dragging) fade(0f);
            }
        };
        private android.animation.ValueAnimator anim;

        FastScroll(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            thumbW = 5 * d;
            thumbH = 46 * d;
            touchW = 30 * d;
            margin = 4 * d;
            thumb.setColor(Ui.ACCENT);
            bubble.setColor(Ui.ACCENT);
            bubbleText.setColor(Ui.ON_ACCENT);
            bubbleText.setTextAlign(Paint.Align.CENTER);
            bubbleText.setTypeface(Ui.BOLD);
            bubbleText.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 26, c.getResources().getDisplayMetrics()));
        }

        void attach(AbsListView l) {
            list = l;
        }

        /** Da chiamare dall'OnScrollListener della lista. */
        void onListScrolled() {
            if (!isLong()) {
                if (alphaF > 0) fade(0f);
                return;
            }
            if (alphaF < 1f && (anim == null || !anim.isRunning())) fade(1f);
            removeCallbacks(hide);
            if (!dragging) postDelayed(hide, 1400);
            invalidate();
        }

        private boolean isLong() {
            if (list == null || list.getAdapter() == null) return false;
            int count = list.getCount(), vis = list.getChildCount();
            return vis > 0 && count > vis * 3;
        }

        private void fade(float to) {
            if (anim != null) anim.cancel();
            anim = android.animation.ValueAnimator.ofFloat(alphaF, to);
            anim.setDuration(to > alphaF ? 120 : 300);
            anim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    alphaF = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        }

        private float top() { return list == null ? 0 : list.getPaddingTop() + 6 * d; }
        private float bottom() { return getHeight() - (list == null ? 0 : list.getPaddingBottom()) - 6 * d; }

        private float fraction() {
            int first = list.getFirstVisiblePosition(), count = list.getCount(), vis = list.getChildCount();
            View c0 = list.getChildAt(0);
            float partial = c0 == null || c0.getHeight() == 0 ? 0 : -c0.getTop() / (float) c0.getHeight();
            float f = (first + partial) / Math.max(1f, count - vis);
            return Math.max(0f, Math.min(1f, f));
        }

        @Override
        protected void onDraw(Canvas c) {
            if (alphaF <= 0.01f || list == null) return;
            float t = top(), b = bottom();
            if (!dragging) thumbTop = t + fraction() * (b - t - thumbH);
            float right = getWidth() - margin;
            float w = dragging ? thumbW * 1.6f : thumbW;
            r.set(right - w, thumbTop, right, thumbTop + thumbH);
            thumb.setAlpha((int) (255 * alphaF));
            c.drawRoundRect(r, w / 2, w / 2, thumb);
            if (dragging && label != null) {
                float cy = Math.max(32 * d, Math.min(getHeight() - 32 * d, thumbTop + thumbH / 2));
                float cx = right - 60 * d;
                c.drawCircle(cx, cy, 30 * d, bubble);
                Paint.FontMetrics fm = bubbleText.getFontMetrics();
                c.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2, bubbleText);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    if (alphaF < 0.5f || list == null || !isLong()) return false;
                    if (e.getX() < getWidth() - touchW) return false;
                    float tc = thumbTop + thumbH / 2;
                    if (Math.abs(e.getY() - tc) > thumbH) return false;
                    dragging = true;
                    removeCallbacks(hide);
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    drag(e.getY());
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    if (dragging) drag(e.getY());
                    return dragging;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!dragging) return false;
                    dragging = false;
                    label = null;
                    postDelayed(hide, 1200);
                    invalidate();
                    return true;
            }
            return false;
        }

        private void drag(float y) {
            float t = top(), b = bottom();
            float f = (y - thumbH / 2 - t) / Math.max(1f, b - t - thumbH);
            f = Math.max(0f, Math.min(1f, f));
            thumbTop = t + f * (b - t - thumbH);
            int count = list.getCount();
            int pos = Math.round(f * (count - 1));
            list.setSelection(pos);
            label = null;
            Adapter a = list.getAdapter();
            if (a instanceof HeaderViewListAdapter) a = ((HeaderViewListAdapter) a).getWrappedAdapter();
            if (a instanceof SectionIndexer) {
                SectionIndexer si = (SectionIndexer) a;
                Object[] secs = si.getSections();
                if (secs != null && secs.length > 0) {
                    int s = si.getSectionForPosition(pos);
                    if (s >= 0 && s < secs.length) label = String.valueOf(secs[s]);
                }
            }
            invalidate();
        }
    }

    /**
     * Lista con "striscia per aggiungere alla coda": trascinando un brano da sinistra verso destra si
     * scopre una fascia colorata; superata la soglia il telefono vibra leggermente e, rilasciando,
     * il brano va in coda. Lo scorrimento verticale, il tocco e la pressione lunga restano normali.
     */
    static final class SwipeListView extends ListView {
        interface SwipeListener {
            boolean canSwipe(int position);
            void onSwiped(int position);
        }

        private SwipeListener listener;
        private final int slop;
        private final float d;
        private float downX, downY;
        private int downPos = INVALID_POSITION;
        private boolean swiping, armed;
        private View swipeView;
        private android.animation.ValueAnimator back;
        private final Paint reveal = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.drawable.Drawable icon;

        SwipeListView(Context c) {
            super(c);
            slop = android.view.ViewConfiguration.get(c).getScaledTouchSlop();
            d = c.getResources().getDisplayMetrics().density;
            icon = c.getDrawable(R.drawable.ic_queue_add).mutate();
            label.setTypeface(Ui.MEDIUM);
            label.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14, c.getResources().getDisplayMetrics()));
        }

        void setSwipeListener(SwipeListener l) {
            listener = l;
        }

        private float threshold() {
            return Math.max(96 * d, getWidth() * 0.26f);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    downPos = pointToPosition((int) downX, (int) downY);
                    swiping = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!swiping && maybeStart(e)) return true;
                    break;
            }
            return super.onInterceptTouchEvent(e);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    if (!swiping && maybeStart(e)) cancelSuper(e);
                    if (swiping) {
                        follow(e.getX() - downX);
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (swiping) {
                        boolean add = armed && e.getActionMasked() == MotionEvent.ACTION_UP;
                        int pos = downPos;
                        release();
                        if (add && listener != null) listener.onSwiped(pos);
                        return true;
                    }
                    break;
            }
            return super.onTouchEvent(e);
        }

        private boolean maybeStart(MotionEvent e) {
            if (listener == null || downPos == INVALID_POSITION) return false;
            float dx = e.getX() - downX, dy = e.getY() - downY;
            if (dx > slop * 1.5f && dx > Math.abs(dy) * 1.8f && listener.canSwipe(downPos)) {
                View v = getChildAt(downPos - getFirstVisiblePosition());
                if (v == null) return false;
                if (back != null) back.cancel();
                swiping = true;
                armed = false;
                swipeView = v;
                downX = e.getX() - slop; // niente "salto" iniziale
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            return false;
        }

        /** Annulla tocco/pressione lunga già avviati dalla lista. */
        private void cancelSuper(MotionEvent e) {
            MotionEvent c = MotionEvent.obtain(e);
            c.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(c);
            c.recycle();
            setPressed(false);
            if (swipeView != null) swipeView.setPressed(false);
        }

        private void follow(float dx) {
            if (swipeView == null) return;
            float max = getWidth() * 0.55f;
            float t = Math.max(0, dx);
            if (t > threshold()) t = threshold() + (t - threshold()) * 0.45f; // resistenza oltre la soglia
            t = Math.min(t, max);
            swipeView.setTranslationX(t);
            boolean nowArmed = t >= threshold();
            if (nowArmed != armed) {
                armed = nowArmed;
                performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            }
            invalidate();
        }

        private void release() {
            swiping = false;
            final View v = swipeView;
            if (v == null) return;
            back = android.animation.ValueAnimator.ofFloat(v.getTranslationX(), 0f);
            back.setDuration(220);
            back.setInterpolator(new android.view.animation.DecelerateInterpolator());
            back.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    v.setTranslationX((Float) a.getAnimatedValue());
                    invalidate();
                }
            });
            back.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator a) {
                    v.setTranslationX(0f);
                    if (swipeView == v) swipeView = null;
                    armed = false;
                    invalidate();
                }
            });
            back.start();
        }

        @Override
        protected void dispatchDraw(Canvas c) {
            View v = swipeView;
            if (v != null && v.getParent() == this && v.getTranslationX() > 0.5f) {
                float tx = v.getTranslationX();
                float l = v.getLeft(), top = v.getTop(), b = v.getBottom();
                reveal.setColor(armed ? Ui.ACCENT : Ui.ACCENT_SOFT);
                c.drawRect(l, top, l + tx, b, reveal);
                int fg = armed ? Ui.ON_ACCENT : Ui.ACCENT;
                int s = (int) (24 * d);
                int ix = (int) (l + 20 * d), iy = (int) ((top + b) / 2 - s / 2f);
                if (tx > 20 * d + s) {
                    icon.setTint(fg);
                    icon.setBounds(ix, iy, ix + s, iy + s);
                    icon.draw(c);
                }
                String txt = armed ? "Rilascia: in coda" : "Aggiungi alla coda";
                float tw = label.measureText(txt);
                if (tx > 20 * d + s + 12 * d + tw + 12 * d) {
                    label.setColor(fg);
                    Paint.FontMetrics fm = label.getFontMetrics();
                    c.drawText(txt, ix + s + 12 * d, (top + b) / 2f - (fm.ascent + fm.descent) / 2f, label);
                }
            }
            super.dispatchDraw(c);
        }
    }
}
