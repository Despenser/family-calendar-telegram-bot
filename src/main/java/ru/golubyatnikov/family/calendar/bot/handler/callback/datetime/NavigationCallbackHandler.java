package ru.golubyatnikov.family.calendar.bot.handler.callback.datetime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

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
    private final ConversationStateService conversationStateService;
    private final ru.golubyatnikov.family.calendar.bot.service.event.EventService eventService;
    private final ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationService conversationService;
    private final ru.golubyatnikov.family.calendar.bot.repository.EventRepository eventRepository;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.CALENDAR;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        
        // Не обрабатываем callback'и от напоминаний - они обрабатываются в EventReminderNavigationHandler
        if (callbackData.startsWith("view_event_from_reminder_") || 
            callbackData.startsWith("back_to_reminder_")) {
            return false;
        }
        
        return CallbackPrefix.CALENDAR.matches(callbackData) ||
               CallbackPrefix.DATE_ACTIONS.matches(callbackData) ||
               callbackData.startsWith("view_event_") ||
               callbackData.startsWith("create_event_on_date_") ||
               callbackData.startsWith("view_events_on_date_") ||
               callbackData.startsWith("edit_my_events_on_date_") ||
               callbackData.startsWith("edit_event_from_calendar_") ||
               callbackData.startsWith("delete_my_events_on_date_") ||
               callbackData.startsWith("repeat_event_");
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
        
        // Сначала проверяем специфичные callback'и от напоминаний (они не должны обрабатываться здесь)
        if (callbackData.startsWith("view_event_from_reminder_") || 
            callbackData.startsWith("back_to_reminder_")) {
            log.debug("Callback от напоминания, пропускаем обработку в NavigationCallbackHandler: data='{}'", callbackData);
            return;
        }
        
        if (callbackData.startsWith("view_event_")) {
            handleViewEvent(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (callbackData.startsWith("repeat_event_")) {
            handleRepeatEvent(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (callbackData.startsWith("create_event_on_date_")) {
            handleCreateEventOnDate(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (callbackData.startsWith("view_events_on_date_")) {
            handleViewEventsOnDate(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (callbackData.startsWith("edit_event_from_calendar_")) {
            handleEditEventFromCalendar(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (callbackData.startsWith("edit_my_events_on_date_")) {
            handleEditMyEventsOnDate(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (callbackData.startsWith("delete_my_events_on_date_")) {
            handleDeleteMyEventsOnDate(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.CALENDAR.matches(callbackData)) {
            handleCalendarNavigation(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.DATE_ACTIONS.matches(callbackData)) {
            handleDateActions(callbackData, user, chatId, messageId, callbackQueryId);
        }
    }
    
    /**
     * Обрабатывает навигацию по календарю (переключение месяцев и выбор даты).
     * 
     * @param callbackData данные callback (формат: calendar_YYYY-MM, calendar_YYYY-MM-DD или calendar_cancel)
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
                // Проверяем, редактируется ли существующее событие
                if (conversationStateService.isEditingEvent(user.getId())) {
                    // Редактирование существующего события - просто выходим из режима редактирования
                    var context = conversationStateService.getEditingContext(user.getId());
                    
                    if (context != null && context.getEventId() != null) {
                        try {
                            // Получаем событие
                            ru.golubyatnikov.family.calendar.bot.model.Event event = 
                                eventService.getEventById(context.getEventId());
                            
                            // Получаем messageId из контекста
                            Integer editingMessageId = context.getMessageId();
                            
                            if (editingMessageId != null) {
                                // Возвращаем карточку события
                                int eventCount = eventService.getActiveEventsCount(event.getUser().getId());
                                String eventMessage = messageBuilder.buildEventMessageWithHeader(event, eventCount);
                                InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, user.getId());
                                
                                try {
                                    messageService.editMessageText(chatId, editingMessageId, eventMessage, keyboard);
                                    log.info("Редактирование даты отменено, возврат к карточке события: eventId={}, messageId={}", 
                                            context.getEventId(), editingMessageId);
                                } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                                    log.warn("Не удалось обновить сообщение о событии: eventId={}, messageId={}, error={}", 
                                            context.getEventId(), editingMessageId, e.getMessage());
                                    
                                    // Fallback: отправляем новое сообщение
                                    eventService.sendOrUpdateEventMessage(event, chatId);
                                }
                            } else {
                                // Fallback: отправляем новое сообщение
                                log.warn("MessageId не найден в контексте, используем sendOrUpdateEventMessage");
                                eventService.sendOrUpdateEventMessage(event, chatId);
                            }
                            
                            // Очищаем состояние редактирования
                            conversationStateService.clearEventEditing(user.getId());
                            
                            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.actionCancelled("Редактирование"));
                            log.info("Редактирование даты отменено пользователем {}, eventId={}", 
                                    user.getId(), context.getEventId());
                            
                        } catch (Exception e) {
                            log.error("Ошибка при отмене редактирования даты: userId={}, error={}", 
                                     user.getId(), e.getMessage());
                            
                            // Очищаем состояние редактирования в любом случае
                            conversationStateService.clearEventEditing(user.getId());
                            
                            throw new RuntimeException("Ошибка при отмене редактирования даты", e);
                        }
                    } else {
                        // Контекст некорректный - просто очищаем состояние
                        conversationStateService.clearEventEditing(user.getId());
                        log.warn("Некорректный контекст редактирования при отмене: userId={}", user.getId());
                    }
                } else {
                    // Создание нового события - отменяем создание
                    conversationService.cancelEventCreation(user.getId());
                    
                    String message = messageBuilder.buildEventCancelledMessage();
                    messageService.editMessageText(chatId, messageId, message, null);
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.actionCancelled("Создание"));
                    
                    log.info("Создание события отменено пользователем {}", user.getId());
                }
                return;
            }
            
            // Извлекаем payload из callback data (формат: calendar_YYYY-MM или calendar_YYYY-MM-DD)
            String payload = CallbackPrefix.CALENDAR.extractPayload(callbackData);
            String[] parts = payload.split("-");
            
            // Проверяем, это выбор даты или навигация по месяцам
            if (parts.length == 3) {
                // Выбор конкретной даты (формат: YYYY-MM-DD)
                handleDateSelection(payload, user, chatId, messageId, callbackQueryId);
            } else if (parts.length == 2) {
                // Навигация по месяцам (формат: YYYY-MM)
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                
                log.debug("Навигация по календарю: год={}, месяц={}, userId={}", year, month, user.getId());
                
                // Проверяем контекст: создание нового события или редактирование существующего
                boolean isCreatingEvent = conversationService.hasActiveDraft(user.getId());
                boolean isEditingEvent = conversationStateService.isEditingEvent(user.getId());
                Long editingEventId = null;
                
                if (isEditingEvent) {
                    ConversationStateService.EditingContext context = conversationStateService.getEditingContext(user.getId());
                    editingEventId = context != null ? context.getEventId() : null;
                }
                
                // Показываем календарь для выбранного месяца с учетом timezone пользователя
                // Для создания события блокируем прошлые даты, для просмотра - разрешаем
                InlineKeyboardMarkup keyboard;
                if (isCreatingEvent || isEditingEvent) {
                    keyboard = keyboardService.createCalendarKeyboard(year, month, user, editingEventId);
                } else {
                    keyboard = keyboardService.createViewCalendarKeyboard(year, month, user);
                }
                
                String message = (isCreatingEvent || isEditingEvent)
                    ? messageBuilder.buildSelectDateMessageWithHeader()
                    : messageBuilder.buildCalendarViewMessage();
                
                messageService.editMessageText(chatId, messageId, message, keyboard);
                messageService.answerCallbackQuery(callbackQueryId, "");
            }
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при навигации по календарю: userId={}, error={}", 
                     user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при навигации по календарю", e);
        }
    }
    
    /**
     * Обрабатывает выбор конкретной даты в календаре.
     * 
     * @param dateStr строка с датой в формате YYYY-MM-DD
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleDateSelection(String dateStr, User user, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
        try {
            java.time.LocalDate selectedDate = java.time.LocalDate.parse(dateStr);
            java.time.LocalDate today = java.time.LocalDate.now(user.getZoneId());
            
            // Проверяем контекст: создание события или просмотр календаря
            boolean isCreatingEvent = conversationService.hasActiveDraft(user.getId());
            
            if (isCreatingEvent) {
                // Режим создания события - обрабатываем выбор даты для нового события
                handleDateSelectionForEventCreation(selectedDate, user, chatId, messageId, callbackQueryId);
            } else {
                // Режим просмотра календаря - показываем события на выбранную дату
                handleDateSelectionForCalendarView(selectedDate, today, user, chatId, messageId, callbackQueryId);
            }
        } catch (Exception e) {
            log.error("Ошибка при выборе даты в календаре: userId={}, error={}", 
                     user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при выборе даты в календаре", e);
        }
    }
    
    /**
     * Обрабатывает выбор даты для создания нового события.
     */
    private void handleDateSelectionForEventCreation(java.time.LocalDate selectedDate, User user, 
                                                     Long chatId, Integer messageId, String callbackQueryId) {
        try {
            log.info("Пользователь {} выбрал дату {} для создания события", user.getId(), selectedDate);
            
            // Обновляем дату в черновике
            conversationService.updateEventDate(user.getId(), selectedDate);
            
            // Сохраняем messageId для дальнейшего обновления сообщения
            conversationService.setCreationMessageId(user.getId(), messageId.longValue());
            
            InlineKeyboardMarkup keyboard = keyboardService.createFilteredHourSelectionKeyboard(selectedDate, user);
            String message = messageBuilder.buildSelectTimeMessage(selectedDate);
            
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "Дата выбрана");
        } catch (Exception e) {
            log.error("Ошибка при выборе даты для создания события: userId={}, error={}", 
                     user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при выборе даты для создания события", e);
        }
    }
    
    /**
     * Обрабатывает выбор даты в режиме просмотра календаря.
     */
    private void handleDateSelectionForCalendarView(java.time.LocalDate selectedDate, java.time.LocalDate today,
                                                    User user, Long chatId, Integer messageId, String callbackQueryId) {
        try {
            log.info("Пользователь {} выбрал дату {} в календаре просмотра", user.getId(), selectedDate);
            
            boolean isPastDate = selectedDate.isBefore(today);
            
            // Для прошлых дат получаем события включая завершенные, для будущих - только активные
            java.util.List<ru.golubyatnikov.family.calendar.bot.model.Event> events = isPastDate
                ? eventService.getEventsByDateIncludingCompleted(user.getFamily().getId(), selectedDate)
                : eventService.getEventsByDate(user.getFamily().getId(), selectedDate);
            
            // Фильтруем персональные события других пользователей
            events = events.stream()
                .filter(event -> !event.getIsPersonal() || event.belongsToUser(user.getId()))
                .collect(java.util.stream.Collectors.toList());
            
            boolean hasEvents = !events.isEmpty();
            
            log.debug("Дата {}: прошлая={}, события={}", selectedDate, isPastDate, hasEvents);
            
            if (isPastDate && !hasEvents) {
                // Прошлая дата без событий - всплывающее сообщение
                messageService.answerCallbackQuery(callbackQueryId, 
                    "На эту дату нет событий для просмотра");
                log.info("Прошлая дата {} без событий, показано всплывающее сообщение", selectedDate);
            } else if (isPastDate && hasEvents) {
                // Прошлая дата с событиями - список событий (включая завершенные)
                String message = messageBuilder.buildDateEventsListMessage(selectedDate, events);
                InlineKeyboardMarkup keyboard = keyboardService.createDateEventsListKeyboard(
                    selectedDate, events, user);
                
                try {
                    messageService.editMessageText(chatId, messageId, message, keyboard);
                    messageService.answerCallbackQuery(callbackQueryId, "");
                    log.info("Прошлая дата {} с {} событиями (включая завершенные), показан список", selectedDate, events.size());
                } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                    // Если сообщение не изменилось (повторный клик на ту же дату), просто отвечаем на callback
                    if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                        messageService.answerCallbackQuery(callbackQueryId, "");
                        log.debug("Повторный клик на дату {}, сообщение не изменилось", selectedDate);
                    } else {
                        throw e;
                    }
                }
            } else if (!isPastDate && !hasEvents) {
                // Будущая дата без событий - предложение создать событие
                String message = messageBuilder.buildCreateEventOnDateMessage(selectedDate);
                InlineKeyboardMarkup keyboard = keyboardService.createCreateEventOnDateKeyboard(selectedDate);
                
                try {
                    messageService.editMessageText(chatId, messageId, message, keyboard);
                    messageService.answerCallbackQuery(callbackQueryId, "");
                    log.info("Будущая дата {} без событий, предложено создание", selectedDate);
                } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                    if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                        messageService.answerCallbackQuery(callbackQueryId, "");
                        log.debug("Повторный клик на дату {}, сообщение не изменилось", selectedDate);
                    } else {
                        throw e;
                    }
                }
            } else {
                // Будущая дата с событиями - управление событиями
                String message = messageBuilder.buildDateEventsManagementMessage(selectedDate, events);
                InlineKeyboardMarkup keyboard = keyboardService.createDateEventsManagementKeyboard(
                    selectedDate, events, user);
                
                try {
                    messageService.editMessageText(chatId, messageId, message, keyboard);
                    messageService.answerCallbackQuery(callbackQueryId, "");
                    log.info("Будущая дата {} с {} событиями, показано управление", selectedDate, events.size());
                } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                    if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                        messageService.answerCallbackQuery(callbackQueryId, "");
                        log.debug("Повторный клик на дату {}, сообщение не изменилось", selectedDate);
                    } else {
                        throw e;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при просмотре даты в календаре: userId={}, error={}", 
                     user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при просмотре даты в календаре", e);
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
    
    // ===== Методы для обработки календаря просмотра =====
    
    private void handleViewEvent(String callbackData, User user, Long chatId, Integer messageId, String callbackQueryId) {
        try {
            Long eventId = Long.parseLong(callbackData.substring("view_event_".length()));
            ru.golubyatnikov.family.calendar.bot.model.Event event = eventService.getEventById(eventId);
            int eventCount = eventService.getActiveEventsCount(event.getUser().getId());
            String eventMessage = messageBuilder.buildEventMessageWithHeader(event, eventCount);
            
            java.time.LocalDate eventDate = event.getEventDate();
            java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();
            
            // Для завершенных событий добавляем кнопку "Повторить"
            if (event.isCompleted()) {
                java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> repeatRow = new java.util.ArrayList<>();
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton repeatButton = 
                    new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("🔄 Повторить событие");
                repeatButton.setCallbackData("repeat_event_" + eventId);
                repeatRow.add(repeatButton);
                rows.add(repeatRow);
            }
            
            java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> backRow = new java.util.ArrayList<>();
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton backButton = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("🔙 Назад к списку");
            backButton.setCallbackData("view_events_on_date_" + eventDate.toString());
            backRow.add(backButton);
            rows.add(backRow);
            
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
            keyboard.setKeyboard(rows);
            
            messageService.editMessageText(chatId, messageId, eventMessage, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "");
        } catch (Exception e) {
            log.error("Ошибка при просмотре события: userId={}, error={}", user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при просмотре события", e);
        }
    }
    
    private void handleCreateEventOnDate(String callbackData, User user, Long chatId, Integer messageId, String callbackQueryId) {
        try {
            String dateStr = callbackData.substring("create_event_on_date_".length());
            java.time.LocalDate selectedDate = java.time.LocalDate.parse(dateStr);
            
            // Создаем черновик события, если его еще нет
            if (!conversationService.hasActiveDraft(user.getId())) {
                conversationService.startEventCreation(user.getId());
                log.info("Создан черновик события для пользователя {} при выборе даты в календаре", user.getId());
            }
            
            // Обновляем дату в черновике
            conversationService.updateEventDate(user.getId(), selectedDate);
            
            // Сохраняем messageId для дальнейшего обновления сообщения
            conversationService.setCreationMessageId(user.getId(), messageId.longValue());
            
            InlineKeyboardMarkup keyboard = keyboardService.createFilteredHourSelectionKeyboard(selectedDate, user);
            String message = messageBuilder.buildSelectTimeMessage(selectedDate);
            
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "Дата выбрана");
            
            log.info("Пользователь {} выбрал дату {} для создания события", user.getId(), selectedDate);
        } catch (Exception e) {
            log.error("Ошибка при создании события на дату: userId={}, error={}", user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при создании события на дату", e);
        }
    }
    
    private void handleViewEventsOnDate(String callbackData, User user, Long chatId, Integer messageId, String callbackQueryId) {
        try {
            String dateStr = callbackData.substring("view_events_on_date_".length());
            java.time.LocalDate selectedDate = java.time.LocalDate.parse(dateStr);
            java.time.LocalDate today = user.getCurrentDate();
            boolean isPastDate = selectedDate.isBefore(today);
            
            // Для прошлых дат получаем события включая завершенные, для будущих - только активные
            java.util.List<ru.golubyatnikov.family.calendar.bot.model.Event> events = isPastDate
                ? eventService.getEventsByDateIncludingCompleted(user.getFamily().getId(), selectedDate)
                : eventService.getEventsByDate(user.getFamily().getId(), selectedDate);
            
            // Фильтруем персональные события других пользователей
            events = events.stream()
                .filter(event -> !event.getIsPersonal() || event.belongsToUser(user.getId()))
                .collect(java.util.stream.Collectors.toList());
            
            String message = messageBuilder.buildDateEventsListMessage(selectedDate, events);
            InlineKeyboardMarkup keyboard = keyboardService.createDateEventsListKeyboard(selectedDate, events, user);
            
            try {
                messageService.editMessageText(chatId, messageId, message, keyboard);
                messageService.answerCallbackQuery(callbackQueryId, "");
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                // Если сообщение не изменилось (возврат к тому же списку), просто отвечаем на callback
                if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                    messageService.answerCallbackQuery(callbackQueryId, "");
                    log.debug("Возврат к списку событий на дату {}, сообщение не изменилось", selectedDate);
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при просмотре событий на дату: userId={}, error={}", user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при просмотре событий на дату", e);
        }
    }
    
    private void handleEditMyEventsOnDate(String callbackData, User user, Long chatId, Integer messageId, String callbackQueryId) {
        try {
            String dateStr = callbackData.substring("edit_my_events_on_date_".length());
            java.time.LocalDate selectedDate = java.time.LocalDate.parse(dateStr);
            
            java.util.List<ru.golubyatnikov.family.calendar.bot.model.Event> allEvents = 
                eventService.getEventsByDate(user.getFamily().getId(), selectedDate);
            java.util.List<ru.golubyatnikov.family.calendar.bot.model.Event> myEvents = allEvents.stream()
                .filter(event -> event.getUser().getId().equals(user.getId()))
                .collect(java.util.stream.Collectors.toList());
            
            String message = "✏️ Выберите событие для редактирования:";
            
            java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();
            
            for (ru.golubyatnikov.family.calendar.bot.model.Event event : myEvents) {
                java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new java.util.ArrayList<>();
                String buttonText = String.format("%s - %s", 
                    event.getEventTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                    event.getTitle());
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton button = 
                    new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton(buttonText);
                // Используем специальный callback для редактирования из календаря
                button.setCallbackData("edit_event_from_calendar_" + event.getId() + "_" + selectedDate.toString());
                row.add(button);
                rows.add(row);
            }
            
            java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> backRow = new java.util.ArrayList<>();
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton backButton = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("🔙 Назад");
            backButton.setCallbackData("calendar_" + selectedDate.toString());
            backRow.add(backButton);
            rows.add(backRow);
            
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
            keyboard.setKeyboard(rows);
            
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "");
        } catch (Exception e) {
            log.error("Ошибка при редактировании событий на дату: userId={}, error={}", user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при редактировании событий на дату", e);
        }
    }
    
    private void handleDeleteMyEventsOnDate(String callbackData, User user, Long chatId, Integer messageId, String callbackQueryId) {
        try {
            String dateStr = callbackData.substring("delete_my_events_on_date_".length());
            java.time.LocalDate selectedDate = java.time.LocalDate.parse(dateStr);
            
            java.util.List<ru.golubyatnikov.family.calendar.bot.model.Event> allEvents = 
                eventService.getEventsByDate(user.getFamily().getId(), selectedDate);
            java.util.List<ru.golubyatnikov.family.calendar.bot.model.Event> myEvents = allEvents.stream()
                .filter(event -> event.getUser().getId().equals(user.getId()))
                .collect(java.util.stream.Collectors.toList());
            
            String message = "🗑 Выберите событие для удаления:";
            
            java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();
            
            for (ru.golubyatnikov.family.calendar.bot.model.Event event : myEvents) {
                java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new java.util.ArrayList<>();
                String buttonText = String.format("%s - %s", 
                    event.getEventTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                    event.getTitle());
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton button = 
                    new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton(buttonText);
                button.setCallbackData("delete_event_" + event.getId());
                row.add(button);
                rows.add(row);
            }
            
            java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> backRow = new java.util.ArrayList<>();
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton backButton = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("🔙 Назад");
            backButton.setCallbackData("calendar_" + selectedDate.toString());
            backRow.add(backButton);
            rows.add(backRow);
            
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
            keyboard.setKeyboard(rows);
            
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "");
        } catch (Exception e) {
            log.error("Ошибка при удалении событий на дату: userId={}, error={}", user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при удалении событий на дату", e);
        }
    }
    
    /**
     * Обрабатывает редактирование события из календаря.
     * Сохраняет исходную дату для возврата к списку событий.
     * 
     * @param callbackData данные callback (формат: edit_event_from_calendar_{eventId}_{date})
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleEditEventFromCalendar(String callbackData, User user, Long chatId, Integer messageId, String callbackQueryId) {
        try {
            String payload = callbackData.substring("edit_event_from_calendar_".length());
            String[] parts = payload.split("_", 2);
            
            if (parts.length != 2) {
                log.error("Некорректный формат callback data для редактирования из календаря: {}", callbackData);
                messageService.answerCallbackQuery(callbackQueryId, "Ошибка при обработке запроса");
                return;
            }
            
            Long eventId = Long.parseLong(parts[0]);
            java.time.LocalDate sourceDate = java.time.LocalDate.parse(parts[1]);
            
            log.info("Пользователь {} начал редактирование события {} из календаря (дата={})", 
                    user.getId(), eventId, sourceDate);
            
            // Получаем событие и проверяем права доступа
            ru.golubyatnikov.family.calendar.bot.model.Event event = eventService.getEventById(eventId);
            
            if (!event.getUser().getId().equals(user.getId())) {
                log.warn("Пользователь ID={} не имеет прав для редактирования события ID={}", 
                        user.getId(), eventId);
                messageService.answerCallbackQuery(callbackQueryId, ru.golubyatnikov.family.calendar.bot.util.CallbackMessages.NO_ACCESS);
                return;
            }
            
            // Сохраняем контекст редактирования с исходной датой ПЕРЕД созданием клавиатуры
            conversationStateService.startEventEditingFromCalendar(user.getId(), eventId, chatId, messageId, sourceDate);
            
            log.info("Сохранён контекст редактирования из календаря: userId={}, eventId={}, messageId={}, sourceDate={}", 
                    user.getId(), eventId, messageId, sourceDate);
            
            // Формируем сообщение с текущими данными события и клавиатурой выбора поля
            String message = buildEditFieldSelectionMessage(event);
            InlineKeyboardMarkup keyboard = keyboardService.createEditFieldSelectionKeyboard(eventId, user.getId());
            
            // Обновляем текущее сообщение
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, ru.golubyatnikov.family.calendar.bot.util.CallbackMessages.EMPTY);
            
            log.debug("Начато редактирование события ID={} из календаря в сообщении ID={} пользователем ID={}", 
                     eventId, messageId, user.getId());
            
        } catch (Exception e) {
            log.error("Ошибка при редактировании события из календаря: userId={}, error={}", 
                     user.getId(), e.getMessage(), e);
            throw new RuntimeException("Ошибка при редактировании события из календаря", e);
        }
    }
    
    /**
     * Формирует сообщение с текущими данными события для выбора поля редактирования.
     * 
     * @param event событие для редактирования
     * @return отформатированное сообщение
     */
    private String buildEditFieldSelectionMessage(ru.golubyatnikov.family.calendar.bot.model.Event event) {
        StringBuilder message = new StringBuilder();
        message.append("📝 ").append(ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold("Редактирование события")).append("\n\n");
        message.append(messageBuilder.buildEventMessage(event));
        message.append("\n\n").append("Выберите поле для редактирования:");
        return message.toString();
    }
    
    /**
     * Обрабатывает повторение события с новой датой и временем.
     * Создает черновик события с предзаполненными данными из исходного события.
     * 
     * @param callbackData данные callback (формат: repeat_event_{eventId})
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleRepeatEvent(String callbackData, User user, Long chatId, Integer messageId, String callbackQueryId) {
        try {
            Long eventId = Long.parseLong(callbackData.substring("repeat_event_".length()));
            ru.golubyatnikov.family.calendar.bot.model.Event originalEvent = eventService.getEventById(eventId);
            
            log.info("Пользователь {} начал повторение события ID={}", user.getId(), eventId);
            
            // Проверяем, что событие принадлежит пользователю или его семье
            if (!originalEvent.belongsToUser(user.getId()) && 
                (user.getFamily() == null || !originalEvent.getFamily().getId().equals(user.getFamily().getId()))) {
                log.warn("Попытка повторить чужое событие: userId={}, eventId={}", user.getId(), eventId);
                messageService.answerCallbackQuery(callbackQueryId, "У вас нет доступа к этому событию");
                return;
            }
            
            // Отменяем старые черновики пользователя
            conversationService.cancelEventCreation(user.getId());
            
            // Создаем новый черновик события
            ru.golubyatnikov.family.calendar.bot.model.Event draft = conversationService.startEventCreation(user.getId());
            
            // Копируем данные из исходного события в черновик (кроме даты и времени)
            draft.setTitle(originalEvent.getTitle());
            draft.setDescription(originalEvent.getDescription());
            draft.setIsPersonal(originalEvent.getIsPersonal());
            // Дата и время будут выбраны пользователем заново
            
            // Сохраняем изменения в черновике
            eventRepository.save(draft);
            
            log.debug("Создан черновик для повторения события: draftId={}, originalEventId={}", 
                     draft.getId(), eventId);
            
            // Показываем календарь для выбора новой даты
            java.time.LocalDate currentDate = user.getCurrentDate();
            int year = currentDate.getYear();
            int month = currentDate.getMonthValue();
            
            InlineKeyboardMarkup keyboard = keyboardService.createCalendarKeyboard(year, month, user);
            String message = messageBuilder.buildRepeatEventSelectDateMessage(originalEvent);
            
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "Данные скопированы. Выберите новую дату");
            
            log.info("Пользователь {} начал выбор новой даты для повторения события ID={}", 
                    user.getId(), eventId);
            
        } catch (Exception e) {
            log.error("Ошибка при повторении события: userId={}, error={}", user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при повторении события", e);
        }
    }
}
