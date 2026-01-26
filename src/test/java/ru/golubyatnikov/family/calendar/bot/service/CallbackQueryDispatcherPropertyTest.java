package ru.golubyatnikov.family.calendar.bot.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property-based тесты для CallbackQueryDispatcher.
 * 
 * <p>Тесты проверяют свойство корректности маршрутизации: для любого callback data
 * с известным префиксом из CallbackPrefix enum, CallbackQueryDispatcher должен
 * маршрутизировать его к handler, у которого canHandle(callbackData) возвращает true.</p>
 * 
 * <p><b>Feature: code-quality-refactoring, Property 1: Callback Routing Correctness</b></p>
 * <p><b>Validates: Requirements 1.2</b></p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
class CallbackQueryDispatcherPropertyTest {
    
    /**
     * Property 1: Callback Routing Correctness
     * 
     * <p>Для любого callback data с известным префиксом из CallbackPrefix enum,
     * CallbackQueryDispatcher должен маршрутизировать его к handler,
     * у которого canHandle(callbackData) возвращает true.</p>
     * 
     * <p>Validates: Requirements 1.2</p>
     */
    @Property(tries = 100)
    void callbackRoutingCorrectness(
            @ForAll("validCallbackDataProvider") String callbackData) {
        
        // Создаём mock handlers для каждого префикса
        List<CallbackHandler> handlers = createMockHandlers();
        
        // Создаём mock сервисы
        TelegramMessageService messageService = mock(TelegramMessageService.class);
        UserService userService = mock(UserService.class);
        
        // Создаём mock FilterCallbackHandler
        ru.golubyatnikov.family.calendar.bot.handler.callback.FilterCallbackHandler filterCallbackHandler = 
            mock(ru.golubyatnikov.family.calendar.bot.handler.callback.FilterCallbackHandler.class);
        when(filterCallbackHandler.canHandle(anyString())).thenAnswer(invocation -> {
            String data = invocation.getArgument(0);
            return data != null && data.startsWith("filter_");
        });
        
        // Создаём диспетчер
        CallbackQueryDispatcher dispatcher = new CallbackQueryDispatcher(
            handlers, messageService, userService, filterCallbackHandler);
        
        // Находим handler через диспетчер
        Optional<CallbackHandler> foundHandler = dispatcher.findHandler(callbackData);
        
        // Проверяем, что найден handler
        assertThat(foundHandler)
            .as("Должен быть найден handler для callback data '%s'", callbackData)
            .isPresent();
        
        // Проверяем, что найденный handler действительно может обработать данный callback
        assertThat(foundHandler.get().canHandle(callbackData))
            .as("Найденный handler должен canHandle('%s') == true", callbackData)
            .isTrue();
        
        // Проверяем, что CallbackPrefix.fromCallbackData() находит соответствующий префикс
        CallbackPrefix prefix = CallbackPrefix.fromCallbackData(callbackData);
        assertThat(prefix)
            .as("CallbackPrefix.fromCallbackData('%s') должен найти префикс", callbackData)
            .isNotNull();
    }
    
    /**
     * Проверяет, что для игнорируемых callback data handler не ищется.
     */
    @Property(tries = 100)
    void ignoredCallbacksAreHandledCorrectly(
            @ForAll("ignoredCallbackDataProvider") String callbackData) {
        
        // Создаём mock handlers
        List<CallbackHandler> handlers = createMockHandlers();
        
        // Создаём mock сервисы
        TelegramMessageService messageService = mock(TelegramMessageService.class);
        UserService userService = mock(UserService.class);
        
        // Создаём mock FilterCallbackHandler
        ru.golubyatnikov.family.calendar.bot.handler.callback.FilterCallbackHandler filterCallbackHandler = 
            mock(ru.golubyatnikov.family.calendar.bot.handler.callback.FilterCallbackHandler.class);
        when(filterCallbackHandler.canHandle(anyString())).thenAnswer(invocation -> {
            String data = invocation.getArgument(0);
            return data != null && data.startsWith("filter_");
        });
        
        // Создаём диспетчер
        CallbackQueryDispatcher dispatcher = new CallbackQueryDispatcher(
            handlers, messageService, userService, filterCallbackHandler);
        
        // Проверяем, что isIgnored возвращает true
        assertThat(CallbackPrefix.isIgnored(callbackData))
            .as("CallbackPrefix.isIgnored('%s') должен вернуть true", callbackData)
            .isTrue();
    }
    
