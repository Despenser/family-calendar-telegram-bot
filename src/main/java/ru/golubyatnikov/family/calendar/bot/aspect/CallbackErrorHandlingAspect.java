package ru.golubyatnikov.family.calendar.bot.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

/**
 * AOP-аспект для централизованной обработки ошибок в callback handlers.
 *
 * @author Golubyatnikov Aleksey
 * @version 1.0.0
 * @since 2026-01-16
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class CallbackErrorHandlingAspect {
    
    private final TelegramMessageService messageService;
    
    /**
     * Перехватывает исключения в методах, помеченных @HandleCallbackErrors.
     * 
     * @param joinPoint точка соединения AOP
     * @return результат выполнения метода или null при ошибке
     * @throws Throwable если ошибка не может быть обработана
     */
    @Around("@annotation(ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors)")
    public Object handleCallbackErrors(@NonNull ProceedingJoinPoint joinPoint) throws Throwable {
        CallbackQuery callbackQuery = extractCallbackQuery(joinPoint.getArgs());
        
        try {
            return joinPoint.proceed();

        } catch (Exception e) {
            handleException(e, callbackQuery, joinPoint);
            return null;
        }
    }
    
    /**
     * Извлекает CallbackQuery из аргументов метода.
     * 
     * @param args аргументы метода
     * @return CallbackQuery или null если не найден
     */
    private CallbackQuery extractCallbackQuery(Object[] args) {
        if (args == null) {
            return null;
        }
        
        for (Object arg : args) {
            if (arg instanceof CallbackQuery) {
                return (CallbackQuery) arg;
            }
        }
        return null;
    }
    
    /**
     * Обрабатывает исключение: логирует и отправляет сообщение пользователю.
     * 
     * @param e исключение
     * @param callbackQuery объект callback query (может быть null)
     * @param joinPoint точка соединения для получения информации о методе
     */
    private void handleException(Exception e,
                                 CallbackQuery callbackQuery,
                                 @NonNull ProceedingJoinPoint joinPoint) {

        String callbackData = extractCallbackData(callbackQuery);
        Long userId = extractUserId(callbackQuery);
        Long chatId = extractChatId(callbackQuery);
        String handlerName = joinPoint.getSignature().toShortString();

        log.error("Ошибка при обработке callback: data='{}', userId={}, chatId={}, " +
                  "handler={}, errorType={}, errorMessage={}", 
                  callbackData, userId, chatId,
                  handlerName,
                  e.getClass().getSimpleName(), 
                  e.getMessage(), 
                  e);

        sendErrorResponse(callbackQuery);
    }
    
    /**
     * Извлекает callback data из CallbackQuery.
     */
    private String extractCallbackData(CallbackQuery callbackQuery) {
        if (callbackQuery == null) {
            return "unknown";
        }
        return callbackQuery.getData() != null ? callbackQuery.getData() : "unknown";
    }
    
    /**
     * Извлекает userId из CallbackQuery.
     */
    private Long extractUserId(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.getFrom() == null) {
            return null;
        }
        return callbackQuery.getFrom().getId();
    }
    
    /**
     * Извлекает chatId из CallbackQuery.
     *
     * @param callbackQuery объект callback query
     * @return chatId или null если не найден
     */
    private Long extractChatId(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.getMessage() == null) {
            return null;
        }
        return callbackQuery.getMessage().getChatId();
    }
    
    /**
     * Отправляет ответ об ошибке пользователю.
     * 
     * @param callbackQuery объект callback query
     */
    private void sendErrorResponse(CallbackQuery callbackQuery) {
        if (callbackQuery == null) {
            return;
        }
        
        try {
            messageService.answerCallbackQuery(
                callbackQuery.getId(), 
                "❌ Произошла ошибка. Попробуйте еще раз."
            );
        } catch (Exception ex) {
            log.error("Ошибка при ответе на callback query: callbackId={}, error={}", 
                    callbackQuery.getId(), ex.getMessage());
        }
    }
}
