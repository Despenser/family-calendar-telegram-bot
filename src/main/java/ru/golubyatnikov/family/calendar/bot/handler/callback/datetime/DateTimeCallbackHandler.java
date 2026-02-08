package ru.golubyatnikov.family.calendar.bot.handler.callback.datetime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.user.UserService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Обработчик callback queries для выбора даты и времени события.
 * 
 * <p>Обрабатывает следующие типы callback:</p>
 * <ul>
 *   <li>date_ - выбор даты из календаря</li>
 *   <li>hour_ - выбор часа</li>
 *   <li>time_HH:MM - выбор времени (час и минуты)</li>
 *   <li>time_back - возврат к выбору часа</li>
 *   <li>time_cancel - возврат к списку полей для редактирования</li>
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
public class DateTimeCallbackHandler implements CallbackHandler {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    
    private final ConversationService conversationService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final BotMessageBuilder messageBuilder;
    private final ConversationStateService conversationStateService;
    private final EventService eventService;
    private final UserService userService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.DATE;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        
        return CallbackPrefix.DATE.matches(callbackData) ||
               CallbackPrefix.HOUR.matches(callbackData) ||
               isTimeWithMinutes(callbackData) ||
               CallbackPrefix.TIME_BACK.matches(callbackData) ||
               CallbackPrefix.TIME_CANCEL.matches(callbackData);
    }
    
    /**
     * Проверяет, является ли callback data выбором времени с минутами (формат time_HH:MM).
     * 
     * @param callbackData строка callback data
     * @return true если это выбор времени с минутами
     */
    private boolean isTimeWithMinutes(String callbackData) {
        return callbackData.startsWith("time_") && callbackData.contains(":");
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        log.debug("Обработка callback для даты/времени: data='{}', userId={}", 
                callbackData, user.getId());
        
        if (CallbackPrefix.DATE.matches(callbackData)) {
            handleDateSelection(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.HOUR.matches(callbackData)) {
            handleHourSelection(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        } else if (isTimeWithMinutes(callbackData)) {
            handleTimeSelection(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.TIME_BACK.matches(callbackData)) {
            handleTimeBack(user.getId(), chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.TIME_CANCEL.matches(callbackData)) {
            handleTimeCancel(user.getId(), chatId, messageId, callbackQueryId);
        }
    }
    
    /**
     * Обрабатывает выбор даты из календаря.
     * Обновляет черновик события или существующее событие и показывает выбор часа.
     * Применяет фильтрацию прошедших часов для сегодняшнего дня.
     * 
     * <p><b>Требования:</b> 1.1, 1.3, 1.5, 4.3</p>
     * 
     * @param callbackData данные callback (формат: date_YYYY-MM-DD)
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleDateSelection(String callbackData, Long userId, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
        // Извлекаем дату из callback data
        String dateStr = CallbackPrefix.DATE.extractPayload(callbackData);
        LocalDate date = LocalDate.parse(dateStr);
        
        // Получаем пользователя для timezone
        User user = userService.findById(userId)
                .orElseThrow(() -> new ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException(
                        "Пользователь не найден: " + userId));
        
        // Проверяем, редактируется ли существующее событие
        if (conversationStateService.isEditingEvent(userId)) {
            // Редактирование существующего события
            ConversationStateService.EditingContext context = conversationStateService.getEditingContext(userId);
            if (context != null && context.getEventId() != null) {
                try {
                    // Обновляем дату события через EventService
                    ru.golubyatnikov.family.calendar.bot.model.Event updatedEvent = 
                        eventService.updateEventDate(context.getEventId(), userId, date);
                    
                    // ИЗМЕНЕНИЕ: Получаем messageId из контекста вместо параметра callback
                    Integer editingMessageId = context.getMessageId();
                    
                    if (editingMessageId != null) {
                        // ИЗМЕНЕНИЕ: Обновляем сообщение о событии через editMessageText с messageId из контекста
                        // Используем buildEventMessageWithHeader для сохранения шапки, если это первое событие
                        int eventCount = eventService.getActiveEventsCount(updatedEvent.getUser().getId());
                        String eventMessage = messageBuilder.buildEventMessageWithHeader(updatedEvent, eventCount);
                        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(updatedEvent, userId);
                        
                        try {
                            messageService.editMessageText(chatId, editingMessageId, eventMessage, keyboard);
                            log.info("Дата события обновлена и сообщение обновлено: eventId={}, messageId={}", 
                                    context.getEventId(), editingMessageId);
                        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                            log.warn("Не удалось обновить сообщение о событии: eventId={}, messageId={}, error={}", 
                                    context.getEventId(), editingMessageId, e.getMessage());
                            
                            // ИЗМЕНЕНИЕ: Fallback на sendOrUpdateEventMessage если messageId не найден
                            log.warn("MessageId не найден в контексте, используем sendOrUpdateEventMessage");
                            eventService.sendOrUpdateEventMessage(updatedEvent, chatId);
                        }
                    } else {
                        // ИЗМЕНЕНИЕ: Fallback на sendOrUpdateEventMessage если messageId не найден
                        log.warn("MessageId не найден в контексте, используем sendOrUpdateEventMessage");
                        eventService.sendOrUpdateEventMessage(updatedEvent, chatId);
                    }
                    
                    // ИЗМЕНЕНИЕ: Очищаем состояние редактирования после успешного обновления
                    conversationStateService.clearEventEditing(userId);
                    
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.UPDATED);
                    
                    log.info("Дата события обновлена: eventId={}, userId={}, date={}", 
                            context.getEventId(), userId, date);
                } catch (Exception e) {
                    log.error("Ошибка при обновлении даты события: userId={}, date={}, error={}", 
                             userId, date, e.getMessage());
                    throw new RuntimeException("Ошибка при обновлении даты", e);
                }
            }
        } else {
            // Создание нового события (черновик)
            conversationService.updateEventDate(userId, date);
            
            // Показываем фильтрованный выбор часа
            InlineKeyboardMarkup keyboard = keyboardService.createFilteredHourSelectionKeyboard(date, user);
            
            // Проверяем, есть ли доступные часы
            // Клавиатура содержит: заголовок (1 строка) + кнопка отмены (1 строка) + часы (если есть)
            if (keyboard.getKeyboard().size() <= 2) {
                // Нет доступных часов - слишком поздно для создания события на сегодня
                String message = messageBuilder.buildTooLateForTodayMessage();
                
                try {
                    messageService.editMessageText(chatId, messageId, message, null);
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.TOO_LATE_TODAY);
                    log.warn("Попытка создать событие на сегодня, когда все часы прошли: userId={}, date={}", 
                            userId, date);
                } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                    log.error("Ошибка при отображении сообщения о слишком позднем времени: userId={}, date={}, error={}", 
                             userId, date, e.getMessage());
                    throw new RuntimeException("Ошибка при отображении сообщения", e);
                }
                return;
            }
            
            String formattedDate = date.format(DATE_FORMATTER);
            String message = messageBuilder.buildDateSelectedMessage(formattedDate);
            
            try {
                // Обновляем сообщение создания через editMessageText
                messageService.editMessageText(chatId, messageId, message, keyboard);
                log.debug("Сообщение создания обновлено после выбора даты с фильтрацией часов: userId={}, messageId={}, date={}", 
                         userId, messageId, date);
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.itemSelected("Дата"));
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                log.error("Ошибка при выборе даты: userId={}, date={}, error={}", 
                         userId, date, e.getMessage());
                throw new RuntimeException("Ошибка при выборе даты", e);
            }
            
            log.info("Дата выбрана для пользователя {}: {}", userId, date);
        }
    }
    
    /**
     * Обрабатывает выбор часа.
     * Показывает выбор минут для выбранного часа с фильтрацией прошедших минут.
     * 
     * <p><b>Требования:</b> 2.1, 2.3, 2.5, 4.3</p>
     * 
     * @param callbackData данные callback (формат: hour_HH)
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleHourSelection(String callbackData, Long userId, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
        // Извлекаем час из callback data
        String hourStr = CallbackPrefix.HOUR.extractPayload(callbackData);
        int hour = Integer.parseInt(hourStr);
        
        // Получаем пользователя для timezone
        User user = userService.findById(userId)
                .orElseThrow(() -> new ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException(
                        "Пользователь не найден: " + userId));
        
        // Получаем дату события и eventId (из черновика или редактируемого события)
        LocalDate eventDate;
        Long editingEventId = null;
        boolean isEditingEvent = conversationStateService.isEditingEvent(userId);
        
        if (isEditingEvent) {
            ConversationStateService.EditingContext context = conversationStateService.getEditingContext(userId);
            ru.golubyatnikov.family.calendar.bot.model.Event event = 
                eventService.getEventById(context.getEventId());
            eventDate = event.getEventDate();
            editingEventId = context.getEventId();
        } else {
            ru.golubyatnikov.family.calendar.bot.model.Event draft = conversationService.getActiveDraft(userId);
            eventDate = draft.getEventDate();
        }
        
        // Показываем фильтрованный выбор минут с учетом контекста редактирования
        InlineKeyboardMarkup keyboard = keyboardService.createFilteredMinuteSelectionKeyboard(hour, eventDate, user, editingEventId);
        
        // Проверяем, есть ли доступные минуты
        // Клавиатура содержит: заголовок (1 строка) + кнопки навигации (1 строка) + минуты (если есть)
        if (keyboard.getKeyboard().size() <= 2) {
            // Нет доступных минут - все минуты прошли для текущего часа
            String message = messageBuilder.buildSelectNextHourMessage(hour);
            
            try {
                // Показываем сообщение и возвращаем к выбору часа
                messageService.editMessageText(chatId, messageId, message, 
                        keyboardService.createFilteredHourSelectionKeyboard(eventDate, user, editingEventId));
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.SELECT_NEXT_HOUR);
                log.warn("Попытка выбрать час, для которого все минуты прошли: userId={}, hour={}, eventDate={}", 
                        userId, hour, eventDate);
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                log.error("Ошибка при отображении сообщения о прошедших минутах: userId={}, hour={}, error={}", 
                         userId, hour, e.getMessage());
                throw new RuntimeException("Ошибка при отображении сообщения", e);
            }
            return;
        }
        
        // Используем правильное сообщение в зависимости от контекста
        String message = isEditingEvent
            ? messageBuilder.buildEditTimeHourSelectedMessage(hour)
            : messageBuilder.buildHourSelectedMessage(hour);
        
        try {
            // Обновляем сообщение создания через editMessageText
            messageService.editMessageText(chatId, messageId, message, keyboard);
            log.debug("Сообщение обновлено после выбора часа с фильтрацией минут: messageId={}, hour={}, isEditing={}", 
                     messageId, hour, isEditingEvent);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.itemSelected("Час"));
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при выборе часа: hour={}, error={}", hour, e.getMessage());
            throw new RuntimeException("Ошибка при выборе часа", e);
        }
        
        log.debug("Час выбран: {}, isEditing={}", hour, isEditingEvent);
    }
    
    /**
     * Обрабатывает выбор времени (час и минуты).
     * Обновляет черновик или существующее событие и показывает выбор типа события.
     * 
     * <p><b>Требования:</b> 3.1, 3.3</p>
     * 
     * @param callbackData данные callback (формат: time_HH:MM)
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleTimeSelection(String callbackData, Long userId, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
        // Извлекаем время из callback data (формат: time_HH:MM)
        String timeStr = callbackData.substring(5); // Убираем "time_"
        LocalTime time = LocalTime.parse(timeStr);
        
        // Получаем пользователя для проверки timezone
        User user = userService.findById(userId)
                .orElseThrow(() -> new ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException(
                        "Пользователь не найден: " + userId));
        
        // Проверяем, редактируется ли существующее событие
        if (conversationStateService.isEditingEvent(userId)) {
            // Редактирование существующего события
            ConversationStateService.EditingContext context = conversationStateService.getEditingContext(userId);
            if (context != null && context.getEventId() != null) {
                try {
                    // Обновляем время события через EventService
                    ru.golubyatnikov.family.calendar.bot.model.Event updatedEvent = 
                        eventService.updateEventTime(context.getEventId(), userId, time);
                    
                    // ИЗМЕНЕНИЕ: Получаем messageId из контекста вместо параметра callback
                    Integer editingMessageId = context.getMessageId();
                    
                    if (editingMessageId != null) {
                        // ИЗМЕНЕНИЕ: Обновляем сообщение о событии через editMessageText с messageId из контекста
                        // Используем buildEventMessageWithHeader для сохранения шапки, если это первое событие
                        int eventCount = eventService.getActiveEventsCount(updatedEvent.getUser().getId());
                        String eventMessage = messageBuilder.buildEventMessageWithHeader(updatedEvent, eventCount);
                        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(updatedEvent, userId);
                        
                        try {
                            messageService.editMessageText(chatId, editingMessageId, eventMessage, keyboard);
                            log.info("Время события обновлено и сообщение обновлено: eventId={}, messageId={}", 
                                    context.getEventId(), editingMessageId);
                        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                            log.warn("Не удалось обновить сообщение о событии: eventId={}, messageId={}, error={}", 
                                    context.getEventId(), editingMessageId, e.getMessage());
                            
                            // ИЗМЕНЕНИЕ: Fallback на sendOrUpdateEventMessage если messageId не найден
                            log.warn("MessageId не найден в контексте, используем sendOrUpdateEventMessage");
                            eventService.sendOrUpdateEventMessage(updatedEvent, chatId);
                        }
                    } else {
                        // ИЗМЕНЕНИЕ: Fallback на sendOrUpdateEventMessage если messageId не найден
                        log.warn("MessageId не найден в контексте, используем sendOrUpdateEventMessage");
                        eventService.sendOrUpdateEventMessage(updatedEvent, chatId);
                    }
                    
                    // ИЗМЕНЕНИЕ: Очищаем состояние редактирования после успешного обновления
                    conversationStateService.clearEventEditing(userId);
                    
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.UPDATED);
                    
                    log.info("Время события обновлено: eventId={}, userId={}, time={}", 
                            context.getEventId(), userId, time);
                } catch (Exception e) {
                    log.error("Ошибка при обновлении времени события: userId={}, time={}, error={}", 
                             userId, time, e.getMessage());
                    throw new RuntimeException("Ошибка при обновлении времени", e);
                }
            }
        } else {
            // Создание нового события (черновик)
            Event draft = conversationService.updateEventTime(userId, time);
            
            // Проверяем, является ли это повторением события (уже заполнены title, description, isPersonal)
            boolean isRepeatEvent = draft.getTitle() != null && !draft.getTitle().isBlank() 
                                 && draft.getIsPersonal() != null;
            
            if (isRepeatEvent) {
                // Это повторение события - сразу завершаем создание
                Event completedEvent = conversationService.completeEventCreation(userId, draft.getDescription());
                
                String eventMessage = messageBuilder.buildEventCreatedMessage(completedEvent);
                InlineKeyboardMarkup eventKeyboard = keyboardService.createEventActionsKeyboard(completedEvent.getId());
                
                try {
                    messageService.editMessageText(chatId, messageId, eventMessage, eventKeyboard);
                    messageService.answerCallbackQuery(callbackQueryId, "✅ Событие создано");
                    
                    log.info("Событие повторено успешно: eventId={}, userId={}, originalTitle={}", 
                            completedEvent.getId(), userId, completedEvent.getTitle());
                } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                    log.error("Ошибка при завершении повторения события: userId={}, error={}", 
                             userId, e.getMessage());
                    throw new RuntimeException("Ошибка при завершении повторения события", e);
                }
            } else {
                // Обычное создание события - показываем выбор типа события
                String formattedTime = time.format(TIME_FORMATTER);
                InlineKeyboardMarkup typeKeyboard = keyboardService.createEventTypeSelectionKeyboard();
                String message = messageBuilder.buildTimeSelectedMessage(formattedTime);
                
                try {
                    // Обновляем сообщение создания через editMessageText
                    messageService.editMessageText(chatId, messageId, message, typeKeyboard);
                    log.debug("Сообщение создания обновлено после выбора времени: userId={}, messageId={}, time={}", 
                             userId, messageId, time);
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.itemSelected("Время"));
                } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                    log.error("Ошибка при выборе времени: userId={}, time={}, error={}", 
                             userId, time, e.getMessage());
                    throw new RuntimeException("Ошибка при выборе времени", e);
                }
                
                log.info("Время выбрано для пользователя {}: {}", userId, time);
            }
        }
    }
    
    /**
     * Обрабатывает возврат к выбору часа.
     * Пересчитывает доступные часы на основе текущего времени пользователя.
     * 
     * <p><b>Требования:</b> 4.4</p>
     * 
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleTimeBack(Long userId, Long chatId, Integer messageId, String callbackQueryId) {
        // Получаем пользователя для timezone
        User user = userService.findById(userId)
                .orElseThrow(() -> new ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException(
                        "Пользователь не найден: " + userId));
        
        // Получаем дату события и eventId (из черновика или редактируемого события)
        LocalDate eventDate;
        Long editingEventId = null;
        boolean isEditingEvent = conversationStateService.isEditingEvent(userId);
        
        if (isEditingEvent) {
            ConversationStateService.EditingContext context = conversationStateService.getEditingContext(userId);
            ru.golubyatnikov.family.calendar.bot.model.Event event = 
                eventService.getEventById(context.getEventId());
            eventDate = event.getEventDate();
            editingEventId = context.getEventId();
        } else {
            ru.golubyatnikov.family.calendar.bot.model.Event draft = conversationService.getActiveDraft(userId);
            eventDate = draft.getEventDate();
        }
        
        // Пересчитываем доступные часы на основе текущего времени с учетом контекста редактирования
        InlineKeyboardMarkup keyboard = keyboardService.createFilteredHourSelectionKeyboard(eventDate, user, editingEventId);
        
        // Используем правильное сообщение в зависимости от контекста
        String message = isEditingEvent 
            ? messageBuilder.buildEditTimeSelectHourMessage()
            : messageBuilder.buildSelectHourMessage();
        
        try {
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            log.debug("Возврат к выбору часа с пересчетом доступных часов: userId={}, eventDate={}, isEditing={}", 
                     userId, eventDate, isEditingEvent);
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при возврате к выбору часа: userId={}, error={}", userId, e.getMessage());
            throw new RuntimeException("Ошибка при возврате к выбору часа", e);
        }
        
        log.debug("Возврат к выбору часа с фильтрацией: userId={}, eventDate={}, isEditing={}", 
                 userId, eventDate, isEditingEvent);
    }
    
    /**
     * Обрабатывает отмену выбора времени.
     * 
     * <p>Поведение зависит от контекста:</p>
     * <ul>
     *   <li>При создании нового события - возвращает к календарю или экрану управления событиями</li>
     *   <li>При редактировании существующего события - выходит из режима редактирования 
     *       и возвращает пользователя к карточке события</li>
     * </ul>
     * 
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleTimeCancel(Long userId, Long chatId, Integer messageId, 
                                  String callbackQueryId) {
        // Проверяем, редактируется ли существующее событие
        if (conversationStateService.isEditingEvent(userId)) {
            // Редактирование существующего события - просто выходим из режима редактирования
            ConversationStateService.EditingContext context = conversationStateService.getEditingContext(userId);
            
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
                        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
                        
                        try {
                            messageService.editMessageText(chatId, editingMessageId, eventMessage, keyboard);
                            log.info("Редактирование времени отменено, возврат к карточке события: eventId={}, messageId={}", 
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
                    conversationStateService.clearEventEditing(userId);
                    
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.actionCancelled("Редактирование"));
                    log.info("Редактирование времени отменено пользователем {}, eventId={}", 
                            userId, context.getEventId());
                    
                } catch (Exception e) {
                    log.error("Ошибка при отмене редактирования времени: userId={}, error={}", 
                             userId, e.getMessage());
                    
                    // Очищаем состояние редактирования в любом случае
                    conversationStateService.clearEventEditing(userId);
                    
                    throw new RuntimeException("Ошибка при отмене редактирования времени", e);
                }
            } else {
                // Контекст некорректный - просто очищаем состояние
                conversationStateService.clearEventEditing(userId);
                log.warn("Некорректный контекст редактирования при отмене: userId={}", userId);
            }
        } else {
            // Создание нового события - возвращаем к календарю или экрану управления событиями
            handleBackFromEventCreation(userId, chatId, messageId, callbackQueryId);
        }
    }
    
    /**
     * Обрабатывает возврат из создания события.
     * Возвращает пользователя к календарю или экрану управления событиями в зависимости от наличия событий на дату.
     * 
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleBackFromEventCreation(Long userId, Long chatId, Integer messageId, String callbackQueryId) {
        try {
            // Получаем пользователя для timezone
            User user = userService.findById(userId)
                    .orElseThrow(() -> new ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException(
                            "Пользователь не найден: " + userId));
            
            // Получаем черновик события для определения даты
            ru.golubyatnikov.family.calendar.bot.model.Event draft = conversationService.getActiveDraft(userId);
            
            if (draft == null || draft.getEventDate() == null) {
                // Если черновика нет или дата не выбрана - возвращаем к текущему месяцу календаря
                log.info("Возврат из создания события без выбранной даты: userId={}", userId);
                
                java.time.LocalDate currentDate = user.getCurrentDate();
                int year = currentDate.getYear();
                int month = currentDate.getMonthValue();
                
                InlineKeyboardMarkup keyboard = keyboardService.createViewCalendarKeyboard(year, month, user);
                String message = messageBuilder.buildCalendarViewMessage();
                
                // Отменяем создание события
                conversationService.cancelEventCreation(userId);
                
                try {
                    messageService.editMessageText(chatId, messageId, message, keyboard);
                    messageService.answerCallbackQuery(callbackQueryId, "");
                } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                    log.error("Ошибка при возврате к календарю: userId={}, error={}", userId, e.getMessage());
                    throw new RuntimeException("Ошибка при возврате к календарю", e);
                }
                
                log.debug("Возврат к календарю текущего месяца: userId={}, год={}, месяц={}", 
                         userId, year, month);
                return;
            }
            
            // Получаем выбранную дату из черновика
            java.time.LocalDate selectedDate = draft.getEventDate();
            java.time.LocalDate today = user.getCurrentDate();
            boolean isToday = selectedDate.equals(today);
            boolean isPastDate = selectedDate.isBefore(today);
            boolean isFutureDate = selectedDate.isAfter(today);
            
            log.info("Возврат из создания события с выбранной датой: userId={}, дата={}", 
                    userId, selectedDate);
            
            // Получаем события на выбранную дату
            // Для прошлых дат и сегодня получаем события включая завершенные, для будущих - только активные
            java.util.List<ru.golubyatnikov.family.calendar.bot.model.Event> events = (isPastDate || isToday)
                ? eventService.getEventsByDateIncludingCompleted(user.getFamily().getId(), selectedDate)
                : eventService.getEventsByDate(user.getFamily().getId(), selectedDate);
            
            // Фильтруем персональные события других пользователей
            events = events.stream()
                .filter(event -> !event.getIsPersonal() || event.belongsToUser(userId))
                .collect(java.util.stream.Collectors.toList());
            
            boolean hasEvents = !events.isEmpty();
            
            // Отменяем создание события
            conversationService.cancelEventCreation(userId);
            
            try {
                if (isPastDate && !hasEvents) {
                    // Прошлая дата без событий - возвращаем к календарю месяца
                    int year = selectedDate.getYear();
                    int month = selectedDate.getMonthValue();
                    
                    InlineKeyboardMarkup keyboard = keyboardService.createViewCalendarKeyboard(year, month, user);
                    String message = messageBuilder.buildCalendarViewMessage();
                    
                    messageService.editMessageText(chatId, messageId, message, keyboard);
                    messageService.answerCallbackQuery(callbackQueryId, "");
                    
                    log.info("Возврат к календарю (прошлая дата без событий): userId={}, дата={}", 
                            userId, selectedDate);
                } else if (isPastDate && hasEvents) {
                    // Прошлая дата с событиями - показываем список событий
                    String message = messageBuilder.buildDateEventsListMessage(selectedDate, events);
                    InlineKeyboardMarkup keyboard = keyboardService.createDateEventsListKeyboard(
                        selectedDate, events, user);
                    
                    messageService.editMessageText(chatId, messageId, message, keyboard);
                    messageService.answerCallbackQuery(callbackQueryId, "");
                    
                    log.info("Возврат к списку событий (прошлая дата): userId={}, дата={}, событий={}", 
                            userId, selectedDate, events.size());
                } else if ((isToday || isFutureDate) && !hasEvents) {
                    // Сегодняшняя или будущая дата без событий - возвращаем к календарю месяца
                    int year = selectedDate.getYear();
                    int month = selectedDate.getMonthValue();
                    
                    InlineKeyboardMarkup keyboard = keyboardService.createViewCalendarKeyboard(year, month, user);
                    String message = messageBuilder.buildCalendarViewMessage();
                    
                    messageService.editMessageText(chatId, messageId, message, keyboard);
                    messageService.answerCallbackQuery(callbackQueryId, "");
                    
                    log.info("Возврат к календарю (сегодня или будущая дата без событий): userId={}, дата={}", 
                            userId, selectedDate);
                } else {
                    // Сегодняшняя или будущая дата с событиями - показываем экран управления событиями
                    String message = messageBuilder.buildDateEventsManagementMessage(selectedDate, events);
                    InlineKeyboardMarkup keyboard = keyboardService.createDateEventsManagementKeyboard(
                        selectedDate, events, user);
                    
                    messageService.editMessageText(chatId, messageId, message, keyboard);
                    messageService.answerCallbackQuery(callbackQueryId, "");
                    
                    log.info("Возврат к экрану управления событиями (сегодня или будущая дата): userId={}, дата={}, событий={}", 
                            userId, selectedDate, events.size());
                }
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                log.error("Ошибка при возврате из создания события: userId={}, error={}", 
                         userId, e.getMessage());
                throw new RuntimeException("Ошибка при возврате из создания события", e);
            }
            
        } catch (Exception e) {
            log.error("Ошибка при возврате из создания события: userId={}, error={}", 
                     userId, e.getMessage());
            throw new RuntimeException("Ошибка при возврате из создания события", e);
        }
    }
}
