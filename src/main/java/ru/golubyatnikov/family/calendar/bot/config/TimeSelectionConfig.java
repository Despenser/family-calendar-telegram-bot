package ru.golubyatnikov.family.calendar.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация выбора времени в UI.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Configuration
@ConfigurationProperties(prefix = "app.time-selection")
@Getter
@Setter
public class TimeSelectionConfig {

    /**
     * Максимальный час для выбора времени
     */
    private int maxHour = 23;

    /**
     * Пороговая минута для отсечки
     */
    private int cutoffMinute = 46;

    /**
     * Количество часов в одном ряду клавиатуры
     */
    private int hoursPerRow = 4;
}
