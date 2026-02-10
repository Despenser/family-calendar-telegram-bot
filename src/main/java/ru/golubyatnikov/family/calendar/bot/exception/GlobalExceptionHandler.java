package ru.golubyatnikov.family.calendar.bot.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.italic;

/**
 * Глобальный обработчик исключений для всего приложения.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Создает стандартизированный объект ответа об ошибке.
     *
     * @param status HTTP статус
     * @param error краткое описание ошибки
     * @param message детальное сообщение для пользователя
     * @return Map с информацией об ошибке
     */
    private @NonNull Map<String, Object> createErrorResponse(@NonNull HttpStatus status,
                                                                      String error,
                                                                      String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        errorResponse.put("status", status.value());
        errorResponse.put("error", error);
        errorResponse.put("message", message);
        return errorResponse;
    }
    
    /**
     * Обрабатывает исключение UserNotFoundException.
     * 
     * @param ex исключение UserNotFoundException
     * @return ResponseEntity с информацией об ошибке
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFoundException(UserNotFoundException ex) {

        log.error("Пользователь не найден: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            HttpStatus.NOT_FOUND,
            bold("Пользователь не найден"),
            ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }
    
    /**
     * Обрабатывает исключение EventNotFoundException.
     * 
     * @param ex исключение EventNotFoundException
     * @return ResponseEntity с информацией об ошибке
     */
    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEventNotFoundException(EventNotFoundException ex) {

        log.error("Событие не найдено: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            HttpStatus.NOT_FOUND,
            bold("Событие не найдено"),
            ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }
    
    /**
     * Обрабатывает исключение UnauthorizedAccessException.
     * 
     * @param ex исключение UnauthorizedAccessException
     * @return ResponseEntity с информацией об ошибке
     */
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedAccessException(UnauthorizedAccessException ex) {

        log.error("Несанкционированный доступ: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            HttpStatus.FORBIDDEN,
            bold("Доступ запрещен"),
            italic("У вас нет прав для выполнения этой операции")
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorResponse);
    }
    
    /**
     * Обрабатывает исключение InvalidDateException.
     * 
     * @param ex исключение InvalidDateException
     * @return ResponseEntity с информацией об ошибке
     */
    @ExceptionHandler(InvalidDateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidDateException(InvalidDateException ex) {

        log.error("Некорректная дата: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            HttpStatus.BAD_REQUEST,
            bold("Некорректная дата"),
            ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }
    
    /**
     * Обрабатывает исключения DataAccessException (ошибки базы данных).
     * 
     * @param ex исключение DataAccessException
     * @return ResponseEntity с информацией об ошибке
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccessException(DataAccessException ex) {

        log.error("Ошибка доступа к базе данных: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            HttpStatus.SERVICE_UNAVAILABLE,
            bold("Временная ошибка сервиса"),
            italic("Произошла ошибка при работе с базой данных. Пожалуйста, попробуйте позже.")
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorResponse);
    }
    
    /**
     * Обрабатывает исключения TelegramApiException.
     * 
     * @param ex исключение TelegramApiException
     * @return ResponseEntity с информацией об ошибке
     */
    @ExceptionHandler(TelegramApiException.class)
    public ResponseEntity<Map<String, Object>> handleTelegramApiException(TelegramApiException ex) {

        log.error("Ошибка Telegram API: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            HttpStatus.BAD_GATEWAY,
            bold("Ошибка связи с Telegram"),
            italic("Произошла ошибка при взаимодействии с Telegram. Пожалуйста, попробуйте позже.")
        );
        
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(errorResponse);
    }
    
    /**
     * Обрабатывает все остальные необработанные исключения.
     * 
     * @param ex любое исключение
     * @return ResponseEntity с информацией об ошибке
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {

        log.error("Необработанное исключение: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            bold("Внутренняя ошибка сервера"),
            italic("Произошла непредвиденная ошибка. Пожалуйста, попробуйте позже.")
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }
}
