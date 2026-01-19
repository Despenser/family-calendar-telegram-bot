# План реализации: Управление сообщениями в корзине

- [x] 1. Добавить поле isTrashHeader в модель Event и создать миграцию базы данных





  - Добавить поле `isTrashHeader` типа Boolean с значением по умолчанию false в класс Event
  - Создать миграцию V15__Add_is_trash_header_to_events.sql для добавления колонки в таблицу events
  - Создать индекс для быстрого поиска события с шапкой корзины
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [ ]* 1.1 Написать property test для round-trip персистентности isTrashHeader
  - **Property 12: Round-trip персистентности isTrashHeader**
  - **Validates: Requirements 5.3, 5.4**


- [x] 2. Добавить метод buildTrashHeader в BotMessageBuilder



  - Реализовать метод для формирования шапки корзины с количеством событий
  - Использовать MarkdownV2 форматирование с правильным экранированием
  - Включить эмодзи 🗑️ и italic текст для подсказки о сроке хранения
  - _Requirements: 4.1, 3.2_

- [ ]* 2.1 Написать unit test для buildTrashHeader
  - Проверить формат шапки с разным количеством событий
  - Проверить корректность MarkdownV2 форматирования
  - _Requirements: 4.1, 3.2_




- [ ] 3. Обновить TrashCommandHandler для сохранения messageId и управления флагом isTrashHeader

  - Добавить зависимость от KeyboardService и BotMessageBuilder
  - Реализовать установку флага isTrashHeader=true для первого события
  - Реализовать сброс флага isTrashHeader для остальных событий
  - Реализовать отправку первого события с шапкой в одном сообщении
  - Сохранять messageId для всех отправленных сообщений событий
  - Использовать sendMessageAndGet вместо sendMessage для получения messageId
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 2.1, 2.2_

- [ ]* 3.1 Написать property test для установки флага isTrashHeader
  - **Property 11: Установка флага для первого события**
  - **Validates: Requirements 4.3**

- [ ]* 3.2 Написать property test для сохранения messageId
  - **Property 10: Сохранение messageId для всех событий**
  - **Validates: Requirements 4.2, 4.4**

- [ ]* 3.3 Написать property test для объединенного сообщения
  - **Property 9: Объединенное сообщение для первого события**
  - **Validates: Requirements 4.1**

- [ ]* 3.4 Написать unit test для TrashCommandHandler
  - Тест отображения пустой корзины



  - Тест отображения корзины с одним событием
  - Тест отображения корзины с несколькими событиями
  - _Requirements: 3.1, 3.3, 4.1, 4.2, 4.3, 4.4_

- [ ] 4. Добавить методы в TrashService для управления шапкой корзины

  - Реализовать метод updateTrashHeaderAfterRemoval(userId)
  - Реализовать метод updateTrashHeaderCount(userId)
  - Реализовать метод buildEmptyTrashMessage()
  - Добавить логику отправки сообщения о пустой корзине
  - Добавить логику переноса флага isTrashHeader на следующее событие
  - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.3, 6.1, 6.2, 6.3_

- [ ]* 4.1 Написать property test для переноса флага isTrashHeader
  - **Property 4: Перенос флага isTrashHeader**
  - **Validates: Requirements 2.1**

- [ ]* 4.2 Написать property test для уникальности флага isTrashHeader
  - **Property 5: Уникальность флага isTrashHeader**
  - **Validates: Requirements 2.2**

- [ ]* 4.3 Написать property test для обновления сообщения при установке флага
  - **Property 6: Обновление сообщения при установке флага**
  - **Validates: Requirements 2.3**

- [ ]* 4.4 Написать property test для консистентности сообщения о пустой корзине
  - **Property 7: Консистентность сообщения о пустой корзине**
  - **Validates: Requirements 3.1, 3.3**

- [ ]* 4.5 Написать property test для MarkdownV2 форматирования
  - **Property 8: MarkdownV2 форматирование пустой корзины**
  - **Validates: Requirements 3.2**




