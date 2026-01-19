# План реализации: Удаление сообщений событий из чата

- [ ] 1. Добавить удаление сообщений в TrashService




  - Добавить зависимость TelegramMessageService в TrashService
  - Реализовать удаление сообщения в методе permanentlyDelete()
  - Реализовать удаление сообщения в методе restoreEvent()
  - Добавить проверки на null messageId
  - Добавить логирование операций удаления
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4_

- [ ]* 1.1 Написать property тест для удаления сообщения при окончательном удалении
  - **Property 1: Удаление сообщения при окончательном удалении**
  - **Validates: Requirements 1.1**

- [ ]* 1.2 Написать property тест для удаления сообщения при восстановлении
  - **Property 2: Удаление сообщения при восстановлении**
  - **Validates: Requirements 2.1**

- [ ]* 1.3 Написать property тест для graceful обработки null messageId
  - **Property 5: Graceful обработка отсутствующего messageId**
  - **Validates: Requirements 1.4, 2.4**

- [ ]* 1.4 Написать unit тесты для TrashService
  - Тест вызова deleteMessage при permanentlyDelete с непустым messageId
  - Тест вызова deleteMessage при restoreEvent с непустым messageId
  - Тест пропуска вызова при null messageId
  - Тест сброса messageId после восстановления
  - Тест продолжения выполнения при ошибке удаления
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4_

- [x] 2. Добавить удаление сообщений в EventCallbackHandler





  - Реализовать удаление сообщения в методе handleDeleteEvent()
  - Реализовать удаление сообщения в методе handleCompleteEvent()
  - Добавить сохранение messageId перед удалением/завершением события
  - Добавить проверки на null messageId
  - Добавить логирование операций удаления
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 4.3, 4.4_

- [ ]* 2.1 Написать property тест для удаления сообщения при удалении из /my_events
  - **Property 3: Удаление сообщения при удалении из /my_events**
  - **Validates: Requirements 3.1**

- [ ]* 2.2 Написать property тест для удаления сообщения при завершении из /my_events
  - **Property 4: Удаление сообщения при завершении из /my_events**
  - **Validates: Requirements 4.1**

- [ ]* 2.3 Написать unit тесты для EventCallbackHandler
  - Тест вызова deleteMessage при handleDeleteEvent с непустым messageId
  - Тест вызова deleteMessage при handleCompleteEvent с непустым messageId
  - Тест пропуска вызова при null messageId
  - Тест продолжения выполнения при ошибке удаления
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 4.3, 4.4_


- [x] 3. Checkpoint - Убедиться, что все тесты проходят




  - Ensure all tests pass, ask the user if questions arise.

- [ ]* 4. Написать integration тест для полного цикла
  - Создать EventMessageDeletionIntegrationTest
  - Тест полного цикла: создание → удаление → восстановление → окончательное удаление
  - Проверка удаления сообщений на каждом этапе
  - Проверка работы с реальной БД и mock TelegramMessageService
  - _Requirements: 1.1, 2.1, 3.1, 4.1_

- [ ]* 5. Написать property тест для логирования
  - **Property 6: Логирование успешного удаления**
  - **Validates: Requirements 1.2, 2.2, 3.2, 4.2**

- [ ]* 6. Написать property тест для продолжения выполнения при ошибках
  - **Property 7: Продолжение выполнения при ошибке удаления**
  - **Validates: Requirements 1.3, 2.3, 3.3, 4.3**


- [x] 7. Final Checkpoint - Убедиться, что все тесты проходят




  - Ensure all tests pass, ask the user if questions arise.
