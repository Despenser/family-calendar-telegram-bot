# Документ дизайна

## Обзор

Данный дизайн описывает изменения в методе `createEventActionsKeyboard` класса `KeyboardService` для реорганизации кнопок управления событиями. Текущая реализация размещает кнопки в четыре ряда:
- Ряд 1: Редактировать | Удалить
- Ряд 2: Вложения
- Ряд 3: Включить/Отключить напоминания (условно)
- Ряд 4: Завершить (условно)

Новая реализация будет размещать кнопки в три ряда:
- Ряд 1: Редактировать | Удалить
- Ряд 2: Вложения | Включить/Отключить напоминания (условно)
- Ряд 3: Завершить (условно)

Это изменение улучшит пользовательский опыт за счет более компактного и логичного расположения кнопок.

## Архитектура

### Затрагиваемые компоненты

1. **KeyboardService** - основной компонент, требующий изменений
   - Метод `createEventActionsKeyboard(Event event, Long userId)` - требует модификации логики создания рядов кнопок

2. **KeyboardServiceTest** - тестовый класс, требующий обновления
   - Тесты, проверяющие структуру клавиатуры, должны быть обновлены для проверки нового расположения

### Диаграмма структуры клавиатуры

```mermaid
graph TD
    A[Event Actions Keyboard] --> B[Ряд 1: Редактировать | Удалить]
    A --> C[Ряд 2: Вложения | Напоминания*]
    A --> D[Ряд 3: Завершить*]
    C --> E{Событие активно И<br/>принадлежит пользователю?}
    E -->|Да| F[Показать кнопку Напоминаний]
    E -->|Нет| G[Показать только Вложения]
    D --> H{Событие активно И<br/>принадлежит пользователю?}
    H -->|Да| I[Показать кнопку Завершить]
    H -->|Нет| J[Не показывать ряд 3]
```

## Компоненты и интерфейсы

### KeyboardService.createEventActionsKeyboard

**Сигнатура:**
```java
public InlineKeyboardMarkup createEventActionsKeyboard(Event event, Long userId)
```

**Параметры:**
- `event` - объект события, для которого создается клавиатура
- `userId` - идентификатор текущего пользователя для проверки прав

**Возвращаемое значение:**
- `InlineKeyboardMarkup` - настроенная inline-клавиатура с кнопками

**Изменения в логике:**

1. **Первый ряд** (без изменений):
   - Кнопка "✏️ Редактировать" с callback `edit_event_{eventId}`
   - Кнопка "🗑️ Удалить" с callback `delete_event_{eventId}`

2. **Второй ряд** (новая логика):
   - Всегда добавляется кнопка "📎 Вложения" (или "📎 Вложения (N)") с callback `attach_file_list_{eventId}`
   - Если событие активно И принадлежит пользователю:
     - Добавляется кнопка управления напоминаниями:
       - "🔔 Включить напоминания" с callback `enable_reminders_{eventId}` (если нет активных напоминаний)
       - "🔕 Отключить напоминания" с callback `disable_reminders_{eventId}` (если есть активные напоминания)
   - Иначе:
     - Второй ряд содержит только кнопку "Вложения"

3. **Третий ряд** (новая логика):
   - Если событие активно И принадлежит пользователю:
     - Добавляется кнопка "✅ Завершить" с callback `complete_event_{eventId}`
   - Иначе:
     - Третий ряд не создается

**Псевдокод новой реализации:**

```
FUNCTION createEventActionsKeyboard(event, userId):
    VALIDATE event IS NOT NULL
    VALIDATE event.id IS NOT NULL
    VALIDATE userId IS NOT NULL AND userId > 0
    
    keyboard = NEW InlineKeyboardMarkup
    rows = NEW List<List<InlineKeyboardButton>>
    
    // Ряд 1: Редактировать | Удалить
    row1 = NEW List<InlineKeyboardButton>
    editBtn = CREATE_BUTTON("✏️ Редактировать", "edit_event_" + event.id)
    deleteBtn = CREATE_BUTTON("🗑️ Удалить", "delete_event_" + event.id)
    ADD editBtn TO row1
    ADD deleteBtn TO row1
    ADD row1 TO rows
    
    // Ряд 2: Вложения | Напоминания (условно)
    row2 = NEW List<InlineKeyboardButton>
    
    // Кнопка вложений (всегда присутствует)
    attachmentsCount = attachmentService.countEventAttachments(event.id)
    attachmentsText = IF attachmentsCount > 0 
                      THEN "📎 Вложения (" + attachmentsCount + ")"
                      ELSE "📎 Вложения"
    attachmentsBtn = CREATE_BUTTON(attachmentsText, "attach_file_list_" + event.id)
    ADD attachmentsBtn TO row2
    
    // Проверяем статус и права доступа для условных кнопок
    isActive = event.status == ACTIVE
    isOwner = event.belongsToUser(userId)
    
    // Кнопка управления напоминаниями (условно)
    IF isActive AND isOwner THEN
        hasReminders = reminderService.hasActiveReminders(event.id)
        
        IF hasReminders THEN
            remindersBtn = CREATE_BUTTON("🔕 Отключить напоминания", "disable_reminders_" + event.id)
        ELSE
            remindersBtn = CREATE_BUTTON("🔔 Включить напоминания", "enable_reminders_" + event.id)
        END IF
        
        ADD remindersBtn TO row2
    END IF
    
    ADD row2 TO rows
    
    // Ряд 3: Завершить (условно)
    IF isActive AND isOwner THEN
        row3 = NEW List<InlineKeyboardButton>
        completeBtn = CREATE_BUTTON("✅ Завершить", "complete_event_" + event.id)
        ADD completeBtn TO row3
        ADD row3 TO rows
    END IF
    
    keyboard.setKeyboard(rows)
    
    LOG creation details
    
    RETURN keyboard
END FUNCTION
```

