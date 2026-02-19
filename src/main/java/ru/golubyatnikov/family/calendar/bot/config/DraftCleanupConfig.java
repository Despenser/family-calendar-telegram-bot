package ru.golubyatnikov.family.calendar.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация автоматической очистки черновиков событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Configuration
@ConfigurationProperties(prefix = "app.draft.cleanup")
@Getter
@Setter
public class DraftCleanupConfig {

    /**
     * Включить/выключить автоматическую очистку черновиков
     */
    private boolean enabled = true;

    /**
     * Пороговое значение для очистки при запуске приложения в часах
     */
    private int startupThresholdHours = 1;

    /**
     * Пороговое значение для периодической очистки в часах
     */
    private int periodicThresholdHours = 24;

    /**
     * Расписание периодической очистки в формате cron
     */
    private String scheduleCron = "0 0 */6 * * *";
}
