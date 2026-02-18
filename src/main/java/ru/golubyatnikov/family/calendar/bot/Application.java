package ru.golubyatnikov.family.calendar.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Главный класс приложения Family Calendar Bot.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@EnableAsync
@EnableRetry
@EnableScheduling
@EnableCaching
@SpringBootApplication
public class Application {

    /**
     * Точка входа в приложение.
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
