package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Reminder;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Сервис-фасад для управления напоминаниями о событиях.
 * Делегирует вызовы в специализированные сервисы для обеспечения обратной совместимости.
 * 
 * <p><b>ВАЖНО:</b> Этот класс является фасадом для обратной совместимости.
 * Для новых функций используйте специализированные сервисы напрямую:</p>
 * <ul>
 *   <li>{@link ReminderCreationService} - создание и удаление напоминаний</li>
 *   <li>{@link ReminderSchedulingService} - планирование и получение напоминаний</li>
 *   <li>{@link ReminderNotificationService} - отправка уведомлений</li>
 *   <li>{@link ReminderConfigurationService} - настройка типов напоминаний</li>
 * </ul>
 * 
 * @author Family Calendar Bot
 * @since 2026-02-02
 * @deprecated Используйте специализированные сервисы напрямую
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Deprecated
public class ReminderService {
    
    private final ReminderCreationService reminderCreationService;
    private final ReminderSchedulingService reminderSchedulingService;
    private final ReminderNotificationService reminderNotificationService;
    private final ReminderConfigurationService reminderConfigurationService;
    
    // ==================== МЕТОДЫ СОЗДАНИЯ (делегирование в ReminderCreationService) ====================
    
    /**
     * Создает стандартные напоминания для события.
     * 
     * @deprecated Используйте {@link ReminderCreationService#createReminders(Long, List)}
     */
    @Deprecated
    @Transactional
    public List<Reminder> createReminders(Long eventId, List<Reminder.ReminderType> reminderTypes) {
        log.debug("Делегирование createReminders в ReminderCreationService: eventId={}", eventId);
        return reminderCreationService.createReminders(eventId, reminderTypes);
    }
    
    /**
     * Создает автоматические напоминания по умолчанию для события.
     * 
     * @deprecated Используйте {@link ReminderCreationService#createDefaultReminders(Event, User)}
     */
    @Deprecated
    @Transactional
    public List<Reminder> createDefaultReminders(Event event, User user) {
        log.debug("Делегирование createDefaultReminders в ReminderCreationService: eventId={}", event.getId());
        return reminderCreationService.createDefaultReminders(event, user);
    }
    
    /**
     * Создает кастомное напоминание с произвольным временем.
     * 
     * @deprecated Используйте {@link ReminderCreationService#createCustomReminder(Long, int)}
     */
    @Deprecated
    public Reminder createCustomReminder(Long eventId, int minutesBefore) {
        log.debug("Делегирование createCustomReminder в ReminderCreationService: eventId={}", eventId);
        return reminderCreationService.createCustomReminder(eventId, minutesBefore);
    }
    
    /**
     * Удаляет напоминание по идентификатору.
     * 
     * @deprecated Используйте {@link ReminderCreationService#deleteReminder(Long)}
     */
    @Deprecated
    @Transactional
    public void deleteReminder(Long reminderId) {
        log.debug("Делегирование deleteReminder в ReminderCreationService: reminderId={}", reminderId);
        reminderCreationService.deleteReminder(reminderId);
    }
    
    /**
     * Отключает все автоматические напоминания для события.
     * 
     * @deprecated Используйте {@link ReminderCreationService#disableRemindersForEvent(Long)}
     */
    @Deprecated
    @Transactional
    public void disableRemindersForEvent(Long eventId) {
        log.debug("Делегирование disableRemindersForEvent в ReminderCreationService: eventId={}", eventId);
        reminderCreationService.disableRemindersForEvent(eventId);
    }
    
    // ==================== МЕТОДЫ ПЛАНИРОВАНИЯ (делегирование в ReminderSchedulingService) ====================
    
    /**
     * Пересчитывает время отправки для всех неотправленных напоминаний события.
     * 
     * @deprecated Используйте {@link ReminderSchedulingService#recalculateReminders(Long)}
     */
    @Deprecated
    @Transactional
    public void recalculateReminders(Long eventId) {
        log.debug("Делегирование recalculateReminders в ReminderSchedulingService: eventId={}", eventId);
        reminderSchedulingService.recalculateReminders(eventId);
    }
    
    /**
     * Отмечает все неотправленные напоминания как отправленные.
     * 
     * @deprecated Используйте {@link ReminderSchedulingService#markRemindersAsSent(Long)}
     */
    @Deprecated
    @Transactional
    public void markRemindersAsSent(Long eventId) {
        log.debug("Делегирование markRemindersAsSent в ReminderSchedulingService: eventId={}", eventId);
        reminderSchedulingService.markRemindersAsSent(eventId);
    }
    
    /**
     * Проверяет наличие активных (неотправленных) напоминаний для события.
     * 
     * @deprecated Используйте {@link ReminderSchedulingService#hasActiveReminders(Long)}
     */
    @Deprecated
    @Transactional(readOnly = true)
    public boolean hasActiveReminders(Long eventId) {
        log.debug("Делегирование hasActiveReminders в ReminderSchedulingService: eventId={}", eventId);
        return reminderSchedulingService.hasActiveReminders(eventId);
    }
    
    /**
     * Получает все напоминания для указанного события.
     * 
     * @deprecated Используйте {@link ReminderSchedulingService#getEventReminders(Long)}
     */
    @Deprecated
    @Transactional(readOnly = true)
    public List<Reminder> getEventReminders(Long eventId) {
        log.debug("Делегирование getEventReminders в ReminderSchedulingService: eventId={}", eventId);
        return reminderSchedulingService.getEventReminders(eventId);
    }
    
