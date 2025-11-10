package com.apidoc.apidocumentation.doc;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiDocClass {
    String description() default "";
    String[] tags() default {};
    boolean logRequest() default true;
    boolean logResponse() default true;
}