    /**
     * Проверяет, что для неизвестных callback data handler не находится.
     */
    @Property(tries = 100)
    void unknownCallbacksReturnEmptyHandler(
            @ForAll @StringLength(min = 1, max = 30) String randomData) {
        
        // Пропускаем данные, которые могут совпасть с известными префиксами
        if (CallbackPrefix.fromCallbackData(randomData) != null) {
            return;
        }
        
        // Создаём mock handlers
        List<CallbackHandler> handlers = createMockHandlers();
        
        // Создаём mock сервисы
        TelegramMessageService messageService = mock(TelegramMessageService.class);
        UserService userService = mock(UserService.class);
        
        // Создаём mock FilterCallbackHandler
        ru.golubyatnikov.family.calendar.bot.handler.callback.FilterCallbackHandler filterCallbackHandler = 
            mock(ru.golubyatnikov.family.calendar.bot.handler.callback.FilterCallbackHandler.class);
        when(filterCallbackHandler.canHandle(anyString())).thenAnswer(invocation -> {
            String data = invocation.getArgument(0);
            return data != null && data.startsWith("filter_");
        });
        
        // Создаём диспетчер
        CallbackQueryDispatcher dispatcher = new CallbackQueryDispatcher(
            handlers, messageService, userService, filterCallbackHandler);
        
        // Проверяем, что handler не найден
        Optional<CallbackHandler> foundHandler = dispatcher.findHandler(randomData);
        
        assertThat(foundHandler)
            .as("Не должен быть найден handler для неизвестного callback data '%s'", randomData)
            .isEmpty();
    }
    
    /**
     * Проверяет, что null callback data обрабатывается корректно.
     */
    @Example
    void nullCallbackDataReturnsEmptyHandler() {
        // Создаём mock handlers
        List<CallbackHandler> handlers = createMockHandlers();
        
        // Создаём mock сервисы
        TelegramMessageService messageService = mock(TelegramMessageService.class);
        UserService userService = mock(UserService.class);
        
        // Создаём mock FilterCallbackHandler
        ru.golubyatnikov.family.calendar.bot.handler.callback.FilterCallbackHandler filterCallbackHandler = 
            mock(ru.golubyatnikov.family.calendar.bot.handler.callback.FilterCallbackHandler.class);
        when(filterCallbackHandler.canHandle(anyString())).thenAnswer(invocation -> {
            String data = invocation.getArgument(0);
            return data != null && data.startsWith("filter_");
        });
        
        // Создаём диспетчер
        CallbackQueryDispatcher dispatcher = new CallbackQueryDispatcher(
            handlers, messageService, userService, filterCallbackHandler);
        
        // Проверяем, что handler не найден для null
        Optional<CallbackHandler> foundHandler = dispatcher.findHandler(null);
        
        assertThat(foundHandler)
            .as("Не должен быть найден handler для null callback data")
            .isEmpty();
    }
    
