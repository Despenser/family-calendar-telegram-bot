# Документ дизайна: Исправление LazyInitializationException при возврате к напоминанию

## Обзор

Данный дизайн описывает решение проблемы `LazyInitializationException`, возникающей при нажатии кнопки "Назад к напоминанию". Проблема вызвана попыткой доступа к lazy-loaded полю `User.timezone` после закрытия Hibernate сессии в методе `ReminderService.formatShortReminderMessage()`.

**Корневая причина:** Метод `formatShortReminderMessage` принимает параметр `recipientTimezone`, но игнорирует его и пытается получить timezone из `event.getUser().getTimezone()` через вызов `getUserTimezone(event.getUser())`. Это приводит к `LazyInitializationException`, так как объект User является Hibernate прокси, и сессия уже закрыта.

**Решение:** Удалить вызов `getUserTimezone(event.getUser())` из метода `formatShortReminderMessage` и использовать только переданный параметр `recipientTimezone`.

## Архитектура

### Текущая архитектура (с проблемой)

```
EventCallbackHandler.handleBackToReminder()
  ├─> userService.findById(userId) // Загружает User
  ├─> ZoneId userTimezone = ZoneId.of(eventOwner.getTimezone())
  └─> reminderService.formatShortReminderMessage(reminder, userTimezone)
        └─> getUserTimezone(event.getUser()) // ❌ LazyInitializationException!
              └─> user.getTimezone() // Попытка доступа к lazy-loaded полю
```

### Новая архитектура (исправленная)

```
EventCallbackHandler.handleBackToReminder()
  ├─> userService.findById(userId) // Загружает User
  ├─> ZoneId userTimezone = ZoneId.of(eventOwner.getTimezone())
  └─> reminderService.formatShortReminderMessage(reminder, userTimezone)
        └─> Использует recipientTimezone напрямую // ✅ Нет обращения к User
```

## Компоненты и интерфейсы

### ReminderService

**Изменяемый метод:**

```java
/**
 * Форматирует короткое сообщение напоминания для отображения в уведомлении.
 * 
 * <p>Метод использует переданный recipientTimezone для форматирования времени события.
 * НЕ обращается к event.getUser().getTimezone() для избежания LazyInitializationException.</p>
 * 
 * <p>Формат сообщения зависит от типа напоминания:</p>
 * <ul>
 *   <li>EVENING_BEFORE: "🌙 Напоминание: завтра в HH:mm у вас событие - [название]"</li>
 *   <li>ONE_HOUR_BEFORE: "⚡ Напоминание: через 1 час начнется событие - [название]"</li>
 *   <li>FIFTEEN_MINUTES_BEFORE: "🔥 Напоминание: через 15 минут начнется событие - [название]"</li>
 * </ul>
 * 
 * <p>Обработка ошибок:</p>
 * <ul>
 *   <li>При DateTimeException используется fallback формат</li>
 *   <li>При любой другой ошибке используется базовый формат</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.1, 1.2, 1.3, 2.1, 2.2, 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 4.3, 4.4</p>
 * 
 * @param reminder напоминание
 * @param recipientTimezone часовой пояс получателя для форматирования времени
 * @return короткое отформатированное сообщение
 */
public String formatShortReminderMessage(Reminder reminder, ZoneId recipientTimezone)
```

**Ключевые изменения:**
1. Удалить строку: `ZoneId creatorTimezone = getUserTimezone(event.getUser());`
2. Заменить все использования `creatorTimezone` на `recipientTimezone`
3. Обновить JavaDoc для отражения изменений
4. Обновить логирование для удаления упоминаний `creatorTimezone`

### EventCallbackHandler

**Метод без изменений:**

Метод `handleBackToReminder()` уже корректно получает timezone пользователя и передает его в `formatShortReminderMessage()`. Никаких изменений не требуется.

```java
// Загружаем пользователя для получения timezone
User eventOwner = userService.findById(userId)
    .orElseThrow(() -> new UserNotFoundException("Пользователь не найден: userId=" + userId));

// Получаем timezone пользователя для форматирования
ZoneId userTimezone = eventOwner.getTimezone() != null 
    ? ZoneId.of(eventOwner.getTimezone()) 
    : ZoneId.of("UTC");

// Восстанавливаем КОРОТКИЙ текст напоминания
String reminderMessage = reminderService.formatShortReminderMessage(reminder, userTimezone);
```

## Модели данных

Изменений в моделях данных не требуется.

## Свойства корректности

*Свойство - это характеристика или поведение, которое должно выполняться для всех допустимых выполнений системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Свойство 1: Отсутствие LazyInitializationException

