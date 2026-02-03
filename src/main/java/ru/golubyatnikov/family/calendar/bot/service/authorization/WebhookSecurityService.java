package ru.golubyatnikov.family.calendar.bot.service.authorization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
public class WebhookSecurityService {

    private static final int SECRET_TOKEN_LENGTH = 64;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    private volatile String secretToken;

    /**
     * Генерирует новый secret token для webhook.
     * Использует криптографически стойкий генератор случайных чисел.
     *
     * @return сгенерированный secret token
     */
    public String generateSecretToken() {
        byte[] randomBytes = new byte[SECRET_TOKEN_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        this.secretToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        
        log.debug("Сгенерирован новый secret token для webhook");
        return this.secretToken;
    }

    /**
     * Сохраняет secret token для последующей валидации.
     *
     * @param token secret token для сохранения
     */
    public void storeSecretToken(String token) {
        this.secretToken = token;
        log.debug("Secret token сохранен для валидации webhook запросов");
    }

    /**
     * Валидирует secret token из входящего webhook запроса.
     *
     * @param token токен из заголовка X-Telegram-Bot-Api-Secret-Token
     * @return true если токен валиден, false в противном случае
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

    /**
     * Возвращает текущий secret token.
     *
     * @return текущий secret token или null если не инициализирован
     */
    public String getSecretToken() {
        return secretToken;
    }
}
