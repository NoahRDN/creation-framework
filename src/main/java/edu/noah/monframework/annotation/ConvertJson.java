package edu.noah.monframework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the return value of a handler should be rendered as JSON.
 * The response status can be customised via {@link #status()} and additional
 * metadata can be included using {@link #message()}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ConvertJson {
    /**
     * HTTP status code to send with the JSON response.
     */
    int status() default 200;

    /**
     * Optional message added to the "meta" section of the JSON response.
     */
    String message() default "";

    /**
     * Whether to include a "meta" object wrapping status/message/path/timestamp.
     */
    boolean includeMeta() default true;
}
