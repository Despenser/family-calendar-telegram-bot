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
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.authorization.WebhookSecurityService;
import java.util.HashMap;
import java.util.Map;

/**
 * Компонент для регистрации Webhook при старте приложения.
 * Компонент активируется, только когда свойство telegram.bot.webhook.enabled=true (по умолчанию true)
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
    private final TelegramApiConfig telegramApiConfig;

    /**
     * Регистрирует webhook в Telegram Bot API при старте приложения.
     * Использует secret token для безопасной валидации webhook запросов.
     */
    @PostConstruct
    public void registerWebhook() {
        log.info("Регистрация webhook: {}", botConfig.getWebhookUrl());
        
        try {
            String apiUrl = telegramApiConfig.getBaseUrl() + 
                String.format(telegramApiConfig.getSetWebhookPath(), botConfig.getToken());
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

            var responseBody = response.getBody();
            if (responseBody != null && Boolean.TRUE.equals(responseBody.get("ok"))) {
                log.info("Webhook зарегистрирован");

            } else {
                String errorDescription = responseBody != null
                        ? String.valueOf(responseBody.get("description"))
                        : "Неизвестная ошибка";

                log.error("Ошибка регистрации webhook: {}", errorDescription);
                shutdownApplication("Регистрация webhook не удалась: " + errorDescription);
            }

        } catch (Exception e) {
            log.error("Ошибка регистрации webhook: {}", e.getMessage(), e);
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
        log.error("Критическая ошибка - приложение остановлено: {}", reason);
        
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                SpringApplication.exit(applicationContext, () -> 1);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "webhook-shutdown-thread").start();
    }
}
