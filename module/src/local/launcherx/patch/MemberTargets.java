package local.launcherx.patch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Resolve a structural contract only when exactly one member path satisfies it. */
final class MemberTargets {
    static Field[] fieldPath(Class<?> owner, Class<?> target) {
        Field[] match = null;
        for (Field outer : owner.getDeclaredFields()) {
            if (Modifier.isStatic(outer.getModifiers()) || outer.getType().isPrimitive()) continue;
            for (Field inner : outer.getType().getDeclaredFields()) {
                if (Modifier.isStatic(inner.getModifiers()) || inner.getType() != target) continue;
                if (match != null) throw new IllegalStateException("Ambiguous field path from " + owner.getName() + " to " + target.getName());
                match = new Field[]{outer, inner};
            }
        }
        if (match == null) throw new IllegalStateException("Missing field path from " + owner.getName() + " to " + target.getName());
        for (Field field : match) field.setAccessible(true);
        return match;
    }

    static Method method(Class<?> owner, Class<?> result, boolean isStatic, Class<?>... parameters) {
        Method match = null;
        for (Method method : owner.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) != isStatic || method.getReturnType() != result
                    || !java.util.Arrays.equals(method.getParameterTypes(), parameters)) continue;
            if (match != null) throw new IllegalStateException("Ambiguous method contract in " + owner.getName());
            match = method;
        }
        if (match == null) throw new IllegalStateException("Missing method contract in " + owner.getName());
        match.setAccessible(true);
        return match;
    }
}
