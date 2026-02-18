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
     * Максимальный час для выбора времени (по умолчанию: 23)
     */
    private int maxHour = 23;

    /**
     * Пороговая минута для отсечки (по умолчанию: 46)
     */
    private int cutoffMinute = 46;

    /**
     * Количество часов в одном ряду клавиатуры (по умолчанию: 4)
     */
    private int hoursPerRow = 4;
}
