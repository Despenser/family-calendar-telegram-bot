package ru.golubyatnikov.family.calendar.bot.handler.callback.myevents;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventQueryService;
import ru.golubyatnikov.family.calendar.bot.service.domain.myevents.MyEventsPageService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.MyEventsPageKeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.myevents.MyEventsPageDisplayService;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;

/**
 * Обработчик callback-запросов для постраничного списка событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-26
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MyEventsCallbackHandler implements CallbackHandler {
    
    private final MyEventsPageService pageService;
    private final EventQueryService eventQueryService;
    private final MyEventsPageDisplayService pageDisplayService;
    private final BotMessageFormattingService eventFormattingService;
    private final MyEventsPageKeyboardService keyboardService;
    private final TelegramMessageService messageService;
    
    @Override
    public CallbackPrefix getPrefix() {
        // Этот обработчик обрабатывает несколько префиксов
        return null;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        return CallbackPrefix.MY_EVENTS_PAGE.matches(callbackData) ||
               CallbackPrefix.MY_EVENTS_VIEW.matches(callbackData) ||
               CallbackPrefix.MY_EVENTS_BACK.matches(callbackData);
    }
    
    @Override
    public void handle(@NonNull CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        
        try {
            if (CallbackPrefix.MY_EVENTS_PAGE.matches(callbackData)) {
                handlePageNavigation(chatId, messageId, callbackData, user);
                
            } else if (CallbackPrefix.MY_EVENTS_VIEW.matches(callbackData)) {
                handleEventView(chatId, messageId, callbackData, user);
                
            } else if (CallbackPrefix.MY_EVENTS_BACK.matches(callbackData)) {
                handleBackToList(chatId, messageId, callbackData, user);
            }

            messageService.answerCallbackQuery(callbackQuery.getId(), "");
            
        } catch (Exception e) {
            log.error("Ошибка при обработке callback для /my_events: callbackData={}, userId={}, error={}", 
                     callbackData, user.getId(), e.getMessage(), e);
            
            messageService.answerCallbackQuery(
                callbackQuery.getId(), 
                ERROR + " Произошла ошибка. Попробуйте еще раз."
            );
        }
    }
    
    /**
     * Обрабатывает навигацию по страницам списка событий.
     * 
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackData данные callback
     * @param user пользователь
     */
    private void handlePageNavigation(Long chatId, Integer messageId, String callbackData, @NonNull User user) {
        String payload = CallbackPrefix.MY_EVENTS_PAGE.extractPayload(callbackData);
        int page = Integer.parseInt(payload);
        
        log.debug("Переход на страницу {} списка событий для пользователя ID={}", page, user.getId());
        
        Page<Event> eventsPage = pageService.getEventsPage(user.getId(), page);
        
        try {
            pageDisplayService.displayEventsPage(chatId, messageId, eventsPage);
            
        } catch (Exception e) {
            log.error("Ошибка при навигации по страницам: page={}, userId={}, error={}", 
                     page, user.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Обрабатывает просмотр деталей события.
     * 
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackData данные callback
     * @param user пользователь
     */
    private void handleEventView(Long chatId, Integer messageId, String callbackData, User user) {
        String payload = CallbackPrefix.MY_EVENTS_VIEW.extractPayload(callbackData);
        String[] parts = payload.split("_");
        
        if (parts.length != 2) {
            log.error("Неверный формат payload для MY_EVENTS_VIEW: {}", payload);
            return;
        }
        
        Long eventId = Long.parseLong(parts[0]);
        int page = Integer.parseInt(parts[1]);
        
        log.debug("Просмотр деталей события ID={} для пользователя ID={}, страница={}", 
                 eventId, user.getId(), page);
        
        try {
            Event event = eventQueryService.getEventById(eventId);
            
            String eventMessage = eventFormattingService.buildEventMessage(event);
            InlineKeyboardMarkup keyboard = keyboardService.createEventDetailsKeyboardWithBackToList(
                event, 
                user.getId(), 
                page
            );
            
            messageService.editMessageText(chatId, messageId, eventMessage, keyboard);
            
        } catch (Exception e) {
            log.error("Ошибка при просмотре деталей события: eventId={}, userId={}, error={}", 
                     eventId, user.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Обрабатывает возврат к списку событий.
     * 
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackData данные callback
     * @param user пользователь
     */
    private void handleBackToList(Long chatId, Integer messageId, String callbackData, @NonNull User user) {
        String payload = CallbackPrefix.MY_EVENTS_BACK.extractPayload(callbackData);
        int page = Integer.parseInt(payload);
        
        log.debug("Возврат к списку событий на страницу {} для пользователя ID={}", page, user.getId());
        
        Page<Event> eventsPage = pageService.getEventsPage(user.getId(), page);
        
        try {
            pageDisplayService.displayEventsPage(chatId, messageId, eventsPage);
            
        } catch (Exception e) {
            log.error("Ошибка при возврате к списку: page={}, userId={}, error={}", 
                     page, user.getId(), e.getMessage(), e);
        }
    }
}
