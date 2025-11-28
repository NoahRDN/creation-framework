package edu.noah.monframework.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Utility responsible for building the argument list passed to @InvokeMe methods
 * from the incoming {@link HttpServletRequest}. Parameters are matched by name:
 * - {@link HttpServletRequest} and {@link HttpServletResponse} are injected directly.
 * - Simple types (String, primitive wrappers, primitives, enums) are populated from
 *   request parameters with the same name. When conversion fails, primitives fall back
 *   to their default value while wrapper types become {@code null}.
 */
public final class RequestParameterMapper {
    private RequestParameterMapper() {}

    public static Object[] map(Method method, HttpServletRequest req, HttpServletResponse resp) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
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

            String name = parameter.getName();
            String rawValue = (req != null) ? req.getParameter(name) : null;
            args[i] = convert(rawValue, type);
        }
        return args;
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
