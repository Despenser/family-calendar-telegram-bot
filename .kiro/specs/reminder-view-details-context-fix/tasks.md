# Implementation Plan: Исправление контекста просмотра деталей события из напоминания

## Overview

Данный план описывает пошаговую реализацию исправления логики определения контекста просмотра деталей события. Изменения включают добавление нового префикса callback, обновление ReminderService и EventCallbackHandler, а также удаление неправильной логики определения контекста по наличию активных напоминаний.

## Tasks

- [x] 1. Добавить новый префикс callback VIEW_EVENT_FROM_REMINDER
  - Добавить константу `VIEW_EVENT_FROM_REMINDER("view_event_from_reminder_")` в enum CallbackPrefix
  - Префикс должен поддерживать payload формата "{eventId}_{reminderId}"
  - _Requirements: 1.1, 6.1, 6.2, 6.3, 6.4_

- [ ]* 1.1 Написать property test для CallbackPrefix round-trip
  - **Property 3: CallbackPrefix round-trip для VIEW_EVENT_FROM_REMINDER**
  - **Validates: Requirements 6.2, 6.3, 6.4**
  - Проверить, что `withPayload()` → `extractPayload()` возвращает исходные значения
  - Использовать генераторы для eventId и reminderId (1-999999)
  - _Requirements: 6.2, 6.3, 6.4_

- [ ]* 1.2 Написать unit test для наличия префикса
  - **Example 3: Наличие префикса VIEW_EVENT_FROM_REMINDER**
  - **Validates: Requirements 6.1**
  - Проверить, что enum содержит константу VIEW_EVENT_FROM_REMINDER
  - Проверить, что значение префикса равно "view_event_from_reminder_"
  - _Requirements: 6.1_

- [x] 2. Обновить ReminderService для использования нового формата callback
  - [x] 2.1 Обновить сигнатуру метода createSimplifiedReminderKeyboard
    - Добавить параметр `Long reminderId`
    - Обновить создание callback data для использования нового формата
    - Использовать `CallbackPrefix.VIEW_EVENT_FROM_REMINDER.withPayload(eventId + "_" + reminderId)`
    - _Requirements: 1.1, 1.3, 5.1, 5.2_
  
  - [x] 2.2 Обновить вызовы createSimplifiedReminderKeyboard
    - Обновить метод `createReminderKeyboard()` для передачи reminderId
    - Обновить метод `sendReminderNotification()` для передачи reminderId
    - _Requirements: 5.4_

- [ ]* 2.3 Написать property test для callback data напоминаний
  - **Property 1: Callback data для напоминаний содержит eventId и reminderId**
  - **Validates: Requirements 1.1, 1.3, 5.1, 5.2**
  - Проверить формат callback data для всех событий и напоминаний
  - Проверить, что payload содержит оба идентификатора
  - _Requirements: 1.1, 1.3, 5.1, 5.2_

- [ ]* 2.4 Написать unit test для уведомления о напоминании
  - **Example 4: Уведомление о напоминании содержит клавиатуру с новым форматом**
  - **Validates: Requirements 5.4**
  - Проверить, что клавиатура содержит кнопку с правильным callback data
  - _Requirements: 5.4_

- [ ] 3. Checkpoint - Убедиться, что ReminderService обновлен корректно
  - Убедиться, что все тесты проходят
  - Спросить пользователя, если возникли вопросы

- [x] 4. Добавить новый метод handleViewEventFromReminder в EventCallbackHandler
  - [x] 4.1 Создать метод handleViewEventFromReminder
    - Извлечь payload из callback data
    - Распарсить payload на eventId и reminderId
    - Добавить валидацию формата (проверка количества частей)
    - Добавить обработку NumberFormatException
    - Загрузить событие из базы данных
    - Создать упрощенную клавиатуру через createDetailsKeyboard
    - Обновить сообщение с деталями события
    - Добавить логирование на всех этапах
    - _Requirements: 2.1, 2.3, 3.1, 3.2, 7.1, 7.2, 7.4, 10.1, 10.2_
  
  - [x] 4.2 Добавить обработку ошибок
    - Обработать EventNotFoundException
    - Обработать TelegramApiException
    - Обработать некорректный формат callback data
    - Обработать NumberFormatException
    - Добавить общий catch для неожиданных ошибок
    - _Requirements: 7.5_

- [ ]* 4.3 Написать property test для парсинга payload
  - **Property 9: Парсинг payload корректно извлекает eventId и reminderId**
  - **Validates: Requirements 7.4**
  - Проверить парсинг для всех корректных payload
  - _Requirements: 7.4_

- [ ]* 4.4 Написать property test для обработки некорректного формата
  - **Property 10: Некорректный формат callback data обрабатывается gracefully**
  - **Validates: Requirements 2.5, 7.5**
  - Проверить обработку некорректных форматов без исключений
  - _Requirements: 2.5, 7.5_

- [ ]* 4.5 Написать unit test для маршрутизации VIEW_EVENT_FROM_REMINDER
  - **Example 1: Маршрутизация callback VIEW_EVENT_FROM_REMINDER**
  - **Validates: Requirements 2.1, 7.2, 9.2**
  - Проверить, что callback правильно маршрутизируется
  - _Requirements: 2.1, 7.2, 9.2_

