package local.launcherx.patch;

import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;

/** Run against Android's real cursor implementation with app_process. */
public final class ModeCursorProbe {
    private static MatrixCursor row(int mode, String account) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"accountName", "accountMode", "profileLock", "payload"});
        cursor.addRow(new Object[]{account, mode, 1, new byte[]{3, 7}});
        Bundle extras = new Bundle(); extras.putString("sentinel", "retained"); cursor.setExtras(extras);
        return cursor;
    }

    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }

    public static void main(String[] args) {
        MatrixCursor original = row(2, "");
        Cursor cursor = AmbientModeCursor.adapt(original);
        check(cursor != original && cursor.getPosition() == -1);
        check("retained".equals(cursor.getExtras().getString("sentinel")));
        check(cursor.moveToFirst());
        check(cursor.getInt(1) == 4 && cursor.getLong(1) == 4 && cursor.getShort(1) == 4);
        check(cursor.getFloat(1) == 4 && cursor.getDouble(1) == 4 && "4".equals(cursor.getString(1)));
        CharArrayBuffer buffer = new CharArrayBuffer(0); cursor.copyStringToBuffer(1, buffer);
        check(buffer.sizeCopied == 1 && buffer.data[0] == '4');
        check("".equals(cursor.getString(0)) && cursor.getInt(2) == 1 && cursor.getBlob(3)[1] == 7);
        check(original.getInt(1) == 2);
        cursor.close(); check(original.isClosed());
        for (int mode : new int[]{0, 1, 3, 4, Integer.MAX_VALUE}) {
            original = row(mode, "");
            check(AmbientModeCursor.adapt(original) == original && original.getPosition() == -1);
            original.close();
        }
        original = row(2, "unexpected-account");
        original.moveToFirst();
        check(AmbientModeCursor.adapt(original) == original && original.getPosition() == 0);
        original.close();
        original = row(2, ""); original.addRow(new Object[]{"", 2, 1, null});
        check(AmbientModeCursor.adapt(original) == original); original.close();
        original = new MatrixCursor(new String[]{"accountMode"}); original.addRow(new Object[]{2});
        check(AmbientModeCursor.adapt(original) == original); original.close();
        check(AmbientModeCursor.adapt(null) == null);
        System.out.println("PASS: anonymous-only adaptation, identity/lock/blob/extras preservation, position and close delegation");
    }
}
