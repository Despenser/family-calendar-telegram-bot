# Проектирование: Исправление дублирования событий и лишних уведомлений при удалении

## Обзор

Система имеет две проблемы при удалении первого события из списка "Мои события":

1. **Дублирование второго события**: Когда удаляется первое событие (с флагом `isMyEventsHeader=true`), второе событие отображается дважды - один раз с шапкой (обновленное сообщение) и один раз без шапки (старое сообщение).

2. **Лишнее уведомление**: После удаления события отправляется дополнительное сообщение с текстом "*Событие удалено*Событие успешно удалено из календаря.Используйте /my_events для просмотра оставшихся событий.", которое не нужно.

Решение включает:
- Удаление старого сообщения второго события перед его обновлением с шапкой
- Удаление отправки лишнего уведомления об удалении
- Использование только callback query ответа для подтверждения удаления

## Архитектура

### Текущая архитектура

Проблемные компоненты:

1. **EventService.deleteEvent()** - при передаче флага `isMyEventsHeader` следующему событию вызывает `sendOrUpdateEventMessage`, который пытается обновить существующее сообщение, но так как у события уже есть `messageId`, создается дублирование.

2. **EventCallbackHandler.handleDeleteEvent()** - после удаления события отправляет дополнительное сообщение с подтверждением, которое не нужно.

### Предлагаемые изменения

1. **EventService.deleteEvent()** - перед вызовом `sendOrUpdateEventMessage` для следующего события:
   - Удалить старое сообщение события через `telegramMessageService.deleteMessage()`
   - Сбросить `messageId` события в `null`
   - Вызвать `sendOrUpdateEventMessage`, который создаст новое сообщение с шапкой

2. **EventCallbackHandler.handleDeleteEvent()** - убрать отправку дополнительного сообщения:
   - Удалить вызов `messageService.sendMessage(chatId, response)`
   - Оставить только `messageService.answerCallbackQuery(callbackQueryId, "Событие удалено")`

## Компоненты и интерфейсы

