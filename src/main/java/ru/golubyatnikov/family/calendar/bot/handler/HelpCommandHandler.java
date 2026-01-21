package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /help для Telegram бота семейного календаря.
 * 
 * <p>Команда /help предоставляет пользователям справочную информацию
 * о всех доступных командах бота. Она выполняет следующие функции:</p>
 * <ul>
 *   <li>Собирает информацию о всех зарегистрированных обработчиках команд</li>
 *   <li>Группирует команды по функциональным категориям для улучшения навигации</li>
 *   <li>Формирует отформатированный список команд с описаниями</li>
 *   <li>Использует Markdown форматирование для улучшения читаемости</li>
 *   <li>Сортирует команды в алфавитном порядке внутри каждой категории</li>
 *   <li>Отображает команды в кликабельном формате (без моноширинного форматирования)</li>
 *   <li>Помечает команды, требующие авторизации, эмодзи "🔒" для неавторизованных пользователей</li>
 *   <li>Использует тематические эмодзи для визуального выделения команд</li>
 * </ul>
 * 
 * <p>Команда /help не требует авторизации и доступна всем пользователям,
 * включая тех, кто еще не зарегистрирован в системе.</p>
 * 
 * <p><b>Категории команд:</b></p>
 * <ul>
 *   <li>📅 Просмотр событий: /today, /week, /upcoming_events</li>
 *   <li>➕ Управление событиями: /add_event, /my_events</li>
 *   <li>🔍 Поиск и фильтрация: /search, /filter</li>
 *   <li>📊 Статистика и корзина: /stats, /trash</li>
 *   <li>ℹ️ Справка: /help, /start</li>
 * </ul>
 * 
 * <p><b>Форматирование команд:</b> Команды отображаются в формате /command без использования
 * backticks, что делает их кликабельными в Telegram-клиенте. При нажатии на
 * команду она автоматически отправляется боту.</p>
 * 
 * <p><b>Визуальные индикаторы:</b></p>
 * <ul>
 *   <li>Для неавторизованных пользователей: команды с авторизацией помечены 🔒</li>
 *   <li>Для авторизованных пользователей: каждая команда имеет тематический эмодзи</li>
 *   <li>Категории выделены жирным шрифтом с соответствующими эмодзи</li>
 * </ul>
 * 
 * <p><b>Обработка ошибок:</b> Класс включает надежную обработку ошибок:</p>
 * <ul>
 *   <li>Проверка на пустой список команд с логированием предупреждений</li>
 *   <li>Фильтрация null значений в списке обработчиков</li>
 *   <li>Логирование предупреждений для команд без категории</li>
 *   <li>Использование fallback категории (HELP) для неизвестных команд</li>
 *   <li>Корректное экранирование специальных символов MarkdownV2</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5, 
 * 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 
 * 6.1, 6.2, 6.3, 6.4, 6.5</p>
 * 
 * <p><b>Пример использования для авторизованного пользователя:</b></p>
 * <pre>
 * Пользователь отправляет: /help
 * 
 * Бот отвечает:
 * "📚 *Справка по командам Семейного Календаря*
 * 
 * Семейный календарь помогает организовать события и задачи для всей семьи. 
 * Вы можете создавать события, просматривать расписание, получать напоминания и многое другое.
 * 
 * *Доступные команды:*
 * 
 * *📅 Просмотр событий*
 * 📆 /today - Показать события на сегодня
 * 🗓️ /week - Показать события на неделю (7 дней)
 * 📅 /upcoming_events - Показать планы на 30 дней
 * 
 * *➕ Управление событиями*
 * ➕ /add_event - Добавить новое событие в календарь
 * 📋 /my_events - Управление моими событиями
 * 
 * *🔍 Поиск и фильтрация*
 * 🔎 /filter - Фильтрация событий по типу
 * 🔍 /search - Поиск событий по тексту
 * 
 * *📊 Статистика и корзина*
 * 📊 /stats - Статистика событий за месяц
 * 🗑️ /trash - Корзина удаленных событий
 * 
 * *ℹ️ Справка*
 * 📚 /help - Показать список всех команд
 * 
 * Для использования команды просто отправьте её в чат.
 * Если у вас возникли вопросы, обратитесь к администратору семьи."
 * </pre>
 * 
 * <p><b>Пример использования для неавторизованного пользователя:</b></p>
 * <pre>
 * Пользователь отправляет: /help
 * 
 * Бот отвечает:
 * "📚 *Справка по командам Семейного Календаря*
 * 
 * Семейный календарь помогает организовать события и задачи для всей семьи. 
 * Вы можете создавать события, просматривать расписание, получать напоминания и многое другое.
 * 
 * ⚠️ Вы не зарегистрированы в семейном календаре.
 * Некоторые команды требуют регистрации (отмечены 🔒).
 * Для получения доступа к полному функционалу обратитесь к администратору вашей семьи.
 * 
 * *Доступные команды:*
 * 
 * *📅 Просмотр событий*
 * 🔒 /today - Показать события на сегодня
 * 🔒 /week - Показать события на неделю (7 дней)
 * 🔒 /upcoming_events - Показать планы на 30 дней
 * 
 * *➕ Управление событиями*
 * 🔒 /add_event - Добавить новое событие в календарь
 * 🔒 /my_events - Управление моими событиями
 * 
 * ... (остальные категории)
 * 
 * Для использования команды просто отправьте её в чат.
 * После регистрации вам станут доступны все функции бота."
 * </pre>
 * 
 * @see CommandHandler
 * @see CommandCategory
 * @see StartCommandHandler
 * @author Family Calendar Bot Team
 * @version 2.0.0
 * @since 2025-12-30
 */
