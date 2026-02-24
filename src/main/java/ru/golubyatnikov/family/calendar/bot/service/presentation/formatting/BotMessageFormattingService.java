package ru.golubyatnikov.family.calendar.bot.service.presentation.formatting;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.CallbackMessages.NO_EVENTS_ON_DATE;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Actions.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Event.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.EventType.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Time.*;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Сервис для формирования сообщений бота.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-04
 */
@Component
@RequiredArgsConstructor
public class BotMessageFormattingService {
    
    private static final String EVENT_CREATION_HEADER = CREATION + " Создание нового события";
    private static final String FAMILY_EVENT_TYPE = FAMILY + " Тип: Семейное";
    private static final String PERSONAL_EVENT_TYPE = PERSONAL + " Тип: Персональное";

    private final DateTimeFormattingService dateTimeFormattingService;
    
    /**
     * Формирует сообщение об успешном создании события.
     * 
     * @param event созданное событие
     * @return отформатированное сообщение
     */
    public String buildEventCreatedMessage(@NonNull Event event) {
        StringBuilder sb = new StringBuilder();
        sb.append(SUCCESS).append(" ").append(bold("Событие успешно создано!")).append("\n\n");
        sb.append(DATE).append(" Дата: ").append(escape(formatDate(event.getEventDate()))).append("\n");
        sb.append(TIME).append(" Время: ").append(escape(formatTime(event.getEventTime()))).append("\n");
        sb.append(DESCRIPTION).append(" Название: ").append(escape(event.getTitle()));
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            sb.append("\n").append(NOTE).append(" Описание: ").append(escape(event.getDescription()));
        }
        
