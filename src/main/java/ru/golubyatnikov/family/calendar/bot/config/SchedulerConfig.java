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
     * Cron выражение для очистки корзины (по умолчанию: каждый день в 2:00)
     */
    private String trashCleanupCron = "0 0 2 * * ?";

    /**
     * Интервал проверки напоминаний в миллисекундах (по умолчанию: 60000 = 1 минута)
     */
    private long reminderCheckInterval = 60000;

    /**
     * Интервал завершения событий в миллисекундах (по умолчанию: 600000 = 10 минут)
     */
    private long eventCompletionInterval = 600000;
}
