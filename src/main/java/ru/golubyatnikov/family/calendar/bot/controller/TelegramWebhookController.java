package ru.golubyatnikov.family.calendar.bot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.golubyatnikov.family.calendar.bot.config.TelegramApiConfig;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.UpdateProcessor;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.authorization.WebhookSecurityService;

/**
 * REST контроллер для приема webhook обновлений от Telegram Bot API.
 * Валидирует входящие запросы с помощью secret token.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    private final UpdateProcessor updateProcessor;
    private final WebhookSecurityService webhookSecurityService;
    private final TelegramApiConfig telegramApiConfig;

    /**
     * Обрабатывает входящие webhook обновления от Telegram Bot API.
     *
     * @param secretToken secret token из заголовка X-Telegram-Bot-Api-Secret-Token
     * @param update объект Update от Telegram, содержащий информацию о событии
     * @return ResponseEntity с HTTP 200 OK при успешной обработке, или HTTP 401 Unauthorized при невалидном токене
     */
    @PostMapping
    public ResponseEntity<Void> onUpdateReceived(
            @RequestHeader(value = "#{telegramApiConfig.secretTokenHeader}", required = false) String secretToken,
            @NonNull @RequestBody Update update) {

        if (!webhookSecurityService.validateSecretToken(secretToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        logUpdate(update);
        updateProcessor.processUpdate(update);

        return ResponseEntity.ok().build();
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
