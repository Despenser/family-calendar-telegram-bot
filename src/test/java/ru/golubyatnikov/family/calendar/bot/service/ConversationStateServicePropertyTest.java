package ru.golubyatnikov.family.calendar.bot.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.mockito.Mockito;
import ru.golubyatnikov.family.calendar.bot.repository.ConversationStateRepository;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService.EditField;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService.EditingContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based тесты для ConversationStateService.
 * 
 * <p>Тесты проверяют свойства корректности при редактировании событий,
 * включая сохранение messageId, консистентность контекста и корректность
 * операций редактирования.</p>
 * 
 * <p><b>Feature: event-field-editing-fix</b></p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-19
 */
class ConversationStateServicePropertyTest {
    
    private final ConversationStateService service;
    
    public ConversationStateServicePropertyTest() {
        // Создаем моки для зависимостей
        ConversationStateRepository conversationStateRepository = Mockito.mock(ConversationStateRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        EventRepository eventRepository = Mockito.mock(EventRepository.class);
        
        this.service = new ConversationStateService(conversationStateRepository, userRepository, eventRepository);
    }
    
    /**
     * Property 1: Редактирование в одном сообщении
     * 
     * <p>Для любого события и любого редактируемого поля, при начале редактирования
     * messageId сообщения должен сохраниться в контексте редактирования и оставаться
     * неизменным на протяжении всего процесса редактирования.</p>
     * 
     * <p><b>Feature: event-field-editing-fix, Property 1: Редактирование в одном сообщении</b></p>
     * <p><b>Validates: Requirements 1.1, 1.2, 1.3, 1.4</b></p>
     */
    @Property(tries = 100)
    void messageIdPreservedDuringEditing(
            @ForAll @LongRange(min = 1, max = 1_000_000) Long userId,
            @ForAll @LongRange(min = 1, max = 1_000_000) Long eventId,
            @ForAll @LongRange(min = 1, max = 1_000_000) Long chatId,
            @ForAll @IntRange(min = 1, max = 1_000_000) Integer messageId,
            @ForAll("editFieldProvider") EditField field) {
        
        // Начинаем редактирование с messageId
        service.startEventEditing(userId, eventId, chatId, messageId);
        
        // Проверяем, что пользователь находится в режиме редактирования
        assertThat(service.isEditingEvent(userId))
            .as("Пользователь ID=%d должен быть в режиме редактирования", userId)
            .isTrue();
        
        // Проверяем, что messageId сохранен в контексте
        Integer savedMessageId = service.getEditingMessageId(userId);
        assertThat(savedMessageId)
            .as("MessageId должен быть сохранен в контексте редактирования")
            .isNotNull()
            .isEqualTo(messageId);
        
        // Проверяем, что контекст содержит правильные данные
        EditingContext context = service.getEditingContext(userId);
        assertThat(context)
            .as("Контекст редактирования должен существовать")
            .isNotNull();
        assertThat(context.getEventId())
            .as("EventId в контексте должен совпадать")
            .isEqualTo(eventId);
        assertThat(context.getChatId())
            .as("ChatId в контексте должен совпадать")
            .isEqualTo(chatId);
        assertThat(context.getMessageId())
            .as("MessageId в контексте должен совпадать")
            .isEqualTo(messageId);
        
        // Устанавливаем редактируемое поле
        service.setEditingField(userId, field);
        
        // Проверяем, что messageId не изменился после установки поля
        Integer messageIdAfterFieldSet = service.getEditingMessageId(userId);
        assertThat(messageIdAfterFieldSet)
            .as("MessageId не должен измениться после установки редактируемого поля")
            .isEqualTo(messageId);
        
        // Проверяем, что поле установлено корректно
        EditingContext updatedContext = service.getEditingContext(userId);
        assertThat(updatedContext.getCurrentField())
            .as("Редактируемое поле должно быть установлено")
            .isEqualTo(field);
        
        // Проверяем, что messageId все еще не изменился
        assertThat(updatedContext.getMessageId())
            .as("MessageId должен оставаться неизменным на протяжении всего процесса редактирования")
            .isEqualTo(messageId);
    }
    
    /**
     * Property 2: Возврат к просмотру после завершения
     * 
     * <p>Для любого события, после завершения редактирования любого поля
     * (успешного обновления или отмены), состояние редактирования должно быть
     * очищено, но messageId должен оставаться доступным до момента очистки.</p>
     * 
     * <p><b>Feature: event-field-editing-fix, Property 2: Возврат к просмотру после завершения</b></p>
     * <p><b>Validates: Requirements 1.5, 2.2</b></p>
     */
    @Property(tries = 100)
    void editingContextClearedAfterCompletion(
            @ForAll @LongRange(min = 1, max = 1_000_000) Long userId,
            @ForAll @LongRange(min = 1, max = 1_000_000) Long eventId,
            @ForAll @LongRange(min = 1, max = 1_000_000) Long chatId,
            @ForAll @IntRange(min = 1, max = 1_000_000) Integer messageId,
            @ForAll("editFieldProvider") EditField field) {
        
        // Начинаем редактирование с messageId
        service.startEventEditing(userId, eventId, chatId, messageId);
        service.setEditingField(userId, field);
        
        // Проверяем, что контекст существует и содержит messageId
        assertThat(service.isEditingEvent(userId))
            .as("Пользователь должен быть в режиме редактирования")
            .isTrue();
        
        Integer savedMessageId = service.getEditingMessageId(userId);
        assertThat(savedMessageId)
            .as("MessageId должен быть сохранен перед завершением")
            .isEqualTo(messageId);
        
        // Завершаем редактирование (очищаем контекст)
        service.clearEventEditing(userId);
        
        // Проверяем, что состояние редактирования очищено
        assertThat(service.isEditingEvent(userId))
            .as("После завершения редактирования пользователь не должен быть в режиме редактирования")
            .isFalse();
        
        // Проверяем, что контекст больше не доступен
        EditingContext clearedContext = service.getEditingContext(userId);
        assertThat(clearedContext)
            .as("Контекст редактирования должен быть null после очистки")
            .isNull();
        
        // Проверяем, что messageId больше не доступен
        Integer clearedMessageId = service.getEditingMessageId(userId);
        assertThat(clearedMessageId)
            .as("MessageId должен быть null после очистки контекста")
            .isNull();
    }
    
    /**
     * Property 3: Замена контекста при редактировании другого события
     * 
     * <p>Для любого пользователя, если он начинает редактирование нового события
     * во время редактирования предыдущего, старый контекст должен быть заменен
     * новым с новым messageId.</p>
     * 
     * <p><b>Validates: Requirements 6.5</b></p>
     */
    @Property(tries = 100)
    void contextReplacedWhenEditingDifferentEvent(
            @ForAll @LongRange(min = 1, max = 1_000_000) Long userId,
            @ForAll @LongRange(min = 1, max = 1_000_000) Long firstEventId,
            @ForAll @LongRange(min = 1, max = 1_000_000) Long secondEventId,
            @ForAll @LongRange(min = 1, max = 1_000_000) Long chatId,
            @ForAll @IntRange(min = 1, max = 1_000_000) Integer firstMessageId,
            @ForAll @IntRange(min = 1, max = 1_000_000) Integer secondMessageId) {
        
        // Предполагаем, что события разные
        Assume.that(!firstEventId.equals(secondEventId));
        Assume.that(!firstMessageId.equals(secondMessageId));
        
        // Начинаем редактирование первого события
        service.startEventEditing(userId, firstEventId, chatId, firstMessageId);
        
        // Проверяем первый контекст
        EditingContext firstContext = service.getEditingContext(userId);
        assertThat(firstContext)
            .as("Первый контекст должен существовать")
            .isNotNull();
        assertThat(firstContext.getEventId())
            .as("EventId первого контекста должен совпадать")
            .isEqualTo(firstEventId);
        assertThat(firstContext.getMessageId())
            .as("MessageId первого контекста должен совпадать")
            .isEqualTo(firstMessageId);
        
        // Начинаем редактирование второго события (заменяем контекст)
        service.startEventEditing(userId, secondEventId, chatId, secondMessageId);
        
        // Проверяем, что контекст заменен
        EditingContext secondContext = service.getEditingContext(userId);
        assertThat(secondContext)
            .as("Второй контекст должен существовать")
            .isNotNull();
        assertThat(secondContext.getEventId())
            .as("EventId должен быть обновлен на второе событие")
            .isEqualTo(secondEventId);
        assertThat(secondContext.getMessageId())
            .as("MessageId должен быть обновлен на второе сообщение")
            .isEqualTo(secondMessageId);
        
        // Проверяем, что старый контекст больше не доступен
        assertThat(secondContext.getEventId())
            .as("Старый eventId не должен быть доступен")
            .isNotEqualTo(firstEventId);
        assertThat(secondContext.getMessageId())
            .as("Старый messageId не должен быть доступен")
            .isNotEqualTo(firstMessageId);
    }
    
    /**
     * Провайдер для генерации случайных полей редактирования.
     */
    @Provide
    Arbitrary<EditField> editFieldProvider() {
        return Arbitraries.of(EditField.values());
    }
}
