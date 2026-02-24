package ru.golubyatnikov.family.calendar.bot.service.presentation.formatting;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import java.time.LocalDate;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Event.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.EventType.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Misc.SEPARATOR;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Reminders.ENABLED;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Time.*;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Сервис для единообразного форматирования событий в командах списка событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
public class EventFormattingService {
    
    private final DateTimeFormattingService dateTimeFormattingService;
    
    /**
     * Форматирует событие в едином компактном формате для всех команд.
     * 
     * @param event событие для форматирования, не может быть null
     * @param currentUser текущий пользователь (для определения, показывать ли создателя), не может быть null
     * @param hasReminders флаг наличия активных напоминаний для события
     *
     * @return отформатированная строка с информацией о событии, завершающаяся пустой строкой
     * @throws IllegalArgumentException если event или currentUser равны null
     */
    public String formatEvent(Event event, User currentUser, boolean hasReminders) {
        if (event == null) {
            throw new IllegalArgumentException("Событие не может быть null");
        }
        if (currentUser == null) {
            throw new IllegalArgumentException("Текущий пользователь не может быть null");
        }
        
        StringBuilder sb = new StringBuilder();
        
        // Иконка типа события и название на первой строке
        sb.append(escape(getEventTypeIcon(event)));
        sb.append(bold(event.getTitle()));
        
        // Время события (на новой строке без отступа)
        String timeStr = formatEventTime(event);
        if (timeStr != null && !timeStr.isEmpty()) {
            sb.append(escape("\n")).append(timeStr);
        }

        // Активированы напоминания (на новой строке без отступа)
        if (hasReminders) {
            sb.append(escape("\n")).append(escape(ENABLED + " Напоминания включены"));
        }
        
        // Описание события (на новой строке без отступа)
        String descriptionStr = formatEventDescription(event);
        if (descriptionStr != null && !descriptionStr.isEmpty()) {
            sb.append(escape("\n")).append(descriptionStr);
        }
        
        // Создатель события (на новой строке без отступа, если не текущий пользователь)
        String creatorInfo = formatCreatorInfo(event, currentUser);
        if (!creatorInfo.isEmpty()) {
            sb.append(escape("\n")).append(creatorInfo);
        }
        
        // Добавляем пустую строку после каждого события для разделения
        sb.append(escape("\n\n"));
        
        return sb.toString();
    }
    
    /**
     * Форматирует заголовок команды списка событий с эмодзи.
     * 
     * @param emoji эмодзи для заголовка, не может быть null или пустым
     * @param commandName название команды, не может быть null или пустым
     * @param additionalInfo дополнительная информация в скобках, не может быть null или пустой
     *
     * @return отформатированный заголовок команды
     * @throws IllegalArgumentException если любой из параметров null или пустой
     */
    public String formatCommandHeader(@NonNull String emoji,
                                      @NonNull String commandName,
                                      @NonNull String additionalInfo) {

        if (emoji.isBlank()) {
            throw new IllegalArgumentException("Эмодзи не может быть пустым");
        }
        if (commandName.isBlank()) {
            throw new IllegalArgumentException("Название команды не может быть пустым");
        }
        if (additionalInfo.isBlank()) {
            throw new IllegalArgumentException("Дополнительная информация не может быть пустой");
        }
        
        return escape(emoji + " ") + bold(commandName) + escape(" (") + escape(additionalInfo) + escape(")");
    }
    
    /**
     * Форматирует сообщение об отсутствии событий с эмодзи.
     * 
     * @param emoji эмодзи для заголовка, не может быть null или пустым
     * @param commandName название команды, не может быть null или пустым
     * @param message сообщение об отсутствии событий, не может быть null или пустым
     *
     * @return отформатированное сообщение об отсутствии событий
     * @throws IllegalArgumentException если любой из параметров null или пустой
     */
    public String formatNoEventsMessage(@NonNull String emoji,
                                        @NonNull String commandName,
                                        @NonNull String message) {

        if (emoji.isBlank()) {
            throw new IllegalArgumentException("Эмодзи не может быть пустым");
        }
        if (commandName.isBlank()) {
            throw new IllegalArgumentException("Название команды не может быть пустым");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("Сообщение не может быть пустым");
        }
        
        return escape(emoji + " ") + bold(commandName) + escape("\n\n") + escape(message);
    }
    
    /**
     * Форматирует счетчик событий.
     * 
     * @param count количество событий
     * @return отформатированный счетчик событий курсивом
     */
    public String formatEventCounter(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Количество событий должно быть больше 0");
        }
        
