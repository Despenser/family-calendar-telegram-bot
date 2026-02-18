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
import java.util.stream.IntStream;

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
            throw new IllegalArgumentException("Сообщение и пользователь не могут быть null");
        }

        Long chatId = message.getChatId();
        try {
            List<Event> userEvents = queryService.getUserEvents(user.getId());
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
            return;
        }
        
        try {
            List<Event> userEvents = queryService.getUserEvents(userId);
            
            if (queryService.isEmpty(userEvents)) {
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

        // Отправляем первое событие с заголовком
        boolean sent = navigationService.sendCombinedMessageWithFallback(
                chatId, combinedMessage, header, firstEvent, userId);

        if (sent) {
            navigationService.saveHeaderContext(userId, userEvents.size());
        }

        // Отправляем остальные события
        IntStream.range(1, userEvents.size())
                .mapToObj(userEvents::get)
                .forEach(event -> {
                    String eventText = formattingService.buildEventMessage(event);
                    navigationService.sendEventMessageWithFallback(chatId, eventText, event, userId);
                });

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
