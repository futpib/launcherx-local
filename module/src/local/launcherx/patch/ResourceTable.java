package local.launcherx.patch;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/** Resource-ID ranges from Android's ResTable_package / ResTable_typeSpec headers. */
final class ResourceTable {
    static Map<Integer, Integer> ranges(byte[] data) {
        ByteBuffer bytes = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 12 || u16(bytes, 0) != 2 || bytes.getInt(4) != data.length)
            throw new IllegalStateException("Invalid resource table");
        Map<Integer, Integer> result = new TreeMap<>();
        chunks(bytes, u16(bytes, 2), data.length, 0, 0, result);
        return result;
    }

    private static int u16(ByteBuffer b, int p) { return b.getShort(p) & 65535; }

    private static void chunks(ByteBuffer b, int start, int end, int pkg, int offset, Map<Integer, Integer> result) {
        if (start < 8 || start > end) throw new IllegalStateException("Invalid resource header");
        for (int p = start; p < end;) {
            if (end - p < 8) throw new IllegalStateException("Truncated resource chunk");
            int type = u16(b, p), header = u16(b, p + 2), size = b.getInt(p + 4);
            if (header < 8 || size < header || size > end - p) throw new IllegalStateException("Invalid resource chunk");
            if (type == 0x200) {
                if (pkg != 0 || header < 284) throw new IllegalStateException("Invalid resource package");
                int id = b.getInt(p + 8), typeOffset = header >= 288 ? b.getInt(p + 284) : 0;
                if (id <= 0 || id > 255 || typeOffset < 0 || typeOffset > 254) throw new IllegalStateException("Invalid resource package ID");
                chunks(b, p + header, p + size, id, typeOffset, result);
            } else if (type == 0x202) {
                if (pkg == 0 || header < 16) throw new IllegalStateException("Invalid resource type specification");
                int id = (b.get(p + 8) & 255) + offset, count = b.getInt(p + 12);
                if (id <= 0 || id > 255 || count < 0 || count > 65536 || (long) count * 4 > size - header)
                    throw new IllegalStateException("Invalid resource entry count");
                if (result.put((pkg << 24) | (id << 16), count) != null)
                    throw new IllegalStateException("Duplicate resource type specification");
            }
            p += size;
        }
    }
}
