package edu.noah.monframework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as requiring an authenticated user.
 * The framework checks that a session exists and contains the configured
 * "user" attribute (name configurable via XML).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Authorized {
    /** Optional custom message when access is denied. */
    String message() default "Utilisateur non authentifié";
}
