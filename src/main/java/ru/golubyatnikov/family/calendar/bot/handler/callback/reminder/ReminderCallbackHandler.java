package ru.golubyatnikov.family.calendar.bot.handler.callback.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Reminder;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.model.ReminderType;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.service.reminder.ReminderService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.formatMessage;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.italic;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;

/**
 * Обработчик callback-запросов для управления напоминаниями о событиях.
 * 
 * <p>Этот компонент обрабатывает все взаимодействия пользователя с напоминаниями через inline-кнопки:</p>
 * <ul>
 *   <li>Отображение меню выбора типов напоминаний</li>
 *   <li>Обработка выбора типов напоминаний (с поддержкой множественного выбора)</li>
 *   <li>Запрос и валидация пользовательского ввода для custom напоминаний</li>
 *   <li>Отображение списка настроенных напоминаний</li>
 *   <li>Удаление напоминаний</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.1, 1.2, 1.4, 2.1, 2.2, 3.1, 3.2, 3.3</p>
 * 
 * @author Family Calendar Bot
 * @version 1.0
 * @see ReminderService
 * @see Reminder
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderCallbackHandler {
    
    private final ReminderService reminderService;
    private final TelegramMessageService messageService;
    private final EventRepository eventRepository;
    private final ru.golubyatnikov.family.calendar.bot.service.event.EventService eventService;
    private final ru.golubyatnikov.family.calendar.bot.service.KeyboardService keyboardService;
    private final ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder botMessageBuilder;
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    
    // Хранилище выбранных типов напоминаний для каждого события (временное, в памяти)
    // В production следует использовать Redis или другое хранилище
    private final Set<String> selectedReminders = new HashSet<>();
    
    /**
     * Обрабатывает нажатие на кнопку "🔔 Настроить напоминания".
     * Отображает меню с типами напоминаний.
     * 
     * @param eventId идентификатор события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     */
    public void handleSetupReminders(Long eventId, Long chatId, Integer messageId, String callbackQueryId) {
        log.debug("Настройка напоминаний для события ID={}", eventId);
        
        try {
            StringBuilder message = new StringBuilder();
            message.append("🔔 ").append(bold("Настройка напоминаний")).append("\n\n");
            message.append("Выберите один или несколько типов напоминаний:\n\n");
            message.append("• Вечером накануне (20:00)\n");
            message.append("• За 1 час до события\n");
            message.append("• За 15 минут до события\n\n");
            message.append(italic("Нажмите на тип, чтобы выбрать или отменить выбор"));
            
            InlineKeyboardMarkup keyboard = createReminderTypesKeyboard(eventId);
            
            messageService.editMessageText(chatId, messageId, message.toString(), keyboard);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.selectPrompt("типы напоминаний"));
            
        } catch (Exception e) {
            log.error("Ошибка при настройке напоминаний: eventId={}, chatId={}, error={}, stackTrace={}", 
                    eventId, chatId, e.getMessage(), getStackTraceString(e), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: callbackQueryId={}, error={}, stackTrace={}", 
                        callbackQueryId, ex.getMessage(), getStackTraceString(ex), ex);
            }
        }
    }
    
    /**
     * Обрабатывает выбор типа напоминания.
     * Поддерживает множественный выбор через toggle механизм.
     * 
     * @param eventId идентификатор события
     * @param type тип напоминания
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     */
    public void handleReminderTypeSelection(Long eventId, ReminderType type, 
                                           Long chatId, Integer messageId, String callbackQueryId) {
        log.debug("Выбор типа напоминания {} для события ID={}", type, eventId);
        
        try {
            String key = eventId + "_" + type.name();
            
            // Toggle выбора
            if (selectedReminders.contains(key)) {
                selectedReminders.remove(key);
                log.debug("Отменен выбор типа {} для события ID={}", type, eventId);
            } else {
                selectedReminders.add(key);
                log.debug("Выбран тип {} для события ID={}", type, eventId);
            }
            
            // Обновляем клавиатуру с отметками выбранных типов
            InlineKeyboardMarkup keyboard = createReminderTypesKeyboard(eventId);
            String message = buildReminderSelectionMessage(eventId);
            
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, 
                selectedReminders.contains(key) ? CallbackMessages.SELECTED : CallbackMessages.CANCELLED);
            
        } catch (Exception e) {
            log.error("Ошибка при выборе типа напоминания: eventId={}, type={}, chatId={}, error={}, stackTrace={}", 
                    eventId, type, chatId, e.getMessage(), getStackTraceString(e), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: callbackQueryId={}, error={}, stackTrace={}", 
                        callbackQueryId, ex.getMessage(), getStackTraceString(ex), ex);
            }
        }
    }
    
    /**
     * Отображает список настроенных напоминаний для события.
     * 
     * @param eventId идентификатор события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     */
    public void handleViewReminders(Long eventId, Long chatId, Integer messageId) {
        log.debug("Просмотр напоминаний для события ID={}", eventId);
        
        try {
            List<Reminder> reminders = reminderService.getEventReminders(eventId);
            
            if (reminders.isEmpty()) {
                StringBuilder message = new StringBuilder();
                message.append("🔔 ").append(bold("Напоминания")).append("\n\n");
                message.append("Для этого события пока не настроено ни одного напоминания.\n\n");
                message.append("Используйте кнопку \"🔔 Настроить напоминания\" для добавления.");
                
                messageService.editMessageText(chatId, messageId, message.toString(), null);
                return;
            }
            
            StringBuilder message = new StringBuilder();
            message.append("🔔 ").append(bold("Настроенные напоминания")).append("\n\n");
            
            for (Reminder reminder : reminders) {
                message.append(formatReminder(reminder));
                message.append("\n");
            }
            
            InlineKeyboardMarkup keyboard = createRemindersListKeyboard(reminders);
            messageService.editMessageText(chatId, messageId, message.toString(), keyboard);
            
        } catch (Exception e) {
            log.error("Ошибка при просмотре напоминаний: eventId={}, chatId={}, error={}, stackTrace={}", 
                    eventId, chatId, e.getMessage(), getStackTraceString(e), e);
            try {
                messageService.sendMessage(chatId, "❌ Ошибка при загрузке напоминаний");
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения: chatId={}, error={}, stackTrace={}", 
                        chatId, ex.getMessage(), getStackTraceString(ex), ex);
            }
        }
    }
    
    /**
     * Обрабатывает удаление напоминания.
     * 
     * @param reminderId идентификатор напоминания
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     */
    public void handleDeleteReminder(Long reminderId, Long chatId, Integer messageId, String callbackQueryId) {
        log.debug("Удаление напоминания ID={}", reminderId);
        
        try {
            reminderService.deleteReminder(reminderId);
            
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.DELETED);
            
            StringBuilder message = new StringBuilder();
            message.append("✅ ").append(bold("Напоминание удалено")).append("\n\n");
            message.append("Напоминание успешно удалено.");
            
            messageService.editMessageText(chatId, messageId, message.toString(), null);
            
            log.debug("Напоминание ID={} успешно удалено", reminderId);
            
        } catch (Exception e) {
            log.error("Ошибка при удалении напоминания: reminderId={}, chatId={}, error={}, stackTrace={}", 
                    reminderId, chatId, e.getMessage(), getStackTraceString(e), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: callbackQueryId={}, error={}, stackTrace={}", 
                        callbackQueryId, ex.getMessage(), getStackTraceString(ex), ex);
            }
        }
    }
    
    /**
     * Обрабатывает отключение всех автоматических напоминаний для события.
     * 
     * <p>Этот метод вызывается при нажатии кнопки "🔕 Отключить напоминания".
     * Он удаляет все напоминания для события и обновляет сообщение с подтверждением.</p>
     * 
     * <p>После отключения напоминаний клавиатура события обновляется
     * с кнопкой "🔔 Включить напоминания" вместо "🔕 Отключить напоминания".</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 3.1, 3.2, 3.3, 3.4, 4.1, 4.3, 4.4, 4.5</p>
     * 
     * @param eventId идентификатор события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования (может быть null)
     * @param callbackQueryId идентификатор callback query для ответа
     */
    public void handleDisableReminders(Long eventId, Long chatId, Integer messageId, String callbackQueryId) {
        log.debug("Отключение автоматических напоминаний для события ID={}", eventId);
        
        try {
            // Отключаем все напоминания для события
            reminderService.disableRemindersForEvent(eventId);
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.SUCCESS);
            
            // Обновляем сообщение события с новой клавиатурой
            if (messageId != null) {
                try {
                    // Получаем событие
                    Event event = eventRepository.findById(eventId)
                        .orElseThrow(() -> new EventNotFoundException(eventId));
                    User user = event.getUser();
                    
                    // Формируем текст сообщения с учетом флага isMyEventsHeader
                    int eventCount = eventService.getActiveEventsCount(user.getId());
                    String messageText = botMessageBuilder.buildEventMessageWithHeader(event, eventCount);
                    
                    // Создаем клавиатуру с обновленной кнопкой напоминаний
                    InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, user.getId());
                    
                    // Обновляем сообщение
                    messageService.editMessageText(chatId, messageId, messageText, keyboard);
                    
                    log.debug("Сообщение события обновлено после отключения напоминаний: eventId={}, messageId={}", 
                             eventId, messageId);
                } catch (Exception e) {
                    log.warn("Не удалось обновить сообщение события после отключения напоминаний: eventId={}, messageId={}, error={}", 
                            eventId, messageId, e.getMessage());
                    // Не прерываем выполнение, так как напоминания уже отключены
                }
            }
            
            log.info("Автоматические напоминания отключены для события ID={}", eventId);
            
        } catch (Exception e) {
            log.error("Ошибка при отключении напоминаний: eventId={}, chatId={}, error={}, stackTrace={}", 
                    eventId, chatId, e.getMessage(), getStackTraceString(e), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: callbackQueryId={}, error={}, stackTrace={}", 
                        callbackQueryId, ex.getMessage(), getStackTraceString(ex), ex);
            }
        }
    }
    
    /**
     * Обрабатывает включение автоматических напоминаний для события.
     * 
     * <p>Этот метод вызывается при нажатии кнопки "🔔 Включить напоминания".
     * Он создает стандартный набор автоматических напоминаний для события
     * и обновляет сообщение с подтверждением.</p>
     * 
     * <p>После включения напоминаний клавиатура события обновляется
     * с кнопкой "🔕 Отключить напоминания" вместо "🔔 Включить напоминания".</p>
     * 
     * <p><b>Требования:</b> 13.5</p>
     * 
     * @param eventId идентификатор события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования (может быть null)
     * @param callbackQueryId идентификатор callback query для ответа
     */
    public void handleEnableReminders(Long eventId, Long chatId, Integer messageId, String callbackQueryId) {
        log.debug("Включение автоматических напоминаний для события ID={}", eventId);
        
        try {
            // Получаем событие с eager загрузкой пользователя для предотвращения LazyInitializationException
            Event event = eventRepository.findByIdWithUser(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
            User user = event.getUser();
            
            // Проверяем, что User инициализирован
            if (user == null) {
                log.error("User is null для события ID {}", eventId);
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.notFound("Пользователь"));
                return;
            }
            
            // Создаем автоматические напоминания
            List<Reminder> createdReminders = reminderService.createDefaultReminders(event, user);
            
            // Формируем сообщение в зависимости от результата
            String responseMessage;
            if (createdReminders.isEmpty()) {
                if (event.getEventTime() == null) {
                    responseMessage = CallbackMessages.REMINDER_NEEDS_TIME;
                } else {
                    responseMessage = CallbackMessages.REMINDER_TOO_SOON;
                }
            } else {
                responseMessage = CallbackMessages.SUCCESS;
            }
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, responseMessage);
            
            // Обновляем сообщение события с новой клавиатурой
            if (messageId != null) {
                try {
                    // Формируем текст сообщения с учетом флага isMyEventsHeader
                    int eventCount = eventService.getActiveEventsCount(user.getId());
                    String messageText = botMessageBuilder.buildEventMessageWithHeader(event, eventCount);
                    
                    // Создаем клавиатуру с обновленной кнопкой напоминаний
                    InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, user.getId());
                    
                    // Обновляем сообщение
                    messageService.editMessageText(chatId, messageId, messageText, keyboard);
                    
                    log.debug("Сообщение события обновлено после включения напоминаний: eventId={}, messageId={}", 
                             eventId, messageId);
                } catch (Exception e) {
                    log.warn("Не удалось обновить сообщение события после включения напоминаний: eventId={}, messageId={}, error={}", 
                            eventId, messageId, e.getMessage());
                    // Не прерываем выполнение, так как операция включения напоминаний уже выполнена
                }
            }
            
            log.info("Автоматические напоминания включены для события ID={}, создано напоминаний: {}", 
                    eventId, createdReminders.size());
            
        } catch (org.hibernate.LazyInitializationException e) {
            log.error("LazyInitializationException при включении напоминаний: eventId={}, chatId={}, error={}", 
                    eventId, chatId, e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: callbackQueryId={}, error={}, stackTrace={}", 
                        callbackQueryId, ex.getMessage(), getStackTraceString(ex), ex);
            }
        } catch (Exception e) {
            log.error("Ошибка при включении напоминаний: eventId={}, chatId={}, error={}, stackTrace={}", 
                    eventId, chatId, e.getMessage(), getStackTraceString(e), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: callbackQueryId={}, error={}, stackTrace={}", 
                        callbackQueryId, ex.getMessage(), getStackTraceString(ex), ex);
            }
        }
    }
    
    /**
     * Завершает настройку напоминаний и создает выбранные напоминания.
     * 
     * @param eventId идентификатор события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     */
    public void handleConfirmReminders(Long eventId, Long chatId, Integer messageId, String callbackQueryId) {
        log.debug("Подтверждение напоминаний для события ID={}", eventId);
        
        try {
            // Собираем выбранные типы
            List<ReminderType> selectedTypes = new ArrayList<>();
            for (String key : selectedReminders) {
                if (key.startsWith(eventId + "_")) {
                    String typeName = key.substring((eventId + "_").length());
                    try {
                        ReminderType type = ReminderType.valueOf(typeName);
                        selectedTypes.add(type);
                    } catch (IllegalArgumentException e) {
                        log.warn("Неизвестный тип напоминания: {}", typeName);
                    }
                }
            }
            
            if (selectedTypes.isEmpty()) {
                messageService.answerCallbackQuery(callbackQueryId, 
                    String.format(CallbackMessages.VALIDATION_REQUIRED, "один тип"));
                return;
            }
            
            // Создаем напоминания
            List<Reminder> createdReminders = reminderService.createReminders(eventId, selectedTypes);
            
            // Очищаем выбранные типы для этого события
            selectedReminders.removeIf(key -> key.startsWith(eventId + "_"));
            
            StringBuilder message = new StringBuilder();
            message.append("✅ ").append(bold("Напоминания созданы")).append("\n\n");
            message.append(formatMessage("Создано напоминаний: %d\n\n", createdReminders.size()));
            
            for (Reminder reminder : createdReminders) {
                message.append(formatReminder(reminder));
                message.append("\n");
            }
            
            messageService.editMessageText(chatId, messageId, message.toString(), null);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.CREATED);
            
            log.debug("Создано {} напоминаний для события ID={}", createdReminders.size(), eventId);
            
        } catch (Exception e) {
            log.error("Ошибка при создании напоминаний: eventId={}, chatId={}, error={}, stackTrace={}", 
                    eventId, chatId, e.getMessage(), getStackTraceString(e), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError(e.getMessage()));
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: callbackQueryId={}, error={}, stackTrace={}", 
                        callbackQueryId, ex.getMessage(), getStackTraceString(ex), ex);
            }
        }
    }
    
    /**
     * Создает inline-клавиатуру с типами напоминаний.
     * 
     * @param eventId идентификатор события
     * @return InlineKeyboardMarkup с кнопками типов
     */
    private InlineKeyboardMarkup createReminderTypesKeyboard(Long eventId) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопки типов напоминаний
        addReminderTypeButton(keyboard, eventId, ReminderType.EVENING_BEFORE, "🌙 Вечером накануне");
        addReminderTypeButton(keyboard, eventId, ReminderType.ONE_HOUR_BEFORE, "⏰ За 1 час");
        addReminderTypeButton(keyboard, eventId, ReminderType.FIFTEEN_MINUTES_BEFORE, "⏱️ За 15 минут");
        
        // Кнопка подтверждения
        List<InlineKeyboardButton> confirmRow = new ArrayList<>();
        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText("✅ Создать напоминания");
        confirmButton.setCallbackData("confirm_reminders_" + eventId);
        confirmRow.add(confirmButton);
        keyboard.add(confirmRow);
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    /**
     * Добавляет кнопку типа напоминания в клавиатуру.
     * 
     * @param keyboard клавиатура для добавления
     * @param eventId идентификатор события
     * @param type тип напоминания
     * @param text текст кнопки
     */
    private void addReminderTypeButton(List<List<InlineKeyboardButton>> keyboard, Long eventId, 
                                      ReminderType type, String text) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton button = new InlineKeyboardButton();
        
        String key = eventId + "_" + type.name();
        boolean isSelected = selectedReminders.contains(key);
        
        button.setText((isSelected ? "✅ " : "") + text);
        button.setCallbackData("toggle_reminder_" + eventId + "_" + type.name());
        row.add(button);
        keyboard.add(row);
    }
    
    /**
     * Создает inline-клавиатуру со списком напоминаний и кнопками удаления.
     * 
     * @param reminders список напоминаний
     * @return InlineKeyboardMarkup с кнопками
     */
    private InlineKeyboardMarkup createRemindersListKeyboard(List<Reminder> reminders) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        for (Reminder reminder : reminders) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton deleteButton = new InlineKeyboardButton();
            deleteButton.setText("🗑️ Удалить " + getReminderTypeText(reminder.getReminderType()));
            deleteButton.setCallbackData("delete_reminder_" + reminder.getId());
            row.add(deleteButton);
            keyboard.add(row);
        }
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    /**
     * Форматирует напоминание для отображения.
     * 
     * @param reminder напоминание
     * @return отформатированная строка
     */
    private String formatReminder(Reminder reminder) {
        String typeText = getReminderTypeText(reminder.getReminderType());
        String timeText = reminder.getReminderTime().format(TIME_FORMATTER);
        
        return String.format("• %s\n  Время: %s", typeText, timeText);
    }
    
    /**
     * Возвращает текстовое описание типа напоминания.
     * 
     * @param type тип напоминания
     * @return текстовое описание
     */
    private String getReminderTypeText(ReminderType type) {
        switch (type) {
            case EVENING_BEFORE:
                return "Вечером накануне";
            case ONE_HOUR_BEFORE:
                return "За 1 час";
            case FIFTEEN_MINUTES_BEFORE:
                return "За 15 минут";
            default:
                return type.name();
        }
    }
    
    /**
     * Формирует сообщение с текущим выбором напоминаний.
     * 
     * @param eventId идентификатор события
     * @return отформатированное сообщение
     */
    private String buildReminderSelectionMessage(Long eventId) {
        StringBuilder message = new StringBuilder();
        message.append("🔔 ").append(bold("Настройка напоминаний")).append("\n\n");
        
        long selectedCount = selectedReminders.stream()
            .filter(key -> key.startsWith(eventId + "_"))
            .count();
        
        if (selectedCount == 0) {
            message.append("Выберите один или несколько типов напоминаний.\n\n");
        } else {
            message.append(String.format("Выбрано типов: %d\n\n", selectedCount));
        }
        
        message.append(italic("Нажмите на тип, чтобы выбрать или отменить выбор")).append("\n");
        message.append(italic("После выбора нажмите \"✅ Создать напоминания\""));
        
        return message.toString();
    }
    
    /**
     * Получает строковое представление стека вызовов исключения.
     * 
     * <p>Используется для детального логирования критических ошибок.</p>
     * 
     * @param e исключение
     * @return строка со стеком вызовов (первые 5 элементов)
     */
    private String getStackTraceString(Exception e) {
        if (e == null || e.getStackTrace() == null || e.getStackTrace().length == 0) {
            return "no stack trace";
        }
        
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] elements = e.getStackTrace();
        int limit = Math.min(5, elements.length);
        
        for (int i = 0; i < limit; i++) {
            sb.append(elements[i].toString());
            if (i < limit - 1) {
                sb.append(" -> ");
            }
        }
        
        return sb.toString();
    }
}
