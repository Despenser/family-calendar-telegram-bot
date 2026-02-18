package ru.golubyatnikov.family.calendar.bot.service.domain.search;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.config.SearchConfig;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Сервис для валидации поисковых запросов.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Service
@RequiredArgsConstructor
public class SearchQueryValidator {
    
    private final SearchConfig searchConfig;
    
    /**
     * Проверяет валидность поискового запроса.
     *
     * @param query поисковый запрос
     * @return true, если запрос валиден
     */
    public boolean isValid(String query) {
        return query != null && query.trim().length() >= searchConfig.getMinQueryLength();
    }
    
    /**
     * Формирует сообщение об ошибке валидации с подсказкой для повторного ввода.
     * @return сообщение об ошибке
     */
    public @NonNull String getValidationErrorMessage() {
        return "❌ " + escape("Поисковый запрос должен содержать минимум 2 символа.") + "\n\n" +
               "🔍 " + bold("Поиск событий") + "\n\n" +
               escape("Введите текст для поиска в названии или описании событий.") + "\n\n" +
               italic("Например: день рождения, встреча, поездка");
    }
}
