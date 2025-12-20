package edu.noah.monframework.util;

import edu.noah.monframework.annotation.FormObject;
import edu.noah.monframework.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
     * Utility responsible for building the argument list passed to @InvokeMe methods
     * from the incoming {@link HttpServletRequest}. Parameters are matched by name:
     * - {@link HttpServletRequest} and {@link HttpServletResponse} are injected directly.
     * - {@link java.util.Map} parameters receive all request params (including checkbox
     *   multi-values) merged with any path variables.
     * - Custom object types are instantiated and populated from request parameters using
     *   dot-notation with the parameter name as prefix (e.g. {@code user.name}, {@code user.address.city}).
     * - Simple types (String, primitive wrappers, primitives, enums) are populated from
 *   path variables (if present) or request parameters with the same name. When conversion fails,
 *   primitives fall back to their default value while wrapper types become {@code null}.
 */
public final class RequestParameterMapper {
    private RequestParameterMapper() {}

    private static final Map<String, Map<String, Class<?>>> FORM_OBJECT_CACHE = new ConcurrentHashMap<>();

    public static Object[] map(Method method,
                               HttpServletRequest req,
                               HttpServletResponse resp,
                               String requestPath,
                               String annotatedPath) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        Map<String, String> pathVars = PathVariableExtractor.extract(requestPath, annotatedPath);
        Map<String, Object> aggregated = buildParameterMap(req, pathVars);
        Map<String, byte[]> uploadData = extractUploads(req);
        for (Map.Entry<String, byte[]> e : uploadData.entrySet()) {
            aggregated.putIfAbsent(e.getKey(), toBoxed(e.getValue()));
        }
        prebindAnnotatedFormObjects(method, aggregated);
        prebindComplexParameters(parameters, aggregated);
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Class<?> type = parameter.getType();

            if (HttpServletRequest.class.isAssignableFrom(type)) {
                args[i] = req;
                continue;
            }
            if (HttpServletResponse.class.isAssignableFrom(type)) {
                args[i] = resp;
                continue;
            }
            if (Map.class.isAssignableFrom(type)) {
                if (wantsByteArrayMap(parameter)) {
                    Map<String, Byte[]> boxed = new LinkedHashMap<>();
                    for (Map.Entry<String, byte[]> entry : uploadData.entrySet()) {
                        boxed.put(entry.getKey(), toBoxed(entry.getValue()));
                    }
                    args[i] = boxed;
                } else {
                    // Provide a fresh copy so handlers can mutate safely.
                    args[i] = new LinkedHashMap<>(aggregated);
                }
                continue;
            }

            String name = resolveName(parameter);
            Object raw = aggregated.get(name);

