package com.numcha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Days as coloured squares. Month mode is a seven-column calendar with the
 * day numbers in it. Year mode is twelve small months, one square a day; tap a
 * month's name for that month, or a square for that day.
 *
 * A day takes the colour of its latest post; a day with nothing is a faint
 * square; future days are an outline only.
 */
final class MosaicView extends View {

    interface Listener {
        void onDay(LocalDate day);

        void onMonth(YearMonth month);
    }

    private boolean yearMode;
    private YearMonth month = YearMonth.now();
    private int year = LocalDate.now().getYear();
    private Map<LocalDate, Integer> days = Collections.emptyMap();
    private LocalDate selected;
    private YearMonth selectedMonth;
    private Listener listener;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float gap, radius;

    // where things are, worked out from the current size by place()
    private float cell, top, blockW, blockH, headH;

    MosaicView(Context c) {
        super(c);
        gap = Ui.dp(c, 4);
        radius = Ui.dp(c, 4);
        edge.setStyle(Paint.Style.STROKE);
        label.setTextAlign(Paint.Align.CENTER);
        setContentDescription("Calendar");
    }

    void setListener(Listener l) {
        listener = l;
    }

    void showMonth(YearMonth m, Map<LocalDate, Integer> d) {
        yearMode = false;
        month = m;
        days = d;
        requestLayout();
        invalidate();
    }

    void showYear(int y, Map<LocalDate, Integer> d) {
        yearMode = true;
        year = y;
        days = d;
        requestLayout();
        invalidate();
    }

    void select(LocalDate d) {
        selected = d;
        invalidate();
    }

    void selectMonth(YearMonth m) {
        selectedMonth = m;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h;
        if (yearMode) {
            // 3 columns of months, each 7 squares wide; 4 rows, each a title + 6 weeks
            float c = (w - gap * 2 * 3) / 21f;
            float head = Ui.dp(getContext(), 22);
            h = (int) Math.ceil(4 * (head + 6 * c + gap * 2));
        } else {
            float c = w / 7f;
            h = (int) Math.ceil(Ui.dp(getContext(), 24) + 6 * c);
        }
        setMeasuredDimension(w, h);
    }

