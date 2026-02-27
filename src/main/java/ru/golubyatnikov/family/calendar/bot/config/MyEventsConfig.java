package ru.golubyatnikov.family.calendar.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация постраничного списка событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-26
 */
@Configuration
@ConfigurationProperties(prefix = "app.my-events")
@Getter
@Setter
public class MyEventsConfig {

    /**
     * Размер страницы по умолчанию
     */
    private int pageSize = 10;

    /**
     * Максимальная длина названия события в кнопке
     */
    private int maxTitleLength = 25;
}
