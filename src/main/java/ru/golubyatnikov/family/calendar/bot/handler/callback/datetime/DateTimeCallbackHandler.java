package ru.golubyatnikov.family.calendar.bot.handler.callback.datetime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.context.EditingContext;
import ru.golubyatnikov.family.calendar.bot.model.context.HourSelectionContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.domain.user.UserService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.callback.CalendarNavigationService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.callback.EventEditingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Обработчик callback queries для выбора даты и времени события.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DateTimeCallbackHandler implements CallbackHandler {
    
    private final ConversationService conversationService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final ConversationStateService conversationStateService;
    private final EventService eventService;
    private final UserService userService;
    private final EventEditingService eventEditingService;
    private final CalendarNavigationService navigationService;
    private final DateTimeFormattingService dateTimeFormattingService;
    private final CallbackDataExtractionService callbackDataExtractionService;
    private final CallbackQueryService callbackQueryService;
    
    /**
     * Возвращает префикс callback, который обрабатывает этот handler.
     * 
     * @return префикс DATE
     */
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.DATE;
    }
    
    /**
     * Определяет, может ли обработчик обработать данный callback.
     * Обрабатывает callback для выбора даты, часа, времени и управления процессом выбора.
     * 
     * @param callbackData данные callback query
     * @return true, если обработчик может обработать callback
     */
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        
        return CallbackPrefix.DATE.matches(callbackData) ||
               CallbackPrefix.HOUR.matches(callbackData) ||
               isTimeWithMinutes(callbackData) ||
               CallbackPrefix.TIME_BACK.matches(callbackData) ||
               CallbackPrefix.TIME_TO_CALENDAR.matches(callbackData) ||
               CallbackPrefix.TIME_CANCEL.matches(callbackData);
    }
    
    /**
     * Проверяет, является ли callback data выбором времени с минутами (формат time_HH:MM).
     * 
     * @param callbackData строка callback data
     * @return true, если это выбор времени с минутами
     */
    private boolean isTimeWithMinutes(@NonNull String callbackData) {
        return CallbackPrefix.TIME.matches(callbackData) && callbackData.contains(":");
    }
    
    /**
     * Обрабатывает callback query для выбора даты и времени события.
     * Маршрутизирует запрос к соответствующему обработчику в зависимости от типа callback.
     * 
     * @param callbackQuery объект callback query от Telegram
     * @param user пользователь, инициировавший callback
     *
     * @throws Exception если произошла ошибка при обработке callback
     */
    @Override
    @HandleCallbackErrors
    public void handle(@NonNull CallbackQuery callbackQuery, @NonNull User user) throws Exception {
        CallbackQueryContext context = callbackDataExtractionService.extractContext(callbackQuery, user);
        String callbackData = context.callbackData();
        
        if (CallbackPrefix.DATE.matches(callbackData)) {
            handleDateSelection(context);

        } else if (CallbackPrefix.HOUR.matches(callbackData)) {
            handleHourSelection(context);

        } else if (isTimeWithMinutes(callbackData)) {
            handleTimeSelection(context);

        } else if (CallbackPrefix.TIME_BACK.matches(callbackData)) {
            handleTimeBack(context);

        } else if (CallbackPrefix.TIME_TO_CALENDAR.matches(callbackData)) {
            handleTimeToCalendar(context);

        } else if (CallbackPrefix.TIME_CANCEL.matches(callbackData)) {
            handleTimeCancel(context);
        }
    }
    
    /**
     * Обрабатывает выбор даты из календаря.
     * Обновляет черновик события или существующее событие и показывает выбор часа.
     * Применяет фильтрацию прошедших часов для сегодняшнего дня.
     * 
     * @param context контекст callback query
     *
     * @throws RuntimeException если произошла ошибка при выборе даты
     */
    private void handleDateSelection(@NonNull CallbackQueryContext context) {
        String dateStr = CallbackPrefix.DATE.extractPayload(context.callbackData());
        LocalDate date = LocalDate.parse(dateStr);
        
        User user = userService.findById(context.getUserId())
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден: " + context.getUserId()));
        
        if (conversationStateService.isEditingEvent(context.getUserId())) {
            eventEditingService.updateEventDate(context.getUserId(), date, context.chatId(), context.callbackQueryId());

        } else {
            handleDateSelectionForNewEvent(date, user, context);
        }
    }
    
    /**
     * Обрабатывает выбор даты для нового события.
     * 
     * @param date выбранная дата
     * @param user пользователь, создающий событие
     * @param context контекст callback query
     */
    private void handleDateSelectionForNewEvent(LocalDate date,
                                                User user,
                                                @NonNull CallbackQueryContext context) {

        conversationService.updateEventDate(context.getUserId(), date);

        InlineKeyboardMarkup keyboard = keyboardService.createFilteredHourSelectionKeyboard(date, user);
        
        // Проверяем наличие доступных часов
        if (keyboard.getKeyboard().size() <= 2) {
            showTooLateMessage(context, date);
            return;
        }
        
        String formattedDate = dateTimeFormattingService.formatDate(date);
        String message = botMessageFormattingService.buildDateSelectedMessage(formattedDate);
        
        try {
            callbackQueryService.editMessageAndAnswer(context, message, keyboard,
                    CallbackMessageFormatter.itemSelected("Дата"));

        } catch (Exception e) {
            log.error("Ошибка при выборе даты: userId={}, date={}, error={}", context.getUserId(), date, e.getMessage());
            throw new RuntimeException("Ошибка при выборе даты", e);
        }
    }
    
    /**
     * Показывает сообщение о том, что слишком поздно создавать событие на сегодня.
     * 
     * @param context контекст callback query
     * @param date дата, для которой нет доступных часов
     *
     * @throws RuntimeException если произошла ошибка при отображении сообщения
     */
    private void showTooLateMessage(@NonNull CallbackQueryContext context, LocalDate date) {
        String message = botMessageFormattingService.buildTooLateForTodayMessage();
        
        try {
            callbackQueryService.editMessageAndAnswer(context, message, null, CallbackMessages.TOO_LATE_TODAY);
            log.warn("Попытка создать событие на сегодня, когда все часы прошли: userId={}, date={}", 
                    context.getUserId(), date);

        } catch (Exception e) {
            log.error("Ошибка при отображении сообщения о слишком позднем времени: userId={}, date={}, error={}", 
                     context.getUserId(), date, e.getMessage());

            throw new RuntimeException("Ошибка при отображении сообщения", e);
        }
    }
    
    /**
     * Обрабатывает выбор часа.
     * Показывает выбор минут для выбранного часа с фильтрацией прошедших минут.
     * 
     * @param context контекст callback query
     *
     * @throws RuntimeException если произошла ошибка при выборе часа
     */
    private void handleHourSelection(@NonNull CallbackQueryContext context) {
        String hourStr = CallbackPrefix.HOUR.extractPayload(context.callbackData());
        int hour = Integer.parseInt(hourStr);
        
        User user = userService.findById(context.getUserId())
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден: " + context.getUserId()));
        
        HourSelectionContext hourContext = buildHourSelectionContext(context.getUserId());
        
        InlineKeyboardMarkup keyboard;
        if (hourContext.isEditingEvent()) {
            keyboard = keyboardService.createFilteredMinuteSelectionKeyboard(
                hour, hourContext.eventDate(), user, hourContext.editingEventId());

        } else {
            keyboard = keyboardService.createFilteredMinuteSelectionKeyboard(
                hour, hourContext.eventDate(), user);
        }
        
        // Проверяем наличие доступных минут
        if (keyboard.getKeyboard().size() <= 2) {
            showSelectNextHourMessage(context, hour, hourContext);
            return;
        }
        
        String message = hourContext.isEditingEvent()
            ? botMessageFormattingService.buildEditTimeHourSelectedMessage(hour)
            : botMessageFormattingService.buildHourSelectedMessage(hour);
        
        try {
            callbackQueryService.editMessageAndAnswer(context, message, keyboard,
                    CallbackMessageFormatter.itemSelected("Час"));

        } catch (Exception e) {
            log.error("Ошибка при выборе часа: hour={}, error={}", hour, e.getMessage());
            throw new RuntimeException("Ошибка при выборе часа", e);
        }
    }
    
    /**
     * Строит контекст выбора часа.
     * 
     * @param userId идентификатор пользователя
     * @return контекст выбора часа с информацией о дате события и режиме редактирования
     */
    private @NonNull HourSelectionContext buildHourSelectionContext(Long userId) {
        boolean isEditingEvent = conversationStateService.isEditingEvent(userId);
        LocalDate eventDate;
        Long editingEventId = null;
        
        if (isEditingEvent) {
            EditingContext context = conversationStateService.getEditingContext(userId);
            Event event = eventService.getEventById(context.getEventId());
            eventDate = event.getEventDate();
            editingEventId = context.getEventId();

        } else {
            Event draft = conversationService.getActiveDraft(userId);
            eventDate = draft.getEventDate();
        }
        
        return HourSelectionContext.builder()
                .eventDate(eventDate)
                .editingEventId(editingEventId)
                .isEditingEvent(isEditingEvent)
                .build();
    }
    
    /**
     * Показывает сообщение о необходимости выбрать следующий час.
     * 
     * @param context контекст callback query
     * @param hour час, для которого все минуты прошли
     * @param hourContext контекст выбора часа
     *
     * @throws RuntimeException если произошла ошибка при отображении сообщения
     */
    private void showSelectNextHourMessage(@NonNull CallbackQueryContext context,
                                           int hour,
                                           @NonNull HourSelectionContext hourContext) {

        String message = botMessageFormattingService.buildSelectNextHourMessage(hour);
        User user = userService.findById(context.getUserId())
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден: " + context.getUserId()));
        
        try {
            InlineKeyboardMarkup filteredHourSelectionKeyboard;
            if (hourContext.isEditingEvent()) {
                filteredHourSelectionKeyboard = keyboardService.createFilteredHourSelectionKeyboard(
                        hourContext.eventDate(),
                        user,
                        hourContext.editingEventId()
                );

            } else {
                filteredHourSelectionKeyboard = keyboardService.createFilteredHourSelectionKeyboard(
                        hourContext.eventDate(),
                        user);
            }

            callbackQueryService.editMessageAndAnswer(context, message, filteredHourSelectionKeyboard,
                    CallbackMessages.SELECT_NEXT_HOUR);

        } catch (Exception e) {
            log.error("Ошибка при отображении сообщения о прошедших минутах: userId={}, hour={}, error={}", 
                     context.getUserId(), hour, e.getMessage());

            throw new RuntimeException("Ошибка при отображении сообщения", e);
        }
    }
    
    /**
     * Обрабатывает выбор времени (час и минуты).
     * Обновляет черновик или существующее событие и показывает выбор типа события.
     * 
     * @param context контекст callback query
     *
     * @throws RuntimeException если произошла ошибка при выборе времени
     */
    private void handleTimeSelection(@NonNull CallbackQueryContext context) {
        String timeStr = CallbackPrefix.TIME.extractPayload(context.callbackData());
        LocalTime time = LocalTime.parse(timeStr);
        
        if (conversationStateService.isEditingEvent(context.getUserId())) {
            eventEditingService.updateEventTime(context.getUserId(), time, context.chatId(), context.callbackQueryId());

        } else {
            handleTimeSelectionForNewEvent(time, context);
        }
    }
    
    /**
     * Обрабатывает выбор времени для нового события.
     * 
     * @param time выбранное время
     * @param context контекст callback query
     */
    private void handleTimeSelectionForNewEvent(LocalTime time, @NonNull CallbackQueryContext context) {
        Event draft = conversationService.updateEventTime(context.getUserId(), time);
        
        boolean isRepeatEvent = draft.getTitle() != null
                && !draft.getTitle().isBlank()
                && draft.getIsPersonal() != null;
        
        if (isRepeatEvent) {
            completeEventRepeat(draft, context);

        } else {
            showEventTypeSelection(time, context);
        }
    }
    
    /**
     * Завершает повторение события.
     * 
     * @param draft черновик события с данными для повторения
     * @param context контекст callback query
     *
     * @throws RuntimeException если произошла ошибка при завершении повторения события
     */
    private void completeEventRepeat(@NonNull Event draft, @NonNull CallbackQueryContext context) {
        Event completedEvent = conversationService.completeEventCreation(context.getUserId(), draft.getDescription());
        
        String eventMessage = botMessageFormattingService.buildEventCreatedMessage(completedEvent);
        InlineKeyboardMarkup eventKeyboard = keyboardService.createEventActionsKeyboard(completedEvent.getId());
        
        try {
            callbackQueryService.editMessageAndAnswer(context, eventMessage,
                    eventKeyboard, "✅ Событие создано");
            
        } catch (Exception e) {
            log.error("Ошибка при завершении повторения события: userId={}, error={}", context.getUserId(), e.getMessage());
            throw new RuntimeException("Ошибка при завершении повторения события", e);
        }
    }
    
    /**
     * Показывает выбор типа события.
     * 
     * @param time выбранное время события
     * @param context контекст callback query
     *
     * @throws RuntimeException если произошла ошибка при выборе времени
     */
    private void showEventTypeSelection(LocalTime time, @NonNull CallbackQueryContext context) {
        String formattedTime = dateTimeFormattingService.formatTime(time);
        InlineKeyboardMarkup typeKeyboard = keyboardService.createEventTypeSelectionKeyboard();
        String message = botMessageFormattingService.buildTimeSelectedMessage(formattedTime);
        
        try {
            callbackQueryService.editMessageAndAnswer(context, message, typeKeyboard,
                    CallbackMessageFormatter.itemSelected("Время"));

        } catch (Exception e) {
            log.error("Ошибка при выборе времени: userId={}, time={}, error={}", context.getUserId(), time, e.getMessage());
            throw new RuntimeException("Ошибка при выборе времени", e);
        }
    }
    
    /**
     * Обрабатывает возврат к выбору часа.
     * Пересчитывает доступные часы на основе текущего времени пользователя.
     */
    private void handleTimeBack(@NonNull CallbackQueryContext context) {
        User user = userService.findById(context.getUserId())
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден: " + context.getUserId()));
        
        HourSelectionContext hourContext = buildHourSelectionContext(context.getUserId());
        
        InlineKeyboardMarkup keyboard;
        if (hourContext.isEditingEvent()) {
            keyboard = keyboardService.createFilteredHourSelectionKeyboard(
                hourContext.eventDate(), user, hourContext.editingEventId());
                
        } else {
            keyboard = keyboardService.createFilteredHourSelectionKeyboard(
                hourContext.eventDate(), user);
        }
        
        String message;
        if (hourContext.isEditingEvent()) {
            message = botMessageFormattingService.buildEditTimeSelectHourMessage();
            
        } else {
            // Для создания нового события показываем сообщение с выбранной датой
            String formattedDate = dateTimeFormattingService.formatDate(hourContext.eventDate());
            message = botMessageFormattingService.buildDateSelectedMessage(formattedDate);
        }
        
        try {
            messageService.editMessageText(context.chatId(), context.messageId(), message, keyboard);
            callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);

        } catch (TelegramApiException e) {
            log.error("Ошибка при возврате к выбору часа: userId={}, error={}", context.getUserId(), e.getMessage());
            throw new RuntimeException("Ошибка при возврате к выбору часа", e);
        }
    }
    
    /**
     * Обрабатывает возврат к календарю выбора даты.
     * Не отменяет создание события, только возвращает к выбору даты.
     */
    private void handleTimeToCalendar(@NonNull CallbackQueryContext context) {
        User user = userService.findById(context.getUserId())
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден: " + context.getUserId()));
        
        navigationService.handleBackFromEventCreation(user, context.chatId(),
                context.messageId(), context.callbackQueryId());
    }
    
    /**
     * Обрабатывает отмену выбора времени.
     * Поведение зависит от контекста: создание нового события или редактирование существующего.
     */
    private void handleTimeCancel(@NonNull CallbackQueryContext context) {
        if (conversationStateService.isEditingEvent(context.getUserId())) {
            eventEditingService.cancelEditing(context.getUserId(), context.chatId(), context.callbackQueryId());

        } else {
            User user = userService.findById(context.getUserId()).orElseThrow();
            navigationService.handleCancelEventCreation(user, context.chatId(),
                    context.messageId(), context.callbackQueryId());
        }
    }
}
