package ru.golubyatnikov.family.calendar.bot.service.presentation.formatting;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Сервис для форматирования результатов поиска событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Service
@RequiredArgsConstructor
public class SearchResultFormattingService {
    
    private final EventFormattingService eventFormattingService;
    
    /**
     * Формирует сообщение с результатами поиска.
     *
     * @param query поисковый запрос
     * @param results список найденных событий
     *
     * @return отформатированное сообщение
     */
    public @NonNull String formatSearchResults(@NonNull String query,
                                               @NonNull List<Event> results) {

        StringBuilder messageBuilder = new StringBuilder();
        appendSearchHeader(messageBuilder, query);
        
        if (results.isEmpty()) {
            appendEmptyResults(messageBuilder, query);

        } else {
            appendEventResults(messageBuilder, results);
        }
        
        return messageBuilder.toString();
    }
    
    /**
     * Добавляет заголовок результатов поиска.
     */
    private void appendSearchHeader(@NonNull StringBuilder builder, @NonNull String query) {
        builder.append("🔍 ").append(bold("Результаты поиска")).append("\n\n");
        builder.append(italic("Запрос: \"" + query + "\"")).append("\n\n");
    }
    
    /**
     * Добавляет сообщение об отсутствии результатов.
     */
    private void appendEmptyResults(@NonNull StringBuilder builder, @NonNull String query) {
        builder.append(escape("По запросу \""))
               .append(escape(query))
               .append(escape("\" ничего не найдено."))
               .append("\n\n");
        
        builder.append(italic("Попробуйте изменить запрос или использовать другие ключевые слова."))
               .append("\n\n");
        
        builder.append(escape("Вы можете использовать "))
               .append("📅 ")
               .append(escape("/today"))
               .append(escape(" или "))
               .append("📆 ")
               .append(escape("/week"))
               .append(escape(" для просмотра событий."));
    }
    
    /**
     * Добавляет список найденных событий.
     */
    private void appendEventResults(@NonNull StringBuilder builder, @NonNull List<Event> results) {
        User eventUser = results.getFirst().getUser();
        
        for (int i = 0; i < results.size(); i++) {
            Event event = results.get(i);
            builder.append(eventFormattingService.formatSearchResult(event, eventUser));
            
            if (i < results.size() - 1) {
                builder.append(escape("\n"));
                builder.append(eventFormattingService.formatDaySeparator());
                builder.append(escape("\n\n"));
            }
        }
        
        builder.append(escape("\n"));
        builder.append(italic("Найдено событий: " + results.size()));
    }
}
