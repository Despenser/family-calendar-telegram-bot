package ru.golubyatnikov.family.calendar.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация webhook безопасности.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Configuration
@ConfigurationProperties(prefix = "app.webhook")
@Getter
@Setter
public class WebhookConfig {

    /**
     * Длина секретного токена для webhook
     */
    private int secretTokenLength = 64;
}
