package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

/**
 * Обработчик callback queries для навигации по календарю.
 * 
 * <p>Обрабатывает следующие типы callback:</p>
 * <ul>
 *   <li>calendar_ - навигация по месяцам календаря (calendar_YYYY-MM)</li>
 *   <li>calendar_cancel - отмена выбора даты</li>
 *   <li>date_actions_ - действия с выбранной датой</li>
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
public class NavigationCallbackHandler implements CallbackHandler {
    
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final BotMessageBuilder messageBuilder;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.CALENDAR;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        
        return CallbackPrefix.CALENDAR.matches(callbackData) ||
               CallbackPrefix.DATE_ACTIONS.matches(callbackData);
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        log.debug("Обработка callback навигации: data='{}', userId={}", 
                callbackData, user.getId());
        
        if (CallbackPrefix.CALENDAR.matches(callbackData)) {
            handleCalendarNavigation(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.DATE_ACTIONS.matches(callbackData)) {
            handleDateActions(callbackData, user, chatId, messageId, callbackQueryId);
        }
    }
    
    /**
     * Обрабатывает навигацию по календарю (переключение месяцев).
     * 
     * @param callbackData данные callback (формат: calendar_YYYY-MM или calendar_cancel)
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleCalendarNavigation(String callbackData, User user, Long chatId, 
                                          Integer messageId, String callbackQueryId) {
        try {
            // Проверяем отмену
            if (callbackData.equals("calendar_cancel")) {
                String message = messageBuilder.buildEventCancelledMessage();
                messageService.editMessageText(chatId, messageId, message, null);
                messageService.answerCallbackQuery(callbackQueryId, "Отменено");
                
                log.info("Создание события отменено пользователем {}", user.getId());
                return;
            }
            
            // Извлекаем год и месяц из callback data (формат: calendar_YYYY-MM)
            String payload = CallbackPrefix.CALENDAR.extractPayload(callbackData);
            String[] parts = payload.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            
            log.debug("Навигация по календарю: год={}, месяц={}, userId={}", year, month, user.getId());
            
            // Показываем календарь для выбранного месяца с событиями семьи
            InlineKeyboardMarkup keyboard = keyboardService.createCalendarKeyboard(
                    year, month, user.getFamily().getId());
            
            String message = messageBuilder.buildSelectDateMessage();
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "");
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при навигации по календарю: userId={}, error={}", 
                     user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при навигации по календарю", e);
        }
    }
    
    /**
     * Обрабатывает действия с датой в календаре.
     * 
     * @param callbackData данные callback (формат: date_actions_{action}_{date})
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleDateActions(String callbackData, User user, Long chatId, 
                                   Integer messageId, String callbackQueryId) {
        // Извлекаем действие (view или create)
        String payload = CallbackPrefix.DATE_ACTIONS.extractPayload(callbackData);
        
        log.info("Пользователь {} выбрал действие с датой: {}", user.getId(), payload);
        
        try {
            if (payload.equals("view")) {
                // TODO: Показать события на выбранную дату
                messageService.editMessageText(chatId, messageId, 
                    "📅 Просмотр событий на дату", null);
            } else if (payload.equals("create")) {
                // TODO: Начать создание нового события на выбранную дату
                messageService.editMessageText(chatId, messageId, 
                    "➕ Создание нового события", null);
            }
            
            messageService.answerCallbackQuery(callbackQueryId, "");
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при обработке действия с датой: userId={}, action={}, error={}", 
                     user.getId(), payload, e.getMessage());
            throw new RuntimeException("Ошибка при обработке действия с датой", e);
        }
    }
}
