package com.numcha;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Export and import. An export is one .zip holding journal.json and a photos/
 * folder, so it can be opened on a computer too:
 *
 * <pre>
 * journal.json  {"app":"numcha","version":1,"exported":"...","posts":[
 *                 {"uid":"...","at":1759955160000,"date":"2026-10-08T21:46",
 *                  "title":"...","text":"...","colour":"green","photo":"photos/x.jpg"}]}
 * photos/x.jpg
 * </pre>
 *
 * Importing adds the file's posts to the journal. A post that was exported
 * from this journal before (same uid) is replaced by the imported copy, so
 * importing the same file twice does not duplicate anything.
 */
final class Archive {

    static final int VERSION = 1;
    private static final Pattern SAFE = Pattern.compile("photos/[A-Za-z0-9._-]{1,80}");
    private static final long MAX_JSON = 64L * 1024 * 1024;
    private static final long MAX_PHOTO = 64L * 1024 * 1024;

    static final class Result {
        int added, updated, photos, skipped;
    }

    private Archive() {
    }

    private static final String[] COLOURS = {"none", "green", "yellow", "red", "black"};

    static int moodFrom(String s) {
        if (s == null) return Post.NONE;
        for (int i = 0; i < COLOURS.length; i++) if (COLOURS[i].equalsIgnoreCase(s)) return i;
        return Post.NONE;
    }

    static void export(Context c, Uri dest) throws IOException, JSONException {
        Store store = Store.get(c);
        List<Post> posts = store.all();
        JSONArray arr = new JSONArray();
        for (int i = posts.size() - 1; i >= 0; i--) {   // oldest first reads better
            Post p = posts.get(i);
            JSONObject o = new JSONObject();
            o.put("uid", p.uid);
            o.put("at", p.at);
            o.put("date", p.local().toString());
            o.put("title", p.title);
            o.put("text", p.body);
            o.put("colour", COLOURS[Post.clampMood(p.mood)]);
            File f = store.photoFile(p.photo);
            if (f != null && f.exists()) o.put("photo", "photos/" + p.photo);
            arr.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("app", "numcha");
        root.put("version", VERSION);
        root.put("exported", java.time.OffsetDateTime.now().toString());
        root.put("posts", arr);

        OutputStream raw = c.getContentResolver().openOutputStream(dest, "wt");
        if (raw == null) throw new IOException("could not open the file to write");
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw))) {
            zip.putNextEntry(new ZipEntry("journal.json"));
            zip.write(root.toString(1).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            byte[] buf = new byte[64 * 1024];
            for (Post p : posts) {
                File f = store.photoFile(p.photo);
                if (f == null || !f.exists()) continue;
                // JPEGs do not shrink, so store them rather than deflate again
                ZipEntry e = new ZipEntry("photos/" + p.photo);
                e.setMethod(ZipEntry.STORED);
                e.setSize(f.length());
                e.setCrc(crc(f, buf));
                zip.putNextEntry(e);
                try (InputStream in = new FileInputStream(f)) {
                    int n;
                    while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
                }
                zip.closeEntry();
            }
        }
    }

    private static long crc(File f, byte[] buf) throws IOException {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        try (InputStream in = new FileInputStream(f)) {
            int n;
            while ((n = in.read(buf)) > 0) crc.update(buf, 0, n);
        }
        return crc.getValue();
    }

    /**
     * Reads an export. Nothing in the journal changes until the whole file has
     * been read and understood, so a broken file leaves the journal as it was.
     */
    static Result importFrom(Context c, Uri src, boolean replace) throws IOException, JSONException {
        Store store = Store.get(c);
        File tmp = new File(c.getCacheDir(), "import");
        deleteTree(tmp);
        tmp.mkdirs();
        Map<String, File> photos = new HashMap<String, File>();
        String json = null;
        try {
            InputStream raw = c.getContentResolver().openInputStream(src);
            if (raw == null) throw new IOException("could not open the file");
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry e;
                byte[] buf = new byte[64 * 1024];
                while ((e = zip.getNextEntry()) != null) {
                    String name = e.getName();
                    if (e.isDirectory()) continue;
                    if (name.equals("journal.json")) {
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        copy(zip, bo, buf, MAX_JSON);
                        json = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                    } else if (SAFE.matcher(name).matches() && !name.contains("..")) {
                        File f = new File(tmp, "p" + photos.size());
                        try (OutputStream fo = new FileOutputStream(f)) {
                            copy(zip, fo, buf, MAX_PHOTO);
                        }
                        photos.put(name, f);
                    }
                }
            }
            if (json == null) {
                throw new IOException("This is not a Numcha export: it has no journal.json.");
            }
            JSONObject root = new JSONObject(json);
            if (root.optInt("version", 1) > VERSION) {
                throw new IOException("This export comes from a newer version of Numcha.");
            }
            JSONArray arr = root.getJSONArray("posts");

            Result r = new Result();
            if (replace) store.wipe();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null || !o.has("at")) {
                    r.skipped++;
                    continue;
                }
                Post p = new Post();
                p.uid = o.optString("uid", "");
                if (p.uid.isEmpty()) p.uid = null;
                p.at = o.getLong("at");
                p.title = o.optString("title", "");
                p.body = o.optString("text", "");
                p.mood = moodFrom(o.optString("colour", "none"));
                File pf = photos.get(o.optString("photo", ""));
                if (pf != null && pf.exists()) {
                    // moved as-is: re-encoding would lose quality on every round trip
                    String name = Store.newPhotoName();
                    if (pf.renameTo(store.photoFile(name))) {
                        p.photo = name;
                        r.photos++;
                    }
                }
                Post old = p.uid == null ? null : store.byUid(p.uid);
                if (old != null) {
                    p.id = old.id;
                    if (old.photo != null && !old.photo.equals(p.photo)) {
                        store.deletePhoto(old.photo);
                    }
                    r.updated++;
                } else {
                    r.added++;
                }
                store.save(p);
            }
            return r;
        } finally {
            deleteTree(tmp);
        }
    }

    private static void copy(InputStream in, OutputStream out, byte[] buf, long max)
            throws IOException {
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > max) throw new IOException("A file inside the export is too large.");
            out.write(buf, 0, n);
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
