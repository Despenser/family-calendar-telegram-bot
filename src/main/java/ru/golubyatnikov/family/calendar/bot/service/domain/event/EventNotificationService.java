package ru.golubyatnikov.family.calendar.bot.service.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.dto.EventMessageData;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderCreationService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;

/**
 * Сервис для отправки уведомлений о событиях.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-01
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventNotificationService {
    
    private final EventRepository eventRepository;
    private final TelegramMessageService telegramMessageService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final ReminderCreationService reminderCreationService;
    
    /**
     * Обрабатывает создание события: создает напоминания по умолчанию и отправляет уведомления членам семьи.
     * 
     * @param event созданное событие
     * @param user пользователь, создавший событие
     */
    @Transactional
    public void handleEventCreated(@NonNull Event event, @NonNull User user) {
        // Для семейных событий создаем напоминания для всех членов семьи
        if (!event.getIsPersonal() && event.getFamily() != null) {
            createRemindersForFamilyMembers(event);
            notifyFamilyMembersAboutNewEvent(event, user);
        } else {
            // Для персональных событий создаем напоминания только для создателя
            reminderCreationService.createDefaultReminders(event, user);
        }
    }
    
    /**
     * Создает напоминания по умолчанию для всех членов семьи.
     * 
     * @param event семейное событие
     */
    private void createRemindersForFamilyMembers(@NonNull Event event) {
        if (event.getFamily() == null || event.getFamily().getMembers() == null) {
            log.warn("Невозможно создать напоминания: семья или её члены не найдены для события ID={}", event.getId());
            return;
        }
        
        event.getFamily().getMembers().forEach(member -> {
            try {
                reminderCreationService.createDefaultReminders(event, member);
                log.debug("Напоминания созданы для члена семьи ID={} для события ID={}", member.getId(), event.getId());
            } catch (Exception e) {
                log.error("Ошибка создания напоминаний для члена семьи ID={} для события ID={}: {}", 
                         member.getId(), event.getId(), e.getMessage(), e);
            }
        });
    }
    
    /**
     * Отправляет уведомления всем членам семьи о создании нового события.
     * Уведомление не отправляется создателю события.
     * 
     * @param event созданное событие
     * @param creator пользователь, создавший событие
     */
    private void notifyFamilyMembersAboutNewEvent(@NonNull Event event, @NonNull User creator) {
        if (event.getFamily() == null || event.getFamily().getMembers() == null) {
            log.warn("Невозможно отправить уведомления: семья или её члены не найдены для события ID={}", event.getId());
            return;
        }
        
        event.getFamily().getMembers().stream()
            .filter(member -> !member.getId().equals(creator.getId())) // Не отправляем создателю
            .forEach(member -> {
                try {
                    sendEventCreationNotification(event, member, creator);
                } catch (Exception e) {
                    log.error("Ошибка отправки уведомления о создании события ID={} члену семьи ID={}: {}", 
                             event.getId(), member.getId(), e.getMessage(), e);
                }
            });
    }
    
    /**
     * Отправляет уведомление о создании события конкретному члену семьи.
     * 
     * @param event созданное событие
     * @param member член семьи, которому отправляется уведомление
     * @param creator создатель события
     * @throws TelegramApiException если не удалось отправить уведомление
     */
    private void sendEventCreationNotification(@NonNull Event event, @NonNull User member, @NonNull User creator) 
            throws TelegramApiException {
        
        if (member.getTelegramId() == null) {
            log.warn("Невозможно отправить уведомление члену семьи ID={}: отсутствует Telegram ID", member.getId());
            return;
        }
        
        String messageText = buildEventCreationNotificationMessage(event, creator);
        telegramMessageService.sendMessage(member.getTelegramId(), messageText);
        
        log.info("Уведомление о создании события ID={} отправлено члену семьи ID={}", event.getId(), member.getId());
    }
    
    /**
     * Формирует текст уведомления о создании нового события.
     * 
     * @param event созданное событие
     * @param creator создатель события
     * @return отформатированный текст уведомления
     */
    private String buildEventCreationNotificationMessage(@NonNull Event event, @NonNull User creator) {
        String creatorName = creator.getFirstName() != null ? creator.getFirstName() : "Член семьи";
        
        return "🔔 Новое событие в семейном календаре\n\n" +
               "Создатель: " + creatorName + "\n\n" +
               botMessageFormattingService.buildEventMessage(event);
    }
    
    /**
     * Подготавливает данные сообщения о событии с учетом контекста страницы.
     * 
     * @param event событие
     * @param userId идентификатор пользователя
     * @param myEventsPage номер страницы "Мои события" (может быть null)
     * @return данные сообщения о событии (текст и клавиатура)
     */
    public EventMessageData prepareEventMessageData(@NonNull Event event, 
                                                     @NonNull Long userId, 
                                                     Integer myEventsPage) {
        int eventCount = eventRepository.countByUserIdAndStatus(event.getUser().getId(), EventStatus.ACTIVE);
        String messageText = botMessageFormattingService.buildEventMessageWithHeader(event, eventCount);
        
        InlineKeyboardMarkup keyboard = myEventsPage != null
            ? keyboardService.createEventActionsKeyboardWithContext(event, userId, myEventsPage)
            : keyboardService.createEventActionsKeyboard(event, userId);
        
        return new EventMessageData(messageText, keyboard);
    }
    
    /**
     * Отправляет или обновляет сообщение о событии в Telegram.
     *
     * @param event  событие для отправки/обновления
     * @param chatId ID чата для отправки
     *
     * @throws TelegramApiException при критических ошибках отправки
     */
    @Transactional
    public void sendOrUpdateEventMessage(Event event, Long chatId) throws TelegramApiException {
        
        if (event == null) {
            throw new IllegalArgumentException("Event не может быть null");
        }
        
        if (chatId == null) {
            throw new IllegalArgumentException("ChatId не может быть null");
        }
        
        String messageText = botMessageFormattingService.buildEventMessage(event);
        if (Boolean.TRUE.equals(event.getIsMyEventsHeader())) {
            int eventCount = eventRepository.countByUserIdAndStatus(event.getUser().getId(), EventStatus.ACTIVE);
            String header = botMessageFormattingService.buildMyEventsHeader(eventCount);
            messageText = header + "\n" + messageText;
        }
        
        InlineKeyboardMarkup keyboard;
        if (event.getStatus() == EventStatus.DRAFT) {
            keyboard = keyboardService.createEditFieldSelectionKeyboard(event.getId());
        } else {
            Long userId = event.getUser().getId();
            keyboard = keyboardService.createEventActionsKeyboard(event, userId);
        }
        
        if (event.getMessageId() != null) {
            boolean updated = telegramMessageService.tryEditMessageText(
                    chatId, 
                    event.getMessageId().intValue(), 
                    messageText, 
                    keyboard
            );
            
            if (updated) {
                return;
            }
        }
        
        Message sentMessage = telegramMessageService.sendMessageAndGet(chatId, messageText, keyboard);
        event.setMessageId((long) sentMessage.getMessageId());

        eventRepository.save(event);
    }
}