            if (isSimpleType(type)) {
                String rawValue = rawToString(raw, pathVars.get(name));
                args[i] = convert(rawValue, type);
            } else {
                Object bound = raw;
                if (bound == null || !type.isInstance(bound)) {
                    bound = bindObject(type, name, aggregated);
                }
                args[i] = bound;
            }
        }
        return args;
    }

    private static Map<String, Object> buildParameterMap(HttpServletRequest req, Map<String, String> pathVars) {
        Map<String, Object> data = new LinkedHashMap<>();

        if (req != null) {
            Map<String, String[]> requestParams = req.getParameterMap();
            for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isBlank()) continue;
                String[] values = entry.getValue();
                Object value;
                if (values == null) {
                    value = null;
                } else if (values.length == 1) {
                    value = values[0];
                } else {
                    // Preserve checkbox/multi-select values.
                    value = Arrays.asList(values);
                }
                data.put(key, value);
            }
        }

        if (pathVars != null) {
            for (Map.Entry<String, String> entry : pathVars.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isBlank()) continue;
                data.put(key, entry.getValue());
            }
        }

        return data;
    }

    private static Map<String, byte[]> extractUploads(HttpServletRequest req) {
        Map<String, byte[]> uploads = new LinkedHashMap<>();
        if (req == null) return uploads;
        String contentType = req.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("multipart/form-data")) {
            return uploads;
        }
        Map<String, Integer> counters = new HashMap<>();
        try {
            for (Part part : req.getParts()) {
                String filename = part.getSubmittedFileName();
                if (filename == null) continue; // not a file part
                String baseName = part.getName();
                int count = counters.getOrDefault(baseName, 0);
                counters.put(baseName, count + 1);
                String key = (count == 0) ? baseName : baseName + "[" + count + "]";
                try (var is = part.getInputStream()) {
                    byte[] bytes = is.readAllBytes();
                    uploads.put(key, bytes);
                }
            }
        } catch (Exception ex) {
            System.out.println("RequestParameterMapper: Failed to parse multipart upload -> "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            // best effort; leave uploads empty if parsing fails
        }
        return uploads;
    }

    private static void prebindAnnotatedFormObjects(Method method, Map<String, Object> aggregated) {
        if (method == null || aggregated == null || aggregated.isEmpty()) return;
        Set<String> prefixes = extractPrefixes(aggregated.keySet());
        if (prefixes.isEmpty()) return;

        String basePackage = method.getDeclaringClass().getPackageName();
        Map<String, Class<?>> mapping = FORM_OBJECT_CACHE.computeIfAbsent(basePackage, key -> {
            Map<String, Class<?>> out = new HashMap<>();
            for (Class<?> c : ClassScanner.findAllClasses(basePackage)) {
                FormObject fo = c.getAnnotation(FormObject.class);
                if (fo == null) continue;
                String name = fo.value();
                if (name != null && !name.isBlank()) {
                    out.put(name, c);
                }
            }
            return Collections.unmodifiableMap(out);
        });

        if (mapping.isEmpty()) return;
        for (String prefix : prefixes) {
            if (aggregated.containsKey(prefix)) continue;
            Class<?> type = mapping.get(prefix);
            if (type == null) continue;
            Object bound = bindObject(type, prefix, aggregated);
            if (bound != null) aggregated.put(prefix, bound);
        }
    }

    private static Set<String> extractPrefixes(Set<String> keys) {
        if (keys == null || keys.isEmpty()) return Collections.emptySet();
        java.util.Set<String> prefixes = new java.util.LinkedHashSet<>();
        for (String key : keys) {
            if (key == null) continue;
            int dot = key.indexOf('.');
            if (dot <= 0) continue;
            String prefix = key.substring(0, dot);
            if (!prefix.isBlank()) prefixes.add(prefix);
        }
        return prefixes;
    }

    private static void prebindComplexParameters(Parameter[] parameters, Map<String, Object> aggregated) {
        for (Parameter parameter : parameters) {
            Class<?> type = parameter.getType();
            if (HttpServletRequest.class.isAssignableFrom(type)) continue;
            if (HttpServletResponse.class.isAssignableFrom(type)) continue;
            if (Map.class.isAssignableFrom(type)) continue;
            if (isSimpleType(type)) continue;

            String name = resolveName(parameter);
            Object bound = bindObject(type, name, aggregated);
            if (bound != null) {
                aggregated.put(name, bound);
            }
        }
    }

    private static Object bindObject(Class<?> type, String prefix, Map<String, Object> source) {
        try {
            Object instance = instantiate(type);
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                String keyWithPrefix = (prefix == null || prefix.isBlank())
                        ? field.getName()
                        : prefix + "." + field.getName();

                Object raw = source.get(keyWithPrefix);
                if (raw == null) {
                    raw = source.get(field.getName()); // fallback sans prefix si rien trouvé
                }

                Object value;
                Class<?> fieldType = field.getType();
                if (isSimpleType(fieldType)) {
                    value = convert(rawToString(raw, null), fieldType);
                } else if (Map.class.isAssignableFrom(fieldType)) {
                    value = new LinkedHashMap<>(source);
                } else if (Collection.class.isAssignableFrom(fieldType)) {
                    value = toCollection(raw);
                } else if (fieldType.isArray() && isSimpleType(fieldType.getComponentType())) {
                    value = toArray(raw, fieldType.getComponentType());
                } else {
                    value = bindObject(fieldType, keyWithPrefix, source);
                }

                if (!field.canAccess(instance)) field.setAccessible(true);
                field.set(instance, value);
            }
            return instance;
        } catch (Exception e) {
            return null;
        }
    }

    private static Object instantiate(Class<?> type) throws Exception {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException("Cannot instantiate type: " + type.getName());
        }
        var ctor = type.getDeclaredConstructor();
        if (!ctor.canAccess(null)) ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static boolean isSimpleType(Class<?> targetType) {
        if (targetType == null) return false;
        return String.class.equals(targetType)
                || targetType.isEnum()
                || Integer.class.equals(targetType) || int.class.equals(targetType)
                || Long.class.equals(targetType) || long.class.equals(targetType)
                || Double.class.equals(targetType) || double.class.equals(targetType)
                || Boolean.class.equals(targetType) || boolean.class.equals(targetType)
                || Float.class.equals(targetType) || float.class.equals(targetType)
                || Short.class.equals(targetType) || short.class.equals(targetType);
    }

    private static String rawToString(Object raw, String fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Iterable<?> it) {
            for (Object o : it) return Objects.toString(o, fallback);
            return fallback;
        }
        if (raw.getClass().isArray()) {
            Object[] arr = (Object[]) raw;
            return arr.length > 0 ? Objects.toString(arr[0], fallback) : fallback;
        }
        return Objects.toString(raw, fallback);
    }

    private static Collection<Object> toCollection(Object raw) {
        List<Object> out = new ArrayList<>();
        if (raw == null) return out;
        if (raw instanceof Iterable<?> it) {
            for (Object o : it) out.add(o);
            return out;
        }
        if (raw.getClass().isArray()) {
            Object[] arr = (Object[]) raw;
            out.addAll(Arrays.asList(arr));
            return out;
        }
        out.add(raw);
        return out;
    }

    private static Object toArray(Object raw, Class<?> componentType) {
        List<Object> items = new ArrayList<>(toCollection(raw));
        Object array = Array.newInstance(componentType, items.size());
        for (int i = 0; i < items.size(); i++) {
            String str = rawToString(items.get(i), null);
            Object converted = convert(str, componentType);
            Array.set(array, i, converted);
        }
        return array;
    }

    private static String resolveName(Parameter parameter) {
        RequestParam rp = parameter.getAnnotation(RequestParam.class);
        if (rp != null && rp.value() != null && !rp.value().isBlank()) {
            return rp.value();
        }
        return parameter.getName();
    }

    private static Object convert(String rawValue, Class<?> targetType) {
        if (targetType == null) return null;
        if (String.class.equals(targetType)) return rawValue;

        if (targetType.isEnum()) {
            if (rawValue == null || rawValue.isBlank()) return null;
            try {
                @SuppressWarnings("unchecked")
                Class<? extends Enum> enumType = (Class<? extends Enum>) targetType;
                return Enum.valueOf(enumType, rawValue);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        if (Integer.class.equals(targetType) || int.class.equals(targetType)) {
            return parseInt(rawValue, int.class.equals(targetType));
        }
        if (Long.class.equals(targetType) || long.class.equals(targetType)) {
            return parseLong(rawValue, long.class.equals(targetType));
        }
        if (Double.class.equals(targetType) || double.class.equals(targetType)) {
            return parseDouble(rawValue, double.class.equals(targetType));
        }
        if (Boolean.class.equals(targetType) || boolean.class.equals(targetType)) {
            return parseBoolean(rawValue, boolean.class.equals(targetType));
        }
        if (Float.class.equals(targetType) || float.class.equals(targetType)) {
            return parseFloat(rawValue, float.class.equals(targetType));
        }
        if (Short.class.equals(targetType) || short.class.equals(targetType)) {
            return parseShort(rawValue, short.class.equals(targetType));
        }

        // Unsupported type: give the raw string so the handler can decide what to do.
        return rawValue;
    }

    private static Integer parseInt(String raw, boolean primitive) {
        if (raw == null || raw.isBlank()) return primitive ? 0 : null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return primitive ? 0 : null;
        }
    }

    private static Long parseLong(String raw, boolean primitive) {
        if (raw == null || raw.isBlank()) return primitive ? 0L : null;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return primitive ? 0L : null;
        }
    }

    private static Double parseDouble(String raw, boolean primitive) {
        if (raw == null || raw.isBlank()) return primitive ? 0d : null;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return primitive ? 0d : null;
        }
    }

    private static Float parseFloat(String raw, boolean primitive) {
        if (raw == null || raw.isBlank()) return primitive ? 0f : null;
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ex) {
            return primitive ? 0f : null;
        }
    }

    private static Short parseShort(String raw, boolean primitive) {
        if (raw == null || raw.isBlank()) return primitive ? (short) 0 : null;
        try {
            return Short.parseShort(raw);
        } catch (NumberFormatException ex) {
            return primitive ? (short) 0 : null;
        }
    }

    private static Boolean parseBoolean(String raw, boolean primitive) {
        if (raw == null || raw.isBlank()) return primitive ? false : null;
        return Boolean.parseBoolean(raw);
    }

    private static boolean wantsByteArrayMap(Parameter parameter) {
        if (parameter == null) return false;
        Type type = parameter.getParameterizedType();
        if (!(type instanceof ParameterizedType pt)) return false;
        Type[] args = pt.getActualTypeArguments();
        if (args.length != 2) return false;
        if (!(args[0] instanceof Class<?> key) || !(args[1] instanceof Class<?> val)) return false;
        boolean keyOk = String.class.equals(key);
        boolean valOk = Byte[].class.equals(val) || byte[].class.equals(val);
        return keyOk && valOk;
    }

    private static Byte[] toBoxed(byte[] bytes) {
        if (bytes == null) return null;
        Byte[] boxed = new Byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            boxed[i] = bytes[i];
        }
        return boxed;
    }
}