    /**
     * Проверяет, что hasHandler() возвращает корректные результаты.
     */
    @Property(tries = 100)
    void hasHandlerConsistentWithFindHandler(
            @ForAll("validCallbackDataProvider") String callbackData) {
        
        // Создаём mock handlers
        List<CallbackHandler> handlers = createMockHandlers();
        
        // Создаём mock сервисы
        TelegramMessageService messageService = mock(TelegramMessageService.class);
        UserService userService = mock(UserService.class);
        
        // Создаём mock FilterCallbackHandler
        ru.golubyatnikov.family.calendar.bot.handler.callback.FilterCallbackHandler filterCallbackHandler = 
            mock(ru.golubyatnikov.family.calendar.bot.handler.callback.FilterCallbackHandler.class);
        when(filterCallbackHandler.canHandle(anyString())).thenAnswer(invocation -> {
            String data = invocation.getArgument(0);
            return data != null && data.startsWith("filter_");
        });
        
        // Создаём диспетчер
        CallbackQueryDispatcher dispatcher = new CallbackQueryDispatcher(
            handlers, messageService, userService, filterCallbackHandler);
        
        // Проверяем консистентность hasHandler() и findHandler()
        boolean hasHandler = dispatcher.hasHandler(callbackData);
        Optional<CallbackHandler> foundHandler = dispatcher.findHandler(callbackData);
        
        assertThat(hasHandler)
            .as("hasHandler('%s') должен быть равен findHandler().isPresent()", callbackData)
            .isEqualTo(foundHandler.isPresent());
    }
    
    /**
     * Провайдер валидных callback data для тестирования.
     * Генерирует callback data для всех известных префиксов.
     */
    @Provide
    Arbitrary<String> validCallbackDataProvider() {
        return Arbitraries.of(
            // Дата и время
            "date_2026-01-16",
            "date_2025-12-31",
            "calendar_2026-01",
            "calendar_cancel",
            "hour_09",
            "hour_14",
            "time_09:30",
            "time_14:45",
            "time_back",
            "time_cancel",
            
            // События
            "view_event_123",
            "view_event_456789",
            "edit_event_123",
            "delete_event_123",
            "edit_field_title_123",
            "edit_field_date_456",
            "event_type_personal",
            "event_type_family",
            "skip_description",
            
            // Фильтры и корзина
            "filter_today",
            "filter_week",
            "trash_restore_123",
            "trash_delete_456",
            
            // Напоминания
            "setup_reminders_123",
            "toggle_reminder_123_MORNING",
            "confirm_reminders_123",
            "view_reminders_123",
            "delete_reminder_456",
            "reminder_morning_of_day",
            
            // Повторения
            "recurrence_daily",
            "recurrence_weekly",
            "series_action_this_only",
            "series_action_entire_series",
            
            // Дополнительные функции
            "date_actions_view",
            "date_actions_create",
            "attach_file_123",
            "checklist_add",
            "checklist_toggle_123",
            "comment_add",
            "add_completion_note_123",
            
            // Поиск
            "search_again:",
            
            // Создание события из текста
            "confirm_text_event:dGVzdA==",
            "cancel_text_event"
        );
    }
    
    /**
     * Провайдер игнорируемых callback data.
     */
    @Provide
    Arbitrary<String> ignoredCallbackDataProvider() {
        return Arbitraries.of(
            "calendar_ignore",
            "time_ignore"
        );
    }
    
