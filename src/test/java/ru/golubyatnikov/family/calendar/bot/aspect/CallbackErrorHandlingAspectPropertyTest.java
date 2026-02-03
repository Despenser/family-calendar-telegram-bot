package ru.golubyatnikov.family.calendar.bot.aspect;

import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property-based тесты для CallbackErrorHandlingAspect.
 * 
 * <p>Тесты проверяют свойство полноты обработки ошибок: для любого исключения,
 * возникающего в методе с аннотацией @HandleCallbackErrors, аспект должен
 * логировать ошибку с контекстом и отправлять пользователю сообщение об ошибке.</p>
 * 
 * <p><b>Feature: code-quality-refactoring, Property 3: Error Handling Completeness</b></p>
 * <p><b>Validates: Requirements 2.2, 2.3, 2.4</b></p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
class CallbackErrorHandlingAspectPropertyTest {
    
    /**
     * Property 3: Error Handling Completeness
     * 
     * <p>Для любого исключения, возникающего в методе с аннотацией @HandleCallbackErrors,
     * аспект должен отправлять пользователю сообщение об ошибке через answerCallbackQuery.</p>
     * 
     * <p>Validates: Requirements 2.2, 2.3, 2.4</p>
     */
    @Property(tries = 100)
    void aspectSendsErrorResponseForAnyException(
            @ForAll @StringLength(min = 1, max = 50) String callbackData,
            @ForAll @StringLength(min = 1, max = 50) String callbackQueryId,
            @ForAll("exceptionProvider") Exception exception) throws Throwable {
        
        // Arrange
        TelegramMessageService messageService = mock(TelegramMessageService.class);
        CallbackErrorHandlingAspect aspect = new CallbackErrorHandlingAspect(messageService);
        
        // Создаём mock CallbackQuery
        CallbackQuery callbackQuery = createMockCallbackQuery(callbackData, callbackQueryId, 123L, 456L);
        
        // Создаём mock JoinPoint, который выбрасывает исключение
        ProceedingJoinPoint joinPoint = createMockJoinPoint(callbackQuery, exception);
        
        // Act
        Object result = aspect.handleCallbackErrors(joinPoint);
        
        // Assert
        // Результат должен быть null (исключение обработано)
        assertThat(result)
            .as("Результат должен быть null после обработки исключения")
            .isNull();
        
        // answerCallbackQuery должен быть вызван с сообщением об ошибке
        ArgumentCaptor<String> queryIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        
        verify(messageService, times(1))
            .answerCallbackQuery(queryIdCaptor.capture(), textCaptor.capture());
        
        assertThat(queryIdCaptor.getValue())
            .as("CallbackQueryId должен соответствовать исходному")
            .isEqualTo(callbackQueryId);
        
        assertThat(textCaptor.getValue())
            .as("Сообщение об ошибке должно содержать индикатор ошибки")
            .contains("❌");
    }
    
    /**
     * Проверяет, что аспект корректно обрабатывает null CallbackQuery.
     */
    @Property(tries = 100)
    void aspectHandlesNullCallbackQueryGracefully(
            @ForAll("exceptionProvider") Exception exception) throws Throwable {
        
        // Arrange
        TelegramMessageService messageService = mock(TelegramMessageService.class);
        CallbackErrorHandlingAspect aspect = new CallbackErrorHandlingAspect(messageService);
        
        // Создаём mock JoinPoint без CallbackQuery
        ProceedingJoinPoint joinPoint = createMockJoinPointWithoutCallbackQuery(exception);
        
        // Act
        Object result = aspect.handleCallbackErrors(joinPoint);
        
        // Assert
        // Результат должен быть null (исключение обработано)
        assertThat(result)
            .as("Результат должен быть null после обработки исключения")
            .isNull();
        
        // answerCallbackQuery НЕ должен быть вызван (нет CallbackQuery)
        verify(messageService, never()).answerCallbackQuery(anyString(), anyString());
    }
    
