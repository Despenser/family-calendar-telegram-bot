package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.TrashService;
import ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter;

/**
 * Обработчик callback queries для операций с корзиной.
 * 
 * <p>Обрабатывает следующие типы callback:</p>
 * <ul>
 *   <li>trash_restore_ - восстановление события из корзины</li>
 *   <li>trash_delete_ - окончательное удаление события</li>
 * </ul>
 * 
 * <p>Этот обработчик решает проблему, когда пользователь нажимает на кнопки
 * "Восстановить" или "Удалить навсегда" в корзине, но система выдаёт ошибку
 * "Неизвестная команда". Теперь callback-запросы корректно маршрутизируются
 * через CallbackQueryDispatcher к этому обработчику.</p>
 * 
 * <p><b>Требования:</b> 1.5, 2.1, 2.3</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-17
 * @see TrashService
 * @see CallbackHandler
 * @see CallbackPrefix#TRASH
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrashCallbackHandler implements CallbackHandler {
    
    private final TrashService trashService;
    private final TelegramMessageService messageService;
    
    /**
     * Возвращает префикс callback data для корзины.
     * 
     * <p>Этот метод используется CallbackQueryDispatcher для маршрутизации
     * callback-запросов с префиксом "trash_" к данному обработчику.</p>
     * 
     * @return CallbackPrefix.TRASH
     */
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.TRASH;
    }
    
    /**
     * Обрабатывает callback query от inline-кнопок корзины.
     * 
     * <p>Метод определяет тип операции (восстановление или удаление) по префиксу
     * callback data и вызывает соответствующий приватный метод для обработки.</p>
     * 
     * <p>Аннотация {@code @HandleCallbackErrors} обеспечивает централизованную
     * обработку ошибок через AOP аспект.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 2.2, 2.4</p>
     * 
     * @param callbackQuery объект callback query от Telegram
     * @param user авторизованный пользователь, выполняющий действие
     * @throws Exception если произошла ошибка при обработке callback
     */
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        
        log.debug("Обработка callback корзины: data='{}', userId={}", callbackData, user.getId());
        
        // Определяем тип операции по префиксу
        if (callbackData.startsWith("trash_restore_")) {
            Long eventId = extractEventId(callbackData);
            handleRestore(chatId, user, eventId);
        } else if (callbackData.startsWith("trash_delete_")) {
            Long eventId = extractEventId(callbackData);
            handlePermanentDelete(chatId, user, eventId);
        } else {
            log.warn("Неизвестный формат callback data корзины: data='{}', userId={}", 
                    callbackData, user.getId());
        }
    }
    
    /**
     * Извлекает ID события из callback data.
     * 
     * <p>Callback data имеет формат "trash_restore_{eventId}" или "trash_delete_{eventId}".
     * Метод определяет префикс и извлекает eventId из строки после префикса.</p>
     * 
     * <p><b>Требования:</b> 1.3</p>
     * 
     * @param callbackData строка callback data
     * @return ID события
     * @throws NumberFormatException если eventId не является числом
     * @throws IllegalArgumentException если формат callback data неизвестен
     */
    private Long extractEventId(String callbackData) {
        String prefix;
        
        if (callbackData.startsWith("trash_restore_")) {
            prefix = "trash_restore_";
        } else if (callbackData.startsWith("trash_delete_")) {
            prefix = "trash_delete_";
        } else {
            log.error("Неизвестный формат callback data: {}", callbackData);
            throw new IllegalArgumentException("Unknown callback data format: " + callbackData);
        }
        
        String eventIdStr = callbackData.substring(prefix.length());
        
        try {
            return Long.parseLong(eventIdStr);
        } catch (NumberFormatException e) {
            log.error("Ошибка парсинга eventId: callbackData='{}', eventIdStr='{}'", 
                    callbackData, eventIdStr, e);
            throw e;
        }
    }
    
    /**
     * Обрабатывает восстановление события из корзины.
     * 
     * <p>Вызывает TrashService для восстановления события и отправляет
     * подтверждающее сообщение пользователю. При ошибках отправляет
     * соответствующее сообщение об ошибке.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.3</p>
     * 
     * @param chatId ID чата для отправки сообщения
     * @param user пользователь, выполняющий восстановление
     * @param eventId ID события для восстановления
     */
    private void handleRestore(Long chatId, User user, Long eventId) {
        log.debug("handleRestore вызван: chatId={}, userId={}, eventId={}", 
                chatId, user.getId(), eventId);
        
        try {
            // Восстанавливаем событие через TrashService
            Event restoredEvent = trashService.restoreEvent(eventId, user.getId());
            
            // Формируем подтверждающее сообщение с использованием MarkdownFormatter
            String message = MarkdownFormatter.escape("♻️ ") + 
                           MarkdownFormatter.bold("Событие восстановлено") + 
                           MarkdownFormatter.escape("\n\n") +
                           MarkdownFormatter.bold(restoredEvent.getTitle());
            
            // Добавляем дату и время, если они есть
            if (restoredEvent.getEventDate() != null) {
                message += MarkdownFormatter.escape("\n📅 " + restoredEvent.getFormattedDate());
            }
            if (restoredEvent.getEventTime() != null) {
                message += MarkdownFormatter.escape("\n🕐 " + restoredEvent.getFormattedTime());
            }
            
            // Отправляем сообщение пользователю
            try {
                messageService.sendMessage(chatId, message);
            } catch (Exception telegramException) {
                log.error("Ошибка при отправке сообщения о восстановлении события ID={}: {}", 
                         eventId, telegramException.getMessage(), telegramException);
                // Событие уже восстановлено, просто логируем ошибку отправки сообщения
            }
            
            log.info("Событие ID={} успешно восстановлено пользователем ID={}", 
                    eventId, user.getId());
            
        } catch (EventNotFoundException e) {
            log.error("Событие ID={} не найдено при попытке восстановления пользователем ID={}", 
                     eventId, user.getId(), e);
            try {
                messageService.sendMessage(chatId, MarkdownFormatter.escape("❌ Событие не найдено"));
            } catch (Exception telegramException) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", 
                         telegramException.getMessage(), telegramException);
            }
            
        } catch (UnauthorizedAccessException e) {
            log.error("Пользователь ID={} попытался восстановить чужое событие ID={}", 
                     user.getId(), eventId, e);
            try {
                messageService.sendMessage(chatId, 
                        MarkdownFormatter.escape("❌ У вас нет доступа к этому событию"));
            } catch (Exception telegramException) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", 
                         telegramException.getMessage(), telegramException);
            }
            
        } catch (Exception e) {
            log.error("Ошибка при восстановлении события ID={} пользователем ID={}: {}", 
                     eventId, user.getId(), e.getMessage(), e);
            try {
                messageService.sendMessage(chatId, 
                        MarkdownFormatter.escape("❌ Ошибка при восстановлении события"));
            } catch (Exception telegramException) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", 
                         telegramException.getMessage(), telegramException);
            }
        }
    }
    
    /**
     * Обрабатывает окончательное удаление события.
     * 
     * <p>Вызывает TrashService для окончательного удаления события и отправляет
     * подтверждающее сообщение пользователю. При ошибках отправляет
     * соответствующее сообщение об ошибке.</p>
     * 
     * <p><b>Требования:</b> 1.2, 1.3</p>
     * 
     * @param chatId ID чата для отправки сообщения
     * @param user пользователь, выполняющий удаление
     * @param eventId ID события для удаления
     */
    private void handlePermanentDelete(Long chatId, User user, Long eventId) {
        log.debug("handlePermanentDelete вызван: chatId={}, userId={}, eventId={}", 
                chatId, user.getId(), eventId);
        
        try {
            // Окончательно удаляем событие через TrashService
            trashService.permanentlyDelete(eventId, user.getId());
            
            // Формируем подтверждающее сообщение с использованием MarkdownFormatter
            String message = MarkdownFormatter.escape("❌ ") + 
                           MarkdownFormatter.bold("Событие удалено навсегда") + 
                           MarkdownFormatter.escape("\n\n") +
                           MarkdownFormatter.escape("Событие ID: " + eventId + " было окончательно удалено из системы.");
            
            // Отправляем сообщение пользователю
            try {
                messageService.sendMessage(chatId, message);
            } catch (Exception telegramException) {
                log.error("Ошибка при отправке сообщения об удалении события ID={}: {}", 
                         eventId, telegramException.getMessage(), telegramException);
                // Событие уже удалено, просто логируем ошибку отправки сообщения
            }
            
            log.info("Событие ID={} успешно удалено навсегда пользователем ID={}", 
                    eventId, user.getId());
            
        } catch (EventNotFoundException e) {
            log.error("Событие ID={} не найдено при попытке удаления пользователем ID={}", 
                     eventId, user.getId(), e);
            try {
                messageService.sendMessage(chatId, MarkdownFormatter.escape("❌ Событие не найдено"));
            } catch (Exception telegramException) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", 
                         telegramException.getMessage(), telegramException);
            }
            
        } catch (UnauthorizedAccessException e) {
            log.error("Пользователь ID={} попытался удалить чужое событие ID={}", 
                     user.getId(), eventId, e);
            try {
                messageService.sendMessage(chatId, 
                        MarkdownFormatter.escape("❌ У вас нет доступа к этому событию"));
            } catch (Exception telegramException) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", 
                         telegramException.getMessage(), telegramException);
            }
            
        } catch (Exception e) {
            log.error("Ошибка при удалении события ID={} пользователем ID={}: {}", 
                     eventId, user.getId(), e.getMessage(), e);
            try {
                messageService.sendMessage(chatId, 
                        MarkdownFormatter.escape("❌ Ошибка при удалении события"));
            } catch (Exception telegramException) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", 
                         telegramException.getMessage(), telegramException);
            }
        }
    }
}
