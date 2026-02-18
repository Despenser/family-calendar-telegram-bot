package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.domain.planner.MyEventsHeaderUpdater;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.PlannerFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.domain.planner.PlannerNavigationService;
import ru.golubyatnikov.family.calendar.bot.service.domain.planner.PlannerQueryService;
import java.util.List;

/**
 * Координатор команды /my_events для Telegram бота семейного календаря.
 * Отвечает за обработку команды отображения списка событий пользователя.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlannerCommandHandler implements CommandHandler, MyEventsHeaderUpdater {

    private final PlannerQueryService queryService;
    private final PlannerFormattingService formattingService;
    private final PlannerNavigationService navigationService;
    private final TelegramMessageService messageService;

    /**
     * Обрабатывает команду /my_events от пользователя.
     *
     * @param message входящее сообщение от Telegram
     * @param user пользователь из базы данных
     *
     * @return null, так как все сообщения отправляются внутри метода
     */
    @Override
    public String handle(Message message, User user) {
        if (message == null || user == null) {
            log.error("Получено null сообщение или пользователь в PlannerCommandHandler");
            throw new IllegalArgumentException("Сообщение и пользователь не могут быть null");
        }

        Long chatId = message.getChatId();
        log.info("Обработка команды /my_events: userId={}", user.getId());

        try {
            List<Event> userEvents = queryService.getUserEvents(user.getId());
            log.info("Получено {} событий для пользователя ID={}", userEvents.size(), user.getId());

            if (queryService.isEmpty(userEvents)) {
                sendNoEventsMessage(chatId);
                return null;
            }

            sendUserEvents(chatId, user.getId(), userEvents);
            return null;

        } catch (Exception e) {
            log.error("Ошибка при обработке команды /my_events для пользователя ID={}: {}", 
                    user.getId(), e.getMessage(), e);

            sendErrorMessage(chatId);
            return null;
        }
    }

    /**
     * Обновляет счетчик событий в шапке первого сообщения.
     * Вызывается при изменении количества событий пользователя.
     * 
     * @param userId идентификатор пользователя
     */
    @Override
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
    public String getCommand() {
        return "/my_events";
    }

    @Override
    public String getDescription() {
        return "Управление моими событиями";
    }

    /**
     * Отправляет сообщение об отсутствии событий.
     * 
     * @param chatId идентификатор чата
     */
    private void sendNoEventsMessage(Long chatId) {
        try {
            String noEventsMessage = formattingService.buildNoEventsMessage();
            messageService.sendMessage(chatId, noEventsMessage);
            log.info("Сообщение об отсутствии событий отправлено пользователю chatId={}", chatId);

        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения об отсутствии событий: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправляет список событий пользователю.
     * 
     * @param chatId идентификатор чата
     * @param userId идентификатор пользователя
     * @param userEvents список событий
     */
    private void sendUserEvents(Long chatId, Long userId, List<Event> userEvents) {
        navigationService.manageHeaderFlags(userEvents);

        String header = formattingService.buildMyEventsHeader(userEvents.size());
        Event firstEvent = queryService.getFirstEvent(userEvents);
        String firstEventText = formattingService.buildEventMessage(firstEvent);
        String combinedMessage = formattingService.buildCombinedMessage(header, firstEventText);

        int successCount = 0;
        int failureCount = 0;

        // Отправляем первое событие с заголовком
        boolean sent = navigationService.sendCombinedMessageWithFallback(
                chatId, combinedMessage, header, firstEvent, userId);

        if (sent) {
            successCount++;
            navigationService.saveHeaderContext(userId, userEvents.size());

        } else {
            failureCount++;
        }

        // Отправляем остальные события
        for (int i = 1; i < userEvents.size(); i++) {
            Event event = userEvents.get(i);
            String eventText = formattingService.buildEventMessage(event);
            boolean eventSent = navigationService.sendEventMessageWithFallback(chatId, eventText, event, userId);
            if (eventSent) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        log.info("Завершена отправка событий пользователю ID={}: успешно={}, ошибок={}", 
                userId, successCount, failureCount);
    }

    /**
     * Отправляет сообщение об ошибке.
     * 
     * @param chatId идентификатор чата
     */
    private void sendErrorMessage(Long chatId) {
        try {
            String errorMessage = formattingService.buildErrorMessage(
                    "Произошла ошибка при получении списка событий. Попробуйте позже.");

            messageService.sendMessage(chatId, errorMessage);

        } catch (Exception sendError) {
            log.error("Не удалось отправить сообщение об ошибке: {}", sendError.getMessage());
        }
    }
}
