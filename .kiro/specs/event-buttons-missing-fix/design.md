# Дизайн: Исправление пропавших кнопок редактирования и удаления событий

## Обзор

Проблема с отсутствием кнопок редактирования и удаления событий требует систематического подхода к диагностике и исправлению. Анализ кода показывает, что инфраструктура для создания и обработки кнопок существует, но возможны проблемы с:

1. Форматированием текста сообщений (MarkdownV2)
2. Логированием для диагностики
3. Обработкой исключений при отправке

## Архитектура

Текущая архитектура обработки событий с кнопками:

```
MyEventsCommandHandler
    ↓
    ├─> EventService.getUserEvents() - получение событий
    ├─> formatEvent() - форматирование текста события
    ├─> KeyboardService.createEventActionsKeyboard() - создание кнопок
    └─> TelegramMessageService.sendMessageWithInlineKeyboard() - отправка
            ↓
            └─> SendMessage с parseMode="MarkdownV2"
```

Обработка нажатий на кнопки:

```
UpdateProcessor.processCallbackQuery()
    ↓
    ├─> "edit_event_*" → MyEventsCommandHandler.handleEditCallback()
    └─> "delete_event_*" → MyEventsCommandHandler.handleDeleteCallback()
```

## Компоненты и интерфейсы

### 1. MyEventsCommandHandler

**Текущая реализация:**
- Метод `handle()` отправляет каждое событие отдельным сообщением
- Использует `formatEvent()` для форматирования текста
- Вызывает `keyboardService.createEventActionsKeyboard()` для создания кнопок
- Обрабатывает исключения, но логирует только общую ошибку

**Необходимые изменения:**
- Добавить детальное логирование перед отправкой каждого сообщения
- Логировать содержимое клавиатуры (количество кнопок, callback data)
- Улучшить обработку исключений с выводом полного стека

### 2. KeyboardService

**Текущая реализация:**
- Метод `createEventActionsKeyboard()` создает две кнопки
- Устанавливает callback data в формате "edit_event_{id}" и "delete_event_{id}"
- Логирует создание клавиатуры на уровне DEBUG

**Необходимые изменения:**
- Добавить валидацию eventId перед созданием кнопок
- Логировать детали созданных кнопок (текст, callback data)
- Добавить проверку на null для всех компонентов клавиатуры

### 3. TelegramMessageService

**Текущая реализация:**
- Метод `sendMessageWithInlineKeyboard()` вызывает `sendMessage()`
- Использует parseMode="MarkdownV2"
- Обрабатывает TelegramApiException с retry механизмом
- Логирует ошибки через `handleTelegramApiError()`

**Необходимые изменения:**
- Добавить логирование текста сообщения при ошибке 400 (Bad Request)
- Улучшить вывод ошибок форматирования MarkdownV2
- Добавить fallback механизм для отправки без форматирования при ошибке парсинга

### 4. MarkdownFormatter

**Текущая функциональность:**
- Методы `escape()`, `bold()`, `code()` для экранирования
- Метод `formatMessage()` для форматирования с плейсхолдерами

**Потенциальные проблемы:**
- Возможны проблемы с экранированием специальных символов
- Нужно проверить корректность работы с числовыми плейсхолдерами

## Модели данных

### InlineKeyboardMarkup

```java
InlineKeyboardMarkup {
    List<List<InlineKeyboardButton>> keyboard;
}
```

### InlineKeyboardButton

```java
InlineKeyboardButton {
    String text;           // "✏️ Редактировать" или "🗑️ Удалить"
    String callbackData;   // "edit_event_{id}" или "delete_event_{id}"
}
```

### Event

```java
Event {
    Long id;
    String title;
    LocalDate eventDate;
    LocalTime eventTime;
    String description;
    // ... другие поля
}
```

## Свойства корректности

*Свойство - это характеристика или поведение, которое должно выполняться во всех допустимых выполнениях системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинопроверяемыми гарантиями корректности.*

### Свойство 1: Создание кнопок для всех событий

*Для любого* списка событий пользователя, система должна создать inline клавиатуру с двумя кнопками для каждого события.

**Validates: Requirements 2.1, 2.2, 2.3**

### Свойство 2: Корректность callback data

*Для любого* события с ID, callback data кнопки редактирования должен быть равен "edit_event_{id}", а кнопки удаления - "delete_event_{id}".

**Validates: Requirements 2.4, 2.5, 2.6**

### Свойство 3: Обработка callback queries

*Для любого* callback query с данными "edit_event_*" или "delete_event_*", система должна вызвать соответствующий обработчик и отправить answerCallbackQuery.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4**

