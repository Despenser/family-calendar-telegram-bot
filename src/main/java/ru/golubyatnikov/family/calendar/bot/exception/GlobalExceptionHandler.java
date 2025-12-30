package ru.golubyatnikov.family.calendar.bot.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Глобальный обработчик исключений для всего приложения.
 * 
 * <p>Этот класс перехватывает все исключения, возникающие в контроллерах,
 * логирует их с полным stack trace и возвращает дружественные сообщения
 * пользователям.</p>
 * 
 * <p>Обрабатываемые типы исключений:</p>
 * <ul>
 *   <li>{@link UserNotFoundException} - пользователь не найден</li>
 *   <li>{@link EventNotFoundException} - событие не найдено</li>
 *   <li>{@link UnauthorizedAccessException} - несанкционированный доступ</li>
 *   <li>{@link InvalidDateException} - некорректная дата</li>
 *   <li>{@link DataAccessException} - ошибки базы данных</li>
 *   <li>{@link TelegramApiException} - ошибки Telegram API</li>
 *   <li>{@link Exception} - все остальные исключения</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 9.1, 9.2, 9.3, 9.4</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * Обрабатывает исключение UserNotFoundException.
     * 
     * <p>Возвращает HTTP 404 NOT FOUND с дружественным сообщением.</p>
     * 
     * @param ex исключение UserNotFoundException
     * @return ResponseEntity с информацией об ошибке
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFoundException(UserNotFoundException ex) {
        log.error("Пользователь не найден: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            HttpStatus.NOT_FOUND,
            "Пользователь не найден",
            ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    /**
     * Обрабатывает исключение EventNotFoundException.
     * 
     * <p>Возвращает HTTP 404 NOT FOUND с дружественным сообщением.</p>
     * 
     * @param ex исключение EventNotFoundException
     * @return ResponseEntity с информацией об ошибке
     */
    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEventNotFoundException(EventNotFoundException ex) {
        log.error("Событие не найдено: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            HttpStatus.NOT_FOUND,
            "Событие не найдено",
            ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    /**
     * Обрабатывает исключение UnauthorizedAccessException.
     * 
     * <p>Возвращает HTTP 403 FORBIDDEN с дружественным сообщением.</p>
     * 
     * @param ex исключение UnauthorizedAccessException
     * @return ResponseEntity с информацией об ошибке
     */
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedAccessException(UnauthorizedAccessException ex) {
        log.error("Несанкционированный доступ: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            HttpStatus.FORBIDDEN,
            "Доступ запрещен",
            "У вас нет прав для выполнения этой операции"
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }
    
    /**
     * Обрабатывает исключение InvalidDateException.
     * 
     * <p>Возвращает HTTP 400 BAD REQUEST с дружественным сообщением.</p>
     * 
     * @param ex исключение InvalidDateException
     * @return ResponseEntity с информацией об ошибке
     */
    @ExceptionHandler(InvalidDateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidDateException(InvalidDateException ex) {
        log.error("Некорректная дата: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Некорректная дата",
            ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Обрабатывает исключения DataAccessException (ошибки базы данных).
     * 
     * <p>Логирует полный stack trace и возвращает HTTP 503 SERVICE UNAVAILABLE
     * с дружественным сообщением о временной недоступности.</p>
     * 
     * @param ex исключение DataAccessException
     * @return ResponseEntity с информацией об ошибке
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccessException(DataAccessException ex) {
        log.error("Ошибка доступа к базе данных: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Временная ошибка сервиса",
            "Произошла ошибка при работе с базой данных. Пожалуйста, попробуйте позже."
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }
    
    /**
     * Обрабатывает исключения TelegramApiException.
     * 
     * <p>Логирует полный stack trace и возвращает HTTP 502 BAD GATEWAY
     * с дружественным сообщением об ошибке взаимодействия с Telegram API.</p>
     * 
     * @param ex исключение TelegramApiException
     * @return ResponseEntity с информацией об ошибке
     */
    @ExceptionHandler(TelegramApiException.class)
    public ResponseEntity<Map<String, Object>> handleTelegramApiException(TelegramApiException ex) {
        log.error("Ошибка Telegram API: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            HttpStatus.BAD_GATEWAY,
            "Ошибка связи с Telegram",
            "Произошла ошибка при взаимодействии с Telegram. Пожалуйста, попробуйте позже."
        );
        
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }
    
    /**
     * Обрабатывает все остальные необработанные исключения.
     * 
     * <p>Логирует полный stack trace и возвращает HTTP 500 INTERNAL SERVER ERROR
     * с общим дружественным сообщением.</p>
     * 
     * @param ex любое исключение
     * @return ResponseEntity с информацией об ошибке
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Необработанное исключение: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Внутренняя ошибка сервера",
            "Произошла непредвиденная ошибка. Пожалуйста, попробуйте позже."
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    /**
     * Создает стандартизированный объект ответа об ошибке.
     * 
     * @param status HTTP статус
     * @param error краткое описание ошибки
     * @param message детальное сообщение для пользователя
     * @return Map с информацией об ошибке
     */
    private Map<String, Object> createErrorResponse(HttpStatus status, String error, String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        errorResponse.put("status", status.value());
        errorResponse.put("error", error);
        errorResponse.put("message", message);
        return errorResponse;
    }
}
