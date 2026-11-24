from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ResourcesTest(unittest.TestCase):
    def test_renumbered_sparse_ranges_and_malformed_headers(self):
        source = '''package local.launcherx.patch;
import java.nio.*;
import java.util.*;
public class ResourcesTest {
    static byte[] table(int pkg, int type, int offset) {
        ByteBuffer b = ByteBuffer.allocate(12 + 288 + 16 + 12).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)2).putShort((short)12).putInt(b.capacity()).putInt(1);
        b.putShort((short)0x200).putShort((short)288).putInt(b.capacity()-12).putInt(pkg);
        b.putInt(12+284, offset); b.position(12+288);
        b.putShort((short)0x202).putShort((short)16).putInt(28).put((byte)type).put((byte)0).putShort((short)0).putInt(3);
        b.putInt(0).putInt(0).putInt(0);
        return b.array();
    }
    static void refuse(byte[] bytes) {
        try { ResourceTable.ranges(bytes); throw new AssertionError("Malformed resource accepted"); }
        catch (IllegalStateException expected) {}
    }
    public static void main(String[] args) {
        for (int id : new int[]{1, 20, 34}) {
            Map<Integer,Integer> ranges=ResourceTable.ranges(table(127,id,2));
            if (!ranges.equals(Collections.singletonMap(0x7f000000 | ((id+2)<<16),3))) throw new AssertionError(ranges);
        }
        refuse(new byte[8]);
        byte[] bytes=table(127,20,0); ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(12+288+4,0); refuse(bytes);
        bytes=table(127,20,0); b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(12+288+12,65537); refuse(bytes);
    }
}
'''
        with tempfile.TemporaryDirectory(prefix='launcherx-resources-') as directory:
            path = Path(directory)
            fixture = path / 'ResourcesTest.java'
            fixture.write_text(source)
            subprocess.run(['javac', '-d', path, fixture,
                            ROOT / 'module/src/local/launcherx/patch/ResourceTable.java'], check=True)
            subprocess.run(['java', '-cp', path, 'local.launcherx.patch.ResourcesTest'], check=True)
