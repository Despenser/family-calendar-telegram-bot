# Requirements Document

## Introduction

Исправление логики определения контекста просмотра деталей события. В текущей реализации при нажатии кнопки "Посмотреть детали" система определяет, откуда был вызван просмотр, проверяя наличие активных напоминаний у события. Это приводит к неправильному поведению: если у события есть активные напоминания, то при просмотре из любого места (например, из списка "Мои события") пользователь видит упрощенную клавиатуру только с кнопкой "Назад к напоминанию" вместо полной клавиатуры с действиями (редактирование, удаление, завершение).

Правильное поведение:
- При просмотре из уведомления о напоминании → упрощенная клавиатура с кнопкой "Назад к напоминанию"
- При просмотре из любого другого места → полная клавиатура с действиями над событием

## Glossary

- **View_Details_Button**: Кнопка "📋 Посмотреть детали" для просмотра информации о событии
- **Reminder_Context**: Контекст просмотра события из уведомления о напоминании
- **Standard_Context**: Контекст просмотра события из других частей приложения (список событий, поиск и т.д.)
- **Simplified_Keyboard**: Упрощенная клавиатура с одной кнопкой "◀️ Назад к напоминанию"
- **Full_Actions_Keyboard**: Полная клавиатура с кнопками действий (✏️ Редактировать, 🗑️ Удалить, ✅ Завершить, 📎 Вложения, 🔔 Напоминания)
- **Callback_Data**: Данные callback-запроса, содержащие информацию о действии и контексте
- **EventCallbackHandler**: Обработчик callback-запросов для операций с событиями
- **ReminderService**: Сервис для управления напоминаниями и отправки уведомлений
- **Short_Reminder_Message**: Короткая версия уведомления о напоминании в формате "{эмодзи} Напоминание: {текст времени} - {название события}"
- **Full_Reminder_Message**: Полная версия информации о напоминании с заголовком "🔔 Напоминание о событии" и всеми деталями события
- **Reminder_Emoji**: Эмодзи, специфичный для типа напоминания (🔥 для 15 минут, ⚡ для 1 часа, 🌙 для накануне)

## Requirements

### Requirement 1: Передача контекста в callback data

**User Story:** Как разработчик, я хочу передавать информацию о контексте просмотра в callback data, чтобы система могла правильно определить, откуда был вызван просмотр деталей события.

#### Acceptance Criteria

1. WHEN система создает кнопку "Посмотреть детали" в уведомлении о напоминании THEN THE System SHALL использовать callback data формата "view_event_from_reminder_{eventId}_{reminderId}"
2. WHEN система создает кнопку "Посмотреть детали" в других частях приложения THEN THE System SHALL использовать callback data формата "view_event_{eventId}"
3. WHEN формируется callback data для напоминания THEN THE System SHALL включать reminderId для возможности возврата к конкретному напоминанию
4. WHEN формируется callback data для стандартного контекста THEN THE System SHALL НЕ включать информацию о напоминании

### Requirement 2: Определение контекста по callback data

**User Story:** Как разработчик, я хочу определять контекст просмотра по callback data, чтобы показывать правильную клавиатуру в зависимости от того, откуда пользователь открыл событие.

#### Acceptance Criteria

1. WHEN обрабатывается callback "view_event_from_reminder_{eventId}_{reminderId}" THEN THE System SHALL определить контекст как Reminder_Context
2. WHEN обрабатывается callback "view_event_{eventId}" THEN THE System SHALL определить контекст как Standard_Context
3. WHEN определен контекст Reminder_Context THEN THE System SHALL использовать Simplified_Keyboard
4. WHEN определен контекст Standard_Context THEN THE System SHALL использовать Full_Actions_Keyboard
5. WHEN callback data имеет некорректный формат THEN THE System SHALL использовать Standard_Context по умолчанию

### Requirement 3: Создание упрощенной клавиатуры для контекста напоминания

**User Story:** Как пользователь, я хочу видеть только кнопку "Назад к напоминанию" при просмотре деталей из уведомления, чтобы не выполнять случайные действия над событием из уведомления.

#### Acceptance Criteria

