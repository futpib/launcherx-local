package local.launcherx.patch;

import de.robv.android.xposed.XposedHelpers;
import java.util.HashMap;
import java.util.Map;

/** Reviewed executable bindings, selected only after Compatibility verifies every DEX. */
final class LauncherSymbols extends ClassLoader {
    final long version;
    final boolean modern;
    private final Map<String, String> classes = new HashMap<>();

    LauncherSymbols(ClassLoader parent, long version) {
        super(parent);
        this.version = version;
        modern = version == 835645;
        if (version != 702401 && !modern) throw new IllegalArgumentException("No bindings for " + version);
        if (modern) {
            String[][] bindings = {
                {"isa", "jeo"}, {"isn", "jfd"}, {"isf", "jew"}, {"idj", "iqa"},
                {"kye", "lhs"}, {"kxv", "lia"}, {"hsx", "inm"}, {"lhw", "lrm"},
                {"xgr", "xyy"}, {"vuw", "vit"}, {"lft", "lps"}, {"lfs", "lpr"},
                {"kve", "lez"}, {"vip", "uzp"}, {"yjv", "dii"}, {"vhe", "uyc"},
                {"vjm", "van"}, {"vjr", "var"}, {"vjn", "vao"}, {"vnq", "vdq"},
                {"kxc", "lhb"}, {"kwx", "lgw"}, {"kwe", "lge"}, {"kwf", "lgf"},
                {"juj", "kxe"}, {"wqw", "wdv"}, {"wrb", "wea"}, {"wyc", "wmc"},
                {"igc", "isq"}, {"lhj", "lrc"}, {"abbc", "aafi"}, {"aaug", "zxv"},
                {"aahl", "zhf"}, {"ljh", "lsh"}, {"lji", "lsi"}, {"ljf", "lsf"},
                {"zej", "yiw"}, {"aagk", "zge"}, {"abbi", "aafo"}, {"aaff", "zeu"},
                {"fxr", "gkp"}, {"fyt", "gls"}, {"aaht", "zho"}, {"fyw", "glv"},
                {"xnc", "xcm"}, {"tzk", "the"}, {"tze", "tgy"}, {"tzf", "tgz"},
                {"tzg", "tha"}, {"tzh", "thb"}, {"tzd", "tgx"}, {"lmj", "lvj"},
                {"lma", "lvd"}, {"llx", "lva"}, {"gbp", "goe"}, {"gcf", "got"},
                {"wga", "vuh"}, {"lmb", "lve"}, {"uzs", "uuw"}, {"ibq", "inx"},
                {"ibv", "iog"}, {"kna", "kxd"}
            };
            for (String[] binding : bindings) classes.put(binding[0], binding[1]);
        }
    }

    @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
        return getParent().loadClass(classes.containsKey(name) ? classes.get(name) : name);
    }

    String pick(String legacy, String current) { return modern ? current : legacy; }
    int pick(int legacy, int current) { return modern ? current : legacy; }
    Object finish(Object builder) { return XposedHelpers.callMethod(builder, pick("r", "p")); }
    void copyOnWrite(Object builder) { XposedHelpers.callMethod(builder, pick("w", "t")); }
    Object emptyBytes() throws ClassNotFoundException {
        return XposedHelpers.getStaticObjectField(loadClass("zej"), pick("b", "d"));
    }
}