    /**
     * Получает напоминание по идентификатору.
     * 
     * @deprecated Используйте {@link ReminderSchedulingService#getReminderById(Long)}
     */
    @Deprecated
    @Transactional(readOnly = true)
    public Reminder getReminderById(Long reminderId) {
        log.debug("Делегирование getReminderById в ReminderSchedulingService: reminderId={}", reminderId);
        return reminderSchedulingService.getReminderById(reminderId);
    }
    
    /**
     * Получает напоминание по идентификатору с eager загрузкой связанного события.
     * 
     * @deprecated Используйте {@link ReminderSchedulingService#getReminderWithEventById(Long)}
     */
    @Deprecated
    @Transactional(readOnly = true)
    public Reminder getReminderWithEventById(Long reminderId) {
        log.debug("Делегирование getReminderWithEventById в ReminderSchedulingService: reminderId={}", reminderId);
        return reminderSchedulingService.getReminderWithEventById(reminderId);
    }
    
    /**
     * Получает напоминание по идентификатору с eager загрузкой события и пользователя.
     * 
     * @deprecated Используйте {@link ReminderSchedulingService#getReminderWithEventAndUser(Long)}
     */
    @Deprecated
    @Transactional(readOnly = true)
    public Reminder getReminderWithEventAndUser(Long reminderId) {
        log.debug("Делегирование getReminderWithEventAndUser в ReminderSchedulingService: reminderId={}", reminderId);
        return reminderSchedulingService.getReminderWithEventAndUser(reminderId);
    }
    
    // ==================== МЕТОДЫ УВЕДОМЛЕНИЙ (делегирование в ReminderNotificationService) ====================
    
    /**
     * Автоматически отправляет напоминания по расписанию.
     * 
     * @deprecated Используйте {@link ReminderNotificationService#sendReminders()}
     */
    @Deprecated
    @Transactional
    public void sendReminders() {
        log.debug("Делегирование sendReminders в ReminderNotificationService");
        reminderNotificationService.sendReminders();
    }
    
    /**
     * Форматирует короткую версию уведомления о напоминании.
     * 
     * @deprecated Используйте {@link ReminderNotificationService#formatShortReminderMessage(Reminder, ZoneId)}
     */
    @Deprecated
    public String formatShortReminderMessage(Reminder reminder, ZoneId recipientTimezone) {
        log.debug("Делегирование formatShortReminderMessage в ReminderNotificationService: reminderId={}", 
                 reminder.getId());
        return reminderNotificationService.formatShortReminderMessage(reminder, recipientTimezone);
    }
    
    /**
     * Форматирует полную версию уведомления о напоминании.
     * 
     * @deprecated Используйте {@link ReminderNotificationService#formatReminderMessageByType(Reminder, ZoneId)}
     */
    @Deprecated
    public String formatReminderMessageByType(Reminder reminder, ZoneId recipientTimezone) {
        log.debug("Делегирование formatReminderMessageByType в ReminderNotificationService: reminderId={}", 
                 reminder.getId());
        return reminderNotificationService.formatReminderMessageByType(reminder, recipientTimezone);
    }
    
    /**
     * Создает упрощенную клавиатуру для уведомления о напоминании.
     * 
     * @deprecated Используйте {@link ReminderNotificationService#createSimplifiedReminderKeyboard(Event, Long)}
     */
    @Deprecated
    public InlineKeyboardMarkup createSimplifiedReminderKeyboard(Event event, Long reminderId) {
        log.debug("Делегирование createSimplifiedReminderKeyboard в ReminderNotificationService: eventId={}, reminderId={}", 
                 event.getId(), reminderId);
        return reminderNotificationService.createSimplifiedReminderKeyboard(event, reminderId);
    }
    
    // ==================== МЕТОДЫ КОНФИГУРАЦИИ (делегирование в ReminderConfigurationService) ====================
    
    /**
     * Рассчитывает время отправки напоминания на основе типа.
     * 
     * @deprecated Используйте {@link ReminderConfigurationService#calculateReminderTime(Event, Reminder.ReminderType, Integer)}
     */
    @Deprecated
    public LocalDateTime calculateReminderTime(Event event, Reminder.ReminderType type, Integer customMinutes) {
        log.debug("Делегирование calculateReminderTime в ReminderConfigurationService: eventId={}, type={}", 
                 event.getId(), type);
        return reminderConfigurationService.calculateReminderTime(event, type, customMinutes);
    }
    
    /**
     * Рассчитывает время отправки напоминания с учетом часового пояса пользователя.
     * 
     * @deprecated Используйте {@link ReminderConfigurationService#calculateReminderTimeWithTimezone(Event, Reminder.ReminderType, ZoneId, Integer)}
     */
    @Deprecated
    public LocalDateTime calculateReminderTimeWithTimezone(Event event, Reminder.ReminderType type, 
                                                          ZoneId userTimezone, Integer customMinutes) {
        log.debug("Делегирование calculateReminderTimeWithTimezone в ReminderConfigurationService: eventId={}, type={}", 
                 event.getId(), type);
        return reminderConfigurationService.calculateReminderTimeWithTimezone(event, type, userTimezone, customMinutes);
    }
    
    /**
     * Получает часовой пояс пользователя с обработкой ошибок и fallback на UTC.
     * 
     * @deprecated Используйте {@link ReminderConfigurationService#getUserTimezone(User)}
     */
    @Deprecated
    public ZoneId getUserTimezone(User user) {
        log.debug("Делегирование getUserTimezone в ReminderConfigurationService: userId={}", 
                 user != null ? user.getId() : null);
        return reminderConfigurationService.getUserTimezone(user);
    }
}
