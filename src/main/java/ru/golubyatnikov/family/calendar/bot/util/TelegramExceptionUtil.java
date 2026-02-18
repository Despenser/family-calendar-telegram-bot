package ru.golubyatnikov.family.calendar.bot.util;

import org.springframework.lang.NonNull;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

/**
 * Утилитный класс для работы с исключениями Telegram API.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
public final class TelegramExceptionUtil {

    private TelegramExceptionUtil() {
        throw new UnsupportedOperationException("Утилитный класс не может быть инстанцирован");
    }

    /**
     * Проверяет, является ли исключение ошибкой парсинга MarkdownV2.
     * 
     * @param e исключение от Telegram API
     * @return true, если это ошибка парсинга
     */
    public static boolean isParseError(TelegramApiRequestException e) {
        if (e == null || e.getErrorCode() == null || e.getErrorCode() != 400) {
            return false;
        }
        
        String message = e.getMessage();
        String apiResponse = e.getApiResponse();
        
        return (message != null && message.contains("can't parse entities")) ||
               (apiResponse != null && apiResponse.contains("can't parse entities"));
    }

    /**
     * Получает строковое представление стека вызовов исключения.
     * 
     * @param e исключение
     * @return строка со стеком вызовов (первые 5 элементов)
     */
    public static @NonNull String getStackTraceString(Exception e) {
        if (e == null || e.getStackTrace() == null || e.getStackTrace().length == 0) {
            return "no stack trace";
        }
        
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] elements = e.getStackTrace();
        int limit = Math.min(5, elements.length);
        
        for (int i = 0; i < limit; i++) {
            sb.append(elements[i].toString());
            if (i < limit - 1) {
                sb.append(" -> ");
            }
        }
        
        return sb.toString();
    }
}
