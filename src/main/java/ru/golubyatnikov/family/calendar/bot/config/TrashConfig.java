package ru.golubyatnikov.family.calendar.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация корзины событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Configuration
@ConfigurationProperties(prefix = "app.trash")
@Getter
@Setter
public class TrashConfig {

    /**
     * Количество дней хранения событий в корзине (по умолчанию: 30)
     */
    private int retentionDays = 30;
}
