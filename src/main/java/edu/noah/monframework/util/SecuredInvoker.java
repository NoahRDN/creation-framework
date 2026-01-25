package edu.noah.monframework.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Invokes a method while applying {@link edu.noah.monframework.annotation.Authorized}
 * and {@link edu.noah.monframework.annotation.Role} checks.
 * <p>
 * Direct Java calls cannot be intercepted automatically; use this helper for internal calls.
 */
public final class SecuredInvoker {
    private SecuredInvoker() {}

    public static Object invoke(Object target, String methodName, Object... args) {
        if (target == null) throw new IllegalArgumentException("target must not be null");
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }

        Method method = resolve(target.getClass(), methodName, args);
        if (!AuthGuard.isAllowed(method, null, null)) {
            throw new SecurityException("Access denied to method: " + method.getName());
        }

        try {
            if (!method.canAccess(target)) method.setAccessible(true);
            return method.invoke(target, args);
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke secured method: " + method, e);
        }
    }

    private static Method resolve(Class<?> type, String name, Object[] args) {
        int arity = args == null ? 0 : args.length;
        List<Method> candidates = new ArrayList<>();

        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) candidates.add(m);
        }
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) candidates.add(m);
        }

        candidates.removeIf(m -> !isCompatible(m.getParameterTypes(), args));
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No compatible method " + type.getName() + "#" + name
                    + " with " + arity + " args");
        }

        candidates.sort(Comparator.comparing((Method m) -> specificityScore(m.getParameterTypes()))
                .reversed()
                .thenComparing(Method::toGenericString));
        return candidates.get(0);
    }

    private static boolean isCompatible(Class<?>[] paramTypes, Object[] args) {
        if (paramTypes == null) return args == null || args.length == 0;
        if (args == null) return paramTypes.length == 0;
        if (paramTypes.length != args.length) return false;
        for (int i = 0; i < paramTypes.length; i++) {
            if (!isCompatible(paramTypes[i], args[i])) return false;
        }
        return true;
    }

    private static boolean isCompatible(Class<?> paramType, Object arg) {
        if (arg == null) return !paramType.isPrimitive();
        Class<?> argType = arg.getClass();
        if (paramType.isAssignableFrom(argType)) return true;
        if (!paramType.isPrimitive()) return false;

        if (paramType == int.class) return Integer.class.isAssignableFrom(argType);
        if (paramType == long.class) return Long.class.isAssignableFrom(argType);
        if (paramType == boolean.class) return Boolean.class.isAssignableFrom(argType);
        if (paramType == double.class) return Double.class.isAssignableFrom(argType);
        if (paramType == float.class) return Float.class.isAssignableFrom(argType);
        if (paramType == short.class) return Short.class.isAssignableFrom(argType);
        if (paramType == byte.class) return Byte.class.isAssignableFrom(argType);
        if (paramType == char.class) return Character.class.isAssignableFrom(argType);
        return false;
    }

    private static int specificityScore(Class<?>[] paramTypes) {
        if (paramTypes == null) return 0;
        int score = 0;
        for (Class<?> pt : paramTypes) {
            if (pt == null) continue;
            if (pt.isPrimitive()) score += 3;
            else if (Object.class.equals(pt)) score += 0;
            else score += 1;
        }
        return score;
    }
}

