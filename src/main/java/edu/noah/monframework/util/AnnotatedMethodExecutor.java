package edu.noah.monframework.util;

import edu.noah.monframework.annotation.InvokeMe;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Utility to discover and execute methods annotated with {@link InvokeMe}.
 *
 * Usage examples:
 * - AnnotatedMethodExecutor.executeAnnotatedMethods(new MyClass());
 * - AnnotatedMethodExecutor.executeAnnotatedMethods(MyClass.class);
 */
public class AnnotatedMethodExecutor {

    /** Result of invoking an annotated method. */
    public record InvocationResult(Method method, Object returnValue) {}

    /** Execute annotated methods on a provided instance. */
    public static void executeAnnotatedMethods(Object instance) {
        if (instance == null) throw new IllegalArgumentException("instance must not be null");
        Class<?> clazz = instance.getClass();
        List<Method> methods = getAnnotatedMethods(clazz);
        for (Method m : methods) {
            invokeMethod(instance, m);
        }
    }

    /** Execute annotated methods on a class. If methods are instance methods, the class must have a
     * public no-arg constructor which will be used to create an instance. Static annotated methods are
     * invoked directly.
     */
    public static void executeAnnotatedMethods(Class<?> clazz) {
        List<Method> methods = getAnnotatedMethods(clazz);
        Object instance = null;
        for (Method m : methods) {
            if (Modifier.isStatic(m.getModifiers())) {
                invokeStaticMethod(m);
            } else {
                if (instance == null) {
                    instance = instantiate(clazz);
                }
                invokeMethod(instance, m);
            }
        }
    }

    private static Object instantiate(Class<?> clazz) {
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            if (!ctor.canAccess(null)) ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No no-arg constructor available for " + clazz.getName(), e);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to instantiate " + clazz.getName(), e);
        }
    }

    private static void invokeStaticMethod(Method m) {
        try {
            if (!m.canAccess(null)) m.setAccessible(true);
            if (m.getParameterCount() != 0) {
                throw new IllegalStateException("Annotated static method must have no parameters: " + m);
            }
            m.invoke(null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke static method " + m, e);
        }
    }

    private static void invokeMethod(Object instance, Method m) {
        try {
            if (!m.canAccess(instance)) m.setAccessible(true);
            if (m.getParameterCount() != 0) {
                throw new IllegalStateException("Annotated method must have no parameters: " + m);
            }
            m.invoke(instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke method " + m + " on " + instance, e);
        }
    }

    private static List<Method> getAnnotatedMethods(Class<?> clazz) {
        List<Method> out = new ArrayList<>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(InvokeMe.class)) {
                out.add(m);
            }
        }
        // sort by annotation path then name for deterministic behavior
        out.sort(Comparator.comparing((Method m) -> {
            InvokeMe a = m.getAnnotation(InvokeMe.class);
            return (a == null) ? "" : a.path();
        }).thenComparing(Method::getName));
        return out;
    }

    /** Execute annotated methods whose annotation path matches the provided request path.
    * Supports exact matches and placeholder patterns like "/hello/{id}".
    * If multiple methods declare the same path, they are executed in a deterministic order (by
    * annotation path then method name).
     */
    public static List<Method> executeAnnotatedMethodsForPath(Class<?> clazz, String path) {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        List<Method> all = getAnnotatedMethods(clazz);
        List<Method> matched = new ArrayList<>();
        for (Method m : all) {
            InvokeMe a = m.getAnnotation(InvokeMe.class);
            if (matchesPath(path, a)) matched.add(m);
        }
        List<InvocationResult> invoked = new ArrayList<>();
        Object instance = null;
        for (Method m : matched) {
            Object returnValue;
            if (Modifier.isStatic(m.getModifiers())) {
                returnValue = invokeStaticMethod(m);
            } else {
                if (instance == null) instance = instantiate(clazz);
                returnValue = invokeMethod(instance, m);
            }
            invoked.add(new InvocationResult(m, returnValue));
        }
        return invoked;
    }

    /** Execute annotated methods on a specific instance whose annotation path equals the provided path. */
    public static List<Method> executeAnnotatedMethodsForPath(Object instance, String path) {
        if (instance == null) throw new IllegalArgumentException("instance must not be null");
        if (path == null) throw new IllegalArgumentException("path must not be null");
        Class<?> clazz = instance.getClass();
        List<Method> all = getAnnotatedMethods(clazz);
        List<InvocationResult> invoked = new ArrayList<>();
        for (Method m : all) {
            InvokeMe a = m.getAnnotation(InvokeMe.class);
            if (a != null && path.equals(a.path())) {
                Object returnValue = invokeMethod(instance, m);
                invoked.add(new InvocationResult(m, returnValue));
            }
        }
        return invoked;
    }

    private static boolean matchesPath(String path, InvokeMe annotation) {
        return annotation != null && RoutePathMatcher.matches(path, annotation.path());
    }
}
