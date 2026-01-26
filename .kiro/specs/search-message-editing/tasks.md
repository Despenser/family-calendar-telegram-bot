# План реализации: Редактирование сообщения при поиске событий

## Обзор

Этот план описывает пошаговую реализацию функциональности редактирования сообщения при поиске событий. Реализация включает изменения в базе данных, сервисах, обработчиках команд и тестирование.

## Задачи

- [x] 1. Создать миграцию базы данных для хранения контекста поиска
  - Создать файл миграции V18__Add_search_context_to_conversation_states.sql
  - Добавить поля search_chat_id и search_message_id в таблицу conversation_states
  - Добавить комментарии к полям
  - _Requirements: 1.2, 4.1_

- [x] 2. Обновить модель ConversationState
  - [x] 2.1 Добавить поля для контекста поиска
    - Добавить поля searchChatId и searchMessageId
    - Добавить аннотации @Column
    - _Requirements: 1.2, 4.1_

  - [x] 2.2 Добавить методы для работы с контекстом поиска
    - Реализовать hasSearchContext()
    - Реализовать clearSearchContext()
    - _Requirements: 4.1, 4.4_

- [x] 3. Расширить ConversationStateService
  - [x] 3.1 Добавить класс SearchQueryContext
    - Создать внутренний статический класс с полями chatId и messageId
    - Добавить аннотации @Data и @AllArgsConstructor
    - _Requirements: 1.2, 4.2_

  - [x] 3.2 Реализовать методы управления состоянием поиска
    - Реализовать setAwaitingSearchQuery(Long userId, Long chatId, Integer messageId)
    - Реализовать getSearchQueryContext(Long userId)
    - Реализовать clearAwaitingSearchQuery(Long userId)
    - Добавить транзакционность (@Transactional) где необходимо
    - _Requirements: 1.2, 4.1, 4.2, 4.3, 4.4_

  - [ ]* 3.3 Написать unit тесты для новых методов ConversationStateService
    - Тест setAwaitingSearchQuery()
    - Тест getSearchQueryContext()
    - Тест clearAwaitingSearchQuery()
    - Тест сохранения в базе данных
    - _Requirements: 1.2, 4.1, 4.2_

- [x] 4. Добавить CallbackPrefix для кнопки повторного поиска
  - Добавить SEARCH_AGAIN("search_again:") в enum CallbackPrefix
  - _Requirements: 3.1, 5.1_

- [x] 5. Обновить SearchCommandHandler для редактирования сообщений
  - [x] 5.1 Изменить метод handle() для сохранения message_id
    - Использовать sendMessageWithInlineKeyboardAndGet() вместо возврата строки
    - Сохранить message_id через conversationStateService.setAwaitingSearchQuery()
    - Обработать исключения TelegramApiException
    - _Requirements: 1.1, 1.2_

  - [x] 5.2 Обновить метод performSearch() для редактирования сообщения
    - Получить SearchQueryContext из conversationStateService
    - Использовать tryEditMessageText() для редактирования сообщения
    - Реализовать fallback на sendMessage() при ошибке редактирования
    - Добавить кнопку "🔍 Найти заново" в результаты
    - Удалить сообщение пользователя с запросом через deleteMessage()
    - Обработать короткие запросы (< 2 символов) с редактированием сообщения
    - _Requirements: 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 6.1, 6.2, 6.4, 7.1, 7.2_

  - [x] 5.3 Реализовать метод handleSearchAgainCallback()
    - Создать новый метод для обработки callback "search_again:"
    - Редактировать сообщение, возвращая к запросу текста
    - Установить состояние ожидания нового запроса
    - Ответить на callback query
    - Обработать исключения
    - _Requirements: 3.2, 3.3, 3.4, 5.2, 5.3, 5.4_

  - [ ]* 5.4 Написать unit тесты для SearchCommandHandler
    - Тест handle() - проверка отправки и сохранения message_id
    - Тест performSearch() - успешное редактирование
    - Тест performSearch() - fallback при ошибке редактирования
    - Тест performSearch() - удаление сообщения пользователя
    - Тест performSearch() - валидация короткого запроса
    - Тест performSearch() - добавление кнопки "Найти заново"
    - Тест handleSearchAgainCallback() - редактирование сообщения
    - Тест handleSearchAgainCallback() - установка состояния
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.6, 3.1, 3.2, 6.1, 6.2, 6.4, 7.1_

