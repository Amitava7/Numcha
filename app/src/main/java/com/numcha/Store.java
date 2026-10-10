package com.numcha;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The journal: one SQLite table in the app's private storage, plus the photos
 * as JPEG files beside it. Nothing here touches the network.
 */
final class Store extends SQLiteOpenHelper {

    private static Store sInstance;
    private final File photos;

    static synchronized Store get(Context ctx) {
        if (sInstance == null) sInstance = new Store(ctx.getApplicationContext());
        return sInstance;
    }

    private Store(Context ctx) {
        super(ctx, "journal.db", null, 1);
        photos = new File(ctx.getFilesDir(), "photos");
        photos.mkdirs();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE posts(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uid TEXT NOT NULL UNIQUE," +
                "at INTEGER NOT NULL," +
                "title TEXT NOT NULL DEFAULT ''," +
                "body TEXT NOT NULL DEFAULT ''," +
                "mood INTEGER NOT NULL DEFAULT 2," +
                "photo TEXT)");
        db.execSQL("CREATE INDEX posts_at ON posts(at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int from, int to) {
        // Only one version so far. A later one must migrate, never drop:
        // this table is the user's diary.
    }

    File photoDir() {
        return photos;
    }

    File photoFile(String name) {
        return name == null ? null : new File(photos, name);
    }

    static String newPhotoName() {
        return UUID.randomUUID().toString().replace("-", "") + ".jpg";
    }

    private static final String[] COLS = {"id", "uid", "at", "title", "body", "mood", "photo"};

    private static Post read(Cursor c) {
        Post p = new Post();
        p.id = c.getLong(0);
        p.uid = c.getString(1);
        p.at = c.getLong(2);
        p.title = c.getString(3);
        p.body = c.getString(4);
        p.mood = Post.clampMood(c.getInt(5));
        p.photo = c.isNull(6) ? null : c.getString(6);
        return p;
    }

    private List<Post> query(String where, String[] args, String order) {
        List<Post> out = new ArrayList<Post>();
        Cursor c = getReadableDatabase().query("posts", COLS, where, args, null, null, order);
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    /** Newest first. */
    List<Post> all() {
        return query(null, null, "at DESC, id DESC");
    }

    Post byId(long id) {
        List<Post> l = query("id=?", new String[]{String.valueOf(id)}, null);
        return l.isEmpty() ? null : l.get(0);
    }

    Post byUid(String uid) {
        List<Post> l = query("uid=?", new String[]{uid}, null);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Posts whose time falls on [from, to] inclusive, oldest first. */
    List<Post> between(LocalDate from, LocalDate to) {
        return find(from, to, Post.ANY, true);
    }

    /**
     * Posts on the days [from, to] inclusive (either end may be null for
     * open-ended) with the given colour ({@link Post#ANY} for every colour).
     */
    List<Post> find(LocalDate from, LocalDate to, int mood, boolean oldestFirst) {
        StringBuilder where = new StringBuilder();
        List<String> args = new ArrayList<String>();
        if (from != null) {
            where.append("at>=?");
            args.add(String.valueOf(Post.toMillis(from.atStartOfDay())));
        }
        if (to != null) {
            if (where.length() > 0) where.append(" AND ");
            where.append("at<?");
            args.add(String.valueOf(Post.toMillis(to.plusDays(1).atTime(LocalTime.MIDNIGHT))));
        }
        if (mood != Post.ANY) {
            if (where.length() > 0) where.append(" AND ");
            where.append("mood=?");
            args.add(String.valueOf(mood));
        }
        return query(where.length() == 0 ? null : where.toString(),
                args.isEmpty() ? null : args.toArray(new String[0]),
                oldestFirst ? "at ASC, id ASC" : "at DESC, id DESC");
    }

    /**
     * The colour each day shows on the calendar: that of the day's latest post.
     * Days with no posts are absent from the map.
     */
    Map<LocalDate, Integer> days(LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> out = new HashMap<LocalDate, Integer>();
        for (Post p : between(from, to)) out.put(p.day(), p.mood);   // oldest first: last wins
        return out;
    }

    /** The day of the earliest post, or null if there are none. */
    LocalDate firstDay() {
        List<Post> l = query(null, null, "at ASC LIMIT 1");
        return l.isEmpty() ? null : l.get(0).day();
    }

    void save(Post p) {
        if (p.uid == null) p.uid = UUID.randomUUID().toString();
        ContentValues v = new ContentValues();
        v.put("uid", p.uid);
        v.put("at", p.at);
        v.put("title", p.title == null ? "" : p.title);
        v.put("body", p.body == null ? "" : p.body);
        p.mood = Post.clampMood(p.mood);
        v.put("mood", p.mood);
        if (p.photo == null) v.putNull("photo");
        else v.put("photo", p.photo);
        SQLiteDatabase db = getWritableDatabase();
        if (p.id == 0) {
            p.id = db.insertOrThrow("posts", null, v);
        } else {
            db.update("posts", v, "id=?", new String[]{String.valueOf(p.id)});
        }
    }

    void delete(Post p) {
        getWritableDatabase().delete("posts", "id=?", new String[]{String.valueOf(p.id)});
        deletePhoto(p.photo);
    }

    void deletePhoto(String name) {
        File f = photoFile(name);
        if (f != null) f.delete();
    }

    /** Removes every post and photo. Used by "replace" on import. */
    void wipe() {
        getWritableDatabase().delete("posts", null, null);
        File[] fs = photos.listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    int count() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM posts", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    /** Bytes used by the photos folder. */
    long photoBytes() {
        long n = 0;
        File[] fs = photos.listFiles();
        if (fs != null) for (File f : fs) n += f.length();
        return n;
    }

    int photoCount() {
        File[] fs = photos.listFiles();
        return fs == null ? 0 : fs.length;
    }
}
