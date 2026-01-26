package ru.golubyatnikov.family.calendar.bot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.golubyatnikov.family.calendar.bot.config.BotConfig;
import ru.golubyatnikov.family.calendar.bot.service.UpdateProcessor;

/**
 * REST контроллер для приема webhook обновлений от Telegram Bot API.
 *
 * @author Golubyatnikov Aleksey
 * @version 1.0.0
 * @since 2026-01-16
 */
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    private final BotConfig botConfig;
    private final UpdateProcessor updateProcessor;

    /**
     * Обрабатывает входящие webhook обновления от Telegram Bot API.
     * Этот метод вызывается Telegram каждый раз, когда происходит событие,
     * связанное с ботом (новое сообщение, команда, callback и т.д.)
     * 
     * @param botToken токен бота из URL пути, используется для валидации запроса
     * @param update объект Update от Telegram, содержащий информацию о событии
     * @return ResponseEntity с HTTP 200 OK при успешной обработке, или HTTP 401 Unauthorized при неверном токене
     */
    @PostMapping("/{botToken}")
    public ResponseEntity<Void> onUpdateReceived(@NonNull @PathVariable String botToken,
                                                 @RequestBody Update update) {
        
        log.debug("Получен webhook запрос с токеном: {}...", 
                 botToken.substring(0, Math.min(10, botToken.length())));

        if (!isValidToken(botToken)) {
            log.warn("Попытка доступа с неверным токеном. Update ID: {}", update.getUpdateId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        logUpdate(update);
        updateProcessor.processUpdate(update);
        return ResponseEntity.ok().build();
    }

    /**
     * Валидирует токен бота из URL.
     * 
     * @param token токен из URL пути
     * @return true, если токен совпадает с настроенным, false в противном случае
     */
    private boolean isValidToken(String token) {
        return botConfig.getToken().equals(token);
    }

    /**
     * Логирует информацию о входящем обновлении.
     * 
     * @param update объект Update от Telegram
     */
    private void logUpdate(@NonNull Update update) {
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
