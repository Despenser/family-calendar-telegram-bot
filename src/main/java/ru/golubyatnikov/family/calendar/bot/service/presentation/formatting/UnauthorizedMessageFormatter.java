package ru.golubyatnikov.family.calendar.bot.service.presentation.formatting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.config.UnauthorizedMessagesConfig;
import ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter;

/**
 * Компонент для форматирования финальных сообщений с префиксом и инструкциями.
 * Применяет экранирование для MarkdownV2.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnauthorizedMessageFormatter {
    
    private static final String FALLBACK_MESSAGE =
            """
                    🔒 Эта функция доступна только зарегистрированным пользователям\\.
            
                    Для получения доступа обратитесь к администратору\\.
            """;
    
    private final UnauthorizedMessagesConfig config;
    
    /**
     * Форматирует сообщение с префиксом и инструкциями.
     * Применяет экранирование специальных символов MarkdownV2.
     *
     * @param mainText основной текст сообщения
     * @return отформатированное сообщение
     */
    public String format(String mainText) {
        try {
            return MarkdownFormatter.formatMessage(
                "%s %s\n\n%s",
                config.getPrefix(),
                mainText,
                config.getContactAdmin()
            );

        } catch (Exception e) {
            log.error("Ошибка форматирования сообщения: {}", e.getMessage(), e);
            return FALLBACK_MESSAGE;
        }
    }
}
