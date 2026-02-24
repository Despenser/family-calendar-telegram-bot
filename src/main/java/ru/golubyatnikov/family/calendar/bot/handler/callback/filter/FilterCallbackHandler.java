package ru.golubyatnikov.family.calendar.bot.handler.callback.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventFilter;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.domain.user.UserService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter;
import java.util.List;
import java.util.stream.IntStream;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.FILTER;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Misc.SEPARATOR;

/**
 * Обработчик callback queries для фильтрации событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FilterCallbackHandler implements CallbackHandler {
    
    private final UserService userService;
    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final CallbackDataExtractionService callbackDataExtractionService;
    private final CallbackQueryService callbackQueryService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.FILTER;
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(@NonNull CallbackQuery callbackQuery, @NonNull User user) throws Exception {
        CallbackQueryContext context = callbackDataExtractionService.extractContext(callbackQuery, user);
        
        EventFilter filter = parseFilter(context.callbackData());
        userService.setEventFilter(user.getId(), filter);

        List<Event> events = eventService.getFilteredEvents(user, filter);
        String messageText = formatFilteredEvents(events, filter);
        String confirmationText = "Фильтр применен: " + filter.getDisplayName();
        
        callbackQueryService.editMessageAndAnswer(context, messageText, 
                keyboardService.createFilterKeyboard(), confirmationText);
        
        }
    
    /**
     * Парсит тип фильтра из callback data.
     *
     * @param callbackData строка callback data (формат: filter_{type})
     * @return соответствующий EventFilter
     */
    private EventFilter parseFilter(String callbackData) {
        String filterType = CallbackPrefix.FILTER.extractPayload(callbackData);
        
        return switch (filterType) {
            case "family" -> EventFilter.FAMILY;
            case "personal" -> EventFilter.PERSONAL;
            default -> EventFilter.ALL;
        };
    }
    
    /**
     * Форматирует список отфильтрованных событий для отображения.
     *
     * @param events список событий для форматирования
     * @param filter примененный фильтр
     *
     * @return отформатированный текст сообщения с экранированием MarkdownV2
     */
    private @NonNull String formatFilteredEvents(@NonNull List<Event> events, @NonNull EventFilter filter) {
        StringBuilder sb = new StringBuilder();
        
        // Заголовок с типом фильтра
        sb.append(MarkdownFormatter.escapeMarkdownV2(FILTER + " Фильтр: "))
          .append("*")
          .append(MarkdownFormatter.escapeMarkdownV2(filter.getDisplayName()))
          .append("*\n\n");
        
        if (events.isEmpty()) {
            sb.append(MarkdownFormatter.escapeMarkdownV2("Нет событий для отображения"));

        } else {
            sb.append(MarkdownFormatter.escapeMarkdownV2("Найдено событий: "))
              .append("*")
              .append(events.size())
              .append("*\n\n");

            IntStream.range(0, events.size()).forEach(i -> {
                Event event = events.get(i);
                sb.append(botMessageFormattingService.buildEventMessage(event));
                if (i < events.size() - 1) {
                    sb.append("\n\n")
                            .append(MarkdownFormatter.escapeMarkdownV2(SEPARATOR))
                            .append("\n\n");
                }
            });
        }
        
        return sb.toString();
    }
}
