package com.numcha;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Writes or edits one post. A new post takes the current date and time; both
 * can be changed. Title, text, colour and photo are all optional, but a post
 * needs at least one of them.
 */
public final class EditActivity extends Base {

    static final String EXTRA_ID = "id";
    static final String EXTRA_DAY = "day";   // epoch day, for "write for this day"

    private static final int REQ_CAMERA = 1;
    private static final int REQ_GALLERY = 2;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Post post;
    private String origTitle = "", origBody = "", origPhoto;
    private int origMood;
    private long origAt;
    private final ArrayList<String> added = new ArrayList<String>();  // photo files made here

    private TextView dateText, timeText, photoStatus;
    private EditText title, body;
    private ImageView photoView;
    private Button removePhoto;
    private final View[] swatches = new View[5];
    private boolean busy;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        long id = getIntent().getLongExtra(EXTRA_ID, 0);
        Post p = id == 0 ? null : Store.get(this).byId(id);
        if (p == null) {
            p = new Post();
            LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
            long day = getIntent().getLongExtra(EXTRA_DAY, Long.MIN_VALUE);
            if (day != Long.MIN_VALUE) {
                now = LocalDate.ofEpochDay(day).atTime(now.toLocalTime());
            }
            p.at = Post.toMillis(now);
        }
        post = p;
        origTitle = p.title;
        origBody = p.body;
        origMood = p.mood;
        origAt = p.at;
        origPhoto = p.photo;

