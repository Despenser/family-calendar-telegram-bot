package ru.golubyatnikov.family.calendar.bot.service.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Reminder;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.model.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.ReminderRepository;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Сервис для отправки уведомлений о напоминаниях.
 * Отвечает за автоматическую отправку напоминаний по расписанию.
 * 
 * @author Family Calendar Bot
 * @since 2026-02-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReminderNotificationService {
    
    private final ReminderRepository reminderRepository;
    private final TelegramMessageService telegramMessageService;
    private final ReminderConfigurationService reminderConfigurationService;
    
    /**
     * Автоматически отправляет напоминания по расписанию.
     * 
     * <p><b>ВАЖНО:</b> Этот метод НЕ должен иметь аннотацию @Scheduled.
     * Он вызывается только из ReminderScheduler.checkAndSendReminders(),
     * который имеет единственную аннотацию @Scheduled для отправки напоминаний.</p>
     */
    @Transactional
    public void sendReminders() {
        // Получаем текущее время в UTC
        LocalDateTime nowUTC = LocalDateTime.now(ZoneId.of("UTC"));
        LocalDateTime oneHourAgo = nowUTC.minusHours(1);
        
        log.debug("Запуск отправки напоминаний: nowUTC={}, oneHourAgo={}", nowUTC, oneHourAgo);
        
        List<Reminder> reminders = reminderRepository.findBySentFalseAndReminderTimeLessThanEqualAndReminderTimeGreaterThanEqual(
            nowUTC, oneHourAgo
        );
        
        if (reminders.isEmpty()) {
            log.debug("Нет напоминаний для отправки в окне [{}, {}] UTC", oneHourAgo, nowUTC);
            return;
        }
        
        log.info("Найдено {} напоминаний для проверки в окне [{}, {}] UTC", 
                reminders.size(), oneHourAgo, nowUTC);
        
        int sentCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        int lockFailureCount = 0;
        int recoveredCount = 0;
        int markedAsOldCount = 0;
        
        for (Reminder reminder : reminders) {
            try {
                // Получаем блокировку на напоминание для предотвращения race conditions
                Reminder lockedReminder;
                try {
                    lockedReminder = reminderRepository.findByIdWithLock(reminder.getId())
                        .orElse(null);
                } catch (PessimisticLockingFailureException e) {
                    log.warn("Не удалось получить блокировку на напоминание ID {}, пропуск (обрабатывается другим процессом): {}", 
                            reminder.getId(), e.getMessage());
                    lockFailureCount++;
                    continue;
                }
                
                if (lockedReminder == null) {
                    log.warn("Напоминание ID {} не найдено при попытке получить блокировку", reminder.getId());
                    skippedCount++;
                    continue;
                }
                
                // Проверяем флаг sent после получения блокировки
                if (Boolean.TRUE.equals(lockedReminder.getSent())) {
                    log.debug("Напоминание ID {} уже отправлено другим процессом, пропуск", reminder.getId());
                    skippedCount++;
                    continue;
                }
                
                Event event = lockedReminder.getEvent();
                
                // Фильтр 1: Пропускаем напоминания для удаленных событий
                if (event.getStatus() == EventStatus.DELETED) {
                    log.debug("Пропуск напоминания ID={}: событие удалено", lockedReminder.getId());
                    skippedCount++;
                    continue;
                }
                
                // Фильтр 2: Пропускаем напоминания для событий в прошлом (с учетом UTC)
                if (!shouldSendReminder(lockedReminder, nowUTC)) {
                    skippedCount++;
                    continue;
                }
                
                // Проверяем, является ли это восстановлением после сбоя
                boolean isRecovery = lockedReminder.getReminderTime().isBefore(nowUTC.minusMinutes(2));
                if (isRecovery) {
                    recoveredCount++;
                    log.warn("Восстановление пропущенного напоминания: id={}, eventId={}, reminderTimeUTC={}, " +
                            "delayMinutes={}, nowUTC={}", 
                            lockedReminder.getId(), event.getId(), lockedReminder.getReminderTime(),
                            java.time.Duration.between(lockedReminder.getReminderTime(), nowUTC).toMinutes(),
                            nowUTC);
                }
                
                // Отправляем напоминание
                try {
                    sendReminderNotification(lockedReminder);
                    
                    // Атомарно обновляем флаг sent в той же транзакции
                    lockedReminder.setSent(true);
                    lockedReminder.setSentAt(nowUTC);
                    reminderRepository.save(lockedReminder);
                    
                    sentCount++;
                    
                    if (isRecovery) {
                        log.info("Восстановленное напоминание успешно отправлено: id={}, eventId={}, reminderType={}, " +
                                "reminderTimeUTC={}, sentAtUTC={}, delayMinutes={}", 
                                lockedReminder.getId(), event.getId(), lockedReminder.getReminderType(),
                                lockedReminder.getReminderTime(), nowUTC,
                                java.time.Duration.between(lockedReminder.getReminderTime(), nowUTC).toMinutes());
                    } else {
                        log.info("Напоминание отправлено: id={}, eventId={}, reminderType={}, reminderTimeUTC={}, sentAtUTC={}", 
                                lockedReminder.getId(), event.getId(), lockedReminder.getReminderType(),
                                lockedReminder.getReminderTime(), nowUTC);
                    }
                    
                } catch (TelegramApiException e) {
                    log.error("Ошибка отправки напоминания ID {}: {}", lockedReminder.getId(), e.getMessage(), e);
                    
                    // Если напоминание старше 1 часа, отмечаем как sent для предотвращения бесконечных попыток
                    if (lockedReminder.getReminderTime().isBefore(oneHourAgo)) {
                        log.warn("Напоминание ID {} старше 1 часа и не может быть отправлено, отмечаем как sent: " +
                                "reminderTimeUTC={}, nowUTC={}, ageMinutes={}", 
                                lockedReminder.getId(), lockedReminder.getReminderTime(), nowUTC,
                                java.time.Duration.between(lockedReminder.getReminderTime(), nowUTC).toMinutes());
                        
                        lockedReminder.setSent(true);
                        lockedReminder.setSentAt(nowUTC);
                        reminderRepository.save(lockedReminder);
                        markedAsOldCount++;
                    } else {
                        failedCount++;
                    }
                }
                
            } catch (PessimisticLockingFailureException e) {
                lockFailureCount++;
                log.warn("Не удалось получить блокировку на напоминание ID {} (race condition): {}", 
                        reminder.getId(), e.getMessage());
            } catch (Exception e) {
                failedCount++;
                log.error("Ошибка при обработке напоминания ID {}: {}", reminder.getId(), e.getMessage(), e);
            }
        }
        
        // Итоговое логирование с информацией о восстановлении
        if (recoveredCount > 0 || markedAsOldCount > 0) {
            log.info("Отправка напоминаний завершена: успешно={}, ошибок={}, пропущено={}, блокировок не получено={}, " +
                    "восстановлено после сбоя={}, отмечено как старые={}, nowUTC={}", 
                    sentCount, failedCount, skippedCount, lockFailureCount, recoveredCount, markedAsOldCount, nowUTC);
        } else {
            log.info("Отправка напоминаний завершена: успешно={}, ошибок={}, пропущено={}, блокировок не получено={}, nowUTC={}", 
                    sentCount, failedCount, skippedCount, lockFailureCount, nowUTC);
        }
    }
    
    /**
     * Проверяет, следует ли отправлять напоминание.
     * 
     * @param reminder напоминание для проверки
     * @param nowUTC текущее время в UTC
     * @return true если напоминание следует отправить, иначе false
     */
    private boolean shouldSendReminder(Reminder reminder, LocalDateTime nowUTC) {
        Event event = reminder.getEvent();
        
        log.debug("Проверка фильтров для напоминания ID {}: eventId={}, reminderType={}, reminderTimeUTC={}", 
                 reminder.getId(), event.getId(), reminder.getReminderType(), reminder.getReminderTime());
        
        // Фильтр 1: Событие не удалено
        if (event.getStatus() == EventStatus.DELETED) {
            log.debug("Пропуск напоминания ID {}: событие ID {} удалено (status=DELETED)", 
                     reminder.getId(), event.getId());
            return false;
        }
        
        // Фильтр 2: Событие не в прошлом (с учетом UTC)
        try {
            // Получаем timezone пользователя-создателя события
            ZoneId userTimezone = reminderConfigurationService.getUserTimezone(event.getUser());
            
            log.debug("Конвертация времени события в UTC для напоминания ID {}: " +
                     "eventId={}, eventDate={}, eventTime={}, userTimezone={}", 
                     reminder.getId(), event.getId(), event.getEventDate(), event.getEventTime(), userTimezone);
            
            // Конвертируем время события из User TZ в UTC для корректного сравнения
            ZonedDateTime eventZonedDateTime = ZonedDateTime.of(
                event.getEventDate(), 
                event.getEventTime(),
                userTimezone
            );
            
            LocalDateTime eventDateTimeUTC = eventZonedDateTime
                .withZoneSameInstant(ZoneId.of("UTC"))
                .toLocalDateTime();
            
            log.debug("Время события сконвертировано в UTC для напоминания ID {}: " +
                     "eventTimeUserTZ={}, eventTimeUTC={}, nowUTC={}", 
                     reminder.getId(), eventZonedDateTime.toLocalDateTime(), eventDateTimeUTC, nowUTC);
            
            // Сравниваем время события в UTC с текущим временем в UTC
            if (eventDateTimeUTC.isBefore(nowUTC)) {
                log.debug("Пропуск напоминания ID {}: событие ID {} в прошлом " +
                         "(eventTimeUTC={}, nowUTC={}, diffMinutes={})", 
                         reminder.getId(), event.getId(), eventDateTimeUTC, nowUTC,
                         java.time.Duration.between(eventDateTimeUTC, nowUTC).toMinutes());
                return false;
            }
            
            log.debug("Напоминание ID {} прошло все фильтры: eventId={}, eventTimeUTC={}, nowUTC={}, " +
                     "timeUntilEventMinutes={}", 
                     reminder.getId(), event.getId(), eventDateTimeUTC, nowUTC,
                     java.time.Duration.between(nowUTC, eventDateTimeUTC).toMinutes());
            
        } catch (java.time.DateTimeException e) {
            log.error("Ошибка DateTimeException при проверке времени события для напоминания ID {}: " +
                     "eventId={}, eventDate={}, eventTime={}, userTimezone={}, error={}", 
                     reminder.getId(), event.getId(), event.getEventDate(), event.getEventTime(),
                     event.getUser().getTimezone(), e.getMessage(), e);
            return false;
            
        } catch (Exception e) {
            log.error("Непредвиденная ошибка {} при проверке времени события для напоминания ID {}: " +
                     "eventId={}, eventDate={}, eventTime={}, userTimezone={}, error={}", 
                     e.getClass().getSimpleName(), reminder.getId(), event.getId(), 
                     event.getEventDate(), event.getEventTime(),
                     event.getUser().getTimezone(), e.getMessage(), e);
            return false;
        }
        
        return true;
    }
    
    /**
     * Отправляет уведомление о напоминании.
     * 
     * @param reminder напоминание для отправки
     * @throws TelegramApiException если не удалось отправить уведомление
     */
    private void sendReminderNotification(Reminder reminder) throws TelegramApiException {
        Event event = reminder.getEvent();
        
        if (event.getIsPersonal()) {
            // Персональное событие - отправить только создателю
            log.debug("Отправка напоминания о персональном событии ID {} создателю ID {}", 
                     event.getId(), event.getUser().getId());
            
            try {
                ZoneId recipientTimezone = reminderConfigurationService.getUserTimezone(event.getUser());
                log.debug("Форматирование короткого сообщения для пользователя ID {} в timezone {}", 
                         event.getUser().getId(), recipientTimezone);
                
                String message = formatShortReminderMessage(reminder, recipientTimezone);
                var keyboard = createReminderKeyboard(event, event.getUser().getId(), reminder.getId());
                telegramMessageService.sendMessageWithInlineKeyboard(event.getUser().getTelegramId(), message, keyboard);
                
            } catch (java.time.DateTimeException e) {
                log.error("Ошибка DateTimeException при форматировании/отправке напоминания пользователю ID {}: " +
                         "reminderId={}, eventId={}, userTimezone={}, error={}", 
                         event.getUser().getId(), reminder.getId(), event.getId(), 
                         event.getUser().getTimezone(), e.getMessage(), e);
                
                try {
                    log.warn("Fallback на UTC для пользователя ID {} после DateTimeException", event.getUser().getId());
                    String message = formatShortReminderMessage(reminder, ZoneId.of("UTC"));
                    var keyboard = createReminderKeyboard(event, event.getUser().getId(), reminder.getId());
                    telegramMessageService.sendMessageWithInlineKeyboard(event.getUser().getTelegramId(), message, keyboard);
                } catch (Exception fallbackError) {
                    log.error("Критическая ошибка {} при отправке напоминания пользователю ID {} даже с UTC: {}", 
                             fallbackError.getClass().getSimpleName(), event.getUser().getId(), 
                             fallbackError.getMessage(), fallbackError);
                    throw fallbackError;
                }
                
            } catch (TelegramApiException e) {
                log.error("Ошибка TelegramApiException при отправке напоминания пользователю ID {}: " +
                         "reminderId={}, eventId={}, telegramId={}, error={}", 
                         event.getUser().getId(), reminder.getId(), event.getId(), 
                         event.getUser().getTelegramId(), e.getMessage(), e);
                throw e;
                
            } catch (Exception e) {
                log.error("Непредвиденная ошибка {} при форматировании/отправке напоминания пользователю ID {}: " +
                         "reminderId={}, eventId={}, error={}", 
                         e.getClass().getSimpleName(), event.getUser().getId(), 
                         reminder.getId(), event.getId(), e.getMessage(), e);
                
                try {
                    log.warn("Fallback на UTC для пользователя ID {} после непредвиденной ошибки {}", 
                            event.getUser().getId(), e.getClass().getSimpleName());
                    String message = formatShortReminderMessage(reminder, ZoneId.of("UTC"));
                    var keyboard = createReminderKeyboard(event, event.getUser().getId(), reminder.getId());
                    telegramMessageService.sendMessageWithInlineKeyboard(event.getUser().getTelegramId(), message, keyboard);
                } catch (Exception fallbackError) {
                    log.error("Критическая ошибка {} при отправке напоминания пользователю ID {} даже с UTC: {}", 
                             fallbackError.getClass().getSimpleName(), event.getUser().getId(), 
                             fallbackError.getMessage(), fallbackError);
                    throw fallbackError;
                }
            }
            
        } else {
            // Семейное событие - отправить всем членам семьи
            log.debug("Отправка напоминания о семейном событии ID {} всем членам семьи", event.getId());
            
            if (event.getFamily() != null && event.getFamily().getMembers() != null) {
                for (User member : event.getFamily().getMembers()) {
                    try {
                        ZoneId recipientTimezone = reminderConfigurationService.getUserTimezone(member);
                        log.debug("Форматирование короткого сообщения для члена семьи ID {} в timezone {}", 
                                 member.getId(), recipientTimezone);
                        
                        String message = formatShortReminderMessage(reminder, recipientTimezone);
                        var keyboard = createReminderKeyboard(event, member.getId(), reminder.getId());
                        telegramMessageService.sendMessageWithInlineKeyboard(member.getTelegramId(), message, keyboard);
                        
                    } catch (java.time.DateTimeException e) {
                        log.error("Ошибка DateTimeException при форматировании/отправке напоминания члену семьи ID {} " +
                                 "(telegramId={}): reminderId={}, eventId={}, memberTimezone={}, error={}", 
                                 member.getId(), member.getTelegramId(), reminder.getId(), event.getId(), 
                                 member.getTimezone(), e.getMessage(), e);
                        
                        try {
                            log.warn("Fallback на UTC для члена семьи ID {} после DateTimeException", member.getId());
                            String message = formatShortReminderMessage(reminder, ZoneId.of("UTC"));
                            var keyboard = createReminderKeyboard(event, member.getId(), reminder.getId());
                            telegramMessageService.sendMessageWithInlineKeyboard(member.getTelegramId(), message, keyboard);
                        } catch (Exception fallbackError) {
                            log.error("Критическая ошибка {} при отправке напоминания члену семьи ID {} даже с UTC: {}", 
                                     fallbackError.getClass().getSimpleName(), member.getId(), 
                                     fallbackError.getMessage(), fallbackError);
                        }
                        
                    } catch (TelegramApiException e) {
                        log.error("Ошибка TelegramApiException при отправке напоминания члену семьи ID {} " +
                                 "(telegramId={}): reminderId={}, eventId={}, error={}", 
                                 member.getId(), member.getTelegramId(), reminder.getId(), event.getId(), 
                                 e.getMessage(), e);
                        
                    } catch (Exception e) {
                        log.error("Непредвиденная ошибка {} при форматировании/отправке напоминания члену семьи ID {} " +
                                 "(telegramId={}): reminderId={}, eventId={}, error={}", 
                                 e.getClass().getSimpleName(), member.getId(), member.getTelegramId(), 
                                 reminder.getId(), event.getId(), e.getMessage(), e);
                        
                        try {
                            log.warn("Fallback на UTC для члена семьи ID {} после непредвиденной ошибки {}", 
                                    member.getId(), e.getClass().getSimpleName());
                            String message = formatShortReminderMessage(reminder, ZoneId.of("UTC"));
                            var keyboard = createReminderKeyboard(event, member.getId(), reminder.getId());
                            telegramMessageService.sendMessageWithInlineKeyboard(member.getTelegramId(), message, keyboard);
                        } catch (Exception fallbackError) {
                            log.error("Критическая ошибка {} при отправке напоминания члену семьи ID {} даже с UTC: {}", 
                                     fallbackError.getClass().getSimpleName(), member.getId(), 
                                     fallbackError.getMessage(), fallbackError);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Создает inline-клавиатуру для уведомления о напоминании.
     * 
     * @param event событие
     * @param userId идентификатор пользователя, которому отправляется уведомление
     * @param reminderId идентификатор напоминания для возврата к нему
     * @return inline-клавиатура с одной кнопкой
     */
    private InlineKeyboardMarkup createReminderKeyboard(Event event, Long userId, Long reminderId) {
        return createSimplifiedReminderKeyboard(event, reminderId);
    }
    
    /**
     * Создает упрощенную клавиатуру для уведомления о напоминании.
     * 
     * @param event событие для создания клавиатуры
     * @param reminderId идентификатор напоминания для возврата к нему
     * @return inline-клавиатура с одной кнопкой
     */
    public InlineKeyboardMarkup createSimplifiedReminderKeyboard(Event event, Long reminderId) {
        log.debug("Создание упрощенной клавиатуры напоминания для события ID {} и напоминания ID {}", 
                 event.getId(), reminderId);
        
        var keyboard = new InlineKeyboardMarkup();
        var rows = new ArrayList<List<InlineKeyboardButton>>();
        
        // Единственная кнопка: "Посмотреть детали"
        var viewButton = InlineKeyboardButton.builder()
            .text("📋 Посмотреть детали")
            .callbackData(CallbackPrefix.VIEW_EVENT_FROM_REMINDER.withPayload(
                event.getId() + "_" + reminderId))
            .build();
        
        rows.add(List.of(viewButton));
        keyboard.setKeyboard(rows);
        
        log.debug("Упрощенная клавиатура создана для события ID {} и напоминания ID {}: " +
                 "1 ряд, 1 кнопка, callback format: view_event_from_reminder_{}_{}",
                 event.getId(), reminderId, event.getId(), reminderId);
        
        return keyboard;
    }
    
    /**
     * Форматирует короткую версию уведомления о напоминании.
     * 
     * @param reminder напоминание
     * @param recipientTimezone часовой пояс получателя для форматирования времени
     * @return короткое отформатированное сообщение
     */
    public String formatShortReminderMessage(Reminder reminder, ZoneId recipientTimezone) {
        Event event = reminder.getEvent();
        
        try {
            log.debug("Форматирование короткого сообщения напоминания ID {} для получателя: " +
                     "eventId={}, eventDate={}, eventTime={}, recipientTimezone={}", 
                     reminder.getId(), event.getId(), event.getEventDate(), event.getEventTime(), 
                     recipientTimezone);
            
            // Создаем ZonedDateTime для времени события напрямую в timezone получателя
            ZonedDateTime eventInRecipientTZ = ZonedDateTime.of(
                event.getEventDate(), 
                event.getEventTime(), 
                recipientTimezone
            );
            
            log.debug("Время события для короткого напоминания ID {}: " +
                     "eventTimeRecipientTZ={}, recipientTZ={}", 
                     reminder.getId(), eventInRecipientTZ.toLocalDateTime(), recipientTimezone);
            
            // Форматтер для времени
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            String formattedTime = eventInRecipientTZ.format(timeFormatter);
            
            StringBuilder message = new StringBuilder();
            
            log.info("Форматирование короткого напоминания ID {}: reminderType={}", 
                     reminder.getId(), reminder.getReminderType());
            
            // Уникальный заголовок в зависимости от типа напоминания
            switch (reminder.getReminderType()) {
                case EVENING_BEFORE:
                    log.debug("Используется формат EVENING_BEFORE для напоминания ID {}", reminder.getId());
                    message.append("🌙 ").append(bold("Напоминание: завтра в " + formattedTime + " у вас событие - "));
                    break;
                    
                case ONE_HOUR_BEFORE:
                    log.debug("Используется формат ONE_HOUR_BEFORE для напоминания ID {}", reminder.getId());
                    message.append("⚡ ").append(bold("Напоминание: через 1 час начнется событие - "));
                    break;
                    
                case FIFTEEN_MINUTES_BEFORE:
                    log.debug("Используется формат FIFTEEN_MINUTES_BEFORE для напоминания ID {}", reminder.getId());
                    message.append("🔥 ").append(bold("Напоминание: через 15 минут начнется событие - "));
                    break;
                    
                default:
                    log.warn("Используется fallback формат для напоминания ID {}: reminderType={}", 
                            reminder.getId(), reminder.getReminderType());
                    message.append("🔔 ").append(bold("Напоминание о событии - "));
            }
            
            // Название события БЕЗ эмодзи типа события
            message.append(bold(event.getTitle()));
            
            log.debug("Сформировано короткое уведомление для напоминания ID {} в timezone {}: " +
                     "eventTimeRecipientTZ={}, recipientTZ={}, длина={}", 
                     reminder.getId(), recipientTimezone, eventInRecipientTZ.toLocalDateTime(), 
                     recipientTimezone, message.length());
            
            return message.toString();
            
        } catch (java.time.DateTimeException e) {
            log.error("Ошибка DateTimeException при форматировании короткого сообщения напоминания ID {} в timezone {}: " +
                     "eventId={}, eventDate={}, eventTime={}, error={}", 
                     reminder.getId(), recipientTimezone, event.getId(), 
                     event.getEventDate(), event.getEventTime(), e.getMessage(), e);
            
            if (!recipientTimezone.equals(ZoneId.of("UTC"))) {
                log.warn("Fallback на UTC для форматирования короткого сообщения напоминания ID {} после DateTimeException", 
                        reminder.getId());
                return formatShortReminderMessage(reminder, ZoneId.of("UTC"));
            }
            
            log.error("Критическая ошибка форматирования короткого напоминания ID {} даже с UTC, используется базовый формат", 
                     reminder.getId(), e);
            return "🔔 " + bold("Напоминание о событии - " + event.getTitle());
            
        } catch (Exception e) {
            log.error("Непредвиденная ошибка {} при форматировании короткого сообщения напоминания ID {} в timezone {}: " +
                     "eventId={}, eventDate={}, eventTime={}, error={}", 
                     e.getClass().getSimpleName(), reminder.getId(), recipientTimezone, 
                     event.getId(), event.getEventDate(), event.getEventTime(), e.getMessage(), e);
            
            if (!recipientTimezone.equals(ZoneId.of("UTC"))) {
                log.warn("Fallback на UTC для форматирования короткого сообщения напоминания ID {} после непредвиденной ошибки {}", 
                        reminder.getId(), e.getClass().getSimpleName());
                return formatShortReminderMessage(reminder, ZoneId.of("UTC"));
            }
            
            log.error("Критическая ошибка {} форматирования короткого напоминания ID {} даже с UTC, используется базовый формат", 
                     e.getClass().getSimpleName(), reminder.getId(), e);
            return "🔔 " + bold("Напоминание о событии - " + event.getTitle());
        }
    }
    
    /**
     * Форматирует полную версию уведомления о напоминании с эмодзи 🔔 и всей информацией.
     * 
     * @param reminder напоминание
     * @param recipientTimezone часовой пояс получателя для форматирования времени
     * @return отформатированное сообщение с полной информацией
     */
    public String formatReminderMessageByType(Reminder reminder, ZoneId recipientTimezone) {
        Event event = reminder.getEvent();
        
        try {
            // Получаем timezone создателя события
            ZoneId creatorTimezone = reminderConfigurationService.getUserTimezone(event.getUser());
            
            log.debug("Форматирование полного сообщения напоминания ID {} для получателя: " +
                     "eventId={}, eventDate={}, eventTime={}, creatorTimezone={}, recipientTimezone={}", 
                     reminder.getId(), event.getId(), event.getEventDate(), event.getEventTime(), 
                     creatorTimezone, recipientTimezone);
            
            // Создаем ZonedDateTime для времени события в timezone создателя
            ZonedDateTime eventInCreatorTZ = ZonedDateTime.of(
                event.getEventDate(), 
                event.getEventTime(), 
                creatorTimezone
            );
            
            // Конвертируем время события из timezone создателя в timezone получателя
            ZonedDateTime eventInRecipientTZ = eventInCreatorTZ.withZoneSameInstant(recipientTimezone);
            
            log.debug("Конвертация времени события для полного напоминания ID {}: " +
                     "eventTimeCreatorTZ={}, eventTimeRecipientTZ={}, creatorTZ={}, recipientTZ={}", 
                     reminder.getId(), eventInCreatorTZ.toLocalDateTime(), 
                     eventInRecipientTZ.toLocalDateTime(), creatorTimezone, recipientTimezone);
            
            // Форматтеры для даты и времени
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            
            String formattedTime = eventInRecipientTZ.format(timeFormatter);
            String formattedDate = eventInRecipientTZ.format(dateFormatter);
            
            StringBuilder message = new StringBuilder();
            
            // Заголовок с эмодзи 🔔 (единый для всех типов)
            message.append("🔔 ").append(bold("Напоминание о событии")).append("\n\n");
            
            // Название события
            message.append(formatMessage("📌 Событие: %s\n", event.getTitle()));
            
            // Дата
            message.append(formatMessage("📅 Дата: %s\n", formattedDate));
            
            // Время
            message.append(formatMessage("🕐 Время: %s\n", formattedTime));
            
            // Тип события (персональное/семейное)
            if (event.getIsPersonal()) {
                message.append("👤 Тип: Персональное\n");
            } else {
                message.append("👨‍👩‍👧‍👦 Тип: Семейное\n");
            }
            
            // Описание события (полное, без обрезки)
            if (event.getDescription() != null && !event.getDescription().isBlank()) {
                message.append(formatMessage("📝 Описание: %s\n\n", event.getDescription()));
            }
            
            // Тип напоминания
            String reminderTypeText = getReminderTimeInfo(reminder);
            message.append(formatMessage("⏰ Напоминание: %s", reminderTypeText));
            
            log.debug("Сформировано полное уведомление для напоминания ID {} в timezone {}: " +
                     "eventTimeCreatorTZ={}, eventTimeRecipientTZ={}, creatorTZ={}, recipientTZ={}, длина={}", 
                     reminder.getId(), recipientTimezone, eventInCreatorTZ.toLocalDateTime(), 
                     eventInRecipientTZ.toLocalDateTime(), creatorTimezone, recipientTimezone, message.length());
            
            return message.toString();
            
        } catch (java.time.DateTimeException e) {
            log.error("Ошибка DateTimeException при форматировании полного сообщения напоминания ID {} в timezone {}: " +
                     "eventId={}, eventDate={}, eventTime={}, error={}", 
                     reminder.getId(), recipientTimezone, event.getId(), 
                     event.getEventDate(), event.getEventTime(), e.getMessage(), e);
            
            if (!recipientTimezone.equals(ZoneId.of("UTC"))) {
                log.warn("Fallback на UTC для форматирования полного сообщения напоминания ID {} после DateTimeException", 
                        reminder.getId());
                return formatReminderMessageByType(reminder, ZoneId.of("UTC"));
            }
            
            log.error("Критическая ошибка форматирования полного напоминания ID {} даже с UTC, используется базовый формат", 
                     reminder.getId(), e);
            return formatReminderMessage(reminder);
            
        } catch (Exception e) {
            log.error("Непредвиденная ошибка {} при форматировании полного сообщения напоминания ID {} в timezone {}: " +
                     "eventId={}, eventDate={}, eventTime={}, error={}", 
                     e.getClass().getSimpleName(), reminder.getId(), recipientTimezone, 
                     event.getId(), event.getEventDate(), event.getEventTime(), e.getMessage(), e);
            
            if (!recipientTimezone.equals(ZoneId.of("UTC"))) {
                log.warn("Fallback на UTC для форматирования полного сообщения напоминания ID {} после непредвиденной ошибки {}", 
                        reminder.getId(), e.getClass().getSimpleName());
                return formatReminderMessageByType(reminder, ZoneId.of("UTC"));
            }
            
            log.error("Критическая ошибка {} форматирования полного напоминания ID {} даже с UTC, используется базовый формат", 
                     e.getClass().getSimpleName(), reminder.getId(), e);
            return formatReminderMessage(reminder);
        }
    }
    
    /**
     * Форматирует текст уведомления о напоминании.
     * 
     * @deprecated Используйте {@link #formatReminderMessageByType(Reminder, ZoneId)} для уникальных форматов по типам
     * @param reminder напоминание
     * @return отформатированное сообщение
     */
    @Deprecated
    private String formatReminderMessage(Reminder reminder) {
        Event event = reminder.getEvent();
        String timeInfo = getReminderTimeInfo(reminder);
        
        StringBuilder message = new StringBuilder();
        
        message.append("🔔 ").append(bold("Напоминание о событии")).append("\n\n");
        message.append(formatMessage("📅 Событие: %s\n", event.getTitle()));
        message.append(formatMessage("🕐 Дата: %s\n", event.getFormattedDate()));
        
        if (event.getEventTime() != null) {
            if (event.getEndTime() != null) {
                message.append(formatMessage("⏰ Время: %s - %s\n", 
                    event.getFormattedTime(), 
                    event.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm"))));
            } else {
                message.append(formatMessage("⏰ Время: %s\n", event.getFormattedTime()));
            }
        }
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            String truncatedDesc = event.getDescription().length() > 100 
                ? event.getDescription().substring(0, 100) + "..." 
                : event.getDescription();
            message.append(formatMessage("📝 Описание: %s\n", truncatedDesc));
        }
        
        message.append("\n");
        
        if (event.getIsPersonal()) {
            message.append("👤 ").append(bold("Персональное событие")).append("\n");
        } else {
            String creatorName = "пользователь";
            try {
                if (event.getUser() != null && Hibernate.isInitialized(event.getUser())) {
                    creatorName = event.getUser().getFirstName();
                }
            } catch (Exception e) {
                log.warn("Не удалось получить имя создателя события ID {} для напоминания ID {}: {}", 
                        event.getId(), reminder.getId(), e.getMessage());
            }
            message.append(formatMessage("👨‍👩‍👧‍👦 Семейное событие (создал: %s)\n", creatorName));
        }
        
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
            case EVENING_BEFORE:
                return "накануне вечером";
            case ONE_HOUR_BEFORE:
                return "за 1 час до события";
            case FIFTEEN_MINUTES_BEFORE:
                return "за 15 минут до события";
            default:
                return "о событии";
        }
    }
}
