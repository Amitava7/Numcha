package com.numcha;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The journal, newest first, filtered by date (a day, a month, a year or all
 * of it) and by colour, with the way in to writing, the calendar and settings.
 */
public final class MainActivity extends Base {

    static final int ALL = 0, DAY = 1, MONTH = 2, YEAR = 3;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Post> posts = new ArrayList<Post>();
    private Adapter adapter;
    private TextView empty, dateChip, count;
    private Button prev, next, clear;
    private final TextView[] colourChips = new TextView[Post.MOODS.length + 1];

    // the filter
    private int span = ALL;
    private LocalDate anchor = LocalDate.now();
    private int mood = Post.ANY;
    private int loads;   // so a slow, stale query never overwrites a newer one

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            span = state.getInt("span", ALL);
            anchor = LocalDate.ofEpochDay(state.getLong("anchor", anchor.toEpochDay()));
            mood = state.getInt("mood", Post.ANY);
        }
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout head = Ui.row(this);
        head.addView(Ui.text(this, "Numcha", 30f, Ui.TEXT, true), Ui.lp(0, Ui.WRAP, 1f));
        Button cal = Ui.button(this, "Calendar", Ui.BUTTON, Ui.TEXT);
        cal.setOnClickListener(v -> startActivity(new Intent(this, CalendarActivity.class)));
        Button set = Ui.button(this, "Settings", Ui.BUTTON, Ui.TEXT);
        set.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        head.addView(cal);
        head.addView(Ui.gap(this, 8));
        head.addView(set);
        root.addView(head);

        root.addView(dateBar());
        root.addView(colourBar());
        count = Ui.text(this, "", 13f, Ui.DIM, false);
        count.setPadding(Ui.dp(this, 2), Ui.dp(this, 8), 0, 0);
        root.addView(count);

        empty = Ui.text(this, "", 16f, Ui.DIM, false);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(Ui.dp(this, 10));
        list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false);
        list.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 14));
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) ->
                startActivity(new Intent(this, EditActivity.class)
                        .putExtra(EditActivity.EXTRA_ID, posts.get(pos).id)));
        list.setEmptyView(empty);

        FrameLayout frame = new FrameLayout(this);
        frame.addView(list);
        frame.addView(empty, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        root.addView(frame, Ui.lp(Ui.MATCH, 0, 1f));

        Button write = Ui.button(this, "New post", Ui.ACCENT, Ui.BG);
        write.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class)));
        root.addView(write, Ui.lp(Ui.MATCH, Ui.WRAP));

        setContentView(root);
        Ui.fitSystemWindows(root);
        showFilter();
    }

    // ---- filter bars --------------------------------------------------------

    private View dateBar() {
        LinearLayout bar = Ui.row(this);
        bar.setPadding(0, Ui.dp(this, 14), 0, 0);
        prev = Ui.button(this, "‹", Ui.BG, Ui.ACCENT);
        prev.setTextSize(22f);
        prev.setContentDescription("Earlier");
        prev.setOnClickListener(v -> step(-1));
        next = Ui.button(this, "›", Ui.BG, Ui.ACCENT);
        next.setTextSize(22f);
        next.setContentDescription("Later");
        next.setOnClickListener(v -> step(1));
        dateChip = Ui.chip(this, "");
        dateChip.setContentDescription("Choose dates");
        dateChip.setOnClickListener(v -> chooseSpan());
        clear = Ui.button(this, "Clear", Ui.BG, Ui.DIM);
        clear.setOnClickListener(v -> {
            span = ALL;
            mood = Post.ANY;
            showFilter();
            load();
        });
        bar.addView(prev, Ui.lp(Ui.dp(this, 52), Ui.WRAP));
        bar.addView(dateChip, Ui.lp(0, Ui.WRAP, 1f));
        bar.addView(next, Ui.lp(Ui.dp(this, 52), Ui.WRAP));
        bar.addView(clear);
        return bar;
    }

    private View colourBar() {
        // five chips sharing the width, so all of them are always in view
        LinearLayout chips = Ui.row(this);
        chips.setPadding(0, Ui.dp(this, 8), 0, 0);
        for (int i = 0; i <= Post.MOODS.length; i++) {
            final int m = i == 0 ? Post.ANY : Post.MOODS[i - 1];
            TextView c = Ui.chip(this, m == Post.ANY ? "All" : Post.name(m));
            c.setContentDescription(m == Post.ANY ? "All colours" : Post.name(m) + " posts only");
            c.setOnClickListener(v -> {
                mood = mood == m ? Post.ANY : m;   // tapping the chosen one again clears it
                showFilter();
                load();
            });
            LinearLayout.LayoutParams lp = Ui.lp(0, Ui.WRAP, 1f);
            if (i > 0) lp.leftMargin = Ui.dp(this, 6);
            chips.addView(c, lp);
            colourChips[i] = c;
        }
        return chips;
    }

    private void showFilter() {
        dateChip.setText(spanText() + "  \u25be");
        Ui.chipState(this, dateChip, span != ALL, Post.ANY);
        prev.setVisibility(span == ALL ? View.GONE : View.VISIBLE);
        next.setVisibility(span == ALL ? View.GONE : View.VISIBLE);
        clear.setVisibility(span == ALL && mood == Post.ANY ? View.GONE : View.VISIBLE);
        for (int i = 0; i < colourChips.length; i++) {
            int m = i == 0 ? Post.ANY : Post.MOODS[i - 1];
            Ui.chipState(this, colourChips[i], mood == m, m);
        }
    }

    private String spanText() {
        switch (span) {
            case DAY:
                return anchor.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy",
                        Locale.getDefault()));
            case MONTH:
                return anchor.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault())
                        + " " + anchor.getYear();
            case YEAR:
                return String.valueOf(anchor.getYear());
            default:
                return "All dates";
        }
    }

    private void step(int dir) {
        if (span == DAY) anchor = anchor.plusDays(dir);
        else if (span == MONTH) anchor = anchor.plusMonths(dir);
        else if (span == YEAR) anchor = anchor.plusYears(dir);
        showFilter();
        load();
    }

    private void setSpan(int s, LocalDate a) {
        span = s;
        anchor = a;
        showFilter();
        load();
    }

    private void chooseSpan() {
        String[] items = {"All dates", "A day…", "A month…", "A year…"};
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Show posts from")
                .setItems(items, (d, which) -> {
                    if (which == 0) setSpan(ALL, anchor);
                    else if (which == 1) pickDay();
                    else if (which == 2) pickMonth();
                    else pickYear();
                })
                .show();
    }

    private void pickDay() {
        LocalDate a = span == ALL ? LocalDate.now() : anchor;
        new DatePickerDialog(this, android.R.style.Theme_Material_Dialog_Alert,
                (v, y, m, d) -> setSpan(DAY, LocalDate.of(y, m + 1, d)),
                a.getYear(), a.getMonthValue() - 1, a.getDayOfMonth()).show();
    }

    private NumberPicker yearPicker(int selected) {
        LocalDate first = Store.get(this).firstDay();
        int now = LocalDate.now().getYear();
        int lo = Math.min(first == null ? now : first.getYear(), now - 5);
        NumberPicker p = new NumberPicker(this);
        p.setMinValue(lo);
        p.setMaxValue(now + 1);
        p.setValue(Math.max(lo, Math.min(now + 1, selected)));
        p.setWrapSelectorWheel(false);
        p.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        return p;
    }

    private void pickMonth() {
        LocalDate a = span == ALL ? LocalDate.now() : anchor;
        final NumberPicker month = new NumberPicker(this);
        String[] names = new String[12];
        for (int i = 0; i < 12; i++) {
            names[i] = java.time.Month.of(i + 1).getDisplayName(TextStyle.FULL,
                    Locale.getDefault());
        }
        month.setMinValue(1);
        month.setMaxValue(12);
        month.setDisplayedValues(names);
        month.setValue(a.getMonthValue());
        month.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        final NumberPicker year = yearPicker(a.getYear());
        LinearLayout box = Ui.row(this);
        box.setGravity(Gravity.CENTER);
        box.addView(month, Ui.lp(Ui.dp(this, 150), Ui.WRAP));
        box.addView(year, Ui.lp(Ui.dp(this, 100), Ui.WRAP));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Show a month")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Show", (d, w) -> setSpan(MONTH,
                        YearMonth.of(year.getValue(), month.getValue()).atDay(1)))
                .show();
    }

    private void pickYear() {
        LocalDate a = span == ALL ? LocalDate.now() : anchor;
        final NumberPicker year = yearPicker(a.getYear());
        LinearLayout box = Ui.row(this);
        box.setGravity(Gravity.CENTER);
        box.addView(year, Ui.lp(Ui.dp(this, 120), Ui.WRAP));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Show a year")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Show", (d, w) -> setSpan(YEAR,
                        LocalDate.of(year.getValue(), 1, 1)))
                .show();
    }

    // ---- loading ------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        if (Lock.needsUnlock(this)) return;
        load();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("span", span);
        out.putLong("anchor", anchor.toEpochDay());
        out.putInt("mood", mood);
    }

    private LocalDate from() {
        switch (span) {
            case DAY: return anchor;
            case MONTH: return YearMonth.from(anchor).atDay(1);
            case YEAR: return LocalDate.of(anchor.getYear(), 1, 1);
            default: return null;
        }
    }

    private LocalDate to() {
        switch (span) {
            case DAY: return anchor;
            case MONTH: return YearMonth.from(anchor).atEndOfMonth();
            case YEAR: return LocalDate.of(anchor.getYear(), 12, 31);
            default: return null;
        }
    }

    private void load() {
        final int ticket = ++loads;
        final LocalDate a = from(), b = to();
        final int m = mood;
        final boolean filtered = span != ALL || m != Post.ANY;
        new Thread(() -> {
            Store s = Store.get(getApplicationContext());
            final List<Post> found = s.find(a, b, m, false);
            final boolean any = filtered && found.isEmpty() && s.count() > 0;
            ui.post(() -> {
                if (ticket != loads || isDestroyed()) return;
                posts.clear();
                posts.addAll(found);
                adapter.notifyDataSetChanged();
                int n = found.size();
                count.setText(n + (n == 1 ? " post" : " posts")
                        + (filtered ? " match" + (n == 1 ? "es" : "") + " the filters" : ""));
                empty.setText(any ? "No posts match these filters."
                        : "No posts yet.\nTap “New post” to write about today.");
            });
        }, "posts").start();
    }

    private final class Adapter extends BaseAdapter {
        public int getCount() {
            return posts.size();
        }

        public Object getItem(int i) {
            return posts.get(i);
        }

        public long getItemId(int i) {
            return posts.get(i).id;
        }

        public View getView(int i, View convert, ViewGroup parent) {
            PostCard c = convert == null ? new PostCard(MainActivity.this)
                    : (PostCard) convert.getTag();
            c.bind(posts.get(i));
            return c.root;
        }
    }
}
