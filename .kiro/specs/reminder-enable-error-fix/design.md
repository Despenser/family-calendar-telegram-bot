# Design Document: Исправление ошибки при включении напоминаний

## Overview

При нажатии кнопки "🔔 Включить напоминания" возникает ошибка `LazyInitializationException: Could not initialize proxy [ru.golubyatnikov.family.calendar.bot.model.User#1] - no session`. Анализ стек-трейса показывает, что проблема возникает при попытке получить `user.getTimezone()` в методе `ReminderService.getUserTimezone()`.

### Причина ошибки

1. **В методе `ReminderCallbackHandler.handleEnableReminders` получается объект `Event` из репозитория**
2. **Из события извлекается `User` через `event.getUser()`**
3. **Объект `User` загружается как Hibernate lazy proxy (не инициализирован)**
4. **Метод `handleEnableReminders` НЕ имеет аннотации `@Transactional`**
5. **При вызове `reminderService.createDefaultReminders(event, user)` передается lazy proxy**
6. **Внутри `createDefaultReminders` вызывается `getUserTimezone(user)`, который пытается получить `user.getTimezone()`**
7. **Hibernate пытается инициализировать proxy, но сессия уже закрыта → `LazyInitializationException`**

### Решение

Есть несколько вариантов решения:

**Вариант 1: Eager fetch User при загрузке Event (РЕКОМЕНДУЕТСЯ)**
- Изменить запрос в `EventRepository.findById` для eager загрузки User
- Использовать `@EntityGraph` или `JOIN FETCH` в JPQL
- Преимущества: простое решение, не требует изменения транзакционных границ
- Недостатки: может повлиять на производительность других запросов

**Вариант 2: Добавить @Transactional на handleEnableReminders**
- Добавить аннотацию `@Transactional` на метод `handleEnableReminders`
- Преимущества: простое решение
- Недостатки: расширяет транзакционную границу, может привести к длинным транзакциям

**Вариант 3: Инициализировать User явно**
- Вызвать `Hibernate.initialize(event.getUser())` перед передачей в сервис
- Преимущества: явный контроль над инициализацией
- Недостатки: требует доступа к Hibernate API

**Выбранное решение: Вариант 1**

Мы выберем Вариант 1, так как:
1. Это самое чистое решение с точки зрения архитектуры
2. Не расширяет транзакционные границы
3. Не требует изменения бизнес-логики
4. User всегда нужен при работе с Event в контексте напоминаний

## Architecture

### Текущая архитектура (с проблемой)

```
ReminderCallbackHandlerImpl (Component)
  └─> @HandleCallbackErrors (AOP Aspect)
      └─> ReminderCallbackHandler (Component) [НЕТ @Transactional]
          └─> EventRepository.findById(eventId)
              └─> Event (с lazy User proxy) ❌
                  └─> ReminderService.createDefaultReminders(event, user)
                      └─> getUserTimezone(user)
                          └─> user.getTimezone() ❌ LazyInitializationException
```

### Исправленная архитектура

```
ReminderCallbackHandlerImpl (Component)
  └─> @HandleCallbackErrors (AOP Aspect)
      └─> ReminderCallbackHandler (Component) [НЕТ @Transactional]
          └─> EventRepository.findByIdWithUser(eventId) [EAGER FETCH]
              └─> Event (с инициализированным User) ✅
                  └─> ReminderService.createDefaultReminders(event, user)
                      └─> getUserTimezone(user)
                          └─> user.getTimezone() ✅ Работает
```

## Components and Interfaces

### EventRepository

**Изменения:**
- Добавить метод `findByIdWithUser(Long id)` с eager загрузкой User
- Использовать `@EntityGraph` или `@Query` с `JOIN FETCH`

**Обоснование:**
- Предотвращает LazyInitializationException
- Явно указывает, что User нужен при загрузке Event
- Не влияет на существующие методы

**Пример реализации:**

