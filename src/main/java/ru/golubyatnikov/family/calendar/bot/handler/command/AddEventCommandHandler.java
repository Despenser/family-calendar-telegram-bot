package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /add_event для создания новых событий в семейном календаре.
 * 
 * <p>Команда /add_event реализует многошаговый диалог с использованием inline-кнопок:</p>
 * <ol>
 *   <li>Выбор даты через inline-календарь с навигацией по месяцам</li>
 *   <li>Выбор времени через inline-кнопки (час и минуты)</li>
 *   <li>Выбор типа события (персональное или семейное)</li>
 *   <li>Ввод названия события через текстовое сообщение</li>
 *   <li>Ввод описания события (опционально)</li>
 *   <li>Создание события в базе данных</li>
 * </ol>
 * 
 * <p>Состояние диалога хранится в базе данных через ConversationService в виде
 * черновиков событий со статусом DRAFT. Это обеспечивает персистентность состояния
 * и позволяет пользователю продолжить создание события после перезапуска бота.</p>
 * 
 * <p>Команда требует авторизации - пользователь должен быть зарегистрирован
 * в системе и принадлежать семье.</p>
 * 
 * <p><b>Требования:</b> 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 15.1, 15.2, 15.3, 15.4, 15.5, 15.6</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>
 * Пользователь: /add_event
 * Бот: [Показывает inline-календарь]
 * 
 * Пользователь: [Выбирает дату 31.12.2025]
 * Бот: [Показывает выбор часа]
 * 
 * Пользователь: [Выбирает 18:00]
 * Бот: [Показывает выбор типа события: Семейное/Персональное]
 * 
 * Пользователь: [Выбирает "Семейное"]
 * Бот: Теперь отправьте название события:
 * 
 * Пользователь: Новогодний ужин
 * Бот: Теперь отправьте описание или напишите 'пропустить':
 * 
 * Пользователь: пропустить
 * Бот: ✅ Событие успешно создано!
 *      
 *      📅 Дата: 31.12.2025
 *      🕐 Время: 18:00
 *      📝 Название: Новогодний ужин
 * </pre>
 * 
 * @see CommandHandler
 * @see ConversationService
 * @see KeyboardService
 * @author Family Calendar Bot Team
 * @version 2.0.0
 * @since 2025-12-30
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AddEventCommandHandler implements CommandHandler {

    private final ConversationService conversationService;
    private final KeyboardService keyboardService;
    private final TelegramMessageService messageService;

    /**
     * Обрабатывает команду /add_event и начинает диалог создания события.
     * 
     * <p>При вызове команды создается черновик события в БД и отображается
     * inline-календарь для выбора даты. Дальнейшая обработка происходит через
     * callback queries в UpdateProcessor.</p>
     * 
     * <p>Метод выполняется в транзакции для обеспечения атомарности операций.
     * При возникновении ошибки транзакция откатывается, и черновик удаляется
     * из базы данных через вызов {@link ConversationService#cancelEventCreation(Long)}.</p>
     * 
     * <p><b>Новое поведение:</b> Метод сохраняет messageId отправленного сообщения
     * в черновике события для последующего обновления этого же сообщения на всех
     * шагах диалога. Это обеспечивает чистоту чата - весь процесс создания события
     * происходит в одном сообщении бота.</p>
     * 
     * @param message входящее сообщение от Telegram
     * @param user пользователь из базы данных (не может быть null, так как команда требует авторизации)
     * @return текст ответа пользователю (null, так как ответ отправляется через TelegramMessageService)
     * @throws IllegalArgumentException если message или user равны null
     */
    @Override
    @Transactional
    public String handle(Message message, User user) {
        if (message == null) {
            log.error("Получено null сообщение в AddEventCommandHandler");
            throw new IllegalArgumentException("Сообщение не может быть null");
        }
        
        if (user == null) {
            log.error("Получен null пользователь в AddEventCommandHandler");
            throw new IllegalArgumentException("Пользователь не может быть null для команды /add_event");
        }
        
        Long telegramId = user.getTelegramId();
        Long chatId = message.getChatId();
        
        log.debug("Начало создания события через inline-календарь: userId={}", user.getId());
        
        // Проверяем, что пользователь принадлежит семье
        if (!user.hasFamily()) {
            log.warn("Пользователь без семьи попытался создать событие: userId={}, telegramId={}", 
                    user.getId(), telegramId);
            return "❌ " + escape("Вы не принадлежите ни одной семье.") + "\n\n" +
                   escape("Для создания событий необходимо быть членом семьи. ") +
                   escape("Обратитесь к администратору для добавления в семью.");
        }
        
        try {
            // Создаем черновик события
            conversationService.startEventCreation(user.getId());
            
            // Показываем календарь для выбора даты
            java.time.LocalDate now = user.getCurrentDate();
            InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(
                now.getYear(), now.getMonthValue(), user);
            
            // Отправляем сообщение и получаем объект Message с messageId
            org.telegram.telegrambots.meta.api.objects.Message sentMessage = 
                messageService.sendMessageWithInlineKeyboardAndGet(chatId, 
                    bold("📋 Создание нового события") + "\n\nВыберите дату события:", 
                    calendar);
            
            // Сохраняем messageId в черновике для последующих обновлений
            conversationService.setCreationMessageId(user.getId(), sentMessage.getMessageId().longValue());
            
            log.debug("Календарь для выбора даты отправлен и messageId сохранен: userId={}, telegramId={}, messageId={}", 
                    user.getId(), telegramId, sentMessage.getMessageId());
            
            // Возвращаем null, так как ответ уже отправлен через TelegramMessageService
            return null;
            
        } catch (Exception e) {
            log.error("Ошибка при начале создания события: userId={}, telegramId={}, errorType={}, errorMessage={}", 
                    user.getId(), telegramId, e.getClass().getSimpleName(), e.getMessage(), e);
            
            // Очищаем черновик при ошибке
            try {
                conversationService.cancelEventCreation(user.getId());
                log.debug("Черновик успешно удален после ошибки: userId={}", user.getId());
            } catch (Exception cleanupError) {
                log.error("Ошибка при очистке черновика после сбоя: userId={}, cleanupError={}", 
                        user.getId(), cleanupError.getMessage(), cleanupError);
            }
            
            return String.format("❌ %s: %s\n\n%s", 
                    bold("Произошла ошибка при создании события"),
                    escape(e.getMessage()),
                    escape("Попробуйте снова, используя команду ") + escape("/add_event"));
        }
    }

    @Override
    public String getCommand() {
        return "/add_event";
    }

    @Override
    public String getDescription() {
        return "Добавить новое событие в календарь";
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }
}
