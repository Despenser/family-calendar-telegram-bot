package ru.golubyatnikov.family.calendar.bot.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit тесты для GlobalExceptionHandler.
 * 
 * <p>Проверяет корректность обработки различных типов исключений:</p>
 * <ul>
 *   <li>UserNotFoundException - пользователь не найден</li>
 *   <li>EventNotFoundException - событие не найдено</li>
 *   <li>UnauthorizedAccessException - несанкционированный доступ</li>
 *   <li>InvalidDateException - некорректная дата</li>
 *   <li>DataAccessException - ошибки базы данных</li>
 *   <li>TelegramApiException - ошибки Telegram API</li>
 *   <li>Exception - общие исключения</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 9.2, 9.3</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    // ========== Тесты для UserNotFoundException ==========

    @Test
    @DisplayName("Должен обработать UserNotFoundException и вернуть 404")
    void shouldHandleUserNotFoundException() {
        // Given
        Long userId = 123L;
        UserNotFoundException exception = new UserNotFoundException(userId);

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleUserNotFoundException(exception);

        // Then
        assertNotNull(response, "Response не должен быть null");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body, "Body не должен быть null");
        assertEquals(404, body.get("status"));
        assertEquals("Пользователь не найден", body.get("error"));
        assertNotNull(body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    @DisplayName("Должен включать дружественное сообщение в ответ для UserNotFoundException")
    void shouldIncludeFriendlyMessageForUserNotFoundException() {
        // Given
        Long userId = 456L;
        UserNotFoundException exception = new UserNotFoundException(userId);

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleUserNotFoundException(exception);

        // Then
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        String message = (String) body.get("message");
        assertTrue(message.contains("123") || message.contains("не найден") || message.contains("456"),
            "Сообщение должно содержать информацию о пользователе");
    }

    // ========== Тесты для EventNotFoundException ==========

    @Test
    @DisplayName("Должен обработать EventNotFoundException и вернуть 404")
    void shouldHandleEventNotFoundException() {
        // Given
        Long eventId = 789L;
        EventNotFoundException exception = new EventNotFoundException(eventId);

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleEventNotFoundException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(404, body.get("status"));
        assertEquals("Событие не найдено", body.get("error"));
        assertNotNull(body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    // ========== Тесты для UnauthorizedAccessException ==========

    @Test
    @DisplayName("Должен обработать UnauthorizedAccessException и вернуть 403")
    void shouldHandleUnauthorizedAccessException() {
        // Given
        String errorMessage = "User cannot edit this event";
        UnauthorizedAccessException exception = new UnauthorizedAccessException(errorMessage);

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleUnauthorizedAccessException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(403, body.get("status"));
        assertEquals("Доступ запрещен", body.get("error"));
        assertNotNull(body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    @DisplayName("Должен включать дружественное сообщение для UnauthorizedAccessException")
    void shouldIncludeFriendlyMessageForUnauthorizedAccessException() {
        // Given
        UnauthorizedAccessException exception = 
            new UnauthorizedAccessException("User cannot delete this event");

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleUnauthorizedAccessException(exception);

        // Then
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        String message = (String) body.get("message");
        assertEquals("У вас нет прав для выполнения этой операции", message,
            "Должно быть дружественное сообщение вместо технического");
    }

    // ========== Тесты для InvalidDateException ==========

    @Test
    @DisplayName("Должен обработать InvalidDateException и вернуть 400")
    void shouldHandleInvalidDateException() {
        // Given
        String errorMessage = "Дата не может быть в прошлом";
        InvalidDateException exception = new InvalidDateException(errorMessage);

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleInvalidDateException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(400, body.get("status"));
        assertEquals("Некорректная дата", body.get("error"));
        assertEquals(errorMessage, body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    // ========== Тесты для DataAccessException ==========

    @Test
    @DisplayName("Должен обработать DataAccessException и вернуть 503")
    void shouldHandleDataAccessException() {
        // Given
        DataAccessException exception = new DataAccessException("Database connection failed") {};

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleDataAccessException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(503, body.get("status"));
        assertEquals("Временная ошибка сервиса", body.get("error"));
        assertNotNull(body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    @DisplayName("Должен включать дружественное сообщение для DataAccessException")
    void shouldIncludeFriendlyMessageForDataAccessException() {
        // Given
        DataAccessException exception = new DataAccessException("SQL syntax error") {};

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleDataAccessException(exception);

        // Then
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        String message = (String) body.get("message");
        assertTrue(message.contains("базой данных") && message.contains("попробуйте позже"),
            "Сообщение должно быть дружественным и не содержать технических деталей");
    }

    // ========== Тесты для TelegramApiException ==========

    @Test
    @DisplayName("Должен обработать TelegramApiException и вернуть 502")
    void shouldHandleTelegramApiException() {
        // Given
        TelegramApiException exception = new TelegramApiException("Telegram API error");

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleTelegramApiException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(502, body.get("status"));
        assertEquals("Ошибка связи с Telegram", body.get("error"));
        assertNotNull(body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    @DisplayName("Должен включать дружественное сообщение для TelegramApiException")
    void shouldIncludeFriendlyMessageForTelegramApiException() {
        // Given
        TelegramApiException exception = new TelegramApiException("Rate limit exceeded");

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleTelegramApiException(exception);

        // Then
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        String message = (String) body.get("message");
        assertTrue(message.contains("Telegram") && message.contains("попробуйте позже"),
            "Сообщение должно быть дружественным");
    }

    // ========== Тесты для общих Exception ==========

    @Test
    @DisplayName("Должен обработать общее Exception и вернуть 500")
    void shouldHandleGenericException() {
        // Given
        Exception exception = new RuntimeException("Unexpected error");

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleGenericException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(500, body.get("status"));
        assertEquals("Внутренняя ошибка сервера", body.get("error"));
        assertNotNull(body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    @DisplayName("Должен включать дружественное сообщение для общего Exception")
    void shouldIncludeFriendlyMessageForGenericException() {
        // Given
        Exception exception = new NullPointerException("Null pointer");

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleGenericException(exception);

        // Then
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        String message = (String) body.get("message");
        assertEquals("Произошла непредвиденная ошибка. Пожалуйста, попробуйте позже.", message,
            "Должно быть общее дружественное сообщение");
    }

    // ========== Тесты структуры ответа ==========

    @Test
    @DisplayName("Все ответы должны содержать обязательные поля")
    void allResponsesShouldContainRequiredFields() {
        // Given
        UserNotFoundException userEx = new UserNotFoundException(1L);
        UnauthorizedAccessException authEx = new UnauthorizedAccessException("test");
        DataAccessException dbEx = new DataAccessException("test") {};

        // When
        ResponseEntity<Map<String, Object>> response1 = 
            exceptionHandler.handleUserNotFoundException(userEx);
        ResponseEntity<Map<String, Object>> response2 = 
            exceptionHandler.handleUnauthorizedAccessException(authEx);
        ResponseEntity<Map<String, Object>> response3 = 
            exceptionHandler.handleDataAccessException(dbEx);

        // Then
        for (ResponseEntity<Map<String, Object>> response : 
                java.util.List.of(response1, response2, response3)) {
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertTrue(body.containsKey("timestamp"), "Должно содержать timestamp");
            assertTrue(body.containsKey("status"), "Должно содержать status");
            assertTrue(body.containsKey("error"), "Должно содержать error");
            assertTrue(body.containsKey("message"), "Должно содержать message");
        }
    }
}
