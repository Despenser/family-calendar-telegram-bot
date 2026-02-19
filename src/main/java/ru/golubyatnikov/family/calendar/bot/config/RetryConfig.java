package ru.golubyatnikov.family.calendar.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация параметров повторных попыток для Telegram API.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Configuration
@ConfigurationProperties(prefix = "app.retry")
@Getter
@Setter
public class RetryConfig {

    /**
     * Максимальное количество попыток
     */
    private int maxAttempts = 3;

    /**
     * Начальная задержка между попытками в миллисекундах
     */
    private long initialDelay = 1000;

    /**
     * Множитель для увеличения задержки
     */
    private double multiplier = 2.0;
}
