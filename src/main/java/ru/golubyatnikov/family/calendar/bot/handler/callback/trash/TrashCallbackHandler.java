package ru.golubyatnikov.family.calendar.bot.handler.callback.trash;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.trash.TrashService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;

/**
 * Обработчик callback queries для операций с корзиной.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-17
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrashCallbackHandler implements CallbackHandler {
    
    private final TrashService trashService;
    private final CallbackDataExtractionService callbackDataExtractionService;
    
    /**
     * Возвращает префикс callback data для корзины.
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
     * @param callbackQuery объект callback query от Telegram
     * @param user авторизованный пользователь, выполняющий действие
     *
     * @throws Exception если произошла ошибка при обработке callback
     */
    @Override
    @HandleCallbackErrors
    public void handle(@NonNull CallbackQuery callbackQuery, @NonNull User user) throws Exception {
        CallbackQueryContext context = callbackDataExtractionService.extractContext(callbackQuery, user);
        
        if (CallbackPrefix.TRASH_RESTORE.matches(context.callbackData())) {
            Long eventId = Long.parseLong(CallbackPrefix.TRASH_RESTORE.extractPayload(context.callbackData()));
            handleRestore(context, eventId);

        } else if (CallbackPrefix.TRASH_DELETE.matches(context.callbackData())) {
            Long eventId = Long.parseLong(CallbackPrefix.TRASH_DELETE.extractPayload(context.callbackData()));
            handlePermanentDelete(context, eventId);

        } else {
            log.warn("Неизвестный формат callback data корзины: data='{}', userId={}",
                    context.callbackData(), user.getId());
        }
    }
    
    /**
     * Обрабатывает восстановление события из корзины.
     */
    private void handleRestore(@NonNull CallbackQueryContext context, Long eventId) {
        try {
            trashService.restoreEvent(eventId, context.getUserId());

        } catch (EventNotFoundException e) {
            log.error("Событие ID={} не найдено при попытке восстановления пользователем ID={}", 
                     eventId, context.getUserId(), e);

        } catch (UnauthorizedAccessException e) {
            log.error("Пользователь ID={} попытался восстановить чужое событие ID={}", 
                     context.getUserId(), eventId, e);

        } catch (Exception e) {
            log.error("Ошибка при восстановлении события ID={} пользователем ID={}: {}", 
                     eventId, context.getUserId(), e.getMessage(), e);
        }
    }
    
    /**
     * Обрабатывает окончательное удаление события.
     */
    private void handlePermanentDelete(@NonNull CallbackQueryContext context, Long eventId) {
        try {
            trashService.permanentlyDelete(eventId, context.getUserId());
            log.info("Событие ID={} успешно удалено навсегда пользователем ID={}", 
                    eventId, context.getUserId());

        } catch (EventNotFoundException e) {
            log.error("Событие ID={} не найдено при попытке удаления пользователем ID={}", 
                     eventId, context.getUserId(), e);

        } catch (UnauthorizedAccessException e) {
            log.error("Пользователь ID={} попытался удалить чужое событие ID={}", 
                     context.getUserId(), eventId, e);

        } catch (Exception e) {
            log.error("Ошибка при удалении события ID={} пользователем ID={}: {}", 
                     eventId, context.getUserId(), e.getMessage(), e);
        }
    }
}