@Component
@Slf4j
public class HelpCommandHandler implements CommandHandler {

    /**
     * Enum для категорий команд.
     * 
     * <p>Категории используются для группировки команд по функциональному назначению
     * в справке /help. Каждая категория имеет отображаемое имя с эмодзи для
     * визуального выделения и улучшения навигации.</p>
     * 
     * <p><b>Категории:</b></p>
     * <ul>
     *   <li>VIEW_EVENTS - Команды для просмотра событий (today, week, upcoming_events)</li>
     *   <li>MANAGE_EVENTS - Команды для управления событиями (add_event, my_events)</li>
     *   <li>SEARCH_FILTER - Команды для поиска и фильтрации (search, filter)</li>
     *   <li>STATS_TRASH - Команды для статистики и корзины (stats, trash)</li>
     *   <li>HELP - Справочные команды (help, start)</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.2, 2.1, 2.2, 2.3, 2.4, 2.5</p>
     * 
     * @see HelpCommandHandler#COMMAND_CATEGORIES
     * @see HelpCommandHandler#groupCommandsByCategory(List)
     */
    public enum CommandCategory {
        VIEW_EVENTS("Просмотр событий"),
        MANAGE_EVENTS("Управление событиями"),
        SEARCH_FILTER("Поиск и фильтрация"),
        STATS_TRASH("Статистика и корзина"),
        HELP("Справка");

        private final String displayName;

        CommandCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Маппинг команд на категории.
     * 
     * <p>Используется для группировки команд в справке /help.
     * Каждая команда должна быть зарегистрирована в этом маппинге,
     * иначе она будет помещена в категорию HELP по умолчанию
     * с логированием предупреждения.</p>
     * 
     * <p><b>Структура маппинга:</b></p>
     * <ul>
     *   <li>Ключ: имя команды (например, "/add_event")</li>
     *   <li>Значение: категория команды (например, CommandCategory.MANAGE_EVENTS)</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.2, 2.1, 2.2, 2.3, 2.4, 2.5</p>
     * 
     * @see CommandCategory
     * @see #groupCommandsByCategory(List)
     * @see #getCommandCategory(String)
     */
    private static final Map<String, CommandCategory> COMMAND_CATEGORIES = Map.ofEntries(
            Map.entry("/today", CommandCategory.VIEW_EVENTS),
            Map.entry("/week", CommandCategory.VIEW_EVENTS),
            Map.entry("/upcoming_events", CommandCategory.VIEW_EVENTS),
            Map.entry("/add_event", CommandCategory.MANAGE_EVENTS),
            Map.entry("/my_events", CommandCategory.MANAGE_EVENTS),
            Map.entry("/search", CommandCategory.SEARCH_FILTER),
            Map.entry("/filter", CommandCategory.SEARCH_FILTER),
            Map.entry("/stats", CommandCategory.STATS_TRASH),
            Map.entry("/trash", CommandCategory.STATS_TRASH),
            Map.entry("/help", CommandCategory.HELP),
            Map.entry("/start", CommandCategory.HELP)
    );

    /**
     * Маппинг команд на эмодзи.
     * 
     * <p>Используется для визуального выделения команд в списке справки.
     * Каждый эмодзи выбран таким образом, чтобы интуитивно отражать
     * назначение команды.</p>
     * 
     * <p><b>Маппинг эмодзи:</b></p>
     * <ul>
     *   <li>🚀 /start - начало работы, запуск</li>
     *   <li>📚 /help - справка, документация</li>
     *   <li>➕ /add_event - добавление нового элемента</li>
     *   <li>📋 /my_events - список, управление</li>
     *   <li>📅 /upcoming_events - календарь, предстоящие события</li>
     *   <li>📆 /today - сегодняшний день</li>
     *   <li>🗓️ /week - неделя</li>
     *   <li>🔍 /search - поиск</li>
     *   <li>🔎 /filter - фильтрация</li>
     *   <li>📊 /stats - статистика, аналитика</li>
     *   <li>🗑️ /trash - корзина, удаленные элементы</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.4, 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 6.5</p>
     * 
     * @see #getCommandEmoji(String)
     */
    private static final Map<String, String> COMMAND_EMOJIS = Map.ofEntries(
            Map.entry("/start", "🚀"),
            Map.entry("/help", "📚"),
            Map.entry("/add_event", "➕"),
            Map.entry("/my_events", "📋"),
            Map.entry("/upcoming_events", "📅"),
            Map.entry("/today", "📆"),
            Map.entry("/week", "🗓️"),
            Map.entry("/search", "🔍"),
            Map.entry("/filter", "🔎"),
            Map.entry("/stats", "📊"),
            Map.entry("/trash", "🗑️")
    );

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

        log.debug("Обработка команды /help: telegramId={}, isAuthorized={}", 
                telegramId, isAuthorized);

        // Формируем список команд с учетом статуса авторизации
        String commandsList = buildCommandsList(isAuthorized);

        log.debug("Сформирован список команд для пользователя: telegramId={}, commandsCount={}, isAuthorized={}", 
                telegramId, commandHandlers != null ? commandHandlers.size() : 0, isAuthorized);

        return buildHelpMessage(commandsList, isAuthorized);
    }

