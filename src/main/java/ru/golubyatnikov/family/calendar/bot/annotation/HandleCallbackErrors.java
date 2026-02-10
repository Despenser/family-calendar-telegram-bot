package ru.golubyatnikov.family.calendar.bot.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация для маркировки методов, в которых нужна централизованная обработка ошибок.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HandleCallbackErrors {}
