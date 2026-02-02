package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.myevents.MyEventsFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.myevents.MyEventsNavigationService;
import ru.golubyatnikov.family.calendar.bot.service.myevents.MyEventsQueryService;

import java.util.List;

/**
 * Координатор команды /my_events для Telegram бота семейного календаря.
 * 
 * <p>Делегирует выполнение операций специализированным сервисам:</p>
 * <ul>
 *   <li>{@link MyEventsQueryService} - получение данных о событиях</li>
 *   <li>{@link MyEventsFormattingService} - форматирование сообщений</li>
 *   <li>{@link MyEventsNavigationService} - навигация и отправка сообщений</li>
 * </ul>
 * 
 * @see CommandHandler
 * @see MyEventsQueryService
 * @see MyEventsFormattingService
 * @see MyEventsNavigationService
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MyEventsCommandHandler implements CommandHandler {

    private final MyEventsQueryService queryService;
    private final MyEventsFormattingService formattingService;
    private final MyEventsNavigationService navigationService;
    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final TelegramMessageService messageService;
    private final ConversationStateService conversationStateService;

    /**
     * Обрабатывает команду /my_events от пользователя.
     * 
     * <p>Координирует выполнение операций через специализированные сервисы.</p>
     * 
     * @param message входящее сообщение от Telegram
     * @param user пользователь из базы данных
     * @return null, так как все сообщения отправляются внутри метода
     */
    @Override
    public String handle(Message message, User user) {
        if (message == null || user == null) {
            log.error("Получено null сообщение или пользователь в MyEventsCommandHandler");
            throw new IllegalArgumentException("Сообщение и пользователь не могут быть null");
        }

        Long chatId = message.getChatId();
        log.info("Обработка команды /my_events: userId={}", user.getId());

        try {
            // Получаем события пользователя
            List<Event> userEvents = queryService.getUserEvents(user.getId());
            log.info("Получено {} событий для пользователя ID={}", userEvents.size(), user.getId());

            // Если событий нет
            if (queryService.isEmpty(userEvents)) {
                String noEventsMessage = formattingService.buildNoEventsMessage();
                messageService.sendMessage(chatId, noEventsMessage);
                log.info("Сообщение об отсутствии событий отправлено пользователю chatId={}", chatId);
                return null;
            }

            // Управляем флагами шапки
            navigationService.manageHeaderFlags(userEvents);

            // Формируем заголовок и первое событие
            String header = formattingService.buildMyEventsHeader(userEvents.size());
            Event firstEvent = queryService.getFirstEvent(userEvents);
            String firstEventText = formattingService.buildEventMessage(firstEvent);
            String combinedMessage = formattingService.buildCombinedMessage(header, firstEventText);

            // Отправляем объединенное сообщение
            int successCount = 0;
            int failureCount = 0;

            boolean sent = sendCombinedMessageWithFallback(chatId, combinedMessage, header, firstEvent, user.getId());
            if (sent) {
                successCount++;
                navigationService.saveHeaderContext(user.getId(), userEvents.size());
            } else {
                failureCount++;
            }

            // Отправляем остальные события
            for (int i = 1; i < userEvents.size(); i++) {
                Event event = userEvents.get(i);
                boolean eventSent = sendEventWithFallback(chatId, event, user.getId());
                if (eventSent) {
                    successCount++;
                } else {
                    failureCount++;
                }
            }

            log.info("Завершена отправка событий пользователю ID={}: успешно={}, ошибок={}", 
                    user.getId(), successCount, failureCount);

            return null;

        } catch (Exception e) {
            log.error("Ошибка при обработке команды /my_events для пользователя ID={}: {}", 
                    user.getId(), e.getMessage(), e);
            
            try {
                String errorMessage = formattingService.buildErrorMessage(
                    "Произошла ошибка при получении списка событий. Попробуйте позже.");
                messageService.sendMessage(chatId, errorMessage);
            } catch (Exception sendError) {
                log.error("Не удалось отправить сообщение об ошибке: {}", sendError.getMessage());
            }
            
            return null;
        }
    }

    /**
     * Отправляет объединенное сообщение с fallback механизмом.
     * 
     * @param chatId идентификатор чата
     * @param combinedMessage объединенное сообщение
     * @param header заголовок
     * @param firstEvent первое событие
     * @param userId идентификатор пользователя
     * @return true если отправка успешна
     */
    private boolean sendCombinedMessageWithFallback(Long chatId, String combinedMessage, 
                                                     String header, Event firstEvent, Long userId) {
        try {
            boolean sent = navigationService.sendCombinedMessage(chatId, combinedMessage, firstEvent, userId);
            if (!sent) {
                // Попытка fallback при неудаче
                log.warn("Не удалось отправить объединенное сообщение, используем fallback");
                try {
                    messageService.sendMessage(chatId, header);
                    String plainText = formattingService.buildPlainEventText(firstEvent);
                    return navigationService.sendPlainEventMessage(chatId, plainText, firstEvent, userId);
                } catch (Exception fallbackException) {
                    log.error("Fallback не сработал: {}", fallbackException.getMessage());
                    return false;
                }
            }
            return sent;
        } catch (Exception e) {
            log.error("Ошибка при отправке объединенного сообщения: {}", e.getMessage());
            // Попытка fallback при исключении
            try {
                messageService.sendMessage(chatId, header);
                String plainText = formattingService.buildPlainEventText(firstEvent);
                return navigationService.sendPlainEventMessage(chatId, plainText, firstEvent, userId);
            } catch (Exception fallbackException) {
                log.error("Fallback не сработал: {}", fallbackException.getMessage());
                return false;
            }
        }
    }

    /**
     * Отправляет событие с fallback механизмом.
     * 
     * @param chatId идентификатор чата
     * @param event событие
     * @param userId идентификатор пользователя
     * @return true если отправка успешна
     */
    private boolean sendEventWithFallback(Long chatId, Event event, Long userId) {
        try {
            String eventText = formattingService.buildEventMessage(event);
            boolean sent = navigationService.sendEventMessage(chatId, eventText, event, userId);
            if (!sent) {
                // Попытка fallback при неудаче
                log.warn("Не удалось отправить событие ID={}, используем fallback", event.getId());
                try {
                    String plainText = formattingService.buildPlainEventText(event);
                    return navigationService.sendPlainEventMessage(chatId, plainText, event, userId);
                } catch (Exception fallbackException) {
                    log.error("Fallback не сработал для события ID={}: {}", 
                            event.getId(), fallbackException.getMessage());
                    return false;
                }
            }
            return sent;
        } catch (Exception e) {
            log.error("Ошибка при отправке события ID={}: {}", event.getId(), e.getMessage());
            // Попытка fallback при исключении
            try {
                String plainText = formattingService.buildPlainEventText(event);
                return navigationService.sendPlainEventMessage(chatId, plainText, event, userId);
            } catch (Exception fallbackException) {
                log.error("Fallback не сработал для события ID={}: {}", 
                        event.getId(), fallbackException.getMessage());
                return false;
            }
        }
    }

    /**
     * Обрабатывает callback query для просмотра деталей события.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @return сообщение с полной информацией о событии
     */
    public String handleViewEventDetails(Long eventId, Long userId) {
        log.debug("Обработка callback просмотра деталей события ID={} пользователем ID={}", 
                eventId, userId);
        
        try {
            Event event = queryService.getEventById(eventId);
            
            if (!queryService.canUserViewEvent(event, userId)) {
                log.warn("Пользователь ID={} попытался просмотреть чужое персональное событие ID={}", 
                        userId, eventId);
                return formattingService.buildAccessDeniedMessage(
                    "У вас нет прав для просмотра этого события\\.");
            }
            
            String details = formattingService.buildEventDetails(event);
            log.debug("Детали события ID={} успешно отображены пользователю ID={}", eventId, userId);
            
            return details;
            
        } catch (Exception e) {
            log.error("Ошибка при просмотре деталей события ID={}: {}", eventId, e.getMessage(), e);
            return formattingService.buildErrorMessage(
                "Не удалось загрузить детали события. Возможно, событие было удалено.");
        }
    }

    /**
     * Обрабатывает callback query для редактирования события.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @return сообщение с текущими данными события
     */
    public String handleEditCallback(Long eventId, Long userId, Long chatId) {
        log.debug("Обработка callback редактирования события ID={} пользователем ID={}", 
                eventId, userId);
        
        try {
            Event event = queryService.getEventById(eventId);
            
            if (!queryService.canUserEditEvent(event, userId)) {
                log.warn("Пользователь ID={} не имеет прав для редактирования события ID={}", 
                        userId, eventId);
                return formattingService.buildAccessDeniedMessage(
                    "У вас нет прав для редактирования этого события.");
            }
            
            conversationStateService.startEventEditing(userId, eventId, chatId);
            
            String message = formattingService.buildEditFieldSelectionMessage(event);
            InlineKeyboardMarkup keyboard = keyboardService.createEditFieldSelectionKeyboard(eventId);
            messageService.sendMessageWithInlineKeyboard(chatId, message, keyboard);
            
            log.debug("Начато редактирование события ID={} пользователем ID={}", eventId, userId);
            
            return message;
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            log.error("Событие ID={} не найдено: {}", eventId, e.getMessage());
            return formattingService.buildErrorMessage(
                "Событие не найдено. Возможно, оно было удалено.");
        } catch (Exception e) {
            log.error("Ошибка при начале редактирования события ID={}: {}", eventId, e.getMessage(), e);
            return formattingService.buildErrorMessage(
                "Произошла ошибка при начале редактирования.");
        }
    }

    /**
     * Обрабатывает callback query для удаления события.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     */
    public void handleDeleteCallback(Long eventId, Long userId) {
        log.debug("Обработка callback удаления события ID={} пользователем ID={}", 
                eventId, userId);
        
        eventService.deleteEvent(eventId, userId);
        
        log.debug("Событие ID={} успешно удалено пользователем ID={}", eventId, userId);
    }

    /**
     * Обновляет счетчик событий в шапке первого сообщения.
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
            List<Event> userEvents = queryService.getUserEvents(userId);
            
            if (queryService.isEmpty(userEvents)) {
                log.debug("Нет активных событий для пользователя ID={}", userId);
                return;
            }
            
            String header = formattingService.buildMyEventsHeader(userEvents.size());
            Event firstEvent = queryService.getFirstEvent(userEvents);
            String eventText = formattingService.buildEventMessage(firstEvent);
            
            navigationService.updateHeaderCount(userId, userEvents, header, eventText);
            
        } catch (Exception e) {
            log.error("Ошибка при обновлении счетчика событий в шапке для пользователя ID={}: {}", 
                    userId, e.getMessage(), e);
        }
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    @Override
    public String getCommand() {
        return "/my_events";
    }

    @Override
    public String getDescription() {
        return "Управление моими событиями";
    }
}
