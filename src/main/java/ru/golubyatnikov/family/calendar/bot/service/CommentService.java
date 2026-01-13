package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.Comment;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.CommentRepository;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;

import java.util.List;

/**
 * Сервис для управления комментариями к событиям.
 * Предоставляет функциональность для добавления, получения комментариев
 * и уведомления членов семьи о новых комментариях.
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Добавление комментариев к событиям</li>
 *   <li>Получение списка комментариев события</li>
 *   <li>Уведомление членов семьи о новых комментариях (только для семейных событий)</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 21.2, 21.3, 21.4, 21.5</p>
 * 
 * @author Family Calendar Bot
 * @version 1.0
 * @see Comment
 * @see CommentRepository
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class CommentService {
    
    private final CommentRepository commentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final TelegramMessageService telegramMessageService;
    
    /**
     * Добавляет комментарий к событию.
     * 
     * <p>После добавления комментария к семейному событию, все члены семьи
     * (кроме автора комментария) получают уведомление.</p>
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя, добавляющего комментарий
     * @param text текст комментария
     * @return сохраненный комментарий
     * @throws EventNotFoundException если событие не найдено
     * @throws UserNotFoundException если пользователь не найден
     * @throws IllegalArgumentException если текст комментария пустой
     */
    public Comment addComment(Long eventId, Long userId, String text) {
        log.debug("Добавление комментария к событию ID {}: userId={}, textLength={}", 
                  eventId, userId, text != null ? text.length() : 0);
        
        if (text == null || text.isBlank()) {
            log.error("Попытка добавить пустой комментарий: eventId={}, userId={}", eventId, userId);
            throw new IllegalArgumentException("Текст комментария не может быть пустым");
        }
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        Comment comment = Comment.builder()
            .event(event)
            .user(user)
            .text(text)
            .build();
        
        Comment saved = commentRepository.save(comment);
        log.info("Комментарий ID {} успешно добавлен к событию ID {} пользователем ID {}", 
                 saved.getId(), eventId, userId);
        
        // Уведомить семью о новом комментарии (только для семейных событий)
        if (!event.getIsPersonal()) {
            notifyFamilyAboutComment(event, user, text);
        } else {
            log.debug("Событие ID {} является персональным, уведомления семье не отправляются", eventId);
        }
        
        return saved;
    }
    
    /**
     * Получает все комментарии события, отсортированные по дате создания.
     * 
     * @param eventId идентификатор события
     * @return список комментариев события, отсортированный по дате создания (от старых к новым)
     */
    @Transactional(readOnly = true)
    public List<Comment> getEventComments(Long eventId) {
        log.debug("Получение комментариев для события ID {}", eventId);
        
        List<Comment> comments = commentRepository.findByEventIdOrderByCreatedAtAsc(eventId);
        
        log.debug("Найдено {} комментариев для события ID {}", comments.size(), eventId);
        return comments;
    }
    
    /**
     * Отправляет уведомления всем членам семьи о новом комментарии.
     * 
     * <p>Уведомление отправляется только для семейных событий.
     * Автор комментария не получает уведомление.</p>
     * 
     * @param event событие, к которому добавлен комментарий
     * @param author автор комментария
     * @param commentText текст комментария
     */
    private void notifyFamilyAboutComment(Event event, User author, String commentText) {
        log.debug("Отправка уведомлений семье о новом комментарии к событию ID {}", event.getId());
        
        if (event.getFamily() == null || event.getFamily().getMembers() == null) {
            log.warn("Событие ID {} не имеет семьи или членов семьи", event.getId());
            return;
        }
        
        List<User> familyMembers = event.getFamily().getMembers();
        int notificationsSent = 0;
        int notificationsFailed = 0;
        
        for (User member : familyMembers) {
            // Не отправлять уведомление автору комментария
            if (member.getId().equals(author.getId())) {
                continue;
            }
            
            try {
                String message = formatCommentNotification(event, author, commentText);
                telegramMessageService.sendMessage(member.getTelegramId(), message);
                notificationsSent++;
                
                log.debug("Уведомление о комментарии отправлено пользователю ID {} (telegramId={})", 
                         member.getId(), member.getTelegramId());
                
            } catch (TelegramApiException e) {
                notificationsFailed++;
                log.error("Ошибка при отправке уведомления о комментарии пользователю ID {} (telegramId={}): {}", 
                         member.getId(), member.getTelegramId(), e.getMessage());
            }
        }
        
        log.info("Уведомления о комментарии к событию ID {} отправлены: успешно={}, ошибок={}", 
                 event.getId(), notificationsSent, notificationsFailed);
    }
    
    /**
     * Форматирует текст уведомления о новом комментарии.
     * 
     * @param event событие, к которому добавлен комментарий
     * @param author автор комментария
     * @param commentText текст комментария
     * @return отформатированное сообщение для отправки
     */
    private String formatCommentNotification(Event event, User author, String commentText) {
        String authorName = author.getFirstName() != null ? author.getFirstName() : author.getUsername();
        
        // Обрезать длинный комментарий для уведомления
        String truncatedComment = commentText.length() > 100 
            ? commentText.substring(0, 100) + "..." 
            : commentText;
        
        return String.format(
            "💬 *Новый комментарий к событию*\n\n" +
            "📅 Событие: *%s*\n" +
            "👤 Автор: %s\n" +
            "💭 Комментарий: %s",
            event.getTitle(),
            authorName,
            truncatedComment
        );
    }
}