### Изменения в EventService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {
    private final EventRepository eventRepository;
    private final TelegramMessageService telegramMessageService;
    private final BotMessageBuilder botMessageBuilder;
    private final KeyboardService keyboardService;
    // ... другие зависимости ...
    
    /**
     * Удаляет событие (перемещает в корзину).
     * 
     * ИЗМЕНЕНИЕ: Добавлено удаление старого сообщения следующего события
     * перед передачей ему флага isMyEventsHeader.
     */
    public void deleteEvent(Long eventId, Long userId) {
        log.debug("Перемещение события ID={} в корзину пользователем ID={}", eventId, userId);
        
        // ... существующая логика проверок ...
        
        // Если удаляется первое событие в списке "Мои события", передаем флаг следующему
        if (Boolean.TRUE.equals(event.getIsMyEventsHeader())) {
            log.debug("Удаляется первое событие в списке 'Мои события', ищем следующее событие");
            
            // Получаем список активных событий пользователя
            List<Event> userEvents = getUserEvents(event.getUser().getId());
            
            // Находим следующее событие
            Event nextEvent = userEvents.stream()
                .filter(e -> !e.getId().equals(eventId))
                .findFirst()
                .orElse(null);
            
            if (nextEvent != null) {
                log.debug("Найдено следующее событие ID={}, передаем флаг isMyEventsHeader", 
                         nextEvent.getId());
                
                // НОВОЕ: Удаляем старое сообщение следующего события перед обновлением
                if (nextEvent.getMessageId() != null) {
                    Long chatId = event.getUser().getTelegramId();
                    telegramMessageService.deleteMessage(chatId, nextEvent.getMessageId().intValue());
                    log.debug("Старое сообщение следующего события удалено: eventId={}, messageId={}", 
                             nextEvent.getId(), nextEvent.getMessageId());
                    
                    // Сбрасываем messageId, чтобы sendOrUpdateEventMessage создало новое сообщение
                    nextEvent.setMessageId(null);
                }
                
                // Устанавливаем флаг для следующего события
                nextEvent.setIsMyEventsHeader(true);
                saveEvent(nextEvent);
                
                log.info("Флаг isMyEventsHeader передан следующему событию ID={}", nextEvent.getId());
                
                // Обновляем сообщение следующего события с шапкой
                try {
                    sendOrUpdateEventMessage(nextEvent, event.getUser().getTelegramId());
                    log.info("Сообщение следующего события ID={} успешно обновлено с шапкой", 
                            nextEvent.getId());
                } catch (TelegramApiException e) {
                    log.error("Не удалось обновить сообщение нового первого события ID={}: {}", 
                             nextEvent.getId(), e.getMessage());
                    // Не прерываем удаление, продолжаем
                }
            } else {
                log.debug("Следующее событие не найдено, это было последнее событие пользователя");
            }
        }
        
        // Перемещение в корзину
        event.setStatus(Event.EventStatus.DELETED);
        event.setDeletedAt(LocalDateTime.now());
        eventRepository.save(event);
        
        log.info("Событие ID={} успешно перемещено в корзину пользователем ID={}", eventId, userId);
        
        // Запись в историю изменений
        eventHistoryService.recordDeletion(eventId, userId);
    }
}
```

### Изменения в EventCallbackHandler

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class EventCallbackHandler implements CallbackHandler {
    private final MyEventsCommandHandler myEventsCommandHandler;
    private final TelegramMessageService messageService;
    // ... другие зависимости ...
    
    /**
     * Обрабатывает удаление события.
     * 
     * ИЗМЕНЕНИЕ: Убрана отправка дополнительного сообщения с подтверждением.
     * Используется только callback query ответ.
     */
    private void handleDeleteEvent(String callbackData, Long userId, Long chatId, 
                                   Integer messageId, String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.DELETE_EVENT);
        
        log.info("Удаление события ID={} пользователем ID={}", eventId, userId);
        
        try {
            // Выполняем удаление события (перемещение в корзину)
            myEventsCommandHandler.handleDeleteCallback(eventId, userId);
            
            // Удаляем сообщение, из которого был вызван callback
            messageService.deleteMessage(chatId, messageId);
            log.debug("Сообщение события удалено после удаления: eventId={}, messageId={}", 
                     eventId, messageId);
            
            // ИЗМЕНЕНИЕ: Убрана отправка дополнительного сообщения
            // Старый код: messageService.sendMessage(chatId, response);
            
            // Отвечаем на callback query с подтверждением
            messageService.answerCallbackQuery(callbackQueryId, "Событие удалено");
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при удалении события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage());
            
            // При ошибке отправляем сообщение об ошибке
            try {
                messageService.sendMessage(chatId, "❌ Не удалось удалить событие. Попробуйте позже.");
                messageService.answerCallbackQuery(callbackQueryId, "Ошибка удаления");
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", ex.getMessage());
            }
            
            throw new RuntimeException("Ошибка при удалении события", e);
        }
    }
}
```