    /**
     * Works out the square size and offsets for the current mode and width.
     * Drawing and hit testing both call it, so a tap that arrives before the
     * next frame (say, just after switching month and year) still lands right.
     */
    private void place() {
        float w = getWidth();
        if (yearMode) {
            blockW = w / 3f;
            cell = (blockW - gap * 2) / 7f;
            headH = Ui.dp(getContext(), 22);
            blockH = headH + 6 * cell + gap * 2;
        } else {
            cell = w / 7f;
            headH = Ui.dp(getContext(), 24);
            top = headH;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        place();
        if (yearMode) drawYear(canvas);
        else drawMonth(canvas);
    }

    private void drawMonth(Canvas canvas) {
        label.setColor(Ui.DIM);
        label.setTextSize(Ui.dp(getContext(), 12));
        label.setTypeface(Typeface.DEFAULT_BOLD);
        for (int i = 0; i < 7; i++) {
            String s = DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault());
            canvas.drawText(s, cell * i + cell / 2, headH - Ui.dp(getContext(), 8), label);
        }
        int offset = month.atDay(1).getDayOfWeek().getValue() - 1;  // Monday first
        LocalDate today = LocalDate.now();
        label.setTextSize(cell * 0.3f);
        label.setTypeface(Typeface.DEFAULT);
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            int i = offset + d - 1;
            float x = (i % 7) * cell, y = top + (i / 7) * cell;
            LocalDate day = month.atDay(d);
            square(canvas, day, x, y, cell, today);
            Integer m = days.get(day);
            label.setColor(m != null ? Post.ink(m) : Ui.DIM);
            canvas.drawText(String.valueOf(d), x + cell / 2,
                    y + cell / 2 - (label.descent() + label.ascent()) / 2, label);
        }
    }

    private void drawYear(Canvas canvas) {
        LocalDate today = LocalDate.now();
        label.setTextSize(Ui.dp(getContext(), 13));
        label.setTypeface(Typeface.DEFAULT_BOLD);
        for (int mo = 1; mo <= 12; mo++) {
            YearMonth ym = YearMonth.of(year, mo);
            float bx = ((mo - 1) % 3) * blockW + gap, by = ((mo - 1) / 3) * blockH;
            label.setColor(ym.equals(selectedMonth) ? Ui.TEXT
                    : ym.equals(YearMonth.from(today)) ? Ui.ACCENT : Ui.DIM);
            canvas.drawText(ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    bx + 7 * cell / 2, by + headH - Ui.dp(getContext(), 7), label);
            int offset = ym.atDay(1).getDayOfWeek().getValue() - 1;
            for (int d = 1; d <= ym.lengthOfMonth(); d++) {
                int i = offset + d - 1;
                square(canvas, ym.atDay(d), bx + (i % 7) * cell, by + headH + (i / 7) * cell,
                        cell, today);
            }
        }
    }

    private void square(Canvas canvas, LocalDate day, float x, float y, float size,
                        LocalDate today) {
        float g = yearMode ? Math.max(1f, size * 0.12f) : gap;
        r.set(x + g / 2, y + g / 2, x + size - g / 2, y + size - g / 2);
        float rad = yearMode ? Math.min(radius, size * 0.2f) : radius * 1.5f;
        Integer m = days.get(day);
        if (day.isAfter(today) && m == null) {
            edge.setColor(Ui.EMPTY);
            edge.setStrokeWidth(Math.max(1f, size * 0.05f));
            canvas.drawRoundRect(r, rad, rad, edge);
            return;
        }
        fill.setColor(m == null ? Ui.EMPTY : Post.color(m));
        canvas.drawRoundRect(r, rad, rad, fill);
        if (m != null && m == Post.BLACK) {
            edge.setColor(Ui.BLACK_EDGE);
            edge.setStrokeWidth(Math.max(1f, size * 0.04f));
            canvas.drawRoundRect(r, rad, rad, edge);
        }
        if (day.equals(today) || day.equals(selected)) {
            edge.setColor(day.equals(selected) ? Ui.TEXT : Ui.ACCENT);
            edge.setStrokeWidth(Math.max(2f, size * (yearMode ? 0.12f : 0.05f)));
            canvas.drawRoundRect(r, rad, rad, edge);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() != MotionEvent.ACTION_UP || listener == null) return false;
        if (getWidth() == 0) return true;
        place();
        float x = e.getX(), y = e.getY();
        if (yearMode) {
            int col = (int) (x / blockW), row = (int) (y / blockH);
            if (col < 0 || col > 2 || row < 0 || row > 3) return true;
            YearMonth ym = YearMonth.of(year, row * 3 + col + 1);
            float bx = col * blockW + gap, by = row * blockH;
            if (y - by < headH) {               // the month's name
                performClick();
                listener.onMonth(ym);
                return true;
            }
            int c = (int) ((x - bx) / cell), r = (int) ((y - by - headH) / cell);
            int offset = ym.atDay(1).getDayOfWeek().getValue() - 1;
            int d = r * 7 + c - offset + 1;
            if (c >= 0 && c < 7 && r >= 0 && d >= 1 && d <= ym.lengthOfMonth()) {
                performClick();
                listener.onDay(ym.atDay(d));
            }
            return true;
        }
        if (y < top) return true;
        int col = (int) (x / cell), row = (int) ((y - top) / cell);
        int offset = month.atDay(1).getDayOfWeek().getValue() - 1;
        int d = row * 7 + col - offset + 1;
        if (col >= 0 && col < 7 && d >= 1 && d <= month.lengthOfMonth()) {
            performClick();
            listener.onDay(month.atDay(d));
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