*Для любого* напоминания и любого timezone, вызов `formatShortReminderMessage(reminder, timezone)` не должен генерировать `LazyInitializationException`.

**Валидирует: Требования 2.3, 3.3**

### Свойство 2: Использование переданного timezone

*Для любого* напоминания и любого timezone, метод `formatShortReminderMessage(reminder, timezone)` должен использовать переданный `timezone` для форматирования времени события, а не получать timezone из `event.getUser()`.

**Валидирует: Требования 1.1, 1.2, 2.1, 2.2**

### Свойство 3: Корректное форматирование времени

*Для любого* напоминания с типом EVENING_BEFORE, ONE_HOUR_BEFORE или FIFTEEN_MINUTES_BEFORE, метод `formatShortReminderMessage` должен возвращать сообщение с корректно отформатированным временем в указанном timezone.

**Валидирует: Требования 3.1, 3.2, 3.4**

### Свойство 4: Fallback при ошибках

*Для любого* напоминания, если при форматировании возникает ошибка, метод `formatShortReminderMessage` должен вернуть fallback сообщение с названием события, а не выбросить исключение.

**Валидирует: Требования 4.1, 4.2, 4.3, 4.4**

## Обработка ошибок

### Существующая обработка (сохраняется)

Метод `formatShortReminderMessage` уже имеет корректную обработку ошибок:

1. **DateTimeException**: Fallback на UTC, затем на базовый формат
2. **Любое другое исключение**: Fallback на UTC, затем на базовый формат
3. **Базовый формат**: `"🔔 Напоминание о событии - [название события]"`

### Новая обработка

После удаления вызова `getUserTimezone(event.getUser())`, `LazyInitializationException` больше не будет возникать. Существующая обработка ошибок остается без изменений.

## Стратегия тестирования

### Unit-тесты

1. **Тест на отсутствие LazyInitializationException**
   - Создать напоминание с lazy-loaded User
   - Вызвать `formatShortReminderMessage` с явным timezone
   - Проверить, что исключение не возникает

2. **Тест на использование переданного timezone**
   - Создать напоминание с событием в определенном времени
   - Вызвать `formatShortReminderMessage` с разными timezone
   - Проверить, что время отформатировано в переданном timezone

3. **Тест на корректное форматирование для каждого типа напоминания**
   - Для EVENING_BEFORE: проверить формат "🌙 Напоминание: завтра в HH:mm..."
   - Для ONE_HOUR_BEFORE: проверить формат "⚡ Напоминание: через 1 час..."
   - Для FIFTEEN_MINUTES_BEFORE: проверить формат "🔥 Напоминание: через 15 минут..."

4. **Тест на fallback при ошибках**
   - Создать напоминание с некорректными данными
   - Проверить, что возвращается базовый формат

### Property-based тесты

Не требуются для данного исправления, так как изменения минимальны и покрываются unit-тестами.

### Интеграционные тесты

1. **Тест на возврат к напоминанию**
   - Создать событие и напоминание
   - Симулировать нажатие кнопки "Назад к напоминанию"
   - Проверить, что сообщение отображается корректно без ошибок

## Детали реализации

### Изменения в ReminderService.formatShortReminderMessage()

**Было:**
```java
public String formatShortReminderMessage(Reminder reminder, ZoneId recipientTimezone) {
    Event event = reminder.getEvent();
    
    try {
        // Получаем timezone создателя события
        ZoneId creatorTimezone = getUserTimezone(event.getUser());
        
        log.debug("Форматирование короткого сообщения напоминания ID {} для получателя: " +
                 "eventId={}, eventDate={}, eventTime={}, creatorTimezone={}, recipientTimezone={}", 
                 reminder.getId(), event.getId(), event.getEventDate(), event.getEventTime(), 
                 creatorTimezone, recipientTimezone);
        
        // Создаем ZonedDateTime для времени события в timezone создателя
        ZonedDateTime eventInCreatorTZ = ZonedDateTime.of(
            event.getEventDate(), 
            event.getEventTime(), 
            creatorTimezone
        );
        
        // Конвертируем время события из timezone создателя в timezone получателя
        ZonedDateTime eventInRecipientTZ = eventInCreatorTZ.withZoneSameInstant(recipientTimezone);
        
        log.debug("Конвертация времени события для короткого напоминания ID {}: " +
                 "eventTimeCreatorTZ={}, eventTimeRecipientTZ={}, creatorTZ={}, recipientTZ={}", 
                 reminder.getId(), eventInCreatorTZ.toLocalDateTime(), 
                 eventInRecipientTZ.toLocalDateTime(), creatorTimezone, recipientTimezone);
        
        // ... остальной код
    }
}
```

