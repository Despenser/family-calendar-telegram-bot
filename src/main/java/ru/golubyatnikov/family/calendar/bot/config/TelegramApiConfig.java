package ru.golubyatnikov.family.calendar.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация Telegram API.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Configuration
@ConfigurationProperties(prefix = "app.telegram-api")
@Getter
@Setter
public class TelegramApiConfig {

    /**
     * Базовый URL Telegram Bot API (по умолчанию: https://api.telegram.org)
     */
    private String baseUrl = "https://api.telegram.org";

    /**
     * Путь для установки webhook (по умолчанию: /bot{token}/setWebhook)
     */
    private String setWebhookPath = "/bot%s/setWebhook";

    /**
     * Имя заголовка для secret token (по умолчанию: X-Telegram-Bot-Api-Secret-Token)
     */
    private String secretTokenHeader = "X-Telegram-Bot-Api-Secret-Token";
}