    /**
     * Создаёт список mock handlers для всех типов callback.
     */
    private List<CallbackHandler> createMockHandlers() {
        List<CallbackHandler> handlers = new ArrayList<>();
        
        // DateTimeCallbackHandler
        handlers.add(createMockHandler(CallbackPrefix.DATE, callbackData ->
            CallbackPrefix.DATE.matches(callbackData) ||
            CallbackPrefix.HOUR.matches(callbackData) ||
            (callbackData.startsWith("time_") && callbackData.contains(":")) ||
            CallbackPrefix.TIME_BACK.matches(callbackData) ||
            CallbackPrefix.TIME_CANCEL.matches(callbackData)
        ));
        
        // NavigationCallbackHandler
        handlers.add(createMockHandler(CallbackPrefix.CALENDAR, callbackData ->
            CallbackPrefix.CALENDAR.matches(callbackData)
        ));
        
        // EventCallbackHandler
        handlers.add(createMockHandler(CallbackPrefix.VIEW_EVENT, callbackData ->
            CallbackPrefix.VIEW_EVENT.matches(callbackData) ||
            CallbackPrefix.EDIT_EVENT.matches(callbackData) ||
            CallbackPrefix.DELETE_EVENT.matches(callbackData) ||
            CallbackPrefix.EDIT_FIELD.matches(callbackData)
        ));
        
        // EventTypeCallbackHandler
        handlers.add(createMockHandler(CallbackPrefix.EVENT_TYPE, callbackData ->
            CallbackPrefix.EVENT_TYPE.matches(callbackData) ||
            CallbackPrefix.SKIP_DESCRIPTION.matches(callbackData)
        ));
        
        // FilterCallbackHandler (через FilterCommandHandler)
        handlers.add(createMockHandler(CallbackPrefix.FILTER, callbackData ->
            CallbackPrefix.FILTER.matches(callbackData)
        ));
        
        // TrashCallbackHandler (через TrashCommandHandler)
        handlers.add(createMockHandler(CallbackPrefix.TRASH, callbackData ->
            CallbackPrefix.TRASH.matches(callbackData)
        ));
        
        // ReminderCallbackHandler
        handlers.add(createMockHandler(CallbackPrefix.SETUP_REMINDERS, callbackData ->
            CallbackPrefix.SETUP_REMINDERS.matches(callbackData) ||
            CallbackPrefix.TOGGLE_REMINDER.matches(callbackData) ||
            CallbackPrefix.CONFIRM_REMINDERS.matches(callbackData) ||
            CallbackPrefix.VIEW_REMINDERS.matches(callbackData) ||
            CallbackPrefix.DELETE_REMINDER.matches(callbackData) ||
            CallbackPrefix.REMINDER.matches(callbackData)
        ));
        
        // RecurrenceCallbackHandler
        handlers.add(createMockHandler(CallbackPrefix.RECURRENCE, callbackData ->
            CallbackPrefix.RECURRENCE.matches(callbackData) ||
            CallbackPrefix.SERIES_ACTION.matches(callbackData)
        ));
        
        // AttachmentCallbackHandler
        handlers.add(createMockHandler(CallbackPrefix.ATTACH_FILE, callbackData ->
            CallbackPrefix.ATTACH_FILE.matches(callbackData)
        ));
        
        // ChecklistCallbackHandler
        handlers.add(createMockHandler(CallbackPrefix.CHECKLIST, callbackData ->
            CallbackPrefix.CHECKLIST.matches(callbackData)
        ));
        
        // CommentCallbackHandler
        handlers.add(createMockHandler(CallbackPrefix.COMMENT, callbackData ->
            CallbackPrefix.COMMENT.matches(callbackData) ||
            CallbackPrefix.ADD_COMPLETION_NOTE.matches(callbackData)
        ));
        
        // DateActionsCallbackHandler (через NavigationCallbackHandler)
        handlers.add(createMockHandler(CallbackPrefix.DATE_ACTIONS, callbackData ->
            CallbackPrefix.DATE_ACTIONS.matches(callbackData)
        ));
        
        // TextEventCallbackHandler
        handlers.add(createMockHandler(CallbackPrefix.CONFIRM_TEXT_EVENT, callbackData ->
            CallbackPrefix.CONFIRM_TEXT_EVENT.matches(callbackData) ||
            CallbackPrefix.CANCEL_TEXT_EVENT.matches(callbackData)
        ));
        
        // SearchCommandHandler (для callback "search_again:")
        handlers.add(createMockHandler(CallbackPrefix.SEARCH_AGAIN, callbackData ->
            CallbackPrefix.SEARCH_AGAIN.matches(callbackData)
        ));
        
        return handlers;
    }
    
    /**
     * Создаёт mock handler с заданным предикатом canHandle.
     */
    private CallbackHandler createMockHandler(CallbackPrefix prefix, 
                                              java.util.function.Predicate<String> canHandlePredicate) {
        CallbackHandler handler = mock(CallbackHandler.class);
        when(handler.getPrefix()).thenReturn(prefix);
        when(handler.canHandle(anyString())).thenAnswer(invocation -> {
            String callbackData = invocation.getArgument(0);
            return canHandlePredicate.test(callbackData);
        });
        return handler;
    }
}
