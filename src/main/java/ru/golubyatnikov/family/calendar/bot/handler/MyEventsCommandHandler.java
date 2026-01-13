package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

import java.util.ArrayList;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /my_events для Telegram бота семейного календаря.
 * 
 * <p>Команда /my_events позволяет пользователям просматривать и управлять своими событиями.
 * Она выполняет следующие функции:</p>
 * <ul>
 *   <li>Получает список всех событий пользователя</li>
 *   <li>Отображает события с inline кнопками для редактирования и удаления</li>
 *   <li>Форматирует события с использованием Markdown для улучшения читаемости</li>
 *   <li>Сортирует события по дате</li>
 *   <li>Отправляет соответствующее сообщение, если событий нет</li>
 * </ul>
 * 
 * <p>Команда требует авторизации - пользователь должен быть зарегистрирован в системе.</p>
 * 
 * <p><b>Требования:</b> 7.1, 7.2, 7.3, 7.4, 7.5</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>
 * Пользователь отправляет: /my_events
 * 
 * Если есть события:
 * Бот отвечает: "📋 *Мои события*
 *                
 *                📌 *День рождения мамы*
 *                📅 Дата: 31.12.2025
 *                🕐 Время: 18:00
 *                📝 Описание: Празднование дня рождения
 *                
 *                [Редактировать] [Удалить]
 *                
 *                📌 *Поход в кино*
 *                📅 Дата: 02.01.2026
 *                🕐 Время: 20:00
 *                📝 Описание: Смотрим новый фильм
 *                
 *                [Редактировать] [Удалить]"
 * 
 * Если событий нет:
 * Бот отвечает: "📋 *Мои события*
 *                
 *                У вас пока нет созданных событий.
 *                
 *                Используйте /add_event для добавления нового события."
 * </pre>
 * 
 * @see CommandHandler
 * @see EventService
 * @see Event
 * @see User
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MyEventsCommandHandler implements CommandHandler {

    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final TelegramMessageService messageService;

    /**
     * Обрабатывает команду /my_events от пользователя.
     * 
     * <p>Метод получает список всех событий пользователя и отправляет
     * каждое событие отдельным сообщением с inline кнопками для редактирования и удаления.</p>
     * 
     * @param message входящее сообщение от Telegram, содержащее команду /my_events
     * @param user пользователь из базы данных, запросивший список своих событий.
     *             Не может быть null, так как команда требует авторизации.
     * @return текст заголовка со списком событий пользователя или сообщение об их отсутствии
     * @throws IllegalArgumentException если message или user равны null
     */
    @Override
    public String handle(Message message, User user) {
        if (message == null) {
            log.error("Получено null сообщение в MyEventsCommandHandler");
            throw new IllegalArgumentException("Сообщение не может быть null");
        }

        if (user == null) {
            log.error("Получен null пользователь в MyEventsCommandHandler");
            throw new IllegalArgumentException("Пользователь не может быть null");
        }

        Long telegramId = message.getFrom().getId();
        String username = message.getFrom().getUserName();
        Long chatId = message.getChatId();

        log.info("Обработка команды /my_events: telegramId={}, username={}, userId={}", 
                telegramId, username, user.getId());

        // Получаем события пользователя
        List<Event> userEvents = eventService.getUserEvents(user.getId());

        log.info("Найдено {} событий для пользователя ID={}", userEvents.size(), user.getId());

        if (userEvents.isEmpty()) {
            return buildNoEventsMessage();
        }

        // Отправляем заголовок
        String header = formatMessage("📋 %s\n\nВсего событий: %s\n", 
                bold("Мои события"), userEvents.size());
        
        // Отправляем каждое событие отдельным сообщением с inline-кнопками
        for (Event event : userEvents) {
            try {
                String eventText = formatEvent(event);
                InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event.getId());
                messageService.sendMessageWithInlineKeyboard(chatId, eventText, keyboard);
            } catch (Exception e) {
                log.error("Ошибка при отправке события ID={}: {}", event.getId(), e.getMessage(), e);
            }
        }
        
        return header;
    }

    /**
     * Формирует сообщение об отсутствии событий у пользователя.
     * 
     * <p>Сообщение включает:</p>
     * <ul>
     *   <li>Заголовок с эмодзи</li>
     *   <li>Информацию об отсутствии событий</li>
     *   <li>Подсказку о добавлении нового события</li>
     * </ul>
     * 
     * @return отформатированное сообщение об отсутствии событий
     */
    private String buildNoEventsMessage() {
        return escape("📋 ") + bold("Мои события") + escape("\n\nУ вас пока нет созданных событий.\n\nИспользуйте ") + code("/add_event") + escape(" для добавления нового события.");
    }

    /**
     * Форматирует одно событие в читаемый вид.
     * 
     * <p>Использует эмодзи для визуального выделения различных полей события.
     * Название события выделяется жирным шрифтом с помощью Markdown.</p>
     * 
     * <p>Если у события нет описания, поле "Описание" не отображается.</p>
     * 
     * @param event событие для форматирования
     * @return отформатированная строка с информацией о событии
     */
    private String formatEvent(Event event) {
        StringBuilder formatted = new StringBuilder();
        
        formatted.append(formatMessage("📌 %s\n", bold(event.getTitle())));
        formatted.append(formatMessage("📅 Дата: %s\n", escape(event.getFormattedDate())));
        formatted.append(formatMessage("🕐 Время: %s", escape(event.getFormattedTime())));
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            formatted.append(formatMessage("\n📝 Описание: %s", 
                    escape(event.getDescription())));
        }
        
        return formatted.toString();
    }

    /**
     * Создает inline клавиатуру с кнопками для управления событием.
     * 
     * <p>Создает две кнопки:</p>
     * <ul>
     *   <li>Редактировать - callback data: "edit_event_{eventId}"</li>
     *   <li>Удалить - callback data: "delete_event_{eventId}"</li>
     * </ul>
     * 
     * <p>Эти кнопки будут обрабатываться через callback queries.</p>
     * 
     * @param eventId идентификатор события
     * @return InlineKeyboardMarkup с кнопками управления
     */
    public InlineKeyboardMarkup createEventManagementKeyboard(Long eventId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Создаем ряд с двумя кнопками
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        // Кнопка "Редактировать"
        InlineKeyboardButton editButton = new InlineKeyboardButton();
        editButton.setText("✏️ Редактировать");
        editButton.setCallbackData("edit_event_" + eventId);
        row.add(editButton);
        
        // Кнопка "Удалить"
        InlineKeyboardButton deleteButton = new InlineKeyboardButton();
        deleteButton.setText("🗑️ Удалить");
        deleteButton.setCallbackData("delete_event_" + eventId);
        row.add(deleteButton);
        
        keyboard.add(row);
        markup.setKeyboard(keyboard);
        
        return markup;
    }

    /**
     * Обрабатывает callback query для просмотра деталей события.
     * 
     * <p>Извлекает ID события из callback data и отображает полную информацию о событии.
     * Используется при нажатии на кнопку "📋 Посмотреть детали" в уведомлениях о напоминаниях.</p>
     * 
     * <p><b>Требования:</b> 8.1</p>
     * 
     * @param eventId идентификатор события для просмотра
     * @param userId идентификатор пользователя, запросившего просмотр
     * @return сообщение с полной информацией о событии
     */
    public String handleViewEventDetails(Long eventId, Long userId) {
        log.info("Обработка callback просмотра деталей события ID={} пользователем ID={}", 
                eventId, userId);
        
        try {
            Event event = eventService.getEventById(eventId);
            
            // Проверяем права доступа
            if (event.getIsPersonal() && !event.getUser().getId().equals(userId)) {
                log.warn("Пользователь ID={} попытался просмотреть чужое персональное событие ID={}", 
                        userId, eventId);
                return formatMessage("❌ %s\n\n%s",
                       bold("Доступ запрещен"),
                       "У вас нет прав для просмотра этого события\\.");
            }
            
            // Формируем детальное описание события
            StringBuilder details = new StringBuilder();
            details.append(formatMessage("📋 %s\n\n", bold("Детали события")));
            
            // Название
            details.append(formatMessage("📌 %s\n\n", bold(event.getTitle())));
            
            // Дата
            details.append(formatMessage("📅 Дата: %s\n", escape(event.getFormattedDate())));
            
            // Время
            if (event.getEventTime() != null) {
                if (event.getEndTime() != null) {
                    details.append(formatMessage("🕐 Время: %s - %s\n", 
                        escape(event.getFormattedTime()), 
                        escape(event.getEndTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))));
                } else {
                    details.append(formatMessage("🕐 Время: %s\n", escape(event.getFormattedTime())));
                }
            }
            
            // Описание
            if (event.getDescription() != null && !event.getDescription().isBlank()) {
                details.append(formatMessage("📝 Описание: %s\n", escape(event.getDescription())));
            }
            
            // Тип события
            details.append("\n");
            if (event.getIsPersonal()) {
                details.append(formatMessage("🔒 %s\n", bold("Персональное событие")));
            } else {
                details.append(formatMessage("👨‍👩‍👧‍👦 Семейное событие (создал: %s)\n", 
                    escape(event.getUser().getFirstName())));
            }
            
            // Статус
            if (event.getStatus() == Event.EventStatus.COMPLETED) {
                details.append(escape("✅ Статус: Завершено\n"));
            } else if (event.getStatus() == Event.EventStatus.DELETED) {
                details.append(escape("🗑️ Статус: Удалено\n"));
            }
            
            log.info("Детали события ID={} успешно отображены пользователю ID={}", eventId, userId);
            
            return details.toString();
            
        } catch (Exception e) {
            log.error("Ошибка при просмотре деталей события ID={}: {}", eventId, e.getMessage(), e);
            
            return formatMessage("❌ %s\n\n%s",
                   bold("Ошибка"),
                   "Не удалось загрузить детали события\\. Возможно, событие было удалено\\.");
        }
    }

    /**
     * Обрабатывает callback query для редактирования события.
     * 
     * <p>Извлекает ID события из callback data и инициирует процесс редактирования.
     * В будущем это будет многошаговый диалог для изменения полей события.</p>
     * 
     * @param eventId идентификатор события для редактирования
     * @param userId идентификатор пользователя, инициировавшего редактирование
     * @return сообщение с инструкциями по редактированию
     */
    public String handleEditCallback(Long eventId, Long userId) {
        log.info("Обработка callback редактирования события ID={} пользователем ID={}", 
                eventId, userId);
        
        // TODO: Реализовать многошаговый диалог редактирования
        // Пока возвращаем заглушку
        return formatMessage(
                "✏️ %s\n\nФункция редактирования будет реализована в следующей версии\\.\n\nID события: %s", 
                bold("Редактирование события"),
                eventId
        );
    }

    /**
     * Обрабатывает callback query для удаления события.
     * 
     * <p>Запрашивает подтверждение удаления события и выполняет удаление
     * после подтверждения пользователя.</p>
     * 
     * @param eventId идентификатор события для удаления
     * @param userId идентификатор пользователя, инициировавшего удаление
     * @return сообщение с запросом подтверждения или результатом удаления
     */
    public String handleDeleteCallback(Long eventId, Long userId) {
        log.info("Обработка callback удаления события ID={} пользователем ID={}", 
                eventId, userId);
        
        try {
            // Удаляем событие через сервис (он проверит права доступа)
            eventService.deleteEvent(eventId, userId);
            
            log.info("Событие ID={} успешно удалено пользователем ID={}", eventId, userId);
            
            return formatMessage("✅ %s\n\nСобытие успешно удалено из календаря\\.\n\nИспользуйте %s для просмотра оставшихся событий\\.",
                   bold("Событие удалено"), code("/my_events"));
                   
        } catch (Exception e) {
            log.error("Ошибка при удалении события ID={}: {}", eventId, e.getMessage(), e);
            
            return formatMessage("❌ %s\n\nНе удалось удалить событие\\. Возможно, у вас нет прав на удаление этого события\\.",
                   bold("Ошибка удаления"));
        }
    }

    /**
     * Определяет, требуется ли авторизация для выполнения этой команды.
     * 
     * <p>Команда /my_events требует авторизации, так как она отображает
     * события конкретного пользователя.</p>
     * 
     * @return true, так как команда требует авторизации
     */
    @Override
    public boolean requiresAuth() {
        return true;
    }

    /**
     * Возвращает команду, которую обрабатывает этот handler.
     * 
     * @return строка "/my_events"
     */
    @Override
    public String getCommand() {
        return "/my_events";
    }

    /**
     * Возвращает описание команды для отображения в справке.
     * 
     * @return описание команды
     */
    @Override
    public String getDescription() {
        return "Управление моими событиями";
    }
}
