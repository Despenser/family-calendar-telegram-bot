package ru.golubyatnikov.family.calendar.bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.handler.CommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape;

/**
 * Сервис для маршрутизации команд к соответствующим обработчикам.
 * 
 * <p>CommandDispatcher является центральным компонентом обработки команд бота.
 * Он отвечает за:</p>
 * <ul>
 *   <li>Регистрацию всех доступных обработчиков команд</li>
 *   <li>Маршрутизацию входящих команд к соответствующим обработчикам</li>
 *   <li>Проверку авторизации пользователей перед выполнением команд</li>
 *   <li>Логирование процесса маршрутизации для отладки и мониторинга</li>
 * </ul>
 * 
 * <p>Использует паттерн Command Pattern для делегирования обработки команд
 * специализированным обработчикам.</p>
 * 
 * <p><b>Архитектурный паттерн:</b> Dispatcher + Command Pattern</p>
 * <p><b>Требования:</b> 1.2</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>{@code
 * @Service
 * public class UpdateProcessor {
 *     private final CommandDispatcher dispatcher;
 *     
 *     public void processUpdate(Update update) {
 *         Message message = update.getMessage();
 *         String response = dispatcher.dispatch(message);
 *         // отправить response пользователю
 *     }
 * }
 * }</pre>
 * 
 * @see CommandHandler
 * @see UserService
 * @author Family Calendar Bot Team
 * @version 1.0.0
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
     * <p>Spring автоматически внедряет все бины, реализующие интерфейс {@link CommandHandler},
     * и создает Map для быстрого поиска обработчика по команде.</p>
     * 
     * <p>Если несколько обработчиков зарегистрированы для одной команды,
     * будет использован последний обработчик из списка, и будет залогировано предупреждение.</p>
     * 
     * @param handlers список всех доступных обработчиков команд, автоматически внедряемых Spring
     * @param userService сервис для работы с пользователями и проверки авторизации
     * @throws IllegalArgumentException если список обработчиков пуст
     */
    public CommandDispatcher(List<CommandHandler> handlers, UserService userService) {
        this.userService = userService;
        this.commandHandlers = new HashMap<>();
        
        if (handlers == null || handlers.isEmpty()) {
            log.warn("Не найдено ни одного обработчика команд. Бот не сможет обрабатывать команды.");
        } else {
            log.info("Регистрация обработчиков команд. Всего обработчиков: {}", handlers.size());
            
            for (CommandHandler handler : handlers) {
                String command = handler.getCommand();
                
                if (commandHandlers.containsKey(command)) {
                    log.warn("Обнаружен дубликат обработчика для команды '{}'. " +
                            "Предыдущий обработчик будет заменен: {} -> {}", 
                            command, 
                            commandHandlers.get(command).getClass().getSimpleName(),
                            handler.getClass().getSimpleName());
                }
                
                commandHandlers.put(command, handler);
                log.debug("Зарегистрирован обработчик: команда='{}', handler={}, requiresAuth={}, description='{}'",
                        command, 
                        handler.getClass().getSimpleName(),
                        handler.requiresAuth(),
                        handler.getDescription());
            }
            
            log.info("Регистрация обработчиков завершена. Зарегистрировано команд: {}", 
                    commandHandlers.size());
        }
    }

    /**
     * Маршрутизирует входящее сообщение к соответствующему обработчику команды.
     * 
     * <p>Процесс маршрутизации:</p>
     * <ol>
     *   <li>Извлечение команды из текста сообщения</li>
     *   <li>Поиск соответствующего обработчика</li>
     *   <li>Проверка требования авторизации</li>
     *   <li>Получение пользователя из БД (если требуется)</li>
     *   <li>Делегирование обработки найденному обработчику</li>
     * </ol>
     * 
     * <p>Если команда не найдена, возвращается сообщение с предложением использовать /help.</p>
     * 
     * <p>Если команда требует авторизации, но пользователь не найден в БД,
     * выбрасывается {@link UnauthorizedAccessException}.</p>
     * 
     * @param message входящее сообщение от Telegram, содержащее команду
     * @return текст ответа, который должен быть отправлен пользователю
     * @throws IllegalArgumentException если message равен null или не содержит текста
     * @throws UnauthorizedAccessException если команда требует авторизации, 
     *         но пользователь не зарегистрирован в системе
     * @see CommandHandler#handle(Message, User)
     * @see CommandHandler#requiresAuth()
     */
    public String dispatch(Message message) {
        if (message == null) {
            log.error("Получено null сообщение для маршрутизации");
            throw new IllegalArgumentException("Сообщение не может быть null");
        }
        
        if (!message.hasText()) {
            log.warn("Получено сообщение без текста от пользователя: telegramId={}, chatId={}", 
                    message.getFrom().getId(), message.getChatId());
            return "Пожалуйста, отправьте текстовую команду. Используйте " + escape("/help") + " для списка доступных команд.";
        }
        
        String messageText = message.getText().trim();
        Long telegramId = message.getFrom().getId();
        
        log.info("Начало маршрутизации команды: text='{}', telegramId={}, chatId={}, messageId={}", 
                messageText, telegramId, message.getChatId(), message.getMessageId());
        
        // Извлекаем команду (первое слово, начинающееся с /)
        String command = extractCommand(messageText);
        
        if (command == null) {
            log.warn("Не удалось извлечь команду из текста: '{}', telegramId={}", 
                    messageText, telegramId);
            return "Команда должна начинаться с символа '/'. Используйте " + escape("/help") + " для списка доступных команд.";
        }
        
        log.debug("Извлечена команда: '{}' из текста: '{}'", command, messageText);
        
        // Ищем обработчик для команды
        CommandHandler handler = commandHandlers.get(command);
        
        if (handler == null) {
            log.warn("Обработчик не найден для команды: '{}', telegramId={}", command, telegramId);
            return String.format("Неизвестная команда: %s\n\nИспользуйте %s для списка доступных команд.", command, escape("/help"));
        }
        
        log.debug("Найден обработчик для команды '{}': {}, requiresAuth={}", 
                command, handler.getClass().getSimpleName(), handler.requiresAuth());
        
        // Загружаем пользователя из БД (всегда, независимо от требования авторизации)
        log.debug("Загрузка пользователя из БД: telegramId={}", telegramId);
        Optional<User> userOptional = userService.findByTelegramId(telegramId);
        User user = userOptional.orElse(null);
        
        // Проверяем требование авторизации
        if (handler.requiresAuth()) {
            log.debug("Команда '{}' требует авторизации. Проверка пользователя: telegramId={}", 
                    command, telegramId);
            
            if (user == null) {
                log.warn("Неавторизованная попытка выполнить команду '{}': telegramId={}", 
                        command, telegramId);
                throw new UnauthorizedAccessException(
                        String.format("Команда %s требует авторизации. " +
                                "Пожалуйста, используйте %s для регистрации.", command, escape("/start")));
            }
            
            log.info("Пользователь авторизован: telegramId={}, userId={}, username={}, familyId={}", 
                    telegramId, user.getId(), user.getUsername(), 
                    user.getFamily() != null ? user.getFamily().getId() : null);
        } else {
            if (user != null) {
                log.debug("Команда '{}' не требует авторизации, но пользователь найден: telegramId={}, userId={}", 
                        command, telegramId, user.getId());
            } else {
                log.debug("Команда '{}' не требует авторизации. Выполнение без пользователя.", command);
            }
        }
        
        // Делегируем обработку команды
        try {
            log.info("Делегирование обработки команды '{}' обработчику: {}, telegramId={}", 
                    command, handler.getClass().getSimpleName(), telegramId);
            
            String response = handler.handle(message, user);
            
            log.info("Команда '{}' успешно обработана: telegramId={}, responseLength={}", 
                    command, telegramId, response != null ? response.length() : 0);
            
            return response;
            
        } catch (Exception e) {
            log.error("Ошибка при обработке команды '{}': telegramId={}, handler={}, error={}", 
                    command, telegramId, handler.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Извлекает команду из текста сообщения.
     * 
     * <p>Команда - это первое слово в сообщении, начинающееся с символа '/'.</p>
     * <p>Примеры:</p>
     * <ul>
     *   <li>"/start" → "/start"</li>
     *   <li>"/add_event Встреча" → "/add_event"</li>
     *   <li>"/help " → "/help"</li>
     *   <li>"Привет" → null</li>
     * </ul>
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
     * Возвращает количество зарегистрированных обработчиков команд.
     * 
     * <p>Используется для диагностики и тестирования.</p>
     * 
     * @return количество зарегистрированных обработчиков
     */
    public int getRegisteredHandlersCount() {
        return commandHandlers.size();
    }

    /**
     * Проверяет, зарегистрирован ли обработчик для указанной команды.
     * 
     * <p>Используется для диагностики и тестирования.</p>
     * 
     * @param command команда для проверки (должна начинаться с '/')
     * @return true, если обработчик зарегистрирован, иначе false
     */
    public boolean hasHandler(String command) {
        return commandHandlers.containsKey(command);
    }
}
