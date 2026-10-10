package com.numcha;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One post as a card: colour dot, when, title, a few lines of text and a
 * thumbnail. Used by the journal list and by the lists under the calendar.
 */
final class PostCard {

    // Two threads decode thumbnails, so a fast scroll queues work instead of
    // starting a thread per row.
    private static final ExecutorService THUMBS = Executors.newFixedThreadPool(2);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    final LinearLayout root;
    private final Context ctx;
    private final View dot;
    private final TextView when, title, body;
    private final ImageView thumb;
    private String showing;

    PostCard(Context c) {
        ctx = c;
        root = Ui.row(c);
        root.setBackground(Ui.card(c));
        int p = Ui.dp(c, 14);
        root.setPadding(p, p, p, p);
        root.setGravity(Gravity.TOP);
        root.setTag(this);

        dot = new View(c);
        LinearLayout.LayoutParams dl = Ui.lp(Ui.dp(c, 14), Ui.dp(c, 14));
        dl.topMargin = Ui.dp(c, 3);
        dl.rightMargin = Ui.dp(c, 12);
        root.addView(dot, dl);

        LinearLayout col = Ui.column(c);
        when = Ui.text(c, "", 13f, Ui.DIM, false);
        title = Ui.text(c, "", 17f, Ui.TEXT, true);
        body = Ui.text(c, "", 15f, Ui.TEXT, false);
        body.setMaxLines(3);
        body.setEllipsize(TextUtils.TruncateAt.END);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(when);
        col.addView(title);
        col.addView(body);
        root.addView(col, Ui.lp(0, Ui.WRAP, 1f));

        thumb = new ImageView(c);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setClipToOutline(true);
        thumb.setBackground(Ui.card(c));
        int t = Ui.dp(c, 72);
        LinearLayout.LayoutParams tl = Ui.lp(t, t);
        tl.leftMargin = Ui.dp(c, 12);
        root.addView(thumb, tl);
    }

    void bind(Post p) {
        dot.setBackground(Ui.dot(ctx, p.mood));
        dot.setContentDescription(Post.name(p.mood));

        String day = p.day().equals(LocalDate.now()) ? "Today" : p.dateText();
        when.setText(day + " · " + p.timeText());
        title.setText(p.title);
        title.setVisibility(p.title.isEmpty() ? View.GONE : View.VISIBLE);
        if (p.title.isEmpty() && p.body.isEmpty()) {
            body.setText(p.photo != null ? "Picture" : Post.name(p.mood) + " day");
            body.setTextColor(Ui.DIM);
            body.setVisibility(View.VISIBLE);
        } else {
            body.setText(p.body);
            body.setTextColor(Ui.TEXT);
            body.setVisibility(p.body.isEmpty() ? View.GONE : View.VISIBLE);
        }

        showing = p.photo;
        thumb.setImageDrawable(null);
        thumb.setVisibility(p.photo == null ? View.GONE : View.VISIBLE);
        if (p.photo != null) {
            final String name = p.photo;
            final int px = Ui.dp(ctx, 144);
            final Context app = ctx.getApplicationContext();
            THUMBS.execute(() -> {
                final Bitmap b = Photos.load(app, name, px);
                UI.post(() -> {
                    if (name.equals(showing)) thumb.setImageBitmap(b);
                });
            });
        }
    }
}
