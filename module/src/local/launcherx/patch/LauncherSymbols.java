package local.launcherx.patch;

import de.robv.android.xposed.XposedHelpers;
import java.util.HashMap;
import java.util.Map;

/** Reviewed executable bindings, selected only after Compatibility verifies every DEX. */
final class LauncherSymbols extends ClassLoader {
    final long version;
    final boolean modern;
    final boolean recent;
    final boolean latest;
    private final Map<String, String> classes = new HashMap<>();

    LauncherSymbols(ClassLoader parent, Compatibility.Result compatibility) {
        this(parent, compatibility.bindings);
        classes.putAll(compatibility.classes);
    }

    LauncherSymbols(ClassLoader parent, long version) {
        super(parent);
        this.version = version;
        classes.put("ambient_account_provider", Compatibility.TARGET + ".coreservices.account.AccountModeContentProvider");
        latest = version == 841183;
        recent = version == 837595 || latest;
        modern = version == 835645 || recent;
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
        if (recent) {
            String[][] bindings = {
                {"isa", "jal"},
                {"isn", "jba"},
                {"isf", "jat"},
                {"idj", "ilw"},
                {"kye", "lej"},
                {"kxv", "ler"},
                {"hsx", "ijm"},
                {"lhw", "lnz"},
                {"xgr", "xkw"},
                {"vuw", "vjq"},
                {"lft", "lmg"},
                {"lfs", "lmf"},
                {"kve", "lbq"},
                {"vip", "vaj"},
                {"yjv", "voh"},
                {"vhe", "uyw"},
                {"vjm", "vbi"},
                {"vjr", "vbm"},
                {"vjn", "vbj"},
                {"vnq", "vem"},
                {"kxc", "lds"},
                {"kwx", "ldn"},
                {"kwe", "lcv"},
                {"kwf", "lcw"},
                {"juj", "ktc"},
                {"wqw", "wej"},
                {"wrb", "weo"},
                {"wyc", "wmp"},
                {"igc", "ion"},
                {"lhj", "lnp"},
                {"abbc", "aahv"},
                {"aaug", "aaai"},
                {"aahl", "zjr"},
                {"ljh", "lov"},
                {"lji", "low"},
                {"ljf", "lot"},
                {"zej", "ylj"},
                {"aagk", "ziq"},
                {"abbi", "aaib"},
                {"aaff", "zhf"},
                {"fxr", "ggi"},
                {"fyt", "ghl"},
                {"aaht", "zka"},
                {"fyw", "ghp"},
                {"xnc", "xda"},
                {"tzk", "tht"},
                {"tze", "thn"},
                {"tzf", "tho"},
                {"tzg", "thp"},
                {"tzh", "thq"},
                {"tzd", "thm"},
                {"lmj", "lru"},
                {"lma", "lro"},
                {"llx", "lrl"},
                {"gbp", "gka"},
                {"gcf", "gko"},
                {"wga", "vuv"},
                {"lmb", "lrp"},
                {"uzs", "uru"},
                {"ibq", "ijv"},
                {"ibv", "ikd"},
                {"kna", "ktb"},
                {"lrs", "lof"},
                {"addc", "adgq"},
                {"zhw", "zkh"},
                {"adbk", "adey"},
                {"xhu", "xik"},
                {"xje", "xju"},
                {"inw", "iju"},
                {"oro", "opr"},
                {"jgd", "jca"},
                {"jgf", "jcc"},
                {"eig", "eea"},
                {"enw", "ejq"},
                {"zqu", "ztf"},
                {"zqj", "zsu"},
                {"otk", "orn"},
                {"gfc", "gax"}
            };
            for (String[] binding : bindings) classes.put(binding[0], binding[1]);
            classes.put("basic_network_exit", "orl");
            classes.put("basic_exit_reason", "xfg");
        }
        if (latest) {
            String[][] bindings = {
                {"aaug", "aaem"},
                {"abbc", "aalz"},
                {"abbi", "aamf"},
                {"adbk", "adkd"},
                {"addc", "adlv"},
                {"fragment", "bh"},
                {"ambient_account_provider", "com.google.android.apps.tv.launcherx.coreservices.account.AccountModeContentProvider"},
                {"profile_tabs", "com.google.android.apps.tv.launcherx.guide.tabs.GuideTabsLayout"},
                {"eig", "ecu"},
                {"enw", "eik"},
                {"gfc", "fzr"},
                {"fxr", "gfb"},
                {"fyt", "gge"},
                {"fyw", "ggi"},
                {"gbp", "giz"},
                {"gcf", "gjn"},
                {"hsx", "iin"},
                {"inw", "fdo"},
                {"ibq", "iiw"},
                {"ibv", "ijf"},
                {"idj", "iky"},
                {"igc", "inp"},
                {"profile_tabs_peer", "itb"},
                {"isa", "izp"},
                {"isf", "izx"},
                {"isn", "jae"},
                {"jgd", "jbe"},
                {"jgf", "jbg"},
                {"kna", "ksg"},
                {"juj", "ksh"},
                {"kve", "lav"},
                {"kwe", "lca"},
                {"kwf", "lcb"},
                {"kwx", "lcs"},
                {"kxc", "lcx"},
                {"kye", "ldn"},
                {"kxv", "ldv"},
                {"lfs", "lll"},
                {"lft", "llm"},
                {"lhj", "lmu"},
                {"lhw", "lne"},
                {"lrs", "lnl"},
                {"ljf", "lnz"},
                {"ljh", "lob"},
                {"lji", "loc"},
                {"llx", "lqs"},
                {"lma", "lqv"},
                {"lmb", "lqw"},
                {"lmj", "lrb"},
                {"oro", "oqe"},
                {"basic_network_exit", "osa"},
                {"otk", "osc"},
                {"tzd", "tkm"},
                {"tze", "tkn"},
                {"tzf", "tko"},
                {"tzg", "tkp"},
                {"tzh", "tkq"},
                {"tzk", "tku"},
                {"uzs", "uzu"},
                {"vhe", "vdb"},
                {"vip", "vep"},
                {"vjm", "vfo"},
                {"vjn", "vfp"},
                {"vjr", "vfs"},
                {"vnq", "vip"},
                {"vuw", "vnu"},
                {"yjv", "od"},
                {"wga", "vyw"},
                {"wqw", "wih"},
                {"wrb", "wim"},
                {"wyc", "wqq"},
                {"xnc", "xhb"},
                {"basic_exit_reason", "xjh"},
                {"xhu", "xmj"},
                {"xje", "xnt"},
                {"xgr", "xov"},
                {"zej", "ypl"},
                {"proto_builder", "yqg"},
                {"proto_message", "yqo"},
                {"aaff", "zli"},
                {"aagk", "zms"},
                {"aahl", "znu"},
                {"aaht", "zod"},
                {"zhw", "zok"},
                {"zqj", "zwx"},
                {"zqu", "zxi"}
            };
            for (String[] binding : bindings) classes.put(binding[0], binding[1]);
        }
    }

    @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
        return getParent().loadClass(classes.containsKey(name) ? classes.get(name) : name);
    }

    String pick(String legacy, String current) { return modern ? current : legacy; }
    String pick(String legacy, String current, String latest) { return recent ? latest : pick(legacy, current); }
    String pick(String legacy, String current, String recent, String latest) { return this.latest ? latest : pick(legacy, current, recent); }
    int pick(int legacy, int current) { return modern ? current : legacy; }
    int pick(int legacy, int current, int latest) { return recent ? latest : pick(legacy, current); }
    int pick(int legacy, int current, int recent, int latest) { return this.latest ? latest : pick(legacy, current, recent); }
    Object finish(Object builder) { return XposedHelpers.callMethod(builder, pick("r", "p")); }
    void copyOnWrite(Object builder) { XposedHelpers.callMethod(builder, pick("w", "t")); }
    Object emptyBytes() throws ClassNotFoundException {
        return XposedHelpers.getStaticObjectField(loadClass("zej"), pick("b", "d"));
    }
}
