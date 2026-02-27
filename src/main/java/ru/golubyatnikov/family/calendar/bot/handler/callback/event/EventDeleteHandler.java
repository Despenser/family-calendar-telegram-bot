package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.domain.myevents.MyEventsPageService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.EventEditKeyboardFactory;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.myevents.MyEventsPageDisplayService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Actions.DELETE;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;

/**
 * Обработчик удаления события.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventDeleteHandler implements CallbackHandler {
    
    private final EventService eventService;
    private final MyEventsPageService myEventsPageService;
    private final TelegramMessageService messageService;
    private final CallbackQueryService callbackQueryService;
    private final CallbackDataExtractionService callbackDataExtractionService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final MyEventsPageDisplayService pageDisplayService;
    private final EventEditKeyboardFactory keyboardFactory;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.DELETE_EVENT;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        return CallbackPrefix.DELETE_EVENT.matches(callbackData);
    }
    
    @Override
    public void handle(@NonNull CallbackQuery callbackQuery, @NonNull User user) throws Exception {
        CallbackQueryContext context = callbackDataExtractionService.extractContext(callbackQuery, user);
        
        String payload = CallbackPrefix.DELETE_EVENT.extractPayload(context.callbackData());
        String[] parts = payload.split("_");
        Long eventId = Long.parseLong(parts[0]);
        Integer page = parts.length > 1 ? Integer.parseInt(parts[1]) : null;
        
        log.info("Удаление события ID={} пользователем ID={}, page={}", eventId, context.getUserId(), page);
        
        try {
            Event event = eventService.getEventById(eventId);
            LocalDate eventDate = event.getEventDate();
            Long familyId = user.getFamily().getId();
            
            eventService.deleteEvent(eventId, context.getUserId());
            
            // Если удаление из /my_events - возвращаемся к списку
            if (page != null) {
                handleDeleteFromMyEvents(context, page);
                return;
            }
            
            List<Event> allRemainingEvents = eventService.getEventsByDate(familyId, eventDate);
            List<Event> myRemainingEvents = filterUserEvents(allRemainingEvents, context.getUserId());
            
            LocalDate today = LocalDate.now(user.getZoneId());
            boolean isPastDate = eventDate.isBefore(today);
            
            updateUIAfterDeletion(context, eventDate, allRemainingEvents, myRemainingEvents, isPastDate, user);
            
        } catch (EventNotFoundException e) {
            log.error("Событие не найдено: eventId={}, userId={}", eventId, context.getUserId(), e);
            callbackQueryService.answerCallback(context, ERROR + " Событие не найдено");

        } catch (UnauthorizedAccessException e) {
            log.error("Нет прав на удаление события: eventId={}, userId={}", eventId, context.getUserId(), e);
            callbackQueryService.answerCallback(context, ERROR + " Нет прав на удаление");

        } catch (Exception e) {
            log.error("Ошибка при удалении события: eventId={}, userId={}, error={}", 
                     eventId, context.getUserId(), e.getMessage(), e);

            callbackQueryService.answerCallback(context, ERROR + " Ошибка при удалении");
        }
    }
    
    /**
     * Обрабатывает удаление события из постраничного списка /my_events.
     * Возвращает пользователя к списку событий.
     */
    private void handleDeleteFromMyEvents(CallbackQueryContext context, int page) {
        callbackQueryService.answerCallback(context, CallbackMessages.DELETED);
        
        try {
            // Получаем обновленный список событий пользователя
            Page<Event> eventsPage = myEventsPageService.getEventsPage(context.getUserId(), page);
            pageDisplayService.displayEventsPage(context.chatId(), context.messageId(), eventsPage);
            
        } catch (Exception e) {
            log.error("Ошибка при возврате к списку /my_events после удаления: {}", e.getMessage(), e);
            messageService.deleteMessageSilently(context.chatId(), context.messageId());
        }
    }
    
    /**
     * Обновляет UI после удаления события.
     */
    private void updateUIAfterDeletion(CallbackQueryContext context,
                                       LocalDate eventDate,
                                       @NonNull List<Event> allRemainingEvents,
                                       List<Event> myRemainingEvents,
                                       boolean isPastDate,
                                       User user) throws TelegramApiException {

        if (allRemainingEvents.isEmpty()) {
            handleNoEventsRemaining(context, eventDate, isPastDate);

        } else if (myRemainingEvents.isEmpty()) {
            handleNoUserEventsRemaining(context, eventDate, allRemainingEvents, isPastDate, user);

        } else {
            handleUserEventsRemaining(context, eventDate, myRemainingEvents);
        }
    }
    
    /**
     * Обрабатывает случай, когда не осталось событий на дату.
     */
    private void handleNoEventsRemaining(CallbackQueryContext context,
                                         LocalDate eventDate,
                                         boolean isPastDate) throws TelegramApiException {

        if (isPastDate) {
            callbackQueryService.answerCallback(context, CallbackMessages.DELETED + ". На эту дату больше нет событий");
            messageService.deleteMessageSilently(context.chatId(), context.messageId());

        } else {
            String message = botMessageFormattingService.buildCreateEventOnDateMessage(eventDate);
            InlineKeyboardMarkup keyboard = keyboardService.createCreateEventOnDateKeyboard(eventDate);
            messageService.editMessageText(context.chatId(), context.messageId(), message, keyboard);
            callbackQueryService.answerCallback(context, CallbackMessages.DELETED);
        }
    }
    
    /**
     * Обрабатывает случай, когда у пользователя не осталось своих событий.
     */
    private void handleNoUserEventsRemaining(CallbackQueryContext context,
                                             LocalDate eventDate,
                                             List<Event> allRemainingEvents,
                                             boolean isPastDate,
                                             User user) throws TelegramApiException {

        String message;
        InlineKeyboardMarkup keyboard;
        
        if (isPastDate) {
            message = botMessageFormattingService.buildDateEventsListMessage(eventDate, allRemainingEvents);
            keyboard = keyboardService.createDateEventsListKeyboard(eventDate, allRemainingEvents);

        } else {
            message = botMessageFormattingService.buildDateEventsManagementMessage(eventDate, allRemainingEvents);
            keyboard = keyboardService.createDateEventsManagementKeyboard(eventDate, allRemainingEvents, user);
        }
        
        messageService.editMessageText(context.chatId(), context.messageId(), message, keyboard);
        callbackQueryService.answerCallback(context, CallbackMessages.DELETED);
    }
    
    /**
     * Обрабатывает случай, когда у пользователя остались свои события.
     */
    private void handleUserEventsRemaining(@NonNull CallbackQueryContext context,
                                           LocalDate eventDate,
                                           @NonNull List<Event> myRemainingEvents) throws TelegramApiException {

        String message = DELETE + " Выберите событие для удаления:";
        InlineKeyboardMarkup keyboard = keyboardFactory.createDeleteEventListKeyboard(myRemainingEvents, eventDate);
        
        messageService.editMessageText(context.chatId(), context.messageId(), message, keyboard);
        callbackQueryService.answerCallback(context, CallbackMessages.DELETED);
    }
    
    /**
     * Фильтрует события пользователя.
     */
    private List<Event> filterUserEvents(@NonNull List<Event> events, Long userId) {
        return events.stream()
                .filter(e -> e.getUser().getId().equals(userId))
                .collect(Collectors.toList());
    }
}
