package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.golubyatnikov.family.calendar.bot.handler.MyEventsCommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.util.Optional;

/**
 * Сервис для асинхронной обработки обновлений от Telegram Bot API.
 * 
 * <p>UpdateProcessor является центральным компонентом обработки входящих webhook обновлений.
 * Он отвечает за:</p>
 * <ul>
 *   <li>Асинхронную обработку обновлений для быстрого ответа Telegram API</li>
 *   <li>Извлечение сообщений из обновлений</li>
 *   <li>Делегирование обработки команд в CommandDispatcher</li>
 *   <li>Логирование процесса обработки для мониторинга и отладки</li>
 * </ul>
 * 
 * <p>Асинхронная обработка позволяет быстро возвращать HTTP 200 OK в webhook контроллере,
 * что критично для соблюдения 60-секундного таймаута Telegram API.</p>
 * 
 * <p><b>Архитектурный паттерн:</b> Async Processing + Delegation</p>
 * <p><b>Требования:</b> 8.2</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>{@code
 * @RestController
 * public class TelegramWebhookController {
 *     private final UpdateProcessor updateProcessor;
 *     
 *     @PostMapping("/webhook/{token}")
 *     public ResponseEntity<Void> onUpdate(@RequestBody Update update) {
 *         updateProcessor.processUpdate(update);
 *         return ResponseEntity.ok().build();
 *     }
 * }
 * }</pre>
 * 
 * @see CommandDispatcher
 * @see UserService
 * @see Update
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateProcessor {

    private final CommandDispatcher commandDispatcher;
    private final UserService userService;
    private final MyEventsCommandHandler myEventsCommandHandler;
    private final TelegramMessageService messageService;

    /**
     * Асинхронно обрабатывает входящее обновление от Telegram Bot API.
     * 
     * <p>Этот метод выполняется в отдельном потоке благодаря аннотации @Async,
     * что позволяет webhook контроллеру быстро вернуть ответ Telegram API.</p>
     * 
     * <p>Процесс обработки:</p>
     * <ol>
     *   <li>Проверка наличия сообщения в обновлении</li>
     *   <li>Извлечение объекта Message</li>
     *   <li>Делегирование обработки в CommandDispatcher</li>
     *   <li>Логирование результата обработки</li>
     * </ol>
     * 
     * <p>Если обновление не содержит сообщения (например, это callback query или
     * другой тип обновления), метод логирует это и завершает обработку.</p>
     * 
     * <p>Все исключения перехватываются и логируются, чтобы не прерывать
     * обработку других обновлений.</p>
     * 
     * @param update объект Update от Telegram, содержащий информацию о событии
     * @throws IllegalArgumentException если update равен null
     * @see Update
     * @see Message
     * @see CommandDispatcher#dispatch(Message)
     */
    @Async
    public void processUpdate(Update update) {
        if (update == null) {
            log.error("Получено null обновление для обработки");
            throw new IllegalArgumentException("Update не может быть null");
        }
        
        log.info("Начало асинхронной обработки обновления: updateId={}", update.getUpdateId());
        
        try {
            // Проверяем, содержит ли обновление callback query
            if (update.hasCallbackQuery()) {
                log.debug("Обновление содержит callback query: updateId={}", update.getUpdateId());
                processCallbackQuery(update.getCallbackQuery());
                return;
            }
            
            // Проверяем, содержит ли обновление сообщение
            if (!update.hasMessage()) {
                log.debug("Обновление не содержит сообщения: updateId={}, hasCallbackQuery={}, hasEditedMessage={}", 
                        update.getUpdateId(), 
                        update.hasCallbackQuery(), 
                        update.hasEditedMessage());
                
                // TODO: В будущем здесь можно добавить обработку других типов обновлений
                // (edited messages и т.д.)
                log.info("Обновление пропущено (не содержит сообщения): updateId={}", update.getUpdateId());
                return;
            }
            
            // Извлекаем сообщение из обновления
            Message message = update.getMessage();
            
            if (message == null) {
                log.warn("Обновление помечено как hasMessage=true, но message=null: updateId={}", 
                        update.getUpdateId());
                return;
            }
            
            log.debug("Извлечено сообщение из обновления: updateId={}, messageId={}, chatId={}, from={}", 
                    update.getUpdateId(), 
                    message.getMessageId(), 
                    message.getChatId(),
                    message.getFrom() != null ? message.getFrom().getId() : null);
            
            // Делегируем обработку команды в CommandDispatcher
            // CommandDispatcher сам проверит авторизацию через UserService
            String response = commandDispatcher.dispatch(message);
            
            log.info("Обновление успешно обработано: updateId={}, responseLength={}", 
                    update.getUpdateId(), 
                    response != null ? response.length() : 0);
            
            // Отправляем ответ пользователю через TelegramMessageService
            if (response != null && !response.isBlank()) {
                try {
                    messageService.sendMessage(message.getChatId(), response);
                    log.info("Ответ успешно отправлен пользователю: chatId={}, responseLength={}", 
                            message.getChatId(), response.length());
                } catch (Exception e) {
                    log.error("Ошибка при отправке ответа пользователю: chatId={}, error={}", 
                            message.getChatId(), e.getMessage(), e);
                }
            } else {
                log.warn("Пустой ответ от обработчика команды: updateId={}", update.getUpdateId());
            }
            
        } catch (Exception e) {
            log.error("Ошибка при обработке обновления: updateId={}, error={}", 
                    update.getUpdateId(), e.getMessage(), e);
            
            // Не пробрасываем исключение дальше, чтобы не прерывать обработку других обновлений
            // В production можно добавить отправку сообщения об ошибке пользователю
        }
    }

    /**
     * Обрабатывает callback query от inline кнопок.
     * 
     * <p>Callback queries возникают, когда пользователь нажимает на inline кнопку.
     * Данные кнопки (callback data) содержат информацию о действии, которое нужно выполнить.</p>
     * 
     * <p>Поддерживаемые callback data:</p>
     * <ul>
     *   <li>edit_event_{eventId} - редактирование события</li>
     *   <li>delete_event_{eventId} - удаление события</li>
     *   <li>confirm_delete_{eventId} - подтверждение удаления</li>
     * </ul>
     * 
     * @param callbackQuery объект CallbackQuery от Telegram
     */
    private void processCallbackQuery(CallbackQuery callbackQuery) {
        if (callbackQuery == null) {
            log.warn("Получен null callback query");
            return;
        }
        
        String callbackData = callbackQuery.getData();
        Long telegramId = callbackQuery.getFrom().getId();
        
        log.info("Обработка callback query: data='{}', telegramId={}", callbackData, telegramId);
        
        try {
            // Проверяем авторизацию пользователя
            Optional<User> userOptional = userService.findByTelegramId(telegramId);
            if (userOptional.isEmpty()) {
                log.warn("Пользователь с telegramId={} не найден при обработке callback", telegramId);
                // TODO: Отправить сообщение о необходимости регистрации
                return;
            }
            
            User user = userOptional.get();
            String response = null;
            
            // Обрабатываем различные типы callback data
            if (callbackData.startsWith("edit_event_")) {
                Long eventId = extractEventId(callbackData, "edit_event_");
                response = myEventsCommandHandler.handleEditCallback(eventId, user.getId());
                
            } else if (callbackData.startsWith("delete_event_")) {
                Long eventId = extractEventId(callbackData, "delete_event_");
                response = myEventsCommandHandler.handleDeleteCallback(eventId, user.getId());
                
            } else {
                log.warn("Неизвестный callback data: '{}'", callbackData);
                response = "❌ Неизвестная команда";
            }
            
            log.info("Callback query успешно обработан: data='{}', responseLength={}", 
                    callbackData, response != null ? response.length() : 0);
            
            // Отправляем ответ пользователю через TelegramMessageService
            if (response != null && !response.isBlank()) {
                try {
                    messageService.sendMessage(callbackQuery.getMessage().getChatId(), response);
                    messageService.answerCallbackQuery(callbackQuery.getId(), "Обработано");
                    log.info("Ответ на callback query успешно отправлен: chatId={}, responseLength={}", 
                            callbackQuery.getMessage().getChatId(), response.length());
                } catch (Exception e) {
                    log.error("Ошибка при отправке ответа на callback query: chatId={}, error={}", 
                            callbackQuery.getMessage().getChatId(), e.getMessage(), e);
                }
            } else {
                log.warn("Пустой ответ от обработчика callback: data='{}'", callbackData);
                try {
                    messageService.answerCallbackQuery(callbackQuery.getId(), "");
                } catch (Exception e) {
                    log.error("Ошибка при ответе на callback query: error={}", e.getMessage(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("Ошибка при обработке callback query: data='{}', error={}", 
                    callbackData, e.getMessage(), e);
            
            // TODO: Отправить сообщение об ошибке пользователю
        }
    }

    /**
     * Извлекает ID события из callback data.
     * 
     * <p>Callback data имеет формат: "{prefix}_{eventId}"
     * Например: "edit_event_123" или "delete_event_456"</p>
     * 
     * @param callbackData строка с callback data
     * @param prefix префикс для удаления
     * @return ID события
     * @throws NumberFormatException если ID не является числом
     */
    private Long extractEventId(String callbackData, String prefix) {
        String eventIdStr = callbackData.substring(prefix.length());
        return Long.parseLong(eventIdStr);
    }
}