```java
@EntityGraph(attributePaths = {"user"})
Optional<Event> findByIdWithUser(Long id);
```

или

```java
@Query("SELECT e FROM Event e JOIN FETCH e.user WHERE e.id = :id")
Optional<Event> findByIdWithUser(@Param("id") Long id);
```

### ReminderCallbackHandler

**Изменения:**
- Изменить вызов `eventRepository.findById(eventId)` на `eventRepository.findByIdWithUser(eventId)`
- Добавить проверку, что User инициализирован
- Улучшить обработку ошибок

**Обоснование:**
- Гарантирует, что User загружен и инициализирован
- Предотвращает LazyInitializationException
- Улучшает читаемость кода (явно показывает, что User нужен)

### ReminderService

**Изменения:**
- Добавить проверку в методе `getUserTimezone`, что User не является proxy
- Добавить более детальное логирование

**Обоснование:**
- Дополнительная защита от LazyInitializationException
- Лучшая диагностика проблем

## Data Models

Изменений в моделях данных не требуется.

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Успешное создание напоминаний

*For any* события с установленными датой и временем, при нажатии кнопки "🔔 Включить напоминания" система должна успешно создать автоматические напоминания без ошибок.

**Validates: Requirements 2.1, 2.2**

### Property 2: Корректная обработка событий без времени

*For any* события без установленного времени, при нажатии кнопки "🔔 Включить напоминания" система должна вернуть сообщение "ℹ️ Добавьте время события для автоматических напоминаний" и не создавать напоминания.

**Validates: Requirements 3.1**

### Property 3: Корректная обработка событий в прошлом

*For any* события, время которого в прошлом, при нажатии кнопки "🔔 Включить напоминания" система должна вернуть сообщение "ℹ️ Событие слишком скоро, автоматические напоминания не созданы" и не создавать напоминания.

**Validates: Requirements 3.2**

### Property 4: Обновление клавиатуры после включения

*For any* события, после успешного включения напоминаний клавиатура события должна обновиться с кнопкой "🔕 Отключить напоминания" вместо "🔔 Включить напоминания".

**Validates: Requirements 3.4**

### Property 5: Логирование процесса

*For any* попытки включения напоминаний, система должна залогировать eventId, chatId, количество созданных напоминаний и их типы.

**Validates: Requirements 4.1, 4.2**

### Property 6: Graceful degradation при ошибках

*For any* ошибки при включении напоминаний, система должна залогировать полный стек-трейс и вернуть пользователю понятное сообщение об ошибке.

**Validates: Requirements 2.3, 4.3**

## Error Handling

### Текущие проблемы

1. **LazyInitializationException при доступе к User**
   - Причина: User загружается как lazy proxy и не инициализируется вне транзакции
   - Решение: использовать eager fetch при загрузке Event

2. **Недостаточно детальное логирование**
   - Причина: не все этапы процесса логируются
   - Решение: добавить логирование на каждом этапе

### Стратегия обработки ошибок

1. **На уровне EventRepository:**
   - Использовать `@EntityGraph` для eager загрузки User
   - Предотвращать LazyInitializationException на уровне загрузки данных

2. **На уровне ReminderCallbackHandler:**
   - Ловить `LazyInitializationException` отдельно
   - Ловить исключения от `ReminderService`
   - Формировать понятные сообщения для пользователя
   - Логировать с контекстом (eventId, chatId, callbackQueryId)

3. **На уровне ReminderService:**
   - Ловить все исключения в методе `createDefaultReminders`
   - Логировать с полным контекстом (eventId, userId, типы напоминаний)
   - Возвращать пустой список при ошибках (graceful degradation)

4. **На уровне AOP Aspect:**
   - Ловить все необработанные исключения
   - Отправлять generic сообщение об ошибке
   - Логировать с полным стек-трейсом

## Testing Strategy

### Unit Tests

