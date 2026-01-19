package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

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
 *   <li>Отображает заголовок и первое событие в одном сообщении для единообразия с командой /trash</li>
 *   <li>Отображает остальные события отдельными сообщениями с inline кнопками для редактирования и удаления</li>
 *   <li>Форматирует события с использованием MarkdownV2 для улучшения читаемости</li>
 *   <li>Сортирует события по дате</li>
 *   <li>Отправляет соответствующее сообщение, если событий нет</li>
 *   <li>Использует fallback механизм при ошибках форматирования MarkdownV2</li>
 * </ul>
 * 
 * <p>Команда требует авторизации - пользователь должен быть зарегистрирован в системе.</p>
 * 
 * <p><b>Требования:</b> 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>
 * Пользователь отправляет: /my_events
 * 
 * Если есть события:
 * Бот отвечает одним сообщением (заголовок + первое событие):
 * "📋 *Мои события*
 *  
 *  Всего событий: 2
 *  
 *  📌 *День рождения мамы*
 *  📅 Дата: 31.12.2025
 *  🕐 Время: 18:00
 *  📝 Описание: Празднование дня рождения
 *  
 *  [Редактировать] [Удалить]"
 * 
 * Затем отдельным сообщением (второе событие):
 * "📌 *Поход в кино*
 *  📅 Дата: 02.01.2026
 *  🕐 Время: 20:00
 *  📝 Описание: Смотрим новый фильм
 *  
 *  [Редактировать] [Удалить]"
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
 * @version 2.0.0
 * @since 2025-12-30
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MyEventsCommandHandler implements CommandHandler {

    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final TelegramMessageService messageService;
    private final ConversationStateService conversationStateService;
    private final BotMessageBuilder botMessageBuilder;

    /**
     * Обрабатывает команду /my_events от пользователя.
     * 
     * <p>Метод получает список всех событий пользователя. Если события есть,
     * отправляет заголовок вместе с первым событием в одном сообщении,
     * а остальные события отправляет отдельными сообщениями с inline кнопками
     * для редактирования и удаления.</p>
     * 
     * <p>Формат вывода соответствует команде /trash для единообразия интерфейса.</p>
     * 
     * <p><b>Алгоритм работы:</b></p>
     * <ol>
     *   <li>Получение списка событий пользователя из базы данных</li>
     *   <li>Если список пуст - отправка сообщения об отсутствии событий</li>
     *   <li>Если события есть:
     *     <ul>
     *       <li>Формирование заголовка с количеством событий</li>
     *       <li>Объединение заголовка с первым событием в одно сообщение</li>
     *       <li>Отправка объединенного сообщения с inline-кнопками первого события</li>
     *       <li>Отправка остальных событий отдельными сообщениями</li>
     *     </ul>
     *   </li>
     *   <li>При ошибке форматирования MarkdownV2 (код 400) используется fallback механизм:
     *     <ul>
     *       <li>Заголовок отправляется отдельно</li>
     *       <li>Первое событие отправляется без форматирования, но с inline-кнопками</li>
     *     </ul>
     *   </li>
     * </ol>
     * 
     * <p><b>Обработка ошибок:</b></p>
     * <ul>
     *   <li>Ошибки форматирования MarkdownV2 (400) - fallback без форматирования</li>
     *   <li>Другие ошибки Telegram API - логирование и продолжение обработки</li>
     *   <li>Общие исключения - логирование и увеличение счетчика ошибок</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 4.1, 4.2, 4.3</p>
     * 
     * @param message входящее сообщение от Telegram, содержащее команду /my_events
     * @param user пользователь из базы данных, запросивший список своих событий.
     *             Не может быть null, так как команда требует авторизации.
     * @return null, так как все сообщения отправляются внутри метода
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

        log.debug("Обработка команды /my_events: telegramId={}, userId={}", 
                telegramId, user.getId());

        // Получаем события пользователя
        List<Event> userEvents = eventService.getUserEvents(user.getId());

        log.debug("Найдено {} событий для пользователя ID={}", userEvents.size(), user.getId());

        if (userEvents.isEmpty()) {
            String noEventsMessage = buildNoEventsMessage();
            try {
                messageService.sendMessage(chatId, noEventsMessage);
                log.debug("Сообщение об отсутствии событий отправлено пользователю chatId={}", chatId);
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения об отсутствии событий: chatId={}, error={}", 
                        chatId, e.getMessage(), e);
            }
            return null;
        }

        // Управление флагами isMyEventsHeader для событий
        // Устанавливаем флаг для первого события
        Event firstEvent = userEvents.get(0);
        if (!Boolean.TRUE.equals(firstEvent.getIsMyEventsHeader())) {
            log.debug("Установка флага isMyEventsHeader=true для первого события ID={}", firstEvent.getId());
            firstEvent.setIsMyEventsHeader(true);
            eventService.saveEvent(firstEvent);
        }
        
        // Сбрасываем флаг для остальных событий (если он был установлен)
        for (int i = 1; i < userEvents.size(); i++) {
            Event event = userEvents.get(i);
            if (Boolean.TRUE.equals(event.getIsMyEventsHeader())) {
                log.debug("Сброс флага isMyEventsHeader=false для события ID={}", event.getId());
                event.setIsMyEventsHeader(false);
                eventService.saveEvent(event);
            }
        }

        // Формируем заголовок
        String header = botMessageBuilder.buildMyEventsHeader(userEvents.size());
        
        log.debug("Начало отправки {} событий пользователю chatId={}", userEvents.size(), chatId);
        
        int successCount = 0;
        int failureCount = 0;
        
        // Обрабатываем первое событие - объединяем с заголовком
        try {
            String firstEventText = botMessageBuilder.buildEventMessage(firstEvent);
            String combinedMessage = header + "\n" + firstEventText;
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(firstEvent, user.getId());
            
            // Проверяем, что клавиатура создана корректно
            if (keyboard == null || keyboard.getKeyboard() == null || keyboard.getKeyboard().isEmpty()) {
                log.warn("Клавиатура для первого события ID={} некорректна, используем fallback", firstEvent.getId());
                throw new IllegalStateException("Некорректная клавиатура");
            }
            
            // Логируем детали перед отправкой
            int buttonCount = keyboard.getKeyboard().stream()
                    .mapToInt(List::size)
                    .sum();
            String textPreview = combinedMessage.length() > 50 
                    ? combinedMessage.substring(0, 50) + "..." 
                    : combinedMessage;
            
            log.debug("Отправка объединенного сообщения (заголовок + первое событие ID={}): textPreview='{}', buttonCount={}", 
                    firstEvent.getId(), textPreview, buttonCount);
            
            // ИЗМЕНЕНИЕ: Используем sendMessageAndGet для получения messageId
            org.telegram.telegrambots.meta.api.objects.Message sentMessage = 
                messageService.sendMessageAndGet(chatId, combinedMessage, keyboard);
            
            // ИЗМЕНЕНИЕ: Сохраняем messageId в базу данных
            firstEvent.setMessageId((long) sentMessage.getMessageId());
            eventService.saveEvent(firstEvent);
            
            successCount++;
            log.debug("Объединенное сообщение с первым событием ID={} успешно отправлено, messageId={} сохранен", 
                    firstEvent.getId(), sentMessage.getMessageId());
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException e) {
            // Обработка ошибок Telegram API с fallback механизмом
            if (e.getErrorCode() != null && e.getErrorCode() == 400) {
                // Ошибка 400 - проблема с форматированием MarkdownV2
                log.warn("Ошибка 400 при отправке объединенного сообщения с MarkdownV2, " +
                        "используем fallback: отправка заголовка и первого события отдельно. Ошибка: {}", 
                        e.getMessage());
                
                try {
                    // Fallback: отправляем заголовок отдельно
                    messageService.sendMessage(chatId, header);
                    // Отправляем первое событие без форматирования и сохраняем messageId
                    sendWithoutFormattingAndSaveMessageId(chatId, firstEvent, user.getId());
                    successCount++;
                    log.debug("Заголовок и первое событие ID={} успешно отправлены через fallback механизм", 
                            firstEvent.getId());
                } catch (Exception fallbackException) {
                    failureCount++;
                    log.error("Fallback механизм не сработал для первого события ID={}: {}", 
                            firstEvent.getId(), fallbackException.getMessage(), fallbackException);
                }
            } else {
                // Другие ошибки Telegram API
                failureCount++;
                log.error("Ошибка Telegram API при отправке объединенного сообщения: код={}, сообщение={}", 
                        e.getErrorCode(), e.getMessage(), e);
            }
        } catch (Exception e) {
            failureCount++;
            log.error("Ошибка при отправке объединенного сообщения с первым событием ID={}: {}", 
                    firstEvent.getId(), e.getMessage(), e);
        }
        
        // Отправляем остальные события отдельными сообщениями
        for (int i = 1; i < userEvents.size(); i++) {
            Event event = userEvents.get(i);
            try {
                String eventText = botMessageBuilder.buildEventMessage(event);
                InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, user.getId());
                
                // Проверяем, что клавиатура создана корректно
                if (keyboard == null) {
                    log.warn("Клавиатура для события ID={} равна null, пропускаем отправку", event.getId());
                    failureCount++;
                    continue;
                }
                
                if (keyboard.getKeyboard() == null || keyboard.getKeyboard().isEmpty()) {
                    log.warn("Клавиатура для события ID={} пустая (нет кнопок), пропускаем отправку", event.getId());
                    failureCount++;
                    continue;
                }
                
                // Логируем детали перед отправкой
                int buttonCount = keyboard.getKeyboard().stream()
                        .mapToInt(List::size)
                        .sum();
                String textPreview = eventText.length() > 50 
                        ? eventText.substring(0, 50) + "..." 
                        : eventText;
                
                log.debug("Отправка события ID={}: textPreview='{}', buttonCount={}", 
                        event.getId(), textPreview, buttonCount);
                
                // ИЗМЕНЕНИЕ: Используем sendMessageAndGet для получения messageId
                org.telegram.telegrambots.meta.api.objects.Message sentMessage = 
                    messageService.sendMessageAndGet(chatId, eventText, keyboard);
                
                // ИЗМЕНЕНИЕ: Сохраняем messageId в базу данных
                event.setMessageId((long) sentMessage.getMessageId());
                eventService.saveEvent(event);
                
                successCount++;
                log.debug("Событие ID={} успешно отправлено, messageId={} сохранен", 
                        event.getId(), sentMessage.getMessageId());
                
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException e) {
                // Обработка ошибок Telegram API с fallback механизмом
                if (e.getErrorCode() != null && e.getErrorCode() == 400) {
                    // Ошибка 400 - проблема с форматированием MarkdownV2
                    log.warn("Ошибка 400 при отправке события ID={} с MarkdownV2, " +
                            "попытка отправить без форматирования. Ошибка: {}", 
                            event.getId(), e.getMessage());
                    
                    try {
                        // Используем fallback механизм - отправка без форматирования и сохранение messageId
                        sendWithoutFormattingAndSaveMessageId(chatId, event, user.getId());
                        successCount++;
                        log.debug("Событие ID={} успешно отправлено через fallback механизм (без форматирования)", 
                                event.getId());
                    } catch (Exception fallbackException) {
                        failureCount++;
                        log.error("Fallback механизм не сработал для события ID={}: {}", 
                                event.getId(), fallbackException.getMessage(), fallbackException);
                    }
                } else {
                    // Другие ошибки Telegram API
                    failureCount++;
                    log.error("Ошибка Telegram API при отправке события ID={}: код={}, сообщение={}", 
                            event.getId(), e.getErrorCode(), e.getMessage(), e);
                }
            } catch (Exception e) {
                failureCount++;
                log.error("Ошибка при отправке события ID={}: {}", event.getId(), e.getMessage(), e);
            }
        }
        
        log.debug("Завершена отправка событий: успешно={}, ошибок={}", successCount, failureCount);
        
        // Возвращаем null, так как все сообщения уже отправлены внутри метода
        return null;
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
        StringBuilder message = new StringBuilder();
        message.append("📋 ").append(bold("Мои события")).append("\n\n");
        message.append(escape("У вас пока нет созданных событий.\n\n"));
        message.append(escape("Используйте ")).append(escape("/add_event")).append(escape(" для добавления нового события."));
        return message.toString();
    }

    /**
     * Отправляет событие без форматирования MarkdownV2.
     * 
     * <p>Этот метод используется как fallback механизм, когда отправка
     * с MarkdownV2 форматированием не удается из-за ошибки 400 (Bad Request).
     * Он форматирует текст события без использования MarkdownFormatter
     * и отправляет сообщение с parseMode=null.</p>
     * 
     * <p>Метод создает простое текстовое представление события с эмодзи,
     * но без специального форматирования (жирный текст, экранирование и т.д.).</p>
     * 
     * <p><b>Требования:</b> 4.4</p>
     * 
     * @param chatId ID чата для отправки сообщения
     * @param event событие для отправки
     * @param userId идентификатор пользователя для создания клавиатуры с учетом прав доступа
     * @throws org.telegram.telegrambots.meta.exceptions.TelegramApiException если отправка не удалась
     */
    private void sendWithoutFormatting(Long chatId, Event event, Long userId) 
            throws org.telegram.telegrambots.meta.exceptions.TelegramApiException {
        
        log.debug("Отправка события ID={} без форматирования MarkdownV2", event.getId());
        
        // Форматируем текст без использования MarkdownFormatter
        StringBuilder plainText = new StringBuilder();
        
        plainText.append("📌 ").append(event.getTitle()).append("\n");
        plainText.append("📅 Дата: ").append(event.getFormattedDate()).append("\n");
        plainText.append("🕐 Время: ").append(event.getFormattedTime());
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            plainText.append("\n📝 Описание: ").append(event.getDescription());
        }
        
        // Создаем inline кнопки с учетом статуса и прав доступа
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
        
        // Отправляем сообщение без форматирования через новый метод TelegramMessageService
        messageService.sendMessageWithoutFormatting(chatId, plainText.toString(), keyboard);
        
        log.debug("Событие ID={} успешно отправлено без форматирования", event.getId());
    }

    /**
     * Отправляет сообщение о событии без форматирования MarkdownV2 и сохраняет messageId.
     * 
     * <p>Этот метод используется как fallback механизм, когда отправка с MarkdownV2
     * форматированием не удается из-за ошибки 400 (Bad Request). После успешной отправки
     * сохраняет messageId в базу данных.</p>
     * 
     * @param chatId идентификатор чата для отправки сообщения
     * @param event событие для отправки
     * @param userId идентификатор пользователя для создания клавиатуры
     * @throws org.telegram.telegrambots.meta.exceptions.TelegramApiException если отправка не удалась
     */
    private void sendWithoutFormattingAndSaveMessageId(Long chatId, Event event, Long userId) 
            throws org.telegram.telegrambots.meta.exceptions.TelegramApiException {
        
        log.debug("Отправка события ID={} без форматирования MarkdownV2 с сохранением messageId", event.getId());
        
        // Форматируем текст без использования MarkdownFormatter
        StringBuilder plainText = new StringBuilder();
        
        plainText.append("📌 ").append(event.getTitle()).append("\n");
        plainText.append("📅 Дата: ").append(event.getFormattedDate()).append("\n");
        plainText.append("🕐 Время: ").append(event.getFormattedTime());
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            plainText.append("\n📝 Описание: ").append(event.getDescription());
        }
        
        // Создаем inline кнопки с учетом статуса и прав доступа
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
        
        // Отправляем сообщение без форматирования и получаем messageId
        // Используем sendMessageAndGet, но без parseMode (plain text)
        org.telegram.telegrambots.meta.api.methods.send.SendMessage sendMessage = 
            org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
                .chatId(chatId.toString())
                .text(plainText.toString())
                .replyMarkup(keyboard)
                .build();
        
        org.telegram.telegrambots.meta.api.objects.Message sentMessage = 
            messageService.execute(sendMessage);
        
        // Сохраняем messageId в базу данных
        event.setMessageId((long) sentMessage.getMessageId());
        eventService.saveEvent(event);
        
        log.debug("Событие ID={} успешно отправлено без форматирования, messageId={} сохранен", 
                event.getId(), sentMessage.getMessageId());
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
        log.debug("Обработка callback просмотра деталей события ID={} пользователем ID={}", 
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
            details.append("📋 ").append(bold("Детали события")).append("\n\n");
            
            // Название
            details.append("📌 ").append(bold(event.getTitle())).append("\n\n");
            
            // Дата
            details.append(formatMessage("📅 Дата: %s\n", event.getFormattedDate()));
            
            // Время
            if (event.getEventTime() != null) {
                if (event.getEndTime() != null) {
                    details.append(formatMessage("🕐 Время: %s - %s\n", 
                        event.getFormattedTime(), 
                        event.getEndTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))));
                } else {
                    details.append(formatMessage("🕐 Время: %s\n", event.getFormattedTime()));
                }
            }
            
            // Описание
            if (event.getDescription() != null && !event.getDescription().isBlank()) {
                details.append(formatMessage("📝 Описание: %s\n", event.getDescription()));
            }
            
            // Тип события
            details.append("\n");
            if (event.getIsPersonal()) {
                details.append("🔒 ").append(bold("Персональное событие")).append("\n");
            } else {
                details.append(formatMessage("👨‍👩‍👧‍👦 Семейное событие (создал: %s)\n", 
                    event.getUser().getFirstName()));
            }
            
            // Статус
            if (event.getStatus() == Event.EventStatus.COMPLETED) {
                details.append(escape("✅ Статус: Завершено\n"));
            } else if (event.getStatus() == Event.EventStatus.DELETED) {
                details.append(escape("🗑️ Статус: Удалено\n"));
            }
            
            log.debug("Детали события ID={} успешно отображены пользователю ID={}", eventId, userId);
            
            return details.toString();
            
        } catch (Exception e) {
            log.error("Ошибка при просмотре деталей события ID={}: {}", eventId, e.getMessage(), e);
            
            return formatMessage("❌ %s\n\n%s",
                   bold("Ошибка"),
                   "Не удалось загрузить детали события. Возможно, событие было удалено.");
        }
    }

    /**
     * Обрабатывает callback query для редактирования события.
     * 
     * <p>Извлекает ID события из callback data и инициирует процесс редактирования.
     * Проверяет права доступа пользователя и начинает многошаговый диалог редактирования.</p>
     * 
     * <p><b>Требования:</b> 2.1, 2.2, 4.1</p>
     * 
     * @param eventId идентификатор события для редактирования
     * @param userId идентификатор пользователя, инициировавшего редактирование
     * @param chatId идентификатор чата для отправки сообщений
     * @return сообщение с текущими данными события и клавиатурой выбора поля
     */
    public String handleEditCallback(Long eventId, Long userId, Long chatId) {
        log.debug("Обработка callback редактирования события ID={} пользователем ID={}", 
                eventId, userId);
        
        try {
            // Получаем событие и проверяем права доступа
            Event event = eventService.getEventById(eventId);
            
            // Проверяем права доступа
            if (!canUserEditEvent(event, userId)) {
                log.warn("Пользователь ID={} не имеет прав для редактирования события ID={}", 
                        userId, eventId);
                return formatMessage("❌ %s\n\nУ вас нет прав для редактирования этого события.",
                       bold("Доступ запрещен"));
            }
            
            // Начинаем диалог редактирования
            conversationStateService.startEventEditing(userId, eventId, chatId);
            
            // Формируем сообщение с текущими данными события и клавиатурой выбора поля
            String message = buildEditFieldSelectionMessage(event);
            
            // Отправляем сообщение с клавиатурой
            InlineKeyboardMarkup keyboard = keyboardService.createEditFieldSelectionKeyboard(eventId);
            messageService.sendMessageWithInlineKeyboard(chatId, message, keyboard);
            
            log.debug("Начато редактирование события ID={} пользователем ID={}", eventId, userId);
            
            return message;
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            log.error("Событие ID={} не найдено: {}", eventId, e.getMessage());
            return formatMessage("❌ %s\n\nСобытие не найдено. Возможно, оно было удалено.",
                   bold("Ошибка"));
        } catch (Exception e) {
            log.error("Ошибка при начале редактирования события ID={}: {}", eventId, e.getMessage(), e);
            return formatMessage("❌ %s\n\nПроизошла ошибка при начале редактирования.",
                   bold("Ошибка"));
        }
    }

    /**
     * Обрабатывает callback query для удаления события.
     * 
     * <p>Выполняет удаление события через EventService.
     * Метод больше не возвращает сообщение для отправки,
     * так как подтверждение теперь отправляется через callback query ответ
     * в {@link ru.golubyatnikov.family.calendar.bot.handler.callback.EventCallbackHandler}.</p>
     * 
     * <p>Метод делегирует удаление в {@link EventService#deleteEvent(Long, Long)},
     * который выполняет проверку прав доступа и перемещает событие в корзину.</p>
     * 
     * <p><b>Требования:</b> 2.1, 2.3</p>
     * 
     * @param eventId идентификатор события для удаления
     * @param userId идентификатор пользователя, инициировавшего удаление
     * @throws ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException если событие не найдено
     * @throws ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException если пользователь не имеет прав на удаление
     */
    public void handleDeleteCallback(Long eventId, Long userId) {
        log.debug("Обработка callback удаления события ID={} пользователем ID={}", 
                eventId, userId);
        
        // Удаляем событие через сервис (он проверит права доступа)
        eventService.deleteEvent(eventId, userId);
        
        log.debug("Событие ID={} успешно удалено пользователем ID={}", eventId, userId);
    }
    
    /**
     * Проверяет, может ли пользователь редактировать событие.
     * 
     * <p>Пользователь может редактировать событие, если:</p>
     * <ul>
     *   <li>Он создатель события</li>
     *   <li>Событие семейное и пользователь из той же семьи</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 2.2, 4.1, 4.3</p>
     * 
     * @param event событие для проверки
     * @param userId идентификатор пользователя
     * @return true, если пользователь может редактировать событие
     */
    private boolean canUserEditEvent(Event event, Long userId) {
        // Пользователь может редактировать событие, если:
        // 1. Он создатель события
        if (event.getUser().getId().equals(userId)) {
            return true;
        }
        
        // 2. Событие семейное и пользователь из той же семьи
        if (!event.getIsPersonal() && event.getFamily() != null) {
            return event.getFamily().getMembers().stream()
                    .anyMatch(u -> u.getId().equals(userId));
        }
        
        return false;
    }
    
    /**
     * Формирует сообщение с выбором поля для редактирования.
     * 
     * <p>Отображает текущие данные события и предлагает выбрать поле для редактирования.</p>
     * <p>Все специальные символы MarkdownV2 корректно экранированы.</p>
     * 
     * <p><b>Требования:</b> 2.2, 4.1, 4.3</p>
     * 
     * @param event событие для отображения
     * @return отформатированное сообщение с текущими данными события
     */
    public String buildEditFieldSelectionMessage(Event event) {
        StringBuilder message = new StringBuilder();
        
        // Заголовок
        message.append("✏️ ").append(bold("Редактирование события")).append("\n\n");
        
        // Название события
        message.append("📌 ").append(bold(event.getTitle())).append("\n\n");
        
        // Текущие данные
        message.append(formatMessage("Текущие данные:\n"));
        message.append(formatMessage("📅 Дата: %s\n", event.getFormattedDate()));
        message.append(formatMessage("🕐 Время: %s\n", event.getFormattedTime()));
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            message.append(formatMessage("📝 Описание: %s\n", event.getDescription()));
        } else {
            message.append(formatMessage("📝 Описание: не указано\n"));
        }
        
        message.append(formatMessage("\nВыберите поле для редактирования:"));
        
        return message.toString();
    }
    
    /**
     * Формирует сообщение об успешном обновлении поля события.
     * 
     * <p>Отображает обновленные данные события после изменения поля.</p>
     * <p>Все специальные символы MarkdownV2 корректно экранированы.</p>
     * 
     * <p><b>Требования:</b> 2.2, 4.1, 4.3</p>
     * 
     * @param event событие с обновленными данными
     * @param field обновленное поле
     * @return отформатированное сообщение об успешном обновлении
     */
    private String buildFieldUpdateSuccessMessage(Event event, ConversationStateService.EditField field) {
        StringBuilder message = new StringBuilder();
        
        // Заголовок
        message.append("✅ ").append(bold("Поле обновлено")).append("\n\n");
        
        // Название события
        message.append("📌 ").append(bold(event.getTitle())).append("\n\n");
        
        // Информация об обновленном поле
        switch (field) {
            case TITLE:
                message.append("Название изменено на: ").append(bold(event.getTitle())).append("\n");
                break;
            case DATE:
                message.append(formatMessage("Дата изменена на: %s\n", event.getFormattedDate()));
                break;
            case TIME:
                message.append(formatMessage("Время изменено на: %s\n", event.getFormattedTime()));
                break;
            case DESCRIPTION:
                if (event.getDescription() != null && !event.getDescription().isBlank()) {
                    message.append(formatMessage("Описание изменено на: %s\n", event.getDescription()));
                } else {
                    message.append(formatMessage("Описание удалено\n"));
                }
                break;
        }
        
        // Текущие данные
        message.append(formatMessage("\nТекущие данные события:\n"));
        message.append(formatMessage("📅 Дата: %s\n", event.getFormattedDate()));
        message.append(formatMessage("🕐 Время: %s\n", event.getFormattedTime()));
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            message.append(formatMessage("📝 Описание: %s", event.getDescription()));
        } else {
            message.append(formatMessage("📝 Описание: не указано"));
        }
        
        return message.toString();
    }

    /**
     * Обновляет счетчик событий в шапке первого сообщения.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Получает актуальное количество активных событий пользователя</li>
     *   <li>Находит событие с флагом isMyEventsHeader=true</li>
     *   <li>Формирует новую шапку с актуальным счетчиком</li>
     *   <li>Формирует полный текст сообщения (шапка + событие)</li>
     *   <li>Обновляет сообщение через Telegram API</li>
     *   <li>Сохраняет inline-кнопки события</li>
     * </ol>
     * 
     * <p>Если событие с шапкой не найдено или обновление не удается,
     * метод логирует ошибку и продолжает работу без выброса исключения.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5</p>
     * 
     * @param userId идентификатор пользователя
     */
    public void updateMyEventsHeaderCount(Long userId) {
        if (userId == null) {
            log.error("Попытка обновить шапку с null userId");
            return;
        }
        
        log.debug("Обновление счетчика событий в шапке для пользователя ID={}", userId);
        
        try {
            // Получаем актуальное количество активных событий пользователя
            List<Event> userEvents = eventService.getUserEvents(userId);
            int eventCount = userEvents.size();
            
            log.debug("Найдено {} активных событий для пользователя ID={}", eventCount, userId);
            
            // Если нет событий, нечего обновлять
            if (eventCount == 0) {
                log.debug("Нет активных событий для пользователя ID={}, обновление шапки не требуется", userId);
                return;
            }
            
            // Находим событие с флагом isMyEventsHeader=true
            Event headerEvent = userEvents.stream()
                    .filter(e -> Boolean.TRUE.equals(e.getIsMyEventsHeader()))
                    .findFirst()
                    .orElse(null);
            
            if (headerEvent == null) {
                log.warn("Событие с флагом isMyEventsHeader не найдено для пользователя ID={}", userId);
                return;
            }
            
            // Проверяем наличие messageId
            if (headerEvent.getMessageId() == null) {
                log.warn("У события ID={} с флагом isMyEventsHeader отсутствует messageId", headerEvent.getId());
                return;
            }
            
            log.debug("Найдено событие с шапкой: ID={}, messageId={}", 
                    headerEvent.getId(), headerEvent.getMessageId());
            
            // Формируем новую шапку с актуальным счетчиком
            String header = botMessageBuilder.buildMyEventsHeader(eventCount);
            
            // Формируем текст события
            String eventText = botMessageBuilder.buildEventMessage(headerEvent);
            
            // Объединяем шапку и событие
            String combinedMessage = header + "\n" + eventText;
            
            // Получаем inline-кнопки события
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(headerEvent, userId);
            
            // Получаем chatId пользователя
            Long chatId = headerEvent.getUser().getTelegramId();
            
            log.debug("Попытка обновить сообщение: chatId={}, messageId={}, textLength={}", 
                    chatId, headerEvent.getMessageId(), combinedMessage.length());
            
            // Обновляем сообщение через Telegram API
            boolean updated = messageService.tryEditMessageText(
                    chatId, 
                    headerEvent.getMessageId().intValue(), 
                    combinedMessage, 
                    keyboard);
            
            if (updated) {
                log.info("Счетчик событий в шапке успешно обновлен для пользователя ID={}, новое значение: {}", 
                        userId, eventCount);
            } else {
                log.warn("Не удалось обновить счетчик событий в шапке для пользователя ID={}: сообщение не найдено или удалено", 
                        userId);
            }
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка Telegram API при обновлении счетчика событий в шапке для пользователя ID={}: {}", 
                    userId, e.getMessage(), e);
            // Продолжаем работу без выброса исключения
            
        } catch (Exception e) {
            log.error("Неожиданная ошибка при обновлении счетчика событий в шапке для пользователя ID={}: {}", 
                    userId, e.getMessage(), e);
            // Продолжаем работу без выброса исключения
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
