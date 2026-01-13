package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Reminder;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.ReminderRepository;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Сервис для управления напоминаниями о событиях.
 * Предоставляет функциональность для создания напоминаний с различными типами
 * и автоматической отправки уведомлений по расписанию.
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Создание стандартных напоминаний (утром, вечером, за час, за 10 минут)</li>
 *   <li>Создание кастомных напоминаний с произвольным временем</li>
 *   <li>Автоматическая отправка напоминаний по расписанию (каждую минуту)</li>
 *   <li>Отправка напоминаний только создателю для персональных событий</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 26.6</p>
 * 
 * @author Family Calendar Bot
 * @version 1.0
 * @see Reminder
 * @see ReminderRepository
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ReminderService {
    
    private final ReminderRepository reminderRepository;
    private final EventRepository eventRepository;
    private final TelegramMessageService telegramMessageService;
    
    /**
     * Создает стандартные напоминания для события.
     * 
     * <p>Создает напоминания указанных типов с автоматическим расчетом времени отправки.</p>
     * 
     * @param eventId идентификатор события
     * @param reminderTypes список типов напоминаний для создания
     * @return список созданных напоминаний
     * @throws EventNotFoundException если событие не найдено
     * @throws IllegalArgumentException если список типов пустой или событие не имеет даты/времени
     */
    public List<Reminder> createReminders(Long eventId, List<Reminder.ReminderType> reminderTypes) {
        log.debug("Создание напоминаний для события ID {}: типы={}", eventId, reminderTypes);
        
        if (reminderTypes == null || reminderTypes.isEmpty()) {
            log.error("Попытка создать пустой список напоминаний для события ID {}", eventId);
            throw new IllegalArgumentException("Список типов напоминаний не может быть пустым");
        }
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        if (event.getEventDate() == null || event.getEventTime() == null) {
            log.error("Попытка создать напоминания для события ID {} без даты или времени", eventId);
            throw new IllegalArgumentException("Событие должно иметь дату и время для создания напоминаний");
        }
        
        List<Reminder> reminders = new ArrayList<>();
        
        for (Reminder.ReminderType type : reminderTypes) {
            LocalDateTime reminderTime = calculateReminderTime(event, type, null);
            
            // Не создавать напоминания для прошедшего времени
            if (reminderTime.isBefore(LocalDateTime.now())) {
                log.warn("Пропуск создания напоминания типа {} для события ID {}: время в прошлом", 
                        type, eventId);
                continue;
            }
            
            Reminder reminder = Reminder.builder()
                .event(event)
                .reminderType(type)
                .reminderTime(reminderTime)
                .sent(false)
                .build();
            
            reminders.add(reminder);
        }
        
        if (reminders.isEmpty()) {
            log.warn("Не создано ни одного напоминания для события ID {} (все времена в прошлом)", eventId);
            return reminders;
        }
        
        List<Reminder> saved = reminderRepository.saveAll(reminders);
        log.info("Создано {} напоминаний для события ID {}", saved.size(), eventId);
        
        return saved;
    }
    
    /**
     * Создает кастомное напоминание с произвольным временем.
     * 
     * <p>Позволяет указать точное количество минут до события для отправки напоминания.</p>
     * 
     * @param eventId идентификатор события
     * @param minutesBefore количество минут до события
     * @return созданное напоминание
     * @throws EventNotFoundException если событие не найдено
     * @throws IllegalArgumentException если minutesBefore некорректно или событие не имеет даты/времени
     */
    public Reminder createCustomReminder(Long eventId, int minutesBefore) {
        log.debug("Создание кастомного напоминания для события ID {}: за {} минут", eventId, minutesBefore);
        
        if (minutesBefore < 1) {
            log.error("Некорректное количество минут для напоминания: {}", minutesBefore);
            throw new IllegalArgumentException("Количество минут должно быть >= 1");
        }
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        if (event.getEventDate() == null || event.getEventTime() == null) {
            log.error("Попытка создать напоминание для события ID {} без даты или времени", eventId);
            throw new IllegalArgumentException("Событие должно иметь дату и время для создания напоминания");
        }
        
        LocalDateTime reminderTime = calculateReminderTime(event, Reminder.ReminderType.CUSTOM, minutesBefore);
        
        // Не создавать напоминание для прошедшего времени
        if (reminderTime.isBefore(LocalDateTime.now())) {
            log.error("Попытка создать напоминание для прошедшего времени: eventId={}, reminderTime={}", 
                     eventId, reminderTime);
            throw new IllegalArgumentException("Время напоминания не может быть в прошлом");
        }
        
        Reminder reminder = Reminder.builder()
            .event(event)
            .reminderType(Reminder.ReminderType.CUSTOM)
            .customMinutes(minutesBefore)
            .reminderTime(reminderTime)
            .sent(false)
            .build();
        
        Reminder saved = reminderRepository.save(reminder);
        log.info("Кастомное напоминание ID {} создано для события ID {} (за {} минут)", 
                 saved.getId(), eventId, minutesBefore);
        
        return saved;
    }
    
    /**
     * Автоматически отправляет напоминания по расписанию.
     * 
     * <p>Выполняется каждую минуту. Находит все неотправленные напоминания,
     * время которых наступило, и отправляет уведомления.</p>
     * 
     * <p>Фильтрует напоминания:</p>
     * <ul>
     *   <li>Пропускает напоминания для событий в прошлом (event_date < now)</li>
     *   <li>Пропускает напоминания для удаленных событий (status = DELETED)</li>
     *   <li>Пропускает напоминания с reminder_time < now - 1 час</li>
     * </ul>
     * 
     * <p>Для персональных событий напоминание отправляется только создателю.
     * Для семейных событий - всем членам семьи.</p>
     * 
     * <p>Расписание: каждую минуту</p>
     * 
     * <p><b>Требования:</b> 4.2, 4.3, 4.4, 4.5, 6.1, 6.2, 6.3, 6.4, 6.5, 11.1, 11.3, 11.4</p>
     */
    @Scheduled(fixedRate = 60000) // Каждую минуту
    public void sendReminders() {
        log.debug("Запуск отправки напоминаний");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = now.plusMinutes(1);
        LocalDateTime oneHourAgo = now.minusHours(1);
        
        List<Reminder> reminders = reminderRepository.findBySentFalseAndReminderTimeBetween(now, windowEnd);
        
        if (reminders.isEmpty()) {
            log.debug("Нет напоминаний для отправки");
            return;
        }
        
        log.info("Найдено {} напоминаний для проверки", reminders.size());
        
        int sentCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        
        for (Reminder reminder : reminders) {
            try {
                Event event = reminder.getEvent();
                
                // Фильтр 1: Пропускаем напоминания для событий в прошлом
                if (event.getEventDate().isBefore(now.toLocalDate())) {
                    log.debug("Пропуск напоминания ID={}: событие в прошлом (дата={})", 
                             reminder.getId(), event.getEventDate());
                    skippedCount++;
                    continue;
                }
                
                // Фильтр 2: Пропускаем напоминания для удаленных событий
                if (event.getStatus() == Event.EventStatus.DELETED) {
                    log.debug("Пропуск напоминания ID={}: событие удалено", reminder.getId());
                    skippedCount++;
                    continue;
                }
                
                // Фильтр 3: Пропускаем старые напоминания (более 1 часа в прошлом)
                if (reminder.getReminderTime().isBefore(oneHourAgo)) {
                    log.warn("Пропуск старого напоминания ID={}: время={}", 
                            reminder.getId(), reminder.getReminderTime());
                    // Отмечаем как отправленное, чтобы не пытаться снова
                    reminder.setSent(true);
                    reminder.setSentAt(now);
                    reminderRepository.save(reminder);
                    skippedCount++;
                    continue;
                }
                
                // Отправляем напоминание
                sendReminderNotification(reminder);
                
                // Отметить как отправленное
                reminder.setSent(true);
                reminder.setSentAt(now);
                reminderRepository.save(reminder);
                
                sentCount++;
                log.info("Напоминание успешно отправлено: eventId={}, reminderId={}, reminderType={}, userId={}", 
                        event.getId(), reminder.getId(), reminder.getReminderType(), 
                        event.getIsPersonal() ? event.getUser().getId() : "семья");
                
            } catch (Exception e) {
                failedCount++;
                log.error("Ошибка при отправке напоминания ID {}: {}", reminder.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Отправка напоминаний завершена: успешно={}, ошибок={}, пропущено={}", 
                sentCount, failedCount, skippedCount);
    }
    
    /**
     * Рассчитывает время отправки напоминания на основе типа.
     * 
     * @param event событие
     * @param type тип напоминания
     * @param customMinutes количество минут для CUSTOM типа (может быть null для других типов)
     * @return рассчитанное время отправки напоминания
     */
    private LocalDateTime calculateReminderTime(Event event, Reminder.ReminderType type, Integer customMinutes) {
        LocalDateTime eventDateTime = LocalDateTime.of(event.getEventDate(), event.getEventTime());
        
        switch (type) {
            case MORNING_OF_DAY:
                // Утром в день события в 9:00
                return LocalDateTime.of(event.getEventDate(), LocalTime.of(9, 0));
                
            case EVENING_BEFORE:
                // Вечером накануне в 20:00
                return LocalDateTime.of(event.getEventDate().minusDays(1), LocalTime.of(20, 0));
                
            case ONE_HOUR_BEFORE:
                // За 1 час до события
                return eventDateTime.minusHours(1);
                
            case TEN_MINUTES_BEFORE:
                // За 10 минут до события
                return eventDateTime.minusMinutes(10);
                
            case CUSTOM:
                // За указанное количество минут до события
                if (customMinutes == null || customMinutes < 1) {
                    throw new IllegalArgumentException("Для CUSTOM типа необходимо указать customMinutes >= 1");
                }
                return eventDateTime.minusMinutes(customMinutes);
                
            default:
                throw new IllegalArgumentException("Неподдерживаемый тип напоминания: " + type);
        }
    }
    
    /**
     * Отправляет уведомление о напоминании.
     * 
     * <p>Для персональных событий отправляет только создателю.
     * Для семейных событий отправляет всем членам семьи.</p>
     * 
     * <p>Включает inline-кнопки для взаимодействия с событием:</p>
     * <ul>
     *   <li>📋 Посмотреть детали - для всех пользователей</li>
     *   <li>✏️ Редактировать - только для создателя события</li>
     *   <li>🗑️ Удалить - только для создателя события</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 8.1, 8.3, 8.4, 8.5</p>
     * 
     * @param reminder напоминание для отправки
     * @throws TelegramApiException если не удалось отправить уведомление
     */
    private void sendReminderNotification(Reminder reminder) throws TelegramApiException {
        Event event = reminder.getEvent();
        String message = formatReminderMessage(reminder);
        
        if (event.getIsPersonal()) {
            // Персональное событие - отправить только создателю
            log.debug("Отправка напоминания о персональном событии ID {} создателю ID {}", 
                     event.getId(), event.getUser().getId());
            
            var keyboard = createReminderKeyboard(event, event.getUser().getId());
            telegramMessageService.sendMessageWithInlineKeyboard(event.getUser().getTelegramId(), message, keyboard);
            
        } else {
            // Семейное событие - отправить всем членам семьи
            log.debug("Отправка напоминания о семейном событии ID {} всем членам семьи", event.getId());
            
            if (event.getFamily() != null && event.getFamily().getMembers() != null) {
                for (User member : event.getFamily().getMembers()) {
                    try {
                        var keyboard = createReminderKeyboard(event, member.getId());
                        telegramMessageService.sendMessageWithInlineKeyboard(member.getTelegramId(), message, keyboard);
                    } catch (TelegramApiException e) {
                        log.error("Ошибка при отправке напоминания пользователю ID {} (telegramId={}): {}", 
                                 member.getId(), member.getTelegramId(), e.getMessage());
                        // Продолжаем отправку остальным членам семьи
                    }
                }
            }
        }
    }
    
    /**
     * Создает inline-клавиатуру для уведомления о напоминании.
     * 
     * <p>Включает кнопки:</p>
     * <ul>
     *   <li>📋 Посмотреть детали - для всех пользователей</li>
     *   <li>✏️ Редактировать - только для создателя события</li>
     *   <li>🗑️ Удалить - только для создателя события</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 8.1, 8.3, 8.4, 8.5</p>
     * 
     * @param event событие
     * @param userId идентификатор пользователя, которому отправляется уведомление
     * @return inline-клавиатура
     */
    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup createReminderKeyboard(
            Event event, Long userId) {
        
        var keyboard = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        var rows = new java.util.ArrayList<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>>();
        
        // Первая строка: кнопка "Посмотреть детали" (для всех)
        var viewButton = org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text("📋 Посмотреть детали")
            .callbackData("view_event_" + event.getId())
            .build();
        rows.add(java.util.List.of(viewButton));
        
        // Вторая строка: кнопки "Редактировать" и "Удалить" (только для создателя)
        if (event.getUser().getId().equals(userId)) {
            var editButton = org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text("✏️ Редактировать")
                .callbackData("edit_event_" + event.getId())
                .build();
            
            var deleteButton = org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text("🗑️ Удалить")
                .callbackData("delete_event_" + event.getId())
                .build();
            
            rows.add(java.util.List.of(editButton, deleteButton));
        }
        
        keyboard.setKeyboard(rows);
        return keyboard;
    }
    
    /**
     * Форматирует текст уведомления о напоминании.
     * 
     * <p>Включает:</p>
     * <ul>
     *   <li>Эмодзи 🔔 в начале</li>
     *   <li>Название события</li>
     *   <li>Дату и время (включая время окончания если указано)</li>
     *   <li>Описание события (обрезанное до 100 символов)</li>
     *   <li>Маркер персонального события (🔒) или семейного (👨‍👩‍👧‍👦 с именем создателя)</li>
     *   <li>Тип напоминания</li>
     * </ul>
     * 
     * <p>Использует Markdown форматирование для улучшения читаемости.</p>
     * 
     * <p><b>Требования:</b> 5.1, 5.2, 5.3, 5.4, 5.5, 5.6</p>
     * 
     * @param reminder напоминание
     * @return отформатированное сообщение
     */
    private String formatReminderMessage(Reminder reminder) {
        Event event = reminder.getEvent();
        String timeInfo = getReminderTimeInfo(reminder);
        
        StringBuilder message = new StringBuilder();
        
        // Эмодзи в начале (Требование 5.1)
        message.append("🔔 ").append(bold("Напоминание о событии")).append("\n\n");
        
        // Название события (Требование 5.2)
        message.append(formatMessage("📅 Событие: %s\n", event.getTitle()));
        
        // Дата и время (Требование 5.2)
        message.append(formatMessage("🕐 Дата: %s\n", event.getFormattedDate()));
        
        if (event.getEventTime() != null) {
            if (event.getEndTime() != null) {
                // Временной интервал
                message.append(formatMessage("⏰ Время: %s - %s\n", 
                    event.getFormattedTime(), 
                    event.getEndTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))));
            } else {
                message.append(formatMessage("⏰ Время: %s\n", event.getFormattedTime()));
            }
        }
        
        // Описание события (Требование 5.3)
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            String truncatedDesc = event.getDescription().length() > 100 
                ? event.getDescription().substring(0, 100) + "..." 
                : event.getDescription();
            message.append(formatMessage("📝 Описание: %s\n", truncatedDesc));
        }
        
        message.append("\n");
        
        // Маркер типа события (Требования 5.4, 5.5)
        if (event.getIsPersonal()) {
            message.append("🔒 ").append(bold("Персональное событие")).append("\n");
        } else {
            message.append(formatMessage("👨‍👩‍👧‍👦 Семейное событие (создал: %s)\n", 
                event.getUser().getFirstName()));
        }
        
        // Тип напоминания
        message.append(formatMessage("\n⏱ %s", timeInfo));
        
        return message.toString();
    }
    
    /**
     * Возвращает текстовое описание времени напоминания.
     * 
     * @param reminder напоминание
     * @return описание времени
     */
    private String getReminderTimeInfo(Reminder reminder) {
        switch (reminder.getReminderType()) {
            case MORNING_OF_DAY:
                return "Напоминание: утром в день события";
            case EVENING_BEFORE:
                return "Напоминание: вечером накануне";
            case ONE_HOUR_BEFORE:
                return "Напоминание: за 1 час до события";
            case TEN_MINUTES_BEFORE:
                return "Напоминание: за 10 минут до события";
            case CUSTOM:
                return formatMessage("Напоминание: за %d минут до события", reminder.getCustomMinutes());
            default:
                return "Напоминание о событии";
        }
    }
    
    /**
     * Получает все напоминания для указанного события.
     * 
     * @param eventId идентификатор события
     * @return список всех напоминаний события
     */
    @Transactional(readOnly = true)
    public List<Reminder> getEventReminders(Long eventId) {
        log.debug("Получение напоминаний для события ID {}", eventId);
        return reminderRepository.findByEventId(eventId);
    }
    
    /**
     * Удаляет напоминание по идентификатору.
     * 
     * @param reminderId идентификатор напоминания
     * @throws IllegalArgumentException если напоминание не найдено
     */
    public void deleteReminder(Long reminderId) {
        log.debug("Удаление напоминания ID {}", reminderId);
        
        if (!reminderRepository.existsById(reminderId)) {
            log.error("Попытка удалить несуществующее напоминание ID {}", reminderId);
            throw new IllegalArgumentException("Напоминание с ID " + reminderId + " не найдено");
        }
        
        reminderRepository.deleteById(reminderId);
        log.info("Напоминание ID {} удалено", reminderId);
    }
    
    /**
     * Пересчитывает время отправки для всех неотправленных напоминаний события.
     * Используется при изменении даты или времени события.
     * 
     * @param eventId идентификатор события
     * @throws EventNotFoundException если событие не найдено
     */
    public void recalculateReminders(Long eventId) {
        log.debug("Пересчет напоминаний для события ID {}", eventId);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        if (event.getEventDate() == null || event.getEventTime() == null) {
            log.warn("Невозможно пересчитать напоминания для события ID {} без даты или времени", eventId);
            return;
        }
        
        List<Reminder> reminders = reminderRepository.findByEventIdAndSentFalse(eventId);
        
        if (reminders.isEmpty()) {
            log.debug("Нет неотправленных напоминаний для пересчета для события ID {}", eventId);
            return;
        }
        
        int recalculatedCount = 0;
        for (Reminder reminder : reminders) {
            try {
                LocalDateTime newReminderTime = calculateReminderTime(
                    event, 
                    reminder.getReminderType(), 
                    reminder.getCustomMinutes()
                );
                
                // Пропускаем напоминания, время которых оказалось в прошлом
                if (newReminderTime.isBefore(LocalDateTime.now())) {
                    log.warn("Пропуск пересчета напоминания ID {} для события ID {}: новое время в прошлом", 
                            reminder.getId(), eventId);
                    continue;
                }
                
                reminder.setReminderTime(newReminderTime);
                reminderRepository.save(reminder);
                recalculatedCount++;
                
            } catch (Exception e) {
                log.error("Ошибка при пересчете напоминания ID {}: {}", 
                         reminder.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Пересчитано {} напоминаний для события ID {}", recalculatedCount, eventId);
    }
    
    /**
     * Отмечает все неотправленные напоминания события как отправленные.
     * Используется при завершении события.
     * 
     * @param eventId идентификатор события
     */
    public void markRemindersAsSent(Long eventId) {
        log.debug("Отметка напоминаний как отправленных для события ID {}", eventId);
        
        List<Reminder> reminders = reminderRepository.findByEventIdAndSentFalse(eventId);
        
        if (reminders.isEmpty()) {
            log.debug("Нет неотправленных напоминаний для события ID {}", eventId);
            return;
        }
        
        LocalDateTime now = LocalDateTime.now();
        for (Reminder reminder : reminders) {
            reminder.setSent(true);
            reminder.setSentAt(now);
        }
        
        reminderRepository.saveAll(reminders);
        log.info("Отмечено {} напоминаний как отправленных для события ID {}", reminders.size(), eventId);
    }
    
    /**
     * Проверяет наличие активных (неотправленных) напоминаний для события.
     * 
     * @param eventId идентификатор события
     * @return true если есть хотя бы одно неотправленное напоминание, иначе false
     */
    @Transactional(readOnly = true)
    public boolean hasActiveReminders(Long eventId) {
        List<Reminder> reminders = reminderRepository.findByEventIdAndSentFalse(eventId);
        return !reminders.isEmpty();
    }
}
