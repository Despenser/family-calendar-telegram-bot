package ru.golubyatnikov.family.calendar.bot.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.model.Event;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Централизованный компонент для формирования сообщений бота.
 * 
 * <p>Обеспечивает консистентный стиль сообщений и корректное экранирование
 * специальных символов MarkdownV2. Все методы автоматически применяют
 * экранирование через {@link MarkdownFormatter}.</p>
 * 
 * <p><b>Требования:</b> 4.1, 4.2, 4.4</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>{@code
 * @Component
 * public class DateTimeCallbackHandler {
 *     private final BotMessageBuilder messageBuilder;
 *     
 *     public void handleDateSelection(LocalDate date) {
 *         String message = messageBuilder.buildDateSelectedMessage(date);
 *         // Отправка сообщения...
 *     }
 * }
 * }</pre>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 * @see MarkdownFormatter
 */
@Component
@RequiredArgsConstructor
public class BotMessageBuilder {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    
    // ===== Сообщения о создании события =====
    
    /**
     * Формирует сообщение об успешном создании события.
     * 
     * @param event созданное событие
     * @return отформатированное сообщение
     */
    public String buildEventCreatedMessage(Event event) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ ").append(bold("Событие успешно создано!")).append("\n\n");
        sb.append("📅 Дата: ").append(escape(formatDate(event.getEventDate()))).append("\n");
        sb.append("🕐 Время: ").append(escape(formatTime(event.getEventTime()))).append("\n");
        sb.append("📝 Название: ").append(escape(event.getTitle()));
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            sb.append("\n📄 Описание: ").append(escape(event.getDescription()));
        }
        
        return sb.toString();
    }
    
    /**
     * Формирует сообщение об отмене создания события.
     * 
     * @return отформатированное сообщение
     */
    public String buildEventCancelledMessage() {
        return "❌ Создание события отменено";
    }
    
    // ===== Сообщения о выборе даты и времени =====
    
    /**
     * Формирует сообщение о выборе даты.
     * 
     * @param date выбранная дата
     * @return отформатированное сообщение
     */
    public String buildDateSelectedMessage(LocalDate date) {
        return formatMessage("✅ Дата выбрана: %s\n\nТеперь выберите час:", formatDate(date));
    }
    
    /**
     * Формирует сообщение о выборе даты (строковый формат).
     * 
     * @param formattedDate отформатированная дата
     * @return отформатированное сообщение
     */
    public String buildDateSelectedMessage(String formattedDate) {
        return formatMessage("✅ Дата выбрана: %s\n\nТеперь выберите час:", formattedDate);
    }
    
    /**
     * Формирует сообщение о выборе часа.
     * 
     * @param hour выбранный час
     * @return отформатированное сообщение
     */
    public String buildHourSelectedMessage(int hour) {
        return formatMessage("✅ Час выбран: %02d:00\n\nТеперь выберите минуты:", hour);
    }
    
    /**
     * Формирует сообщение о выборе времени.
     * 
     * @param time выбранное время
     * @return отформатированное сообщение
     */
    public String buildTimeSelectedMessage(LocalTime time) {
        return formatMessage("✅ Время выбрано: %s\n\nТеперь отправьте название события:", 
                formatTime(time));
    }
    
    /**
     * Формирует сообщение о выборе времени (строковый формат).
     * 
     * @param formattedTime отформатированное время
     * @return отформатированное сообщение
     */
    public String buildTimeSelectedMessage(String formattedTime) {
        return formatMessage("✅ Время выбрано: %s\n\nТеперь отправьте название события:", 
                formattedTime);
    }
    
    /**
     * Формирует сообщение для выбора часа.
     * 
     * @return отформатированное сообщение
     */
    public String buildSelectHourMessage() {
        return "🕐 Выберите час:";
    }
    
    /**
     * Формирует сообщение для выбора даты.
     * 
     * @return отформатированное сообщение
     */
    public String buildSelectDateMessage() {
        return "📅 Выберите дату события:";
    }
    
    // ===== Сообщения о типе события =====
    
    /**
     * Формирует сообщение о выборе типа события.
     * 
     * @param isPersonal true если персональное событие
     * @return отформатированное сообщение
     */
    public String buildEventTypeSelectedMessage(boolean isPersonal) {
        if (isPersonal) {
            return "✅ " + bold("Выбрано: Персональное событие") + "\n\n" +
                   italic("Только вы будете видеть это событие.") + "\n\n" +
                   "📅 " + escape("Теперь выберите дату события:");
        } else {
            return "✅ " + bold("Выбрано: Семейное событие") + "\n\n" +
                   italic("Все члены семьи будут видеть это событие.") + "\n\n" +
                   "📅 " + escape("Теперь выберите дату события:");
        }
    }
    
    // ===== Сообщения об ошибках =====
    
    /**
     * Формирует сообщение об ошибке.
     * 
     * @param errorText текст ошибки
     * @return отформатированное сообщение
     */
    public String buildErrorMessage(String errorText) {
        return "❌ " + bold("Произошла ошибка") + "\\. " + italic(escape(errorText));
    }
    
    /**
     * Формирует сообщение об ошибке с предложением действия.
     * 
     * @param errorText текст ошибки
     * @param actionHint подсказка о действии
     * @return отформатированное сообщение
     */
    public String buildErrorMessageWithAction(String errorText, String actionHint) {
        return buildErrorMessage(errorText) + "\n\n" + italic(escape(actionHint));
    }
    
    /**
     * Формирует простое сообщение об ошибке для callback query.
     * 
     * @return отформатированное сообщение
     */
    public String buildCallbackErrorMessage() {
        return "❌ Произошла ошибка. Попробуйте еще раз.";
    }
    
    // ===== Сообщения о файлах =====
    
    /**
     * Формирует сообщение о прикреплении файла.
     * 
     * @param fileName имя файла
     * @param fileSizeMb размер файла в МБ
     * @return отформатированное сообщение
     */
    public String buildFileAttachedMessage(String fileName, double fileSizeMb) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ ").append(bold("Файл успешно прикреплен!")).append("\n\n");
        sb.append("📎 Название: ").append(escape(fileName)).append("\n");
        sb.append("📊 Размер: ").append(escape(String.format("%.2f МБ", fileSizeMb))).append("\n\n");
        sb.append(escape("Вы можете продолжить прикреплять файлы или завершить создание события."));
        return sb.toString();
    }
    
    // ===== Сообщения о событиях из текста =====
    
    /**
     * Формирует сообщение предпросмотра события из текста.
     * 
     * @param title название события
     * @param date дата события
     * @param time время события
     * @return отформатированное сообщение
     */
    public String buildTextEventPreviewMessage(String title, String date, String time) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ ").append(bold("Распознано событие из текста:")).append("\n\n");
        sb.append("📝 Название: ").append(escape(title)).append("\n");
        sb.append("📅 Дата: ").append(escape(date)).append("\n");
        sb.append("🕐 Время: ").append(escape(time)).append("\n\n");
        sb.append(escape("Подтвердите создание события:"));
        return sb.toString();
    }
    
    /**
     * Формирует сообщение об отмене создания события из текста.
     * 
     * @return отформатированное сообщение
     */
    public String buildTextEventCancelledMessage() {
        return "❌ Создание события из текста отменено";
    }
    
    // ===== Сообщения о пропуске описания =====
    
    /**
     * Формирует сообщение о пропуске описания.
     * 
     * @return отформатированное сообщение
     */
    public String buildDescriptionSkippedMessage() {
        return "✅ Описание пропущено\\. Событие создано\\!";
    }
    
    // ===== Вспомогательные методы =====
    
    /**
     * Форматирует дату в строку.
     * 
     * @param date дата
     * @return отформатированная строка
     */
    private String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DATE_FORMATTER);
    }
    
    /**
     * Форматирует время в строку.
     * 
     * @param time время
     * @return отформатированная строка
     */
    private String formatTime(LocalTime time) {
        if (time == null) {
            return "";
        }
        return time.format(TIME_FORMATTER);
    }
}
