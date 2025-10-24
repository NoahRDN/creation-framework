package edu.noah.monframework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be discovered and executed reflectively by {@code AnnotatedMethodExecutor}.
 * Methods annotated with @InvokeMe must be no-arg (zero parameters). Static and instance
 * methods are supported. An optional {@code order} value controls execution order (ascending).
 *
 * The annotation also accepts a {@code path} (e.g. "/hello") which can be used to lookup and
 * execute the method by URL or logical route via the executor.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InvokeMe {
    /** Logical path or URL used to invoke this method via the executor. */
    String path() default "";

    /** Order in which annotated methods are executed. Lower values run first. */
    int order() default 0;
}
