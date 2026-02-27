package ru.golubyatnikov.family.calendar.bot.service.infrastructure.authorization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.config.WebhookConfig;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Сервис для управления безопасностью webhook.
 * Использует secret token из конфигурации или генерирует новый.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-31
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookSecurityService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    private final WebhookConfig webhookConfig;

    /**
     * Текущий secret token
     */
    private volatile String secretToken;

    /**
     * Получает secret token из конфигурации или генерирует новый.
     *
     * @return secret token
     */
    public String generateSecretToken() {
        // Если токен задан в конфигурации, используем его
        if (webhookConfig.getSecretToken() != null && !webhookConfig.getSecretToken().isEmpty()) {
            this.secretToken = webhookConfig.getSecretToken();
            log.info("Используется secret token из конфигурации");
            return this.secretToken;
        }

        // Иначе генерируем новый токен
        byte[] randomBytes = new byte[webhookConfig.getSecretTokenLength()];
        SECURE_RANDOM.nextBytes(randomBytes);

        this.secretToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        log.warn("Secret token не задан в конфигурации, сгенерирован новый. Рекомендуется задать app.webhook.secret-token в переменных окружения");
        
        return this.secretToken;
    }

    /**
     * Валидирует secret token из входящего webhook запроса.
     *
     * @param token токен из заголовка X-Telegram-Bot-Api-Secret-Token
     * @return true, если токен валиден, false в противном случае
     */
    public boolean validateSecretToken(String token) {
        if (secretToken == null) {
            log.warn("Secret token не инициализирован");
            return false;
        }
        
        if (token == null || token.isEmpty()) {
            log.warn("Получен пустой secret token в webhook запросе");
            return false;
        }
        
        boolean isValid = secretToken.equals(token);
        if (!isValid) {
            log.warn("Получен невалидный secret token в webhook запросе");
        }
        
        return isValid;
    }
}
