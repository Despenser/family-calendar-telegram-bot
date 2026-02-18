package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;

import java.util.List;
import java.util.Map;

/**
 * Маршрутизатор callback queries для операций с событиями.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@Slf4j
public class EventCallbackRouter implements CallbackHandler {
    
    private final Map<CallbackPrefix, CallbackHandler> handlerMap;
    private final List<CallbackPrefix> supportedPrefixes;
    
    public EventCallbackRouter(
            EventViewHandler eventViewHandler,
            EventEditHandler eventEditHandler,
            EventDeleteHandler eventDeleteHandler,
            EventCompletionHandler eventCompletionHandler,
            EventFieldEditHandler eventFieldEditHandler,
            EventReminderNavigationHandler eventReminderNavigationHandler) {
        
        // Маппинг префиксов на handlers
        this.handlerMap = Map.of(
            CallbackPrefix.VIEW_EVENT, eventViewHandler,
            CallbackPrefix.VIEW_EVENT_FROM_REMINDER, eventReminderNavigationHandler,
            CallbackPrefix.BACK_TO_REMINDER, eventReminderNavigationHandler,
            CallbackPrefix.EDIT_EVENT, eventEditHandler,
            CallbackPrefix.EDIT_FIELD, eventFieldEditHandler,
            CallbackPrefix.EDIT_CANCEL, eventEditHandler,
            CallbackPrefix.DELETE_EVENT, eventDeleteHandler,
            CallbackPrefix.COMPLETE_EVENT, eventCompletionHandler,
            CallbackPrefix.ADD_COMPLETION_NOTE, eventCompletionHandler,
            CallbackPrefix.SKIP_COMPLETION_NOTE, eventCompletionHandler
        );
        
        this.supportedPrefixes = List.copyOf(handlerMap.keySet());
    }
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.VIEW_EVENT;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        
        // Проверяем более специфичные префиксы первыми (по длине префикса)
        return supportedPrefixes.stream()
                .anyMatch(prefix -> prefix.matches(callbackData));
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(@NonNull CallbackQuery callbackQuery, @NonNull User user) throws Exception {
        String callbackData = callbackQuery.getData();
        
        if (callbackData == null) {
            log.warn("Получен callback с null данными от пользователя userId={}", user.getId());
            throw new IllegalArgumentException("Callback data не может быть null");
        }
        
        log.debug("Маршрутизация callback для события: data='{}', userId={}", 
                callbackData, user.getId());
        
        CallbackHandler handler = supportedPrefixes.stream()
                .sorted((p1, p2) -> Integer.compare(p2.getPrefix().length(), p1.getPrefix().length()))
                .filter(prefix -> prefix.matches(callbackData))
                .findFirst()
                .map(handlerMap::get)
                .orElseThrow(() -> new IllegalStateException("Не найден handler для callback: " + callbackData));
        
        handler.handle(callbackQuery, user);
    }
}
