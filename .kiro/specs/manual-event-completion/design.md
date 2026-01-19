# Документ проектирования: Ручное завершение событий

## Обзор

Данный документ описывает проектирование функциональности ручного завершения событий в Telegram-боте семейного календаря. Функциональность позволяет пользователям самостоятельно завершать активные события до их автоматического завершения планировщиком, что убирает события из списка активных и улучшает пользовательский опыт.

## Архитектура

Решение интегрируется в существующую архитектуру приложения и использует следующие компоненты:

### Существующие компоненты (модификация)

1. **EventService** - добавление метода `completeEvent()` для ручного завершения
2. **CallbackPrefix** - добавление нового префикса `COMPLETE_EVENT`
3. **EventCallbackHandler** - добавление обработки callback для завершения события
4. **MyEventsCommandHandler** - добавление кнопки "Завершить" в детали события
5. **Event** (модель) - использование существующих полей `status`, `completedAt`

### Новые компоненты

Новые компоненты не требуются - вся функциональность реализуется через расширение существующих классов.

## Компоненты и интерфейсы

### 1. EventService

Добавляется новый метод для ручного завершения события:

```java
/**
 * Завершает событие вручную.
 * 
 * <p>Метод выполняет следующие действия:</p>
 * <ol>
 *   <li>Проверяет существование события</li>
 *   <li>Проверяет права доступа (только создатель может завершить)</li>
 *   <li>Проверяет статус события (должно быть ACTIVE)</li>
 *   <li>Изменяет статус на COMPLETED</li>
 *   <li>Устанавливает completedAt в текущее время</li>
 *   <li>Записывает действие в историю изменений</li>
 *   <li>Отмечает все неотправленные напоминания как отправленные</li>
 * </ol>
 * 
 * @param eventId идентификатор события
 * @param userId идентификатор пользователя, завершающего событие
 * @return завершенное событие
 * @throws EventNotFoundException если событие не найдено
 * @throws UnauthorizedAccessException если пользователь не является создателем
 * @throws IllegalStateException если событие не в статусе ACTIVE
 */
public Event completeEvent(Long eventId, Long userId);
```

### 2. CallbackPrefix

Добавляется новый префикс в enum:

```java
/** Завершение события (формат: complete_event_{eventId}) */
COMPLETE_EVENT("complete_event_")
```

### 3. EventCallbackHandler

Расширяется метод `canHandle()` для обработки нового префикса:

```java
@Override
public boolean canHandle(String callbackData) {
    return CallbackPrefix.VIEW_EVENT.matches(callbackData) ||
           CallbackPrefix.EDIT_EVENT.matches(callbackData) ||
           CallbackPrefix.DELETE_EVENT.matches(callbackData) ||
           CallbackPrefix.EDIT_FIELD.matches(callbackData) ||
           CallbackPrefix.COMPLETE_EVENT.matches(callbackData);  // Новая строка
}
```

Добавляется обработчик в метод `handle()`:

```java
if (CallbackPrefix.COMPLETE_EVENT.matches(callbackData)) {
    handleCompleteEvent(callbackData, user.getId(), chatId, callbackQueryId);
}
```

Добавляется новый приватный метод:

```java
/**
 * Обрабатывает завершение события.
 * 
 * @param callbackData данные callback (формат: complete_event_{eventId})
 * @param userId идентификатор пользователя
 * @param chatId идентификатор чата
 * @param callbackQueryId идентификатор callback query
 */
private void handleCompleteEvent(String callbackData, Long userId, Long chatId, 
                                 String callbackQueryId);
```

### 4. MyEventsCommandHandler

Модифицируется метод создания клавиатуры для деталей события, чтобы добавить кнопку "Завершить событие" для активных событий:

```java
private InlineKeyboardMarkup createEventDetailsKeyboard(Event event, Long userId) {
    List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
    
    // Существующие кнопки (Редактировать, Удалить, и т.д.)
    // ...
    
    // Новая кнопка "Завершить событие" - только для активных событий и только для создателя
    if (event.getStatus() == Event.EventStatus.ACTIVE && event.belongsToUser(userId)) {
        List<InlineKeyboardButton> completeRow = new ArrayList<>();
        InlineKeyboardButton completeButton = new InlineKeyboardButton();
        completeButton.setText("✅ Завершить событие");
        completeButton.setCallbackData(
            CallbackPrefix.COMPLETE_EVENT.withPayload(event.getId().toString())
        );
        completeRow.add(completeButton);
        keyboard.add(completeRow);
    }
    
    // Остальные кнопки
    // ...
    
    return markup;
}
```

## Модели данных

Используются существующие поля модели `Event`:

- `status` (EventStatus) - изменяется с ACTIVE на COMPLETED
- `completedAt` (LocalDateTime) - устанавливается текущее время
- `completionNote` (String) - может быть добавлена пользователем после завершения

