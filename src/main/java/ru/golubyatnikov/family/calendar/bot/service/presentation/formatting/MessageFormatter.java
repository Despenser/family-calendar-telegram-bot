package ru.golubyatnikov.family.calendar.bot.service.presentation.formatting;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

/**
 * Сервис для форматирования сообщений Telegram.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-02
 */
@Service
@Slf4j
public class MessageFormatter {

    /**
     * Проверяет, является ли ошибка "сообщение не найдено".
     * 
     * @param e исключение от Telegram API
     * @return true, если это ошибка "сообщение не найдено"
     */
    public boolean isMessageNotFoundError(@NonNull TelegramApiRequestException e) {
        String message = e.getMessage();
        String apiResponse = e.getApiResponse();
        
        return (message != null && message.contains("message to edit not found")) ||
               (message != null && message.contains("message can't be edited")) ||
               (message != null && message.contains("message to delete not found")) ||
               (apiResponse != null && apiResponse.contains("message to edit not found")) ||
               (apiResponse != null && apiResponse.contains("message can't be edited")) ||
               (apiResponse != null && apiResponse.contains("message to delete not found"));
    }

    /**
     * Проверяет, является ли ошибка "сообщение слишком старое".
     * 
     * @param e исключение от Telegram API
     * @return true, если это ошибка "сообщение слишком старое"
     */
    public boolean isMessageTooOldError(@NonNull TelegramApiRequestException e) {
        String message = e.getMessage();
        String apiResponse = e.getApiResponse();
        
        return (message != null && message.contains("message is too old")) ||
               (message != null && message.contains("message can't be edited")) ||
               (apiResponse != null && apiResponse.contains("message is too old")) ||
               (apiResponse != null && apiResponse.contains("message can't be edited"));
    }

    /**
     * Проверяет, является ли ошибка "сообщение не изменилось".
     * 
     * @param e исключение от Telegram API
     * @return true, если это ошибка "сообщение не изменилось"
     */
    public boolean isMessageNotModifiedError(@NonNull TelegramApiRequestException e) {
        String message = e.getMessage();
        String apiResponse = e.getApiResponse();
        
        return (message != null && message.contains("message is not modified")) ||
               (apiResponse != null && apiResponse.contains("message is not modified"));
    }

    /**
     * Проверяет, является ли ошибка "сообщение для удаления не найдено".
     * 
     * @param e исключение от Telegram API
     * @return true, если это ошибка "сообщение не найдено"
     */
    public boolean isMessageDeleteNotFoundError(@NonNull TelegramApiRequestException e) {
        String message = e.getMessage();
        String apiResponse = e.getApiResponse();
        
        return (message != null && message.contains("message to delete not found")) ||
               (apiResponse != null && apiResponse.contains("message to delete not found"));
    }
}
