# План реализации: Обновление счетчика событий в шапке при удалении

- [x] 1. Реализовать метод обновления шапки в MyEventsCommandHandler





  - Создать метод `updateMyEventsHeaderCount(Long userId)`
  - Получить актуальное количество активных событий пользователя
  - Найти событие с флагом `isMyEventsHeader=true`
  - Сформировать новую шапку с актуальным счетчиком
  - Сформировать полный текст сообщения (шапка + событие)
  - Обновить сообщение через `TelegramMessageService.tryEditMessageText()`
  - Обработать случай отсутствия событий с шапкой
  - Обработать ошибки Telegram API без выброса исключений
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5_

- [ ]* 1.1 Написать property-тест для обновления счетчика при изменении списка
  - **Property 1: Обновление счетчика при изменении списка**
  - **Validates: Requirements 1.1, 3.1, 3.2, 3.3, 3.4**

- [ ]* 1.2 Написать property-тест для сохранения структуры шапки и кнопок
  - **Property 2: Сохранение структуры шапки и кнопок**
  - **Validates: Requirements 1.4, 1.5**

- [ ]* 1.3 Написать property-тест для корректности метода обновления
  - **Property 3: Корректность метода обновления**
  - **Validates: Requirements 2.2, 2.3**

- [ ]* 1.4 Написать unit-тесты для метода updateMyEventsHeaderCount
  - Тест корректного формирования шапки
  - Тест вызова методов сервисов
  - Тест обработки отсутствия событий
  - Тест обработки ошибок Telegram API
  - _Requirements: 2.1, 2.2, 2.3, 2.5_


- [ ] 2. Интегрировать обновление шапки в EventService.deleteEvent



  - Добавить зависимость `MyEventsCommandHandler` в `EventService`
  - Вызвать `updateMyEventsHeaderCount(userId)` после удаления события
  - Убедиться что вызов происходит после передачи флага `isMyEventsHeader`
  - _Requirements: 1.1, 1.2, 1.3, 3.1, 3.2_

- [ ]* 2.1 Написать unit-тест для EventService.deleteEvent с обновлением шапки
  - Проверить вызов `updateMyEventsHeaderCount` после удаления
  - Проверить порядок операций (удаление → передача флага → обновление шапки)
  - _Requirements: 1.1, 3.1, 3.2_

- [ ] 3. Интегрировать обновление шапки в EventService.completeEvent




  - Вызвать `updateMyEventsHeaderCount(userId)` после завершения события
  - Убедиться что вызов происходит после изменения статуса
  - _Requirements: 3.3_

- [ ]* 3.1 Написать unit-тест для EventService.completeEvent с обновлением шапки
  - Проверить вызов `updateMyEventsHeaderCount` после завершения
  - _Requirements: 3.3_


- [ ] 4. Интегрировать обновление шапки в TrashService.restoreEvent



  - Добавить зависимость `MyEventsCommandHandler` в `TrashService`
  - Вызвать `updateMyEventsHeaderCount(userId)` после восстановления события
  - Убедиться что вызов происходит после изменения статуса на ACTIVE
  - _Requirements: 3.4_

- [ ]* 4.1 Написать unit-тест для TrashService.restoreEvent с обновлением шапки
  - Проверить вызов `updateMyEventsHeaderCount` после восстановления
  - _Requirements: 3.4_


- [x] 5. Checkpoint - Убедиться что все тесты проходят




  - Ensure all tests pass, ask the user if questions arise.
