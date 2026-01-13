package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

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
 *   <li>Отображает команды в кликабельном формате (без моноширинного форматирования)</li>
 * </ul>
 * 
 * <p>Команда /help не требует авторизации и доступна всем пользователям,
 * включая тех, кто еще не зарегистрирован в системе.</p>
 * 
 * <p><b>Важно:</b> Команды отображаются в формате /command без использования
 * backticks, что делает их кликабельными в Telegram-клиенте. При нажатии на
 * команду она автоматически отправляется боту.</p>
 * 
 * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5</p>
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
     * <p>Пользователь считается авторизованным, если он существует в базе данных
     * И состоит в семье (user != null && user.hasFamily()). Это обеспечивает
     * согласованность с логикой авторизации в других обработчиках команд.</p>
     * 
     * <p>Для неавторизованных пользователей:</p>
     * <ul>
     *   <li>Команды, требующие авторизации, помечаются эмодзи "🔒"</li>
     *   <li>Отображается информация о необходимости регистрации и добавления в семью</li>
     *   <li>Добавляется описание возможностей бота</li>
     * </ul>
     * 
     * <p>Каждая команда отображается в формате:</p>
     * <pre>
     * [🔒] /команда - Описание команды
     * </pre>
     * 
     * @param message входящее сообщение от Telegram, содержащее команду /help
     * @param user пользователь из базы данных (может быть null для незарегистрированных
     *             пользователей или не null для пользователей без семьи)
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
        boolean isAuthorized = (user != null && user.hasFamily());

        log.info("Обработка команды /help: telegramId={}, username={}, isAuthorized={}, hasFamily={}", 
                telegramId, username, isAuthorized, user != null && user.hasFamily());

        // Формируем список команд с учетом статуса авторизации
        String commandsList = buildCommandsList(isAuthorized);

        log.debug("Сформирован список команд для пользователя: telegramId={}, commandsCount={}, isAuthorized={}", 
                telegramId, commandHandlers != null ? commandHandlers.size() : 0, isAuthorized);

        return buildHelpMessage(commandsList, isAuthorized);
    }

    /**
     * Формирует список всех доступных команд с описаниями.
     * 
     * <p>Команды сортируются в алфавитном порядке для удобства поиска.
     * Каждая команда форматируется в виде:</p>
     * <pre>
     * [эмодзи] /команда - Описание команды
     * </pre>
     * 
     * <p>Команда /start исключается из списка, так как она предназначена только
     * для первоначальной регистрации и не должна отображаться в справке.</p>
     * 
     * <p>Для неавторизованных пользователей команды, требующие авторизации,
     * помечаются эмодзи "🔒" для визуального обозначения ограничения доступа.</p>
     * 
     * <p>Для авторизованных пользователей каждая команда отображается с тематическим
     * эмодзи, соответствующим её назначению (например, 📅 для календаря, 🔍 для поиска).</p>
     * 
     * <p>Команды отображаются в кликабельном формате без использования
     * моноширинного форматирования (backticks). Это позволяет пользователям
     * нажимать на команды в Telegram-клиенте для их автоматической отправки.</p>
     * 
     * <p>Если список обработчиков пуст, возвращается сообщение об отсутствии команд.</p>
     * 
     * <p><b>Требования:</b> 2.3, 2.4, 3.1</p>
     * 
     * @param isAuthorized статус авторизации пользователя
     * @return отформатированный список команд или сообщение об отсутствии команд
     */
    private String buildCommandsList(boolean isAuthorized) {
        if (commandHandlers == null || commandHandlers.isEmpty()) {
            log.warn("Список обработчиков команд пуст");
            return escape("В данный момент команды недоступны.");
        }

        return commandHandlers.stream()
                .filter(handler -> handler != null) // Фильтруем null значения
                .filter(handler -> !"/start".equals(handler.getCommand())) // Исключаем команду /start из списка
                .sorted(Comparator.comparing(CommandHandler::getCommand))
                .map(handler -> {
                    String emoji;
                    
                    if (!isAuthorized && handler.requiresAuth()) {
                        // Для неавторизованных пользователей: эмодзи замка для команд с авторизацией
                        emoji = "🔒 ";
                    } else if (isAuthorized) {
                        // Для авторизованных пользователей: тематические эмодзи
                        String thematicEmoji = getCommandEmoji(handler.getCommand());
                        emoji = thematicEmoji.isEmpty() ? "" : thematicEmoji + " ";
                    } else {
                        // Для команд, не требующих авторизации у неавторизованных пользователей
                        emoji = "";
                    }
                    
                    // Команды экранируем полностью, чтобы избежать проблем с MarkdownV2
                    // Символы подчеркивания в командах типа /add_event могут интерпретироваться как курсив
                    return emoji + escape(handler.getCommand()) + " " + escape("-") + " " + escape(handler.getDescription());
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Формирует полное сообщение справки с заголовком и списком команд.
     * 
     * <p>Сообщение включает:</p>
     * <ul>
     *   <li>Заголовок с эмодзи для визуального выделения</li>
     *   <li>Краткое описание назначения справки</li>
     *   <li>Для неавторизованных пользователей - информацию о регистрации</li>
     *   <li>Описание возможностей бота</li>
     *   <li>Список всех доступных команд</li>
     *   <li>Инструкции по использованию команд</li>
     *   <li>Контактную информацию для получения помощи</li>
     * </ul>
     * 
     * <p>Использует Markdown форматирование для улучшения читаемости.</p>
     * 
     * @param commandsList отформатированный список команд
     * @param isAuthorized статус авторизации пользователя
     * @return полное сообщение справки
     */
    private String buildHelpMessage(String commandsList, boolean isAuthorized) {
        StringBuilder message = new StringBuilder();
        
        // Заголовок
        message.append("📚 ").append(bold("Справка по командам Семейного Календаря")).append("\n\n");
        
        // Описание возможностей бота
        message.append(escape("Семейный календарь помогает организовать события и задачи для всей семьи. "));
        message.append(escape("Вы можете создавать события, просматривать расписание, получать напоминания и многое другое."));
        message.append("\n\n");
        
        // Информация о регистрации для неавторизованных пользователей
        if (!isAuthorized) {
            message.append(escape("⚠️ Вы не зарегистрированы в семейном календаре.")).append("\n");
            message.append(escape("Некоторые команды требуют регистрации (отмечены 🔒).")).append("\n");
            message.append(escape("Для получения доступа к полному функционалу обратитесь к администратору вашей семьи."));
            message.append("\n\n");
        }
        
        // Список команд
        message.append(bold("Доступные команды:")).append("\n\n");
        message.append(commandsList).append("\n\n");
        
        // Инструкции по использованию
        message.append(escape("Для использования команды просто отправьте её в чат.")).append("\n");
        
        // Дополнительная информация для неавторизованных
        if (!isAuthorized) {
            message.append(escape("После регистрации вам станут доступны все функции бота."));
        } else {
            message.append(escape("Если у вас возникли вопросы, обратитесь к администратору семьи."));
        }
        
        String result = message.toString();
        
        // Отладочный вывод
        log.info("=== ОТЛАДКА СООБЩЕНИЯ СПРАВКИ ===");
        log.info("Длина: {} символов", result.length());
        log.info("Длина в байтах: {} байт", result.getBytes().length);
        log.info("Авторизован: {}", isAuthorized);
        log.info("Текст сообщения:");
        log.info("{}", result);
        log.info("=== КОНЕЦ ОТЛАДКИ ===");
        
        return result;
    }

    /**
     * Возвращает тематический эмодзи для указанной команды.
     * 
     * <p>Метод выполняет маппинг команд на соответствующие эмодзи для улучшения
     * визуального восприятия списка команд. Каждый эмодзи выбран таким образом,
     * чтобы интуитивно отражать назначение команды.</p>
     * 
     * <p>Маппинг команд на эмодзи:</p>
     * <ul>
     *   <li>/start - 🚀 (начало работы, запуск)</li>
     *   <li>/help - 📚 (справка, документация)</li>
     *   <li>/add_event - ➕ (добавление нового элемента)</li>
     *   <li>/my_events - 📋 (список, управление)</li>
     *   <li>/upcoming_events - 📅 (календарь, предстоящие события)</li>
     *   <li>/today - 📆 (сегодняшний день)</li>
     *   <li>/week - 🗓️ (неделя)</li>
     *   <li>/search - 🔍 (поиск)</li>
     *   <li>/filter - 🔎 (фильтрация)</li>
     *   <li>/stats - 📊 (статистика, аналитика)</li>
     *   <li>/trash - 🗑️ (корзина, удаленные элементы)</li>
     * </ul>
     * 
     * <p>Для неизвестных команд возвращается пустая строка, что позволяет
     * безопасно использовать метод для любых команд без риска исключений.</p>
     * 
     * <p><b>Требования:</b> 3.1, 3.2, 3.3, 3.4, 3.5</p>
     * 
     * @param command имя команды (например, "/add_event")
     * @return эмодзи, соответствующий команде, или пустая строка для неизвестных команд
     */
    private String getCommandEmoji(String command) {
        if (command == null) {
            return "";
        }
        
        return switch (command) {
            case "/start" -> "🚀";
            case "/help" -> "📚";
            case "/add_event" -> "➕";
            case "/my_events" -> "📋";
            case "/upcoming_events" -> "📅";
            case "/today" -> "📆";
            case "/week" -> "🗓️";
            case "/search" -> "🔍";
            case "/filter" -> "🔎";
            case "/stats" -> "📊";
            case "/trash" -> "🗑️";
            default -> "";
        };
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
