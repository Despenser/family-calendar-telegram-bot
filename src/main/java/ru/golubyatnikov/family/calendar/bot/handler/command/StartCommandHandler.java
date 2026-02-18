package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.user.UserService;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape;

/**
 * Обработчик команды /start для Telegram бота семейного календаря.
 *
 * @author Golubyatnikov Aleksey
 * @since 2025-12-30
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StartCommandHandler implements CommandHandler {

    private final UserService userService;

    /**
     * Обрабатывает команду /start от пользователя.
     *
     * @param message входящее сообщение от Telegram, содержащее команду /start
     * @param user пользователь из базы данных (может быть null, так как команда не требует авторизации)
     *
     * @return текст приветственного сообщения с инструкциями
     * @throws IllegalArgumentException если message равен null
     */
    @Override
    public String handle(Message message, User user) {
        if (message == null) {
            throw new IllegalArgumentException("Сообщение не может быть null");
        }

        Long telegramId = message.getFrom().getId();
        String firstName = message.getFrom().getFirstName();

        // Проверяем наличие пользователя в БД
        boolean isRegistered = userService.isUserAuthorized(telegramId);

        if (isRegistered) {
            return buildWelcomeMessageForRegisteredUser(firstName);

        } else {
            return buildWelcomeMessageForUnregisteredUser(firstName);
        }
    }

    /**
     * Формирует приветственное сообщение для зарегистрированного пользователя.
     *
     * @param firstName имя пользователя для персонализации
     * @return отформатированное приветственное сообщение с кликабельными командами
     */
    private @NonNull String buildWelcomeMessageForRegisteredUser(String firstName) {

        String greetingText = firstName != null && !firstName.isBlank() 
                ? escape("Добро пожаловать, " + firstName + "! 👋")
                : escape("Добро пожаловать! 👋");

        return greetingText + "\n\n" +
                bold("Семейный Календарь") + escape(" - это ваш персональный бот-помощник для управления семейными событиями и планами.") + "\n\n" +
                escape("Вы уже зарегистрированы в системе и можете пользоваться всеми возможностями бота:") + "\n\n" +
                bold("Основной функционал:") + "\n" +
                escape("📅 Создание и управление событиями") + "\n" +
                escape("🔔 Получение напоминаний о важных датах") + "\n" +
                escape("👥 Совместное планирование с семьей") + "\n" +
                escape("📊 Просмотр статистики и аналитики") + "\n" +
                escape("🔍 Поиск и фильтрация событий") + "\n\n" +
                escape("Используйте 📚 /help для получения подробной информации о доступных командах.");
    }

    /**
     * Формирует приветственное сообщение для незарегистрированного пользователя.
     * 
     * @param firstName имя пользователя для персонализации
     * @return отформатированное сообщение о необходимости регистрации
     */
    private @NonNull String buildWelcomeMessageForUnregisteredUser(String firstName) {

        String greetingText = firstName != null && !firstName.isBlank() 
                ? escape("Добро пожаловать, " + firstName + "! 👋")
                : escape("Добро пожаловать! 👋");

        return greetingText + "\n\n" +
                bold("Семейный Календарь") + escape(" - это ваш персональный бот-помощник для управления семейными событиями и планами.") + "\n\n" +
                escape("С помощью этого бота вы сможете:") + "\n" +
                escape("📅 Создавать и управлять событиями") + "\n" +
                escape("🔔 Получать напоминания о важных датах") + "\n" +
                escape("👥 Совместно планировать с семьей") + "\n" +
                escape("🔍 Искать и фильтровать события по типу") + "\n" +
                escape("📊 Просматривать статистику и аналитику") + "\n\n" +
                escape("🔒 ") + bold("Для доступа к функционалу требуется регистрация") + "\n\n" +
                escape("К сожалению, вы еще не зарегистрированы в системе. " +
                "Для получения доступа к боту обратитесь к администратору вашей семьи.") + "\n\n" +
                bold("Как получить доступ:") + "\n" +
                escape("1️⃣ Свяжитесь с администратором вашей семьи") + "\n" +
                escape("2️⃣ Сообщите ему ваш Telegram ID или username") + "\n" +
                escape("3️⃣ Дождитесь подтверждения регистрации") + "\n" +
                escape("4️⃣ После регистрации отправьте /start снова") + "\n\n" +
                escape("После регистрации вам станут доступны все возможности бота для управления семейным календарем!");
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
     * @return false, так как команда доступна всем пользователям
     */
    @Override
    public boolean requiresAuth() {
        return false;
    }
}
