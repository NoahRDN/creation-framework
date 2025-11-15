package edu.noah.monframework.util;

import edu.noah.monframework.web.ModelView;

/**
 * Centralise la logique déterminant quels types de retour sont pris en charge
 * par les handlers @InvokeMe.
 */
public final class HandlerReturnTypeValidator {
    private HandlerReturnTypeValidator() {}

    public static boolean isSupported(Class<?> returnType) {
        if (returnType == null) return false;
        return String.class.equals(returnType) || ModelView.class.isAssignableFrom(returnType);
    }
}
