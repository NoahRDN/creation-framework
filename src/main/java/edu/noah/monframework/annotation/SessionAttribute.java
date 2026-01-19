package edu.noah.monframework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller parameter as using the custom session system.
 * <ul>
 *     <li>On a {@code SessionContext} parameter, it triggers creation/retrieval of the session.</li>
 *     <li>On other parameters, it injects the session attribute named by {@code value()}
 *     (or the parameter name when omitted).</li>
 * </ul>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SessionAttribute {
    /** Logical key of the attribute to read from the session (defaults to parameter name). */
    String value() default "";

    /** Whether to create a session when none exists. */
    boolean create() default true;
}
