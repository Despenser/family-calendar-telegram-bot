package ru.golubyatnikov.family.calendar.bot.handler.callback.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

/**
 * Роутер для обработки callback queries вложений к событиям.
 * Маршрутизирует запросы к соответствующим обработчикам.
 * 
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentCallbackRouter implements CallbackHandler {
    
    private final CallbackQueryService callbackQueryService;
    private final CallbackDataExtractionService callbackDataExtractionService;
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
    public void handle(@NonNull CallbackQuery callbackQuery, @NonNull User user) throws Exception {
        CallbackQueryContext context = callbackDataExtractionService.extractContext(callbackQuery, user);
        
        String payload = extractAndValidatePayload(context);
        if (payload == null) {
            return;
        }
        
        String[] parts = payload.split("_");
        if (parts.length < 2) {
            sendValidationError(context, "некорректный формат данных");
            return;
        }
        
        try {
            routeAction(parts, callbackQuery, context);

        } catch (NumberFormatException e) {
            log.error("Ошибка парсинга ID: userId={}", user.getId());
            callbackQueryService.answerCallback(context, 
                    CallbackMessageFormatter.validationError("некорректный формат ID"));
        }
    }
    
    /**
     * Извлекает и валидирует payload из callback data.
     * 
     * @param context контекст callback query
     * @return извлеченный payload или null при ошибке валидации
     */
    private @Nullable String extractAndValidatePayload(@NonNull CallbackQueryContext context) {
        if (context.callbackData().isEmpty()) {
            sendValidationError(context, "некорректные данные");
            return null;
        }
        
        String payload = CallbackPrefix.ATTACH_FILE.extractPayload(context.callbackData());
        if (payload.isEmpty()) {
            sendValidationError(context, "некорректный формат данных");
            return null;
        }
        
        return payload;
    }
    
    /**
     * Маршрутизирует действие к соответствующему обработчику.
     * 
     * @param parts массив частей callback data
     * @param callbackQuery объект callback query
     * @param context контекст callback query
     *
     * @throws Exception если произошла ошибка при обработке действия
     */
    private void routeAction(@NonNull String[] parts,
                             CallbackQuery callbackQuery,
                             CallbackQueryContext context) throws Exception {
        
        String action = parts[0];
        
        switch (action) {
            case "list" -> handleList(parts, callbackQuery, context);
            case "add" -> handleAdd(parts, context);
            case "view" -> handleView(parts, context);
            case "delete" -> handleDelete(parts, context);
            case "confirm" -> handleConfirm(parts, context);
            case "cancel" -> handleCancel(parts, context);
            case "back" -> handleBack(parts, context);
            default -> callbackQueryService.answerCallback(context, CallbackMessages.UNKNOWN_ACTION);
        }
    }
    
    private void handleList(String[] parts,
                            CallbackQuery callbackQuery,
                            CallbackQueryContext context) throws Exception {

        validatePartsLength(parts, 2, context, "не указан ID события");
        Long eventId = Long.parseLong(parts[1]);
        Integer page = parts.length > 2
                ? Integer.parseInt(parts[2])
                : null;

        navigationHandler.handleBackToAttachments(eventId, context, callbackQuery, page);
    }
    
    private void handleAdd(String[] parts, CallbackQueryContext context) throws Exception {
        validatePartsLength(parts, 2, context, "не указан ID события");
        Long eventId = Long.parseLong(parts[1]);
        Integer page = parts.length > 2
                ? Integer.parseInt(parts[2])
                : null;

        uploadHandler.handleAddFile(eventId, context, page);
    }
    
    private void handleView(String[] parts, CallbackQueryContext context) throws Exception {
        validatePartsLength(parts, 3, context, "не указан ID вложения");
        Long eventId = Long.parseLong(parts[1]);
        Long attachmentId = Long.parseLong(parts[2]);
        Integer page = parts.length > 3
                ? Integer.parseInt(parts[3])
                : null;

        viewHandler.handleViewFile(attachmentId, eventId, context, page);
    }
    
    private void handleDelete(String[] parts, CallbackQueryContext context) throws Exception {
        validatePartsLength(parts, 3, context, "не указан ID вложения");
        Long eventId = Long.parseLong(parts[1]);
        Long attachmentId = Long.parseLong(parts[2]);
        Integer page = parts.length > 3
                ? Integer.parseInt(parts[3])
                : null;

        deleteHandler.handleDeleteFile(attachmentId, eventId, context, page);
    }
    
    private void handleConfirm(String[] parts, CallbackQueryContext context) throws Exception {
        validatePartsLength(parts, 4, context, "некорректный формат данных");
        
        if (!"delete".equals(parts[1])) {
            sendValidationError(context, "неподдерживаемое действие");
            return;
        }
        
        Long eventId = Long.parseLong(parts[2]);
        Long attachmentId = Long.parseLong(parts[3]);
        Integer page = parts.length > 4 ? Integer.parseInt(parts[4]) : null;
        deleteHandler.handleConfirmDelete(attachmentId, eventId, context, page);
    }
    
    private void handleCancel(String[] parts, CallbackQueryContext context) throws Exception {
        validatePartsLength(parts, 3, context, "некорректный формат данных");
        
        String subAction = parts[1];
        Long eventId = Long.parseLong(parts[2]);
        Integer page = parts.length > 3 ? Integer.parseInt(parts[3]) : null;
        
        if ("delete".equals(subAction)) {
            deleteHandler.handleCancelDelete(eventId, context, page);

        } else if ("add".equals(subAction)) {
            uploadHandler.handleCancelAddFile(eventId, context, page);

        } else {
            sendValidationError(context, "неподдерживаемое действие");
        }
    }
    
    private void handleBack(String[] parts, CallbackQueryContext context) throws Exception {
        validatePartsLength(parts, 2, context, "не указан ID события");
        Long eventId = Long.parseLong(parts[1]);
        Integer page = parts.length > 2 ? Integer.parseInt(parts[2]) : null;
        navigationHandler.handleBackToEvent(eventId, context, page);
    }
    
    /**
     * Валидирует длину массива частей callback data.
     * 
     * @param parts массив частей для валидации
     * @param requiredLength требуемая минимальная длина
     * @param context контекст callback query
     * @param errorMessage сообщение об ошибке
     *
     * @throws IllegalArgumentException если длина массива меньше требуемой
     */
    private void validatePartsLength(@NonNull String[] parts,
                                     int requiredLength,
                                     CallbackQueryContext context,
                                     String errorMessage) {

        if (parts.length < requiredLength) {
            sendValidationError(context, errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
    }
    
    /**
     * Отправляет ошибку валидации пользователю.
     * 
     * @param context контекст callback query
     * @param message текст сообщения об ошибке
     */
    private void sendValidationError(CallbackQueryContext context, String message) {
        try {
            callbackQueryService.answerCallback(context, 
                    CallbackMessageFormatter.validationError(message));

        } catch (Exception e) {
            log.error("Ошибка при отправке callback ответа: {}", e.getMessage());
        }
    }
}
