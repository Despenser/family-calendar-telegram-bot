package ru.golubyatnikov.family.calendar.bot.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestTemplate;

/**
 * Конфигурация Telegram бота.
 * Загружает параметры бота из application.yml с префиксом "telegram.bot".
 * Все обязательные поля валидируются при старте приложения.
 *
 * <p>Требуемые параметры:
 * <ul>
 *   <li>token - токен бота, полученный от @BotFather</li>
 *   <li>username - имя пользователя бота</li>
 *   <li>webhookUrl - URL для регистрации webhook</li>
 * </ul>
 *
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 * @see jakarta.validation.constraints.NotBlank
 */
@Configuration
@ConfigurationProperties(prefix = "telegram.bot")
@Validated
@Data
public class BotConfig {

    /**
     * Токен бота, полученный от @BotFather в Telegram.
     * Используется для аутентификации всех запросов к Telegram Bot API.
     * Не должен быть пустым или содержать только пробелы.
     */
    @NotBlank(message = "Токен бота не может быть пустым. Укажите TELEGRAM_BOT_TOKEN в переменных окружения.")
    private String token;

    /**
     * Имя пользователя бота (username) в Telegram.
     * Используется для идентификации бота и отображения в интерфейсе.
     * Не должен быть пустым или содержать только пробелы.
     */
    @NotBlank(message = "Имя пользователя бота не может быть пустым. Укажите TELEGRAM_BOT_USERNAME в переменных окружения.")
    private String username;

    /**
     * URL для регистрации webhook.
     * Telegram будет отправлять обновления на этот адрес.
     * Должен быть доступен из интернета и использовать HTTPS.
     * Не должен быть пустым или содержать только пробелы.
     */
    @NotBlank(message = "URL webhook не может быть пустым. Укажите TELEGRAM_BOT_WEBHOOK_URL в переменных окружения.")
    private String webhookUrl;

    /**
     * Создает и настраивает RestTemplate для HTTP запросов к Telegram Bot API.
     * 
     * <p>RestTemplate используется для выполнения HTTP запросов к Telegram API,
     * включая регистрацию webhook и другие операции.
     * 
     * <p>Настройки:
     * <ul>
     *   <li>Connection timeout: 10 секунд</li>
     *   <li>Read timeout: 30 секунд</li>
     * </ul>
     * 
     * @param builder билдер для создания RestTemplate с настройками по умолчанию
     * @return настроенный экземпляр RestTemplate
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