    /**
     * Формирует список всех доступных команд с описаниями, сгруппированных по категориям.
     * 
     * <p>Команды группируются по функциональным категориям для улучшения читаемости.
     * Каждая категория имеет заголовок с эмодзи, после которого следуют команды этой категории.</p>
     * 
     * <p>Каждая команда форматируется в виде:</p>
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
     * <p><b>Обработка ошибок:</b></p>
     * <ul>
     *   <li>Проверка на пустой список команд с логированием предупреждения</li>
     *   <li>Фильтрация null значений в списке обработчиков</li>
     *   <li>Логирование предупреждений для команд без категории</li>
     *   <li>Использование fallback категории для неизвестных команд</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5, 6.1, 6.2, 6.3, 6.4, 6.5</p>
     * 
     * @param isAuthorized статус авторизации пользователя
     * @return отформатированный список команд, сгруппированных по категориям, или сообщение об отсутствии команд
     */
    private String buildCommandsList(boolean isAuthorized) {
        // Проверка на пустой список команд
        if (commandHandlers == null || commandHandlers.isEmpty()) {
            log.warn("Список обработчиков команд пуст или null. Возвращается сообщение об отсутствии команд.");
            return escape("В данный момент команды недоступны. Пожалуйста, попробуйте позже.");
        }

        // Фильтруем и группируем команды по категориям
        List<CommandHandler> filteredHandlers = commandHandlers.stream()
                .filter(handler -> {
                    if (handler == null) {
                        log.warn("Обнаружен null обработчик команды в списке. Пропускаем.");
                        return false;
                    }
                    return true;
                })
                .filter(handler -> {
                    String command = handler.getCommand();
                    if (command == null) {
                        log.warn("Обработчик команды {} имеет null команду. Пропускаем.", 
                                handler.getClass().getSimpleName());
                        return false;
                    }
                    return !"/start".equals(command); // Исключаем команду /start из списка
                })
                .toList();

        // Проверка на пустой список после фильтрации
        if (filteredHandlers.isEmpty()) {
            log.warn("После фильтрации не осталось команд для отображения");
            return escape("В данный момент команды недоступны. Пожалуйста, попробуйте позже.");
        }

        Map<CommandCategory, List<CommandHandler>> groupedCommands = groupCommandsByCategory(filteredHandlers);

        // Формируем вывод по категориям
        StringBuilder result = new StringBuilder();
        
        // Порядок отображения категорий
        CommandCategory[] categoryOrder = {
                CommandCategory.VIEW_EVENTS,
                CommandCategory.MANAGE_EVENTS,
                CommandCategory.SEARCH_FILTER,
                CommandCategory.STATS_TRASH,
                CommandCategory.HELP
        };

        for (CommandCategory category : categoryOrder) {
            List<CommandHandler> handlers = groupedCommands.get(category);
            if (handlers == null || handlers.isEmpty()) {
                continue;
            }

            // Добавляем заголовок категории
            if (result.length() > 0) {
                result.append("\n");
            }
            result.append(bold(getCategoryName(category))).append("\n");

            // Добавляем команды категории, отсортированные по алфавиту
            String categoryCommands = handlers.stream()
                    .sorted(Comparator.comparing(CommandHandler::getCommand))
                    .map(handler -> {
                        try {
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
                            String command = handler.getCommand();
                            String description = handler.getDescription();
                            
                            // Проверка на null значения
                            if (command == null) {
                                log.warn("Обработчик {} имеет null команду. Пропускаем.", 
                                        handler.getClass().getSimpleName());
                                return null;
                            }
                            
                            if (description == null) {
                                log.warn("Команда {} имеет null описание. Используется fallback описание.", command);
                                description = "Описание недоступно";
                            }
                            
                            return emoji + escape(command) + " " + escape("-") + " " + escape(description);
                        } catch (Exception e) {
                            log.error("Ошибка при форматировании команды {}: {}", 
                                    handler.getCommand(), e.getMessage(), e);
                            return null;
                        }
                    })
                    .filter(cmd -> cmd != null) // Фильтруем null значения после обработки ошибок
                    .collect(Collectors.joining("\n"));

            result.append(categoryCommands).append("\n");
        }

        return result.toString().trim();
    }

