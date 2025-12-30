package ru.golubyatnikov.family.calendar.bot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.golubyatnikov.family.calendar.bot.config.BotConfig;

/**
 * REST контроллер для приема webhook обновлений от Telegram Bot API.
 * 
 * <p>Этот контроллер обрабатывает входящие HTTP POST запросы от Telegram,
 * содержащие обновления (новые сообщения, команды, callback queries и т.д.).
 * Webhook URL должен быть зарегистрирован в Telegram через SetWebhook API метод.</p>
 * 
 * <p>Endpoint: POST /webhook/{botToken}</p>
 * 
 * <p>Безопасность: Токен бота в URL используется для валидации запросов.
 * Только запросы с правильным токеном будут обработаны.</p>
 * 
 * <p>Требования:</p>
 * <ul>
 *   <li>Валидация токена в URL перед обработкой</li>
 *   <li>Возврат HTTP 200 OK после успешного приема</li>
 *   <li>Логирование всех входящих обновлений</li>
 *   <li>Возврат HTTP 401 Unauthorized при неверном токене</li>
 * </ul>
 * 
 * @see Update
 * @see BotConfig
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    private final BotConfig botConfig;
    private final ru.golubyatnikov.family.calendar.bot.service.UpdateProcessor updateProcessor;

    /**
     * Обрабатывает входящие webhook обновления от Telegram Bot API.
     * 
     * <p>Этот метод вызывается Telegram каждый раз, когда происходит событие,
     * связанное с ботом (новое сообщение, команда, callback и т.д.).</p>
     * 
     * <p>Процесс обработки:</p>
     * <ol>
     *   <li>Валидация токена в URL</li>
     *   <li>Логирование информации об обновлении</li>
     *   <li>Возврат HTTP 200 OK для подтверждения получения</li>
     * </ol>
     * 
     * <p>Важно: Telegram ожидает ответ в течение 60 секунд.
     * Если ответ не получен, Telegram повторит отправку обновления.</p>
     * 
     * @param botToken токен бота из URL пути, используется для валидации запроса
     * @param update объект Update от Telegram, содержащий информацию о событии
     * @return ResponseEntity с HTTP 200 OK при успешной обработке,
     *         или HTTP 401 Unauthorized при неверном токене
     * 
     * @see Update
     * @see ResponseEntity
     */
    @PostMapping("/{botToken}")
    public ResponseEntity<Void> onUpdateReceived(
            @PathVariable String botToken,
            @RequestBody Update update) {
        
        log.debug("Получен webhook запрос с токеном: {}...", 
                 botToken.substring(0, Math.min(10, botToken.length())));
        
        // Валидация токена
        if (!isValidToken(botToken)) {
            log.warn("Попытка доступа с неверным токеном. Update ID: {}", 
                    update.getUpdateId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // Логирование входящего обновления
        logUpdate(update);
        
        // Асинхронная обработка обновления через UpdateProcessor
        updateProcessor.processUpdate(update);
        
        // Возврат HTTP 200 OK для подтверждения получения
        return ResponseEntity.ok().build();
    }

    /**
     * Валидирует токен бота из URL.
     * 
     * <p>Сравнивает токен из URL с токеном, настроенным в конфигурации приложения.
     * Это защищает endpoint от несанкционированного доступа.</p>
     * 
     * @param token токен из URL пути
     * @return true если токен совпадает с настроенным, false в противном случае
     */
    private boolean isValidToken(String token) {
        return botConfig.getToken().equals(token);
    }

    /**
     * Логирует информацию о входящем обновлении.
     * 
     * <p>Записывает в лог основную информацию об обновлении для мониторинга
     * и отладки. Включает ID обновления и тип события.</p>
     * 
     * @param update объект Update от Telegram
     */
    private void logUpdate(Update update) {
        StringBuilder logMessage = new StringBuilder("Получено обновление: ");
        logMessage.append("ID=").append(update.getUpdateId());
        
        if (update.hasMessage()) {
            logMessage.append(", Тип=MESSAGE");
            if (update.getMessage().hasText()) {
                logMessage.append(", Текст='")
                         .append(update.getMessage().getText())
                         .append("'");
            }
            if (update.getMessage().getFrom() != null) {
                logMessage.append(", От=")
                         .append(update.getMessage().getFrom().getId());
            }
        } else if (update.hasCallbackQuery()) {
            logMessage.append(", Тип=CALLBACK_QUERY");
            logMessage.append(", Data='")
                     .append(update.getCallbackQuery().getData())
                     .append("'");
        } else if (update.hasEditedMessage()) {
            logMessage.append(", Тип=EDITED_MESSAGE");
        } else {
            logMessage.append(", Тип=OTHER");
        }
        
        log.info(logMessage.toString());
    }
}
