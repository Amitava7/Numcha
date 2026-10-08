package com.numcha;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The mosaic: a month or a year of days in their colours, with a count of how
 * many days were green, yellow, red and black. Tapping a day lists its posts.
 */
public final class CalendarActivity extends Base {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean yearMode;
    private YearMonth month = YearMonth.now();
    private int year = LocalDate.now().getYear();
    private LocalDate selected;

    private Button monthTab, yearTab;
    private TextView heading;
    private MosaicView mosaic;
    private LinearLayout counts, dayPosts;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            yearMode = state.getBoolean("year");
            month = YearMonth.of(state.getInt("y", month.getYear()),
                    state.getInt("m", month.getMonthValue()));
            year = state.getInt("yy", year);
            long sel = state.getLong("sel", Long.MIN_VALUE);
            if (sel != Long.MIN_VALUE) selected = LocalDate.ofEpochDay(sel);
        }

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, 0);

        LinearLayout tabs = Ui.row(this);
        tabs.addView(Ui.text(this, "Calendar", 26f, Ui.TEXT, true), Ui.lp(0, Ui.WRAP, 1f));
        monthTab = Ui.button(this, "Month", Ui.BUTTON, Ui.TEXT);
        yearTab = Ui.button(this, "Year", Ui.BUTTON, Ui.TEXT);
        monthTab.setOnClickListener(v -> {
            yearMode = false;
            refresh();
        });
        yearTab.setOnClickListener(v -> {
            yearMode = true;
            year = month.getYear();
            refresh();
        });
        tabs.addView(monthTab);
        tabs.addView(Ui.gap(this, 8));
        tabs.addView(yearTab);
        root.addView(tabs);

        LinearLayout nav = Ui.row(this);
        nav.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        Button prev = Ui.button(this, "‹", Ui.BG, Ui.ACCENT);
        prev.setTextSize(26f);
        prev.setContentDescription("Previous");
        prev.setOnClickListener(v -> step(-1));
        Button next = Ui.button(this, "›", Ui.BG, Ui.ACCENT);
        next.setTextSize(26f);
        next.setContentDescription("Next");
        next.setOnClickListener(v -> step(1));
        heading = Ui.text(this, "", 20f, Ui.TEXT, true);
        heading.setGravity(Gravity.CENTER);
        nav.addView(prev);
        nav.addView(heading, Ui.lp(0, Ui.WRAP, 1f));
        nav.addView(next);
        root.addView(nav);

        LinearLayout body = Ui.column(this);
        mosaic = new MosaicView(this);
        mosaic.setListener(new MosaicView.Listener() {
            public void onDay(LocalDate day) {
                selected = day;
                mosaic.select(day);
                loadDay();
            }

            public void onMonth(YearMonth m) {
                month = m;
                yearMode = false;
                refresh();
            }
        });
        body.addView(mosaic, Ui.lp(Ui.MATCH, Ui.WRAP));
        counts = Ui.column(this);
        counts.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
        body.addView(counts);
        dayPosts = Ui.column(this);
        dayPosts.setPadding(0, 0, 0, Ui.dp(this, 24));
        body.addView(dayPosts);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        root.addView(scroll, Ui.lp(Ui.MATCH, 0, 1f));
        setContentView(root);
        Ui.fitSystemWindows(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Lock.needsUnlock(this)) refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("year", yearMode);
        out.putInt("y", month.getYear());
        out.putInt("m", month.getMonthValue());
        out.putInt("yy", year);
        if (selected != null) out.putLong("sel", selected.toEpochDay());
    }

    private void step(int dir) {
        if (yearMode) year += dir;
        else month = month.plusMonths(dir);
        refresh();
    }

    private void refresh() {
        monthTab.setTextColor(yearMode ? Ui.DIM : Ui.ACCENT);
        yearTab.setTextColor(yearMode ? Ui.ACCENT : Ui.DIM);
        if (yearMode) {
            heading.setText(String.valueOf(year));
        } else {
            heading.setText(month.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault())
                    + " " + month.getYear());
        }
        final boolean ym = yearMode;
        final YearMonth mo = month;
        final int yr = year;
        final LocalDate from = ym ? LocalDate.of(yr, 1, 1) : mo.atDay(1);
        final LocalDate to = ym ? LocalDate.of(yr, 12, 31) : mo.atEndOfMonth();
        new Thread(() -> {
            final Map<LocalDate, Integer> d = Store.get(getApplicationContext()).days(from, to);
            ui.post(() -> {
                if (isDestroyed() || ym != yearMode || !mo.equals(month) || yr != year) return;
                if (ym) mosaic.showYear(yr, d);
                else mosaic.showMonth(mo, d);
                showCounts(d, from, to);
                if (!ym && selected != null && YearMonth.from(selected).equals(mo)) {
                    mosaic.select(selected);
                    loadDay();
                } else {
                    mosaic.select(null);
                    dayPosts.removeAllViews();
                }
            });
        }, "calendar").start();
    }

    private void showCounts(Map<LocalDate, Integer> d, LocalDate from, LocalDate to) {
        counts.removeAllViews();
        int[] n = new int[5];
        for (Integer m : d.values()) n[m]++;
        LocalDate today = LocalDate.now();
        LocalDate last = to.isAfter(today) ? today : to;
        long sofar = last.isBefore(from) ? 0 : last.toEpochDay() - from.toEpochDay() + 1;
        int written = d.size();
        long blank = Math.max(0, sofar - written);

        for (int m : Post.MOODS) counts.addView(countRow(Post.color(m), Post.name(m), n[m]));
        counts.addView(countRow(Ui.PLAIN, "Posted, no colour", n[Post.NONE]));
        counts.addView(countRow(Ui.EMPTY, "Nothing written", (int) blank));
        long total = to.toEpochDay() - from.toEpochDay() + 1;
        TextView sum = Ui.text(this, written + " of " + total + " days written", 14f, Ui.DIM,
                false);
        sum.setPadding(0, Ui.dp(this, 8), 0, 0);
        counts.addView(sum);
    }

    private View countRow(int color, String name, int count) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
        View sq = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(Ui.dp(this, 4));
        if (color == Ui.BLACK) g.setStroke(Ui.dp(this, 1), Ui.BLACK_EDGE);
        sq.setBackground(g);
        LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(this, 18), Ui.dp(this, 18));
        lp.rightMargin = Ui.dp(this, 12);
        row.addView(sq, lp);
        row.addView(Ui.text(this, name, 15f, Ui.TEXT, false), Ui.lp(0, Ui.WRAP, 1f));
        row.addView(Ui.text(this, count + (count == 1 ? " day" : " days"), 15f, Ui.TEXT, true));
        return row;
    }

    private void loadDay() {
        final LocalDate day = selected;
        if (day == null) return;
        new Thread(() -> {
            final List<Post> list = Store.get(getApplicationContext()).between(day, day);
            ui.post(() -> {
                if (isDestroyed() || !day.equals(selected)) return;
                showDay(day, list);
            });
        }, "day").start();
    }

    private void showDay(final LocalDate day, List<Post> list) {
        dayPosts.removeAllViews();
        TextView h = Ui.text(this,
                day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
                18f, Ui.TEXT, true);
        h.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 8));
        dayPosts.addView(h);
        if (list.isEmpty()) {
            dayPosts.addView(Ui.text(this, "Nothing written on this day.", 15f, Ui.DIM, false));
        }
        for (final Post p : list) {
            LinearLayout c = Ui.row(this);
            c.setBackground(Ui.card(this));
            int pd = Ui.dp(this, 12);
            c.setPadding(pd, pd, pd, pd);
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            if (p.mood == Post.NONE) g.setStroke(Ui.dp(this, 2), Ui.PLAIN);
            else g.setColor(Post.color(p.mood));
            if (p.mood == Post.BLACK) g.setStroke(Ui.dp(this, 1), Ui.BLACK_EDGE);
            dot.setBackground(g);
            LinearLayout.LayoutParams dl = Ui.lp(Ui.dp(this, 12), Ui.dp(this, 12));
            dl.rightMargin = Ui.dp(this, 10);
            c.addView(dot, dl);
            String what = !p.title.isEmpty() ? p.title : !p.body.isEmpty() ? p.body
                    : p.photo != null ? "Picture" : Post.name(p.mood);
            TextView t = Ui.text(this, p.timeText() + "  " + what, 15f, Ui.TEXT, false);
            t.setMaxLines(2);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            c.addView(t, Ui.lp(0, Ui.WRAP, 1f));
            c.setClickable(true);
            c.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class)
                    .putExtra(EditActivity.EXTRA_ID, p.id)));
            LinearLayout.LayoutParams lp = Ui.lp(Ui.MATCH, Ui.WRAP);
            lp.bottomMargin = Ui.dp(this, 8);
            dayPosts.addView(c, lp);
        }
        if (!day.isAfter(LocalDate.now())) {
            Button write = Ui.button(this, "Write for this day", Ui.BUTTON, Ui.ACCENT);
            write.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class)
                    .putExtra(EditActivity.EXTRA_DAY, day.toEpochDay())));
            dayPosts.addView(write, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
    }
}
