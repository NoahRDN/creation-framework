package edu.noah.monframework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a method parameter to a request parameter with the given name.
 * Example: void handler(@RequestParam("nbr") int nombre)
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestParam {
    /** Name of the request parameter to bind. If empty, falls back to the parameter name. */
    String value() default "";
}
