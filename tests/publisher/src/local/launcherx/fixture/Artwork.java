package local.launcherx.fixture;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public final class Artwork extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = new File(getContext().getCacheDir(), "poster-" + uri.hashCode() + ".png");
        if (!file.exists()) {
            Bitmap image = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(image);
            canvas.drawColor(uri.toString().contains("watch") ? Color.rgb(18, 83, 130) : Color.rgb(100, 55, 130));
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            paint.setTextSize(40);
            canvas.drawText(uri.getLastPathSegment(), 40, 180, paint);
            try (FileOutputStream output = new FileOutputStream(file)) {
                image.compress(Bitmap.CompressFormat.PNG, 100, output);
            } catch (Exception error) {
                throw new FileNotFoundException(error.toString());
            } finally { image.recycle(); }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
