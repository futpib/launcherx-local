package local.launcherx.patch;

import android.content.Context;
import android.util.Log;
import android.widget.ImageView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Fill local preview cards using the launcher's own Glide transformation. */
final class LocalArtwork {
    private static final String TAG = "LauncherXLocal";
    private final Constructor<?> copy;
    private final Method cloneBuilder;
    private final Method transform;
    private final Method build;
    private final Object centerCrop;
    private final Method assetFactory;
    private final Field assetId;
    private final Object localRole;
    private final Method entityFactory;
    private final Field entityId;
    private boolean logged;

    LocalArtwork(LauncherSymbols loader) throws Exception {
        Class<?> builder = loader.loadClass(loader.pick("ito", "jgd"));
        Class<?> image = loader.loadClass(loader.pick("itq", "jgf"));
        Class<?> transformation = loader.loadClass(loader.pick("dkp", "eig"));
        Class<?> crop = loader.loadClass(loader.pick("dqh", "enw"));
        if (!transformation.isAssignableFrom(crop)) throw new IllegalStateException("Unknown crop type");
        centerCrop = crop.getDeclaredConstructor().newInstance();
        transform = builder.getDeclaredMethod("j", transformation);
        build = builder.getDeclaredMethod("b");
        copy = loader.modern ? builder.getDeclaredConstructor(image) : null;
        cloneBuilder = loader.modern ? null : builder.getDeclaredMethod("a");
        Class<?> asset = loader.loadClass(loader.pick("aapk", "zqu"));
        Class<?> role = loader.loadClass(loader.pick("aaoz", "zqj"));
        localRole = role.getDeclaredField("p").get(null);
        if (!"ROLE_LOCAL_ASSET".equals(((Enum<?>) localRole).name())) {
            throw new IllegalStateException("Unknown local image role");
        }
        assetId = asset.getDeclaredField("c");
        Class<?> factory = loader.loadClass(loader.pick("nnf", "otk"));
        assetFactory = loader.modern
                ? factory.getDeclaredMethod("h", asset, role, ImageView.class, int.class)
                : factory.getDeclaredMethod("f", asset, role, int.class);
        Class<?> entity = loader.loadClass(loader.pick("frd", "gfc"));
        entityId = entity.getDeclaredField("c");
        entityFactory = loader.modern
                ? entity.getDeclaredMethod("f", Context.class, ImageView.class)
                : entity.getDeclaredMethod("f", Context.class);
        Class<?> result = loader.modern ? image : builder;
        if (assetFactory.getReturnType() != result || entityFactory.getReturnType() != result
                || build.getReturnType() != image) throw new IllegalStateException("Unknown image factory");
    }

    void install(List<XC_MethodHook.Unhook> hooks) {
        hooks.add(XposedBridge.hookMethod(assetFactory, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) throws Throwable {
                if (hook.args[1] == localRole && local(assetId.get(hook.args[0]))) fill(hook);
            }
        }));
        hooks.add(XposedBridge.hookMethod(entityFactory, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) throws Throwable {
                if (local(entityId.get(hook.thisObject))) fill(hook);
            }
        }));
    }

    private static boolean local(Object id) {
        return id instanceof String && ((String) id).startsWith("local:asset:");
    }

    private void fill(XC_MethodHook.MethodHookParam hook) throws Exception {
        if (hook.hasThrowable() || hook.getResult() == null) return;
        Object builder = copy != null ? copy.newInstance(hook.getResult()) : cloneBuilder.invoke(hook.getResult());
        // The centerCrop flag alone skips images with zero target overrides. An explicit
        // transform also keys Glide's bitmap cache and avoids changing recycled views.
        transform.invoke(builder, centerCrop);
        hook.setResult(copy != null ? build.invoke(builder) : builder);
        if (!logged) {
            logged = true;
            Log.i(TAG, "local preview artwork uses CenterCrop");
        }
    }
}
