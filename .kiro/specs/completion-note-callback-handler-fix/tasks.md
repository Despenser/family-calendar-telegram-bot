# План реализации: Исправление обработки callback для добавления заметки к завершенному событию

- [x] 1. Обновить метод canHandle в EventCallbackHandler





  - Добавить проверку для `CallbackPrefix.ADD_COMPLETION_NOTE.matches(callbackData)`
  - Добавить проверку для `CallbackPrefix.SKIP_COMPLETION_NOTE.matches(callbackData)`
  - _Requirements: 1.3, 2.1, 2.2_

- [ ]* 1.1 Написать property тест для метода canHandle
  - **Property 1: Маршрутизация ADD_COMPLETION_NOTE**
  - **Property 2: Маршрутизация SKIP_COMPLETION_NOTE**
  - **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2**


- [x] 2. Обновить метод handle в EventCallbackHandler




  - Добавить условие для `CallbackPrefix.ADD_COMPLETION_NOTE.matches(callbackData)` с вызовом `handleAddCompletionNote`
  - Добавить условие для `CallbackPrefix.SKIP_COMPLETION_NOTE.matches(callbackData)` с вызовом `handleSkipCompletionNote`
  - _Requirements: 1.1, 1.2, 1.4, 1.5, 2.1, 2.2_

- [ ]* 2.1 Написать unit тесты для обработки ADD_COMPLETION_NOTE
  - Создать тест, проверяющий вызов `conversationStateService.setAwaitingCompletionNote`
  - Проверить отправку сообщения с просьбой ввести заметку
  - Проверить вызов `messageService.answerCallbackQuery`
  - _Requirements: 1.1, 1.2, 2.1_

- [ ]* 2.2 Написать unit тесты для обработки SKIP_COMPLETION_NOTE
  - Создать тест, проверяющий отправку подтверждающего сообщения
  - Проверить вызов `messageService.answerCallbackQuery`
  - _Requirements: 1.4, 1.5, 2.2_

- [ ] 3. Проверить работу исправления
  - Запустить приложение
  - Завершить событие и нажать кнопку "📝 Добавить заметку"
  - Убедиться, что не возникает ошибка "Неизвестный callback data"
  - Убедиться, что система переходит в режим ожидания заметки
  - Проверить кнопку "⏭️ Пропустить"
  - _Requirements: 1.1, 1.2, 1.4, 1.5, 2.3, 2.4_


- [x] 4. Checkpoint - Убедиться что все тесты проходят



  - Ensure all tests pass, ask the user if questions arise.
