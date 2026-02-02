package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.util.function.Supplier;

/**
 * Сервис для управления retry логикой при отправке сообщений.
 * 
 * <p>MessageRetryService реализует механизм повторных попыток с exponential backoff
 * для обработки временных сбоев при отправке сообщений через Telegram API. Основные функции:</p>
 * <ul>
 *   <li>Выполнение операций с автоматическими повторными попытками</li>
 *   <li>Exponential backoff между попытками</li>
 *   <li>Обработка ошибок парсинга (прекращение попыток)</li>
 *   <li>Детальное логирование каждой попытки</li>
 * </ul>
 * 
 * <p><b>Retry стратегия:</b></p>
 * <ul>
 *   <li>Попытка 1: немедленно</li>
 *   <li>Попытка 2: через 1 секунду</li>
 *   <li>Попытка 3: через 2 секунды</li>
 *   <li>При ошибках парсинга - прекращение попыток</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.1, 12.1</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MessageRetryService {

    private final MessageFormatter messageFormatter;
    private final AuthorizationMetricsService metricsService;

    /**
     * Выполняет операцию отправки с retry механизмом.
     * 
     * @param operation операция для выполнения
     * @param telegramId ID пользователя для логирования
     * @param text текст сообщения для логирования
     * @param maxAttempts максимальное количество попыток
     * @throws TelegramApiException если все попытки не удались
     */
    public void executeWithRetry(Supplier<Void> operation, Long telegramId, String text, int maxAttempts) 
            throws TelegramApiException {
        executeWithRetryGeneric(operation, telegramId, text, maxAttempts);
    }

    /**
     * Выполняет операцию с retry механизмом и возвращает результат.
     * 
     * @param <T> тип возвращаемого значения
     * @param operation операция для выполнения
     * @param telegramId ID пользователя для логирования
     * @param text текст сообщения для логирования
     * @param maxAttempts максимальное количество попыток
     * @return результат выполнения операции
     * @throws TelegramApiException если все попытки не удались
     */
    public <T> T executeWithRetryGeneric(Supplier<T> operation, Long telegramId, String text, int maxAttempts) 
            throws TelegramApiException {
        int attempt = 0;
        TelegramApiException lastException = null;
        
        while (attempt < maxAttempts) {
            attempt++;
            
            try {
                T result = operation.get();
                return result; // Успешно выполнено
                
            } catch (RuntimeException e) {
                // Проверяем, является ли причина TelegramApiException
                Throwable cause = e.getCause();
                if (cause instanceof TelegramApiRequestException telegramException) {
                    lastException = telegramException;
                    recordMetricForTelegramError(telegramException);
                    
                    // Если это ошибка парсинга, прекращаем попытки
                    if (messageFormatter.isParseError(telegramException)) {
                        log.error("Критическая ошибка парсинга MarkdownV2, прекращаем попытки: " +
                                 "telegramId={}, attempts={}, error={}", 
                                 telegramId, attempt, telegramException.getMessage());
                        throw telegramException;
                    }
                    
                    log.error("Ошибка при выполнении операции: telegramId={}, attempt={}/{}, error={}", 
                            telegramId, attempt, maxAttempts, telegramException.getMessage());
                    
                    // Если это не последняя попытка, делаем задержку
                    if (attempt < maxAttempts) {
                        sleep(calculateDelay(attempt));
                    }
                    
                } else if (cause instanceof TelegramApiException telegramException) {
                    lastException = telegramException;
                    recordMetric("network_error");
                    
                    log.error("Сетевая ошибка при выполнении операции: telegramId={}, attempt={}/{}, error={}", 
                            telegramId, attempt, maxAttempts, telegramException.getMessage());
                    
                    // Если это не последняя попытка, делаем задержку
                    if (attempt < maxAttempts) {
                        sleep(calculateDelay(attempt));
                    } else {
                        throw telegramException;
                    }
                } else {
                    // Не TelegramApiException - пробрасываем дальше
                    throw e;
                }
            }
        }
        
        // Все попытки исчерпаны
        log.error("Все попытки выполнения операции исчерпаны: " +
                 "telegramId={}, textLength={}, attempts={}, error={}", 
                 telegramId, text != null ? text.length() : 0, attempt, 
                 lastException != null ? lastException.getMessage() : "unknown");
        
        if (lastException != null) {
            throw lastException;
        }
        
        return null; // Не должно достигаться, но компилятор требует
    }

    /**
     * Обрабатывает ошибки Telegram API с детальным логированием.
     * 
     * @param e исключение от Telegram API
     * @param telegramId Telegram ID пользователя
     * @param text текст сообщения для логирования
     */
    public void handleTelegramApiError(TelegramApiRequestException e, Long telegramId, String text) {
        Integer errorCode = e.getErrorCode();
        String apiResponse = e.getApiResponse();
        
        if (errorCode == null) {
            log.error("Ошибка Telegram API без кода: telegramId={}, response={}", 
                    telegramId, apiResponse);
            return;
        }
        
        switch (errorCode) {
            case 400:
                String textPreview = text != null 
                    ? text.substring(0, Math.min(200, text.length())) 
                    : "null";
                
                log.error("Bad Request (400): Ошибка парсинга MarkdownV2. " +
                         "telegramId={}, textPreview='{}', fullTextLength={}, response={}", 
                         telegramId, textPreview, text != null ? text.length() : 0, apiResponse);
                
                if (text != null) {
                    log.debug("Полный текст сообщения при ошибке 400: telegramId={}, fullText='{}'", 
                            telegramId, text);
                }
                break;
                
            case 401:
                log.error("Unauthorized (401): Неверный токен бота! Проверьте TELEGRAM_BOT_TOKEN. " +
                         "response={}", apiResponse);
                break;
                
            case 403:
                log.warn("Forbidden (403): Бот заблокирован пользователем или нет доступа. " +
                        "telegramId={}, response={}", telegramId, apiResponse);
                break;
                
            case 404:
                log.warn("Not Found (404): Чат не найден. telegramId={}, response={}", 
                        telegramId, apiResponse);
                break;
                
            case 429:
                log.warn("Too Many Requests (429): Превышен лимит запросов. " +
                        "Требуется увеличить задержку. telegramId={}, response={}", 
                        telegramId, apiResponse);
                break;
                
            default:
                if (errorCode >= 500) {
                    log.error("Server Error ({}): Ошибка сервера Telegram. " +
                            "telegramId={}, response={}", errorCode, telegramId, apiResponse);
                } else {
                    log.error("Telegram API Error ({}): telegramId={}, response={}", 
                            errorCode, telegramId, apiResponse);
                }
        }
    }

    /**
     * Обрабатывает ошибки при ответе на callback query.
     * 
     * @param e исключение от Telegram API
     * @param callbackQueryId ID callback query
     */
    public void handleCallbackQueryError(TelegramApiRequestException e, String callbackQueryId) {
        Integer errorCode = e.getErrorCode();
        String apiResponse = e.getApiResponse();
        
        if (errorCode == null) {
            log.error("Ошибка Telegram API при ответе на callback query без кода: " +
                     "callbackQueryId={}, response={}", callbackQueryId, apiResponse);
            return;
        }
        
        switch (errorCode) {
            case 400:
                log.error("Bad Request (400): Ошибка при ответе на callback query. " +
                         "callbackQueryId={}, response={}", callbackQueryId, apiResponse);
                break;
                
            case 401:
                log.error("Unauthorized (401): Неверный токен бота! " +
                         "callbackQueryId={}, response={}", callbackQueryId, apiResponse);
                break;
                
            case 403:
                log.warn("Forbidden (403): Нет доступа к callback query. " +
                        "callbackQueryId={}, response={}", callbackQueryId, apiResponse);
                break;
                
            case 429:
                log.warn("Too Many Requests (429): Превышен лимит запросов. " +
                        "callbackQueryId={}, response={}", callbackQueryId, apiResponse);
                break;
                
            default:
                if (errorCode >= 500) {
                    log.error("Server Error ({}): Ошибка сервера Telegram при ответе на callback query. " +
                            "callbackQueryId={}, response={}", errorCode, callbackQueryId, apiResponse);
                } else {
                    log.error("Telegram API Error ({}): callbackQueryId={}, response={}", 
                            errorCode, callbackQueryId, apiResponse);
                }
        }
    }

    /**
     * Вычисляет задержку для exponential backoff.
     * 
     * @param attempt номер попытки
     * @return задержка в миллисекундах
     */
    private long calculateDelay(int attempt) {
        return (long) (1000 * Math.pow(2, attempt - 1));
    }

    /**
     * Выполняет задержку с обработкой прерываний.
     * 
     * @param delayMs задержка в миллисекундах
     */
    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Прерывание во время задержки retry");
        }
    }

    private void recordMetricForTelegramError(TelegramApiRequestException e) {
        Integer errorCode = e.getErrorCode();
        
        if (errorCode == null) {
            recordMetric("unknown_error");
            return;
        }
        
        String errorType = switch (errorCode) {
            case 400 -> "bad_request";
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 404 -> "not_found";
            case 429 -> "rate_limit_error";
            default -> errorCode >= 500 ? "server_error" : "telegram_api_error";
        };
        
        recordMetric(errorType);
    }
    
    private void recordMetric(String errorType) {
        if (metricsService != null) {
            metricsService.recordMessageSendError(errorType);
        }
    }
}
