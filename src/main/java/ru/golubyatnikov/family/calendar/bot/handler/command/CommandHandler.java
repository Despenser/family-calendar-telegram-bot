package ru.golubyatnikov.family.calendar.bot.handler.command;

import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;

/**
 * Интерфейс для обработчиков команд Telegram бота.
 *
 * @author Golubyatnikov Aleksey
 * @since 2025-12-30
 */
public interface CommandHandler {

    /**
     * Обрабатывает команду от пользователя и возвращает текст ответа.
     *
     * @param message входящее сообщение от Telegram, содержащее команду и параметры.
     * @param user пользователь из базы данных, отправивший команду.
     *
     * @return текст ответа, который будет отправлен пользователю.
     * @throws IllegalArgumentException если message равен null
     * @throws ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException
     *         если команда требует авторизации, но user равен null
     * @throws ru.golubyatnikov.family.calendar.bot.exception.InvalidDateException
     *         если команда работает с датами, и получены некорректные данные
     */
    String handle(Message message, User user);

    /**
     * Возвращает команду, которую обрабатывает этот handler.
     *
     * @return строка с командой, начинающаяся с "/". Не может быть null или пустой.
     */
    String getCommand();

    /**
     * Возвращает описание команды для отображения в справке.
     *
     * @return строка с описанием команды. Не может быть null или пустой.
     */
    String getDescription();

    /**
     * Определяет, требуется ли авторизация для выполнения этой команды.
     *
     * @return true, если команда требует авторизации (пользователь должен быть
     *         зарегистрирован в системе), false в противном случае.
     */
    default boolean requiresAuth() {
        return true;
    }
}