Новые поля не требуются.

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. 
Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Завершение изменяет статус и устанавливает время

*For any* активное событие и его создатель, когда создатель завершает событие, статус события должен измениться на COMPLETED, а поле completedAt должно быть установлено в текущее время (с точностью до нескольких секунд).

**Validates: Requirements 1.2, 1.3**

### Property 2: Только создатель может завершить событие

*For any* событие и пользователь, если пользователь не является создателем события, попытка завершения должна быть отклонена с UnauthorizedAccessException.

**Validates: Requirements 3.1, 3.2**

### Property 3: Завершенное событие исключается из списка активных

*For any* событие, после его завершения оно не должно появляться в результатах запроса активных событий (getUserEvents, getFilteredEvents с фильтром ACTIVE).

**Validates: Requirements 1.5**

### Property 4: Завершение записывается в историю изменений

*For any* событие, когда оно завершается вручную, в истории изменений должна появиться запись с типом UPDATED, fieldName="status", oldValue="ACTIVE" и newValue="COMPLETED".

**Validates: Requirements 1.4**

### Property 5: Напоминания отмечаются как отправленные при завершении

*For any* событие с неотправленными напоминаниями, когда событие завершается вручную, все его неотправленные напоминания должны быть отмечены как отправленные.

**Validates: Requirements 5.1**

### Property 6: Кнопка завершения отображается только для активных событий создателя

*For any* событие и пользователь, кнопка "Завершить событие" должна присутствовать в клавиатуре деталей события тогда и только тогда, когда статус события равен ACTIVE и пользователь является создателем события.

**Validates: Requirements 1.1, 4.1, 4.2, 4.3, 4.4**

### Property 7: Нельзя завершить неактивное событие

*For any* событие со статусом отличным от ACTIVE (COMPLETED, DELETED, DRAFT), попытка завершения должна быть отклонена с IllegalStateException.

**Validates: Requirements 4.2, 4.3, 4.4**

### Property 8: Подтверждение отправляется после завершения

*For any* событие, после успешного завершения пользователю должно быть отправлено сообщение с подтверждением и предложением добавить заметку.

**Validates: Requirements 2.1, 2.2**

## Обработка ошибок

### Типы ошибок

1. **EventNotFoundException** - событие с указанным ID не найдено
   - HTTP статус: 404
   - Сообщение пользователю: "❌ Событие не найдено"

2. **UnauthorizedAccessException** - пользователь не является создателем события
   - HTTP статус: 403
   - Сообщение пользователю: "❌ Только создатель события может его завершить"

3. **IllegalStateException** - событие не в статусе ACTIVE
   - HTTP статус: 400
   - Сообщение пользователю: "❌ Можно завершить только активное событие"

### Стратегия обработки

Все ошибки обрабатываются через аннотацию `@HandleCallbackErrors` в `EventCallbackHandler`, которая:
- Логирует ошибку с полным контекстом
- Отправляет пользователю понятное сообщение об ошибке
- Отвечает на callback query для снятия индикатора загрузки

## Стратегия тестирования

### Unit-тесты

1. **EventServiceTest.completeEvent()**
   - Успешное завершение активного события создателем
   - Попытка завершения несуществующего события (EventNotFoundException)
   - Попытка завершения чужого события (UnauthorizedAccessException)
   - Попытка завершения неактивного события (IllegalStateException)
   - Проверка установки completedAt
   - Проверка записи в историю

2. **EventCallbackHandlerTest.handleCompleteEvent()**
   - Обработка callback для завершения события
   - Отправка подтверждающего сообщения
   - Обработка ошибок

3. **MyEventsCommandHandlerTest.createEventDetailsKeyboard()**
   - Кнопка отображается для активного события создателя
   - Кнопка не отображается для завершенного события
   - Кнопка не отображается для удаленного события
   - Кнопка не отображается для черновика
   - Кнопка не отображается для чужого события

### Property-Based тесты

Используется библиотека **jqwik** для Java (уже используется в проекте).

Каждый property-based тест должен:
- Запускаться минимум 100 итераций
- Иметь комментарий с явной ссылкой на свойство из design.md
- Генерировать случайные валидные данные для тестирования

Примеры генераторов:
- Генератор событий с различными статусами
- Генератор пользователей (создатели и не создатели)
- Генератор ID событий и пользователей

## Последовательность взаимодействия

### Сценарий: Успешное завершение события