### Изменения в MyEventsCommandHandler

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class MyEventsCommandHandler {
    // ... зависимости ...
    
    /**
     * Обрабатывает callback удаления события.
     * 
     * ИЗМЕНЕНИЕ: Метод больше не возвращает сообщение для отправки,
     * так как подтверждение теперь отправляется через callback query ответ.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     */
    public void handleDeleteCallback(Long eventId, Long userId) {
        log.debug("Обработка callback удаления события ID={} пользователем ID={}", 
                eventId, userId);
        
        // Удаляем событие через сервис (он проверит права доступа)
        eventService.deleteEvent(eventId, userId);
        
        log.debug("Событие ID={} успешно удалено пользователем ID={}", eventId, userId);
    }
}
```

## Модели данных

Используются существующие модели без изменений:

- **Event** - содержит поля `messageId`, `isMyEventsHeader`, `status`, `deletedAt`
- **User** - содержит `telegramId` для определения chatId

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. 
Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Удаление старого сообщения перед обновлением

*For any* первое событие в списке "Мои события", при его удалении, если существует второе событие с непустым messageId, метод `deleteMessage` должен быть вызван для второго события перед вызовом `sendOrUpdateEventMessage`

**Validates: Requirements 1.1, 3.1**

### Property 2: Единственное отображение второго события

*For any* случай удаления первого события, второе событие должно иметь только один messageId после завершения операции, и этот messageId должен отличаться от исходного

**Validates: Requirements 1.2, 4.4**

### Property 3: Сброс messageId при удалении старого сообщения

*For any* событие, при удалении его старого сообщения перед передачей флага isMyEventsHeader, messageId события должен быть сброшен в null

**Validates: Requirements 3.2**

### Property 4: Создание нового сообщения при null messageId

*For any* событие с messageId равным null, при вызове `sendOrUpdateEventMessage` должен быть вызван метод `sendMessageAndGet`, а не `tryEditMessageText`

**Validates: Requirements 3.3**

### Property 5: Отсутствие дополнительных уведомлений

*For any* успешное удаление события, метод `sendMessage` не должен вызываться с текстом содержащим "Событие удалено"

**Validates: Requirements 2.1, 2.3, 4.3**

### Property 6: Ответ на callback query

*For any* успешное удаление события, метод `answerCallbackQuery` должен быть вызван с текстом "Событие удалено"

**Validates: Requirements 2.2, 4.2**

### Property 7: Удаление сообщения удаляемого события

*For any* удаляемое событие с непустым messageId, метод `deleteMessage` должен быть вызван для этого события

**Validates: Requirements 4.1**

### Property 8: Обработка ошибок при удалении старого сообщения

*For any* ошибка при удалении старого сообщения второго события, операция обновления события должна продолжиться, и должна быть создана запись в логе уровня WARN

**Validates: Requirements 1.4, 3.4**

### Property 9: Отправка сообщения об ошибке

*For any* ошибка при удалении события, метод `sendMessage` должен быть вызван с сообщением об ошибке

**Validates: Requirements 2.4**

## Обработка ошибок

### Обработка в EventService.deleteEvent()

1. **Ошибка при удалении старого сообщения следующего события**:
   - Логируется на уровне WARN (уже реализовано в `TelegramMessageService.deleteMessage()`)
   - Операция продолжается - флаг `isMyEventsHeader` устанавливается
   - `messageId` сбрасывается в `null` независимо от результата удаления
   - `sendOrUpdateEventMessage` вызывается и создает новое сообщение

2. **Ошибка при обновлении сообщения следующего события**:
   - Логируется на уровне ERROR
   - Операция удаления первого события продолжается
   - Флаг `isMyEventsHeader` остается установленным для следующего события

### Обработка в EventCallbackHandler.handleDeleteEvent()

1. **Ошибка при удалении события**:
   - Перехватывается `TelegramApiException`
   - Логируется на уровне ERROR
   - Отправляется сообщение об ошибке пользователю
   - Отправляется callback query ответ с текстом "Ошибка удаления"
   - Выбрасывается `RuntimeException` для обработки на верхнем уровне

2. **Ошибка при отправке сообщения об ошибке**:
   - Логируется на уровне ERROR
   - Не прерывает выполнение

### Логирование

Уровни логирования:

- **DEBUG** - успешное удаление старого сообщения следующего события
- **INFO** - успешное обновление сообщения следующего события с шапкой
- **WARN** - ошибки удаления сообщения, не влияющие на основную операцию
- **ERROR** - критические ошибки, влияющие на выполнение операции

## Стратегия тестирования

### Unit тесты

1. **EventServiceTest**
   - Проверка вызова `deleteMessage` для следующего события при удалении первого
   - Проверка сброса `messageId` следующего события в `null`
   - Проверка вызова `sendOrUpdateEventMessage` после удаления старого сообщения
   - Проверка продолжения операции при ошибке удаления старого сообщения
   - Проверка корректной работы при отсутствии следующего события

2. **EventCallbackHandlerTest**
   - Проверка отсутствия вызова `sendMessage` при успешном удалении
   - Проверка вызова `answerCallbackQuery` с правильным текстом
   - Проверка вызова `deleteMessage` для удаляемого события
   - Проверка отправки сообщения об ошибке при неудачном удалении

3. **MyEventsCommandHandlerTest**
   - Проверка, что метод `handleDeleteCallback` больше не возвращает строку
   - Проверка вызова `eventService.deleteEvent`

### Property-based тесты

Используем библиотеку **jqwik** (уже используется в проекте).

1. **Property 1: Удаление старого сообщения перед обновлением**
   - Генерируем случайные события с флагом `isMyEventsHeader=true`
   - Генерируем следующие события с непустым `messageId`
   - Вызываем `deleteEvent`
   - Проверяем порядок вызовов: `deleteMessage` → `sendOrUpdateEventMessage`

2. **Property 2: Единственное отображение второго события**
   - Генерируем случайные пары событий (первое и второе)
   - Вызываем `deleteEvent` для первого
   - Проверяем, что у второго события только один `messageId` и он отличается от исходного

3. **Property 5: Отсутствие дополнительных уведомлений**
   - Генерируем случайные события
   - Вызываем `handleDeleteEvent`
   - Проверяем, что `sendMessage` не вызывается с текстом "Событие удалено"

### Integration тесты

1. **EventDeletionIntegrationTest**
   - Полный цикл: создание двух событий → удаление первого → проверка отображения второго
   - Проверка отсутствия дублирования сообщений
   - Проверка отсутствия лишних уведомлений
   - Проверка работы с реальной БД и mock TelegramMessageService

## Безопасность

### Проверка прав доступа

Все операции уже содержат проверки прав:

- `EventService.deleteEvent()` - проверяет `event.belongsToUser(userId)`
- `EventCallbackHandler` - использует `AuthorizationService` через аннотации

### Защита от race conditions

- Все операции выполняются в транзакциях (`@Transactional`)
- Удаление старого сообщения происходит перед изменением `messageId` в БД
- При ошибке удаления сообщения транзакция не откатывается

## Производительность

### Оптимизации

1. **Асинхронность не требуется** - удаление сообщения быстрая операция (<100ms)
2. **Кэширование не требуется** - операции выполняются редко
3. **Batch операции не требуются** - удаление по одному сообщению

### Метрики

Существующие метрики в `TelegramMessageService`:
- Счетчик успешных удалений
- Счетчик ошибок по типам (not_found, too_old, network_error)

## Зависимости

### Существующие зависимости

- **EventService** → **TelegramMessageService** (уже существует)
- **EventService** → **EventRepository** (уже существует)
- **EventCallbackHandler** → **TelegramMessageService** (уже существует)
- **EventCallbackHandler** → **MyEventsCommandHandler** (уже существует)

### Изменения в зависимостях

Новых зависимостей не требуется - используются существующие.

## Миграция данных

Миграция не требуется - используются существующие поля и таблицы.

## Обратная совместимость

Решение полностью обратно совместимо:

- События без `messageId` обрабатываются корректно (проверка на null)
- Существующие события продолжают работать без изменений
- Изменения затрагивают только логику удаления первого события

## Альтернативные решения

### Альтернатива 1: Не удалять старое сообщение, а обновлять его

**Плюсы:**
- Меньше операций с Telegram API
- Сохраняется история сообщений

**Минусы:**
- Не решает проблему дублирования, так как `sendOrUpdateEventMessage` пытается обновить сообщение по `messageId`, но если обновление не удается, создается новое сообщение
- Сложнее отследить, какое сообщение является актуальным

**Решение:** Не используем, так как не решает основную проблему

### Альтернатива 2: Использовать флаг для отслеживания обновленных сообщений

**Плюсы:**
- Можно отследить, какие сообщения были обновлены

**Минусы:**
- Требует дополнительное поле в БД
- Усложняет логику
- Не решает проблему дублирования

**Решение:** Не используем, так как избыточно сложно

### Выбранное решение: Удаление старого сообщения и создание нового

**Плюсы:**
- Простота реализации
- Гарантирует отсутствие дублирования
- Использует существующую инфраструктуру
- Graceful обработка ошибок

**Минусы:**
- Две операции с Telegram API вместо одной (удаление + создание)

**Обоснование:** Оптимальное решение для данной задачи, так как гарантирует корректное отображение событий и использует уже имеющуюся обработку ошибок.

## Влияние на существующий функционал

### Затронутые компоненты

1. **EventService.deleteEvent()** - добавлена логика удаления старого сообщения следующего события
2. **EventCallbackHandler.handleDeleteEvent()** - убрана отправка дополнительного сообщения
3. **MyEventsCommandHandler.handleDeleteCallback()** - изменен возвращаемый тип с `String` на `void`

### Незатронутые компоненты

- **TrashService** - не затронут, так как работает с окончательным удалением из корзины
- **EventCompletionScheduler** - не затронут, так как работает с завершением событий
- **MyEventsCommandHandler.handle()** - не затронут, так как работает с отображением списка событий

### Регрессионное тестирование

Необходимо проверить:
- Удаление единственного события (не должно быть ошибок)
- Удаление последнего события (не должно быть попыток обновить несуществующее следующее событие)
- Удаление события, которое не является первым (не должно быть изменений в поведении)
- Восстановление события из корзины (не должно быть изменений в поведении)
