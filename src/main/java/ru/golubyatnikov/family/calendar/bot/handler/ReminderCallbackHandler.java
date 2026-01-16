package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.golubyatnikov.family.calendar.bot.model.Reminder;
import ru.golubyatnikov.family.calendar.bot.service.ReminderService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.formatMessage;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.italic;

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
            message.append("• Утром в день события (9:00)\n");
            message.append("• Вечером накануне (20:00)\n");
            message.append("• За 1 час до события\n");
            message.append("• За 10 минут до события\n");
            message.append("• Свое время\n\n");
            message.append(italic("Нажмите на тип, чтобы выбрать или отменить выбор"));
            
            InlineKeyboardMarkup keyboard = createReminderTypesKeyboard(eventId);
            
            messageService.editMessageText(chatId, messageId, message.toString(), keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "Выберите типы напоминаний");
            
        } catch (Exception e) {
            log.error("Ошибка при настройке напоминаний: eventId={}, chatId={}, error={}, stackTrace={}", 
                    eventId, chatId, e.getMessage(), getStackTraceString(e), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка при настройке напоминаний");
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
    public void handleReminderTypeSelection(Long eventId, Reminder.ReminderType type, 
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
            
            // Если выбран CUSTOM, запрашиваем ввод минут
            if (type == Reminder.ReminderType.CUSTOM && selectedReminders.contains(key)) {
                StringBuilder message = new StringBuilder();
                message.append("⏱️ ").append(bold("Свое время напоминания")).append("\n\n");
                message.append("Введите количество минут до события, за которое нужно отправить напоминание.\n\n");
                message.append("Например: 30 (за 30 минут), 120 (за 2 часа), 1440 (за 1 день)");
                
                messageService.editMessageText(chatId, messageId, message.toString(), null);
                messageService.answerCallbackQuery(callbackQueryId, "Введите количество минут");
                return;
            }
            
            // Обновляем клавиатуру с отметками выбранных типов
            InlineKeyboardMarkup keyboard = createReminderTypesKeyboard(eventId);
            String message = buildReminderSelectionMessage(eventId);
            
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, 
                selectedReminders.contains(key) ? "✅ Выбрано" : "Отменено");
            
        } catch (Exception e) {
            log.error("Ошибка при выборе типа напоминания: eventId={}, type={}, chatId={}, error={}, stackTrace={}", 
                    eventId, type, chatId, e.getMessage(), getStackTraceString(e), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: callbackQueryId={}, error={}, stackTrace={}", 
                        callbackQueryId, ex.getMessage(), getStackTraceString(ex), ex);
            }
        }
    }
    
    /**
     * Обрабатывает ввод пользовательского количества минут для custom напоминания.
     * 
     * @param eventId идентификатор события
     * @param input введенный текст
     * @param chatId идентификатор чата
     */
    public void handleCustomReminderInput(Long eventId, String input, Long chatId) {
        log.debug("Обработка custom напоминания для события ID={}: input={}", eventId, input);
        
        try {
            int minutes = Integer.parseInt(input.trim());
            
            if (minutes < 1) {
                messageService.sendMessage(chatId, 
                    "❌ Количество минут должно быть больше 0. Попробуйте еще раз.");
                return;
            }
            
            if (minutes > 43200) { // 30 дней
                messageService.sendMessage(chatId, 
                    "❌ Максимальное количество минут: 43200 (30 дней). Попробуйте еще раз.");
                return;
            }
            
            // Создаем custom напоминание
            Reminder reminder = reminderService.createCustomReminder(eventId, minutes);
            
            StringBuilder message = new StringBuilder();
            message.append("✅ ").append(bold("Напоминание создано")).append("\n\n");
            message.append(formatMessage("Тип: Свое время\n"));
            message.append(formatMessage("За %d минут до события\n", minutes));
            message.append(formatMessage("Время отправки: %s", 
                reminder.getReminderTime().format(TIME_FORMATTER)));
            
            messageService.sendMessage(chatId, message.toString());
            log.debug("Custom напоминание создано для события ID={}: {} минут", eventId, minutes);
            
        } catch (NumberFormatException e) {
            log.warn("Некорректный ввод для custom напоминания: input='{}', chatId={}", input, chatId);
            try {
                messageService.sendMessage(chatId, 
                    "❌ Некорректный формат. Введите число (количество минут).");
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения: chatId={}, error={}, stackTrace={}", 
                        chatId, ex.getMessage(), getStackTraceString(ex), ex);
            }
        } catch (Exception e) {
            log.error("Ошибка при создании custom напоминания: eventId={}, chatId={}, error={}, stackTrace={}", 
                     eventId, chatId, e.getMessage(), getStackTraceString(e), e);
            try {
                messageService.sendMessage(chatId, 
                    "❌ Ошибка при создании напоминания: " + e.getMessage());
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения: chatId={}, error={}, stackTrace={}", 
                        chatId, ex.getMessage(), getStackTraceString(ex), ex);
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
            
            messageService.answerCallbackQuery(callbackQueryId, "✅ Напоминание удалено");
            
            StringBuilder message = new StringBuilder();
            message.append("✅ ").append(bold("Напоминание удалено")).append("\n\n");
            message.append("Напоминание успешно удалено.");
            
            messageService.editMessageText(chatId, messageId, message.toString(), null);
            
            log.debug("Напоминание ID={} успешно удалено", reminderId);
            
        } catch (Exception e) {
            log.error("Ошибка при удалении напоминания: reminderId={}, chatId={}, error={}, stackTrace={}", 
                    reminderId, chatId, e.getMessage(), getStackTraceString(e), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка при удалении");
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
            List<Reminder.ReminderType> selectedTypes = new ArrayList<>();
            for (String key : selectedReminders) {
                if (key.startsWith(eventId + "_")) {
                    String typeName = key.substring((eventId + "_").length());
                    try {
                        Reminder.ReminderType type = Reminder.ReminderType.valueOf(typeName);
                        if (type != Reminder.ReminderType.CUSTOM) { // CUSTOM обрабатывается отдельно
                            selectedTypes.add(type);
                        }
                    } catch (IllegalArgumentException e) {
                        log.warn("Неизвестный тип напоминания: {}", typeName);
                    }
                }
            }
            
            if (selectedTypes.isEmpty()) {
                messageService.answerCallbackQuery(callbackQueryId, "Выберите хотя бы один тип");
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
            messageService.answerCallbackQuery(callbackQueryId, "✅ Напоминания созданы");
            
            log.debug("Создано {} напоминаний для события ID={}", createdReminders.size(), eventId);
            
        } catch (Exception e) {
            log.error("Ошибка при создании напоминаний: eventId={}, chatId={}, error={}, stackTrace={}", 
                    eventId, chatId, e.getMessage(), getStackTraceString(e), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: " + e.getMessage());
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
        addReminderTypeButton(keyboard, eventId, Reminder.ReminderType.MORNING_OF_DAY, "☀️ Утром в день события");
        addReminderTypeButton(keyboard, eventId, Reminder.ReminderType.EVENING_BEFORE, "🌙 Вечером накануне");
        addReminderTypeButton(keyboard, eventId, Reminder.ReminderType.ONE_HOUR_BEFORE, "⏰ За 1 час");
        addReminderTypeButton(keyboard, eventId, Reminder.ReminderType.TEN_MINUTES_BEFORE, "⏱️ За 10 минут");
        addReminderTypeButton(keyboard, eventId, Reminder.ReminderType.CUSTOM, "🕐 Свое время");
        
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
                                      Reminder.ReminderType type, String text) {
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
        
        if (reminder.getReminderType() == Reminder.ReminderType.CUSTOM) {
            return String.format("• %s (за %d мин)\n  Время: %s", 
                typeText, reminder.getCustomMinutes(), timeText);
        }
        
        return String.format("• %s\n  Время: %s", typeText, timeText);
    }
    
    /**
     * Возвращает текстовое описание типа напоминания.
     * 
     * @param type тип напоминания
     * @return текстовое описание
     */
    private String getReminderTypeText(Reminder.ReminderType type) {
        switch (type) {
            case MORNING_OF_DAY:
                return "Утром в день события";
            case EVENING_BEFORE:
                return "Вечером накануне";
            case ONE_HOUR_BEFORE:
                return "За 1 час";
            case TEN_MINUTES_BEFORE:
                return "За 10 минут";
            case CUSTOM:
                return "Свое время";
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