- [x] 6. Обновить CallbackQueryDispatcher
  - Добавить маршрутизацию callback "search_again:" к SearchCommandHandler
  - Проверить, что SearchCommandHandler реализует интерфейс CallbackHandler
  - _Requirements: 5.2_

- [x] 7. Обновить UpdateProcessor для обработки поисковых запросов
  - Проверить, что UpdateProcessor вызывает performSearch() при получении текста от пользователя в состоянии ожидания поиска
  - Убедиться, что передается messageId пользовательского сообщения для удаления
  - _Requirements: 1.3, 7.1_

- [x] 8. Checkpoint - Проверка базовой функциональности
  - Запустить все тесты
  - Проверить, что миграция применяется корректно
  - Убедиться, что нет ошибок компиляции
  - _Requirements: все_

- [ ]* 9. Написать property-based тесты
  - [ ]* 9.1 Property 1: Редактирование сообщения при вводе запроса
    - **Property 1: Редактирование сообщения при вводе запроса**
    - **Validates: Requirements 1.3, 1.4**
    - Генерировать случайные запросы (2-100 символов)
    - Проверять, что tryEditMessageText() вызывается, а sendMessage() - нет

  - [ ]* 9.2 Property 2: Сохранение message_id
    - **Property 2: Сохранение message_id**
    - **Validates: Requirements 1.2**
    - Генерировать случайные userId и chatId
    - Проверять, что message_id сохраняется в ConversationState

  - [ ]* 9.3 Property 3: Отображение кнопки повторного поиска
    - **Property 3: Отображение кнопки повторного поиска**
    - **Validates: Requirements 2.6, 3.1**
    - Генерировать случайные результаты поиска (включая пустые)
    - Проверять наличие кнопки "search_again:" в клавиатуре

  - [ ]* 9.4 Property 5: Удаление пользовательских сообщений
    - **Property 5: Удаление пользовательских сообщений**
    - **Validates: Requirements 7.1, 7.4**
    - Генерировать случайные messageId
    - Проверять, что deleteMessage() вызывается с правильными параметрами

- [ ]* 10. Написать integration тесты
  - [ ]* 10.1 Тест полного цикла поиска
    - Отправка команды /search
    - Ввод поискового запроса
    - Проверка редактирования сообщения
    - Нажатие кнопки "Найти заново"
    - Проверка возврата к запросу текста
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.6, 3.1, 3.2_

  - [ ]* 10.2 Тест fallback при ошибке редактирования
    - Отправка команды /search
    - Симуляция удаления сообщения
    - Ввод поискового запроса
    - Проверка отправки нового сообщения
    - _Requirements: 6.1, 6.2_

- [ ] 11. Checkpoint - Финальная проверка
  - Запустить все тесты (unit, property-based, integration)
  - Проверить покрытие кода тестами
  - Убедиться, что все требования выполнены
  - Проверить логирование и обработку ошибок
  - _Requirements: все_

## Примечания

- Задачи, помеченные `*`, являются опциональными и могут быть пропущены для более быстрой реализации MVP
- Каждая задача ссылается на конкретные требования для отслеживаемости
- Checkpoint задачи обеспечивают инкрементальную валидацию
- Property тесты валидируют универсальные свойства корректности
- Unit тесты валидируют конкретные примеры и граничные случаи
- Integration тесты проверяют end-to-end потоки