    /**
     * Группирует обработчики команд по категориям.
     * 
     * <p>Метод использует маппинг COMMAND_CATEGORIES для определения категории каждой команды.
     * Если команда не найдена в маппинге, она помещается в категорию HELP по умолчанию,
     * и логируется предупреждение для мониторинга.</p>
     * 
     * <p><b>Обработка ошибок:</b></p>
     * <ul>
     *   <li>Логирование предупреждений для команд без категории</li>
     *   <li>Использование fallback категории HELP для неизвестных команд</li>
     *   <li>Безопасная обработка null значений</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.2, 2.1, 2.2, 2.3, 2.4, 2.5, 6.1, 6.2, 6.3, 6.4, 6.5</p>
     * 
     * @param handlers список обработчиков команд для группировки
     * @return Map, где ключ - категория, значение - список обработчиков этой категории
     */
    private Map<CommandCategory, List<CommandHandler>> groupCommandsByCategory(List<CommandHandler> handlers) {
        return handlers.stream()
                .collect(Collectors.groupingBy(handler -> {
                    String command = handler.getCommand();
                    CommandCategory category = getCommandCategory(command);
                    
                    // Fallback для неизвестных категорий
                    if (category == null) {
                        log.warn("Команда '{}' не имеет категории в маппинге COMMAND_CATEGORIES. " +
                                "Используется fallback категория HELP. " +
                                "Рекомендуется добавить команду в маппинг категорий.", 
                                command != null ? command : "null");
                        return CommandCategory.HELP;
                    }
                    
                    return category;
                }));
    }

    /**
     * Определяет категорию для указанной команды.
     * 
     * <p>Метод использует маппинг COMMAND_CATEGORIES для поиска категории.
     * Если команда не найдена в маппинге, возвращается null.</p>
     * 
     * <p><b>Обработка ошибок:</b></p>
     * <ul>
     *   <li>Безопасная обработка null значений команды</li>
     *   <li>Возврат null для неизвестных команд (fallback обрабатывается в вызывающем методе)</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.2, 2.1, 2.2, 2.3, 2.4, 2.5, 6.1, 6.2</p>
     * 
     * @param command имя команды (например, "/add_event")
     * @return категория команды или null, если команда не найдена в маппинге или равна null
     */
    private CommandCategory getCommandCategory(String command) {
        if (command == null) {
            log.warn("Попытка получить категорию для null команды");
            return null;
        }
        return COMMAND_CATEGORIES.get(command);
    }

    /**
     * Возвращает отображаемое имя категории с эмодзи.
     * 
     * <p>Метод используется для формирования заголовков категорий в списке команд.
     * Каждая категория имеет уникальное отображаемое имя с соответствующим эмодзи.</p>
     * 
     * <p><b>Обработка ошибок:</b></p>
     * <ul>
     *   <li>Fallback для null категории - возвращается "Другие команды"</li>
     *   <li>Безопасная обработка неизвестных категорий</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.2, 2.1, 2.2, 2.3, 2.4, 2.5, 6.3, 6.4</p>
     * 
     * @param category категория команд
     * @return отображаемое имя категории с эмодзи, или "Другие команды" для null категории
     */
    private String getCategoryName(CommandCategory category) {
        if (category == null) {
            log.warn("Попытка получить имя для null категории. Используется fallback 'Другие команды'");
            return "Другие команды";
        }
        return category.getDisplayName();
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
        
        log.debug("Сформировано сообщение справки: длина={} символов, isAuthorized={}", 
                result.length(), isAuthorized);
        
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
     * <p><b>Обработка ошибок:</b></p>
     * <ul>
     *   <li>Безопасная обработка null значений команды</li>
     *   <li>Возврат пустой строки для неизвестных команд (fallback)</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 3.1, 3.2, 3.3, 3.4, 3.5, 6.5</p>
     * 
     * @param command имя команды (например, "/add_event")
     * @return эмодзи, соответствующий команде, или пустая строка для неизвестных команд или null
     */
    private String getCommandEmoji(String command) {
        if (command == null) {
            log.debug("Попытка получить эмодзи для null команды. Возвращается пустая строка.");
            return "";
        }
        
        return COMMAND_EMOJIS.getOrDefault(command, "");
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
