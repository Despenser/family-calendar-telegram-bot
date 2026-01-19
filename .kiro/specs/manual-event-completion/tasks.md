# План реализации: Ручное завершение событий

- [x] 1. Добавить префикс COMPLETE_EVENT в CallbackPrefix enum




  - Добавить новую константу `COMPLETE_EVENT("complete_event_")` в enum CallbackPrefix
  - Разместить константу в секции "События" после DELETE_EVENT
  - _Requirements: 1.1_


- [x] 2. Реализовать метод completeEvent в EventService




  - Создать метод `completeEvent(Long eventId, Long userId)` с полной валидацией
  - Проверить существование события (EventNotFoundException)
  - Проверить права доступа через `event.belongsToUser(userId)` (UnauthorizedAccessException)
  - Проверить статус события - должен быть ACTIVE (IllegalStateException)
  - Установить статус COMPLETED и completedAt в текущее время
  - Сохранить событие в БД
  - Записать изменение в историю через EventHistoryService
  - Вызвать handleEventCompletion для обработки напоминаний
  - Добавить логирование на уровнях DEBUG, INFO, WARN
  - _Requirements: 1.2, 1.3, 1.4, 1.5, 3.1, 3.2, 3.3, 5.1_

- [ ]* 2.1 Написать property-тест для completeEvent
  - **Property 1: Завершение изменяет статус и устанавливает время**
  - **Validates: Requirements 1.2, 1.3**

- [ ]* 2.2 Написать property-тест для проверки прав доступа
  - **Property 2: Только создатель может завершить событие**
  - **Validates: Requirements 3.1, 3.2**

- [ ]* 2.3 Написать property-тест для исключения из активных
  - **Property 3: Завершенное событие исключается из списка активных**
  - **Validates: Requirements 1.5**

- [ ]* 2.4 Написать property-тест для записи в историю
  - **Property 4: Завершение записывается в историю изменений**
  - **Validates: Requirements 1.4**

- [ ]* 2.5 Написать property-тест для напоминаний
  - **Property 5: Напоминания отмечаются как отправленные при завершении**
  - **Validates: Requirements 5.1**

- [ ]* 2.6 Написать property-тест для неактивных событий
  - **Property 7: Нельзя завершить неактивное событие**
  - **Validates: Requirements 4.2, 4.3, 4.4**

- [ ]* 2.7 Написать unit-тесты для EventService.completeEvent
  - Тест успешного завершения активного события
  - Тест EventNotFoundException для несуществующего события
  - Тест UnauthorizedAccessException для чужого события
  - Тест IllegalStateException для завершенного события
  - Тест IllegalStateException для удаленного события
  - Тест IllegalStateException для черновика
  - _Requirements: 1.2, 1.3, 1.4, 3.1, 3.2, 3.3, 4.2, 4.3, 4.4_

- [x] 3. Добавить обработку callback в EventCallbackHandler





  - Расширить метод `canHandle()` для поддержки COMPLETE_EVENT
  - Добавить условие в метод `handle()` для обработки COMPLETE_EVENT
  - Создать приватный метод `handleCompleteEvent()`
  - Извлечь eventId из callback data
  - Вызвать EventService.completeEvent()
  - Отправить подтверждающее сообщение с предложением добавить заметку
  - Создать клавиатуру с кнопкой "Добавить заметку" (используя ADD_COMPLETION_NOTE)
  - Обработать ошибки через @HandleCallbackErrors
  - Ответить на callback query
  - _Requirements: 1.2, 2.1, 2.2_

- [ ]* 3.1 Написать property-тест для подтверждения
  - **Property 8: Подтверждение отправляется после завершения**
  - **Validates: Requirements 2.1, 2.2**

- [ ]* 3.2 Написать unit-тесты для EventCallbackHandler.handleCompleteEvent
  - Тест успешной обработки callback
  - Тест отправки подтверждающего сообщения
  - Тест создания клавиатуры с кнопкой добавления заметки
  - Тест обработки EventNotFoundException
  - Тест обработки UnauthorizedAccessException
  - Тест обработки IllegalStateException
  - _Requirements: 1.2, 2.1, 2.2, 3.2, 3.3_


- [x] 4. Добавить кнопку "Завершить событие" в MyEventsCommandHandler




  - Модифицировать метод создания клавиатуры деталей события
  - Добавить проверку: `event.getStatus() == Event.EventStatus.ACTIVE && event.belongsToUser(userId)`
  - Создать кнопку с текстом "✅ Завершить событие"
  - Установить callback data через `CallbackPrefix.COMPLETE_EVENT.withPayload(event.getId().toString())`
  - Разместить кнопку перед кнопкой "Удалить"
  - _Requirements: 1.1, 4.1, 4.2, 4.3, 4.4_

- [ ]* 4.1 Написать property-тест для отображения кнопки
  - **Property 6: Кнопка завершения отображается только для активных событий создателя**
  - **Validates: Requirements 1.1, 4.1, 4.2, 4.3, 4.4**

- [ ]* 4.2 Написать unit-тесты для клавиатуры деталей события
  - Тест отображения кнопки для активного события создателя
  - Тест отсутствия кнопки для завершенного события
  - Тест отсутствия кнопки для удаленного события
  - Тест отсутствия кнопки для черновика
  - Тест отсутствия кнопки для чужого активного события
  - _Requirements: 1.1, 4.1, 4.2, 4.3, 4.4_


- [x] 5. Checkpoint - Убедиться что все тесты проходят




  - Ensure all tests pass, ask the user if questions arise.