        if (state != null) {
            post.at = state.getLong("at", post.at);
            post.mood = state.getInt("mood", post.mood);
            post.photo = state.getString("photo", post.photo);
            ArrayList<String> a = state.getStringArrayList("added");
            if (a != null) added.addAll(a);
        }
        build(state);
    }

    private void build(Bundle state) {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout head = Ui.row(this);
        head.addView(Ui.text(this, post.id == 0 ? "New post" : "Edit post", 26f, Ui.TEXT, true),
                Ui.lp(0, Ui.WRAP, 1f));
        if (post.id != 0) {
            Button del = Ui.button(this, "Delete", Ui.BG, Ui.RED);
            del.setOnClickListener(v -> confirmDelete());
            head.addView(del);
        }
        root.addView(head);

        LinearLayout form = Ui.column(this);
        form.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));

        // when
        LinearLayout when = Ui.row(this);
        when.setBackground(Ui.card(this));
        int p = Ui.dp(this, 14);
        when.setPadding(p, p, p, p);
        LinearLayout whenText = Ui.column(this);
        dateText = Ui.text(this, "", 17f, Ui.TEXT, true);
        timeText = Ui.text(this, "", 15f, Ui.DIM, false);
        whenText.addView(dateText);
        whenText.addView(timeText);
        when.addView(whenText, Ui.lp(0, Ui.WRAP, 1f));
        when.addView(Ui.text(this, "Change", 15f, Ui.ACCENT, true));
        when.setClickable(true);
        when.setOnClickListener(v -> pickDate());
        form.addView(when, Ui.lp(Ui.MATCH, Ui.WRAP));
        showWhen();

        // colour
        form.addView(label("Colour"));
        LinearLayout colours = Ui.row(this);
        int[] moods = {Post.NONE, Post.GREEN, Post.YELLOW, Post.RED, Post.BLACK};
        for (int i = 0; i < moods.length; i++) {
            final int m = moods[i];
            View s = new View(this);
            s.setContentDescription(Post.name(m));
            s.setOnClickListener(v -> {
                post.mood = m;
                showMood();
            });
            LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44));
            lp.rightMargin = Ui.dp(this, 14);
            colours.addView(s, lp);
            swatches[i] = s;
        }
        form.addView(colours);
        showMood();

        // words
        form.addView(label("Title (optional)"));
        title = Ui.field(this, "Title", 18f);
        title.setSingleLine(true);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setText(state != null ? state.getString("title", post.title) : post.title);
        title.setSaveEnabled(false);
        form.addView(title, Ui.lp(Ui.MATCH, Ui.WRAP));

        form.addView(label("Text (optional)"));
        body = Ui.field(this, "What happened today?", 16f);
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setMinLines(6);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setText(state != null ? state.getString("body", post.body) : post.body);
        body.setSaveEnabled(false);
        form.addView(body, Ui.lp(Ui.MATCH, Ui.WRAP));

        // photo
        form.addView(label("Picture (optional)"));
        photoView = new ImageView(this);
        photoView.setAdjustViewBounds(true);
        photoView.setMaxHeight(Ui.dp(this, 420));
        photoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photoView.setContentDescription("Picture");
        form.addView(photoView, Ui.lp(Ui.MATCH, Ui.WRAP));
        photoStatus = Ui.text(this, "", 14f, Ui.DIM, false);
        form.addView(photoStatus);
        LinearLayout photoButtons = Ui.row(this);
        photoButtons.setPadding(0, Ui.dp(this, 6), 0, 0);
        Button cam = Ui.button(this, "Camera", Ui.BUTTON, Ui.TEXT);
        cam.setOnClickListener(v -> openCamera());
        Button gal = Ui.button(this, "Gallery", Ui.BUTTON, Ui.TEXT);
        gal.setOnClickListener(v -> openGallery());
        removePhoto = Ui.button(this, "Remove", Ui.BG, Ui.RED);
        removePhoto.setOnClickListener(v -> {
            post.photo = null;
            showPhoto();
        });
        photoButtons.addView(cam, Ui.lp(0, Ui.WRAP, 1f));
        photoButtons.addView(Ui.gap(this, 10));
        photoButtons.addView(gal, Ui.lp(0, Ui.WRAP, 1f));
        photoButtons.addView(Ui.gap(this, 10));
        photoButtons.addView(removePhoto, Ui.lp(0, Ui.WRAP, 1f));
        form.addView(photoButtons, Ui.lp(Ui.MATCH, Ui.WRAP));
        showPhoto();

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        root.addView(scroll, Ui.lp(Ui.MATCH, 0, 1f));

        Button save = Ui.button(this, "Save", Ui.ACCENT, Ui.BG);
        save.setOnClickListener(v -> save());
        root.addView(save, Ui.lp(Ui.MATCH, Ui.WRAP));

        setContentView(root);
        Ui.fitSystemWindows(root);
    }

    private TextView label(String s) {
        TextView t = Ui.text(this, s, 13f, Ui.DIM, true);
        t.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 6));
        return t;
    }

    private void showWhen() {
        dateText.setText(post.dateText());
        timeText.setText(post.timeText());
    }

    private void showMood() {
        int[] moods = {Post.NONE, Post.GREEN, Post.YELLOW, Post.RED, Post.BLACK};
        for (int i = 0; i < swatches.length; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            boolean on = post.mood == moods[i];
            if (moods[i] == Post.NONE) {
                g.setColor(Ui.BG);
                g.setStroke(Ui.dp(this, on ? 3 : 2), on ? Ui.TEXT : Ui.DIM);
            } else {
                g.setColor(Post.color(moods[i]));
                if (on) g.setStroke(Ui.dp(this, 3), Ui.TEXT);
                else if (moods[i] == Post.BLACK) g.setStroke(Ui.dp(this, 1), Ui.BLACK_EDGE);
            }
            swatches[i].setBackground(g);
            swatches[i].setSelected(on);
            swatches[i].setScaleX(on ? 1.12f : 1f);
            swatches[i].setScaleY(on ? 1.12f : 1f);
        }
    }

    private void showPhoto() {
        removePhoto.setVisibility(post.photo == null ? View.GONE : View.VISIBLE);
        if (post.photo == null) {
            photoView.setImageDrawable(null);
            photoView.setVisibility(View.GONE);
            return;
        }
        photoView.setVisibility(View.VISIBLE);
        final String name = post.photo;
        final int px = getResources().getDisplayMetrics().widthPixels;
        new Thread(() -> {
            final Bitmap b = Photos.load(getApplicationContext(), name, px);
            ui.post(() -> {
                if (isDestroyed() || !name.equals(post.photo)) return;
                photoView.setImageBitmap(b);
                photoStatus.setText(b == null ? "This picture could not be read." : "");
            });
        }, "photo").start();
    }

    private void pickDate() {
        final LocalDateTime t = post.local();
        DatePickerDialog d = new DatePickerDialog(this,
                android.R.style.Theme_Material_Dialog_Alert,
                (view, y, m, dom) -> pickTime(LocalDate.of(y, m + 1, dom)),
                t.getYear(), t.getMonthValue() - 1, t.getDayOfMonth());
        d.show();
    }

    private void pickTime(final LocalDate date) {
        LocalDateTime t = post.local();
        new TimePickerDialog(this, android.R.style.Theme_Material_Dialog_Alert,
                (view, h, min) -> {
                    post.at = Post.toMillis(date.atTime(h, min));
                    showWhen();
                },
                t.getHour(), t.getMinute(),
                android.text.format.DateFormat.is24HourFormat(this)).show();
    }

    private void openCamera() {
        if (busy) return;
        File f = CameraFiles.file(this);
        f.delete();
        Uri out = CameraFiles.uri(this);
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, out);
        i.setClipData(ClipData.newRawUri("photo", out));
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startAway(i, REQ_CAMERA);
        } catch (ActivityNotFoundException e) {
            Lock.cameBack();
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        if (busy) return;
        Intent i = new Intent(MediaStore.ACTION_PICK_IMAGES);
        i.setType("image/*");
        try {
            startAway(i, REQ_GALLERY);
        } catch (ActivityNotFoundException e) {
            Lock.cameBack();
            Intent g = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startAway(g, REQ_GALLERY);
            } catch (ActivityNotFoundException e2) {
                Lock.cameBack();
                Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        Lock.cameBack();
        if (result != RESULT_OK) return;
        if (req == REQ_CAMERA) {
            final File f = CameraFiles.file(this);
            if (!f.exists() || f.length() == 0) {
                Toast.makeText(this, "The camera did not return a picture", Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            addPhoto(null, f);
        } else if (req == REQ_GALLERY && data != null && data.getData() != null) {
            addPhoto(data.getData(), null);
        }
    }

    private void addPhoto(final Uri uri, final File file) {
        busy = true;
        photoStatus.setText("Adding picture…");
        new Thread(() -> {
            String name = null;
            try {
                name = uri != null ? Photos.importImage(getApplicationContext(), uri)
                        : Photos.importFile(getApplicationContext(), file);
            } catch (Exception e) {
                name = null;
            }
            if (file != null) file.delete();
            final String n = name;
            ui.post(() -> {
                busy = false;
                if (n == null) {
                    photoStatus.setText("That picture could not be read.");
                    return;
                }
                added.add(n);
                if (isDestroyed()) return;
                post.photo = n;
                photoStatus.setText("");
                showPhoto();
            });
        }, "add-photo").start();
    }

    private boolean dirty() {
        return !title.getText().toString().equals(origTitle)
                || !body.getText().toString().equals(origBody)
                || post.mood != origMood || post.at != origAt
                || !java.util.Objects.equals(post.photo, origPhoto);
    }

    private void save() {
        if (busy) return;
        post.title = title.getText().toString().trim();
        post.body = body.getText().toString().trim();
        if (post.isEmpty()) {
            Toast.makeText(this, "Add a title, some text, a colour or a picture first",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Store s = Store.get(this);
        s.save(post);
        if (origPhoto != null && !origPhoto.equals(post.photo)) s.deletePhoto(origPhoto);
        for (String n : added) if (!n.equals(post.photo)) s.deletePhoto(n);
        added.clear();
        Ui.hideKeyboard(this, title);
        finish();
    }

    private void discard() {
        Store s = Store.get(this);
        for (String n : added) if (!n.equals(origPhoto)) s.deletePhoto(n);
        added.clear();
        finish();
    }

    @Override
    @SuppressWarnings("deprecation")  // still delivered: predictive back is not opted into
    public void onBackPressed() {
        if (!dirty()) {
            discard();
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Save this post?")
                .setPositiveButton("Save", (d, w) -> save())
                .setNegativeButton("Discard", (d, w) -> discard())
                .setNeutralButton("Keep editing", null)
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Delete this post?")
                .setMessage("It cannot be undone, unless it is in an export you made earlier.")
                .setNegativeButton("Keep it", null)
                .setPositiveButton("Delete", (d, w) -> {
                    Store s = Store.get(this);
                    Post stored = s.byId(post.id);
                    if (stored != null) s.delete(stored);
                    for (String n : added) s.deletePhoto(n);
                    added.clear();
                    finish();
                })
                .show();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong("at", post.at);
        out.putInt("mood", post.mood);
        out.putString("photo", post.photo);
        out.putStringArrayList("added", added);
        out.putString("title", title.getText().toString());
        out.putString("body", body.getText().toString());
    }
}