## Модели данных

Изменения в моделях данных не требуются. Используются существующие структуры:

- `Event` - модель события с полями `id`, `status`, методом `belongsToUser(userId)`
- `InlineKeyboardMarkup` - Telegram API класс для inline-клавиатур
- `InlineKeyboardButton` - Telegram API класс для inline-кнопок

## Correctness Properties

*Свойство (property) - это характеристика или поведение, которое должно выполняться для всех допустимых выполнений системы. По сути, это формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Property 1: Количество рядов зависит от статуса события и прав доступа

*For any* события и пользователя:
- Если событие активно И принадлежит пользователю, клавиатура должна содержать ровно 3 ряда
- Если событие не активно ИЛИ не принадлежит пользователю, клавиатура должна содержать ровно 2 ряда

**Validates: Requirements 1.1, 1.5, 1.6**

### Property 2: Первый ряд содержит кнопки редактирования и удаления

*For any* события и пользователя, первый ряд клавиатуры должен содержать ровно две кнопки: "✏️ Редактировать" с callback `edit_event_{eventId}` и "🗑️ Удалить" с callback `delete_event_{eventId}`, в указанном порядке.

**Validates: Requirements 1.2**

### Property 3: Второй ряд всегда содержит кнопку вложений

*For any* события и пользователя, второй ряд клавиатуры должен всегда начинаться с кнопки "📎 Вложения" (или "📎 Вложения (N)" если N > 0) с callback `attach_file_list_{eventId}`.

**Validates: Requirements 1.3, 4.2**

### Property 4: Кнопка управления напоминаниями отображается условно

*For any* события и пользователя:
- Если событие активно И принадлежит пользователю, второй ряд должен содержать кнопку управления напоминаниями
- Если событие не активно ИЛИ не принадлежит пользователю, второй ряд должен содержать только кнопку вложений (без кнопки напоминаний)

**Validates: Requirements 1.4, 2.5**

### Property 5: Текст кнопки напоминаний зависит от наличия активных напоминаний

*For any* активного события, принадлежащего пользователю:
- Если событие не имеет активных напоминаний, текст кнопки должен быть "🔔 Включить напоминания"
- Если событие имеет активные напоминания, текст кнопки должен быть "🔕 Отключить напоминания"

**Validates: Requirements 2.1, 2.2**

### Property 6: Третий ряд содержит кнопку завершения для активных событий владельца

*For any* события и пользователя:
- Если событие активно И принадлежит пользователю, третий ряд должен содержать ровно одну кнопку "✅ Завершить" с callback `complete_event_{eventId}`
- Текст кнопки не должен содержать слово "событие"
- Если событие не активно ИЛИ не принадлежит пользователю, третий ряд не должен существовать

**Validates: Requirements 1.5, 3.1, 3.2, 3.4**

### Property 7: Счетчик вложений отображается корректно

*For any* события с N вложениями:
- Если N = 0, текст кнопки должен быть "📎 Вложения"
- Если N > 0, текст кнопки должен быть "📎 Вложения (N)"

**Validates: Requirements 4.2**

### Property 8: Callback data имеют правильный формат

*For any* события с ID = eventId, все callback data должны иметь правильный формат:
- Кнопка редактирования: `edit_event_{eventId}`
- Кнопка удаления: `delete_event_{eventId}`
- Кнопка вложений: `attach_file_list_{eventId}`
- Кнопка включения напоминаний (если присутствует): `enable_reminders_{eventId}`
- Кнопка отключения напоминаний (если присутствует): `disable_reminders_{eventId}`
- Кнопка завершения (если присутствует): `complete_event_{eventId}`

**Validates: Requirements 2.3, 2.4, 3.3, 4.1**

## Обработка ошибок

Метод `createEventActionsKeyboard` должен выполнять валидацию входных параметров:

1. **Null event**: Выбросить `IllegalArgumentException` с сообщением "Event не может быть null"
2. **Null event.id**: Выбросить `IllegalArgumentException` с сообщением "Event ID не может быть null"
3. **Null userId**: Выбросить `IllegalArgumentException` с сообщением "UserId не может быть null"
4. **userId <= 0**: Выбросить `IllegalArgumentException` с сообщением "UserId должен быть положительным числом, получено: {userId}"

Все существующие проверки валидации должны быть сохранены без изменений.

## Стратегия тестирования