1. **ReminderServiceTest.testCreateDefaultReminders**
   - Проверить успешное создание напоминаний для события с временем
   - Проверить возврат пустого списка для события без времени
   - Проверить возврат пустого списка для события в прошлом
   - Проверить корректность типов созданных напоминаний

2. **ReminderServiceTest.testCalculateReminderTimeWithTimezone**
   - Проверить корректность расчета времени для разных типов напоминаний
   - Проверить корректность конвертации timezone
   - Проверить fallback на UTC при ошибках

3. **ReminderCallbackHandlerTest.testHandleEnableReminders**
   - Проверить успешное включение напоминаний
   - Проверить обработку события без времени
   - Проверить обработку события в прошлом
   - Проверить обновление клавиатуры после включения

### Integration Tests

1. **ReminderEnableIntegrationTest**
   - Проверить полный flow включения напоминаний
   - Проверить создание напоминаний в БД
   - Проверить обновление сообщения события
   - Проверить отправку callback query ответа

### Property-Based Tests

Минимум 100 итераций для каждого теста.

1. **Property Test: Успешное создание напоминаний**
   - Генерировать случайные события с временем в будущем
   - Вызывать `createDefaultReminders`
   - Проверять, что создано корректное количество напоминаний
   - **Feature: reminder-enable-error-fix, Property 1: Успешное создание напоминаний**

2. **Property Test: Обработка событий без времени**
   - Генерировать случайные события без времени
   - Вызывать `createDefaultReminders`
   - Проверять, что возвращается пустой список
   - **Feature: reminder-enable-error-fix, Property 2: Корректная обработка событий без времени**

3. **Property Test: Обработка событий в прошлом**
   - Генерировать случайные события с временем в прошлом
   - Вызывать `createDefaultReminders`
   - Проверять, что возвращается пустой список
   - **Feature: reminder-enable-error-fix, Property 3: Корректная обработка событий в прошлом**

### Edge Cases

1. **Событие ровно в текущий момент времени**
   - Проверить, что напоминания не создаются

2. **Событие через 1 минуту**
   - Проверить, что создаются только те напоминания, время которых еще не наступило

3. **Событие с некорректным timezone**
   - Проверить fallback на UTC

4. **Событие без пользователя**
   - Проверить обработку ошибки

5. **Отключенные автоматические напоминания в конфигурации**
   - Проверить, что напоминания не создаются

6. **User загружен как lazy proxy**
   - Проверить, что при использовании `findByIdWithUser` User инициализирован
   - Проверить, что при использовании обычного `findById` возникает LazyInitializationException (для регрессионного теста)

## Implementation Notes

### Изменения в EventRepository

Добавить новый метод для eager загрузки User:

```java
/**
 * Находит событие по ID с eager загрузкой пользователя.
 * Используется в случаях, когда нужен доступ к полям User вне транзакции.
 * 
 * @param id идентификатор события
 * @return Optional с событием и инициализированным User, или empty если не найдено
 */
@EntityGraph(attributePaths = {"user"})
Optional<Event> findByIdWithUser(Long id);
```

### Изменения в ReminderCallbackHandler

```java
// БЫЛО:
Event event = eventRepository.findById(eventId)
    .orElseThrow(() -> new EventNotFoundException(eventId));
User user = event.getUser(); // Lazy proxy, не инициализирован

// СТАЛО:
Event event = eventRepository.findByIdWithUser(eventId)
    .orElseThrow(() -> new EventNotFoundException(eventId));
User user = event.getUser(); // Eager loaded, инициализирован
```

### Улучшение логирования в ReminderService.getUserTimezone

Добавить проверку на Hibernate proxy:

```java
private ZoneId getUserTimezone(User user) {
    // Проверка 1: Пользователь не null
    if (user == null) {
        log.error("Попытка получить timezone для null пользователя, используется UTC");
        return ZoneId.of("UTC");
    }
    
    // Проверка 2: Пользователь инициализирован (не Hibernate proxy)
    if (user instanceof org.hibernate.proxy.HibernateProxy) {
        log.warn("Пользователь является неинициализированным Hibernate proxy, " +
                "это может привести к LazyInitializationException. " +
                "Рекомендуется использовать eager fetch при загрузке Event.");
    }
    
    // Остальной код без изменений
    // ...
}
```

