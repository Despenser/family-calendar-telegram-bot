package ru.golubyatnikov.family.calendar.bot.service.presentation.formatting;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.config.MyEventsConfig;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import java.time.LocalDate;
import java.time.LocalTime;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.MY_EVENTS;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.formatMessage;

/**
 * Сервис для форматирования постраничного списка событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-26
 */
@Service
@RequiredArgsConstructor
public class MyEventsPageFormattingService {
    
    private final DateTimeFormattingService dateTimeFormattingService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final MyEventsConfig config;
    
    /**
     * Формирует заголовок страницы со списком событий.
     * 
     * @param totalEvents общее количество событий
     * @param currentPage текущая страница (начиная с 1)
     * @param totalPages общее количество страниц
     *
     * @return отформатированный заголовок
     */
    public String buildPageHeader(long totalEvents, int currentPage, int totalPages) {
        return formatMessage(
                MY_EVENTS + " Мои события\n\n" +
                "Всего событий: " + totalEvents + "\n" +
                "Страница " + currentPage + " из " + totalPages + "\n\n" +
                "Выберите событие из списка ниже:"
        );
    }
    
    /**
     * Формирует текст кнопки для события в списке.
     * Формат: "Название → ДД.ММ.ГГГГ в ЧЧ:ММ"
     * 
     * @param event событие
     * @return текст кнопки
     */
    public String buildEventButtonText(@NonNull Event event) {
        StringBuilder buttonText = new StringBuilder();
        
        // Ограничиваем длину названия события для кнопки
        String title = event.getTitle();
        int maxLength = config.getMaxTitleLength();
        if (title.length() > maxLength) {
            title = title.substring(0, maxLength - 3) + "...";
        }
        
        buttonText.append(title);
        buttonText.append(" → ");
        buttonText.append(formatDate(event.getEventDate()));
        buttonText.append(" в ");
        buttonText.append(formatTime(event.getEventTime()));
        
        return buttonText.toString();
    }
    
    /**
     * Формирует сообщение об отсутствии событий.
     * 
     * @return отформатированное сообщение
     */
    public String buildNoEventsMessage() {
        return botMessageFormattingService.buildEmptyMyEventsMessage();
    }
    
    /**
     * Форматирует дату в формате ДД.ММ.ГГГГ.
     * 
     * @param date дата для форматирования
     * @return отформатированная дата
     */
    private String formatDate(LocalDate date) {
        if (date == null) {
            return "—";
        }
        return dateTimeFormattingService.formatDate(date);
    }
    
    /**
     * Форматирует время в формате ЧЧ:ММ.
     * 
     * @param time время для форматирования
     * @return отформатированное время
     */
    private String formatTime(LocalTime time) {
        if (time == null) {
            return "—";
        }
        return dateTimeFormattingService.formatTime(time);
    }
}