    /**
     * Проверяет, что аспект пропускает успешное выполнение метода.
     */
    @Property(tries = 100)
    void aspectPassesThroughSuccessfulExecution(
            @ForAll @StringLength(min = 1, max = 50) String callbackData,
            @ForAll @StringLength(min = 1, max = 50) String callbackQueryId,
            @ForAll @StringLength(min = 1, max = 50) String expectedResult) throws Throwable {
        
        // Arrange
        TelegramMessageService messageService = mock(TelegramMessageService.class);
        CallbackErrorHandlingAspect aspect = new CallbackErrorHandlingAspect(messageService);
        
        // Создаём mock CallbackQuery
        CallbackQuery callbackQuery = createMockCallbackQuery(callbackData, callbackQueryId, 123L, 456L);
        
        // Создаём mock JoinPoint, который возвращает результат без исключения
        ProceedingJoinPoint joinPoint = createMockJoinPointSuccess(callbackQuery, expectedResult);
        
        // Act
        Object result = aspect.handleCallbackErrors(joinPoint);
        
        // Assert
        // Результат должен быть тем, что вернул метод
        assertThat(result)
            .as("Результат должен соответствовать возвращаемому значению метода")
            .isEqualTo(expectedResult);
        
        // answerCallbackQuery НЕ должен быть вызван (нет ошибки)
        verify(messageService, never()).answerCallbackQuery(anyString(), anyString());
    }
    
    /**
     * Проверяет, что аспект обрабатывает ошибку при отправке ответа на callback query.
     */
    @Example
    void aspectHandlesErrorInAnswerCallbackQuery() throws Throwable {
        // Arrange
        TelegramMessageService messageService = mock(TelegramMessageService.class);
        doThrow(new TelegramApiException("Test error"))
            .when(messageService).answerCallbackQuery(anyString(), anyString());
        
        CallbackErrorHandlingAspect aspect = new CallbackErrorHandlingAspect(messageService);
        
        CallbackQuery callbackQuery = createMockCallbackQuery("test_data", "test_id", 123L, 456L);
        ProceedingJoinPoint joinPoint = createMockJoinPoint(callbackQuery, new RuntimeException("Test"));
        
        // Act - не должно выбросить исключение
        Object result = aspect.handleCallbackErrors(joinPoint);
        
        // Assert
        assertThat(result).isNull();
        verify(messageService, times(1)).answerCallbackQuery(anyString(), anyString());
    }
    
    /**
     * Провайдер различных типов исключений для тестирования.
     */
    @Provide
    Arbitrary<Exception> exceptionProvider() {
        return Arbitraries.of(
            new RuntimeException("Test runtime exception"),
            new IllegalArgumentException("Test illegal argument"),
            new IllegalStateException("Test illegal state"),
            new NullPointerException("Test null pointer"),
            new IndexOutOfBoundsException("Test index out of bounds")
        );
    }
    
    /**
     * Создаёт mock CallbackQuery с заданными параметрами.
     */
    private CallbackQuery createMockCallbackQuery(String callbackData, String callbackQueryId, 
                                                   Long userId, Long chatId) {
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        when(callbackQuery.getData()).thenReturn(callbackData);
        when(callbackQuery.getId()).thenReturn(callbackQueryId);
        
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(callbackQuery.getFrom()).thenReturn(user);
        
        Message message = mock(Message.class);
        when(message.getChatId()).thenReturn(chatId);
        when(callbackQuery.getMessage()).thenReturn(message);
        
        return callbackQuery;
    }
    
    /**
     * Создаёт mock JoinPoint, который выбрасывает исключение.
     */
    private ProceedingJoinPoint createMockJoinPoint(CallbackQuery callbackQuery, 
                                                     Exception exception) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{callbackQuery, null});
        when(joinPoint.proceed()).thenThrow(exception);
        
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("TestHandler.handle(..)");
        when(joinPoint.getSignature()).thenReturn(signature);
        
        return joinPoint;
    }
    
    /**
     * Создаёт mock JoinPoint без CallbackQuery.
     */
    private ProceedingJoinPoint createMockJoinPointWithoutCallbackQuery(Exception exception) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"some string", 123});
        when(joinPoint.proceed()).thenThrow(exception);
        
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("TestHandler.handle(..)");
        when(joinPoint.getSignature()).thenReturn(signature);
        
        return joinPoint;
    }
    
    /**
     * Создаёт mock JoinPoint, который успешно выполняется.
     */
    private ProceedingJoinPoint createMockJoinPointSuccess(CallbackQuery callbackQuery, 
                                                            Object result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{callbackQuery, null});
        when(joinPoint.proceed()).thenReturn(result);
        
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("TestHandler.handle(..)");
        when(joinPoint.getSignature()).thenReturn(signature);
        
        return joinPoint;
    }
}