### Свойство 4: Корректность экранирования MarkdownV2

*Для любого* текста события, содержащего специальные символы MarkdownV2, система должна корректно экранировать все специальные символы перед отправкой.

**Validates: Requirements 4.1, 4.2, 4.3**

### Свойство 5: Логирование при ошибках

*Для любой* ошибки при отправке сообщения с кнопками, система должна записать в лог детальную информацию об ошибке, включая текст сообщения и параметры клавиатуры.

**Validates: Requirements 1.1, 1.2, 1.3, 1.4, 5.1**

### Свойство 6: Продолжение обработки при ошибках

*Для любого* списка событий, если отправка одного события не удалась, система должна продолжить обработку остальных событий.

**Validates: Requirements 5.2**

## Обработка ошибок

### Типы ошибок

1. **TelegramApiRequestException (400 Bad Request)**
   - Причина: Некорректное форматирование MarkdownV2
   - Обработка: Логировать текст сообщения, попытаться отправить без форматирования
   - Метрика: "bad_request"

2. **TelegramApiRequestException (403 Forbidden)**
   - Причина: Бот заблокирован пользователем
   - Обработка: Логировать предупреждение, пропустить пользователя
   - Метрика: "forbidden"

3. **TelegramApiException (Network errors)**
   - Причина: Проблемы с сетью
   - Обработка: Retry с экспоненциальной задержкой (до 3 попыток)
   - Метрика: "network_error"

4. **IllegalArgumentException**
   - Причина: Некорректные параметры (null eventId, пустой текст)
   - Обработка: Логировать ошибку, пропустить событие
   - Метрика: "validation_error"

### Стратегия обработки

```java
for (Event event : userEvents) {
    try {
        // 1. Форматирование текста
        String eventText = formatEvent(event);
        
        // 2. Создание клавиатуры
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event.getId());
        
        // 3. Логирование перед отправкой
        log.debug("Отправка события ID={}: text={}, buttons={}", 
            event.getId(), eventText.substring(0, Math.min(50, eventText.length())), 
            keyboard.getKeyboard().size());
        
        // 4. Отправка сообщения
        messageService.sendMessageWithInlineKeyboard(chatId, eventText, keyboard);
        
    } catch (TelegramApiRequestException e) {
        if (e.getErrorCode() == 400) {
            // Попытка отправить без форматирования
            log.warn("Ошибка форматирования для события ID={}, отправка без Markdown", event.getId());
            sendWithoutFormatting(chatId, event);
        } else {
            log.error("Ошибка API при отправке события ID={}: {}", event.getId(), e.getMessage(), e);
        }
    } catch (Exception e) {
        log.error("Неожиданная ошибка при отправке события ID={}: {}", event.getId(), e.getMessage(), e);
    }
}
```

## Стратегия тестирования

### Unit тесты

1. **KeyboardServiceTest.testCreateEventActionsKeyboard()**
   - Проверка создания клавиатуры с двумя кнопками
   - Проверка корректности callback data
   - Проверка текста кнопок

2. **MyEventsCommandHandlerTest.testHandleWithEvents()**
   - Проверка вызова sendMessageWithInlineKeyboard для каждого события
   - Проверка обработки исключений
   - Проверка продолжения обработки при ошибке

3. **TelegramMessageServiceTest.testSendMessageWithInlineKeyboard()**
   - Проверка корректности формирования SendMessage
   - Проверка установки parseMode="MarkdownV2"
   - Проверка retry механизма

### Property-based тесты

1. **Property 1: Кнопки для всех событий**
   ```java
   @Property
   void allEventsHaveButtons(@ForAll List<Event> events) {
       // Для любого списка событий
       // Должно быть вызвано createEventActionsKeyboard() для каждого
       verify(keyboardService, times(events.size()))
           .createEventActionsKeyboard(anyLong());
   }
   ```

2. **Property 2: Корректность callback data**
   ```java
   @Property
   void callbackDataFormat(@ForAll @Positive Long eventId) {
       // Для любого положительного ID
       InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(eventId);
       
       String editCallback = keyboard.getKeyboard().get(0).get(0).getCallbackData();
       String deleteCallback = keyboard.getKeyboard().get(0).get(1).getCallbackData();
       
       assertEquals("edit_event_" + eventId, editCallback);
       assertEquals("delete_event_" + eventId, deleteCallback);
   }
   ```

