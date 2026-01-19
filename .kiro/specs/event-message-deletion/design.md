# Проектирование: Удаление сообщений событий из чата

## Обзор

Система должна автоматически удалять сообщения событий из чата Telegram при выполнении определенных операций (удаление навсегда, восстановление из корзины, удаление/завершение из списка /my_events). Это обеспечит чистоту интерфейса и предотвратит отображение устаревших сообщений.

Решение интегрируется с существующей архитектурой бота, используя уже имеющийся метод `TelegramMessageService.deleteMessage()` и поле `Event.messageId` для хранения идентификатора сообщения.

## Архитектура

### Текущая архитектура

Система уже имеет необходимые компоненты:

1. **TelegramMessageService** - содержит метод `deleteMessage(Long chatId, Integer messageId)` с обработкой всех граничных случаев
2. **Event.messageId** - поле для хранения ID сообщения Telegram
3. **TrashService** - управляет операциями с корзиной
4. **EventCallbackHandler** - обрабатывает callback для удаления и завершения событий
5. **MyEventsCommandHandler** - управляет списком моих событий

### Интеграционные точки

Необходимо добавить вызовы `deleteMessage()` в следующих местах:

1. **TrashService.permanentlyDelete()** - при окончательном удалении из корзины
2. **TrashService.restoreEvent()** - при восстановлении из корзины (удаляем старое сообщение перед созданием нового)
3. **EventCallbackHandler.handleDeleteEvent()** - при удалении события из списка /my_events
4. **EventCallbackHandler.handleCompleteEvent()** - при завершении события из списка /my_events

## Компоненты и интерфейсы

