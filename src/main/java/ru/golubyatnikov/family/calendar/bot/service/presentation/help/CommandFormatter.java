package ru.golubyatnikov.family.calendar.bot.service.presentation.help;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.handler.command.CommandHandler;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.help.CommandMetadata;
import java.util.Optional;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape;

/**
 * Форматирование команд для отображения в справке.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class CommandFormatter {

    private static final String FALLBACK_DESCRIPTION = "Описание недоступно";

    private final CommandMetadata commandMetadata;

    /**
     * Форматирует команду для отображения в справке.
     *
     * @param handler обработчик команды
     * @param isAuthorized статус авторизации пользователя
     *
     * @return отформатированная строка команды или Optional.empty() при ошибке
     */
    public Optional<String> format(CommandHandler handler, boolean isAuthorized) {
        try {
            String command = handler.getCommand();
            if (command == null) {
                return Optional.empty();
            }

            String emoji = determineEmoji(command, handler.requiresAuth(), isAuthorized);
            String description = Optional.ofNullable(handler.getDescription())
                    .orElse(FALLBACK_DESCRIPTION);

            return Optional.of(buildFormattedCommand(emoji, command, description));

        } catch (Exception e) {
            log.error("Ошибка при форматировании команды {}: {}", handler.getCommand(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Определяет эмодзи для команды в зависимости от статуса авторизации.
     */
    private @NonNull String determineEmoji(String command, boolean requiresAuth, boolean isAuthorized) {
        if (!isAuthorized && requiresAuth) {
            return commandMetadata.getLockEmoji() + " ";
        }
        
        if (isAuthorized) {
            String thematicEmoji = commandMetadata.getEmojiFor(command);
            return thematicEmoji.isEmpty() ? "" : thematicEmoji + " ";
        }
        
        return "";
    }

    /**
     * Собирает отформатированную строку команды.
     */
    private @NonNull String buildFormattedCommand(String emoji, String command, String description) {
        return emoji + escape(command) + " " + escape("-") + " " + escape(description);
    }
}
