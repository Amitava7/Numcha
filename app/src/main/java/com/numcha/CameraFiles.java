package com.numcha;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * A one-file content provider: the camera app writes the photo it takes into
 * cache/camera/shot.jpg through it. This stands in for AndroidX FileProvider,
 * which would be the only library in the app.
 */
public final class CameraFiles extends ContentProvider {

    static Uri uri(Context c) {
        return Uri.parse("content://" + c.getPackageName() + ".camera/shot.jpg");
    }

    static File file(Context c) {
        File dir = new File(c.getCacheDir(), "camera");
        dir.mkdirs();
        return new File(dir, "shot.jpg");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"shot.jpg".equals(uri.getLastPathSegment())) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file(getContext()), ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String sel, String[] args, String order) {
        File f = file(getContext());
        MatrixCursor c = new MatrixCursor(
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, 1);
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String sel, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String sel, String[] args) {
        return 0;
    }
}
