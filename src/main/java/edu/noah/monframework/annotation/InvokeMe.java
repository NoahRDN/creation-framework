package edu.noah.monframework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be discovered and executed reflectively by {@code AnnotatedMethodExecutor}.
 * Static and instance methods are supported. Methods can be associated with a logical
 * {@code path} used by the runtime to invoke them based on incoming URLs. Parameters are
 * mapped from the {@link jakarta.servlet.http.HttpServletRequest} using the parameter name
 * (e.g. a form field named "email" will bind to a method parameter named "email"), or a
 * specific name provided via {@link RequestParam} on each parameter.
 *
 * The annotation also accepts a {@code path} (e.g. "/hello") which can be used to lookup and
 * execute the method by URL or logical route via the executor.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InvokeMe {
    /** Logical path or URL used to invoke this method via the executor. */
    String path() default "";
}
