package ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;

/**
 * Сервис для извлечения данных из CallbackQuery.
 * Централизует логику валидации и извлечения данных из callback запросов.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-16
 */
@Slf4j
@Component
public class CallbackDataExtractionService {

    /**
     * Извлекает и валидирует данные callback запроса.
     * Создает контекст для обработки callback query.
     *
     * @param callbackQuery callback запрос от Telegram
     * @param user пользователь, инициировавший callback
     *
     * @return контекст callback query
     * @throws IllegalArgumentException если callback data равен null
     */
    public CallbackQueryContext extractContext(@NonNull CallbackQuery callbackQuery, @NonNull User user) {
        String callbackData = callbackQuery.getData();
        
        if (callbackData == null) {
            log.warn("Получен callback с null данными от пользователя userId={}", user.getId());
            throw new IllegalArgumentException("Callback data не может быть null");
        }
        
        return CallbackQueryContext.builder()
                .callbackQueryId(callbackQuery.getId())
                .callbackData(callbackData)
                .chatId(callbackQuery.getMessage().getChatId())
                .messageId(callbackQuery.getMessage().getMessageId())
                .user(user)
                .build();
    }
}
