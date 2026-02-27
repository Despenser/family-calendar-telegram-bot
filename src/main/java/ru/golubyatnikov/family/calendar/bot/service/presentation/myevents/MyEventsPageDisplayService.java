package ru.golubyatnikov.family.calendar.bot.service.presentation.myevents;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.MyEventsPageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.MyEventsPageKeyboardService;

/**
 * Сервис для отображения страниц списка событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-26
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MyEventsPageDisplayService {
    
    private final MyEventsPageFormattingService pageFormattingService;
    private final MyEventsPageKeyboardService keyboardService;
    private final TelegramMessageService messageService;
    
    /**
     * Отображает страницу событий или сообщение об отсутствии событий.
     * 
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @param eventsPage страница событий
     * @throws TelegramApiException при ошибке отправки сообщения
     */
    public void displayEventsPage(Long chatId, Integer messageId, Page<Event> eventsPage) 
            throws TelegramApiException {
        
        if (eventsPage.isEmpty()) {
            String noEventsMessage = pageFormattingService.buildNoEventsMessage();
            messageService.editMessageText(chatId, messageId, noEventsMessage, null);
            return;
        }
        
        String headerMessage = pageFormattingService.buildPageHeader(
            eventsPage.getTotalElements(),
            eventsPage.getNumber() + 1,
            eventsPage.getTotalPages()
        );
        
        InlineKeyboardMarkup keyboard = keyboardService.createEventsPageKeyboard(
            eventsPage.getContent(),
            eventsPage.getNumber(),
            eventsPage.getTotalPages()
        );
        
        messageService.editMessageText(chatId, messageId, headerMessage, keyboard);
    }
    
    /**
     * Отправляет новое сообщение со страницей событий или сообщением об отсутствии событий.
     * 
     * @param chatId идентификатор чата
     * @param eventsPage страница событий
     * @throws TelegramApiException при ошибке отправки сообщения
     */
    public void sendEventsPage(Long chatId, Page<Event> eventsPage) throws TelegramApiException {
        if (eventsPage.isEmpty()) {
            String noEventsMessage = pageFormattingService.buildNoEventsMessage();
            messageService.sendMessage(chatId, noEventsMessage);
            return;
        }
        
        String headerMessage = pageFormattingService.buildPageHeader(
            eventsPage.getTotalElements(),
            eventsPage.getNumber() + 1,
            eventsPage.getTotalPages()
        );
        
        InlineKeyboardMarkup keyboard = keyboardService.createEventsPageKeyboard(
            eventsPage.getContent(),
            eventsPage.getNumber(),
            eventsPage.getTotalPages()
        );
        
        messageService.sendMessage(chatId, headerMessage, keyboard);
    }
}
