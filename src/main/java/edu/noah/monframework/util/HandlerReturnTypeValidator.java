package edu.noah.monframework.util;

import edu.noah.monframework.annotation.ConvertJson;
import edu.noah.monframework.web.ModelView;

import java.lang.reflect.Method;

/**
 * Centralise la logique déterminant quels types de retour sont pris en charge
 * par les handlers @InvokeMe.
 */
public final class HandlerReturnTypeValidator {
    private HandlerReturnTypeValidator() {}

    public static boolean isSupported(Method method) {
        if (method == null) return false;
        Class<?> returnType = method.getReturnType();
        if (method.isAnnotationPresent(ConvertJson.class)) return true;
        if (returnType == null) return false;
        return String.class.equals(returnType) || ModelView.class.isAssignableFrom(returnType);
    }
}
