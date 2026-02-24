package ru.golubyatnikov.family.calendar.bot.service.presentation.formatting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.ADD_EVENT;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Event.DESCRIPTION;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Сервис для форматирования сообщений планировщика событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerFormattingService {

    private final BotMessageFormattingService botMessageFormattingService;

    /**
     * Формирует заголовок списка событий.
     * 
     * @param eventCount количество событий
     * @return отформатированный заголовок
     */
    public String buildMyEventsHeader(int eventCount) {
        return botMessageFormattingService.buildMyEventsHeader(eventCount);
    }

    /**
     * Формирует сообщение о событии.
     * 
     * @param event событие
     * @return отформатированное сообщение
     */
    public String buildEventMessage(Event event) {
        return botMessageFormattingService.buildEventMessage(event);
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
        return DESCRIPTION + " " + bold("Мои события") + "\n\n" +
                escape("У вас пока нет созданных событий.\n\n") +
                escape("Используйте ") + escape(ADD_EVENT + " /add_event") + escape(" для добавления нового события.");
    }

    /**
     * Формирует сообщение об ошибке.
     * 
     * @param message текст ошибки
     * @return отформатированное сообщение
     */
    public String buildErrorMessage(String message) {
        return formatMessage(ERROR + " %s\n\n%s", bold("Ошибка"), message);
    }
}