- [x] 5. Обновить метод handleViewEvent для использования только стандартного контекста
  - [x] 5.1 Удалить логику проверки hasActiveReminders
    - Удалить вызов метода hasActiveReminders()
    - Удалить условную логику выбора клавиатуры
    - Всегда использовать keyboardService.createEventActionsKeyboard()
    - Обновить логирование для указания Standard_Context
    - _Requirements: 2.2, 2.4, 8.1, 8.2_
  
  - [x] 5.2 Удалить метод hasActiveReminders
    - Удалить метод hasActiveReminders() из EventCallbackHandler
    - Сохранить метод getFirstActiveReminderId() для обратной совместимости
    - _Requirements: 8.1, 8.3_

- [ ]* 5.3 Написать unit test для маршрутизации VIEW_EVENT
  - **Example 2: Маршрутизация callback VIEW_EVENT**
  - **Validates: Requirements 2.2, 7.3, 9.1**
  - Проверить, что callback правильно маршрутизируется
  - _Requirements: 2.2, 7.3, 9.1_

- [ ]* 5.4 Написать unit test для сохранения метода getFirstActiveReminderId
  - **Example 7: Сохранение метода getFirstActiveReminderId**
  - **Validates: Requirements 8.3**
  - Проверить, что метод существует и работает корректно
  - _Requirements: 8.3_

- [x] 6. Обновить метод canHandle для поддержки нового префикса
  - Добавить проверку `CallbackPrefix.VIEW_EVENT_FROM_REMINDER.matches(callbackData)`
  - _Requirements: 7.1_

- [x] 7. Обновить метод handle для маршрутизации нового callback
  - Добавить условие для VIEW_EVENT_FROM_REMINDER
  - Вызывать handleViewEventFromReminder() для нового префикса
  - Сохранить обработку VIEW_EVENT для обратной совместимости
  - _Requirements: 2.1, 2.2, 7.2, 7.3_

- [ ] 8. Checkpoint - Убедиться, что EventCallbackHandler обновлен корректно
  - Убедиться, что все тесты проходят
  - Спросить пользователя, если возникли вопросы

- [ ] 9. Написать property tests для клавиатур
  - [ ]* 9.1 Property test для callback data стандартного контекста
    - **Property 2: Callback data для стандартного контекста содержит только eventId**
    - **Validates: Requirements 1.2, 1.4**
    - Проверить формат callback data для всех событий
    - _Requirements: 1.2, 1.4_
  
  - [ ]* 9.2 Property test для упрощенной клавиатуры
    - **Property 4: Упрощенная клавиатура содержит только кнопку "Назад"**
    - **Validates: Requirements 2.3, 3.1, 3.2, 3.3, 3.4**
    - Проверить структуру клавиатуры для всех событий и напоминаний
    - _Requirements: 2.3, 3.1, 3.2, 3.3, 3.4_
  
  - [ ]* 9.3 Property test для полной клавиатуры активных событий
    - **Property 5: Полная клавиатура для активных событий содержит кнопки действий**
    - **Validates: Requirements 2.4, 4.2**
    - Проверить наличие кнопок действий для всех активных событий
    - _Requirements: 2.4, 4.2_
  
  - [ ]* 9.4 Property test для условного отображения кнопки "Вложения"
    - **Property 6: Кнопка "Вложения" отображается условно**
    - **Validates: Requirements 4.3**
    - Проверить условие отображения для всех событий
    - _Requirements: 4.3_
  
  - [ ]* 9.5 Property test для условного отображения кнопки "Напоминания"
    - **Property 7: Кнопка "Напоминания" отображается для владельца**
    - **Validates: Requirements 4.4**
    - Проверить условие отображения для всех событий
    - _Requirements: 4.4_
  
  - [ ]* 9.6 Property test для полной клавиатуры завершенных событий
    - **Property 8: Полная клавиатура для завершенных событий не содержит кнопок редактирования и завершения**
    - **Validates: Requirements 4.5**
    - Проверить отсутствие кнопок для всех завершенных событий
    - _Requirements: 4.5_

- [ ] 10. Написать unit tests для обратной совместимости
  - [ ]* 10.1 Unit test для обратной совместимости с back_to_reminder
    - **Example 5: Обратная совместимость с callback back_to_reminder**
    - **Validates: Requirements 9.3**
    - Проверить, что callback корректно обрабатывается
    - _Requirements: 9.3_
  
  - [ ]* 10.2 Unit test для обратной совместимости со старыми уведомлениями
    - **Example 6: Обратная совместимость со старыми уведомлениями**
    - **Validates: Requirements 9.4**
    - Проверить, что старые callback показывают полную клавиатуру
    - _Requirements: 9.4_

- [x] 11. Обновить существующие тесты
  - Обновить тесты, которые используют hasActiveReminders()
  - Обновить тесты, которые проверяют логику определения контекста
  - Убедиться, что все существующие тесты проходят
  - _Requirements: 8.4_

- [x] 12. Final checkpoint - Убедиться, что все тесты проходят
  - Запустить все тесты проекта
  - Убедиться, что нет регрессий
  - Проверить обратную совместимость
  - Спросить пользователя, если возникли вопросы

## Notes

- Задачи, отмеченные `*`, являются опциональными и могут быть пропущены для более быстрого MVP
- Каждая задача ссылается на конкретные требования для отслеживаемости
- Checkpoint задачи обеспечивают инкрементальную валидацию
- Property tests валидируют универсальные свойства корректности
- Unit tests валидируют конкретные примеры и граничные случаи
- Обратная совместимость критически важна - старые callback должны продолжать работать

