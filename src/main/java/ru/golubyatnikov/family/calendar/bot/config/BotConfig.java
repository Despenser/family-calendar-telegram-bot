package ru.golubyatnikov.family.calendar.bot.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestTemplate;

/**
 * Конфигурация Telegram бота.
 * Загружает параметры бота из application.yml с префиксом "telegram.bot".
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Configuration
@ConfigurationProperties(prefix = "telegram.bot")
@Validated
@Data
public class BotConfig {

    /**
     * Токен бота, полученный от @BotFather в Telegram.
     * Используется для аутентификации всех запросов к Telegram Bot API.
     */
    @NotBlank(message = "Токен бота не может быть пустым. Укажите TELEGRAM_BOT_TOKEN в переменных окружения.")
    private String token;

    /**
     * Имя пользователя бота (username) в Telegram.
     * Используется для идентификации бота и отображения в интерфейсе.
     */
    @NotBlank(message = "Имя пользователя бота не может быть пустым. Укажите TELEGRAM_BOT_USERNAME в переменных окружения.")
    private String username;

    /**
     * URL для регистрации webhook.
     * Telegram будет отправлять обновления на этот адрес.
     * Должен быть доступен из интернета и использовать HTTPS.
     */
    @NotBlank(message = "URL webhook не может быть пустым. Укажите TELEGRAM_BOT_WEBHOOK_URL в переменных окружения.")
    private String webhookUrl;

    /**
     * Создает и настраивает RestTemplate для HTTP запросов к Telegram Bot API.
     *
     * @param builder билдер для создания RestTemplate с настройками по умолчанию
     * @return настроенный экземпляр RestTemplate
     */
    @Bean
    public RestTemplate restTemplate(@NonNull RestTemplateBuilder builder) {
        return builder.build();
    }
}