```
Пользователь -> Telegram Bot: /myevents
Telegram Bot -> MyEventsCommandHandler: handle()
MyEventsCommandHandler -> EventService: getUserEvents(userId)
EventService -> Telegram Bot: List<Event>
Telegram Bot -> Пользователь: Список событий с кнопками

Пользователь -> Telegram Bot: Нажатие "Просмотр" на событии
Telegram Bot -> EventCallbackHandler: handle(view_event_{id})
EventCallbackHandler -> MyEventsCommandHandler: handleViewEventDetails()
MyEventsCommandHandler -> EventService: getEventById(eventId)
EventService -> MyEventsCommandHandler: Event
MyEventsCommandHandler -> Telegram Bot: Детали события с кнопкой "✅ Завершить событие"

Пользователь -> Telegram Bot: Нажатие "✅ Завершить событие"
Telegram Bot -> EventCallbackHandler: handle(complete_event_{id})
EventCallbackHandler -> EventService: completeEvent(eventId, userId)
EventService -> EventRepository: findById(eventId)
EventRepository -> EventService: Event
EventService: Проверка прав доступа
EventService: Проверка статуса
EventService: event.setStatus(COMPLETED)
EventService: event.setCompletedAt(now)
EventService -> EventRepository: save(event)
EventService -> EventHistoryService: recordChange()
EventService -> ReminderService: markRemindersAsSent(eventId)
EventService -> EventCallbackHandler: Event
EventCallbackHandler -> Telegram Bot: "✅ Событие завершено"
Telegram Bot -> Пользователь: Подтверждение + предложение добавить заметку
```

## Интеграция с существующими компонентами

### EventCompletionScheduler

Автоматическое завершение планировщиком и ручное завершение используют одну и ту же логику:
- Оба устанавливают статус COMPLETED
- Оба устанавливают completedAt
- Оба записывают в историю
- Оба отмечают напоминания как отправленные

Разница:
- Планировщик завершает события автоматически по истечении времени
- Ручное завершение инициируется пользователем через UI

### Фильтрация событий

Метод `EventService.getUserEvents()` уже фильтрует события по статусу ACTIVE, поэтому завершенные события автоматически исключаются из списка.

### История изменений

Используется существующий `EventHistoryService.recordChange()` с параметрами:
- actionType: UPDATED
- fieldName: "status"
- oldValue: "ACTIVE"
- newValue: "COMPLETED"

## Безопасность

1. **Авторизация**: Проверка выполняется на уровне `EventService.completeEvent()` через метод `event.belongsToUser(userId)`

2. **Валидация**: 
   - Проверка существования события
   - Проверка статуса события (только ACTIVE)
   - Проверка прав доступа (только создатель)

3. **Транзакции**: Метод `completeEvent()` выполняется в транзакции (класс `EventService` аннотирован `@Transactional`)

## Производительность

1. **Индексы БД**: Используются существующие индексы:
   - `idx_events_user_status` для фильтрации по userId и status
   - `idx_events_status` для фильтрации по status

2. **Кэширование**: Не требуется, так как операция выполняется редко

3. **Оптимизация запросов**: Используется один запрос для получения события и один для обновления

## Мониторинг и логирование

Логирование на уровнях:

1. **DEBUG**: 
   - Начало обработки callback
   - Извлечение eventId из callback data

2. **INFO**:
   - Успешное завершение события с указанием eventId и userId

3. **WARN**:
   - Попытка завершить чужое событие
   - Попытка завершить неактивное событие

4. **ERROR**:
   - Ошибки при обработке callback
   - Ошибки при сохранении в БД
   - Ошибки при отправке сообщений

Формат логов:
```
log.info("Событие ID={} успешно завершено вручную пользователем ID={}", eventId, userId);
log.warn("Пользователь ID={} попытался завершить неактивное событие ID={} (статус: {})", 
         userId, eventId, event.getStatus());
```

## Миграции БД

Миграции БД не требуются - используются существующие поля модели `Event`.

## Конфигурация

Дополнительная конфигурация не требуется.

## Зависимости

Новые зависимости не требуются - используются существующие:
- Spring Boot
- Spring Data JPA
- Telegram Bots API
- Lombok
- SLF4J

## Альтернативные решения

### Альтернатива 1: Автоматическое завершение при просмотре

**Описание**: Автоматически завершать событие, когда пользователь просматривает его детали после истечения времени.

**Плюсы**:
- Не требует дополнительной кнопки
- Автоматизация процесса

**Минусы**:
- Неожиданное поведение для пользователя
- Нет явного контроля
- Может завершить событие случайно

**Решение**: Отклонено в пользу явной кнопки для лучшего UX.

### Альтернатива 2: Команда /complete

**Описание**: Добавить команду `/complete {eventId}` для завершения события.

**Плюсы**:
- Простая реализация
- Не требует изменений в UI

**Минусы**:
- Неудобно для пользователя (нужно знать ID)
- Не интуитивно
- Требует дополнительного ввода

**Решение**: Отклонено в пользу кнопки в UI для лучшего UX.

## Будущие улучшения

1. **Массовое завершение**: Возможность завершить несколько событий одновременно
2. **Отмена завершения**: Возможность вернуть событие в статус ACTIVE
3. **Автоматическое завершение с подтверждением**: Запрашивать подтверждение перед автоматическим завершением
4. **Статистика завершений**: Отслеживание процента вручную завершенных событий
