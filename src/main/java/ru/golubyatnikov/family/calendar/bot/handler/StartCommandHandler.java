package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.UserService;

/**
 * Обработчик команды /start для Telegram бота семейного календаря.
 * 
 * <p>Команда /start является точкой входа для пользователей бота.
 * Она выполняет следующие функции:</p>
 * <ul>
 *   <li>Проверяет, зарегистрирован ли пользователь в системе</li>
 *   <li>Отправляет приветственное сообщение зарегистрированным пользователям</li>
 *   <li>Информирует незарегистрированных пользователей о необходимости регистрации</li>
 *   <li>Предоставляет список доступных команд</li>
 * </ul>
 * 
 * <p>Команда /start не требует авторизации и доступна всем пользователям,
 * включая тех, кто еще не зарегистрирован в системе.</p>
 * 
 * <p><b>Требования:</b> 3.1, 3.2, 3.3, 3.5</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>
 * Пользователь отправляет: /start
 * 
 * Если пользователь зарегистрирован:
 * Бот отвечает: "Добро пожаловать в Семейный Календарь Бот! 👋
 *                Вы уже зарегистрированы в системе.
 *                
 *                Доступные команды:
 *                /help - Показать список всех команд
 *                /add_event - Добавить новое событие
 *                /upcoming_events - Показать предстоящие события
 *                /my_events - Управление моими событиями"
 * 
 * Если пользователь не зарегистрирован:
 * Бот отвечает: "Добро пожаловать в Семейный Календарь Бот! 👋
 *                
 *                К сожалению, вы еще не зарегистрированы в системе.
 *                Для получения доступа к боту обратитесь к администратору семьи.
 *                
 *                После регистрации вы сможете:
 *                ✅ Создавать события в семейном календаре
 *                ✅ Просматривать предстоящие события
 *                ✅ Получать уведомления о важных событиях
 *                ✅ Управлять своими событиями"
 * </pre>
 * 
 * @see CommandHandler
 * @see UserService
 * @see User
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Component
@Slf4j
public class StartCommandHandler implements CommandHandler {

    private final UserService userService;

    /**
     * Конструктор для внедрения зависимостей.
     * 
     * @param userService сервис для работы с пользователями
     */
    public StartCommandHandler(UserService userService) {
        this.userService = userService;
    }

    /**
     * Обрабатывает команду /start от пользователя.
     * 
     * <p>Метод проверяет наличие пользователя в базе данных по Telegram ID
     * и возвращает соответствующее приветственное сообщение.</p>
     * 
     * <p>Для зарегистрированных пользователей отправляется приветствие
     * со списком доступных команд.</p>
     * 
     * <p>Для незарегистрированных пользователей отправляется сообщение
     * о необходимости регистрации через администратора.</p>
     * 
     * @param message входящее сообщение от Telegram, содержащее команду /start
     * @param user пользователь из базы данных (может быть null, так как команда
     *             не требует авторизации)
     * @return текст приветственного сообщения с инструкциями
     * @throws IllegalArgumentException если message равен null
     */
    @Override
    public String handle(Message message, User user) {
        if (message == null) {
            log.error("Получено null сообщение в StartCommandHandler");
            throw new IllegalArgumentException("Сообщение не может быть null");
        }

        Long telegramId = message.getFrom().getId();
        String username = message.getFrom().getUserName();
        String firstName = message.getFrom().getFirstName();

        log.info("Обработка команды /start: telegramId={}, username={}, firstName={}", 
                telegramId, username, firstName);

        // Проверяем наличие пользователя в БД
        boolean isRegistered = userService.isUserAuthorized(telegramId);

        if (isRegistered) {
            log.info("Пользователь зарегистрирован: telegramId={}, username={}", 
                    telegramId, username);
            return buildWelcomeMessageForRegisteredUser(firstName);
        } else {
            log.info("Пользователь не зарегистрирован: telegramId={}, username={}", 
                    telegramId, username);
            return buildWelcomeMessageForUnregisteredUser(firstName);
        }
    }

    /**
     * Формирует приветственное сообщение для зарегистрированного пользователя.
     * 
     * <p>Сообщение включает:</p>
     * <ul>
     *   <li>Персонализированное приветствие</li>
     *   <li>Подтверждение регистрации</li>
     *   <li>Список основных доступных команд</li>
     * </ul>
     * 
     * @param firstName имя пользователя для персонализации
     * @return отформатированное приветственное сообщение
     */
    private String buildWelcomeMessageForRegisteredUser(String firstName) {
        String greeting = firstName != null && !firstName.isBlank() 
                ? String.format("Добро пожаловать, %s! 👋", firstName)
                : "Добро пожаловать! 👋";

        return String.format(
                "%s\n\n" +
                "Вы уже зарегистрированы в *Семейном Календаре*.\n\n" +
                "*Доступные команды:*\n" +
                "/help - Показать список всех команд\n" +
                "/add_event - Добавить новое событие\n" +
                "/upcoming_events - Показать предстоящие события\n" +
                "/my_events - Управление моими событиями\n\n" +
                "Используйте /help для получения подробной информации о каждой команде.",
                greeting
        );
    }

    /**
     * Формирует приветственное сообщение для незарегистрированного пользователя.
     * 
     * <p>Сообщение включает:</p>
     * <ul>
     *   <li>Персонализированное приветствие</li>
     *   <li>Информацию о необходимости регистрации</li>
     *   <li>Инструкции по получению доступа</li>
     *   <li>Список возможностей после регистрации</li>
     * </ul>
     * 
     * @param firstName имя пользователя для персонализации
     * @return отформатированное сообщение о необходимости регистрации
     */
    private String buildWelcomeMessageForUnregisteredUser(String firstName) {
        String greeting = firstName != null && !firstName.isBlank() 
                ? String.format("Добро пожаловать, %s! 👋", firstName)
                : "Добро пожаловать! 👋";

        return String.format(
                "%s\n\n" +
                "К сожалению, вы еще не зарегистрированы в *Семейном Календаре*.\n\n" +
                "Для получения доступа к боту обратитесь к администратору вашей семьи. " +
                "После регистрации вы сможете пользоваться всеми возможностями бота.\n\n" +
                "*После регистрации вы сможете:*\n" +
                "✅ Создавать события в семейном календаре\n" +
                "✅ Просматривать предстоящие события семьи\n" +
                "✅ Получать уведомления о важных событиях\n" +
                "✅ Управлять своими событиями\n\n" +
                "Если у вас есть вопросы, обратитесь к администратору семьи.",
                greeting
        );
    }

    /**
     * Возвращает команду, которую обрабатывает этот handler.
     * 
     * @return строка "/start"
     */
    @Override
    public String getCommand() {
        return "/start";
    }

    /**
     * Возвращает описание команды для отображения в справке.
     * 
     * @return описание команды
     */
    @Override
    public String getDescription() {
        return "Начать работу с ботом";
    }

    /**
     * Определяет, требуется ли авторизация для выполнения этой команды.
     * 
     * <p>Команда /start не требует авторизации, так как она используется
     * для первого контакта с ботом и проверки статуса регистрации.</p>
     * 
     * @return false, так как команда доступна всем пользователям
     */
    @Override
    public boolean requiresAuth() {
        return false;
    }
}
