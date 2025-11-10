package com.apidoc.apidocumentation.doc;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiParam {
    String name() default "";
    String description() default "";
    boolean required() default false;
    String example() default "";
}