        return italic("Всего событий: " + count);
    }
    
    /**
     * Форматирует заголовок дня для команды /week.
     * 
     * @param date дата для форматирования, не может быть null
     * @param today текущая дата, не может быть null
     *
     * @return отформатированный заголовок дня
     * @throws IllegalArgumentException если date или today равны null
     */
    public String formatDayHeader(@NonNull LocalDate date, @NonNull LocalDate today) {
        StringBuilder sb = new StringBuilder();
        
        if (date.equals(today)) {
            sb.append(escape(PIN + " "));
            sb.append(escape(formatDateWithDayOfWeek(date, "сегодня")));

        } else if (date.equals(today.plusDays(1))) {
            sb.append(escape(UPCOMING + " "));
            sb.append(escape(formatDateWithDayOfWeek(date, "завтра")));

        } else {
            sb.append(escape(MONTH + " "));
            sb.append(escape(dateTimeFormattingService.formatShortDate(date)));
        }
        
        sb.append(escape("\n\n"));
        return sb.toString();
    }
    
    /**
     * Форматирует дату с днем недели в формате "dd.MM (label - день_недели)".
     * 
     * @param date дата для форматирования
     * @param label метка (например, "сегодня" или "завтра")
     *
     * @return отформатированная строка с датой и днем недели
     */
    private @NonNull String formatDateWithDayOfWeek(LocalDate date, String label) {
        String formattedDate = dateTimeFormattingService.formatShortDateWithoutYear(date);
        String dayOfWeek = dateTimeFormattingService.formatDayOfWeek(date);
        
        return String.format("%s (%s - %s)", formattedDate, label, dayOfWeek);
    }
    
    /**
     * Форматирует разделитель между днями для команды /week.
     * 
     * @return отформатированный разделитель дней
     */
    public String formatDaySeparator() {
        return escape(SEPARATOR);
    }
    
    /**
     * Форматирует событие для результатов поиска с полной информацией.
     * 
     * @param event событие для форматирования, не может быть null
     * @param currentUser текущий пользователь, не может быть null
     *
     * @return отформатированная строка с информацией о событии для результатов поиска
     * @throws IllegalArgumentException если event или currentUser равны null
     */
    public String formatSearchResult(@NonNull Event event, @NonNull User currentUser) {
        
        StringBuilder sb = new StringBuilder();
        
        // Эмодзи и название события
        sb.append(escape(TITLE + " "));
        sb.append(bold(event.getTitle()));
        sb.append(escape("\n"));
        
        // Дата события
        sb.append(escape(DATE + " Дата: "));
        sb.append(escape(dateTimeFormattingService.formatDate(event.getEventDate())));
        sb.append(escape("\n"));
        
        // Время события (если есть)
        if (event.getEventTime() != null) {
            sb.append(escape(TIME + " Время: "));
            if (event.getEndTime() != null) {
                sb.append(escape(dateTimeFormattingService.formatTime(event.getEventTime()) + " - " + 
                                dateTimeFormattingService.formatTime(event.getEndTime())));
            } else {
                sb.append(escape(dateTimeFormattingService.formatTime(event.getEventTime())));
            }
            sb.append(escape("\n"));
        }
        
        // Тип события
        if (event.getIsPersonal()) {
            sb.append(escape(PERSONAL + " Тип: Личное"));
        } else {
            sb.append(escape(FAMILY + " Тип: Семейное"));
        }
        sb.append(escape("\n"));
        
        // Описание события (если есть)
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            sb.append(escape(DESCRIPTION + " Описание: "));
            sb.append(escape(event.getDescription()));
            sb.append(escape("\n"));
        }
        
        // Создатель события (если не текущий пользователь)
        if (!event.belongsToUser(currentUser.getId())) {
            sb.append(escape(CREATOR + " Создал: " + event.getUser().getFirstName()));
            sb.append(escape("\n"));
        }
        
        return sb.toString();
    }
    
    /**
     * Возвращает иконку типа события.
     * 
     * @param event событие
     * @return иконка типа события
     */
    private @NonNull String getEventTypeIcon(@NonNull Event event) {
        return event.getIsPersonal() ? PERSONAL + " " : FAMILY + " ";
    }
    
    /**
     * Форматирует время события без отступа.
     * 
     * @param event событие
     * @return отформатированное время без отступа или null
     */
    private @Nullable String formatEventTime(@NonNull Event event) {
        if (event.getEventTime() == null) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(escape(TIME + " Время: "));
        
        if (event.getEndTime() != null) {
            sb.append(escape(dateTimeFormattingService.formatTime(event.getEventTime()) + " - " + 
                            dateTimeFormattingService.formatTime(event.getEndTime())));
        } else {
            sb.append(escape(dateTimeFormattingService.formatTime(event.getEventTime())));
        }
        
        return sb.toString();
    }
    
    /**
     * Форматирует описание события без отступа.
     * 
     * @param event событие
     * @return отформатированное описание без отступа или null
     */
    private @Nullable String formatEventDescription(@NonNull Event event) {
        if (event.getDescription() == null || event.getDescription().isBlank()) {
            return null;
        }
        
        return escape(DESCRIPTION + " Описание: ") + escape(event.getDescription());
    }
    
    /**
     * Форматирует информацию о создателе события без отступа.
     * 
     * @param event событие
     * @param currentUser текущий пользователь
     *
     * @return информация о создателе или пустая строка
     */
    private @NonNull String formatCreatorInfo(@NonNull Event event,
                                              @NonNull User currentUser) {

        if (event.belongsToUser(currentUser.getId())) {
            return "";
        }
        return escape(CREATOR + " Создал: " + event.getUser().getFirstName());
    }
}
