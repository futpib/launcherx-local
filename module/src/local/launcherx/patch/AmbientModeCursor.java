package local.launcherx.patch;

import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.CharArrayBuffer;

/** Keep the anonymous account empty while allowing Ambient's account-free settings. */
final class AmbientModeCursor extends CursorWrapper {
    private final int mode;

    private AmbientModeCursor(Cursor cursor, int mode) {
        super(cursor);
        this.mode = mode;
    }

    static Cursor adapt(Cursor cursor) {
        if (cursor == null || cursor.getCount() != 1) return cursor;
        int mode = cursor.getColumnIndex("accountMode"), name = cursor.getColumnIndex("accountName");
        if (mode < 0 || name < 0) return cursor;
        int position = cursor.getPosition();
        try {
            if (!cursor.moveToFirst() || cursor.getInt(mode) != 2) return cursor;
            String account = cursor.getString(name);
            if (account != null && !account.isEmpty()) return cursor;
            return new AmbientModeCursor(cursor, mode);
        } finally {
            cursor.moveToPosition(position);
        }
    }

    // Mode 4 is the native unknown/no-account mode. Modes 0/1 assume a real
    // Google account and must never be fabricated for an Incognito profile.
    private boolean local(int column) { return column == mode && getPosition() == 0; }
    @Override public int getInt(int column) { return local(column) ? 4 : super.getInt(column); }
    @Override public long getLong(int column) { return local(column) ? 4 : super.getLong(column); }
    @Override public short getShort(int column) { return local(column) ? 4 : super.getShort(column); }
    @Override public float getFloat(int column) { return local(column) ? 4 : super.getFloat(column); }
    @Override public double getDouble(int column) { return local(column) ? 4 : super.getDouble(column); }
    @Override public String getString(int column) { return local(column) ? "4" : super.getString(column); }
    @Override public void copyStringToBuffer(int column, CharArrayBuffer buffer) {
        if (!local(column)) { super.copyStringToBuffer(column, buffer); return; }
        if (buffer.data == null || buffer.data.length == 0) buffer.data = new char[1];
        buffer.data[0] = '4'; buffer.sizeCopied = 1;
    }
}