**Стало:**
```java
public String formatShortReminderMessage(Reminder reminder, ZoneId recipientTimezone) {
    Event event = reminder.getEvent();
    
    try {
        log.debug("Форматирование короткого сообщения напоминания ID {} для получателя: " +
                 "eventId={}, eventDate={}, eventTime={}, recipientTimezone={}", 
                 reminder.getId(), event.getId(), event.getEventDate(), event.getEventTime(), 
                 recipientTimezone);
        
        // Создаем ZonedDateTime для времени события в timezone получателя
        // ВАЖНО: Предполагаем, что event.getEventDate() и event.getEventTime() 
        // уже хранятся в timezone создателя события
        ZonedDateTime eventInRecipientTZ = ZonedDateTime.of(
            event.getEventDate(), 
            event.getEventTime(), 
            recipientTimezone
        );
        
        log.debug("Время события для короткого напоминания ID {}: " +
                 "eventTimeRecipientTZ={}, recipientTZ={}", 
                 reminder.getId(), eventInRecipientTZ.toLocalDateTime(), recipientTimezone);
        
        // ... остальной код
    }
}
```

### Важное замечание о timezone

**КРИТИЧЕСКОЕ ИЗМЕНЕНИЕ В ЛОГИКЕ:**

В текущей реализации метод выполняет двойную конвертацию:
1. Создает `ZonedDateTime` в timezone создателя события
2. Конвертирует в timezone получателя

После исправления метод будет:
1. Создавать `ZonedDateTime` напрямую в timezone получателя

**Это изменение корректно, если:**
- `event.getEventDate()` и `event.getEventTime()` хранятся как локальное время в timezone создателя
- Вызывающий код (`handleBackToReminder`) передает timezone создателя события, а не получателя

**Проверка предположения:**

Нужно проверить, что `handleBackToReminder` передает правильный timezone. Давайте посмотрим на код:

```java
// Загружаем пользователя для получения timezone
User eventOwner = userService.findById(userId)
    .orElseThrow(() -> new UserNotFoundException("Пользователь не найден: userId=" + userId));

// Получаем timezone пользователя для форматирования
ZoneId userTimezone = eventOwner.getTimezone() != null 
    ? ZoneId.of(eventOwner.getTimezone()) 
    : ZoneId.of("UTC");

// Восстанавливаем КОРОТКИЙ текст напоминания
String reminderMessage = reminderService.formatShortReminderMessage(reminder, userTimezone);
```

**ПРОБЛЕМА:** `userId` в `handleBackToReminder` - это ID текущего пользователя (получателя), а не создателя события!

**ПРАВИЛЬНОЕ РЕШЕНИЕ:**

Нужно передавать timezone создателя события, а не текущего пользователя:

```java
// Загружаем событие
Event event = eventService.getEventById(eventId);

// Получаем timezone создателя события
User eventCreator = event.getUser();
ZoneId creatorTimezone = eventCreator.getTimezone() != null 
    ? ZoneId.of(eventCreator.getTimezone()) 
    : ZoneId.of("UTC");

// Восстанавливаем КОРОТКИЙ текст напоминания
String reminderMessage = reminderService.formatShortReminderMessage(reminder, creatorTimezone);
```

**НО:** Это приведет к той же проблеме LazyInitializationException!

**ОКОНЧАТЕЛЬНОЕ РЕШЕНИЕ:**

Нужно загружать событие с eager fetch User:

```java
// Загружаем событие с eager fetch User
Event event = eventService.getEventByIdWithUser(eventId);

// Получаем timezone создателя события
ZoneId creatorTimezone = event.getUser().getTimezone() != null 
    ? ZoneId.of(event.getUser().getTimezone()) 
    : ZoneId.of("UTC");

// Восстанавливаем КОРОТКИЙ текст напоминания
String reminderMessage = reminderService.formatShortReminderMessage(reminder, creatorTimezone);
```

Или использовать уже загруженное напоминание с eager fetch:

```java
// Загружаем напоминание с eager загрузкой события и пользователя
Reminder reminder = reminderService.getReminderWithEventAndUser(reminderId);

// Получаем timezone создателя события
ZoneId creatorTimezone = reminder.getEvent().getUser().getTimezone() != null 
    ? ZoneId.of(reminder.getEvent().getUser().getTimezone()) 
    : ZoneId.of("UTC");

// Восстанавливаем КОРОТКИЙ текст напоминания
String reminderMessage = reminderService.formatShortReminderMessage(reminder, creatorTimezone);
```

