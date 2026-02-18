package ru.golubyatnikov.family.calendar.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация HTTP заголовков.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Configuration
@ConfigurationProperties(prefix = "app.http-headers")
@Getter
@Setter
public class HttpHeadersConfig {

    /**
     * Имя заголовка для correlation ID (по умолчанию: X-Correlation-ID)
     */
    private String correlationIdHeader = "X-Correlation-ID";

}
