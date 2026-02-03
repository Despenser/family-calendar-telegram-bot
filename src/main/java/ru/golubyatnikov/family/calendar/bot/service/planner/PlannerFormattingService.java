package ru.golubyatnikov.family.calendar.bot.service.planner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.reminder.ReminderService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Сервис для форматирования сообщений планировщика событий.
 * 
 * <p>Отвечает за создание текстовых представлений событий
 * с использованием MarkdownV2 форматирования.</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerFormattingService {

    private final BotMessageBuilder botMessageBuilder;
    private final ReminderService reminderService;

    /**
     * Формирует заголовок списка событий.
     * 
     * @param eventCount количество событий
     * @return отформатированный заголовок
     */
    public String buildMyEventsHeader(int eventCount) {
        return botMessageBuilder.buildMyEventsHeader(eventCount);
    }

    /**
     * Формирует сообщение о событии.
     * 
     * @param event событие
     * @return отформатированное сообщение
     */
    public String buildEventMessage(Event event) {
        return botMessageBuilder.buildEventMessage(event);
    }

    /**
     * Формирует объединенное сообщение (заголовок + событие).
     * 
     * @param header заголовок
     * @param eventText текст события
     * @return объединенное сообщение
     */
    public String buildCombinedMessage(String header, String eventText) {
        return header + "\n" + eventText;
    }

    /**
     * Формирует сообщение об отсутствии событий.
     * 
     * @return отформатированное сообщение
     */
    public String buildNoEventsMessage() {
        StringBuilder message = new StringBuilder();
        message.append("📝 ").append(bold("Мои события")).append("\n\n");
        message.append(escape("У вас пока нет созданных событий.\n\n"));
        message.append(escape("Используйте ")).append(escape("➕ /add_event")).append(escape(" для добавления нового события."));
        return message.toString();
    }

    /**
     * Формирует простое текстовое представление события без форматирования.
     * 
     * @param event событие
     * @return простой текст
     */
    public String buildPlainEventText(Event event) {
        StringBuilder plainText = new StringBuilder();
        
        plainText.append("📌 ").append(event.getTitle()).append("\n");
        plainText.append("📅 Дата: ").append(event.getFormattedDate()).append("\n");
        plainText.append("🕐 Время: ").append(event.getFormattedTime());
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            plainText.append("\n📝 Описание: ").append(event.getDescription());
        }
        
        return plainText.toString();
    }

    /**
     * Формирует детальное описание события.
     * 
     * @param event событие
     * @return детальное описание
     */
    public String buildEventDetails(Event event) {
        StringBuilder details = new StringBuilder();
        details.append("📋 ").append(bold("Детали события")).append("\n\n");
        
        // Название
        details.append("📌 ").append(bold(event.getTitle()));
        
        // Добавляем эмодзи 🔔 если есть напоминания
        boolean hasReminders = reminderService.hasActiveReminders(event.getId());
        if (hasReminders) {
            details.append(escape(" 🔔"));
        }
        details.append("\n\n");
        
        // Дата
        details.append(formatMessage("📅 Дата: %s\n", event.getFormattedDate()));
        
        // Время
        if (event.getEventTime() != null) {
            if (event.getEndTime() != null) {
                details.append(formatMessage("🕐 Время: %s - %s\n", 
                    event.getFormattedTime(), 
                    event.getEndTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))));
            } else {
                details.append(formatMessage("🕐 Время: %s\n", event.getFormattedTime()));
            }
        }
        
        // Описание
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            details.append(formatMessage("📝 Описание: %s\n", event.getDescription()));
        }
        
        // Тип события
        details.append("\n");
        if (event.getIsPersonal()) {
            details.append("👤 ").append(bold("Персональное событие")).append("\n");
        } else {
            details.append(formatMessage("👨‍👩‍👧‍👦 Семейное событие (создал: %s)\n", 
                event.getUser().getFirstName()));
        }
        
        // Статус
        if (event.getStatus() == Event.EventStatus.COMPLETED) {
            details.append(escape("✅ Статус: Завершено\n"));
        } else if (event.getStatus() == Event.EventStatus.DELETED) {
            details.append(escape("🗑️ Статус: Удалено\n"));
        }
        
        // Секция "Напоминания"
        details.append("\n");
        details.append("🔔 ").append(bold("Напоминания")).append("\n");
        
        if (hasReminders) {
            List<ru.golubyatnikov.family.calendar.bot.model.Reminder> reminders = 
                reminderService.getEventReminders(event.getId());
            
            List<ru.golubyatnikov.family.calendar.bot.model.Reminder> activeReminders = 
                reminders.stream()
                    .filter(r -> !r.getSent())
                    .sorted((r1, r2) -> r1.getReminderTime().compareTo(r2.getReminderTime()))
                    .toList();
            
            if (!activeReminders.isEmpty()) {
                for (ru.golubyatnikov.family.calendar.bot.model.Reminder reminder : activeReminders) {
                    String reminderText = getReminderDisplayText(reminder);
                    if (isAutomaticReminderType(reminder.getReminderType())) {
                        details.append(formatMessage("  • %s (автоматически)\n", reminderText));
                    } else {
                        details.append(formatMessage("  • %s (настроено вручную)\n", reminderText));
                    }
                }
            } else {
                details.append(escape("  Все напоминания уже отправлены\n"));
            }
        } else {
            details.append(escape("  Напоминания не настроены\n"));
        }
        
        return details.toString();
    }

    /**
     * Формирует сообщение с выбором поля для редактирования.
     * 
     * @param event событие
     * @return отформатированное сообщение
     */
    public String buildEditFieldSelectionMessage(Event event) {
        StringBuilder message = new StringBuilder();
        
        // Заголовок
        message.append("✏️ ").append(bold("Редактирование события")).append("\n\n");
        
        // Название события
        message.append("📌 ").append(bold(event.getTitle())).append("\n\n");
        
        // Текущие данные
        message.append(formatMessage("Текущие данные:\n"));
        message.append(formatMessage("📅 Дата: %s\n", event.getFormattedDate()));
        message.append(formatMessage("🕐 Время: %s\n", event.getFormattedTime()));
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            message.append(formatMessage("📝 Описание: %s\n", event.getDescription()));
        } else {
            message.append(formatMessage("📝 Описание: не указано\n"));
        }
        
        message.append(formatMessage("\nВыберите поле для редактирования:"));
        
        return message.toString();
    }

    /**
     * Формирует сообщение об успешном обновлении поля события.
     * 
     * @param event событие с обновленными данными
     * @param field обновленное поле
     * @return отформатированное сообщение
     */
    public String buildFieldUpdateSuccessMessage(Event event, ConversationStateService.EditField field) {
        StringBuilder message = new StringBuilder();
        
        // Заголовок
        message.append("✅ ").append(bold("Поле обновлено")).append("\n\n");
        
        // Название события
        message.append("📌 ").append(bold(event.getTitle())).append("\n\n");
        
        // Информация об обновленном поле
        switch (field) {
            case TITLE:
                message.append("Название изменено на: ").append(bold(event.getTitle())).append("\n");
                break;
            case DATE:
                message.append(formatMessage("Дата изменена на: %s\n", event.getFormattedDate()));
                break;
            case TIME:
                message.append(formatMessage("Время изменено на: %s\n", event.getFormattedTime()));
                break;
            case DESCRIPTION:
                if (event.getDescription() != null && !event.getDescription().isBlank()) {
                    message.append(formatMessage("Описание изменено на: %s\n", event.getDescription()));
                } else {
                    message.append(formatMessage("Описание удалено\n"));
                }
                break;
        }
        
        // Текущие данные
        message.append(formatMessage("\nТекущие данные события:\n"));
        message.append(formatMessage("📅 Дата: %s\n", event.getFormattedDate()));
        message.append(formatMessage("🕐 Время: %s\n", event.getFormattedTime()));
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            message.append(formatMessage("📝 Описание: %s", event.getDescription()));
        } else {
            message.append(formatMessage("📝 Описание: не указано"));
        }
        
        return message.toString();
    }

    /**
     * Формирует сообщение об ошибке доступа.
     * 
     * @param message текст ошибки
     * @return отформатированное сообщение
     */
    public String buildAccessDeniedMessage(String message) {
        return formatMessage("❌ %s\n\n%s", bold("Доступ запрещен"), message);
    }

    /**
     * Формирует сообщение об ошибке.
     * 
     * @param message текст ошибки
     * @return отформатированное сообщение
     */
    public String buildErrorMessage(String message) {
        return formatMessage("❌ %s\n\n%s", bold("Ошибка"), message);
    }

    /**
     * Возвращает текстовое описание напоминания.
     * 
     * @param reminder напоминание
     * @return текстовое описание
     */
    private String getReminderDisplayText(ru.golubyatnikov.family.calendar.bot.model.Reminder reminder) {
        return switch (reminder.getReminderType()) {
            case MORNING_OF_DAY -> "Утром в день события";
            case EVENING_BEFORE -> "Вечером накануне";
            case ONE_HOUR_BEFORE -> "За 1 час до события";
            case TEN_MINUTES_BEFORE -> "За 10 минут до события";
            case FIFTEEN_MINUTES_BEFORE -> "За 15 минут до события";
            case CUSTOM -> "За " + reminder.getCustomMinutes() + " минут до события";
        };
    }

    /**
     * Проверяет, является ли тип напоминания автоматическим.
     * 
     * @param type тип напоминания
     * @return true если тип автоматический
     */
    private boolean isAutomaticReminderType(ru.golubyatnikov.family.calendar.bot.model.Reminder.ReminderType type) {
        return type == ru.golubyatnikov.family.calendar.bot.model.Reminder.ReminderType.EVENING_BEFORE ||
               type == ru.golubyatnikov.family.calendar.bot.model.Reminder.ReminderType.ONE_HOUR_BEFORE ||
               type == ru.golubyatnikov.family.calendar.bot.model.Reminder.ReminderType.FIFTEEN_MINUTES_BEFORE;
    }
}
