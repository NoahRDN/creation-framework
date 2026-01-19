package edu.noah.monframework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a controller method to users having at least one of the provided roles.
 * This check is performed after authentication (ideally paired with {@link Authorized}).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Role {
    /** Allowed role names. */
    String[] value();

    /** Optional custom message when role check fails. */
    String message() default "Accès refusé : rôle insuffisant";
}
