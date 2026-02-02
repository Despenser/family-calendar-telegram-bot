package ru.golubyatnikov.family.calendar.bot.handler.callback.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

/**
 * Роутер для обработки callback queries вложений к событиям.
 * 
 * <p>Маршрутизирует запросы к специализированным обработчикам:</p>
 * <ul>
 *   <li>AttachmentListHandler - просмотр списка вложений</li>
 *   <li>AttachmentUploadHandler - добавление файлов</li>
 *   <li>AttachmentViewHandler - просмотр файлов</li>
 *   <li>AttachmentDeleteHandler - удаление файлов</li>
 *   <li>AttachmentNavigationHandler - навигация между экранами</li>
 * </ul>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentCallbackRouter implements CallbackHandler {
    
    private final TelegramMessageService messageService;
    private final AttachmentListHandler listHandler;
    private final AttachmentUploadHandler uploadHandler;
    private final AttachmentViewHandler viewHandler;
    private final AttachmentDeleteHandler deleteHandler;
    private final AttachmentNavigationHandler navigationHandler;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.ATTACH_FILE;
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        log.debug("Маршрутизация callback вложения: data='{}', userId={}", 
                callbackData, user.getId());
        
        // Проверка на null или пустые callback-данные
        if (callbackData == null || callbackData.isEmpty()) {
            log.error("Получены null или пустые callback-данные: userId={}", user.getId());
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("некорректные данные"));
            return;
        }
        
        // Формат: attach_file_{action}_{eventId}[_{attachmentId}]
        String payload = CallbackPrefix.ATTACH_FILE.extractPayload(callbackData);
        
        // Проверка на null или пустой payload после извлечения префикса
        if (payload == null || payload.isEmpty()) {
            log.error("Получен null или пустой payload после извлечения префикса: callbackData='{}', userId={}", 
                    callbackData, user.getId());
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("некорректный формат данных"));
            return;
        }
        
        String[] parts = payload.split("_");
        
        log.debug("Payload разобран: parts={}, length={}", java.util.Arrays.toString(parts), parts.length);
        
        if (parts.length < 2) {
            log.warn("Некорректный формат callback data (недостаточно частей): callbackData='{}', parts={}, userId={}", 
                    callbackData, java.util.Arrays.toString(parts), user.getId());
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("некорректный формат данных"));
            return;
        }
        
        String action = parts[0];
        log.debug("Определено действие: action={}", action);
        
        try {
            switch (action) {
                case "list" -> {
                    // Формат: list_{eventId}
                    if (parts.length < 2) {
                        log.warn("Недостаточно частей для действия 'list': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("не указан ID события"));
                        return;
                    }
                    Long eventId = Long.parseLong(parts[1]);
                    log.debug("Маршрутизация к AttachmentListHandler: eventId={}", eventId);
                    navigationHandler.handleBackToAttachments(eventId, user, chatId, messageId, callbackQueryId, callbackQuery);
                }
                case "add" -> {
                    // Формат: add_{eventId}
                    if (parts.length < 2) {
                        log.warn("Недостаточно частей для действия 'add': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("не указан ID события"));
                        return;
                    }
                    Long eventId = Long.parseLong(parts[1]);
                    log.debug("Маршрутизация к AttachmentUploadHandler: eventId={}", eventId);
                    uploadHandler.handleAddFile(eventId, user, chatId, messageId, callbackQueryId);
                }
                case "view" -> {
                    // Формат: view_{eventId}_{attachmentId}
                    if (parts.length < 3) {
                        log.warn("Недостаточно частей для действия 'view': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("не указан ID вложения"));
                        return;
                    }
                    Long eventId = Long.parseLong(parts[1]);
                    Long attachmentId = Long.parseLong(parts[2]);
                    log.debug("Маршрутизация к AttachmentViewHandler: eventId={}, attachmentId={}", eventId, attachmentId);
                    viewHandler.handleViewFile(attachmentId, eventId, user, chatId, messageId, callbackQueryId);
                }
                case "delete" -> {
                    // Формат: delete_{eventId}_{attachmentId}
                    if (parts.length < 3) {
                        log.warn("Недостаточно частей для действия 'delete': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("не указан ID вложения"));
                        return;
                    }
                    Long eventId = Long.parseLong(parts[1]);
                    Long attachmentId = Long.parseLong(parts[2]);
                    log.debug("Маршрутизация к AttachmentDeleteHandler: eventId={}, attachmentId={}", eventId, attachmentId);
                    deleteHandler.handleDeleteFile(attachmentId, eventId, user, chatId, messageId, callbackQueryId);
                }
                case "confirm" -> {
                    // Составное действие: confirm_delete_{eventId}_{attachmentId}
                    if (parts.length < 4) {
                        log.warn("Недостаточно частей для действия 'confirm': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("некорректный формат данных"));
                        return;
                    }
                    if (!parts[1].equals("delete")) {
                        log.warn("Некорректный subAction для 'confirm': ожидается 'delete', получено '{}', callbackData='{}', userId={}", 
                                parts[1], callbackData, user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("неподдерживаемое действие"));
                        return;
                    }
                    Long eventId = Long.parseLong(parts[2]);
                    Long attachmentId = Long.parseLong(parts[3]);
                    log.debug("Маршрутизация к AttachmentDeleteHandler (confirm): eventId={}, attachmentId={}", eventId, attachmentId);
                    deleteHandler.handleConfirmDelete(attachmentId, eventId, user, chatId, messageId, callbackQueryId);
                }
                case "cancel" -> {
                    // Составное действие: cancel_delete_{eventId} или cancel_add_{eventId}
                    if (parts.length < 3) {
                        log.warn("Недостаточно частей для действия 'cancel': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("некорректный формат данных"));
                        return;
                    }
                    
                    String subAction = parts[1];
                    Long eventId = Long.parseLong(parts[2]);
                    
                    if (subAction.equals("delete")) {
                        log.debug("Маршрутизация к AttachmentDeleteHandler (cancel): eventId={}", eventId);
                        deleteHandler.handleCancelDelete(eventId, user, chatId, messageId, callbackQueryId);
                    } else if (subAction.equals("add")) {
                        log.debug("Маршрутизация к AttachmentUploadHandler (cancel): eventId={}", eventId);
                        uploadHandler.handleCancelAddFile(eventId, user, chatId, messageId, callbackQueryId);
                    } else {
                        log.warn("Некорректный subAction для 'cancel': ожидается 'delete' или 'add', получено '{}', callbackData='{}', userId={}", 
                                subAction, callbackData, user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("неподдерживаемое действие"));
                    }
                }
                case "back" -> {
                    // Формат: back_{eventId}
                    if (parts.length < 2) {
                        log.warn("Недостаточно частей для действия 'back': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("не указан ID события"));
                        return;
                    }
                    Long eventId = Long.parseLong(parts[1]);
                    log.debug("Маршрутизация к AttachmentNavigationHandler (back): eventId={}", eventId);
                    navigationHandler.handleBackToEvent(eventId, user, chatId, messageId, callbackQueryId);
                }
                default -> {
                    log.warn("Неизвестное действие: action='{}', callbackData='{}', userId={}", 
                            action, callbackData, user.getId());
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.UNKNOWN_ACTION);
                }
            }
        } catch (NumberFormatException e) {
            log.error("Ошибка парсинга числа в callback data: callbackData='{}', parts={}, userId={}, error={}", 
                    callbackData, java.util.Arrays.toString(parts), user.getId(), e.getMessage());
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("некорректный формат ID"));
        } catch (ArrayIndexOutOfBoundsException e) {
            log.error("Ошибка доступа к элементу массива в callback data: callbackData='{}', parts={}, userId={}, error={}", 
                    callbackData, java.util.Arrays.toString(parts), user.getId(), e.getMessage());
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("некорректный формат данных"));
        } catch (Exception e) {
            log.error("Неожиданная ошибка при маршрутизации callback вложения: callbackData='{}', userId={}, error={}", 
                    callbackData, user.getId(), e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            throw e;
        }
    }
}