### Unit-тесты

Необходимо обновить существующие unit-тесты в `KeyboardServiceTest`:

1. **Тест структуры клавиатуры**:
   - Проверить, что клавиатура содержит 2 ряда для неактивных событий или событий других пользователей
   - Проверить, что клавиатура содержит 3 ряда для активных событий владельца
   - Проверить количество кнопок в каждом ряду

2. **Тест первого ряда**:
   - Проверить текст и callback data кнопки "Редактировать"
   - Проверить текст и callback data кнопки "Удалить"
   - Проверить порядок кнопок

3. **Тест второго ряда для активного события владельца**:
   - Проверить наличие кнопки "Вложения"
   - Проверить наличие кнопки управления напоминаниями
   - Проверить текст кнопки напоминаний в зависимости от наличия активных напоминаний
   - Проверить callback data обеих кнопок

4. **Тест второго ряда для неактивного события**:
   - Проверить наличие только кнопки "Вложения"
   - Проверить отсутствие кнопки управления напоминаниями

5. **Тест второго ряда для события другого пользователя**:
   - Проверить наличие только кнопки "Вложения"
   - Проверить отсутствие кнопки управления напоминаниями

6. **Тест третьего ряда для активного события владельца**:
   - Проверить наличие кнопки "Завершить"
   - Проверить текст "✅ Завершить" (без слова "событие")
   - Проверить callback data кнопки

7. **Тест отсутствия третьего ряда**:
   - Проверить отсутствие третьего ряда для неактивных событий
   - Проверить отсутствие третьего ряда для событий других пользователей

8. **Тест счетчика вложений**:
   - Проверить текст "📎 Вложения" когда вложений нет
   - Проверить текст "📎 Вложения (N)" когда есть N вложений

### Property-based тесты

Для каждого correctness property необходимо написать property-based тест с минимум 100 итерациями:

1. **Property 1**: Генерировать случайные события с разными статусами и владельцами, проверять количество рядов (2 или 3)
   - **Feature: event-buttons-layout-reorganization, Property 1: Количество рядов зависит от статуса события и прав доступа**

2. **Property 2**: Генерировать случайные события, проверять структуру первого ряда
   - **Feature: event-buttons-layout-reorganization, Property 2: Первый ряд содержит кнопки редактирования и удаления**

3. **Property 3**: Генерировать случайные события, проверять наличие кнопки вложений во втором ряду
   - **Feature: event-buttons-layout-reorganization, Property 3: Второй ряд всегда содержит кнопку вложений**

4. **Property 4**: Генерировать события с разными статусами и владельцами, проверять условное отображение кнопки напоминаний
   - **Feature: event-buttons-layout-reorganization, Property 4: Кнопка управления напоминаниями отображается условно**

5. **Property 5**: Генерировать активные события владельца с разным наличием напоминаний, проверять текст кнопки
   - **Feature: event-buttons-layout-reorganization, Property 5: Текст кнопки напоминаний зависит от наличия активных напоминаний**

6. **Property 6**: Генерировать события с разными статусами и владельцами, проверять структуру третьего ряда
   - **Feature: event-buttons-layout-reorganization, Property 6: Третий ряд содержит кнопку завершения для активных событий владельца**

7. **Property 7**: Генерировать события с разным количеством вложений, проверять текст кнопки
   - **Feature: event-buttons-layout-reorganization, Property 7: Счетчик вложений отображается корректно**

8. **Property 8**: Генерировать события с разными ID, проверять форматы всех callback data
   - **Feature: event-buttons-layout-reorganization, Property 8: Callback data имеют правильный формат**

### Библиотека для property-based тестирования

Для Java Spring Boot проекта рекомендуется использовать библиотеку **jqwik** - современную и мощную библиотеку для property-based тестирования в Java.

**Зависимость Maven:**
```xml
<dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <version>1.8.2</version>
    <scope>test</scope>
</dependency>
```

**Конфигурация тестов:**
- Минимум 100 итераций на каждый property-тест
- Использовать аннотацию `@Property` вместо `@Test`
- Использовать `@ForAll` для генерации случайных данных
- Каждый тест должен содержать комментарий с тегом property

**Пример структуры property-теста:**
```java
@Property(tries = 100)
// Feature: event-buttons-layout-reorganization, Property 1: Количество рядов зависит от статуса события и прав доступа
void keyboardRowCountDependsOnEventStatusAndOwnership(
    @ForAll Event event, 
    @ForAll Long userId
) {
    // Arrange & Act
    InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
    
    // Assert
    boolean isActive = event.getStatus() == Event.EventStatus.ACTIVE;
    boolean isOwner = event.belongsToUser(userId);
    
    if (isActive && isOwner) {
        assertThat(keyboard.getKeyboard()).hasSize(3);
    } else {
        assertThat(keyboard.getKeyboard()).hasSize(2);
    }
}
```

### Integration тесты

Integration тесты не требуются для данного изменения, так как:
- Изменяется только структура клавиатуры, не логика обработки callback
- Все callback data остаются неизменными
- Существующие обработчики callback продолжат работать без изменений
