package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.help.CommandCategory;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.help.CommandGrouper;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.help.HelpMessageBuilder;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;

import java.util.List;
import java.util.Map;

/**
 * Обработчик команды /help для Telegram бота семейного календаря.
 *
 * @author Golubyatnikov Aleksey
 * @since 2025-12-30
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HelpCommandHandler implements CommandHandler {

    private final List<CommandHandler> commandHandlers;
    private final CommandGrouper commandGrouper;
    private final HelpMessageBuilder messageBuilder;

    /**
     * Обрабатывает команду /help от пользователя.
     *
     * @param message входящее сообщение от Telegram, содержащее команду /help
     * @param user пользователь из базы данных (может быть null для незарегистрированных
     *             пользователей или не null для пользователей без семьи)
     *
     * @return текст справки со списком всех доступных команд
     * @throws IllegalArgumentException если message равен null
     */
    @Override
    public String handle(Message message, User user) {
        if (message == null) {
            log.error("Получено null сообщение в HelpCommandHandler");
            throw new IllegalArgumentException("Сообщение не может быть null");
        }

        Long telegramId = message.getFrom().getId();
        boolean isAuthorized = (user != null && user.hasFamily());

        log.debug("Обработка команды /help: telegramId={}, isAuthorized={}", 
                telegramId, isAuthorized);

        if (commandHandlers == null || commandHandlers.isEmpty()) {
            log.warn("Список обработчиков команд пуст");
            return "В данный момент команды недоступны. Пожалуйста, попробуйте позже.";
        }

        Map<CommandCategory, List<String>> groupedCommands = 
                commandGrouper.groupAndFormat(commandHandlers, isAuthorized);

        String helpMessage = messageBuilder.build(groupedCommands, isAuthorized);

        log.debug("Сформировано сообщение справки: длина={} символов", helpMessage.length());
        return helpMessage;
    }

    /**
     * Возвращает команду, которую обрабатывает этот handler.
     * 
     * @return строка "/help"
     */
    @Override
    public String getCommand() {
        return "/help";
    }

    /**
     * Возвращает описание команды для отображения в справке.
     * 
     * @return описание команды
     */
    @Override
    public String getDescription() {
        return "Показать список всех команд";
    }

    /**
     * Определяет, требуется ли авторизация для выполнения этой команды.
     *
     * @return false, так как команда доступна всем пользователям
     */
    @Override
    public boolean requiresAuth() {
        return false;
    }
}
