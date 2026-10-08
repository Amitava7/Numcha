package com.numcha;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The journal, newest first, with the way in to writing, the calendar and settings. */
public final class MainActivity extends Base {

    // Two threads decode thumbnails, so a fast scroll queues work instead of
    // starting a thread per row.
    private static final ExecutorService THUMBS = Executors.newFixedThreadPool(2);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Post> posts = new ArrayList<Post>();
    private Adapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
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

        empty = Ui.text(this, "No posts yet.\nTap “New post” to write about today.",
                16f, Ui.DIM, false);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(Ui.dp(this, 10));
        list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false);
        list.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) ->
                startActivity(new Intent(this, EditActivity.class)
                        .putExtra(EditActivity.EXTRA_ID, posts.get(pos).id)));
        list.setEmptyView(empty);

        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.addView(list);
        frame.addView(empty, new android.widget.FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        root.addView(frame, Ui.lp(Ui.MATCH, 0, 1f));

        Button write = Ui.button(this, "New post", Ui.ACCENT, Ui.BG);
        write.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class)));
        root.addView(write, Ui.lp(Ui.MATCH, Ui.WRAP));

        setContentView(root);
        Ui.fitSystemWindows(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Lock.needsUnlock(this)) return;
        new Thread(() -> {
            final List<Post> all = Store.get(getApplicationContext()).all();
            ui.post(() -> {
                posts.clear();
                posts.addAll(all);
                adapter.notifyDataSetChanged();
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
            Holder h = convert == null ? new Holder() : (Holder) convert.getTag();
            h.bind(posts.get(i));
            return h.root;
        }
    }

    private final class Holder {
        final LinearLayout root;
        final View dot;
        final TextView when, title, body;
        final ImageView thumb;
        String showing;

        Holder() {
            root = Ui.row(MainActivity.this);
            root.setBackground(Ui.card(MainActivity.this));
            int p = Ui.dp(MainActivity.this, 14);
            root.setPadding(p, p, p, p);
            root.setGravity(Gravity.TOP);
            root.setTag(this);

            dot = new View(MainActivity.this);
            LinearLayout.LayoutParams dl = Ui.lp(Ui.dp(MainActivity.this, 14),
                    Ui.dp(MainActivity.this, 14));
            dl.topMargin = Ui.dp(MainActivity.this, 3);
            dl.rightMargin = Ui.dp(MainActivity.this, 12);
            root.addView(dot, dl);

            LinearLayout col = Ui.column(MainActivity.this);
            when = Ui.text(MainActivity.this, "", 13f, Ui.DIM, false);
            title = Ui.text(MainActivity.this, "", 17f, Ui.TEXT, true);
            body = Ui.text(MainActivity.this, "", 15f, Ui.TEXT, false);
            body.setMaxLines(3);
            body.setEllipsize(TextUtils.TruncateAt.END);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(when);
            col.addView(title);
            col.addView(body);
            root.addView(col, Ui.lp(0, Ui.WRAP, 1f));

            thumb = new ImageView(MainActivity.this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setClipToOutline(true);
            thumb.setBackground(Ui.card(MainActivity.this));
            int t = Ui.dp(MainActivity.this, 72);
            LinearLayout.LayoutParams tl = Ui.lp(t, t);
            tl.leftMargin = Ui.dp(MainActivity.this, 12);
            root.addView(thumb, tl);
        }

        void bind(Post p) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            if (p.mood == Post.NONE) {
                g.setStroke(Ui.dp(MainActivity.this, 2), Ui.PLAIN);
            } else {
                g.setColor(Post.color(p.mood));
                if (p.mood == Post.BLACK) g.setStroke(Ui.dp(MainActivity.this, 1), Ui.BLACK_EDGE);
            }
            dot.setBackground(g);
            dot.setContentDescription(Post.name(p.mood));

            String day = p.day().equals(LocalDate.now()) ? "Today" : p.dateText();
            when.setText(day + " · " + p.timeText());
            title.setText(p.title);
            title.setVisibility(p.title.isEmpty() ? View.GONE : View.VISIBLE);
            body.setText(p.body);
            body.setVisibility(p.body.isEmpty() ? View.GONE : View.VISIBLE);
            if (p.title.isEmpty() && p.body.isEmpty()) {
                body.setText(p.photo != null ? "Picture" : Post.name(p.mood) + " day");
                body.setTextColor(Ui.DIM);
                body.setVisibility(View.VISIBLE);
            } else {
                body.setTextColor(Ui.TEXT);
            }

            showing = p.photo;
            thumb.setImageDrawable(null);
            thumb.setVisibility(p.photo == null ? View.GONE : View.VISIBLE);
            if (p.photo != null) {
                final String name = p.photo;
                final int px = Ui.dp(MainActivity.this, 144);
                THUMBS.execute(() -> {
                    final Bitmap b = Photos.load(getApplicationContext(), name, px);
                    ui.post(() -> {
                        if (name.equals(showing)) thumb.setImageBitmap(b);
                    });
                });
            }
        }
    }
}