1. WHEN контекст определен как Reminder_Context THEN THE System SHALL создать клавиатуру с одной кнопкой "◀️ Назад к напоминанию"
2. WHEN создается упрощенная клавиатура THEN THE System SHALL использовать callback data "back_to_reminder_{eventId}_{reminderId}"
3. WHEN создается упрощенная клавиатура THEN THE System SHALL НЕ включать кнопки "✏️ Редактировать", "🗑️ Удалить", "✅ Завершить"
4. WHEN создается упрощенная клавиатура THEN THE System SHALL НЕ включать кнопки "📎 Вложения" и "🔔 Напоминания"

### Requirement 4: Создание полной клавиатуры для стандартного контекста

**User Story:** Как пользователь, я хочу видеть все доступные действия при просмотре деталей события из списка событий, чтобы иметь возможность редактировать, удалять или завершать событие.

#### Acceptance Criteria

1. WHEN контекст определен как Standard_Context THEN THE System SHALL создать полную клавиатуру с действиями через KeyboardService.createEventActionsKeyboard()
2. WHEN создается полная клавиатура для активного события THEN THE System SHALL включать кнопки "✏️ Редактировать", "🗑️ Удалить", "✅ Завершить"
3. WHEN создается полная клавиатура THEN THE System SHALL включать кнопку "📎 Вложения" если есть вложения или пользователь является владельцем
4. WHEN создается полная клавиатура THEN THE System SHALL включать кнопку "🔔 Напоминания" если пользователь является владельцем
5. WHEN создается полная клавиатура для завершенного события THEN THE System SHALL НЕ включать кнопки "✏️ Редактировать" и "✅ Завершить"

### Requirement 5: Обновление ReminderService для использования нового формата callback

**User Story:** Как разработчик, я хочу обновить ReminderService для использования нового формата callback data, чтобы передавать информацию о контексте напоминания.

#### Acceptance Criteria

1. WHEN создается упрощенная клавиатура напоминания THEN THE ReminderService SHALL использовать callback data "view_event_from_reminder_{eventId}_{reminderId}"
2. WHEN формируется callback data THEN THE ReminderService SHALL включать eventId и reminderId
3. WHEN создается клавиатура THEN THE ReminderService SHALL использовать метод CallbackPrefix.VIEW_EVENT_FROM_REMINDER.withPayload()
4. WHEN отправляется уведомление о напоминании THEN THE System SHALL прикреплять клавиатуру с новым форматом callback data

### Requirement 6: Добавление нового префикса callback

**User Story:** Как разработчик, я хочу добавить новый префикс для callback из напоминаний, чтобы отличать их от обычных просмотров событий.

#### Acceptance Criteria

1. WHEN определяются префиксы callback THEN THE System SHALL включать префикс VIEW_EVENT_FROM_REMINDER с значением "view_event_from_reminder_"
2. WHEN проверяется соответствие callback data префиксу THEN THE CallbackPrefix.VIEW_EVENT_FROM_REMINDER SHALL корректно определять callback формата "view_event_from_reminder_{eventId}_{reminderId}"
3. WHEN извлекается payload из callback THEN THE CallbackPrefix.VIEW_EVENT_FROM_REMINDER SHALL возвращать строку "{eventId}_{reminderId}"
4. WHEN формируется callback data THEN THE CallbackPrefix.VIEW_EVENT_FROM_REMINDER.withPayload() SHALL принимать строку "{eventId}_{reminderId}"

### Requirement 7: Обновление EventCallbackHandler для обработки нового формата

**User Story:** Как разработчик, я хочу обновить EventCallbackHandler для обработки нового формата callback, чтобы правильно определять контекст и показывать соответствующую клавиатуру.

#### Acceptance Criteria

1. WHEN обрабатывается callback query THEN THE EventCallbackHandler SHALL проверять соответствие префиксу VIEW_EVENT_FROM_REMINDER
2. WHEN callback соответствует VIEW_EVENT_FROM_REMINDER THEN THE EventCallbackHandler SHALL вызывать handleViewEventFromReminder()
3. WHEN callback соответствует VIEW_EVENT THEN THE EventCallbackHandler SHALL вызывать handleViewEvent() с Standard_Context
4. WHEN извлекается eventId и reminderId из callback THEN THE System SHALL корректно парсить payload формата "{eventId}_{reminderId}"
5. WHEN парсинг payload не удается THEN THE System SHALL логировать ошибку и отправлять callback query answer с сообщением об ошибке

