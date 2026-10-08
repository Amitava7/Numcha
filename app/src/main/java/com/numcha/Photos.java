package com.numcha;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Photos are shrunk to at most 2048 px on the long side and stored as JPEG in
 * the app's private files. A phone camera's 12 MB original would make a year of
 * journal several gigabytes and an export unmovable.
 */
final class Photos {

    private static final int MAX_SIDE = 2048;
    private static final LruCache<String, Bitmap> THUMBS = new LruCache<String, Bitmap>(
            16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getAllocationByteCount();
        }
    };

    private Photos() {
    }

    /** Copies an image from any URI into the journal. Returns the new file name. */
    static String importImage(Context c, Uri src) throws IOException {
        ImageDecoder.Source s = ImageDecoder.createSource(c.getContentResolver(), src);
        return save(c, s);
    }

    static String importFile(Context c, File src) throws IOException {
        return save(c, ImageDecoder.createSource(src));
    }

    private static String save(Context c, ImageDecoder.Source s) throws IOException {
        // ImageDecoder applies the EXIF rotation, so portrait shots stay upright.
        Bitmap b = ImageDecoder.decodeBitmap(s, (dec, info, src) -> {
            int w = info.getSize().getWidth(), h = info.getSize().getHeight();
            int side = Math.max(w, h);
            if (side > MAX_SIDE) {
                float k = MAX_SIDE / (float) side;
                dec.setTargetSize(Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)));
            }
            dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
        String name = Store.newPhotoName();
        File out = Store.get(c).photoFile(name);
        try (FileOutputStream fo = new FileOutputStream(out)) {
            if (!b.compress(Bitmap.CompressFormat.JPEG, 88, fo)) throw new IOException("encode");
        } catch (IOException e) {
            out.delete();
            throw e;
        } finally {
            b.recycle();
        }
        return name;
    }

    /** Decodes a photo to fit inside {@code px} on the long side. Call off the main thread. */
    static Bitmap load(Context c, String name, int px) {
        String key = name + "@" + px;
        Bitmap hit = THUMBS.get(key);
        if (hit != null) return hit;
        File f = Store.get(c).photoFile(name);
        if (f == null || !f.exists()) return null;
        try {
            Bitmap b = ImageDecoder.decodeBitmap(ImageDecoder.createSource(f),
                    (dec, info, src) -> {
                        int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                        int side = Math.max(w, h);
                        if (side > px) {
                            float k = px / (float) side;
                            dec.setTargetSize(Math.max(1, Math.round(w * k)),
                                    Math.max(1, Math.round(h * k)));
                        }
                    });
            THUMBS.put(key, b);
            return b;
        } catch (IOException e) {
            return null;
        }
    }
}