### Улучшение логирования

Добавить логирование на каждом этапе:

1. Начало процесса включения напоминаний
2. Получение события и пользователя
3. Вызов `createDefaultReminders`
4. Результат создания (количество напоминаний)
5. Формирование сообщения для пользователя
6. Обновление клавиатуры
7. Завершение процесса

### Обработка ошибок

Добавить try-catch блоки на каждом уровне:

1. **ReminderCallbackHandler.handleEnableReminders:**
   ```java
   try {
       // Используем findByIdWithUser для eager загрузки User
       Event event = eventRepository.findByIdWithUser(eventId)
           .orElseThrow(() -> new EventNotFoundException(eventId));
       User user = event.getUser();
       
       // Проверяем, что User инициализирован
       if (user == null) {
           log.error("User is null for event ID {}", eventId);
           messageService.answerCallbackQuery(callbackQueryId, 
               "❌ Ошибка: пользователь не найден");
           return;
       }
       
       List<Reminder> createdReminders = reminderService.createDefaultReminders(event, user);
       // ...
   } catch (LazyInitializationException e) {
       log.error("LazyInitializationException при включении напоминаний: eventId={}, error={}", 
               eventId, e.getMessage(), e);
       messageService.answerCallbackQuery(callbackQueryId, 
           "❌ Ошибка загрузки данных. Попробуйте еще раз.");
   } catch (Exception e) {
       log.error("Ошибка при включении напоминаний: eventId={}, chatId={}, error={}", 
               eventId, chatId, e.getMessage(), e);
       messageService.answerCallbackQuery(callbackQueryId, 
           "❌ Ошибка при включении напоминаний");
   }
   ```

2. **ReminderService.createDefaultReminders:**
   ```java
   try {
       LocalDateTime reminderTimeUTC = calculateReminderTimeWithTimezone(event, type, userTimezone, null);
       // ...
   } catch (Exception e) {
       log.error("Ошибка при создании автоматического напоминания типа {} для события ID {}: {}", 
                type, event.getId(), e.getMessage(), e);
       // Продолжаем создание остальных напоминаний
   }
   ```

2. **ReminderCallbackHandler.handleEnableReminders:**
   ```java
   try {
       List<Reminder> createdReminders = reminderService.createDefaultReminders(event, user);
       // ...
   } catch (Exception e) {
       log.error("Ошибка при включении напоминаний: eventId={}, chatId={}, error={}", 
               eventId, chatId, e.getMessage(), e);
       messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка при включении напоминаний");
   }
   ```

## Performance Considerations

Изменения не влияют на производительность, так как:

1. Изменение видимости метода не влияет на производительность
2. Дополнительное логирование минимально
3. Обработка ошибок уже присутствует в коде

## Security Considerations

Изменения не влияют на безопасность, так как:

1. Изменение видимости метода улучшает инкапсуляцию
2. Обработка ошибок предотвращает утечку информации через стек-трейсы

## Deployment Considerations

1. **Backward Compatibility:** Изменения полностью обратно совместимы
2. **Database Migrations:** Не требуются
3. **Configuration Changes:** Не требуются
4. **Rollback Strategy:** Простой откат к предыдущей версии при необходимости

## Future Improvements

1. **Рефакторинг метода `calculateReminderTimeWithTimezone`:**
   - Разбить на более мелкие методы
   - Вынести логику расчета для каждого типа в отдельные методы

2. **Улучшение обработки timezone:**
   - Добавить валидацию timezone при сохранении пользователя
   - Добавить UI для выбора timezone

3. **Мониторинг:**
   - Добавить метрики для отслеживания ошибок при создании напоминаний
   - Добавить алерты при превышении порога ошибок
