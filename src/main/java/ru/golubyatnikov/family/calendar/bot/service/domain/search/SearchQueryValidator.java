package ru.golubyatnikov.family.calendar.bot.service.domain.search;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Сервис для валидации поисковых запросов.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Service
public class SearchQueryValidator {
    
    private static final int MIN_QUERY_LENGTH = 2;
    
    /**
     * Проверяет валидность поискового запроса.
     *
     * @param query поисковый запрос
     * @return true, если запрос валиден
     */
    public boolean isValid(String query) {
        return query != null && query.trim().length() >= MIN_QUERY_LENGTH;
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
