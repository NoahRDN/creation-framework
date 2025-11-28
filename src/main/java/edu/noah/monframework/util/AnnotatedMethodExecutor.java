package edu.noah.monframework.util;

import edu.noah.monframework.annotation.InvokeMe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Utility to discover and execute methods annotated with {@link InvokeMe}. It can
 * now inject request parameters into handler method arguments by matching parameter
 * names to form/query names.
 */
public class AnnotatedMethodExecutor {

    /** Result of invoking an annotated method. */
    public record InvocationResult(Method method, Object returnValue, boolean invoked) {}

    /** Execute annotated methods on a provided instance. */
    public static void executeAnnotatedMethods(Object instance) {
        if (instance == null) throw new IllegalArgumentException("instance must not be null");
        Class<?> clazz = instance.getClass();
        List<Method> methods = getAnnotatedMethods(clazz);
        for (Method m : methods) {
            if (m.getParameterCount() != 0) {
                throw new IllegalStateException("Annotated method must have no parameters: " + m);
            }
            invokeMethod(instance, m, new Object[0]);
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
                if (m.getParameterCount() != 0) {
                    throw new IllegalStateException("Annotated static method must have no parameters: " + m);
                }
                invokeStaticMethod(m, new Object[0]);
            } else {
                if (instance == null) {
                    instance = instantiate(clazz);
                }
                if (m.getParameterCount() != 0) {
                    throw new IllegalStateException("Annotated method must have no parameters: " + m);
                }
                invokeMethod(instance, m, new Object[0]);
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

    private static Object invokeStaticMethod(Method m, Object[] args) {
        try {
            if (!m.canAccess(null)) m.setAccessible(true);
            return m.invoke(null, args == null ? new Object[0] : args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke static method " + m, e);
        }
    }

    private static Object invokeMethod(Object instance, Method m, Object[] args) {
        try {
            if (!m.canAccess(instance)) m.setAccessible(true);
            return m.invoke(instance, args == null ? new Object[0] : args);
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

    /** Convenience overload when no servlet request/response context is available. */
    public static List<InvocationResult> executeAnnotatedMethodsForPath(Class<?> clazz, String path) {
        return executeAnnotatedMethodsForPath(clazz, path, null, null);
    }

    /**
     * Execute annotated methods whose annotation path matches the provided request path.
     * Supports exact matches and placeholder patterns like "/hello/{id}".
     * If multiple methods declare the same path, they are executed in a deterministic order (by
     * annotation path then method name).
     */
    public static List<InvocationResult> executeAnnotatedMethodsForPath(Class<?> clazz,
                                                                        String path,
                                                                        HttpServletRequest req,
                                                                        HttpServletResponse resp) {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        List<MethodCandidate> candidates = collectCandidates(clazz, path);
        List<InvocationResult> invoked = new ArrayList<>();
        Object instance = null;
        for (MethodCandidate candidate : candidates) {
            Method method = candidate.method();
            if (!candidate.supported()) {
                invoked.add(new InvocationResult(method, null, false));
                continue;
            }
            Object[] args = RequestParameterMapper.map(method, req, resp);
            Object returnValue;
            if (Modifier.isStatic(method.getModifiers())) {
                returnValue = invokeStaticMethod(method, args);
            } else {
                if (instance == null) instance = instantiate(clazz);
                returnValue = invokeMethod(instance, method, args);
            }
            invoked.add(new InvocationResult(method, returnValue, true));
        }
        return invoked;
    }

    /** Execute annotated methods on a specific instance whose annotation path equals the provided path. */
    public static List<InvocationResult> executeAnnotatedMethodsForPath(Object instance,
                                                                        String path,
                                                                        HttpServletRequest req,
                                                                        HttpServletResponse resp) {
        if (instance == null) throw new IllegalArgumentException("instance must not be null");
        if (path == null) throw new IllegalArgumentException("path must not be null");
        Class<?> clazz = instance.getClass();
        List<MethodCandidate> candidates = collectCandidates(clazz, path);
        List<InvocationResult> invoked = new ArrayList<>();
        for (MethodCandidate candidate : candidates) {
            Method method = candidate.method();
            if (!candidate.supported()) {
                invoked.add(new InvocationResult(method, null, false));
                continue;
            }
            Object[] args = RequestParameterMapper.map(method, req, resp);
            Object returnValue = invokeMethod(instance, method, args);
            invoked.add(new InvocationResult(method, returnValue, true));
        }
        return invoked;
    }

    /** Convenience overload for instance execution without servlet context. */
    public static List<InvocationResult> executeAnnotatedMethodsForPath(Object instance, String path) {
        return executeAnnotatedMethodsForPath(instance, path, null, null);
    }

    private record MethodCandidate(Method method, boolean supported) {}

    private static List<MethodCandidate> collectCandidates(Class<?> clazz, String path) {
        List<Method> all = getAnnotatedMethods(clazz);
        List<MethodCandidate> candidates = new ArrayList<>();
        for (Method m : all) {
            InvokeMe a = m.getAnnotation(InvokeMe.class);
            if (matchesPath(path, a)) {
                boolean supported = HandlerReturnTypeValidator.isSupported(m.getReturnType());
                candidates.add(new MethodCandidate(m, supported));
            }
        }
        return candidates;
    }

    private static boolean matchesPath(String path, InvokeMe annotation) {
        return annotation != null && RoutePathMatcher.matches(path, annotation.path());
    }
}