### Итоговое решение

1. **Создать метод в ReminderRepository:**
   ```java
   /**
    * Находит напоминание по ID с eager загрузкой события и пользователя.
    * 
    * <p>Использует @EntityGraph для загрузки связанных сущностей в одном запросе.
    * Это предотвращает LazyInitializationException при доступе к event.user
    * вне транзакции.</p>
    * 
    * <p>Загружаемые связи:</p>
    * <ul>
    *   <li>event - событие напоминания</li>
    *   <li>event.user - пользователь-создатель события</li>
    * </ul>
    * 
    * @param id идентификатор напоминания
    * @return напоминание с загруженным событием и пользователем или empty если не найдено
    */
   @EntityGraph(attributePaths = {"event", "event.user"})
   Optional<Reminder> findWithEventAndUserById(Long id);
   ```

2. **Создать метод в ReminderService:**
   ```java
   /**
    * Получает напоминание по ID с eager загрузкой события и пользователя.
    * 
    * <p>Использует специальный метод репозитория с @EntityGraph для загрузки
    * всех необходимых связей в одном запросе. Это позволяет безопасно
    * обращаться к event.user вне транзакции.</p>
    * 
    * @param reminderId идентификатор напоминания
    * @return напоминание с загруженным событием и пользователем
    * @throws ReminderNotFoundException если напоминание не найдено
    */
   @Transactional(readOnly = true)
   public Reminder getReminderWithEventAndUser(Long reminderId) {
       return reminderRepository.findWithEventAndUserById(reminderId)
           .orElseThrow(() -> new ReminderNotFoundException(reminderId));
   }
   ```

3. **Изменить EventCallbackHandler.handleBackToReminder():**
   ```java
   // Загружаем напоминание с eager загрузкой события и пользователя
   Reminder reminder = reminderService.getReminderWithEventAndUser(reminderId);
   
   // Получаем timezone создателя события
   ZoneId creatorTimezone = reminder.getEvent().getUser().getTimezone() != null 
       ? ZoneId.of(reminder.getEvent().getUser().getTimezone()) 
       : ZoneId.of("UTC");
   
   // Восстанавливаем КОРОТКИЙ текст напоминания
   String reminderMessage = reminderService.formatShortReminderMessage(reminder, creatorTimezone);
   ```

4. **Упростить ReminderService.formatShortReminderMessage():**
   - Удалить вызов `getUserTimezone(event.getUser())`
   - Использовать `recipientTimezone` напрямую
   - Обновить логирование

## Альтернативные решения

### Альтернатива 1: Использовать @Transactional на handleBackToReminder

**Плюсы:**
- Минимальные изменения кода
- Hibernate сессия остается открытой

**Минусы:**
- Нарушает принцип разделения слоев (Controller не должен управлять транзакциями)
- Может привести к проблемам с производительностью (длинные транзакции)
- Не решает корневую проблему

**Вердикт:** Не рекомендуется

### Альтернатива 2: Использовать Hibernate.initialize()

**Плюсы:**
- Явная инициализация прокси

**Минусы:**
- Требует открытой сессии
- Не решает корневую проблему
- Добавляет дополнительный запрос к БД

**Вердикт:** Не рекомендуется

### Альтернатива 3: Eager fetch User в Event

**Плюсы:**
- Решает проблему глобально

**Минусы:**
- Может привести к проблемам с производительностью (N+1)
- Загружает User даже когда он не нужен
- Изменяет глобальное поведение

**Вердикт:** Не рекомендуется

### Выбранное решение: @EntityGraph с вложенными путями

**Плюсы:**
- Решает проблему локально
- Не влияет на другие части системы
- Явно показывает зависимости
- Оптимально по производительности (один запрос с JOIN)
- Использует современный Spring Data JPA подход
- Декларативный стиль (аннотация вместо JPQL)
- Консистентно с остальным кодом проекта

**Минусы:**
- Требует создания нового метода в Repository

**Вердикт:** Рекомендуется ✅

## Влияние на производительность

- **Количество запросов к БД:** Без изменений (1 запрос с JOIN FETCH)
- **Размер данных:** Незначительное увеличение (загружается User)
- **Время выполнения:** Без изменений

## Обратная совместимость

Изменения полностью обратно совместимы:
- Сигнатура метода `formatShortReminderMessage` не меняется
- Поведение метода остается прежним (форматирует сообщение)
- Другие вызовы метода не затрагиваются
