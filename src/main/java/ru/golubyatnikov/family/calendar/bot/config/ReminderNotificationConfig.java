package ru.golubyatnikov.family.calendar.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация уведомлений о напоминаниях.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Configuration
@ConfigurationProperties(prefix = "app.reminder-notification")
@Getter
@Setter
public class ReminderNotificationConfig {

    /**
     * Окно проверки напоминаний в часах
     */
    private int windowHours = 1;

    /**
     * Порог для старых напоминаний в часах
     */
    private int oldThresholdHours = 1;
}
