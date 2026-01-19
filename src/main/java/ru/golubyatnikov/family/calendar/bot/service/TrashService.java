package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.EventHistory;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для управления корзиной удаленных событий.
 * 
 * <p>Этот сервис предоставляет функциональность для работы с удаленными событиями:</p>
 * <ul>
 *   <li>Получение списка удаленных событий пользователя</li>
 *   <li>Восстановление событий из корзины</li>
 *   <li>Окончательное удаление событий</li>
 *   <li>Автоматическая очистка старых событий (старше 30 дней)</li>
 * </ul>
 * 
 * <p>События в корзине хранятся 30 дней, после чего автоматически удаляются
 * scheduled задачей, которая выполняется каждый день в 2:00.</p>
 * 
 * <p><b>Требования:</b> 19.1, 19.2, 19.4, 19.5, 19.6</p>
 * 
 * @see Event
 * @see EventRepository
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrashService {
    
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventHistoryService eventHistoryService;
    private final ReminderService reminderService;
    private final TelegramMessageService messageService;
    private final ru.golubyatnikov.family.calendar.bot.handler.MyEventsCommandHandler myEventsCommandHandler;
    private final ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder botMessageBuilder;
    private final KeyboardService keyboardService;
    
    private static final int TRASH_RETENTION_DAYS = 30;
    
    /**
     * Сохраняет событие в базу данных.
     * 
     * <p>Вспомогательный метод для сохранения изменений события.</p>
     * 
     * @param event событие для сохранения
     * @return сохраненное событие
     * @throws IllegalArgumentException если event равен null
     */
    public Event saveEvent(Event event) {
        if (event == null) {
            log.error("Попытка сохранить null событие");
            throw new IllegalArgumentException("Событие не может быть null");
        }
        
        log.debug("Сохранение события ID={}", event.getId());
        Event savedEvent = eventRepository.save(event);
        log.debug("Событие ID={} успешно сохранено", savedEvent.getId());
        
        return savedEvent;
    }
    
    /**
     * Получает список удаленных событий пользователя.
     * 
     * <p>Возвращает события со статусом DELETED, отсортированные по дате удаления
     * (от новых к старым).</p>
     * 
     * @param userId идентификатор пользователя
     * @return список удаленных событий
     * @throws IllegalArgumentException если userId равен null
     */
    @Transactional(readOnly = true)
    public List<Event> getUserTrash(Long userId) {
        if (userId == null) {
            log.error("Попытка получить корзину с userId=null");
            throw new IllegalArgumentException("ID пользователя не может быть null");
        }
        
        log.debug("Получение корзины для пользователя ID={}", userId);
        
        List<Event> trashedEvents = eventRepository.findByUserIdAndStatusOrderByDeletedAtDesc(
            userId, 
            Event.EventStatus.DELETED
        );
        
        log.info("Получено {} удаленных событий для пользователя ID={}", 
                 trashedEvents.size(), userId);
        
        return trashedEvents;
    }
    
    /**
     * Восстанавливает событие из корзины.
     * 
     * <p>Изменяет статус события с DELETED на ACTIVE и очищает дату удаления.
     * Записывает действие в историю изменений.</p>
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя, восстанавливающего событие
     * @return восстановленное событие
     * @throws EventNotFoundException если событие не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     * @throws IllegalStateException если событие не находится в корзине
     */
    @Transactional
    public Event restoreEvent(Long eventId, Long userId) {
        if (eventId == null || userId == null) {
            log.error("Попытка восстановить событие с eventId={} или userId=null", eventId);
            throw new IllegalArgumentException("ID события и пользователя не могут быть null");
        }
        
        log.debug("Восстановление события ID={} пользователем ID={}", eventId, userId);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие ID={} не найдено при попытке восстановления", eventId);
                return new EventNotFoundException("Событие с ID " + eventId + " не найдено");
            });
        
        // Проверка прав доступа
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался восстановить чужое событие ID={}", 
                     userId, eventId);
            throw new UnauthorizedAccessException(
                "Только создатель события может восстановить его из корзины"
            );
        }
        
        // Проверка, что событие находится в корзине
        if (event.getStatus() != Event.EventStatus.DELETED) {
            log.error("Попытка восстановить событие ID={} со статусом {}", 
                     eventId, event.getStatus());
            throw new IllegalStateException(
                "Событие не находится в корзине (статус: " + event.getStatus() + ")"
            );
        }
        
        // Удаляем старое сообщение события перед восстановлением
        if (event.getMessageId() != null) {
            Long chatId = event.getUser().getTelegramId();
            messageService.deleteMessage(chatId, event.getMessageId().intValue());
            log.debug("Старое сообщение события удалено при восстановлении: eventId={}, messageId={}", 
                     eventId, event.getMessageId());
        }
        
        // Восстановление события
        event.setStatus(Event.EventStatus.ACTIVE);
        event.setDeletedAt(null);
        // Сбрасываем messageId, чтобы при восстановлении создалось новое сообщение
        event.setMessageId(null);
        // ВАЖНО: Сбрасываем флаг isMyEventsHeader, он будет установлен заново при вызове /my_events
        event.setIsMyEventsHeader(false);
        // Сбрасываем флаг isTrashHeader при восстановлении (Требование 1.1)
        event.setIsTrashHeader(false);
        
        Event restoredEvent = eventRepository.save(event);
        
        // Пересчет напоминаний при восстановлении из корзины (Требование 9.4)
        if (event.getEventDate() != null && event.getEventTime() != null) {
            try {
                reminderService.recalculateReminders(eventId);
                log.debug("Напоминания пересчитаны для восстановленного события ID={}", eventId);
            } catch (Exception e) {
                log.warn("Не удалось пересчитать напоминания для восстановленного события ID={}: {}", 
                        eventId, e.getMessage());
                // Не прерываем восстановление события из-за ошибки пересчета напоминаний
            }
        }
        
        // Запись в историю
        eventHistoryService.recordChange(
            eventId,
            userId,
            EventHistory.ActionType.RESTORED,
            "status",
            Event.EventStatus.DELETED.name(),
            Event.EventStatus.ACTIVE.name()
        );
        
        log.info("Событие ID={} успешно восстановлено пользователем ID={}", eventId, userId);
        
        // Обновляем счетчик событий в шапке после восстановления
        myEventsCommandHandler.updateMyEventsHeaderCount(userId);
        log.debug("Счетчик событий в шапке обновлен после восстановления события ID={}", eventId);
        
        // Обновляем шапку корзины после восстановления
        updateTrashHeaderAfterRemoval(userId);
        log.debug("Шапка корзины обновлена после восстановления события ID={}", eventId);
        
        return restoredEvent;
    }
    
    /**
     * Окончательно удаляет событие из системы.
     * 
     * <p>Физически удаляет событие из базы данных. Это действие необратимо.
     * Событие должно находиться в корзине (статус DELETED).</p>
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя, удаляющего событие
     * @throws EventNotFoundException если событие не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     * @throws IllegalStateException если событие не находится в корзине
     */
    @Transactional
    public void permanentlyDelete(Long eventId, Long userId) {
        if (eventId == null || userId == null) {
            log.error("Попытка окончательно удалить событие с eventId={} или userId=null", eventId);
            throw new IllegalArgumentException("ID события и пользователя не могут быть null");
        }
        
        log.debug("Окончательное удаление события ID={} пользователем ID={}", eventId, userId);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие ID={} не найдено при попытке окончательного удаления", eventId);
                return new EventNotFoundException("Событие с ID " + eventId + " не найдено");
            });
        
        // Проверка прав доступа
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался окончательно удалить чужое событие ID={}", 
                     userId, eventId);
            throw new UnauthorizedAccessException(
                "Только создатель события может окончательно удалить его"
            );
        }
        
        // Проверка, что событие находится в корзине
        if (event.getStatus() != Event.EventStatus.DELETED) {
            log.error("Попытка окончательно удалить событие ID={} со статусом {}", 
                     eventId, event.getStatus());
            throw new IllegalStateException(
                "Можно окончательно удалить только события из корзины (текущий статус: " + 
                event.getStatus() + ")"
            );
        }
        
        // Удаляем сообщение события перед окончательным удалением
        if (event.getMessageId() != null) {
            Long chatId = event.getUser().getTelegramId();
            messageService.deleteMessage(chatId, event.getMessageId().intValue());
            log.debug("Сообщение события удалено при окончательном удалении: eventId={}, messageId={}", 
                     eventId, event.getMessageId());
        }
        
        // Окончательное удаление
        eventRepository.delete(event);
        
        log.info("Событие ID={} окончательно удалено пользователем ID={}", eventId, userId);
        
        // Обновляем шапку корзины после удаления
        updateTrashHeaderAfterRemoval(userId);
        log.debug("Шапка корзины обновлена после окончательного удаления события ID={}", eventId);
    }
    
    /**
     * Обновляет шапку корзины после удаления или восстановления события.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Получает актуальный список событий в корзине</li>
     *   <li>Если корзина пуста - отправляет сообщение о пустой корзине</li>
     *   <li>Если есть события - обновляет флаг isTrashHeader и счетчик</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 2.1, 2.2, 2.3, 3.1, 3.3, 6.1, 6.2, 6.3</p>
     * 
     * @param userId идентификатор пользователя
     * @throws IllegalArgumentException если userId равен null
     */
    @Transactional
    public void updateTrashHeaderAfterRemoval(Long userId) {
        if (userId == null) {
            log.error("Попытка обновить шапку корзины с userId=null");
            throw new IllegalArgumentException("ID пользователя не может быть null");
        }
        
        log.debug("Обновление шапки корзины для пользователя ID={}", userId);
        
        List<Event> trashedEvents = getUserTrash(userId);
        
        // Получаем chatId пользователя
        Long chatId = userRepository.findById(userId)
            .map(user -> user.getTelegramId())
            .orElse(null);
        
        if (chatId == null) {
            log.warn("Не удалось получить chatId для пользователя ID={}", userId);
            return;
        }
        
        if (trashedEvents.isEmpty()) {
            // Отправляем сообщение о пустой корзине
            String emptyMessage = buildEmptyTrashMessage();
            try {
                messageService.sendMessage(chatId, emptyMessage);
                log.info("Отправлено сообщение о пустой корзине для пользователя ID={}", userId);
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения о пустой корзине для пользователя ID={}: {}", 
                         userId, e.getMessage(), e);
            }
            return;
        }
        
        // Находим новое первое событие
        Event newFirstEvent = trashedEvents.get(0);
        
        // Сбрасываем флаг isTrashHeader у всех событий кроме первого
        for (Event event : trashedEvents) {
            if (event.getId().equals(newFirstEvent.getId())) {
                // Устанавливаем флаг для первого события
                if (!Boolean.TRUE.equals(event.getIsTrashHeader())) {
                    event.setIsTrashHeader(true);
                    eventRepository.save(event);
                    log.debug("Флаг isTrashHeader установлен для события ID={}", event.getId());
                }
            } else {
                // Сбрасываем флаг для остальных событий
                if (Boolean.TRUE.equals(event.getIsTrashHeader())) {
                    event.setIsTrashHeader(false);
                    eventRepository.save(event);
                    log.debug("Флаг isTrashHeader сброшен для события ID={}", event.getId());
                }
            }
        }
        
        // Обновляем счетчик в шапке
        updateTrashHeaderCount(userId);
        log.debug("Шапка корзины обновлена для пользователя ID={}", userId);
    }
    
    /**
     * Обновляет счетчик событий в шапке корзины.
     * 
     * <p>Метод находит событие с флагом isTrashHeader и обновляет его сообщение,
     * добавляя актуальный счетчик событий в корзине.</p>
     * 
     * <p><b>Требования:</b> 6.1, 6.2, 6.3, 6.4</p>
     * 
     * @param userId идентификатор пользователя
     * @throws IllegalArgumentException если userId равен null
     */
    public void updateTrashHeaderCount(Long userId) {
        if (userId == null) {
            log.error("Попытка обновить счетчик корзины с userId=null");
            throw new IllegalArgumentException("ID пользователя не может быть null");
        }
        
        log.debug("Обновление счетчика в шапке корзины для пользователя ID={}", userId);
        
        List<Event> trashedEvents = getUserTrash(userId);
        
        if (trashedEvents.isEmpty()) {
            log.debug("Корзина пуста, обновление счетчика не требуется для пользователя ID={}", userId);
            return;
        }
        
        // Находим событие с шапкой
        Event headerEvent = trashedEvents.stream()
            .filter(e -> Boolean.TRUE.equals(e.getIsTrashHeader()))
            .findFirst()
            .orElse(null);
        
        if (headerEvent == null) {
            log.warn("Не найдено событие с флагом isTrashHeader для пользователя ID={}", userId);
            return;
        }
        
        if (headerEvent.getMessageId() == null) {
            log.warn("У события с шапкой ID={} отсутствует messageId", headerEvent.getId());
            return;
        }
        
        // Формируем новую шапку
        String header = botMessageBuilder.buildTrashHeader(trashedEvents.size());
        String eventText = botMessageBuilder.buildEventMessage(headerEvent);
        String combinedMessage = header + "\n" + eventText;
        
        // Получаем клавиатуру
        InlineKeyboardMarkup keyboard = keyboardService.createTrashActionsKeyboard(headerEvent.getId());
        
        // Обновляем сообщение
        Long chatId = headerEvent.getUser().getTelegramId();
        try {
            boolean updated = messageService.tryEditMessageText(
                chatId,
                headerEvent.getMessageId().intValue(),
                combinedMessage,
                keyboard
            );
            
            if (updated) {
                log.info("Счетчик в шапке корзины обновлен для пользователя ID={}, событие ID={}", 
                        userId, headerEvent.getId());
            } else {
                log.warn("Не удалось обновить счетчик в шапке корзины для пользователя ID={}, событие ID={}", 
                        userId, headerEvent.getId());
            }
        } catch (Exception e) {
            log.error("Ошибка при обновлении счетчика в шапке корзины для пользователя ID={}: {}", 
                     userId, e.getMessage(), e);
            // Не выбрасываем исключение, чтобы не прерывать основной процесс
        }
    }
    
    /**
     * Формирует сообщение о пустой корзине.
     * 
     * <p>Сообщение содержит:</p>
     * <ul>
     *   <li>Эмодзи 🗑️ и заголовок "Корзина" (выделено жирным)</li>
     *   <li>Текст "Корзина пуста."</li>
     *   <li>Информацию о сроке хранения событий (italic текст)</li>
     * </ul>
     * 
     * <p>Все специальные символы MarkdownV2 корректно экранированы.</p>
     * 
     * <p><b>Требования:</b> 3.1, 3.2, 3.3</p>
     * 
     * @return отформатированное сообщение о пустой корзине с MarkdownV2 форматированием
     */
    private String buildEmptyTrashMessage() {
        StringBuilder message = new StringBuilder();
        message.append("🗑️ ").append(ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold("Корзина")).append("\n\n");
        message.append(ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape("Корзина пуста.\n\n"));
        message.append(ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.italic("Удаленные события хранятся здесь 30 дней, после чего автоматически удаляются навсегда."));
        return message.toString();
    }
    
    /**
     * Автоматически очищает корзину от старых событий.
     * 
     * <p>Выполняется каждый день в 2:00 по расписанию.
     * Удаляет события, которые находятся в корзине более 30 дней.</p>
     * 
     * <p>Использует cron выражение: "0 0 2 * * ?" (каждый день в 2:00)</p>
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldTrash() {
        log.info("Запуск автоматической очистки корзины");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(TRASH_RETENTION_DAYS);
        
        log.debug("Поиск событий в корзине старше {}", cutoffDate);
        
        List<Event> oldEvents = eventRepository.findByStatusAndDeletedAtBefore(
            Event.EventStatus.DELETED,
            cutoffDate
        );
        
        if (oldEvents.isEmpty()) {
            log.info("Старых событий для удаления не найдено");
            return;
        }
        
        log.info("Найдено {} старых событий для удаления", oldEvents.size());
        
        int deletedCount = 0;
        for (Event event : oldEvents) {
            try {
                // Удаляем сообщение события перед окончательным удалением (Требование 1.2, 1.3)
                if (event.getMessageId() != null) {
                    try {
                        Long chatId = event.getUser().getTelegramId();
                        messageService.deleteMessage(chatId, event.getMessageId().intValue());
                        log.debug("Сообщение события удалено при автоматической очистке: eventId={}, messageId={}", 
                                 event.getId(), event.getMessageId());
                    } catch (Exception e) {
                        // Обрабатываем ошибки удаления сообщений без прерывания процесса очистки
                        log.warn("Не удалось удалить сообщение события ID={}, messageId={}: {}. Продолжаем очистку.", 
                                event.getId(), event.getMessageId(), e.getMessage());
                        // Продолжаем удаление события из БД даже если не удалось удалить сообщение
                    }
                }
                
                eventRepository.delete(event);
                deletedCount++;
                log.debug("Событие ID={} окончательно удалено (в корзине с {})", 
                         event.getId(), event.getDeletedAt());
            } catch (Exception e) {
                log.error("Ошибка при удалении события ID={} из базы данных: {}", 
                         event.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Автоматическая очистка корзины завершена: удалено {} из {} событий", 
                 deletedCount, oldEvents.size());
    }
}

