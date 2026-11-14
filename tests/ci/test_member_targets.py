from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class MemberTargetsTest(unittest.TestCase):
    def test_renamed_members_wrong_type_decoys_and_ambiguity(self):
        source = '''package local.launcherx.patch;
import java.lang.reflect.Field;
public class MemberTargetsTest {
    static class Provider {}
    static class Holder { private Provider moved = new Provider(); }
    static class Decoy { Object c = new Object(); }
    static class Old { Holder ar = new Holder(); public void w(Provider p, boolean b) {} }
    static class Updated { Decoy ar = new Decoy(); private Holder aq = new Holder();
        public void x(Provider p, boolean b) {} public void w(boolean b) {} }
    static class Ambiguous { Holder a; Holder b; }
    static class DuplicateMethods { void x(Provider p, boolean b) {} void y(Provider p, boolean b) {} }
    static void refused(Runnable action, String expected) {
        try { action.run(); throw new AssertionError("Unsafe match accepted"); }
        catch (IllegalStateException error) { if (!error.getMessage().contains(expected)) throw error; }
    }
    public static void main(String[] args) throws Exception {
        for (Object owner : new Object[]{new Old(), new Updated()}) {
            Field[] path = MemberTargets.fieldPath(owner.getClass(), Provider.class);
            if (!(path[1].get(path[0].get(owner)) instanceof Provider)) throw new AssertionError();
            String expected = owner instanceof Old ? "w" : "x";
            if (!MemberTargets.method(owner.getClass(), void.class, false, Provider.class, boolean.class)
                    .getName().equals(expected)) throw new AssertionError();
        }
        refused(() -> MemberTargets.fieldPath(Ambiguous.class, Provider.class), "Ambiguous");
        refused(() -> MemberTargets.fieldPath(Decoy.class, Provider.class), "Missing");
        refused(() -> MemberTargets.method(DuplicateMethods.class, void.class, false, Provider.class, boolean.class), "Ambiguous");
        refused(() -> MemberTargets.method(Updated.class, void.class, true, Provider.class, boolean.class), "Missing");
    }
}
'''
        with tempfile.TemporaryDirectory(prefix='launcherx-members-') as directory:
            path = Path(directory)
            fixture = path / 'MemberTargetsTest.java'
            fixture.write_text(source)
            subprocess.run(['javac', '-d', path, fixture,
                            ROOT / 'module/src/local/launcherx/patch/MemberTargets.java'], check=True)
            subprocess.run(['java', '-cp', path, 'local.launcherx.patch.MemberTargetsTest'], check=True)
