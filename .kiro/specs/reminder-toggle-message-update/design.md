# Design Document

## Overview

Данный дизайн описывает исправление функциональности включения/отключения напоминаний для событий в Telegram боте. Основная проблема заключается в том, что при нажатии кнопок "🔕 Отключить напоминания" и "🔔 Включить напоминания" пользователь видит только всплывающее уведомление (callback query answer), но само сообщение события не обновляется с новой клавиатурой. Это создает впечатление, что действие не выполнилось.

Дополнительно будет исправлен порядок кнопок в клавиатуре события: кнопка "Завершить" будет перемещена в самый низ, а кнопка управления напоминаниями будет располагаться выше неё.

## Architecture

Решение включает модификацию двух компонентов:

1. **ReminderCallbackHandler** - добавление логики обновления сообщения события после включения/отключения напоминаний
2. **KeyboardService** - изменение порядка кнопок в клавиатуре события

Архитектурный поток:

```
User clicks button → ReminderCallbackHandlerImpl → ReminderCallbackHandler
                                                    ↓
                                    1. Disable/Enable reminders (ReminderService)
                                    2. Answer callback query (TelegramMessageService)
                                    3. Build event message (BotMessageBuilder)
                                    4. Create updated keyboard (KeyboardService)
                                    5. Update message (TelegramMessageService)
```

## Components and Interfaces

### ReminderCallbackHandler

**Изменяемые методы:**

```java
/**
 * Обрабатывает отключение всех автоматических напоминаний для события.
 * После отключения обновляет сообщение события с новой клавиатурой.
 */
public void handleDisableReminders(Long eventId, Long chatId, Integer messageId, String callbackQueryId)

/**
 * Обрабатывает включение автоматических напоминаний для события.
 * После включения обновляет сообщение события с новой клавиатурой.
 */
public void handleEnableReminders(Long eventId, Long chatId, Integer messageId, String callbackQueryId)
```

**Зависимости:**
- `EventRepository` - для получения события
- `ReminderService` - для включения/отключения напоминаний
- `TelegramMessageService` - для ответа на callback query и обновления сообщения
- `BotMessageBuilder` - для формирования текста сообщения события
- `KeyboardService` - для создания обновленной клавиатуры

### KeyboardService

**Изменяемый метод:**

```java
/**
 * Создает inline клавиатуру для управления событием.
 * Порядок кнопок:
 * Ряд 1: [✏️ Редактировать] [🗑️ Удалить]
 * Ряд 2: [📎 Вложения]
 * Ряд 3: [🔕 Отключить напоминания] или [🔔 Включить напоминания] (только для активных событий создателя)
 * Ряд 4: [✅ Завершить] (только для активных событий создателя)
 */
public InlineKeyboardMarkup createEventActionsKeyboard(Event event, Long userId)
```

## Data Models

Изменения в моделях данных не требуются. Используются существующие модели:

- `Event` - модель события
- `User` - модель пользователя
- `Reminder` - модель напоминания

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Обновление сообщения при отключении напоминаний

*For any* активное событие с напоминаниями, когда пользователь нажимает кнопку "🔕 Отключить напоминания", сообщение события должно быть обновлено с клавиатурой, содержащей кнопку "🔔 Включить напоминания" вместо "🔕 Отключить напоминания"

**Validates: Requirements 1.1, 1.3**

### Property 2: Обновление сообщения при включении напоминаний

*For any* активное событие без напоминаний, когда пользователь нажимает кнопку "🔔 Включить напоминания", сообщение события должно быть обновлено с клавиатурой, содержащей кнопку "🔕 Отключить напоминания" вместо "🔔 Включить напоминания"

**Validates: Requirements 2.1, 2.3**

### Property 3: Сохранение текста сообщения

*For any* событие, при обновлении клавиатуры после включения/отключения напоминаний, текст сообщения должен остаться неизменным

**Validates: Requirements 1.2, 2.2**

### Property 4: Порядок кнопок в клавиатуре

*For any* активное событие создателя, кнопка "✅ Завершить" должна быть расположена ниже кнопки управления напоминаниями ("🔕 Отключить напоминания" или "🔔 Включить напоминания")

