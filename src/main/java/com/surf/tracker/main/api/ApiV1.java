package com.surf.tracker.main.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Explicit ownership of the v1 HTTP contract; resource mappings remain relative. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiV1 {
}
