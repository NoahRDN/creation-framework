package edu.noah.monframework.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Marks a class as a form-bindable root object for a given prefix.
 * Example: @FormObject("user") on UserProfile binds fields from user.nom, user.email, etc.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FormObject {
    String value();
}
