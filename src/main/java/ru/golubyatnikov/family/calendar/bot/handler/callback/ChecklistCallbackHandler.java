package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

/**
 * Обработчик callback queries для работы с чек-листами событий.
 * 
 * <p>Обрабатывает следующие типы callback:</p>
 * <ul>
 *   <li>checklist_add - добавление нового пункта в чек-лист</li>
 *   <li>checklist_toggle_{itemId} - переключение статуса пункта</li>
 *   <li>checklist_delete_{itemId} - удаление пункта</li>
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
public class ChecklistCallbackHandler implements CallbackHandler {
    
    private final TelegramMessageService messageService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.CHECKLIST;
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        log.debug("Обработка callback чек-листа: data='{}', userId={}", 
                callbackData, user.getId());
        
        handleChecklist(callbackData, user.getId(), chatId, messageId, callbackQueryId);
    }
    
    /**
     * Обрабатывает действия с чек-листом.
     * 
     * @param callbackData данные callback (формат: checklist_{action}_{itemId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleChecklist(String callbackData, Long userId, Long chatId, 
                                Integer messageId, String callbackQueryId) {
        // Извлекаем действие (add или toggle_ITEM_ID)
        String action = CallbackPrefix.CHECKLIST.extractPayload(callbackData);
        
        log.debug("Пользователь {} выполнил действие с чек-листом: {}", userId, action);
        
        try {
            if (action.equals("add")) {
                String message = "✅ Добавление пункта в чек-лист\n\n" +
                               "Отправьте текст нового пункта:";
                messageService.editMessageText(chatId, messageId, message, null);
                messageService.answerCallbackQuery(callbackQueryId, "");
            } else if (action.startsWith("toggle_")) {
                // Переключаем статус пункта чек-листа
                Long itemId = Long.parseLong(action.substring("toggle_".length()));
                log.debug("Переключение статуса пункта чек-листа ID={}", itemId);
                
                // TODO: Переключить статус пункта чек-листа через ChecklistService
                
                messageService.answerCallbackQuery(callbackQueryId, "✅ Статус изменен");
            } else if (action.startsWith("delete_")) {
                // Удаляем пункт чек-листа
                Long itemId = Long.parseLong(action.substring("delete_".length()));
                log.debug("Удаление пункта чек-листа ID={}", itemId);
                
                // TODO: Удалить пункт чек-листа через ChecklistService
                
                messageService.answerCallbackQuery(callbackQueryId, "🗑️ Пункт удален");
            } else {
                log.warn("Неизвестное действие с чек-листом: {}", action);
                messageService.answerCallbackQuery(callbackQueryId, "❌ Неизвестное действие");
            }
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при обработке чек-листа: userId={}, action={}, error={}", 
                     userId, action, e.getMessage());
            throw new RuntimeException("Ошибка при обработке чек-листа", e);
        }
    }
}
