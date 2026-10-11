package com.numcha;

import android.content.Intent;
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
 * The mosaic: a month or a year of days in their colours, and a donut of how
 * many of the written days were exceptional, green, yellow, red and black.
 *
 * Below it, a list of posts for whatever was tapped last: a day, a month (the
 * heading in month view, a month's name in year view), the year (the heading in
 * year view), or a colour in the donut, which lists that colour's posts in the
 * month or year on screen.
 */
public final class CalendarActivity extends Base {

    private static final int NONE = 0, DAY = 1, MONTH = 2, YEAR = 3, COLOUR = 4;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean yearMode;
    private YearMonth month = YearMonth.now();
    private int year = LocalDate.now().getYear();

    // what the list under the chart shows
    private int pick = NONE;
    private LocalDate pickDay;
    private YearMonth pickMonth;
    private int pickMood = Post.ANY;

    private Button monthTab, yearTab;
    private TextView heading, summary;
    private MosaicView mosaic;
    private PieView pie;
    private LinearLayout chartRow, legend, listBox;
    private ScrollView scroll;
    private int loads;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            yearMode = state.getBoolean("year");
            month = YearMonth.of(state.getInt("y", month.getYear()),
                    state.getInt("m", month.getMonthValue()));
            year = state.getInt("yy", year);
            pick = state.getInt("pick", NONE);
            long d = state.getLong("pd", Long.MIN_VALUE);
            if (d != Long.MIN_VALUE) pickDay = LocalDate.ofEpochDay(d);
            if (state.containsKey("pmy")) {
                pickMonth = YearMonth.of(state.getInt("pmy"), state.getInt("pmm"));
            }
            pickMood = state.getInt("pmood", Post.ANY);
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
            if (!yearMode) return;
            yearMode = false;
            if (pick == COLOUR) pick = NONE;
            refresh();
        });
        yearTab.setOnClickListener(v -> {
            if (yearMode) return;
            yearMode = true;
            year = month.getYear();
            if (pick == COLOUR) pick = NONE;
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
        heading.setClickable(true);
        heading.setMinHeight(Ui.dp(this, 44));
        heading.setOnClickListener(v -> {
            if (yearMode) choose(YEAR, null, null, Post.ANY);
            else choose(MONTH, null, month, Post.ANY);
        });
        nav.addView(prev);
        nav.addView(heading, Ui.lp(0, Ui.WRAP, 1f));
        nav.addView(next);
        root.addView(nav);

        LinearLayout body = Ui.column(this);
        mosaic = new MosaicView(this);
        mosaic.setListener(new MosaicView.Listener() {
            public void onDay(LocalDate day) {
                choose(DAY, day, null, Post.ANY);
            }

            public void onMonth(YearMonth m) {
                choose(MONTH, null, m, Post.ANY);
            }
        });
        body.addView(mosaic, Ui.lp(Ui.MATCH, Ui.WRAP));

        summary = Ui.text(this, "", 15f, Ui.TEXT, true);
        summary.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
        body.addView(summary);

        chartRow = Ui.row(this);
        pie = new PieView(this);
        pie.setListener(this::toggleColour);
        chartRow.addView(pie);
        legend = Ui.column(this);
        legend.setPadding(Ui.dp(this, 16), 0, 0, 0);
        chartRow.addView(legend, Ui.lp(0, Ui.WRAP, 1f));
        body.addView(chartRow, Ui.lp(Ui.MATCH, Ui.WRAP));

        TextView hint = Ui.text(this, "Tap a day, a month’s name, the heading above, or a "
                + "colour to list its posts.", 13f, Ui.DIM, false);
        hint.setPadding(0, Ui.dp(this, 8), 0, 0);
        body.addView(hint);

        listBox = Ui.column(this);
        listBox.setPadding(0, 0, 0, Ui.dp(this, 24));
        body.addView(listBox);

        scroll = new ScrollView(this);
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
        out.putInt("pick", pick);
        if (pickDay != null) out.putLong("pd", pickDay.toEpochDay());
        if (pickMonth != null) {
            out.putInt("pmy", pickMonth.getYear());
            out.putInt("pmm", pickMonth.getMonthValue());
        }
        out.putInt("pmood", pickMood);
    }

    private void step(int dir) {
        if (yearMode) year += dir;
        else month = month.plusMonths(dir);
        if (pick == COLOUR) pick = NONE;
        refresh();
    }

    private LocalDate rangeFrom() {
        return yearMode ? LocalDate.of(year, 1, 1) : month.atDay(1);
    }

    private LocalDate rangeTo() {
        return yearMode ? LocalDate.of(year, 12, 31) : month.atEndOfMonth();
    }

    private String rangeName() {
        return yearMode ? String.valueOf(year) : monthName(month);
    }

    private static String monthName(YearMonth m) {
        return m.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()) + " "
                + m.getYear();
    }

    /** Whether what the list shows still lies inside the month or year on screen. */
    private boolean pickVisible() {
        LocalDate a = rangeFrom(), b = rangeTo();
        switch (pick) {
            case DAY:
                return pickDay != null && !pickDay.isBefore(a) && !pickDay.isAfter(b);
            case MONTH:
                return pickMonth != null && !pickMonth.atDay(1).isBefore(a)
                        && !pickMonth.atEndOfMonth().isAfter(b);
            case YEAR:
                return yearMode;
            case COLOUR:
                return pickMood != Post.ANY;
            default:
                return false;
        }
    }

    private void refresh() {
        monthTab.setTextColor(yearMode ? Ui.DIM : Ui.ACCENT);
        yearTab.setTextColor(yearMode ? Ui.ACCENT : Ui.DIM);
        heading.setText(rangeName());
        heading.setContentDescription(rangeName() + ", show its posts");
        if (!pickVisible()) pick = NONE;

        final boolean ym = yearMode;
        final YearMonth mo = month;
        final int yr = year;
        final LocalDate from = rangeFrom(), to = rangeTo();
        new Thread(() -> {
            final Map<LocalDate, Integer> d = Store.get(getApplicationContext()).days(from, to);
            ui.post(() -> {
                if (isDestroyed() || ym != yearMode || !mo.equals(month) || yr != year) return;
                if (ym) mosaic.showYear(yr, d);
                else mosaic.showMonth(mo, d);
                showChart(d, from, to);
                showPick();
            });
        }, "calendar").start();
    }

    // ---- the donut ------------------------------------------------------------

    private void showChart(Map<LocalDate, Integer> d, LocalDate from, LocalDate to) {
        int[] n = new int[Post.DAY_COLOURS.length];
        for (Integer m : d.values()) {
            for (int i = 0; i < n.length; i++) if (Post.DAY_COLOURS[i] == m) n[i]++;
        }
        long total = to.toEpochDay() - from.toEpochDay() + 1;
        int written = d.size();
        if (written == 0) {
            summary.setText("Nothing written in " + rangeName() + " yet");
            chartRow.setVisibility(View.GONE);
            return;
        }
        summary.setText(written + " of " + total + " days written in " + rangeName());
        chartRow.setVisibility(View.VISIBLE);
        pie.setCounts(n);
        pie.select(pick == COLOUR ? pickMood : Post.ANY);

        legend.removeAllViews();
        for (int i = 0; i < n.length; i++) {
            if (n[i] == 0) continue;      // only colours that happened
            final int mood = Post.DAY_COLOURS[i];
            LinearLayout row = Ui.row(this);
            int p = Ui.dp(this, 4);
            row.setPadding(Ui.dp(this, 8), p, p, p);
            boolean on = pick == COLOUR && pickMood == mood;
            if (on) row.setBackground(Ui.card(this));
            View sq = new View(this);
            sq.setBackground(Ui.dot(this, mood));
            LinearLayout.LayoutParams sl = Ui.lp(Ui.dp(this, 14), Ui.dp(this, 14));
            sl.rightMargin = Ui.dp(this, 10);
            row.addView(sq, sl);
            // name above, count below, so a long name never wraps beside its count
            LinearLayout words = Ui.column(this);
            TextView name = Ui.text(this, Post.name(mood), 15f, Ui.TEXT, on);
            name.setSingleLine(true);
            words.addView(name);
            int pct = Math.round(100f * n[i] / written);
            words.addView(Ui.text(this, n[i] + (n[i] == 1 ? " day" : " days") + " \u00b7 "
                    + pct + "%", 13f, Ui.DIM, false));
            row.addView(words, Ui.lp(0, Ui.WRAP, 1f));
            row.setClickable(true);
            row.setContentDescription(Post.name(mood) + ", " + n[i] + (n[i] == 1 ? " day" : " days")
                    + ", show its posts");
            row.setOnClickListener(v -> toggleColour(mood));
            legend.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
    }

    private void toggleColour(int mood) {
        if (pick == COLOUR && pickMood == mood) choose(NONE, null, null, Post.ANY);
        else choose(COLOUR, null, null, mood);
    }

    // ---- the list -------------------------------------------------------------

    private void choose(int kind, LocalDate day, YearMonth m, int mood) {
        pick = kind;
        pickDay = day;
        pickMonth = m;
        pickMood = mood;
        // the donut and legend show which colour is chosen
        refresh();
        if (kind != NONE) {
            scroll.postDelayed(() -> scroll.smoothScrollTo(0,
                    Math.max(0, listBox.getTop() - Ui.dp(this, 8))), 150);
        }
    }

    private void showPick() {
        mosaic.select(pick == DAY ? pickDay : null);
        mosaic.selectMonth(pick == MONTH && yearMode ? pickMonth : null);
        pie.select(pick == COLOUR ? pickMood : Post.ANY);
        final int ticket = ++loads;
        if (pick == NONE) {
            listBox.removeAllViews();
            return;
        }
        final LocalDate from, to;
        final int mood;
        final String title;
        switch (pick) {
            case DAY:
                from = to = pickDay;
                mood = Post.ANY;
                title = pickDay.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",
                        Locale.getDefault()));
                break;
            case MONTH:
                from = pickMonth.atDay(1);
                to = pickMonth.atEndOfMonth();
                mood = Post.ANY;
                title = "All posts in " + monthName(pickMonth);
                break;
            case YEAR:
                from = LocalDate.of(year, 1, 1);
                to = LocalDate.of(year, 12, 31);
                mood = Post.ANY;
                title = "All posts in " + year;
                break;
            default:
                from = rangeFrom();
                to = rangeTo();
                mood = pickMood;
                title = mood == Post.EXCEPTIONAL ? "Exceptional days in " + rangeName()
                        : Post.name(pickMood) + " posts in " + rangeName();
        }
        final boolean oldestFirst = pick == DAY;
        new Thread(() -> {
            Store s = Store.get(getApplicationContext());
            // an exceptional day lists all of its posts, whatever their colour
            final List<Post> list = mood == Post.EXCEPTIONAL
                    ? s.onExceptionalDays(from, to, oldestFirst)
                    : s.find(from, to, mood, oldestFirst);
            ui.post(() -> {
                if (ticket != loads || isDestroyed()) return;
                showList(title, list);
            });
        }, "list").start();
    }

    private void showList(String title, List<Post> list) {
        listBox.removeAllViews();
        LinearLayout head = Ui.row(this);
        head.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 8));
        LinearLayout words = Ui.column(this);
        words.addView(Ui.text(this, title, 18f, Ui.TEXT, true));
        int n = list.size();
        words.addView(Ui.text(this, n + (n == 1 ? " post" : " posts"), 13f, Ui.DIM, false));
        head.addView(words, Ui.lp(0, Ui.WRAP, 1f));
        Button close = Ui.button(this, "Close", Ui.BG, Ui.DIM);
        close.setOnClickListener(v -> choose(NONE, null, null, Post.ANY));
        head.addView(close);
        listBox.addView(head);

        if (pick == MONTH && yearMode) {
            final YearMonth m = pickMonth;
            Button open = Ui.button(this, "Open " + monthName(m), Ui.BUTTON, Ui.ACCENT);
            open.setOnClickListener(v -> {
                month = m;
                yearMode = false;
                refresh();
            });
            LinearLayout.LayoutParams lp = Ui.lp(Ui.MATCH, Ui.WRAP);
            lp.bottomMargin = Ui.dp(this, 10);
            listBox.addView(open, lp);
        }
        if (list.isEmpty()) {
            listBox.addView(Ui.text(this, "Nothing written here.", 15f, Ui.DIM, false));
        }
        for (final Post p : list) {
            PostCard c = new PostCard(this);
            c.bind(p);
            c.root.setClickable(true);
            c.root.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class)
                    .putExtra(EditActivity.EXTRA_ID, p.id)));
            LinearLayout.LayoutParams lp = Ui.lp(Ui.MATCH, Ui.WRAP);
            lp.bottomMargin = Ui.dp(this, 8);
            listBox.addView(c.root, lp);
        }
        if (pick == DAY && !pickDay.isAfter(LocalDate.now())) {
            final LocalDate day = pickDay;
            Button write = Ui.button(this, "Write for this day", Ui.BUTTON, Ui.ACCENT);
            write.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class)
                    .putExtra(EditActivity.EXTRA_DAY, day.toEpochDay())));
            listBox.addView(write, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
    }
}