### Requirement 8: Удаление неправильной логики hasActiveReminders

**User Story:** Как разработчик, я хочу удалить неправильную логику определения контекста по наличию активных напоминаний, чтобы система работала корректно.

#### Acceptance Criteria

1. WHEN обрабатывается просмотр деталей события THEN THE System SHALL НЕ использовать метод hasActiveReminders() для определения контекста
2. WHEN определяется тип клавиатуры THEN THE System SHALL использовать только информацию из callback data
3. WHEN удаляется метод hasActiveReminders() из EventCallbackHandler THEN THE System SHALL сохранять метод getFirstActiveReminderId() для обратной совместимости
4. WHEN удаляется неправильная логика THEN THE System SHALL обновить все связанные тесты

### Requirement 9: Обратная совместимость

**User Story:** Как разработчик, я хочу сохранить обратную совместимость с существующими callback, чтобы старые сообщения продолжали работать.

#### Acceptance Criteria

1. WHEN обрабатывается старый callback "view_event_{eventId}" THEN THE System SHALL использовать Standard_Context
2. WHEN обрабатывается новый callback "view_event_from_reminder_{eventId}_{reminderId}" THEN THE System SHALL использовать Reminder_Context
3. WHEN обрабатывается callback "back_to_reminder_{eventId}_{reminderId}" THEN THE System SHALL продолжать работать корректно
4. WHEN пользователь нажимает на старую кнопку "Посмотреть детали" в уведомлении THEN THE System SHALL показывать полную клавиатуру (Standard_Context)

### Requirement 10: Логирование операций

**User Story:** Как разработчик, я хочу иметь детальное логирование операций просмотра деталей события, чтобы легко диагностировать проблемы в production.

#### Acceptance Criteria

1. WHEN определяется контекст просмотра THEN THE System SHALL логировать debug сообщение с типом контекста (Reminder_Context или Standard_Context)
2. WHEN создается упрощенная клавиатура THEN THE System SHALL логировать debug сообщение с eventId и reminderId
3. WHEN создается полная клавиатура THEN THE System SHALL логировать debug сообщение с eventId и userId
4. WHEN обрабатывается callback из напоминания THEN THE System SHALL логировать info сообщение с eventId, reminderId и userId
5. IF парсинг callback data не удается THEN THE System SHALL логировать error с деталями ошибки

### Requirement 11: Формат сообщений при просмотре деталей и возврате к напоминанию

**User Story:** Как пользователь, я хочу видеть полную информацию о событии при нажатии "Посмотреть детали" и короткую версию при нажатии "Назад к напоминанию", чтобы удобно переключаться между режимами просмотра.

#### Acceptance Criteria

1. WHEN пользователь нажимает "Посмотреть детали" из уведомления о напоминании THEN THE System SHALL отображать полное сообщение с заголовком "🔔 Напоминание о событии" и всей информацией о событии (дата, время, описание, тип события, тип напоминания)
2. WHEN пользователь нажимает "Назад к напоминанию" THEN THE System SHALL отображать короткую версию уведомления в формате "{эмодзи} Напоминание: {текст времени} - {название события}"
3. WHEN отображается короткая версия для напоминания за 15 минут THEN THE System SHALL использовать формат "🔥 Напоминание: через 15 минут начнется событие - {название события}"
4. WHEN отображается короткая версия для напоминания за 1 час THEN THE System SHALL использовать формат "⚡ Напоминание: через 1 час начнется событие - {название события}"
5. WHEN отображается короткая версия для напоминания накануне THEN THE System SHALL использовать формат "🌙 Напоминание: завтра в {время} у вас событие - {название события}"
6. WHEN отображается полная версия при просмотре деталей THEN THE System SHALL включать все поля: заголовок "🔔 Напоминание о событии", название события, дату, время, описание, тип события, тип напоминания
7. WHEN формируется короткая версия THEN THE System SHALL НЕ включать дату, время, описание и тип события в отдельных строках
