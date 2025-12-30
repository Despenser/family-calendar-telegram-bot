package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Обработчик команды /help для Telegram бота семейного календаря.
 * 
 * <p>Команда /help предоставляет пользователям справочную информацию
 * о всех доступных командах бота. Она выполняет следующие функции:</p>
 * <ul>
 *   <li>Собирает информацию о всех зарегистрированных обработчиках команд</li>
 *   <li>Формирует отформатированный список команд с описаниями</li>
 *   <li>Использует Markdown форматирование для улучшения читаемости</li>
 *   <li>Сортирует команды в алфавитном порядке</li>
 * </ul>
 * 
 * <p>Команда /help не требует авторизации и доступна всем пользователям,
 * включая тех, кто еще не зарегистрирован в системе.</p>
 * 
 * <p><b>Требования:</b> 12.3</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>
 * Пользователь отправляет: /help
 * 
 * Бот отвечает:
 * "📚 *Справка по командам Семейного Календаря*
 * 
 * Вот список всех доступных команд:
 * 
 * /add_event - Добавить новое событие в календарь
 * /help - Показать список всех команд
 * /my_events - Управление моими событиями
 * /start - Начать работу с ботом
 * /upcoming_events - Показать предстоящие события семьи
 * 
 * Для использования команды просто отправьте её в чат.
 * Если у вас возникли вопросы, обратитесь к администратору семьи."
 * </pre>
 * 
 * @see CommandHandler
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Component
@Slf4j
public class HelpCommandHandler implements CommandHandler {

    private final List<CommandHandler> commandHandlers;

    /**
     * Конструктор для внедрения зависимостей.
     * 
     * <p>Spring автоматически внедряет все бины, реализующие интерфейс {@link CommandHandler},
     * включая сам HelpCommandHandler. Это позволяет динамически формировать список
     * всех доступных команд без необходимости ручной регистрации.</p>
     * 
     * @param commandHandlers список всех доступных обработчиков команд,
     *                        автоматически внедряемых Spring
     */
    public HelpCommandHandler(List<CommandHandler> commandHandlers) {
        this.commandHandlers = commandHandlers;
        log.info("HelpCommandHandler инициализирован с {} обработчиками команд", 
                commandHandlers != null ? commandHandlers.size() : 0);
    }

    /**
     * Обрабатывает команду /help от пользователя.
     * 
     * <p>Метод собирает информацию о всех зарегистрированных командах,
     * сортирует их в алфавитном порядке и формирует отформатированный
     * список с использованием Markdown.</p>
     * 
     * <p>Каждая команда отображается в формате:</p>
     * <pre>
     * /команда - Описание команды
     * </pre>
     * 
     * @param message входящее сообщение от Telegram, содержащее команду /help
     * @param user пользователь из базы данных (может быть null, так как команда
     *             не требует авторизации)
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
        String username = message.getFrom().getUserName();

        log.info("Обработка команды /help: telegramId={}, username={}", telegramId, username);

        // Формируем список команд
        String commandsList = buildCommandsList();

        log.debug("Сформирован список команд для пользователя: telegramId={}, commandsCount={}", 
                telegramId, commandHandlers != null ? commandHandlers.size() : 0);

        return buildHelpMessage(commandsList);
    }

    /**
     * Формирует список всех доступных команд с описаниями.
     * 
     * <p>Команды сортируются в алфавитном порядке для удобства поиска.
     * Каждая команда форматируется в виде:</p>
     * <pre>
     * /команда - Описание команды
     * </pre>
     * 
     * <p>Если список обработчиков пуст, возвращается сообщение об отсутствии команд.</p>
     * 
     * @return отформатированный список команд или сообщение об отсутствии команд
     */
    private String buildCommandsList() {
        if (commandHandlers == null || commandHandlers.isEmpty()) {
            log.warn("Список обработчиков команд пуст");
            return "В данный момент команды недоступны.";
        }

        return commandHandlers.stream()
                .filter(handler -> handler != null) // Фильтруем null значения
                .sorted(Comparator.comparing(CommandHandler::getCommand))
                .map(handler -> String.format("%s - %s", 
                        handler.getCommand(), 
                        handler.getDescription()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Формирует полное сообщение справки с заголовком и списком команд.
     * 
     * <p>Сообщение включает:</p>
     * <ul>
     *   <li>Заголовок с эмодзи для визуального выделения</li>
     *   <li>Краткое описание назначения справки</li>
     *   <li>Список всех доступных команд</li>
     *   <li>Инструкции по использованию команд</li>
     *   <li>Контактную информацию для получения помощи</li>
     * </ul>
     * 
     * <p>Использует Markdown форматирование для улучшения читаемости.</p>
     * 
     * @param commandsList отформатированный список команд
     * @return полное сообщение справки
     */
    private String buildHelpMessage(String commandsList) {
        return String.format(
                "📚 *Справка по командам Семейного Календаря*\n\n" +
                "Вот список всех доступных команд:\n\n" +
                "%s\n\n" +
                "Для использования команды просто отправьте её в чат.\n" +
                "Если у вас возникли вопросы, обратитесь к администратору семьи.",
                commandsList
        );
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
     * <p>Команда /help не требует авторизации, так как справка должна быть
     * доступна всем пользователям, включая незарегистрированных.</p>
     * 
     * @return false, так как команда доступна всем пользователям
     */
    @Override
    public boolean requiresAuth() {
        return false;
    }
}