**Validates: Requirements 6.1, 6.2, 6.3**

### Property 5: Устойчивость к ошибкам обновления

*For any* событие, если обновление сообщения не удается, операция включения/отключения напоминаний должна быть завершена успешно, и ошибка должна быть залогирована как предупреждение

**Validates: Requirements 3.3, 3.4**

## Error Handling

### Обработка ошибок в ReminderCallbackHandler

1. **Событие не найдено** (`EventNotFoundException`)
   - Логируется ошибка с eventId
   - Отправляется callback query answer с сообщением об ошибке
   - Выполнение прерывается

2. **Ошибка при обновлении сообщения**
   - Логируется предупреждение с eventId, messageId и деталями ошибки
   - Выполнение продолжается (напоминания уже включены/отключены)
   - Пользователь получает callback query answer с подтверждением операции

3. **messageId равен null**
   - Обновление сообщения пропускается
   - Логируется debug сообщение
   - Выполнение продолжается

### Обработка ошибок в KeyboardService

Изменения в обработке ошибок не требуются. Существующая валидация параметров сохраняется.

## Testing Strategy

### Unit Tests

1. **ReminderCallbackHandlerTest**
   - Тест успешного отключения напоминаний с обновлением сообщения
   - Тест успешного включения напоминаний с обновлением сообщения
   - Тест обработки null messageId
   - Тест обработки ошибки при обновлении сообщения
   - Тест обработки EventNotFoundException

2. **KeyboardServiceTest**
   - Тест порядка кнопок для события с напоминаниями
   - Тест порядка кнопок для события без напоминаний
   - Тест порядка кнопок для неактивного события
   - Тест порядка кнопок для события не-создателя

### Property-Based Tests

Property-based тесты не требуются для данной функциональности, так как она связана с UI взаимодействием и порядком элементов, которые лучше тестировать через unit тесты с конкретными примерами.

### Integration Tests

1. **ReminderToggleIntegrationTest**
   - Полный цикл: создание события → включение напоминаний → проверка обновления сообщения → отключение напоминаний → проверка обновления сообщения
   - Проверка корректности callback data в обновленной клавиатуре

## Implementation Details

### Изменения в ReminderCallbackHandler.handleDisableReminders

```java
public void handleDisableReminders(Long eventId, Long chatId, Integer messageId, String callbackQueryId) {
    log.debug("Отключение автоматических напоминаний для события ID={}", eventId);
    
    try {
        // Отключаем все напоминания для события
        reminderService.disableRemindersForEvent(eventId);
        
        // Отвечаем на callback query
        messageService.answerCallbackQuery(callbackQueryId, "✅ Напоминания отключены");
        
        // Обновляем сообщение события с новой клавиатурой
        if (messageId != null) {
            try {
                // Получаем событие
                Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new EventNotFoundException(eventId));
                User user = event.getUser();
                
                // Формируем текст сообщения
                String messageText = botMessageBuilder.buildEventMessage(event);
                
                // Создаем клавиатуру с обновленной кнопкой напоминаний
                InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, user.getId());
                
                // Обновляем сообщение
                messageService.editMessageText(chatId, messageId, messageText, keyboard);
                
                log.debug("Сообщение события обновлено после отключения напоминаний: eventId={}, messageId={}", 
                         eventId, messageId);
            } catch (Exception e) {
                log.warn("Не удалось обновить сообщение события после отключения напоминаний: eventId={}, messageId={}, error={}", 
                        eventId, messageId, e.getMessage());
                // Не прерываем выполнение, так как напоминания уже отключены
            }
        }
        
        log.info("Автоматические напоминания отключены для события ID={}", eventId);
        
    } catch (Exception e) {
        log.error("Ошибка при отключении напоминаний: eventId={}, chatId={}, error={}, stackTrace={}", 
                eventId, chatId, e.getMessage(), getStackTraceString(e), e);
        try {
            messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка при отключении напоминаний");
        } catch (Exception ex) {
            log.error("Ошибка при ответе на callback query: callbackQueryId={}, error={}, stackTrace={}", 
                    callbackQueryId, ex.getMessage(), getStackTraceString(ex), ex);
        }
    }
}
```

