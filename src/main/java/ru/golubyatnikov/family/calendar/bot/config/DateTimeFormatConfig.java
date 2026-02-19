package ru.golubyatnikov.family.calendar.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация форматирования дат и времени.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Configuration
@ConfigurationProperties(prefix = "app.datetime-format")
@Getter
@Setter
public class DateTimeFormatConfig {

    /**
     * Формат даты
     */
    private String datePattern = "dd.MM.yyyy";

    /**
     * Формат времени
     */
    private String timePattern = "HH:mm";

    /**
     * Формат даты и времени
     */
    private String dateTimePattern = "dd.MM.yyyy HH:mm";

    /**
     * Формат короткой даты с днем недели
     */
    private String shortDatePattern = "dd.MM (EEEE)";

    /**
     * Формат месяца
     */
    private String monthPattern = "LLLL yyyy";

    /**
     * Формат короткой даты без года
     */
    private String shortDateWithoutYearPattern = "dd.MM";

    /**
     * Формат дня недели
     */
    private String dayOfWeekPattern = "EEEE";

    /**
     * Формат даты с днем недели
     */
    private String dateWithDayOfWeekPattern = "dd.MM.yyyy - EEEE";

    /**
     * Локаль для форматирования
     */
    private String locale = "ru";

    /**
     * Часовой пояс по умолчанию
     */
    private String defaultTimezone = "Europe/Moscow";
}