3. **Property 3: Экранирование специальных символов**
   ```java
   @Property
   void markdownEscaping(@ForAll String text) {
       // Для любого текста
       String escaped = MarkdownFormatter.escape(text);
       
       // Не должно быть неэкранированных специальных символов
       assertFalse(escaped.matches(".*[^\\\\][_*\\[\\]()~`>#+=|{}.!-].*"));
   }
   ```

### Integration тесты

1. **Тест полного цикла отправки события с кнопками**
   - Создание тестового события
   - Вызов MyEventsCommandHandler.handle()
   - Проверка отправки сообщения с кнопками
   - Симуляция нажатия кнопки
   - Проверка обработки callback query

2. **Тест обработки ошибок форматирования**
   - Создание события с проблемными символами
   - Вызов handle()
   - Проверка логирования ошибки
   - Проверка fallback механизма

## Диагностические улучшения

### Дополнительное логирование

1. **В MyEventsCommandHandler.handle():**
   ```java
   log.info("Начало обработки /my_events: userId={}, eventsCount={}", 
       user.getId(), userEvents.size());
   
   for (Event event : userEvents) {
       log.debug("Обработка события: id={}, title={}, date={}", 
           event.getId(), event.getTitle(), event.getEventDate());
   }
   ```

2. **В KeyboardService.createEventActionsKeyboard():**
   ```java
   log.debug("Создание клавиатуры для события {}: editCallback={}, deleteCallback={}", 
       eventId, "edit_event_" + eventId, "delete_event_" + eventId);
   ```

3. **В TelegramMessageService.sendMessage():**
   ```java
   log.debug("Отправка сообщения с inline кнопками: chatId={}, textPreview={}, buttonsCount={}", 
       chatId, text.substring(0, Math.min(100, text.length())), countButtons(replyMarkup));
   ```

### Метрики

Добавить метрики для мониторинга:
- `event_message_sent_success` - успешная отправка события
- `event_message_sent_failure` - неудачная отправка события
- `event_button_created` - создание кнопок для события
- `event_callback_processed` - обработка callback от кнопки

## План исправления

### Фаза 1: Диагностика (приоритет: ВЫСОКИЙ)

1. Добавить детальное логирование в MyEventsCommandHandler
2. Добавить логирование в KeyboardService
3. Добавить логирование текста сообщения при ошибке 400
4. Запустить бота и воспроизвести проблему
5. Проанализировать логи для определения точной причины

### Фаза 2: Исправление (приоритет: ВЫСОКИЙ)

В зависимости от результатов диагностики:

**Если проблема в форматировании:**
- Исправить метод formatEvent() для корректного экранирования
- Добавить fallback механизм для отправки без форматирования
- Добавить тесты для проблемных случаев

**Если проблема в создании кнопок:**
- Проверить и исправить createEventActionsKeyboard()
- Добавить валидацию параметров
- Добавить тесты для граничных случаев

**Если проблема в отправке:**
- Проверить конфигурацию TelegramMessageService
- Проверить retry механизм
- Добавить дополнительную обработку ошибок

### Фаза 3: Тестирование (приоритет: СРЕДНИЙ)

1. Написать unit тесты для всех измененных компонентов
2. Написать property-based тесты для проверки корректности
3. Провести integration тестирование
4. Провести ручное тестирование с реальным ботом

### Фаза 4: Мониторинг (приоритет: НИЗКИЙ)

1. Добавить метрики для отслеживания проблем
2. Настроить алерты для критических ошибок
3. Создать dashboard для мониторинга отправки событий

## Альтернативные решения

### Альтернатива 1: Отправка без MarkdownV2

**Плюсы:**
- Гарантированно работает
- Нет проблем с экранированием

**Минусы:**
- Теряется визуальное форматирование
- Ухудшается UX

### Альтернатива 2: Использование HTML вместо MarkdownV2

**Плюсы:**
- Более предсказуемое экранирование
- Меньше специальных символов

**Минусы:**
- Требует переписывания всех методов форматирования
- Большой объем работы

### Альтернатива 3: Отправка кнопок отдельным сообщением

**Плюсы:**
- Разделение ответственности
- Проще отлаживать

**Минусы:**
- Два сообщения вместо одного
- Хуже UX

**Рекомендация:** Использовать текущий подход с улучшенным логированием и обработкой ошибок.

## Заключение

Проблема с отсутствием кнопок требует систематического подхода к диагностике. Основные направления:

1. **Улучшение логирования** для понимания точной причины
2. **Улучшение обработки ошибок** для предотвращения молчаливых сбоев
3. **Добавление fallback механизмов** для обеспечения работоспособности
4. **Расширение тестового покрытия** для предотвращения регрессий

После диагностики и определения точной причины, будет выбрано конкретное решение из предложенных в дизайне.