        return sb.toString();
    }
    
    /**
     * Формирует сообщение об отмене создания события.
     * 
     * @return отформатированное сообщение
     */
    public String buildEventCancelledMessage() {
        return CANCELLED + " Создание события отменено";
    }
    
    /**
     * Формирует сообщение о выборе даты (строковый формат).
     * 
     * @param formattedDate отформатированная дата
     * @return отформатированное сообщение
     */
    public String buildDateSelectedMessage(String formattedDate) {
        return bold(EVENT_CREATION_HEADER) + "\n\n" +
               formatMessage(SUCCESS + " Дата выбрана: %s\n\nТеперь выберите час:", formattedDate);
    }
    
    /**
     * Формирует сообщение о выборе часа.
     * 
     * @param hour выбранный час
     * @return отформатированное сообщение
     */
    public String buildHourSelectedMessage(int hour) {
        return bold(EVENT_CREATION_HEADER) + "\n\n" +
               formatMessage(SUCCESS + " Час выбран: %02d:00\n\nТеперь выберите минуты:", hour);
    }
    
    /**
     * Формирует сообщение о выборе часа при редактировании времени события.
     * 
     * @param hour выбранный час
     * @return отформатированное сообщение с шапкой
     */
    public String buildEditTimeHourSelectedMessage(int hour) {
        return TIME + " Редактирование времени\n\n" +
               formatMessage(SUCCESS + " Час выбран: %02d:00\n\nТеперь выберите минуты:", hour);
    }
    
    /**
     * Формирует сообщение о выборе времени (строковый формат).
     * 
     * @param formattedTime отформатированное время
     * @return отформатированное сообщение
     */
    public String buildTimeSelectedMessage(String formattedTime) {
        return bold(EVENT_CREATION_HEADER) + "\n\n" +
                formatMessage(SUCCESS + " Время выбрано: %s\n\nВыберите тип события:", 
                formattedTime);
    }
    
    /**
     * Формирует сообщение для выбора часа.
     * 
     * @return отформатированное сообщение
     */
    public String buildSelectHourMessage() {
        return TIME + " Выберите час:";
    }
    
    /**
     * Формирует сообщение для выбора часа при редактировании времени события.
     * 
     * @return отформатированное сообщение с шапкой
     */
    public String buildEditTimeSelectHourMessage() {
        return TIME + " Редактирование времени\n\n\nВыберите новое время:\n";
    }

    /**
     * Формирует сообщение для выбора даты при создании нового события.
     * 
     * @return отформатированное сообщение с шапкой
     */
    public String buildSelectDateMessageWithHeader() {
        return bold(EVENT_CREATION_HEADER) + "\n\nВыберите дату события:";
    }
    
    /**
     * Формирует сообщение для выбора даты при повторении события.
     * 
     * @param originalEvent исходное событие, которое повторяется
     * @return отформатированное сообщение с информацией о повторении
     */
    public String buildRepeatEventSelectDateMessage(@NonNull Event originalEvent) {
        StringBuilder sb = new StringBuilder();
        sb.append(REPEAT).append(" ").append(bold("Повторение события")).append("\n\n");
        sb.append(DESCRIPTION).append(" ").append(bold("Название: ")).append(escape(originalEvent.getTitle())).append("\n");
        
        if (originalEvent.getDescription() != null && !originalEvent.getDescription().isBlank()) {
            sb.append(NOTE).append(" ").append(bold("Описание: ")).append(escape(originalEvent.getDescription())).append("\n");
        }
        
        sb.append(CREATOR).append(" ").append(bold("Тип: "))
          .append(originalEvent.getIsPersonal() ? "Персональное" : "Семейное").append("\n\n");
        sb.append("Выберите новую дату для события:");
        
        return sb.toString();
    }
    
    /**
     * Формирует сообщение о невозможности создания события на сегодня.
     * 
     * @return отформатированное сообщение
     */
    public String buildTooLateForTodayMessage() {
        return CLOCK + " " + bold("Слишком поздно создавать события на сегодня") + "\n\n" +
               escape("Выберите завтрашний день или другую дату.");
    }
    
    /**
     * Формирует сообщение с предложением выбрать следующий час.
     * 
     * @param currentHour текущий выбранный час (0-23)
     * @return отформатированное сообщение
     */
    public String buildSelectNextHourMessage(int currentHour) {
        return CLOCK + " " + bold("Все минуты для этого часа уже прошли") + "\n\n" +
               formatMessage("Час %02d:XX недоступен. Выберите следующий час:", currentHour);
    }
    
    /**
     * Формирует сообщение о выборе типа события.
     * 
     * @param isPersonal true если персональное событие
     * @return отформатированное сообщение
     */
    public String buildEventTypeSelectedMessage(boolean isPersonal) {
        if (isPersonal) {
            return bold(EVENT_CREATION_HEADER) + "\n\n" +
                   SUCCESS + " " + escape("Выбрано: Персональное событие") + "\n\n" +
                   italic("Только вы будете видеть это событие.");

        } else {
            return bold(EVENT_CREATION_HEADER) + "\n\n" +
                   SUCCESS + " " + escape("Выбрано: Семейное событие") + "\n\n" +
                   italic("Все члены семьи будут видеть это событие.");
        }
    }
    
    /**
     * Формирует заголовок для списка "Мои события".
     * 
     * @param eventCount количество событий пользователя (должно быть больше 0)
     * @return отформатированный заголовок с использованием MarkdownV2
     */
    public String buildMyEventsHeader(int eventCount) {
        return MY_EVENTS + " " + bold("Мои события") + "\n\n" +
                escape("Всего событий: ") + escape(String.valueOf(eventCount)) + "\n";
    }
    
    /**
     * Формирует сообщение о пустом состоянии списка "Мои события".
     * 
     * @return отформатированное сообщение о пустом состоянии
     */
    public String buildEmptyMyEventsMessage() {
        return DESCRIPTION + " " + bold("Мои события") + "\n\n" +
                escape("У вас пока нет созданных событий.\n\n") +
                escape("Используйте ") + escape(ADD_EVENT + " /add_event") + escape(" для добавления нового события.");
    }
    
    /**
     * Формирует заголовок для корзины удаленных событий.
     * 
     * @param eventCount количество событий в корзине (должно быть больше 0)
     * @return отформатированный заголовок с использованием MarkdownV2
     */
    public String buildTrashHeader(int eventCount) {
        return TRASH + " " + bold("Корзина") + "\n\n" +
                italic("Удаленные события хранятся 30 дней") + "\n\n" +
                escape("Всего событий: ") + escape(String.valueOf(eventCount)) + escape("\n");
    }
    
    /**
     * Формирует сообщение о событии для отображения в списке "Мои события".
     *
     * @param event событие для форматирования
     *
     * @return отформатированное сообщение с MarkdownV2 экранированием
     * @throws IllegalArgumentException если event равен null
     */
    public String buildEventMessage(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event не может быть null");
        }
        
        StringBuilder sb = new StringBuilder();

        sb.append(escape(TITLE + " ")).append(bold(event.getTitle())).append(escape("\n"));
        sb.append(escape(DATE + " Дата: ")).append(escape(formatDate(event.getEventDate()))).append(escape("\n"));
        sb.append(escape(TIME + " Время: ")).append(escape(formatTime(event.getEventTime())));
        
        // Добавляем тип события
        sb.append(escape("\n"));
        boolean isPersonalValue = event.getIsPersonal() != null ? event.getIsPersonal() : false;
        if (isPersonalValue) {
            sb.append(escape(PERSONAL_EVENT_TYPE));

        } else {
            sb.append(escape(FAMILY_EVENT_TYPE));
        }
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            sb.append(escape("\n" + DESCRIPTION + " Описание: ")).append(escape(event.getDescription()));
        }
        
        return sb.toString();
    }
    
    /**
     * Формирует текст сообщения о событии с учетом флага isMyEventsHeader.
     *
     * @param event событие для форматирования
     * @param eventCount количество активных событий пользователя (используется, только если isMyEventsHeader = true)
     *
     * @return отформатированный текст сообщения, возможно, с шапкой
     * @throws IllegalArgumentException если event равен null
     */
    public String buildEventMessageWithHeader(Event event, int eventCount) {
        if (event == null) {
            throw new IllegalArgumentException("Event не может быть null");
        }
        
        String messageText = buildEventMessage(event);
        
        // Если это первое событие в списке "Мои события", добавляем шапку
        if (Boolean.TRUE.equals(event.getIsMyEventsHeader())) {
            String header = buildMyEventsHeader(eventCount);
            messageText = header + "\n" + messageText;
        }
        
        return messageText;
    }
    
    /**
     * Формирует сообщение о завершенном событии с заметкой.
     *
     * @param event завершенное событие (может содержать заметку или нет)
     *
     * @return отформатированное сообщение с использованием MarkdownV2
     * @throws IllegalArgumentException если event равен null
     */
    public String buildCompletedEventMessage(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event не может быть null");
        }
        
        StringBuilder message = new StringBuilder();
        message.append(COMPLETED).append(" ").append(bold("Событие завершено")).append("\n\n");
        message.append(buildEventMessage(event));
        
        // Добавляем секцию заметки, если она присутствует
        if (event.getCompletionNote() != null && !event.getCompletionNote().isBlank()) {
            message.append("\n\n").append(DESCRIPTION).append(" ").append(bold("Заметка:")).append("\n");
            message.append(escape(event.getCompletionNote()));
        }
        
        return message.toString();
    }
    
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
        return dateTimeFormattingService.formatDate(date);
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
        return dateTimeFormattingService.formatTime(time);
    }
    
    /**
     * Формирует сообщение со списком событий на дату (для прошлых дат).
     * 
     * @param date дата
     * @param events список событий
     *
     * @return отформатированное сообщение
     */
    public String buildDateEventsListMessage(LocalDate date, @NonNull List<Event> events) {
        return escape(DATE + " События на ") + bold(formatDate(date)) + "\n\n" +
                escape("Всего событий: ") + bold(String.valueOf(events.size())) + "\n\n" +
                escape("Выберите событие для просмотра:");
    }
    
    /**
     * Формирует сообщение для создания события на дату (для будущих дат без событий).
     * 
     * @param date дата
     * @return отформатированное сообщение
     */
    public String buildCreateEventOnDateMessage(LocalDate date) {
        return escape(DATE + " ") + bold(formatDate(date)) + "\n\n" +
                escape(NO_EVENTS_ON_DATE + ".") + "\n\n" +
                escape("Хотите создать событие?");
    }
    
    /**
     * Формирует сообщение для управления событиями на дату (для будущих дат с событиями).
     * 
     * @param date дата
     * @param events список событий
     *
     * @return отформатированное сообщение
     */
    public String buildDateEventsManagementMessage(LocalDate date, @NonNull List<Event> events) {
        return escape(DATE + " События на ") + bold(formatDate(date)) + "\n\n" +
                escape("Всего событий: ") + bold(String.valueOf(events.size())) + "\n\n" +
                escape("Выберите действие:");
    }
    
    /**
     * Формирует сообщение для выбора времени события.
     * 
     * @param date выбранная дата
     * @return отформатированное сообщение
     */
    public String buildSelectTimeMessage(LocalDate date) {
        return escape(CREATION + " Создание нового события") + "\n\n" +
               escape(SUCCESS + " Дата: ") + bold(formatDate(date)) + "\n\n" +
               escape("Выберите время события:");
    }
    
    /**
     * Формирует сообщение для календаря просмотра событий.
     * 
     * @return отформатированное сообщение с шапкой
     */
    public String buildCalendarViewMessage() {
        return CALENDAR + " " + bold("Календарь событий") + "\n\n" +
               escape("Выберите дату для просмотра, создания или редактирования событий:");
    }
}
