package ru.golubyatnikov.family.calendar.bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;

/**
 * Фабрика для создания кнопок клавиатур Telegram.
 * 
 * <p>KeyboardButtonFactory предоставляет удобные методы для создания
 * различных типов кнопок с правильной настройкой.</p>
 * 
 * <p><b>Требования:</b> 1.1</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-02
 */
@Component
@Slf4j
public class KeyboardButtonFactory {

    /**
     * Создает обычную кнопку клавиатуры с текстом.
     * 
     * @param text текст кнопки
     * @return настроенная KeyboardButton
     * @throws IllegalArgumentException если text равен null или пустой
     */
    public KeyboardButton createButton(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.error("Попытка создать кнопку с пустым текстом");
            throw new IllegalArgumentException("Текст кнопки не может быть пустым");
        }
        
        log.debug("Создание кнопки с текстом: '{}'", text);
        return new KeyboardButton(text);
    }

    /**
     * Создает inline кнопку с текстом и callback data.
     * 
     * @param text текст кнопки
     * @param callbackData данные для callback
     * @return настроенная InlineKeyboardButton
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardButton createInlineButton(String text, String callbackData) {
        if (text == null || text.trim().isEmpty()) {
            log.error("Попытка создать inline кнопку с пустым текстом");
            throw new IllegalArgumentException("Текст кнопки не может быть пустым");
        }
        
        if (callbackData == null || callbackData.trim().isEmpty()) {
            log.error("Попытка создать inline кнопку с пустым callbackData");
            throw new IllegalArgumentException("CallbackData не может быть пустым");
        }
        
        log.debug("Создание inline кнопки: text='{}', callbackData='{}'", text, callbackData);
        
        InlineKeyboardButton button = new InlineKeyboardButton(text);
        button.setCallbackData(callbackData);
        
        return button;
    }

    /**
     * Создает inline кнопку с URL.
     * 
     * @param text текст кнопки
     * @param url URL для перехода
     * @return настроенная InlineKeyboardButton
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardButton createUrlButton(String text, String url) {
        if (text == null || text.trim().isEmpty()) {
            log.error("Попытка создать URL кнопку с пустым текстом");
            throw new IllegalArgumentException("Текст кнопки не может быть пустым");
        }
        
        if (url == null || url.trim().isEmpty()) {
            log.error("Попытка создать URL кнопку с пустым URL");
            throw new IllegalArgumentException("URL не может быть пустым");
        }
        
        log.debug("Создание URL кнопки: text='{}', url='{}'", text, url);
        
        InlineKeyboardButton button = new InlineKeyboardButton(text);
        button.setUrl(url);
        
        return button;
    }

    /**
     * Создает пустую inline кнопку (для заполнения пространства).
     * 
     * @return настроенная InlineKeyboardButton
     */
    public InlineKeyboardButton createEmptyButton() {
        log.debug("Создание пустой inline кнопки");
        
        InlineKeyboardButton button = new InlineKeyboardButton(" ");
        button.setCallbackData("calendar_ignore");
        
        return button;
    }

    /**
     * Создает inline кнопку для навигации.
     * 
     * @param text текст кнопки
     * @param direction направление навигации
     * @param targetId идентификатор цели
     * @return настроенная InlineKeyboardButton
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardButton createNavigationButton(String text, String direction, String targetId) {
        if (text == null || text.trim().isEmpty()) {
            log.error("Попытка создать навигационную кнопку с пустым текстом");
            throw new IllegalArgumentException("Текст кнопки не может быть пустым");
        }
        
        if (direction == null || direction.trim().isEmpty()) {
            log.error("Попытка создать навигационную кнопку с пустым direction");
            throw new IllegalArgumentException("Direction не может быть пустым");
        }
        
        if (targetId == null || targetId.trim().isEmpty()) {
            log.error("Попытка создать навигационную кнопку с пустым targetId");
            throw new IllegalArgumentException("TargetId не может быть пустым");
        }
        
        String callbackData = direction + "_" + targetId;
        log.debug("Создание навигационной кнопки: text='{}', callbackData='{}'", text, callbackData);
        
        InlineKeyboardButton button = new InlineKeyboardButton(text);
        button.setCallbackData(callbackData);
        
        return button;
    }

    /**
     * Создает inline кнопку для действия с событием.
     * 
     * @param text текст кнопки
     * @param action действие
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardButton
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardButton createEventActionButton(String text, String action, Long eventId) {
        if (text == null || text.trim().isEmpty()) {
            log.error("Попытка создать кнопку действия с событием с пустым текстом");
            throw new IllegalArgumentException("Текст кнопки не может быть пустым");
        }
        
        if (action == null || action.trim().isEmpty()) {
            log.error("Попытка создать кнопку действия с событием с пустым action");
            throw new IllegalArgumentException("Action не может быть пустым");
        }
        
        if (eventId == null || eventId <= 0) {
            log.error("Попытка создать кнопку действия с событием с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        String callbackData = action + "_" + eventId;
        log.debug("Создание кнопки действия с событием: text='{}', callbackData='{}'", text, callbackData);
        
        InlineKeyboardButton button = new InlineKeyboardButton(text);
        button.setCallbackData(callbackData);
        
        return button;
    }

    /**
     * Создает inline кнопку для действия с вложением.
     * 
     * @param text текст кнопки
     * @param action действие
     * @param eventId идентификатор события
     * @param attachmentId идентификатор вложения
     * @return настроенная InlineKeyboardButton
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardButton createAttachmentActionButton(String text, String action, Long eventId, Long attachmentId) {
        if (text == null || text.trim().isEmpty()) {
            log.error("Попытка создать кнопку действия с вложением с пустым текстом");
            throw new IllegalArgumentException("Текст кнопки не может быть пустым");
        }
        
        if (action == null || action.trim().isEmpty()) {
            log.error("Попытка создать кнопку действия с вложением с пустым action");
            throw new IllegalArgumentException("Action не может быть пустым");
        }
        
        if (eventId == null || eventId <= 0) {
            log.error("Попытка создать кнопку действия с вложением с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        if (attachmentId == null || attachmentId <= 0) {
            log.error("Попытка создать кнопку действия с вложением с некорректным attachmentId: {}", attachmentId);
            throw new IllegalArgumentException("AttachmentId должен быть положительным числом");
        }
        
        String callbackData = action + "_" + eventId + "_" + attachmentId;
        log.debug("Создание кнопки действия с вложением: text='{}', callbackData='{}'", text, callbackData);
        
        InlineKeyboardButton button = new InlineKeyboardButton(text);
        button.setCallbackData(callbackData);
        
        return button;
    }
}
