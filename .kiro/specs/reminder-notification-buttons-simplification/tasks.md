# План реализации: Упрощение кнопок в уведомлениях о напоминаниях

## Обзор

Данный план описывает пошаговую реализацию упрощения интерфейса уведомлений о напоминаниях. Основная цель - создать минималистичный интерфейс с одной кнопкой "Посмотреть детали", которая открывает детали события в том же сообщении, и кнопкой "Назад к напоминанию" для возврата к минималистичному виду.

## Задачи

- [x] 1. Добавить новый callback prefix BACK_TO_REMINDER
  - Открыть класс CallbackPrefix
  - Добавить новую константу BACK_TO_REMINDER("back_to_reminder_")
  - Добавить JavaDoc с описанием формата: back_to_reminder_{eventId}_{reminderId}
  - _Requirements: 5.1, 6.1_

- [ ]* 1.1 Написать property-тест для Property 1
  - **Property 1: Структура клавиатуры напоминания**
  - **Validates: Requirements 1.1, 1.2, 1.3**

- [x] 2. Создать метод createSimplifiedReminderKeyboard в ReminderService
  - Создать приватный метод createSimplifiedReminderKeyboard(Event event)
  - Метод должен возвращать InlineKeyboardMarkup с одним рядом и одной кнопкой
  - Кнопка: текст "📋 Посмотреть детали", callback data "view_event_{eventId}"
  - Добавить JavaDoc с описанием метода
  - Добавить логирование debug уровня с eventId
  - _Requirements: 1.1, 1.2, 1.3, 9.1_

- [ ]* 2.1 Написать unit-тест для createSimplifiedReminderKeyboard
  - Проверить структуру клавиатуры (1 ряд, 1 кнопка)
  - Проверить текст кнопки
  - Проверить формат callback data
  - _Requirements: 1.1, 1.2, 1.3_

- [x] 3. Изменить метод createReminderKeyboard в ReminderService
  - Заменить текущую реализацию на вызов createSimplifiedReminderKeyboard(event)
  - Удалить код создания кнопок "Редактировать" и "Удалить"
  - Обновить JavaDoc с описанием изменений
  - _Requirements: 1.1, 1.2, 1.3_

- [ ]* 3.1 Написать property-тест для Property 6
  - **Property 6: Единообразие клавиатур для всех типов напоминаний**
  - **Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5**

- [x] 4. Создать метод createDetailsKeyboard в EventCallbackHandler
  - Создать приватный метод createDetailsKeyboard(Long eventId, Long reminderId)
  - Метод должен возвращать InlineKeyboardMarkup с одним рядом и одной кнопкой
  - Кнопка: текст "◀️ Назад к напоминанию", callback data "back_to_reminder_{eventId}_{reminderId}"
  - Добавить JavaDoc с описанием метода
  - _Requirements: 3.1, 3.5_

- [ ]* 4.1 Написать unit-тест для createDetailsKeyboard
  - Проверить структуру клавиатуры (1 ряд, 1 кнопка)
  - Проверить текст кнопки
  - Проверить формат callback data
  - _Requirements: 3.1, 3.5_

- [ ]* 4.2 Написать property-тест для Property 3
  - **Property 3: Отсутствие кнопок действий в деталях**
  - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

- [x] 5. Изменить метод handleViewEvent в EventCallbackHandler
  - Изменить сигнатуру метода: добавить параметр Integer messageId
  - Изменить логику: вместо sendMessage использовать editMessageText
  - Получить событие через EventService.getEventById(eventId)
  - Сформировать текст через BotMessageBuilder.buildEventMessage(event)
  - Определить, вызван ли метод из напоминания (проверить источник callback)
  - Если из напоминания: использовать createDetailsKeyboard(eventId, reminderId)
  - Если из другого места: использовать KeyboardService.createEventActionsKeyboard(event, userId)
  - Обновить сообщение через editMessageText(chatId, messageId, text, keyboard)
  - Добавить обработку ошибок (событие не найдено, ошибка API)
  - Добавить логирование debug/info/warning уровней
  - Обновить JavaDoc с описанием изменений
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 8.1, 8.4, 8.5, 9.2, 9.3, 9.4_

