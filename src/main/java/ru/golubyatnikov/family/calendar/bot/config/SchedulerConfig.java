package ru.golubyatnikov.family.calendar.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация планировщиков приложения.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Configuration
@ConfigurationProperties(prefix = "app.scheduler")
@Getter
@Setter
public class SchedulerConfig {

    /**
     * Cron выражение для очистки корзины
     */
    private String trashCleanupCron = "0 0 2 * * ?";

    /**
     * Интервал проверки напоминаний в миллисекундах
     */
    private long reminderCheckInterval = 60000;

    /**
     * Интервал завершения событий в миллисекундах
     */
    private long eventCompletionInterval = 600000;
}
