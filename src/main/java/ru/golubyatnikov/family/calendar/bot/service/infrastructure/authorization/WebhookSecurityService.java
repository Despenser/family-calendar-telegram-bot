package ru.golubyatnikov.family.calendar.bot.service.infrastructure.authorization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.config.WebhookConfig;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Сервис для управления безопасностью webhook.
 * Генерирует и хранит secret token для валидации webhook запросов от Telegram.
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
     * Текущий secret token или null если не инициализирован
     */
    private volatile String secretToken;

    /**
     * Генерирует новый secret token для webhook.
     *
     * @return сгенерированный secret token
     */
    public String generateSecretToken() {
        byte[] randomBytes = new byte[webhookConfig.getSecretTokenLength()];
        SECURE_RANDOM.nextBytes(randomBytes);

        this.secretToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        
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
