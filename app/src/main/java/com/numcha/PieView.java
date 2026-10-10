package com.numcha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * A donut of the written days by colour. Only colours with at least one day
 * get a slice; each slice carries its count, and the middle the total. Tapping
 * a slice reports its colour, and the chosen slice stands out while the rest
 * fade back.
 */
final class PieView extends View {

    interface Listener {
        void onSlice(int mood);
    }

    private final int[] counts = new int[Post.MOODS.length];
    private int total;
    private int selected = Post.ANY;
    private Listener listener;

    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    PieView(Context c) {
        super(c);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.BUTT);
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(Ui.BLACK_EDGE);
        label.setTextAlign(Paint.Align.CENTER);
        setContentDescription("Colour chart");
    }

    void setListener(Listener l) {
        listener = l;
    }

    /** counts[i] is the number of days of colour Post.MOODS[i]. */
    void setCounts(int[] c) {
        total = 0;
        for (int i = 0; i < counts.length; i++) {
            counts[i] = c[i];
            total += c[i];
        }
        StringBuilder sb = new StringBuilder("Colour chart: ");
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) sb.append(Post.name(Post.MOODS[i])).append(' ')
                    .append(counts[i]).append(", ");
        }
        setContentDescription(sb.toString());
        invalidate();
    }

    void select(int mood) {
        selected = mood;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int s = Ui.dp(getContext(), 168);
        setMeasuredDimension(resolveSize(s, wSpec), resolveSize(s, hSpec));
    }

    private float ring() {
        return Math.min(getWidth(), getHeight()) * 0.2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (total == 0) return;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float ring = ring();
        float r = Math.min(cx, cy) - ring / 2f - Ui.dp(getContext(), 6);
        oval.set(cx - r, cy - r, cx + r, cy + r);
        arc.setStrokeWidth(ring);

        int slices = 0;
        for (int n : counts) if (n > 0) slices++;
        // a 2dp gap of page colour between neighbouring slices, as an angle
        float gapDeg = slices > 1 ? (float) Math.toDegrees(Ui.dp(getContext(), 2) / r) : 0f;

        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextSize(Ui.dp(getContext(), 13));
        float start = -90f;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == 0) continue;
            int mood = Post.MOODS[i];
            float sweep = 360f * counts[i] / total;
            boolean faded = selected != Post.ANY && selected != mood;
            boolean chosen = selected == mood;
            arc.setColor(Post.color(mood));
            arc.setAlpha(faded ? 70 : 255);
            float s0 = start + gapDeg / 2f, sw = Math.max(0.5f, sweep - gapDeg);
            if (chosen) {
                arc.setStrokeWidth(ring * 1.25f);
            }
            canvas.drawArc(oval, s0, sw, false, arc);
            arc.setStrokeWidth(ring);
            if (mood == Post.BLACK && !faded) {
                // black barely shows on the dark page, so it gets an outline
                float half = (chosen ? ring * 1.25f : ring) / 2f;
                edge.setStrokeWidth(Math.max(1f, Ui.dp(getContext(), 1)));
                RectF in = new RectF(oval), out = new RectF(oval);
                in.inset(half, half);
                out.inset(-half, -half);
                canvas.drawArc(out, s0, sw, false, edge);
                canvas.drawArc(in, s0, sw, false, edge);
            }
            if (sweep >= 22f) {
                double mid = Math.toRadians(start + sweep / 2f);
                float lx = cx + (float) Math.cos(mid) * r;
                float ly = cy + (float) Math.sin(mid) * r;
                label.setColor(Post.ink(mood));
                label.setAlpha(faded ? 110 : 255);
                canvas.drawText(String.valueOf(counts[i]), lx,
                        ly - (label.descent() + label.ascent()) / 2, label);
            }
            start += sweep;
        }

        label.setAlpha(255);
        label.setColor(Ui.TEXT);
        label.setTextSize(Ui.dp(getContext(), 26));
        canvas.drawText(String.valueOf(total), cx, cy - Ui.dp(getContext(), 2), label);
        label.setTypeface(Typeface.DEFAULT);
        label.setColor(Ui.DIM);
        label.setTextSize(Ui.dp(getContext(), 12));
        canvas.drawText(total == 1 ? "day" : "days", cx, cy + Ui.dp(getContext(), 16), label);
    }

    /** The colour under a point, or {@link Post#ANY} for the hole and outside. */
    int moodAt(float x, float y) {
        if (total == 0) return Post.ANY;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float ring = ring();
        float r = Math.min(cx, cy) - ring / 2f - Ui.dp(getContext(), 6);
        double d = Math.hypot(x - cx, y - cy);
        // generous target: the whole ring plus a margin either side
        if (d < r - ring || d > r + ring) return Post.ANY;
        double deg = Math.toDegrees(Math.atan2(y - cy, x - cx)) + 90.0;
        if (deg < 0) deg += 360.0;
        double at = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == 0) continue;
            at += 360.0 * counts[i] / total;
            if (deg < at) return Post.MOODS[i];
        }
        return Post.ANY;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return total > 0;
        if (e.getAction() != MotionEvent.ACTION_UP || listener == null) return false;
        int m = moodAt(e.getX(), e.getY());
        if (m != Post.ANY) {
            performClick();
            listener.onSlice(m);
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
