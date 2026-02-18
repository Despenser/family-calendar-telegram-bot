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
     * Формат даты (по умолчанию: dd.MM.yyyy)
     */
    private String datePattern = "dd.MM.yyyy";

    /**
     * Формат времени (по умолчанию: HH:mm)
     */
    private String timePattern = "HH:mm";

    /**
     * Формат даты и времени (по умолчанию: dd.MM.yyyy HH:mm)
     */
    private String dateTimePattern = "dd.MM.yyyy HH:mm";

    /**
     * Формат короткой даты с днем недели (по умолчанию: dd.MM (EEEE))
     */
    private String shortDatePattern = "dd.MM (EEEE)";

    /**
     * Формат месяца (по умолчанию: LLLL yyyy)
     */
    private String monthPattern = "LLLL yyyy";

    /**
     * Формат короткой даты без года (по умолчанию: dd.MM)
     */
    private String shortDateWithoutYearPattern = "dd.MM";

    /**
     * Формат дня недели (по умолчанию: EEEE)
     */
    private String dayOfWeekPattern = "EEEE";

    /**
     * Формат даты с днем недели (по умолчанию: dd.MM.yyyy - EEEE)
     */
    private String dateWithDayOfWeekPattern = "dd.MM.yyyy - EEEE";

    /**
     * Локаль для форматирования (по умолчанию: ru)
     */
    private String locale = "ru";

    /**
     * Часовой пояс по умолчанию (по умолчанию: Europe/Moscow)
     */
    private String defaultTimezone = "Europe/Moscow";
}