### Изменения в ReminderCallbackHandler.handleEnableReminders

Метод уже содержит логику обновления сообщения, но она выполняется только при условии `messageId != null && !createdReminders.isEmpty()`. Нужно убрать проверку `!createdReminders.isEmpty()`, чтобы сообщение обновлялось всегда, даже если напоминания не были созданы (например, событие слишком скоро).

```java
// Обновляем сообщение события с новой клавиатурой
if (messageId != null) {
    try {
        // Формируем текст сообщения
        String messageText = botMessageBuilder.buildEventMessage(event);
        
        // Создаем клавиатуру с обновленной кнопкой напоминаний
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, user.getId());
        
        // Обновляем сообщение
        messageService.editMessageText(chatId, messageId, messageText, keyboard);
        
        log.debug("Сообщение события обновлено после включения напоминаний: eventId={}, messageId={}", 
                 eventId, messageId);
    } catch (Exception e) {
        log.warn("Не удалось обновить сообщение события после включения напоминаний: eventId={}, messageId={}, error={}", 
                eventId, messageId, e.getMessage());
        // Не прерываем выполнение, так как напоминания уже созданы
    }
}
```

### Изменения в KeyboardService.createEventActionsKeyboard

Изменяем порядок рядов кнопок:

```java
// Ряд 1: [✏️ Редактировать] [🗑️ Удалить]
List<InlineKeyboardButton> row1 = new ArrayList<>();
// ... (без изменений)
rows.add(row1);

// Ряд 2: [📎 Вложения]
List<InlineKeyboardButton> row2 = new ArrayList<>();
InlineKeyboardButton attachmentsBtn = new InlineKeyboardButton(attachmentsButtonText);
attachmentsBtn.setCallbackData(attachmentsCallbackData);
row2.add(attachmentsBtn);
rows.add(row2);

// Ряд 3: [🔕 Отключить напоминания] или [🔔 Включить напоминания] (только для активных событий создателя)
if (isActive && isOwner) {
    List<InlineKeyboardButton> row3 = new ArrayList<>();
    // ... (логика создания кнопки напоминаний)
    rows.add(row3);
}

// Ряд 4: [✅ Завершить] (только для активных событий создателя)
if (isActive && isOwner) {
    List<InlineKeyboardButton> row4 = new ArrayList<>();
    InlineKeyboardButton completeBtn = new InlineKeyboardButton("✅ Завершить");
    String completeCallbackData = "complete_event_" + eventId;
    completeBtn.setCallbackData(completeCallbackData);
    row4.add(completeBtn);
    rows.add(row4);
}
```

## Logging Strategy

### ReminderCallbackHandler

- **DEBUG**: Начало обработки отключения/включения напоминаний (eventId)
- **DEBUG**: Успешное обновление сообщения события (eventId, messageId)
- **WARN**: Ошибка при обновлении сообщения события (eventId, messageId, error message)
- **INFO**: Успешное завершение операции (eventId, количество созданных/удаленных напоминаний)
- **ERROR**: Критическая ошибка при включении/отключении напоминаний (eventId, chatId, error, stackTrace)

### KeyboardService

Существующее логирование сохраняется без изменений.

## Performance Considerations

Изменения не влияют на производительность системы:

1. Обновление сообщения выполняется асинхронно через Telegram API
2. Дополнительные запросы к БД не требуются (событие уже загружено)
3. Создание клавиатуры - легковесная операция

## Security Considerations

Безопасность не затрагивается данными изменениями:

1. Проверка прав доступа выполняется на уровне ReminderService
2. Валидация параметров сохраняется в KeyboardService
3. Обработка ошибок предотвращает утечку чувствительной информации

## Migration Strategy

Миграция не требуется. Изменения затрагивают только код приложения.

## Rollback Strategy

В случае проблем можно откатить изменения через Git:
1. Откат коммита с изменениями в ReminderCallbackHandler
2. Откат коммита с изменениями в KeyboardService

Откат безопасен, так как изменения не затрагивают структуру БД.
