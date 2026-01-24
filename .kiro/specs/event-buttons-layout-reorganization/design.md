# Документ дизайна

## Обзор

Данный дизайн описывает изменения в методе `createEventActionsKeyboard` класса `KeyboardService` для реорганизации кнопок управления событиями. Текущая реализация размещает кнопки в три ряда:
- Ряд 1: Редактировать | Удалить
- Ряд 2: Вложения
- Ряд 3: Завершить событие (условно)

Новая реализация будет размещать кнопки в два ряда:
- Ряд 1: Редактировать | Удалить
- Ряд 2: Вложения | Завершить (условно)

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
    A --> C[Ряд 2: Вложения | Завершить*]
    C --> D{Событие активно И<br/>принадлежит пользователю?}
    D -->|Да| E[Показать кнопку Завершить]
    D -->|Нет| F[Показать только Вложения]
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
     - Добавляется кнопка "✅ Завершить" с callback `complete_event_{eventId}`
   - Иначе:
     - Второй ряд содержит только кнопку "Вложения"

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
    
    // Ряд 2: Вложения | Завершить (условно)
    row2 = NEW List<InlineKeyboardButton>
    
    // Кнопка вложений (всегда присутствует)
    attachmentsCount = attachmentService.countEventAttachments(event.id)
    attachmentsText = IF attachmentsCount > 0 
                      THEN "📎 Вложения (" + attachmentsCount + ")"
                      ELSE "📎 Вложения"
    attachmentsBtn = CREATE_BUTTON(attachmentsText, "attach_file_list_" + event.id)
    ADD attachmentsBtn TO row2
    
    // Кнопка завершения (условно)
    isActive = event.status == ACTIVE
    isOwner = event.belongsToUser(userId)
    
    IF isActive AND isOwner THEN
        completeBtn = CREATE_BUTTON("✅ Завершить", "complete_event_" + event.id)
        ADD completeBtn TO row2
    END IF
    
    ADD row2 TO rows
    
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


### Property 1: Клавиатура всегда содержит два ряда

*For any* события и пользователя, созданная клавиатура должна содержать ровно два ряда кнопок.

**Validates: Requirements 1.1**

### Property 2: Первый ряд содержит кнопки редактирования и удаления

*For any* события и пользователя, первый ряд клавиатуры должен содержать ровно две кнопки: "✏️ Редактировать" с callback `edit_event_{eventId}` и "🗑️ Удалить" с callback `delete_event_{eventId}`, в указанном порядке.

**Validates: Requirements 1.2, 3.1**

### Property 3: Второй ряд содержит кнопку вложений и условно кнопку завершения

*For any* события и пользователя:
- Второй ряд всегда должен начинаться с кнопки "📎 Вложения" (или "📎 Вложения (N)" если N > 0) с callback `attach_file_list_{eventId}`
- Если событие активно И принадлежит пользователю, второй ряд должен также содержать кнопку "✅ Завершить" с callback `complete_event_{eventId}`
- Если событие не активно ИЛИ не принадлежит пользователю, второй ряд должен содержать только кнопку вложений

**Validates: Requirements 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 3.1, 3.3**

### Property 4: Счетчик вложений отображается корректно

*For any* события с N вложениями:
- Если N = 0, текст кнопки должен быть "📎 Вложения"
- Если N > 0, текст кнопки должен быть "📎 Вложения (N)"

**Validates: Requirements 3.2**

### Property 5: Callback data имеют правильный формат

*For any* события с ID = eventId, все callback data должны иметь правильный формат:
- Кнопка редактирования: `edit_event_{eventId}`
- Кнопка удаления: `delete_event_{eventId}`
- Кнопка вложений: `attach_file_list_{eventId}`
- Кнопка завершения (если присутствует): `complete_event_{eventId}`

**Validates: Requirements 3.1**

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
   - Проверить, что клавиатура содержит ровно 2 ряда
   - Проверить количество кнопок в каждом ряду

2. **Тест первого ряда**:
   - Проверить текст и callback data кнопки "Редактировать"
   - Проверить текст и callback data кнопки "Удалить"
   - Проверить порядок кнопок

3. **Тест второго ряда для активного события владельца**:
   - Проверить наличие кнопки "Вложения"
   - Проверить наличие кнопки "Завершить"
   - Проверить текст "✅ Завершить" (без слова "событие")
   - Проверить callback data обеих кнопок

4. **Тест второго ряда для неактивного события**:
   - Проверить наличие только кнопки "Вложения"
   - Проверить отсутствие кнопки "Завершить"

5. **Тест второго ряда для события другого пользователя**:
   - Проверить наличие только кнопки "Вложения"
   - Проверить отсутствие кнопки "Завершить"

6. **Тест счетчика вложений**:
   - Проверить текст "📎 Вложения" когда вложений нет
   - Проверить текст "📎 Вложения (N)" когда есть N вложений

### Property-based тесты

Для каждого correctness property необходимо написать property-based тест с минимум 100 итерациями:

1. **Property 1**: Генерировать случайные события и пользователей, проверять количество рядов = 2
   - **Feature: event-buttons-layout-reorganization, Property 1: Клавиатура всегда содержит два ряда**

2. **Property 2**: Генерировать случайные события, проверять структуру первого ряда
   - **Feature: event-buttons-layout-reorganization, Property 2: Первый ряд содержит кнопки редактирования и удаления**

3. **Property 3**: Генерировать события с разными статусами и владельцами, проверять структуру второго ряда
   - **Feature: event-buttons-layout-reorganization, Property 3: Второй ряд содержит кнопку вложений и условно кнопку завершения**

4. **Property 4**: Генерировать события с разным количеством вложений, проверять текст кнопки
   - **Feature: event-buttons-layout-reorganization, Property 4: Счетчик вложений отображается корректно**

5. **Property 5**: Генерировать события с разными ID, проверять форматы callback data
   - **Feature: event-buttons-layout-reorganization, Property 5: Callback data имеют правильный формат**

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
// Feature: event-buttons-layout-reorganization, Property 1: Клавиатура всегда содержит два ряда
void keyboardAlwaysHasTwoRows(@ForAll Event event, @ForAll Long userId) {
    // Arrange & Act
    InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
    
    // Assert
    assertThat(keyboard.getKeyboard()).hasSize(2);
}
```

### Integration тесты

Integration тесты не требуются для данного изменения, так как:
- Изменяется только структура клавиатуры, не логика обработки callback
- Все callback data остаются неизменными
- Существующие обработчики callback продолжат работать без изменений
