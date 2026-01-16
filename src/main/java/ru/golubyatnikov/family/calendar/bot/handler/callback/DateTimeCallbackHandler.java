package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Обработчик callback queries для выбора даты и времени события.
 * 
 * <p>Обрабатывает следующие типы callback:</p>
 * <ul>
 *   <li>date_ - выбор даты из календаря</li>
 *   <li>hour_ - выбор часа</li>
 *   <li>time_HH:MM - выбор времени (час и минуты)</li>
 *   <li>time_back - возврат к выбору часа</li>
 *   <li>time_cancel - отмена выбора времени</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.3, 2.5</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DateTimeCallbackHandler implements CallbackHandler {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    
    private final ConversationService conversationService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final BotMessageBuilder messageBuilder;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.DATE;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        
        return CallbackPrefix.DATE.matches(callbackData) ||
               CallbackPrefix.HOUR.matches(callbackData) ||
               isTimeWithMinutes(callbackData) ||
               CallbackPrefix.TIME_BACK.matches(callbackData) ||
               CallbackPrefix.TIME_CANCEL.matches(callbackData);
    }
    
    /**
     * Проверяет, является ли callback data выбором времени с минутами (формат time_HH:MM).
     * 
     * @param callbackData строка callback data
     * @return true если это выбор времени с минутами
     */
    private boolean isTimeWithMinutes(String callbackData) {
        return callbackData.startsWith("time_") && callbackData.contains(":");
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        log.debug("Обработка callback для даты/времени: data='{}', userId={}", 
                callbackData, user.getId());
        
        if (CallbackPrefix.DATE.matches(callbackData)) {
            handleDateSelection(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.HOUR.matches(callbackData)) {
            handleHourSelection(callbackData, chatId, messageId, callbackQueryId);
        } else if (isTimeWithMinutes(callbackData)) {
            handleTimeSelection(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.TIME_BACK.matches(callbackData)) {
            handleTimeBack(chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.TIME_CANCEL.matches(callbackData)) {
            handleTimeCancel(user.getId(), chatId, messageId, callbackQueryId);
        }
    }
    
    /**
     * Обрабатывает выбор даты из календаря.
     * Обновляет черновик события и показывает выбор часа.
     * 
     * @param callbackData данные callback (формат: date_YYYY-MM-DD)
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleDateSelection(String callbackData, Long userId, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
        // Извлекаем дату из callback data
        String dateStr = CallbackPrefix.DATE.extractPayload(callbackData);
        LocalDate date = LocalDate.parse(dateStr);
        
        // Обновляем черновик с выбранной датой
        conversationService.updateEventDate(userId, date);
        
        // Показываем выбор часа
        InlineKeyboardMarkup keyboard = keyboardService.createHourSelectionKeyboard();
        String formattedDate = date.format(DATE_FORMATTER);
        String message = messageBuilder.buildDateSelectedMessage(formattedDate);
        
        try {
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "Дата выбрана");
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при выборе даты: userId={}, date={}, error={}", 
                     userId, date, e.getMessage());
            throw new RuntimeException("Ошибка при выборе даты", e);
        }
        
        log.info("Дата выбрана для пользователя {}: {}", userId, date);
    }
    
    /**
     * Обрабатывает выбор часа.
     * Показывает выбор минут для выбранного часа.
     * 
     * @param callbackData данные callback (формат: hour_HH)
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleHourSelection(String callbackData, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
        // Извлекаем час из callback data
        String hourStr = CallbackPrefix.HOUR.extractPayload(callbackData);
        int hour = Integer.parseInt(hourStr);
        
        // Показываем выбор минут
        InlineKeyboardMarkup keyboard = keyboardService.createMinuteSelectionKeyboard(hour);
        String message = messageBuilder.buildHourSelectedMessage(hour);
        
        try {
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "Час выбран");
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при выборе часа: hour={}, error={}", hour, e.getMessage());
            throw new RuntimeException("Ошибка при выборе часа", e);
        }
        
        log.debug("Час выбран: {}", hour);
    }
    
    /**
     * Обрабатывает выбор времени (час и минуты).
     * Обновляет черновик и запрашивает название события.
     * 
     * @param callbackData данные callback (формат: time_HH:MM)
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleTimeSelection(String callbackData, Long userId, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
        // Извлекаем время из callback data (формат: time_HH:MM)
        String timeStr = callbackData.substring(5); // Убираем "time_"
        LocalTime time = LocalTime.parse(timeStr);
        
        // Обновляем черновик с выбранным временем
        conversationService.updateEventTime(userId, time);
        
        // Запрашиваем название события
        String formattedTime = time.format(TIME_FORMATTER);
        String message = messageBuilder.buildTimeSelectedMessage(formattedTime);
        
        try {
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, "Время выбрано");
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при выборе времени: userId={}, time={}, error={}", 
                     userId, time, e.getMessage());
            throw new RuntimeException("Ошибка при выборе времени", e);
        }
        
        log.info("Время выбрано для пользователя {}: {}", userId, time);
    }
    
    /**
     * Обрабатывает возврат к выбору часа.
     * 
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleTimeBack(Long chatId, Integer messageId, String callbackQueryId) {
        InlineKeyboardMarkup keyboard = keyboardService.createHourSelectionKeyboard();
        String message = messageBuilder.buildSelectHourMessage();
        
        try {
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "");
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при возврате к выбору часа: error={}", e.getMessage());
            throw new RuntimeException("Ошибка при возврате к выбору часа", e);
        }
        
        log.debug("Возврат к выбору часа");
    }
    
    /**
     * Обрабатывает отмену выбора времени.
     * Удаляет черновик события.
     * 
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleTimeCancel(Long userId, Long chatId, Integer messageId, 
                                  String callbackQueryId) {
        conversationService.cancelEventCreation(userId);
        
        String message = messageBuilder.buildEventCancelledMessage();
        try {
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, "Отменено");
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при отмене создания события: userId={}, error={}", 
                     userId, e.getMessage());
            throw new RuntimeException("Ошибка при отмене создания события", e);
        }
        
        log.info("Создание события отменено пользователем {}", userId);
    }
}