- [ ]* 5.1 Написать property-тест для Property 2
  - **Property 2: Обновление сообщения при просмотре деталей**
  - **Validates: Requirements 2.1, 2.3, 2.4, 2.5**

- [ ]* 5.2 Написать property-тест для Property 5
  - **Property 5: Сохранение messageId**
  - **Validates: Requirements 2.2, 4.4**

- [ ]* 5.3 Написать property-тест для Property 7
  - **Property 7: Обратная совместимость просмотра событий**
  - **Validates: Requirements 8.1, 8.4, 8.5**

- [ ]* 5.4 Написать unit-тесты для handleViewEvent
  - Тест: успешное обновление сообщения из напоминания
  - Тест: успешное обновление сообщения из другого места
  - Тест: событие не найдено
  - Тест: ошибка Telegram API
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 8.1, 8.4, 8.5_

- [x] 6. Создать метод handleBackToReminder в EventCallbackHandler
  - Создать приватный метод handleBackToReminder(String callbackData, Long userId, Long chatId, Integer messageId, String callbackQueryId)
  - Извлечь eventId и reminderId из callback data
  - Загрузить событие и напоминание из базы данных
  - Восстановить текст напоминания через ReminderService.formatReminderMessageByType()
  - Создать упрощенную клавиатуру через ReminderService.createSimplifiedReminderKeyboard()
  - Обновить сообщение через editMessageText(chatId, messageId, text, keyboard)
  - Отправить callback query answer с подтверждением
  - Добавить обработку ошибок (событие/напоминание не найдено, ошибка API)
  - Добавить логирование debug/info/warning уровней
  - Добавить JavaDoc с описанием метода
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 6.1, 6.2, 6.3, 6.4, 6.5, 9.2, 9.3, 9.4, 9.5_

- [ ]* 6.1 Написать property-тест для Property 4
  - **Property 4: Round-trip восстановление напоминания**
  - **Validates: Requirements 4.1, 4.2, 4.3**

- [ ]* 6.2 Написать unit-тесты для handleBackToReminder
  - Тест: успешное восстановление напоминания
  - Тест: событие не найдено
  - Тест: напоминание не найдено
  - Тест: ошибка Telegram API
  - Тест: некорректный формат callback data
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 6.1, 6.2, 6.3, 6.4, 6.5_

- [x] 7. Обновить метод canHandle в EventCallbackHandler
  - Добавить проверку CallbackPrefix.BACK_TO_REMINDER.matches(callbackData)
  - Обновить JavaDoc
  - _Requirements: 6.1_

- [x] 8. Обновить метод handle в EventCallbackHandler
  - Добавить обработку BACK_TO_REMINDER в цепочку if-else
  - Вызвать handleBackToReminder при совпадении префикса
  - Обновить JavaDoc
  - _Requirements: 6.1_

- [x] 9. Добавить метод getReminderById в ReminderService (если не существует)
  - Создать публичный метод getReminderById(Long reminderId)
  - Метод должен возвращать Reminder или выбрасывать исключение
  - Добавить JavaDoc с описанием метода
  - _Requirements: 6.2_

- [ ]* 9.1 Написать unit-тест для getReminderById
  - Тест: успешное получение напоминания
  - Тест: напоминание не найдено
  - _Requirements: 6.2_

- [x] 10. Checkpoint - Убедиться, что все тесты проходят
  - Запустить все unit-тесты
  - Запустить все property-тесты
  - Убедиться, что не сломаны другие тесты
  - Спросить пользователя, если возникнут вопросы

## Примечания

- Задачи, помеченные `*`, являются опциональными и могут быть пропущены для более быстрой реализации MVP
- Каждая задача ссылается на конкретные требования для отслеживаемости
- Property-тесты валидируют универсальные свойства корректности
- Unit-тесты валидируют конкретные примеры и граничные случаи
- Все изменения должны сохранять обратную совместимость с существующей функциональностью
- Используется библиотека jqwik для property-based тестирования (минимум 100 итераций на тест)
