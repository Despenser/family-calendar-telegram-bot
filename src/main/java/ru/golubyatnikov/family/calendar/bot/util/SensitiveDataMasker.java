package ru.golubyatnikov.family.calendar.bot.util;

/**
 * Утилита для маскирования чувствительных данных в логах.
 * 
 * <p>Предоставляет методы для безопасного логирования токенов, паролей,
 * API ключей и других конфиденциальных данных.</p>
 * 
 * <p><b>Требования:</b> 6.6</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
public final class SensitiveDataMasker {
    
    private static final String MASK = "***";
    private static final int DEFAULT_VISIBLE_PREFIX_LENGTH = 10;
    private static final int MIN_LENGTH_FOR_PARTIAL_MASK = 4;
    
    private SensitiveDataMasker() {
        // Утилитный класс, не должен инстанцироваться
    }
    
    /**
     * Маскирует токен бота для безопасного логирования.
     * 
     * <p>Показывает только первые 10 символов токена для идентификации,
     * остальное заменяет на звездочки.</p>
     * 
     * @param token токен бота
     * @return замаскированный токен
     */
    public static String maskToken(String token) {
        return maskWithPrefix(token, DEFAULT_VISIBLE_PREFIX_LENGTH);
    }
    
    /**
     * Маскирует пароль для безопасного логирования.
     * 
     * <p>Полностью скрывает пароль, заменяя его на звездочки.</p>
     * 
     * @param password пароль
     * @return замаскированный пароль
     */
    public static String maskPassword(String password) {
        if (password == null || password.isEmpty()) {
            return MASK;
        }
        return MASK;
    }
    
    /**
     * Маскирует API ключ для безопасного логирования.
     * 
     * <p>Показывает только первые 4 символа ключа для идентификации,
     * остальное заменяет на звездочки.</p>
     * 
     * @param apiKey API ключ
     * @return замаскированный API ключ
     */
    public static String maskApiKey(String apiKey) {
        return maskWithPrefix(apiKey, MIN_LENGTH_FOR_PARTIAL_MASK);
    }
    
    /**
     * Маскирует email адрес для безопасного логирования.
     * 
     * <p>Показывает первые 2 символа до @ и домен.</p>
     * 
     * @param email email адрес
     * @return замаскированный email
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return MASK;
        }
        
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return MASK;
        }
        
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        
        if (localPart.length() <= 2) {
            return localPart + MASK + domain;
        }
        
        return localPart.substring(0, 2) + MASK + domain;
    }
    
    /**
     * Маскирует телефонный номер для безопасного логирования.
     * 
     * <p>Показывает только последние 4 цифры номера.</p>
     * 
     * @param phoneNumber телефонный номер
     * @return замаскированный номер
     */
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return MASK;
        }
        
        // Удаляем все нецифровые символы для подсчета
        String digitsOnly = phoneNumber.replaceAll("\\D", "");
        
        if (digitsOnly.length() <= 4) {
            return MASK;
        }
        
        return MASK + digitsOnly.substring(digitsOnly.length() - 4);
    }
    
    /**
     * Маскирует строку, показывая только указанное количество символов в начале.
     * 
     * @param value строка для маскирования
     * @param visiblePrefixLength количество видимых символов в начале
     * @return замаскированная строка
     */
    public static String maskWithPrefix(String value, int visiblePrefixLength) {
        if (value == null || value.isEmpty()) {
            return MASK;
        }
        
        if (value.length() <= visiblePrefixLength) {
            return MASK;
        }
        
        return value.substring(0, visiblePrefixLength) + MASK;
    }
    
    /**
     * Маскирует строку, показывая только указанное количество символов в конце.
     * 
     * @param value строка для маскирования
     * @param visibleSuffixLength количество видимых символов в конце
     * @return замаскированная строка
     */
    public static String maskWithSuffix(String value, int visibleSuffixLength) {
        if (value == null || value.isEmpty()) {
            return MASK;
        }
        
        if (value.length() <= visibleSuffixLength) {
            return MASK;
        }
        
        return MASK + value.substring(value.length() - visibleSuffixLength);
    }
    
    /**
     * Маскирует Telegram ID для безопасного логирования.
     * 
     * <p>Показывает только последние 4 цифры ID.</p>
     * 
     * @param telegramId Telegram ID
     * @return замаскированный ID
     */
    public static String maskTelegramId(Long telegramId) {
        if (telegramId == null) {
            return MASK;
        }
        
        String idStr = telegramId.toString();
        if (idStr.length() <= 4) {
            return MASK;
        }
        
        return MASK + idStr.substring(idStr.length() - 4);
    }
    
    /**
     * Маскирует callback data для безопасного логирования.
     * 
     * <p>Показывает только префикс callback data (до первого разделителя).</p>
     * 
     * @param callbackData callback data
     * @return замаскированный callback data
     */
    public static String maskCallbackData(String callbackData) {
        if (callbackData == null || callbackData.isEmpty()) {
            return MASK;
        }
        
        // Находим первый разделитель (_, :, или пробел)
        int separatorIndex = -1;
        for (int i = 0; i < callbackData.length(); i++) {
            char c = callbackData.charAt(i);
            if (c == '_' || c == ':' || c == ' ') {
                separatorIndex = i;
                break;
            }
        }
        
        if (separatorIndex <= 0) {
            // Нет разделителя - показываем первые 10 символов
            return maskWithPrefix(callbackData, DEFAULT_VISIBLE_PREFIX_LENGTH);
        }
        
        // Показываем префикс + маску
        return callbackData.substring(0, separatorIndex + 1) + MASK;
    }
}
