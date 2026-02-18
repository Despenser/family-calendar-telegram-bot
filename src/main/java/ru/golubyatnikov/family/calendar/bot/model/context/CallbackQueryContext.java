package ru.golubyatnikov.family.calendar.bot.model.context;

import lombok.Builder;
import org.springframework.lang.NonNull;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;

import java.time.LocalDate;

/**
 * Контекст для обработки callback query.
 * Инкапсулирует все необходимые данные для работы с callback от Telegram.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-16
 */
@Builder
public record CallbackQueryContext(
    @NonNull String callbackQueryId,
    @NonNull String callbackData,
    @NonNull Long chatId,
    @NonNull Integer messageId,
    @NonNull User user
) {
    
    /**
     * Извлекает идентификатор из callback data.
     * 
     * @param prefix префикс для удаления
     * @return извлеченный идентификатор
     */
    public @NonNull Long extractId(@NonNull String prefix) {
        return Long.parseLong(callbackData.substring(prefix.length()));
    }
    
    /**
     * Извлекает дату из callback data.
     * 
     * @param prefix префикс для удаления
     * @return извлеченная дата
     */
    public @NonNull LocalDate extractDate(@NonNull String prefix) {
        String dateStr = callbackData.substring(prefix.length());
        return LocalDate.parse(dateStr);
    }
    
    /**
     * Получает идентификатор пользователя.
     * 
     * @return идентификатор пользователя
     */
    public @NonNull Long getUserId() {
        return user.getId();
    }
}
