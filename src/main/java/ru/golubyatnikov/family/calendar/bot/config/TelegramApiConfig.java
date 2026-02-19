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
     * Базовый URL Telegram Bot API
     */
    private String baseUrl = "https://api.telegram.org";

    /**
     * Путь для установки webhook
     */
    private String setWebhookPath = "/bot%s/setWebhook";

    /**
     * Имя заголовка для secret token
     */
    private String secretTokenHeader = "X-Telegram-Bot-Api-Secret-Token";
}
