package ru.golubyatnikov.family.calendar.bot.handler.callback.datetime;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackRoute;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.context.EditingContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.callback.CalendarNavigationService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.callback.EventListService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.callback.EventViewService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;

/**
 * Обработчик callback queries для навигации по календарю.
 * Делегирует обработку специализированным сервисам.
 * 
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NavigationCallbackHandler implements CallbackHandler {
    
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final ConversationStateService conversationStateService;
    private final EventService eventService;
    private final ConversationService conversationService;
    private final CalendarNavigationService navigationService;
    private final EventViewService eventViewService;
    private final EventListService eventListService;
    private final CallbackDataExtractionService callbackDataExtractionService;
    private final CallbackQueryService callbackQueryService;
    
    private Map<Predicate<String>, CallbackRoute> routes;
    
    /**
     * Инициализирует маршруты для обработки различных типов callback queries.
     * Порядок важен - более специфичные паттерны должны быть первыми.
     */
    @PostConstruct
    private void initRoutes() {
        routes = new LinkedHashMap<>();
        
        // Порядок важен - более специфичные паттерны должны быть первыми
        routes.put(data -> data.startsWith("back_to_calendar_"), this::handleBackToCalendar);
        routes.put(data -> data.startsWith("view_event_"), this::handleViewEvent);
        routes.put(data -> data.startsWith("repeat_event_"), this::handleRepeatEvent);
        routes.put(data -> data.startsWith("create_event_on_date_"), this::handleCreateEventOnDate);
        routes.put(data -> data.startsWith("view_events_on_date_"), this::handleViewEventsOnDate);
        routes.put(data -> data.startsWith("edit_event_from_calendar_"), this::handleEditEventFromCalendar);
        routes.put(data -> data.startsWith("edit_my_events_on_date_"), this::handleEditMyEventsOnDate);
        routes.put(data -> data.startsWith("delete_my_events_on_date_"), this::handleDeleteMyEventsOnDate);
        routes.put(CallbackPrefix.CALENDAR::matches, this::handleCalendarNavigation);
        routes.put(CallbackPrefix.DATE_ACTIONS::matches, this::handleDateActions);
    }
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.CALENDAR;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null || isReminderCallback(callbackData)) {
            return false;
        }
        
        return routes.keySet().stream()
                .anyMatch(predicate -> predicate.test(callbackData));
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(@NonNull CallbackQuery callbackQuery, @NonNull User user) throws Exception {
        String callbackData = callbackQuery.getData();
        
        if (callbackData == null) {
            throw new IllegalArgumentException("Callback data не может быть null");
        }
        
        if (isReminderCallback(callbackData)) {
            return;
        }
        
        CallbackQueryContext context = callbackDataExtractionService.extractContext(callbackQuery, user);
        routeCallback(callbackData, context);
    }
    
    /**
     * Проверяет, является ли callback от напоминания.
     * 
     * @param callbackData данные callback query
     * @return true, если callback от напоминания
     */
    private boolean isReminderCallback(@NonNull String callbackData) {
        return callbackData.startsWith("view_event_from_reminder_") || callbackData.startsWith("back_to_reminder_");
    }
    
    /**
     * Маршрутизирует callback к соответствующему обработчику.
     * Использует паттерн Strategy для выбора обработчика на основе предикатов.
     * 
     * @param callbackData данные callback query
     * @param context контекст обработки callback
     */
    private void routeCallback(String callbackData, CallbackQueryContext context) {
        routes.entrySet().stream()
            .filter(entry -> entry.getKey().test(callbackData))
            .findFirst()
            .ifPresentOrElse(
                entry -> entry.getValue().handle(callbackData, context),
                () -> log.warn("Не найден обработчик для callback: data='{}', userId={}",
                        callbackData, context.getUserId())
            );
    }
    
    // ==================== Route Handlers ====================
    
    /**
     * Обрабатывает возврат к календарю месяца.
     * 
     * @param callbackData данные callback query с годом и месяцем
     * @param ctx контекст обработки callback
     */
    private void handleBackToCalendar(@NonNull String callbackData, @NonNull CallbackQueryContext ctx) {
        String yearMonthStr = callbackData.substring("back_to_calendar_".length());
        String[] parts = yearMonthStr.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        
        InlineKeyboardMarkup keyboard = keyboardService.createViewCalendarKeyboard(year, month, ctx.user());
        String message = botMessageFormattingService.buildCalendarViewMessage();
        
        callbackQueryService.editMessageAndAnswer(ctx, message, keyboard, "");
    }
    
    /**
     * Обрабатывает просмотр события.
     * 
     * @param callbackData данные callback query с идентификатором события
     * @param ctx контекст обработки callback
     */
    private void handleViewEvent(String callbackData, @NonNull CallbackQueryContext ctx) {
        Long eventId = ctx.extractId("view_event_");
        eventViewService.viewEvent(eventId, ctx.user(), ctx.chatId(), ctx.messageId(), ctx.callbackQueryId());
    }
    
    /**
     * Обрабатывает повторение события.
     * 
     * @param callbackData данные callback query с идентификатором события
     * @param ctx контекст обработки callback
     */
    private void handleRepeatEvent(String callbackData, @NonNull CallbackQueryContext ctx) {
        Long eventId = ctx.extractId("repeat_event_");
        eventViewService.repeatEvent(eventId, ctx.user(), ctx.chatId(), ctx.messageId(), ctx.callbackQueryId());
    }
    
    /**
     * Обрабатывает создание события на выбранную дату.
     * 
     * @param callbackData данные callback query с датой
     * @param ctx контекст обработки callback
     */
    private void handleCreateEventOnDate(String callbackData, @NonNull CallbackQueryContext ctx) {
        LocalDate date = ctx.extractDate("create_event_on_date_");
        eventViewService.createEventOnDate(date, ctx.user(), ctx.chatId(), ctx.messageId(), ctx.callbackQueryId());
    }
    
    /**
     * Обрабатывает просмотр событий на выбранную дату.
     * 
     * @param callbackData данные callback query с датой
     * @param ctx контекст обработки callback
     */
    private void handleViewEventsOnDate(String callbackData, @NonNull CallbackQueryContext ctx) {
        LocalDate date = ctx.extractDate("view_events_on_date_");
        eventListService.viewEventsOnDate(date, ctx.user());
    }
    
    /**
     * Обрабатывает показ списка событий пользователя для редактирования.
     * 
     * @param callbackData данные callback query с датой
     * @param ctx контекст обработки callback
     */
    private void handleEditMyEventsOnDate(String callbackData, @NonNull CallbackQueryContext ctx) {
        LocalDate date = ctx.extractDate("edit_my_events_on_date_");
        eventListService.showMyEventsForEdit(date, ctx.user(), ctx.chatId(), ctx.messageId(), ctx.callbackQueryId());
    }
    
    /**
     * Обрабатывает показ списка событий пользователя для удаления.
     * 
     * @param callbackData данные callback query с датой
     * @param ctx контекст обработки callback
     */
    private void handleDeleteMyEventsOnDate(String callbackData, @NonNull CallbackQueryContext ctx) {
        LocalDate date = ctx.extractDate("delete_my_events_on_date_");
        eventListService.showMyEventsForDelete(date, ctx.user(), ctx.chatId(), ctx.messageId(), ctx.callbackQueryId());
    }
    
    /**
     * Обрабатывает редактирование события из календаря.
     * 
     * @param callbackData данные callback query с идентификатором события и датой
     * @param ctx контекст обработки callback
     */
    private void handleEditEventFromCalendar(@NonNull String callbackData, @NonNull CallbackQueryContext ctx) {
        String payload = callbackData.substring("edit_event_from_calendar_".length());
        String[] parts = payload.split("_", 2);
        
        if (parts.length != 2) {
            callbackQueryService.answerCallback(ctx.callbackQueryId(), "Ошибка при обработке запроса");
            return;
        }
        
        Long eventId = Long.parseLong(parts[0]);
        LocalDate sourceDate = LocalDate.parse(parts[1]);
        
        Event event = eventService.getEventById(eventId);
        
        if (!event.getUser().getId().equals(ctx.getUserId())) {
            callbackQueryService.answerCallback(ctx.callbackQueryId(), "У вас нет доступа");
            return;
        }
        
        conversationStateService.startEventEditingFromCalendar(ctx.getUserId(), eventId,
                ctx.chatId(), ctx.messageId(), sourceDate);
        
        String message = buildEditFieldSelectionMessage(event);
        InlineKeyboardMarkup keyboard = keyboardService.createEditFieldSelectionKeyboard(eventId, ctx.getUserId());
        
        callbackQueryService.editMessageAndAnswer(ctx, message, keyboard, "");
    }
    
    /**
     * Обрабатывает навигацию по календарю (переключение месяцев, выбор даты).
     * 
     * @param callbackData данные callback query
     * @param ctx контекст обработки callback
     */
    private void handleCalendarNavigation(@NonNull String callbackData, CallbackQueryContext ctx) {
        if (callbackData.equals("calendar_cancel")) {
            handleCalendarCancel(ctx);
            return;
        }
        
        String payload = CallbackPrefix.CALENDAR.extractPayload(callbackData);
        String[] parts = payload.split("-");
        
        if (parts.length == 3) {
            LocalDate selectedDate = LocalDate.parse(payload);
            handleDateSelection(selectedDate, ctx);

        } else if (parts.length == 2) {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            showMonthCalendar(year, month, ctx);
        }
    }
    
    /**
     * Обрабатывает действия с датой (просмотр, создание).
     * 
     * @param callbackData данные callback query
     * @param ctx контекст обработки callback
     */
    private void handleDateActions(String callbackData, @NonNull CallbackQueryContext ctx) {
        String payload = CallbackPrefix.DATE_ACTIONS.extractPayload(callbackData);
        
        String message = switch (payload) {
            case "view" -> "📅 Просмотр событий на дату";
            case "create" -> "➕ Создание нового события";
            default -> "Неизвестное действие";
        };
        
        callbackQueryService.editMessageAndAnswer(ctx, message, null, "");
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Обрабатывает отмену календаря.
     * 
     * @param ctx контекст обработки callback
     */
    private void handleCalendarCancel(@NonNull CallbackQueryContext ctx) {
        if (conversationStateService.isEditingEvent(ctx.getUserId())) {
            handleCancelEventEditing(ctx);

        } else {
            navigationService.handleBackFromEventCreation(ctx.user(), ctx.chatId(), ctx.messageId(), ctx.callbackQueryId());
        }
    }
    
    /**
     * Обрабатывает отмену редактирования события.
     * 
     * @param ctx контекст обработки callback
     */
    private void handleCancelEventEditing(@NonNull CallbackQueryContext ctx) {
        var context = conversationStateService.getEditingContext(ctx.getUserId());
        
        if (context == null || context.getEventId() == null) {
            conversationStateService.clearEventEditing(ctx.getUserId());
            return;
        }
        
        try {
            Event event = eventService.getEventById(context.getEventId());
            Integer editingMessageId = context.getMessageId();
            
            if (editingMessageId != null) {
                returnToEventCard(event, ctx.getUserId(), ctx.chatId(), editingMessageId);

            } else {
                eventService.sendOrUpdateEventMessage(event, ctx.chatId());
            }
            
            conversationStateService.clearEventEditing(ctx.getUserId());
            callbackQueryService.answerCallback(ctx.callbackQueryId(), 
                    CallbackMessageFormatter.actionCancelled("Редактирование"));
            
        } catch (Exception e) {
            log.error("Ошибка при отмене редактирования: userId={}, error={}", ctx.getUserId(), e.getMessage());
            conversationStateService.clearEventEditing(ctx.getUserId());
            throw new RuntimeException("Ошибка при отмене редактирования", e);
        }
    }
    
    /**
     * Возвращает пользователя к карточке события.
     * 
     * @param event событие для отображения
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     */
    private void returnToEventCard(@NonNull Event event, Long userId, Long chatId, Integer messageId) {

        int eventCount = eventService.getActiveEventsCount(event.getUser().getId());
        String eventMessage = botMessageFormattingService.buildEventMessageWithHeader(event, eventCount);
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
        
        try {
            messageService.editMessageText(chatId, messageId, eventMessage, keyboard);
            } catch (TelegramApiException e) {
            log.warn("Не удалось обновить сообщение о событии: eventId={}, messageId={}, error={}", 
                    event.getId(), messageId, e.getMessage());
            try {
                eventService.sendOrUpdateEventMessage(event, chatId);

            } catch (TelegramApiException ex) {
                log.error("Не удалось отправить сообщение о событии: eventId={}, error={}", 
                        event.getId(), ex.getMessage());
            }
        }
    }
    
    /**
     * Показывает календарь месяца.
     * 
     * @param year год
     * @param month месяц
     * @param ctx контекст обработки callback
     */
    private void showMonthCalendar(int year, int month, @NonNull CallbackQueryContext ctx) {
        boolean isCreatingEvent = conversationService.hasActiveDraft(ctx.getUserId());
        boolean isEditingEvent = conversationStateService.isEditingEvent(ctx.getUserId());
        Long editingEventId = null;
        
        if (isEditingEvent) {
            EditingContext context = conversationStateService.getEditingContext(ctx.getUserId());
            editingEventId = context != null ? context.getEventId() : null;
        }
        
        InlineKeyboardMarkup keyboard;
        if (isCreatingEvent || isEditingEvent) {
            keyboard = keyboardService.createCalendarKeyboard(year, month, ctx.user(), editingEventId);

        } else {
            keyboard = keyboardService.createViewCalendarKeyboard(year, month, ctx.user());
        }
        
        String message = (isCreatingEvent || isEditingEvent)
            ? botMessageFormattingService.buildSelectDateMessageWithHeader()
            : botMessageFormattingService.buildCalendarViewMessage();
        
        callbackQueryService.editMessageAndAnswer(ctx, message, keyboard, "");
    }
    
    /**
     * Обрабатывает выбор даты в календаре.
     * 
     * @param selectedDate выбранная дата
     * @param ctx контекст обработки callback
     */
    private void handleDateSelection(LocalDate selectedDate, @NonNull CallbackQueryContext ctx) {
        boolean isCreatingEvent = conversationService.hasActiveDraft(ctx.getUserId());
        
        if (isCreatingEvent) {
            handleDateSelectionForEventCreation(selectedDate, ctx);

        } else {
            navigationService.handleDateSelectionForCalendarView(
                selectedDate, ctx.user(), ctx.chatId(), ctx.messageId(), ctx.callbackQueryId()
            );
        }
    }
    
    /**
     * Обрабатывает выбор даты для создания события.
     * 
     * @param selectedDate выбранная дата
     * @param ctx контекст обработки callback
     */
    private void handleDateSelectionForEventCreation(LocalDate selectedDate, @NonNull CallbackQueryContext ctx) {
        eventViewService.showTimeSelectionForDate(selectedDate,
                ctx.user(), ctx.chatId(), ctx.messageId(), ctx.callbackQueryId()
        );
    }
    
    /**
     * Строит сообщение для выбора поля редактирования события.
     * 
     * @param event событие для редактирования
     * @return отформатированное сообщение
     */
    private @NonNull String buildEditFieldSelectionMessage(@NonNull Event event) {
        return "📝 " + bold("Редактирование события") + "\n\n"
                + botMessageFormattingService.buildEventMessage(event) + "\n\n"
                + "Выберите поле для редактирования:";
    }
}
