package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.MyEventsCommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

import java.time.ZoneId;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик callback queries для операций с событиями.
 * 
 * <p>Обрабатывает следующие типы callback:</p>
 * <ul>
 *   <li>view_event_ - просмотр деталей события</li>
 *   <li>edit_event_ - редактирование события</li>
 *   <li>delete_event_ - удаление события</li>
 *   <li>complete_event_ - завершение события</li>
 *   <li>edit_field_ - редактирование конкретного поля события</li>
 *   <li>back_to_reminder_ - возврат к минималистичному виду напоминания</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.2, 1.3, 2.1, 2.2, 2.5, 6.1</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.2.0
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventCallbackHandler implements CallbackHandler {
    
    private final MyEventsCommandHandler myEventsCommandHandler;
    private final TelegramMessageService messageService;
    private final ru.golubyatnikov.family.calendar.bot.service.ConversationStateService conversationStateService;
    private final ru.golubyatnikov.family.calendar.bot.service.KeyboardService keyboardService;
    private final ru.golubyatnikov.family.calendar.bot.service.EventService eventService;
    private final ru.golubyatnikov.family.calendar.bot.service.EventNotificationService eventNotificationService;
    private final ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder botMessageBuilder;
    private final ru.golubyatnikov.family.calendar.bot.service.ReminderService reminderService;
    private final ru.golubyatnikov.family.calendar.bot.service.UserService userService;
    
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
        
        log.debug("Обработка callback для события: data='{}', userId={}", 
                callbackData, user.getId());
        
        if (CallbackPrefix.VIEW_EVENT_FROM_REMINDER.matches(callbackData)) {
            handleViewEventFromReminder(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.VIEW_EVENT.matches(callbackData)) {
            handleViewEvent(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.EDIT_EVENT.matches(callbackData)) {
            handleEditEvent(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.DELETE_EVENT.matches(callbackData)) {
            handleDeleteEvent(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.EDIT_FIELD.matches(callbackData)) {
            handleEditField(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.COMPLETE_EVENT.matches(callbackData)) {
            handleCompleteEvent(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.EDIT_CANCEL.matches(callbackData)) {
            handleEditCancel(callbackData, user.getId(), chatId, callbackQueryId);
        } else if (CallbackPrefix.ADD_COMPLETION_NOTE.matches(callbackData)) {
            handleAddCompletionNote(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.SKIP_COMPLETION_NOTE.matches(callbackData)) {
            handleSkipCompletionNote(user.getId(), chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.BACK_TO_REMINDER.matches(callbackData)) {
            handleBackToReminder(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        }
    }
    
    /**
     * Обрабатывает просмотр деталей события.
     * 
     * <p>Метод обновляет текущее сообщение с полной информацией о событии
     * и стандартной клавиатурой с действиями над событием.</p>
     * 
     * <p><b>Требования:</b> 2.2, 2.4, 8.1, 8.2</p>
     * 
     * @param callbackData данные callback (формат: view_event_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для обновления
     * @param callbackQueryId идентификатор callback query
     */
    private void handleViewEvent(String callbackData, Long userId, Long chatId, 
                                 Integer messageId, String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.VIEW_EVENT);
        
        log.debug("Просмотр деталей события: eventId={}, userId={}, messageId={}", 
                 eventId, userId, messageId);
        
        try {
            // Получаем событие
            Event event = eventService.getEventById(eventId);
            
            log.debug("Событие загружено: eventId={}, userId={}", eventId, userId);
            log.debug("Определен контекст: Standard_Context, eventId={}, userId={}", 
                     eventId, userId);
            
            // Формируем текст сообщения с учетом флага isMyEventsHeader
            int eventCount = eventService.getActiveEventsCount(userId);
            String eventMessage = botMessageBuilder.buildEventMessageWithHeader(event, eventCount);
            
            // Используем стандартную клавиатуру с действиями
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
            
            log.debug("Используется стандартная клавиатура с действиями: eventId={}, userId={}", 
                     eventId, userId);
            
            // Обновляем сообщение
            messageService.editMessageText(chatId, messageId, eventMessage, keyboard);
            
            log.info("Детали события отображены: eventId={}, messageId={}, userId={}", 
                    eventId, messageId, userId);
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            log.warn("Событие не найдено при просмотре деталей: eventId={}, userId={}", 
                    eventId, userId, e);
            
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.notFound("Событие"));
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                log.error("Ошибка отправки callback query answer: eventId={}, userId={}, error={}", 
                         eventId, userId, ex.getMessage());
            }
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.warn("Ошибка Telegram API при просмотре деталей события: eventId={}, messageId={}, userId={}, error={}", 
                    eventId, messageId, userId, e.getMessage());
            
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                log.error("Ошибка отправки callback query answer: eventId={}, userId={}, error={}", 
                         eventId, userId, ex.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Неожиданная ошибка при просмотре деталей события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
            
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                log.error("Ошибка отправки callback query answer: eventId={}, userId={}, error={}", 
                         eventId, userId, ex.getMessage());
            }
        }
    }
    
    /**
     * Обрабатывает просмотр деталей события из уведомления о напоминании.
     * 
     * <p>Метод обновляет текущее сообщение с полной информацией о событии
     * и упрощенной клавиатурой с одной кнопкой "Назад к напоминанию".</p>
     * 
     * <p><b>Требования:</b> 2.1, 2.3, 3.1, 3.2, 7.1, 7.2, 7.4, 10.1, 10.2</p>
     * 
     * @param callbackData данные callback (формат: view_event_from_reminder_{eventId}_{reminderId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для обновления
     * @param callbackQueryId идентификатор callback query
     */
    private void handleViewEventFromReminder(String callbackData, Long userId, Long chatId, 
                                            Integer messageId, String callbackQueryId) {
        log.debug("Просмотр деталей события из напоминания: callbackData='{}', userId={}, messageId={}", 
                 callbackData, userId, messageId);
        
        try {
            // Извлекаем payload из callback data
            String payload = CallbackPrefix.VIEW_EVENT_FROM_REMINDER.extractPayload(callbackData);
            
            // Разделяем payload на eventId и reminderId
            String[] parts = payload.split("_", 2);
            
            // Валидация формата
            if (parts.length != 2) {
                log.error("Некорректный формат callback data для view_event_from_reminder: " +
                         "ожидается 2 части, получено {}. CallbackData='{}', userId={}", 
                         parts.length, callbackData, userId);
                
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.INVALID_REQUEST);
                return;
            }
            
            Long eventId;
            Long reminderId;
            
            // Парсинг eventId и reminderId с обработкой NumberFormatException
            try {
                eventId = Long.parseLong(parts[0]);
                reminderId = Long.parseLong(parts[1]);
                
                log.debug("Успешно извлечены данные: eventId={}, reminderId={}, userId={}", 
                         eventId, reminderId, userId);
            } catch (NumberFormatException e) {
                log.error("Некорректный eventId или reminderId в callback data: " +
                         "eventId='{}', reminderId='{}', callbackData='{}', userId={}, error={}", 
                         parts[0], parts[1], callbackData, userId, e.getMessage());
                
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.INVALID_REQUEST);
                return;
            }
            
            // Получаем событие
            Event event = eventService.getEventById(eventId);
            
            log.debug("Событие загружено: eventId={}, userId={}", eventId, userId);
            log.debug("Определен контекст: Reminder_Context, eventId={}, reminderId={}, userId={}", 
                     eventId, reminderId, userId);
            
            // Загружаем напоминание из базы данных с eager загрузкой события И пользователя
            ru.golubyatnikov.family.calendar.bot.model.Reminder reminder = 
                reminderService.getReminderWithEventAndUser(reminderId);
            
            log.debug("Напоминание загружено с событием и пользователем: reminderId={}, eventId={}, userId={}", 
                     reminderId, eventId, userId);
            
            // Загружаем пользователя-получателя для получения timezone
            User recipient = userService.findById(userId)
                .orElseThrow(() -> new ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException(
                    "Пользователь не найден: userId=" + userId));
            
            // Получаем timezone получателя для форматирования
            ZoneId userTimezone = recipient.getTimezone() != null 
                ? ZoneId.of(recipient.getTimezone()) 
                : ZoneId.of("UTC");
            
            // Формируем текст сообщения с ПОЛНОЙ информацией о напоминании (с датой, временем, описанием)
            String eventMessage = reminderService.formatReminderMessageByType(reminder, userTimezone);
            
            // Создаем упрощенную клавиатуру с кнопкой "Назад к напоминанию"
            InlineKeyboardMarkup keyboard = createDetailsKeyboard(eventId, reminderId);
            
            log.debug("Создана упрощенная клавиатура для напоминания: eventId={}, reminderId={}, userId={}", 
                     eventId, reminderId, userId);
            
            // Обновляем сообщение
            messageService.editMessageText(chatId, messageId, eventMessage, keyboard);
            
            log.info("Детали события отображены из напоминания: eventId={}, reminderId={}, " +
                    "messageId={}, userId={}", eventId, reminderId, messageId, userId);
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            log.warn("Событие не найдено при просмотре деталей из напоминания: userId={}", userId, e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, 
                    CallbackMessageFormatter.notFound("Событие"));
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                log.error("Ошибка при отправке callback answer: {}", ex.getMessage());
            }
                
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.warn("Ошибка Telegram API при просмотре деталей из напоминания: " +
                    "messageId={}, userId={}, error={}", messageId, userId, e.getMessage());
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                log.error("Ошибка при отправке callback answer: {}", ex.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Неожиданная ошибка при просмотре деталей из напоминания: " +
                     "userId={}, error={}", userId, e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                log.error("Ошибка при отправке callback answer: {}", ex.getMessage());
            }
        }
    }
    
    /**
     * Получает ID первого активного напоминания события.
     * 
     * @param event событие
     * @return ID первого активного напоминания или null если активных напоминаний нет
     */
    private Long getFirstActiveReminderId(Event event) {
        if (event.getReminders() == null || event.getReminders().isEmpty()) {
            return null;
        }
        
        return event.getReminders().stream()
            .filter(reminder -> reminder.getSent() != null && !reminder.getSent())
            .findFirst()
            .map(ru.golubyatnikov.family.calendar.bot.model.Reminder::getId)
            .orElse(null);
    }
    
    /**
     * Обрабатывает возврат к минималистичному виду напоминания.
     * 
     * <p>Метод восстанавливает исходный текст уведомления о напоминании
     * и упрощенную клавиатуру с одной кнопкой "Посмотреть детали".</p>
     * 
     * <p>Выполняет следующие действия:</p>
     * <ol>
     *   <li>Извлекает eventId и reminderId из callback data</li>
     *   <li>Загружает событие и напоминание из базы данных</li>
     *   <li>Восстанавливает текст напоминания через ReminderService.formatReminderMessageByType()</li>
     *   <li>Создает упрощенную клавиатуру через ReminderService.createSimplifiedReminderKeyboard()</li>
     *   <li>Обновляет сообщение через editMessageText()</li>
     *   <li>Отправляет callback query answer с подтверждением</li>
     * </ol>
     * 
     * <p>Обработка ошибок:</p>
     * <ul>
     *   <li>Событие не найдено - отправляется callback query answer с сообщением об ошибке</li>
     *   <li>Напоминание не найдено - отправляется callback query answer с сообщением об ошибке</li>
     *   <li>Ошибка Telegram API - логируется warning и отправляется callback query answer</li>
     *   <li>Некорректный формат callback data - логируется error и отправляется callback query answer</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 4.1, 4.2, 4.3, 4.4, 4.5, 6.1, 6.2, 6.3, 6.4, 6.5, 9.2, 9.3, 9.4, 9.5</p>
     * 
     * @param callbackData данные callback (формат: back_to_reminder_{eventId}_{reminderId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для обновления
     * @param callbackQueryId идентификатор callback query
     */
    private void handleBackToReminder(String callbackData, Long userId, Long chatId, 
                                      Integer messageId, String callbackQueryId) {
        log.debug("Возврат к напоминанию: callbackData='{}', userId={}, messageId={}", 
                 callbackData, userId, messageId);
        
        try {
            // Извлекаем payload из callback data
            String payload = CallbackPrefix.BACK_TO_REMINDER.extractPayload(callbackData);
            
            // Разделяем payload на eventId и reminderId
            String[] parts = payload.split("_", 2);
            
            // Валидация формата
            if (parts.length != 2) {
                log.error("Некорректный формат callback data для back_to_reminder: ожидается 2 части, получено {}. " +
                         "CallbackData='{}', userId={}", parts.length, callbackData, userId);
                
                try {
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.INVALID_REQUEST);
                } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                    log.error("Ошибка отправки callback query answer: userId={}, error={}", 
                             userId, ex.getMessage());
                }
                return;
            }
            
            Long eventId;
            Long reminderId;
            
            // Парсинг eventId и reminderId с обработкой NumberFormatException
            try {
                eventId = Long.parseLong(parts[0]);
                reminderId = Long.parseLong(parts[1]);
                
                log.debug("Успешно извлечены данные: eventId={}, reminderId={}, userId={}", 
                         eventId, reminderId, userId);
            } catch (NumberFormatException e) {
                log.error("Некорректный eventId или reminderId в callback data: eventId='{}', reminderId='{}', " +
                         "callbackData='{}', userId={}, error={}", 
                         parts[0], parts[1], callbackData, userId, e.getMessage());
                
                try {
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.INVALID_REQUEST);
                } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                    log.error("Ошибка отправки callback query answer: userId={}, error={}", 
                             userId, ex.getMessage());
                }
                return;
            }
            
            // Загружаем напоминание с eager загрузкой события и пользователя
            ru.golubyatnikov.family.calendar.bot.model.Reminder reminder = 
                reminderService.getReminderWithEventAndUser(reminderId);
            
            log.debug("Напоминание загружено с событием и пользователем: reminderId={}, eventId={}, userId={}", 
                     reminderId, reminder.getEvent().getId(), userId);
            
            // Получаем событие из напоминания
            Event event = reminder.getEvent();
            
            // Получаем timezone создателя события
            ZoneId creatorTimezone = event.getUser().getTimezone() != null 
                ? ZoneId.of(event.getUser().getTimezone()) 
                : ZoneId.of("UTC");
            
            log.debug("Timezone создателя события получен: eventId={}, creatorTimezone={}, userId={}", 
                     event.getId(), creatorTimezone, userId);
            
            // Восстанавливаем КОРОТКИЙ текст напоминания
            String reminderMessage = reminderService.formatShortReminderMessage(reminder, creatorTimezone);
            
            log.debug("Текст напоминания восстановлен: eventId={}, reminderId={}, userId={}", 
                     eventId, reminderId, userId);
            
            // Создаем упрощенную клавиатуру
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = 
                reminderService.createSimplifiedReminderKeyboard(event, reminderId);
            
            log.debug("Упрощенная клавиатура создана: eventId={}, reminderId={}, userId={}", 
                     eventId, reminderId, userId);
            
            // Обновляем сообщение
            messageService.editMessageText(chatId, messageId, reminderMessage, keyboard);
            
            log.info("Возврат к напоминанию выполнен: eventId={}, reminderId={}, messageId={}, userId={}", 
                    eventId, reminderId, messageId, userId);
            
            // Отвечаем на callback query с подтверждением
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.ReminderNotFoundException e) {
            log.warn("Напоминание не найдено при возврате к напоминанию: userId={}", userId, e);
            
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.notFound("Напоминание"));
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                log.error("Ошибка отправки callback query answer: userId={}, error={}", 
                         userId, ex.getMessage());
            }
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.warn("Ошибка Telegram API при возврате к напоминанию: messageId={}, userId={}, error={}", 
                    messageId, userId, e.getMessage());
            
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                log.error("Ошибка отправки callback query answer: userId={}, error={}", 
                         userId, ex.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Неожиданная ошибка при возврате к напоминанию: userId={}, error={}", 
                     userId, e.getMessage(), e);
            
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                log.error("Ошибка отправки callback query answer: userId={}, error={}", 
                         userId, ex.getMessage());
            }
        }
    }
    
    /**
     * Обрабатывает редактирование события.
     * 
     * <p>Обновляет текущее сообщение, показывая меню выбора поля для редактирования.</p>
     * 
     * @param callbackData данные callback (формат: edit_event_{eventId})
     * @param user объект пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleEditEvent(String callbackData, User user, Long chatId, 
                                 Integer messageId, String callbackQueryId) {
        Long userId = user.getId();
        Long eventId = extractEventId(callbackData, CallbackPrefix.EDIT_EVENT);
        
        log.info("Редактирование события ID={} пользователем ID={}", eventId, userId);
        
        try {
            // Получаем событие и проверяем права доступа
            var event = eventService.getEventById(eventId);
            
            // Проверяем права доступа
            if (!event.getUser().getId().equals(userId)) {
                log.warn("Пользователь ID={} не имеет прав для редактирования события ID={}", 
                        userId, eventId);
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.NO_ACCESS);
                return;
            }
            
            // ИЗМЕНЕНИЕ: Сохраняем messageId в контексте редактирования
            conversationStateService.startEventEditing(userId, eventId, chatId, messageId);
            
            // Формируем сообщение с текущими данными события и клавиатурой выбора поля
            String message = buildEditFieldSelectionMessage(event);
            InlineKeyboardMarkup keyboard = keyboardService.createEditFieldSelectionKeyboard(eventId);
            
            // ИЗМЕНЕНИЕ: Обновляем ТЕКУЩЕЕ сообщение вместо отправки нового
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
            log.debug("Начато редактирование события ID={} в сообщении ID={} пользователем ID={}", 
                     eventId, messageId, userId);
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при редактировании события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при редактировании события", e);
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
        message.append("📝 ").append(bold("Редактирование события")).append("\n\n");
        message.append(botMessageBuilder.buildEventMessage(event));
        message.append("\n\n").append("Выберите поле для редактирования:");
        return message.toString();
    }
    
    /**
     * Обрабатывает удаление события.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Вызывает eventService.deleteEvent() для удаления события</li>
     *   <li>Отвечает на callback query с текстом "Событие удалено"</li>
     * </ol>
     * 
     * <p>EventService.deleteEvent() автоматически:</p>
     * <ul>
     *   <li>Удаляет сообщение события из чата</li>
     *   <li>Обновляет статус события на DELETED</li>
     *   <li>Сбрасывает messageId и isMyEventsHeader</li>
     *   <li>Вызывает updateMyEventsHeaderAfterRemoval для обновления шапки</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.4</p>
     * 
     * @param callbackData данные callback (формат: delete_event_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения, из которого был вызван callback
     * @param callbackQueryId идентификатор callback query
     */
    private void handleDeleteEvent(String callbackData, Long userId, Long chatId, 
                                   Integer messageId, String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.DELETE_EVENT);
        
        log.info("Удаление события ID={} пользователем ID={}", eventId, userId);
        
        try {
            // Удаляем событие (перемещаем в корзину)
            // EventService автоматически удалит сообщение и обновит шапку /my_events
            eventService.deleteEvent(eventId, userId);
            
            // Отвечаем на callback query с подтверждением
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.DELETED);
            
            log.debug("Событие ID={} успешно удалено пользователем ID={}", eventId, userId);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            // Обработка ошибок без отправки сообщений
            log.error("Событие не найдено: eventId={}, userId={}", eventId, userId, e);
        } catch (ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
            // Обработка ошибок без отправки сообщений
            log.error("Нет прав на удаление события: eventId={}, userId={}", eventId, userId, e);
        } catch (Exception e) {
            // Обработка других ошибок без отправки сообщений
            log.error("Ошибка при удалении события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
        }
    }
    
    /**
     * Обрабатывает редактирование конкретного поля события.
     * 
     * <p>Извлекает имя поля и ID события из callback data формата edit_field_{field}_{eventId},
     * устанавливает состояние редактирования и отправляет соответствующее сообщение пользователю.</p>
     * 
     * @param callbackData данные callback (формат: edit_field_{field}_{eventId})
     * @param user объект пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleEditField(String callbackData, User user, Long chatId, 
                                 Integer messageId, String callbackQueryId) {
        Long userId = user.getId();
        try {
            // Извлекаем payload после префикса edit_field_
            String payload = CallbackPrefix.EDIT_FIELD.extractPayload(callbackData);
            
            log.debug("Извлечен payload из callback data: payload='{}', userId={}", payload, userId);
            
            // Разделяем payload на поле и eventId
            String[] parts = payload.split("_", 2);
            
            // Валидация формата
            if (parts.length != 2) {
                log.error("Некорректный формат callback data: ожидается 2 части, получено {}. " +
                         "CallbackData='{}', userId={}", parts.length, callbackData, userId);
                messageService.editMessageText(chatId, messageId, 
                    "❌ Произошла ошибка при обработке запроса", null);
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
                return;
            }
            
            String field = parts[0];
            Long eventId;
            
            // Парсинг eventId с обработкой NumberFormatException
            try {
                eventId = Long.parseLong(parts[1]);
                log.debug("Успешно извлечены данные: field='{}', eventId={}, userId={}", 
                         field, eventId, userId);
            } catch (NumberFormatException e) {
                log.error("Некорректный eventId в callback data: eventId='{}', callbackData='{}', " +
                         "userId={}, error={}", parts[1], callbackData, userId, e.getMessage());
                messageService.editMessageText(chatId, messageId, 
                    "❌ Произошла ошибка при обработке запроса", null);
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
                return;
            }
            
            log.info("Пользователь ID={} начал редактирование поля '{}' события ID={}", 
                    userId, field, eventId);
            
            // ИЗМЕНЕНИЕ: Сохраняем messageId в контексте редактирования
            conversationStateService.startEventEditing(userId, eventId, chatId, messageId);
            
            // Устанавливаем редактируемое поле
            ru.golubyatnikov.family.calendar.bot.service.ConversationStateService.EditField editField = mapToEditField(field);
            if (editField != null) {
                conversationStateService.setEditingField(userId, editField);
                log.debug("Установлено состояние редактирования: userId={}, eventId={}, field={}, messageId={}", 
                         userId, eventId, editField, messageId);
            }
            
            // Формируем сообщение и клавиатуру в зависимости от поля
            String message;
            InlineKeyboardMarkup keyboard = null;
            
            switch (field) {
                case "date" -> {
                    log.debug("Выбрано поле для редактирования: DATE, userId={}", userId);
                    message = "📅 Редактирование даты\n\nВыберите новую дату из календаря:";
                    // Используем текущий месяц для начального отображения с учетом timezone пользователя
                    java.time.LocalDate now = user.getCurrentDate();
                    keyboard = keyboardService.createCalendarKeyboard(
                        now.getYear(), 
                        now.getMonthValue(), 
                        user
                    );
                    // НЕ добавляем кнопку "Отмена", так как она уже есть в календаре
                }
                case "time" -> {
                    log.debug("Выбрано поле для редактирования: TIME, userId={}", userId);
                    message = "🕐 Редактирование времени\n\nВыберите новое время:";
                    
                    // Получаем событие для определения даты
                    ru.golubyatnikov.family.calendar.bot.model.Event event = eventService.getEventById(eventId);
                    java.time.LocalDate eventDate = event.getEventDate();
                    
                    // Показываем фильтрованный выбор часа с учетом даты события и timezone пользователя
                    keyboard = keyboardService.createFilteredHourSelectionKeyboard(eventDate, user);
                    
                    // НЕ добавляем кнопку "Отменить", так как она уже есть в фильтрованной клавиатуре
                    // (кнопка "❌ Отмена" с callback "time_cancel")
                }
                case "title" -> {
                    log.debug("Выбрано поле для редактирования: TITLE, userId={}", userId);
                    message = "📝 Редактирование названия\n\nОтправьте новое название события:";
                    // Создаем клавиатуру только с кнопкой "Отменить"
                    keyboard = createCancelOnlyKeyboard(eventId);
                }
                case "description" -> {
                    log.debug("Выбрано поле для редактирования: DESCRIPTION, userId={}", userId);
                    message = "📄 Редактирование описания\n\nОтправьте новое описание события:";
                    // Создаем клавиатуру только с кнопкой "Отменить"
                    keyboard = createCancelOnlyKeyboard(eventId);
                }
                default -> {
                    log.warn("Неизвестное поле для редактирования: field='{}', userId={}", field, userId);
                    message = "❌ Неизвестное поле для редактирования";
                }
            }
            
            // ИЗМЕНЕНИЕ: Обновляем ТЕКУЩЕЕ сообщение вместо отправки нового
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка Telegram API при редактировании поля: userId={}, callbackData='{}', error={}", 
                     userId, callbackData, e.getMessage(), e);
            throw new RuntimeException("Ошибка при редактировании поля", e);
        }
    }
    
    /**
     * Добавляет кнопку "Отмена" к существующей клавиатуре.
     * 
     * <p>Если клавиатура null, создает новую клавиатуру только с кнопкой "Отмена".</p>
     * 
     * @param keyboard существующая клавиатура или null
     * @param eventId идентификатор события для callback data
     * @return клавиатура с добавленной кнопкой "Отмена"
     */
    private InlineKeyboardMarkup addCancelButton(InlineKeyboardMarkup keyboard, Long eventId) {
        if (keyboard == null) {
            return createCancelOnlyKeyboard(eventId);
        }
        
        java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = 
            new java.util.ArrayList<>(keyboard.getKeyboard());
        
        // Добавляем кнопку "Отмена" в последнюю строку
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> cancelRow = 
            new java.util.ArrayList<>();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton cancelButton = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        cancelButton.setText("❌ Отмена");
        cancelButton.setCallbackData(CallbackPrefix.EDIT_CANCEL.withPayload(eventId.toString()));
        cancelRow.add(cancelButton);
        rows.add(cancelRow);
        
        keyboard.setKeyboard(rows);
        return keyboard;
    }
    
    /**
     * Создает клавиатуру только с кнопкой "Отмена".
     * 
     * <p>Используется для режимов ожидания текстового ввода (название, описание).</p>
     * 
     * @param eventId идентификатор события для callback data
     * @return клавиатура с кнопкой "Отмена"
     */
    private InlineKeyboardMarkup createCancelOnlyKeyboard(Long eventId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
            new java.util.ArrayList<>();
        
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = 
            new java.util.ArrayList<>();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton cancelButton = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        cancelButton.setText("❌ Отмена");
        cancelButton.setCallbackData(CallbackPrefix.EDIT_CANCEL.withPayload(eventId.toString()));
        row.add(cancelButton);
        keyboard.add(row);
        
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    /**
     * Создает клавиатуру для просмотра деталей события из напоминания.
     * 
     * <p>Клавиатура содержит только одну кнопку "◀️ Назад к напоминанию"
     * без кнопок редактирования, удаления или завершения.</p>
     * 
     * <p>Используется при просмотре деталей события из уведомления о напоминании,
     * чтобы пользователь мог вернуться к минималистичному виду напоминания.</p>
     * 
     * <p><b>Требования:</b> 3.1, 3.5</p>
     * 
     * @param eventId идентификатор события
     * @param reminderId идентификатор напоминания
     * @return inline-клавиатура с кнопкой "Назад к напоминанию"
     */
    private InlineKeyboardMarkup createDetailsKeyboard(Long eventId, Long reminderId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
            new java.util.ArrayList<>();
        
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = 
            new java.util.ArrayList<>();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton backButton = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        backButton.setText("◀️ Назад к напоминанию");
        backButton.setCallbackData(CallbackPrefix.BACK_TO_REMINDER.withPayload(eventId + "_" + reminderId));
        row.add(backButton);
        keyboard.add(row);
        
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    /**
     * Извлекает ID события из callback data.
     * 
     * @param callbackData строка callback data
     * @param prefix префикс для извлечения payload
     * @return ID события
     */
    private Long extractEventId(String callbackData, CallbackPrefix prefix) {
        String payload = prefix.extractPayload(callbackData);
        return Long.parseLong(payload);
    }
    
    /**
     * Преобразует строковое представление поля в EditField enum.
     * 
     * <p>Используется для маппинга строковых значений полей из callback data
     * в типизированный enum для установки состояния редактирования.</p>
     * 
     * @param fieldName строковое имя поля (date, time, title, description)
     * @return соответствующий EditField или null если поле неизвестно
     */
    private ru.golubyatnikov.family.calendar.bot.service.ConversationStateService.EditField mapToEditField(String fieldName) {
        return switch (fieldName) {
            case "date" -> ru.golubyatnikov.family.calendar.bot.service.ConversationStateService.EditField.DATE;
            case "time" -> ru.golubyatnikov.family.calendar.bot.service.ConversationStateService.EditField.TIME;
            case "title" -> ru.golubyatnikov.family.calendar.bot.service.ConversationStateService.EditField.TITLE;
            case "description" -> ru.golubyatnikov.family.calendar.bot.service.ConversationStateService.EditField.DESCRIPTION;
            default -> null;
        };
    }
    
    /**
     * Обрабатывает завершение события с переупорядочиванием списка.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Извлекает eventId из callback data</li>
     *   <li>Вызывает EventService.completeEventWithReordering() для завершения события с переупорядочиванием</li>
     *   <li>Сохраняет контекст с обновлённым messageId из completedEvent для последующего редактирования</li>
     *   <li>Отвечает на callback query</li>
     * </ol>
     * 
     * <p>EventService.completeEventWithReordering():</p>
     * <ul>
     *   <li>Завершает событие (статус → COMPLETED)</li>
     *   <li>Проверяет позицию события в списке</li>
     *   <li>Если событие не последнее - переупорядочивает список "Мои события":</li>
     *   <ul>
     *     <li>Удаляет все сообщения активных событий из чата</li>
     *     <li>Формирует новый порядок: активные события + завершённое</li>
     *     <li>Отправляет события заново с обновлённой шапкой</li>
     *     <li>Сохраняет новые messageId для всех событий</li>
     *   </ul>
     *   <li>Отправляет завершённое событие с предложением добавить заметку</li>
     * </ul>
     * 
     * <p>Переупорядочивание обеспечивает, что завершённое событие отображается внизу списка,
     * а все активные события остаются выше. Это позволяет пользователю комфортно добавлять
     * заметку о завершении, видя контекст оставшихся активных событий.</p>
     * 
     * <p>Все ошибки обрабатываются через аннотацию @HandleCallbackErrors.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 2.1</p>
     * 
     * @param callbackData данные callback (формат: complete_event_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения, из которого был вызван callback (не используется)
     * @param callbackQueryId идентификатор callback query
     */
    private void handleCompleteEvent(String callbackData, Long userId, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.COMPLETE_EVENT);
        
        log.debug("Начало обработки завершения события с переупорядочиванием: eventId={}, userId={}", 
                 eventId, userId);
        
        try {
            // Завершаем событие с переупорядочиванием списка
            // Метод автоматически переупорядочивает список, если событие не последнее,
            // и отправляет завершённое событие с предложением добавить заметку
            Event completedEvent = eventService.completeEventWithReordering(eventId, userId);
            
            log.info("Событие ID={} успешно завершено с переупорядочиванием пользователем ID={}", 
                    eventId, userId);
            
            // Сохраняем контекст для добавления заметки
            // Используем обновлённый messageId из completedEvent после переупорядочивания
            Integer updatedMessageId = completedEvent.getMessageId() != null 
                ? completedEvent.getMessageId().intValue() 
                : null;
            
            conversationStateService.setAwaitingCompletionNote(
                userId, 
                eventId, 
                chatId, 
                updatedMessageId
            );
            
            log.debug("Контекст сохранён для добавления заметки: eventId={}, messageId={}, userId={}", 
                     eventId, updatedMessageId, userId);
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            // Обработка ошибок без отправки сообщений
            log.error("Событие не найдено: eventId={}, userId={}", eventId, userId, e);
        } catch (ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
            // Обработка ошибок без отправки сообщений
            log.error("Нет прав на завершение события: eventId={}, userId={}", eventId, userId, e);
        } catch (IllegalStateException e) {
            // Обработка ошибок без отправки сообщений
            log.error("Неверное состояние события: eventId={}, userId={}", eventId, userId, e);
        } catch (Exception e) {
            // Обработка других ошибок без отправки сообщений
            log.error("Ошибка при завершении события с переупорядочиванием: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
        }
    }
    
    /**
     * Обрабатывает нажатие кнопки "Добавить заметку" к завершенному событию.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Извлекает eventId из callback data</li>
     *   <li>Редактирует текущее сообщение с просьбой ввести текст заметки</li>
     *   <li>Устанавливает состояние ожидания заметки с messageId</li>
     *   <li>Отвечает на callback query</li>
     * </ol>
     * 
     * <p>При ошибке редактирования сообщения используется fallback на отправку нового сообщения.</p>
     * 
     * <p><b>Требования:</b> 1.2, 2.2</p>
     * 
     * @param callbackData данные callback (формат: add_completion_note_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query
     */
    private void handleAddCompletionNote(String callbackData, Long userId, Long chatId, 
                                        Integer messageId, String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.ADD_COMPLETION_NOTE);
        
        log.debug("Начало обработки добавления заметки к событию: eventId={}, userId={}, messageId={}", 
                 eventId, userId, messageId);
        
        try {
            // Формируем сообщение с просьбой ввести заметку
            String message = formatMessage(
                "📝 Напишите заметку о том, как прошло событие.\n\n" +
                "Например, что было сделано, какие были результаты или впечатления."
            );
            
            try {
                // Пытаемся отредактировать текущее сообщение
                messageService.editMessageText(chatId, messageId, message, null);
                
                // Устанавливаем состояние ожидания заметки с messageId
                conversationStateService.setAwaitingCompletionNote(userId, eventId, chatId, messageId);
                
                log.info("Пользователь ID={} переведен в режим ожидания заметки для события ID={}, messageId={}", 
                        userId, eventId, messageId);
                
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                // Fallback: если редактирование не удалось, отправляем новое сообщение
                log.warn("Ошибка редактирования сообщения при добавлении заметки, используем fallback: eventId={}, messageId={}, error={}", 
                        eventId, messageId, e.getMessage());
                
                messageService.sendMessage(chatId, message);
                
                // Устанавливаем состояние ожидания заметки без messageId
                conversationStateService.setAwaitingCompletionNote(userId, eventId, chatId, null);
                
                log.info("Пользователь ID={} переведен в режим ожидания заметки для события ID={} (fallback без messageId)", 
                        userId, eventId);
            }
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка Telegram API при обработке добавления заметки: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
            
            // Очищаем контекст при критической ошибке
            conversationStateService.clearAwaitingCompletionNote(userId);
            
            throw new RuntimeException("Ошибка при обработке добавления заметки", e);
        }
    }
    
    /**
     * Обрабатывает нажатие кнопки "Пропустить" при добавлении заметки к завершенному событию.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Получает контекст для доступа к eventId и messageId</li>
     *   <li>Редактирует сообщение с финальной карточкой события (без заметки)</li>
     *   <li>Очищает контекст ожидания заметки</li>
     *   <li>Обновляет шапку /my_events после завершения процесса</li>
     *   <li>Отвечает на callback query</li>
     * </ol>
     * 
     * <p><b>Важно:</b> Обновление шапки /my_events происходит ПОСЛЕ отображения карточки события.
     * Это гарантирует правильную последовательность сообщений:</p>
     * <ol>
     *   <li>Карточка завершенного события (без заметки)</li>
     *   <li>Сообщение "У вас пока нет созданных событий" (если список активных событий пуст)</li>
     * </ol>
     * 
     * <p><b>Обработка ошибок:</b></p>
     * <ul>
     *   <li>Контекст не найден - отправляется сообщение об истечении времени ожидания</li>
     *   <li>Событие не найдено - отправляется сообщение об ошибке</li>
     *   <li>Ошибка редактирования сообщения - используется fallback на отправку нового сообщения</li>
     * </ul>
     * 
     * <p><b>Реализуемые требования:</b></p>
     * <ul>
     *   <li><b>2.3:</b> Обновление шапки /my_events после пропуска заметки</li>
     *   <li><b>3.3:</b> Очистка контекста после пропуска заметки</li>
     * </ul>
     * 
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query
     */
    private void handleSkipCompletionNote(Long userId, Long chatId, Integer messageId, 
                                         String callbackQueryId) {
        log.info("Пользователь ID={} пропустил добавление заметки к завершенному событию", userId);
        
        try {
            // Получаем контекст для доступа к eventId
            ru.golubyatnikov.family.calendar.bot.service.ConversationStateService.CompletionNoteContext context = 
                conversationStateService.getCompletionNoteContext(userId);
            
            if (context == null) {
                log.warn("Контекст добавления заметки не найден для пользователя ID={}", userId);
                
                // Очищаем состояние на всякий случай
                conversationStateService.clearAwaitingCompletionNote(userId);
                
                // Отправляем сообщение об ошибке
                String errorMessage = formatMessage("❌ Время ожидания истекло. Попробуйте снова.");
                messageService.sendMessage(chatId, errorMessage);
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
                return;
            }
            
            Long eventId = context.getEventId();
            Integer contextMessageId = context.getMessageId();
            
            log.debug("Получен контекст для пропуска заметки: eventId={}, messageId={}, userId={}", 
                     eventId, contextMessageId, userId);
            
            // Получаем событие
            Event event = eventService.getEventById(eventId);
            
            // Формируем финальное сообщение с карточкой события
            String eventMessage = botMessageBuilder.buildCompletedEventMessage(event);
            
            // Используем messageId из контекста, если он есть, иначе из callback query
            Integer targetMessageId = contextMessageId != null ? contextMessageId : messageId;
            
            try {
                // Пытаемся отредактировать сообщение
                if (targetMessageId != null) {
                    messageService.editMessageText(chatId, targetMessageId, eventMessage, null);
                    
                    log.info("Сообщение отредактировано при пропуске заметки: eventId={}, messageId={}, userId={}", 
                            eventId, targetMessageId, userId);
                } else {
                    // Fallback: если messageId отсутствует, отправляем новое сообщение
                    log.warn("MessageId отсутствует при пропуске заметки, отправляем новое сообщение: eventId={}, userId={}", 
                            eventId, userId);
                    messageService.sendMessage(chatId, eventMessage);
                }
                
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                // Fallback: если редактирование не удалось, отправляем новое сообщение
                log.warn("Ошибка редактирования сообщения при пропуске заметки, используем fallback: eventId={}, messageId={}, error={}", 
                        eventId, targetMessageId, e.getMessage());
                
                messageService.sendMessage(chatId, eventMessage);
            }
            
            // Очищаем контекст
            conversationStateService.clearAwaitingCompletionNote(userId);
            
            log.info("Контекст очищен после пропуска заметки: userId={}, eventId={}", userId, eventId);
            
            // Обновляем шапку /my_events ПОСЛЕ отображения карточки события
            // Это обеспечивает правильную последовательность сообщений:
            // 1. Карточка завершенного события (без заметки)
            // 2. Сообщение "У вас пока нет созданных событий" (если список активных событий пуст)
            // Требования: 2.3
            eventNotificationService.updateMyEventsHeaderAfterRemoval(userId);
            
            log.info("Шапка /my_events обновлена после пропуска заметки к событию ID={}: userId={}", 
                    eventId, userId);
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            log.error("Событие не найдено при пропуске заметки: userId={}", userId, e);
            
            // Очищаем контекст при ошибке
            conversationStateService.clearAwaitingCompletionNote(userId);
            
            // Отправляем сообщение об ошибке
            try {
                String errorMessage = formatMessage("❌ Событие не найдено.");
                messageService.sendMessage(chatId, errorMessage);
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                log.error("Ошибка отправки сообщения об ошибке: userId={}", userId, ex);
            }
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка Telegram API при пропуске заметки: userId={}, error={}", 
                     userId, e.getMessage(), e);
            
            // Очищаем контекст при критической ошибке
            conversationStateService.clearAwaitingCompletionNote(userId);
            
            throw new RuntimeException("Ошибка при пропуске заметки", e);
        }
    }
    
    /**
     * Обрабатывает отмену редактирования события.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Получает messageId из EditingContext</li>
     *   <li>Очищает состояние редактирования</li>
     *   <li>Получает событие для отображения</li>
     *   <li>Обновляет то же сообщение через editMessageText с полной информацией о событии</li>
     *   <li>Если messageId не найден, использует fallback на sendOrUpdateEventMessage</li>
     * </ol>
     * 
     * @param callbackData данные callback (формат: edit_cancel_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param callbackQueryId идентификатор callback query
     */
    private void handleEditCancel(String callbackData, Long userId, Long chatId, String callbackQueryId) {
        String eventIdStr = CallbackPrefix.EDIT_CANCEL.extractPayload(callbackData);
        Long eventId = Long.parseLong(eventIdStr);
        
        log.info("Отмена редактирования события ID={} пользователем ID={}", eventId, userId);
        
        try {
            // Получаем messageId из контекста редактирования
            Integer messageId = conversationStateService.getEditingMessageId(userId);
            
            // Очищаем состояние редактирования
            conversationStateService.clearEventEditing(userId);
            
            // Получаем событие для отображения
            var event = eventService.getEventById(eventId);
            
            if (messageId != null) {
                // ИЗМЕНЕНИЕ: Обновляем то же сообщение, возвращая его к отображению события
                // Используем buildEventMessageWithHeader для сохранения шапки, если это первое событие
                int eventCount = eventService.getActiveEventsCount(event.getUser().getId());
                String eventMessage = botMessageBuilder.buildEventMessageWithHeader(event, eventCount);
                InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
                messageService.editMessageText(chatId, messageId, eventMessage, keyboard);
                
                log.debug("Сообщение обновлено при отмене редактирования: eventId={}, messageId={}", 
                         eventId, messageId);
            } else {
                // Fallback: если messageId не найден, отправляем новое сообщение
                log.warn("MessageId не найден в контексте редактирования, используем sendOrUpdateEventMessage: eventId={}, userId={}", 
                        eventId, userId);
                eventService.sendOrUpdateEventMessage(event, chatId);
            }
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.actionCancelled("Редактирование"));
            
            log.info("Редактирование события ID={} успешно отменено пользователем ID={}", eventId, userId);
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка Telegram API при отмене редактирования: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при отмене редактирования события", e);
        }
    }
}
