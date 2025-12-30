package ru.golubyatnikov.family.calendar.bot.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Компонент для регистрации Webhook при старте приложения.
 * 
 * <p>Этот компонент автоматически регистрирует webhook URL в Telegram Bot API
 * при инициализации Spring контекста. Если регистрация не удалась, приложение
 * останавливается с соответствующим сообщением об ошибке.
 * 
 * <p>Webhook позволяет получать обновления от Telegram в реальном времени
 * через HTTP POST запросы вместо постоянного опроса API (long polling).
 * 
 * <p>Требования к webhook URL:
 * <ul>
 *   <li>Должен использовать HTTPS протокол</li>
 *   <li>Должен быть доступен из интернета</li>
 *   <li>Должен использовать один из поддерживаемых портов: 443, 80, 88, 8443</li>
 * </ul>
 * 
 * <p>Компонент активируется только когда свойство telegram.bot.webhook.enabled=true
 * или не установлено (по умолчанию true). Это позволяет отключить регистрацию
 * webhook в тестовом окружении.
 * 
 * @see BotConfig
 * @see PostConstruct
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "telegram.bot.webhook.enabled", havingValue = "true", matchIfMissing = true)
public class WebhookRegistrar {

    private final BotConfig botConfig;
    private final ApplicationContext applicationContext;
    private final RestTemplate restTemplate;

    /**
     * Регистрирует webhook в Telegram Bot API при старте приложения.
     * 
     * <p>Метод вызывается автоматически после инициализации всех зависимостей
     * благодаря аннотации {@link PostConstruct}.
     * 
     * <p>В случае успешной регистрации логируется информационное сообщение.
     * При ошибке регистрации логируется детальная информация об ошибке
     * и приложение останавливается с кодом выхода 1.
     */
    @PostConstruct
    public void registerWebhook() {
        log.info("Начинается регистрация webhook для бота: {}", botConfig.getUsername());
        log.debug("Webhook URL: {}", botConfig.getWebhookUrl());

        try {
            // Формируем URL для Telegram Bot API
            String apiUrl = String.format("https://api.telegram.org/bot%s/setWebhook", botConfig.getToken());

            // Подготавливаем тело запроса
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("url", botConfig.getWebhookUrl());

            // Настраиваем заголовки
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Создаем HTTP запрос
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            // Выполняем запрос к Telegram API
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            // Проверяем результат
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && Boolean.TRUE.equals(responseBody.get("ok"))) {
                log.info("✓ Webhook успешно зарегистрирован для бота: {}", botConfig.getUsername());
                log.info("✓ URL: {}", botConfig.getWebhookUrl());
                log.debug("✓ Ответ от Telegram API: {}", responseBody);
            } else {
                String errorDescription = responseBody != null ? 
                        String.valueOf(responseBody.get("description")) : "Неизвестная ошибка";
                log.error("✗ Не удалось зарегистрировать webhook. Ответ: {}", responseBody);
                shutdownApplication("Регистрация webhook не удалась: " + errorDescription);
            }

        } catch (Exception e) {
            log.error("✗ Ошибка при регистрации webhook для бота: {}", botConfig.getUsername(), e);
            log.error("✗ Детали ошибки: {}", e.getMessage());
            log.error("✗ Проверьте:");
            log.error("  - Корректность токена бота (TELEGRAM_BOT_TOKEN)");
            log.error("  - Доступность webhook URL (TELEGRAM_BOT_WEBHOOK_URL)");
            log.error("  - Использование HTTPS протокола");
            log.error("  - Использование поддерживаемого порта (443, 80, 88, 8443)");
            log.error("  - Доступность Telegram API (https://api.telegram.org)");
            
            shutdownApplication("Ошибка регистрации webhook: " + e.getMessage());
        }
    }

    /**
     * Останавливает приложение с указанной причиной.
     * 
     * <p>Метод логирует критическую ошибку и инициирует graceful shutdown
     * Spring приложения с кодом выхода 1, что указывает на ошибку.
     * 
     * @param reason причина остановки приложения
     */
    private void shutdownApplication(String reason) {
        log.error("═══════════════════════════════════════════════════════════");
        log.error("КРИТИЧЕСКАЯ ОШИБКА: Приложение будет остановлено");
        log.error("Причина: {}", reason);
        log.error("═══════════════════════════════════════════════════════════");
        
        // Останавливаем приложение с кодом ошибки
        int exitCode = SpringApplication.exit(applicationContext, () -> 1);
        System.exit(exitCode);
    }
}
