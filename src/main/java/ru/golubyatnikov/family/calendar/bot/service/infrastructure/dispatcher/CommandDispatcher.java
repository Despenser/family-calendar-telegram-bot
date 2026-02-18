package ru.golubyatnikov.family.calendar.bot.service.infrastructure.dispatcher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.handler.command.CommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.user.UserService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.formatMessage;

/**
 * TODO возможно требуется рефакторинг, сложные методы
 * Сервис для маршрутизации команд к соответствующим обработчикам.
 *
 * @author Golubyatnikov Aleksey
 * @since 2025-12-30
 */
@Service
@Slf4j
public class CommandDispatcher {

    private final Map<String, CommandHandler> commandHandlers;
    private final UserService userService;

    /**
     * Конструктор для внедрения зависимостей.
     *
     * @param handlers список всех доступных обработчиков команд, автоматически внедряемых Spring
     * @param userService сервис для работы с пользователями и проверки авторизации
     *
     * @throws IllegalArgumentException если список обработчиков пуст
     */
    public CommandDispatcher(List<CommandHandler> handlers, UserService userService) {
        this.userService = userService;
        this.commandHandlers = new HashMap<>();
        
        if (handlers == null || handlers.isEmpty()) {
        } else {
            handlers.forEach(handler -> {
                String command = handler.getCommand();
                if (commandHandlers.containsKey(command)) {
                    log.warn("Обнаружен дубликат обработчика для команды '{}'. " + "Предыдущий обработчик будет заменен: {} -> {}",
                            command,
                            commandHandlers.get(command).getClass().getSimpleName(),
                            handler.getClass().getSimpleName());
                }
                commandHandlers.put(command, handler);
                });
            
            }
    }

    /**
     * Маршрутизирует входящее сообщение к соответствующему обработчику команды.
     *
     * @param message входящее сообщение от Telegram, содержащее команду
     *
     * @return текст ответа, который должен быть отправлен пользователю
     * @throws IllegalArgumentException если message равен null или не содержит текста
     * @throws UnauthorizedAccessException если команда требует авторизации, 
     *         но пользователь не зарегистрирован в системе
     */
    public String dispatch(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Сообщение не может быть null");
        }
        
        if (!message.hasText()) {
            log.warn("Получено сообщение без текста от пользователя: telegramId={}, chatId={}", 
                    message.getFrom().getId(), message.getChatId());

            return formatMessage("Пожалуйста, отправьте текстовую команду. " +
                    "Используйте 📚 /help для списка доступных команд.");
        }
        
        String messageText = message.getText().trim();
        Long telegramId = message.getFrom().getId();
        
        // Извлекаем команду (первое слово, начинающееся с /)
        String command = extractCommand(messageText);
        
        if (command == null) {
            return formatMessage("Команда должна начинаться с символа '/'. " +
                    "Используйте 📚 /help для списка доступных команд.");
        }
        
        // Ищем обработчик для команды
        CommandHandler handler = commandHandlers.get(command);
        
        if (handler == null) {
            return formatMessage("""
                    Неизвестная команда: %s
                    
                    Используйте 📚 /help для списка доступных команд.""", command);
        }
        
        // Загружаем пользователя из БД (всегда, независимо от требования авторизации)
        Optional<User> userOptional = userService.findByTelegramId(telegramId);
        User user = userOptional.orElse(null);
        
        // Проверяем требование авторизации
        if (handler.requiresAuth()) {
            if (user == null) {
                log.warn("Неавторизованная попытка выполнить команду '{}': telegramId={}", 
                        command, telegramId);
                throw new UnauthorizedAccessException(
                        formatMessage("Команда %s требует авторизации. " +
                                "Пожалуйста, используйте 🚀 /start для регистрации.", command));
            }
            
            } else {
            if (user != null) {
                } else {
                }
        }
        
        // Делегируем обработку команды
        try {
            String response = handler.handle(message, user);
            
            return response;
            
        } catch (Exception e) {
            log.error("Ошибка при обработке команды '{}': telegramId={}, handler={}, error={}", 
                    command, telegramId, handler.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * //TODO код дублируется в UpdateProcessor
     * Извлекает команду из текста сообщения.
     *
     * @param text текст сообщения
     * @return команда (включая символ '/') или null, если команда не найдена
     */
    private String extractCommand(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        String trimmed = text.trim();
        if (!trimmed.startsWith("/")) {
            return null;
        }
        
        // Находим первый пробел или берем всю строку
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            return trimmed.substring(0, spaceIndex).toLowerCase();
        }
        
        return trimmed.toLowerCase();
    }
    /**
     * Проверяет, зарегистрирован ли обработчик для указанной команды.
     *
     * @param command команда для проверки (должна начинаться с '/')
     * @return true, если обработчик зарегистрирован, иначе false
     */
    public boolean hasHandler(String command) {
        return commandHandlers.containsKey(command);
    }
}
