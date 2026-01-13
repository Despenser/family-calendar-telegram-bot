package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Сервис для отправки уведомлений о предстоящих событиях.
 * 
 * <p>NotificationService автоматически проверяет события, которые начнутся
 * в ближайшее время, и отправляет уведомления всем членам семьи.</p>
 * 
 * <p>Основные возможности:</p>
 * <ul>
 *   <li>Автоматическая проверка предстоящих событий каждые 5 минут</li>
 *   <li>Отправка уведомлений за 1 час до события</li>
 *   <li>Отправка уведомлений всем членам семьи</li>
 *   <li>Отметка событий как notified после отправки</li>
 *   <li>Retry логика для надежной доставки</li>
 *   <li>Подробное логирование всех операций</li>
 * </ul>
 * 
 * <p>Планировщик:</p>
 * <ul>
 *   <li>Интервал проверки: каждые 5 минут (300000 мс)</li>
 *   <li>Окно уведомлений: за 1 час до события</li>
 *   <li>Условие отправки: notified = false</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 6.1, 6.2, 6.3, 6.4, 6.5</p>
 * 
 * <p><b>Пример работы:</b></p>
 * <pre>
 * Текущее время: 14:00
 * Событие: 15:00 - "День рождения"
 * 
 * Действия:
 * 1. Планировщик находит событие (начало через 1 час)
 * 2. Формирует уведомление с деталями события
 * 3. Отправляет уведомление всем членам семьи
 * 4. Отмечает событие как notified = true
 * 5. Логирует результат отправки
 * </pre>
 * 
 * @see EventRepository
 * @see TelegramMessageService
 * @see Event
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final EventRepository eventRepository;
    private final TelegramMessageService messageService;

    /**
     * Отправляет уведомления о событиях, которые начнутся через 1 час.
     * 
     * <p>Этот метод выполняется автоматически каждые 5 минут благодаря
     * аннотации @Scheduled. Он находит все события, которые начнутся
     * в ближайший час и для которых еще не было отправлено уведомление.</p>
     * 
     * <p>Алгоритм работы:</p>
     * <ol>
     *   <li>Определяет временное окно (текущее время + 1 час)</li>
     *   <li>Находит события в этом окне с notified = false</li>
     *   <li>Для каждого события:
     *     <ul>
     *       <li>Формирует текст уведомления</li>
     *       <li>Отправляет уведомление всем членам семьи</li>
     *       <li>Отмечает событие как notified</li>
     *     </ul>
     *   </li>
     *   <li>Логирует результаты отправки</li>
     * </ol>
     * 
     * <p>Обработка ошибок:</p>
     * <ul>
     *   <li>Ошибки отправки конкретному пользователю не прерывают процесс</li>
     *   <li>Событие отмечается как notified только после успешной отправки всем</li>
     *   <li>Все ошибки логируются с полными деталями</li>
     * </ul>
     * 
     * <p>Retry механизм обеспечивается TelegramMessageService (до 3 попыток).</p>
     * 
     * @see EventRepository#findEventsForNotification(LocalDateTime, LocalDateTime)
     * @see #sendNotificationToFamily(Event)
     * @see #markAsNotified(Event)
     */
    @Scheduled(fixedDelay = 300000) // каждые 5 минут
    @Transactional
    public void sendUpcomingEventNotifications() {
        log.info("Запуск проверки предстоящих событий для отправки уведомлений");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourLater = now.plusHours(1);
        
        log.debug("Поиск событий в диапазоне: {} - {}", 
                now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                oneHourLater.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        try {
            // Находим события, которые начнутся в ближайший час
            List<Event> upcomingEvents = eventRepository.findEventsForNotification(now, oneHourLater);
            
            if (upcomingEvents.isEmpty()) {
                log.info("Нет событий для отправки уведомлений в ближайший час");
                return;
            }
            
            log.info("Найдено {} событий для отправки уведомлений", upcomingEvents.size());
            
            int successCount = 0;
            int failureCount = 0;
            
            for (Event event : upcomingEvents) {
                try {
                    log.debug("Обработка события: id={}, title='{}', eventDateTime={}", 
                            event.getId(), 
                            event.getTitle(), 
                            event.getEventDateTime());
                    
                    // Отправляем уведомление всем членам семьи
                    boolean sent = sendNotificationToFamily(event);
                    
                    if (sent) {
                        // Отмечаем событие как notified
                        markAsNotified(event);
                        successCount++;
                        
                        log.info("Уведомление успешно отправлено для события: id={}, title='{}'", 
                                event.getId(), event.getTitle());
                    } else {
                        failureCount++;
                        log.warn("Не удалось отправить уведомление для события: id={}, title='{}'", 
                                event.getId(), event.getTitle());
                    }
                    
                } catch (Exception e) {
                    failureCount++;
                    log.error("Ошибка при обработке события: id={}, title='{}', error={}", 
                            event.getId(), event.getTitle(), e.getMessage(), e);
                }
            }
            
            log.info("Завершена отправка уведомлений: успешно={}, ошибок={}, всего={}", 
                    successCount, failureCount, upcomingEvents.size());
            
        } catch (Exception e) {
            log.error("Критическая ошибка при проверке предстоящих событий: {}", 
                    e.getMessage(), e);
        }
    }

    /**
     * Отправляет уведомление о событии всем членам семьи.
     * 
     * <p>Метод формирует текст уведомления и отправляет его каждому
     * члену семьи через TelegramMessageService. Если отправка хотя бы
     * одному члену семьи не удалась, метод возвращает false.</p>
     * 
     * <p>Формат уведомления:</p>
     * <pre>
     * 🔔 *Напоминание о событии*
     * 
     * 📅 Дата: 30.12.2025
     * 🕐 Время: 15:00
     * 📝 Название: День рождения
     * 📄 Описание: Празднование дня рождения
     * 👤 Создал: Иван Иванов
     * </pre>
     * 
     * <p>Retry логика:</p>
     * <ul>
     *   <li>TelegramMessageService автоматически повторяет попытки</li>
     *   <li>Максимум 3 попытки на каждого пользователя</li>
     *   <li>Экспоненциальная задержка между попытками</li>
     * </ul>
     * 
     * @param event событие, о котором нужно уведомить
     * @return true, если уведомление успешно отправлено всем членам семьи,
     *         false, если хотя бы одна отправка не удалась
     * @throws IllegalArgumentException если event или event.family равны null
     */
    private boolean sendNotificationToFamily(Event event) {
        if (event == null) {
            log.error("Попытка отправить уведомление для null события");
            throw new IllegalArgumentException("Event не может быть null");
        }
        
        Family family = event.getFamily();
        if (family == null) {
            log.error("Событие не имеет семьи: eventId={}", event.getId());
            throw new IllegalArgumentException("Event должен иметь семью");
        }
        
        String message = formatNotificationMessage(event);
        List<User> members = family.getMembers();
        
        if (members == null || members.isEmpty()) {
            log.warn("Семья не имеет членов: familyId={}, eventId={}", 
                    family.getId(), event.getId());
            return false;
        }
        
        log.debug("Отправка уведомления {} членам семьи: familyId={}, eventId={}", 
                members.size(), family.getId(), event.getId());
        
        boolean allSent = true;
        int sentCount = 0;
        
        for (User user : members) {
            try {
                if (user.getTelegramId() == null) {
                    log.warn("Пользователь не имеет Telegram ID: userId={}", user.getId());
                    allSent = false;
                    continue;
                }
                
                log.debug("Отправка уведомления пользователю: userId={}, telegramId={}", 
                        user.getId(), user.getTelegramId());
                
                messageService.sendMessage(user.getTelegramId(), message);
                sentCount++;
                
                log.debug("Уведомление успешно отправлено пользователю: userId={}, telegramId={}", 
                        user.getId(), user.getTelegramId());
                
            } catch (TelegramApiException e) {
                allSent = false;
                log.error("Не удалось отправить уведомление пользователю: userId={}, telegramId={}, error={}", 
                        user.getId(), user.getTelegramId(), e.getMessage(), e);
            } catch (Exception e) {
                allSent = false;
                log.error("Неожиданная ошибка при отправке уведомления пользователю: userId={}, error={}", 
                        user.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Уведомление отправлено {}/{} членам семьи: familyId={}, eventId={}", 
                sentCount, members.size(), family.getId(), event.getId());
        
        return allSent;
    }

    /**
     * Форматирует текст уведомления о событии.
     * 
     * <p>Создает красиво отформатированное сообщение с использованием
     * Markdown и эмодзи для улучшения читаемости.</p>
     * 
     * <p>Включает следующую информацию:</p>
     * <ul>
     *   <li>Дата события (формат: dd.MM.yyyy)</li>
     *   <li>Время события (формат: HH:mm)</li>
     *   <li>Название события</li>
     *   <li>Описание события (если есть)</li>
     *   <li>Имя создателя события</li>
     * </ul>
     * 
     * @param event событие для форматирования
     * @return отформатированный текст уведомления с Markdown разметкой
     * @throws IllegalArgumentException если event равен null
     */
    private String formatNotificationMessage(Event event) {
        if (event == null) {
            log.error("Попытка форматировать уведомление для null события");
            throw new IllegalArgumentException("Event не может быть null");
        }
        
        StringBuilder message = new StringBuilder();
        message.append("🔔 ").append(bold("Напоминание о событии")).append("\n\n");
        message.append("📅 ").append(bold("Дата:")).append(" ").append(escape(event.getFormattedDate())).append("\n");
        message.append("🕐 ").append(bold("Время:")).append(" ").append(escape(event.getFormattedTime())).append("\n");
        message.append("📝 ").append(bold("Название:")).append(" ").append(escape(event.getTitle())).append("\n");
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            message.append("📄 ").append(bold("Описание:")).append(" ").append(italic(event.getDescription())).append("\n");
        }
        
        if (event.getUser() != null) {
            message.append("👤 ").append(bold("Создал:")).append(" ").append(escape(event.getUser().getFullName()));
        }
        
        return message.toString();
    }

    /**
     * Отмечает событие как notified в базе данных.
     * 
     * <p>После успешной отправки уведомления всем членам семьи,
     * событие помечается флагом notified = true, чтобы избежать
     * повторной отправки уведомлений.</p>
     * 
     * <p>Метод выполняется в рамках транзакции, чтобы гарантировать
     * консистентность данных.</p>
     * 
     * @param event событие для отметки
     * @throws IllegalArgumentException если event равен null
     */
    private void markAsNotified(Event event) {
        if (event == null) {
            log.error("Попытка отметить null событие как notified");
            throw new IllegalArgumentException("Event не может быть null");
        }
        
        log.debug("Отметка события как notified: eventId={}", event.getId());
        
        try {
            event.setNotified(true);
            eventRepository.save(event);
            
            log.info("Событие успешно отмечено как notified: eventId={}", event.getId());
            
        } catch (Exception e) {
            log.error("Ошибка при отметке события как notified: eventId={}, error={}", 
                    event.getId(), e.getMessage(), e);
            throw e;
        }
    }
}
