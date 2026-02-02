package ru.golubyatnikov.family.calendar.bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

/**
 * Сервис для форматирования сообщений Telegram.
 * 
 * <p>MessageFormatter отвечает за обработку и форматирование текста сообщений,
 * включая обработку ошибок парсинга MarkdownV2 и fallback механизмы. Основные функции:</p>
 * <ul>
 *   <li>Проверка ошибок парсинга MarkdownV2</li>
 *   <li>Проверка ошибок удаленных/старых сообщений</li>
 *   <li>Подсчет кнопок в клавиатурах</li>
 *   <li>Получение деталей клавиатур для логирования</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.1</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-02
 */
@Service
@Slf4j
public class MessageFormatter {

    /**
     * Проверяет, является ли исключение ошибкой парсинга MarkdownV2.
     * 
     * @param e исключение от Telegram API
     * @return true если это ошибка парсинга
     */
    public boolean isParseError(TelegramApiRequestException e) {
        if (e.getErrorCode() == null || e.getErrorCode() != 400) {
            return false;
        }
        
        String message = e.getMessage();
        String apiResponse = e.getApiResponse();
        
        return (message != null && message.contains("can't parse entities")) ||
               (apiResponse != null && apiResponse.contains("can't parse entities"));
    }

    /**
     * Проверяет, является ли ошибка "сообщение не найдено".
     * 
     * @param e исключение от Telegram API
     * @return true если это ошибка "сообщение не найдено"
     */
    public boolean isMessageNotFoundError(TelegramApiRequestException e) {
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
     * @return true если это ошибка "сообщение слишком старое"
     */
    public boolean isMessageTooOldError(TelegramApiRequestException e) {
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
     * @return true если это ошибка "сообщение не изменилось"
     */
    public boolean isMessageNotModifiedError(TelegramApiRequestException e) {
        String message = e.getMessage();
        String apiResponse = e.getApiResponse();
        
        return (message != null && message.contains("message is not modified")) ||
               (apiResponse != null && apiResponse.contains("message is not modified"));
    }

    /**
     * Проверяет, является ли ошибка "сообщение для удаления не найдено".
     * 
     * @param e исключение от Telegram API
     * @return true если это ошибка "сообщение не найдено"
     */
    public boolean isMessageDeleteNotFoundError(TelegramApiRequestException e) {
        String message = e.getMessage();
        String apiResponse = e.getApiResponse();
        
        return (message != null && message.contains("message to delete not found")) ||
               (apiResponse != null && apiResponse.contains("message to delete not found"));
    }

    /**
     * Подсчитывает количество кнопок в inline клавиатуре.
     * 
     * @param markup разметка inline клавиатуры
     * @return количество кнопок
     */
    public int countButtons(InlineKeyboardMarkup markup) {
        if (markup == null || markup.getKeyboard() == null) {
            return 0;
        }
        
        return markup.getKeyboard().stream()
                .mapToInt(row -> row != null ? row.size() : 0)
                .sum();
    }

    /**
     * Получает детальное описание inline клавиатуры для логирования.
     * 
     * @param markup разметка inline клавиатуры
     * @return строка с деталями клавиатуры
     */
    public String getKeyboardDetails(InlineKeyboardMarkup markup) {
        if (markup == null || markup.getKeyboard() == null || markup.getKeyboard().isEmpty()) {
            return "empty keyboard";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        
        int rowIndex = 0;
        for (var row : markup.getKeyboard()) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            
            sb.append("row").append(rowIndex).append("=[");
            
            int btnIndex = 0;
            for (var button : row) {
                if (button != null) {
                    sb.append("{text='").append(button.getText())
                      .append("', callback='").append(button.getCallbackData())
                      .append("'}");
                    
                    if (btnIndex < row.size() - 1) {
                        sb.append(", ");
                    }
                }
                btnIndex++;
            }
            
            sb.append("]");
            
            if (rowIndex < markup.getKeyboard().size() - 1) {
                sb.append(", ");
            }
            rowIndex++;
        }
        
        sb.append("]");
        return sb.toString();
    }

    /**
     * Получает строковое представление стека вызовов исключения.
     * 
     * @param e исключение
     * @return строка со стеком вызовов (первые 5 элементов)
     */
    public String getStackTraceString(Exception e) {
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
