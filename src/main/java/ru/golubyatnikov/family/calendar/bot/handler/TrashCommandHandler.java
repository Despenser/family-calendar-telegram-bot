package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.TrashService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /trash для управления корзиной удаленных событий.
 * 
 * <p>Этот обработчик позволяет пользователю:</p>
 * <ul>
 *   <li>Просматривать удаленные события (корзина)</li>
 *   <li>Восстанавливать события из корзины</li>
 *   <li>Окончательно удалять события</li>
 * </ul>
 * 
 * <p>События хранятся в корзине 30 дней, после чего автоматически удаляются.</p>
 * 
 * <p>Первое событие в корзине отображается вместе с шапкой в одном сообщении,
 * аналогично команде /my_events. Шапка содержит информацию о количестве событий
 * в корзине и сроке хранения.</p>
 * 
 * <p><b>Требования:</b> 19.4, 4.1, 4.2, 4.3, 4.4, 2.1, 2.2</p>
 * 
 * @see CommandHandler
 * @see TrashService
 * @author Family Calendar Bot Team
 * @version 2.0.0
 * @since 2026-01-19
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrashCommandHandler implements CommandHandler {
    
    private final TrashService trashService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final BotMessageBuilder botMessageBuilder;
    
    /**
     * Обрабатывает команду /trash.
     * 
     * <p>Получает список удаленных событий пользователя и отправляет их
     * с inline-кнопками для восстановления или окончательного удаления.</p>
     * 
     * <p>Первое событие отправляется вместе с шапкой корзины в одном сообщении.
     * Для первого события устанавливается флаг isTrashHeader=true, для остальных
     * событий флаг сбрасывается в false.</p>
     * 
     * <p>Для всех событий сохраняется messageId для последующего управления.</p>
     * 
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     * @return null, так как сообщения отправляются напрямую
     */
    @Override
    public String handle(Message message, User user) {
        Long chatId = message.getChatId();
        log.debug("Обработка команды /trash для пользователя ID={}", user.getId());
        
        try {
            List<Event> trashedEvents = trashService.getUserTrash(user.getId());
            
            if (trashedEvents.isEmpty()) {
                String responseMessage = buildEmptyTrashMessage();
                log.debug("Пользователю ID={} будет отправлено сообщение о пустой корзине", user.getId());
                return responseMessage;
            }
            
            // Управление флагами isTrashHeader
            Event firstEvent = trashedEvents.get(0);
            if (!Boolean.TRUE.equals(firstEvent.getIsTrashHeader())) {
                firstEvent.setIsTrashHeader(true);
                trashService.saveEvent(firstEvent);
                log.debug("Установлен флаг isTrashHeader=true для первого события ID={}", firstEvent.getId());
            }
            
            // Сбрасываем флаг для остальных событий
            for (int i = 1; i < trashedEvents.size(); i++) {
                Event event = trashedEvents.get(i);
                if (Boolean.TRUE.equals(event.getIsTrashHeader())) {
                    event.setIsTrashHeader(false);
                    trashService.saveEvent(event);
                    log.debug("Сброшен флаг isTrashHeader для события ID={}", event.getId());
                }
            }
            
            // Формируем шапку
            String header = botMessageBuilder.buildTrashHeader(trashedEvents.size());
            
            // Отправляем первое событие с шапкой
            String firstEventText = botMessageBuilder.buildEventMessage(firstEvent);
            String combinedMessage = header + "\n" + firstEventText;
            InlineKeyboardMarkup keyboard = keyboardService.createTrashActionsKeyboard(firstEvent.getId());
            
            log.debug("Отправка первого события ID={} с шапкой корзины", firstEvent.getId());
            Message sentMessage = messageService.sendMessageAndGet(chatId, combinedMessage, keyboard);
            firstEvent.setMessageId((long) sentMessage.getMessageId());
            trashService.saveEvent(firstEvent);
            log.debug("Сохранен messageId={} для первого события ID={}", sentMessage.getMessageId(), firstEvent.getId());
            
            // Отправляем остальные события
            for (int i = 1; i < trashedEvents.size(); i++) {
                Event event = trashedEvents.get(i);
                String eventText = botMessageBuilder.buildEventMessage(event);
                InlineKeyboardMarkup eventKeyboard = keyboardService.createTrashActionsKeyboard(event.getId());
                
                log.debug("Отправка события ID={} (позиция {})", event.getId(), i + 1);
                Message eventMessage = messageService.sendMessageAndGet(chatId, eventText, eventKeyboard);
                event.setMessageId((long) eventMessage.getMessageId());
                trashService.saveEvent(event);
                log.debug("Сохранен messageId={} для события ID={}", eventMessage.getMessageId(), event.getId());
            }
            
            log.debug("Пользователю ID={} отправлено {} удаленных событий", user.getId(), trashedEvents.size());
            // Возвращаем null, так как сообщения уже отправлены
            return null;
            
        } catch (Exception e) {
            log.error("Ошибка при обработке команды /trash для пользователя ID={}", user.getId(), e);
            return "❌ Произошла ошибка при получении корзины. Попробуйте позже.";
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
     * @return отформатированное сообщение о пустой корзине
     */
    private String buildEmptyTrashMessage() {
        StringBuilder message = new StringBuilder();
        message.append("🗑️ ").append(bold("Корзина")).append("\n\n");
        message.append(escape("Корзина пуста.\n\n"));
        message.append(italic("Удаленные события хранятся здесь 30 дней, после чего автоматически удаляются навсегда."));
        return message.toString();
    }
    
    @Override
    public String getCommand() {
        return "/trash";
    }
    
    @Override
    public String getDescription() {
        return "Корзина удаленных событий";
    }
    
    @Override
    public boolean requiresAuth() {
        return true;
    }
}