### Изменения в TrashService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class TrashService {
    private final EventRepository eventRepository;
    private final EventHistoryService eventHistoryService;
    private final ReminderService reminderService;
    private final TelegramMessageService messageService; // НОВАЯ ЗАВИСИМОСТЬ
    
    /**
     * Восстанавливает событие из корзины.
     * ИЗМЕНЕНИЕ: Добавлено удаление старого сообщения перед созданием нового.
     */
    @Transactional
    public Event restoreEvent(Long eventId, Long userId) {
        // ... существующая логика ...
        
        // НОВОЕ: Удаляем старое сообщение события перед восстановлением
        if (event.getMessageId() != null) {
            Long chatId = event.getUser().getTelegramId();
            messageService.deleteMessage(chatId, event.getMessageId().intValue());
            log.debug("Старое сообщение события удалено при восстановлении: eventId={}, messageId={}", 
                     eventId, event.getMessageId());
            // Сбрасываем messageId, чтобы при восстановлении создалось новое сообщение
            event.setMessageId(null);
        }
        
        // ... остальная логика восстановления ...
    }
    
    /**
     * Окончательно удаляет событие из системы.
     * ИЗМЕНЕНИЕ: Добавлено удаление сообщения события перед физическим удалением.
     */
    @Transactional
    public void permanentlyDelete(Long eventId, Long userId) {
        // ... существующая логика проверок ...
        
        // НОВОЕ: Удаляем сообщение события перед окончательным удалением
        if (event.getMessageId() != null) {
            Long chatId = event.getUser().getTelegramId();
            messageService.deleteMessage(chatId, event.getMessageId().intValue());
            log.debug("Сообщение события удалено при окончательном удалении: eventId={}, messageId={}", 
                     eventId, event.getMessageId());
        }
        
        // Окончательное удаление
        eventRepository.delete(event);
        
        log.info("Событие ID={} окончательно удалено пользователем ID={}", eventId, userId);
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
    private final EventService eventService;
    // ... другие зависимости ...
    
    /**
     * Обрабатывает удаление события.
     * ИЗМЕНЕНИЕ: Добавлено удаление сообщения события после удаления.
     */
    private void handleDeleteEvent(String callbackData, Long userId, Long chatId, 
                                   String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.DELETE_EVENT);
        
        log.info("Удаление события ID={} пользователем ID={}", eventId, userId);
        
        try {
            // Получаем событие перед удалением для сохранения messageId
            var event = eventService.getEventById(eventId);
            Long messageId = event.getMessageId();
            
            // Выполняем удаление события (перемещение в корзину)
            String response = myEventsCommandHandler.handleDeleteCallback(eventId, userId);
            
            // НОВОЕ: Удаляем сообщение события после успешного удаления
            if (messageId != null) {
                messageService.deleteMessage(chatId, messageId.intValue());
                log.debug("Сообщение события удалено после удаления: eventId={}, messageId={}", 
                         eventId, messageId);
            }
            
            messageService.sendMessage(chatId, response);
            messageService.answerCallbackQuery(callbackQueryId, "Обработано");
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при удалении события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage());
            throw new RuntimeException("Ошибка при удалении события", e);
        }
    }
    
    /**
     * Обрабатывает завершение события.
     * ИЗМЕНЕНИЕ: Добавлено удаление сообщения события после завершения.
     */
    private void handleCompleteEvent(String callbackData, Long userId, Long chatId, 
                                     String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.COMPLETE_EVENT);
        
        log.debug("Начало обработки завершения события: eventId={}, userId={}", eventId, userId);
        
        try {
            // Получаем событие перед завершением для сохранения messageId
            var eventBeforeCompletion = eventService.getEventById(eventId);
            Long messageId = eventBeforeCompletion.getMessageId();
            
            // Завершаем событие
            ru.golubyatnikov.family.calendar.bot.model.Event completedEvent = 
                eventService.completeEvent(eventId, userId);
            
            log.info("Событие ID={} успешно завершено вручную пользователем ID={}", 
                    eventId, userId);
            
            // НОВОЕ: Удаляем сообщение события после завершения
            if (messageId != null) {
                messageService.deleteMessage(chatId, messageId.intValue());
                log.debug("Сообщение события удалено после завершения: eventId={}, messageId={}", 
                         eventId, messageId);
            }
            
            // Формируем подтверждающее сообщение
            String message = formatMessage(
                "✅ Событие \"%s\" успешно завершено!\n\n" +
                "Хотите добавить заметку о том, как прошло событие?",
                completedEvent.getTitle()
            );
            
            // Создаем клавиатуру с кнопкой "Добавить заметку"
            InlineKeyboardMarkup keyboard = createCompletionNoteKeyboard(eventId);
            
            // Отправляем сообщение с клавиатурой
            messageService.sendMessage(chatId, message, keyboard);
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, "Событие завершено");
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка Telegram API при завершении события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при завершении события", e);
        }
    }
}
```

## Модели данных

Используются существующие модели без изменений:

- **Event** - уже содержит поле `messageId` типа `Long`
- **User** - содержит `telegramId` для определения chatId

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. 
Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Удаление сообщения при окончательном удалении

*For any* событие с непустым messageId, при окончательном удалении события из корзины, метод `deleteMessage` должен быть вызван с соответствующим chatId и messageId

**Validates: Requirements 1.1**

### Property 2: Удаление сообщения при восстановлении

*For any* событие с непустым messageId, при восстановлении события из корзины, метод `deleteMessage` должен быть вызван перед изменением статуса события

**Validates: Requirements 2.1**

### Property 3: Удаление сообщения при удалении из /my_events

*For any* событие с непустым messageId, при удалении события через список /my_events, метод `deleteMessage` должен быть вызван после успешного перемещения в корзину

**Validates: Requirements 3.1**

### Property 4: Удаление сообщения при завершении из /my_events

*For any* событие с непустым messageId, при завершении события через список /my_events, метод `deleteMessage` должен быть вызван после успешного изменения статуса на COMPLETED

**Validates: Requirements 4.1**

### Property 5: Graceful обработка отсутствующего messageId

*For any* событие с messageId равным null, операции удаления/восстановления/завершения должны выполняться успешно без попыток удаления сообщения

**Validates: Requirements 1.4, 2.4, 3.4, 4.4**

### Property 6: Логирование успешного удаления

*For any* успешный вызов `deleteMessage`, должна быть создана запись в логе уровня DEBUG с eventId и messageId

**Validates: Requirements 1.2, 2.2, 3.2, 4.2**

### Property 7: Продолжение выполнения при ошибке удаления

*For any* ошибка при вызове `deleteMessage`, основная операция (удаление/восстановление/завершение события) должна завершиться успешно

**Validates: Requirements 1.3, 2.3, 3.3, 4.3**

## Обработка ошибок

### Существующая обработка в TelegramMessageService.deleteMessage()

Метод `deleteMessage()` уже реализует полную обработку ошибок:

1. **Сообщение не найдено** - логирует INFO, не выбрасывает исключение
2. **Сообщение слишком старое** - логирует INFO, не выбрасывает исключение  
3. **Нет прав на удаление** - логирует WARN, не выбрасывает исключение
4. **Сетевые ошибки** - логирует WARN, не выбрасывает исключение
5. **Null параметры** - логирует ERROR, возвращается без действий

### Обработка в интеграционных точках

В местах вызова `deleteMessage()` не требуется дополнительная обработка ошибок, так как:

- Метод никогда не выбрасывает исключения
- Все ошибки логируются внутри метода
- Основная операция продолжается независимо от результата удаления сообщения

### Логирование

Уровни логирования:

- **DEBUG** - успешное удаление сообщения
- **INFO** - сообщение не найдено или слишком старое (нормальные ситуации)
- **WARN** - ошибки удаления, не влияющие на основную операцию
- **ERROR** - некорректные параметры (null chatId или messageId)

## Стратегия тестирования

### Unit тесты

1. **TrashServiceTest**
   - Проверка вызова `deleteMessage` при `permanentlyDelete` с непустым messageId
   - Проверка вызова `deleteMessage` при `restoreEvent` с непустым messageId
   - Проверка пропуска вызова при null messageId
   - Проверка сброса messageId после восстановления

2. **EventCallbackHandlerTest**
   - Проверка вызова `deleteMessage` при `handleDeleteEvent` с непустым messageId
   - Проверка вызова `deleteMessage` при `handleCompleteEvent` с непустым messageId
   - Проверка пропуска вызова при null messageId
   - Проверка продолжения выполнения при ошибке удаления сообщения

### Property-based тесты

Используем библиотеку **jqwik** (уже используется в проекте).

1. **Property 1: Удаление при окончательном удалении**
   - Генерируем случайные события с messageId
   - Вызываем `permanentlyDelete`
   - Проверяем вызов `deleteMessage` с правильными параметрами

2. **Property 2: Удаление при восстановлении**
   - Генерируем случайные события в корзине с messageId
   - Вызываем `restoreEvent`
   - Проверяем вызов `deleteMessage` перед изменением статуса

3. **Property 3: Graceful обработка null messageId**
   - Генерируем события с messageId = null
   - Вызываем все операции
   - Проверяем отсутствие вызовов `deleteMessage` и успешное выполнение

### Integration тесты

1. **EventMessageDeletionIntegrationTest**
   - Полный цикл: создание события → удаление → восстановление → окончательное удаление
   - Проверка удаления сообщений на каждом этапе
   - Проверка работы с реальной БД и mock TelegramMessageService

## Безопасность

### Проверка прав доступа

Все операции уже содержат проверки прав:

- `TrashService.permanentlyDelete()` - проверяет `event.belongsToUser(userId)`
- `TrashService.restoreEvent()` - проверяет `event.belongsToUser(userId)`
- `EventCallbackHandler` - использует `AuthorizationService` через аннотации

### Защита от race conditions

- Все операции выполняются в транзакциях (`@Transactional`)
- Удаление сообщения происходит после изменения статуса в БД
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

### Новые зависимости

- **TrashService** → **TelegramMessageService** (новая зависимость)

### Существующие зависимости

- **EventCallbackHandler** → **TelegramMessageService** (уже существует)
- **EventCallbackHandler** → **EventService** (уже существует)
- **TrashService** → **EventRepository** (уже существует)

## Миграция данных

Миграция не требуется - используются существующие поля и таблицы.

## Обратная совместимость

Решение полностью обратно совместимо:

- События без messageId обрабатываются корректно (проверка на null)
- Существующие события продолжают работать без изменений
- Новая функциональность активируется автоматически для событий с messageId

## Альтернативные решения

### Альтернатива 1: Асинхронное удаление через очередь

**Плюсы:**
- Не блокирует основную операцию
- Можно повторить при ошибке

**Минусы:**
- Избыточная сложность для простой операции
- Задержка в удалении сообщения
- Требует дополнительной инфраструктуры

**Решение:** Не используем, так как `deleteMessage()` уже не блокирует выполнение при ошибках

### Альтернатива 2: Удаление через scheduled задачу

**Плюсы:**
- Централизованная логика
- Можно обрабатывать batch

**Минусы:**
- Задержка в удалении
- Сложность определения, какие сообщения удалять
- Требует дополнительное поле для отметки "к удалению"

**Решение:** Не используем, так как требуется немедленное удаление

### Выбранное решение: Синхронное удаление в точке операции

**Плюсы:**
- Простота реализации
- Немедленное удаление
- Использует существующую инфраструктуру
- Graceful обработка ошибок

**Минусы:**
- Нет автоматических повторов при ошибке

**Обоснование:** Оптимальное решение для данной задачи, так как удаление сообщения не критично для основной операции и уже имеет полную обработку ошибок.
