package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;

/**
 * Маршрутизатор callback queries для операций с событиями.
 * 
 * <p>Делегирует обработку специализированным handlers в зависимости от типа операции:</p>
 * <ul>
 *   <li>view_event_ - просмотр деталей события ({@link EventViewHandler})</li>
 *   <li>view_event_from_reminder_ - просмотр из напоминания ({@link EventReminderNavigationHandler})</li>
 *   <li>edit_event_ - редактирование события ({@link EventEditHandler})</li>
 *   <li>delete_event_ - удаление события ({@link EventDeleteHandler})</li>
 *   <li>complete_event_ - завершение события ({@link EventCompletionHandler})</li>
 *   <li>edit_field_ - редактирование конкретного поля ({@link EventFieldEditHandler})</li>
 *   <li>back_to_reminder_ - возврат к напоминанию ({@link EventReminderNavigationHandler})</li>
 * </ul>
 * 
 * <p><b>Архитектурный паттерн:</b> Router + Delegation</p>
 * <p><b>Требования:</b> 1.1, 1.3</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventCallbackRouter implements CallbackHandler {
    
    private final EventViewHandler eventViewHandler;
    private final EventEditHandler eventEditHandler;
    private final EventDeleteHandler eventDeleteHandler;
    private final EventCompletionHandler eventCompletionHandler;
    private final EventFieldEditHandler eventFieldEditHandler;
    private final EventReminderNavigationHandler eventReminderNavigationHandler;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.VIEW_EVENT;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        
        return CallbackPrefix.VIEW_EVENT.matches(callbackData) ||
               CallbackPrefix.VIEW_EVENT_FROM_REMINDER.matches(callbackData) ||
               CallbackPrefix.EDIT_EVENT.matches(callbackData) ||
               CallbackPrefix.DELETE_EVENT.matches(callbackData) ||
               CallbackPrefix.EDIT_FIELD.matches(callbackData) ||
               CallbackPrefix.COMPLETE_EVENT.matches(callbackData) ||
               CallbackPrefix.EDIT_CANCEL.matches(callbackData) ||
               CallbackPrefix.ADD_COMPLETION_NOTE.matches(callbackData) ||
               CallbackPrefix.SKIP_COMPLETION_NOTE.matches(callbackData) ||
               CallbackPrefix.BACK_TO_REMINDER.matches(callbackData);
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        log.debug("Маршрутизация callback для события: data='{}', userId={}", 
                callbackData, user.getId());
        
        // Маршрутизация к соответствующему handler
        if (CallbackPrefix.VIEW_EVENT_FROM_REMINDER.matches(callbackData) ||
            CallbackPrefix.BACK_TO_REMINDER.matches(callbackData)) {
            eventReminderNavigationHandler.handle(callbackQuery, user);
        } else if (CallbackPrefix.VIEW_EVENT.matches(callbackData)) {
            eventViewHandler.handle(callbackQuery, user);
        } else if (CallbackPrefix.EDIT_EVENT.matches(callbackData) ||
                   CallbackPrefix.EDIT_FIELD.matches(callbackData) ||
                   CallbackPrefix.EDIT_CANCEL.matches(callbackData)) {
            if (CallbackPrefix.EDIT_FIELD.matches(callbackData)) {
                eventFieldEditHandler.handle(callbackQuery, user);
            } else {
                eventEditHandler.handle(callbackQuery, user);
            }
        } else if (CallbackPrefix.DELETE_EVENT.matches(callbackData)) {
            eventDeleteHandler.handle(callbackQuery, user);
        } else if (CallbackPrefix.COMPLETE_EVENT.matches(callbackData) ||
                   CallbackPrefix.ADD_COMPLETION_NOTE.matches(callbackData) ||
                   CallbackPrefix.SKIP_COMPLETION_NOTE.matches(callbackData)) {
            eventCompletionHandler.handle(callbackQuery, user);
        }
    }
}
