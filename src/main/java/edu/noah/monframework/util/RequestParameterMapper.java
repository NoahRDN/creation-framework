package edu.noah.monframework.util;

import edu.noah.monframework.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public static Object[] map(Method method,
                               HttpServletRequest req,
                               HttpServletResponse resp,
                               String requestPath,
                               String annotatedPath) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        Map<String, String> pathVars = PathVariableExtractor.extract(requestPath, annotatedPath);
        Map<String, Object> aggregated = buildParameterMap(req, pathVars);
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
                // Provide a fresh copy so handlers can mutate safely.
                args[i] = new LinkedHashMap<>(aggregated);
                continue;
            }

            String name = resolveName(parameter);
            Object raw = aggregated.get(name);

            if (isSimpleType(type)) {
                String rawValue = rawToString(raw, pathVars.get(name));
                args[i] = convert(rawValue, type);
            } else {
                args[i] = bindObject(type, name, aggregated);
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
}
