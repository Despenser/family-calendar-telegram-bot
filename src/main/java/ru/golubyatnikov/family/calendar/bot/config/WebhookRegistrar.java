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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.golubyatnikov.family.calendar.bot.service.authorization.WebhookSecurityService;
import java.util.HashMap;
import java.util.Map;

/**
 * Компонент для регистрации Webhook при старте приложения.
 * Компонент активируется только когда свойство telegram.bot.webhook.enabled=true (по умолчанию true)
 * Использует secret token для безопасной валидации webhook запросов.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "telegram.bot.webhook.enabled", havingValue = "true", matchIfMissing = true)
public class WebhookRegistrar {

    private final BotConfig botConfig;
    private final ApplicationContext applicationContext;
    private final RestTemplate restTemplate;
    private final WebhookSecurityService webhookSecurityService;

    /**
     * Регистрирует webhook в Telegram Bot API при старте приложения.
     * Использует secret token для безопасной валидации webhook запросов.
     */
    @PostConstruct
    public void registerWebhook() {
        log.info("Начинается регистрация webhook для бота: {}", botConfig.getUsername());
        log.debug("Webhook URL: {}", botConfig.getWebhookUrl());

        try {
            String apiUrl = String.format("https://api.telegram.org/bot%s/setWebhook", botConfig.getToken());
            String secretToken = webhookSecurityService.generateSecretToken();

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("url", botConfig.getWebhookUrl());
            requestBody.put("secret_token", secretToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            var response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            // Проверяем результат
            var responseBody = response.getBody();
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
            shutdownApplication("Ошибка регистрации webhook: " + e.getMessage());
        }
    }

    /**
     * Останавливает приложение с указанной причиной.
     * Выполняет graceful shutdown через Spring Application Context.
     * 
     * @param reason причина остановки приложения
     */
    private void shutdownApplication(String reason) {
        log.error("═══════════════════════════════════════════════════════════");
        log.error("КРИТИЧЕСКАЯ ОШИБКА: Приложение будет остановлено");
        log.error("Причина: {}", reason);
        log.error("═══════════════════════════════════════════════════════════");
        
        // Выполняем graceful shutdown в отдельном потоке,
        // чтобы позволить текущему методу завершиться корректно
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                SpringApplication.exit(applicationContext, () -> 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Прерван процесс graceful shutdown", e);
            }
        }, "webhook-shutdown-thread").start();
    }
}