- [ ]* 4.6 Написать unit test для TrashService
  - Тест updateTrashHeaderAfterRemoval с пустой корзиной
  - Тест updateTrashHeaderAfterRemoval с одним событием
  - Тест updateTrashHeaderAfterRemoval с несколькими событиями
  - Тест updateTrashHeaderCount
  - _Requirements: 2.1, 2.2, 2.3, 3.1, 6.1, 6.2, 6.3_

- [ ] 5. Обновить TrashService.restoreEvent для удаления сообщений и обновления шапки

  - Добавить удаление сообщения события перед восстановлением
  - Сбросить флаг isTrashHeader при восстановлении
  - Вызвать updateTrashHeaderAfterRemoval после восстановления
  - Убрать отправку сообщения "♻️ Событие восстановлено"
  - _Requirements: 1.1, 1.3, 1.4, 2.1, 6.1_

- [ ]* 5.1 Написать property test для удаления сообщения при восстановлении
  - **Property 1: Удаление сообщения при восстановлении события**


  - **Validates: Requirements 1.1, 1.3**

- [ ]* 5.2 Написать property test для отсутствия уведомлений при восстановлении
  - **Property 3: Отсутствие дополнительных уведомлений**
  - **Validates: Requirements 1.4**

- [ ]* 5.3 Написать property test для обновления счетчика при восстановлении
  - **Property 13: Обновление счетчика при восстановлении**
  - **Validates: Requirements 6.1**

- [ ] 6. Обновить TrashService.permanentlyDelete для удаления сообщений и обновления шапки






  - Добавить удаление сообщения события перед окончательным удалением
  - Вызвать updateTrashHeaderAfterRemoval после удаления
  - Убрать отправку сообщения "❌ Событие удалено навсегда"
  - _Requirements: 1.2, 1.3, 1.4, 2.1, 6.2_

- [ ]* 6.1 Написать property test для удаления сообщения при окончательном удалении
  - **Property 2: Удаление сообщения при окончательном удалении**
  - **Validates: Requirements 1.2, 1.3**

- [ ]* 6.2 Написать property test для обновления счетчика при удалении
  - **Property 14: Обновление счетчика при удалении**
  - **Validates: Requirements 6.2**


- [x] 7. Обновить TrashCallbackHandler для удаления дополнительных сообщений






  - Убрать отправку подтверждающих сообщений в handleRestore
  - Убрать отправку подтверждающих сообщений в handlePermanentDelete
  - Оставить только обработку ошибок без отправки сообщений
  - _Requirements: 1.4_

- [ ]* 7.1 Написать unit test для TrashCallbackHandler
  - Тест восстановления события без отправки сообщения
  - Тест окончательного удаления без отправки сообщения
  - Тест обработки ошибок
  - _Requirements: 1.4_

- [ ] 8. Добавить property test для использования editMessageText

  - **Property 15: Использование editMessageText для обновления счетчика**
  - **Validates: Requirements 6.3**

- [ ] 9. Добавить property test для обработки ошибок обновления счетчика

  - **Property 16: Обработка ошибок обновления счетчика**
  - **Validates: Requirements 6.4**

- [x] 10. Обновить TrashService.cleanupOldTrash для удаления сообщений




  - Добавить удаление сообщений событий перед окончательным удалением в автоматической очистке
  - Обработать ошибки удаления сообщений без прерывания процесса очистки
  - _Requirements: 1.2, 1.3_

- [ ]* 10.1 Написать unit test для cleanupOldTrash
  - Тест удаления сообщений при автоматической очистке
  - Тест обработки ошибок удаления сообщений
  - _Requirements: 1.2, 1.3_

- [ ] 11. Checkpoint - Убедиться, что все тесты проходят

  - Ensure all tests pass, ask the user if questions arise.

- [ ]* 12. Написать integration test для полного цикла работы с корзиной
  - Тест полного цикла: создание → удаление → восстановление
  - Тест полного цикла: создание → удаление → окончательное удаление
  - Тест обновления шапки при изменении количества событий
  - Тест отображения пустой корзины
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 3.1, 3.3, 4.1, 4.2, 4.3, 4.4, 6.1, 6.2_
