# Документ требований: Исправление обработки callback-запросов корзины

## Введение

Команда `/trash` отображает удалённые события с кнопками "Восстановить" и "Удалить навсегда", но при нажатии на эти кнопки система выдаёт ошибку "Неизвестная команда". Это происходит потому, что отсутствует обработчик callback-запросов для корзины.

## Глоссарий

- **System**: Telegram-бот семейного календаря
- **TrashCommandHandler**: Обработчик команды `/trash`, который отображает удалённые события
- **CallbackQueryDispatcher**: Диспетчер, который маршрутизирует callback-запросы к соответствующим обработчикам
- **CallbackHandler**: Интерфейс для обработчиков callback-запросов от inline-кнопок
- **TrashCallbackHandler**: Новый обработчик callback-запросов для действий с корзиной
- **Callback_Data**: Строка данных, передаваемая при нажатии на inline-кнопку

## Требования

### Требование 1: Создание обработчика callback-запросов корзины

**User Story:** Как пользователь, я хочу восстанавливать события из корзины или удалять их навсегда, чтобы управлять удалёнными событиями.

#### Критерии приёмки

1. WHEN пользователь нажимает кнопку "Восстановить" в корзине, THEN THE System SHALL восстановить событие и отправить подтверждающее сообщение
2. WHEN пользователь нажимает кнопку "Удалить навсегда" в корзине, THEN THE System SHALL окончательно удалить событие и отправить подтверждающее сообщение
3. WHEN происходит ошибка при обработке callback-запроса корзины, THEN THE System SHALL отправить пользователю сообщение об ошибке
4. WHEN CallbackQueryDispatcher получает callback с префиксом "trash_", THEN THE System SHALL направить его в TrashCallbackHandler
5. THE TrashCallbackHandler SHALL реализовывать интерфейс CallbackHandler
6. THE TrashCallbackHandler SHALL использовать TrashService для выполнения операций восстановления и удаления

### Требование 2: Интеграция с существующей архитектурой

**User Story:** Как разработчик, я хочу, чтобы новый обработчик соответствовал существующей архитектуре, чтобы код был поддерживаемым и согласованным.

#### Критерии приёмки

1. THE TrashCallbackHandler SHALL быть Spring-компонентом с аннотацией @Component
2. THE TrashCallbackHandler SHALL использовать аннотацию @HandleCallbackErrors для обработки ошибок
3. THE TrashCallbackHandler SHALL использовать CallbackPrefix.TRASH для идентификации callback-запросов
4. THE TrashCallbackHandler SHALL логировать все операции с использованием SLF4J
5. THE TrashCallbackHandler SHALL следовать тому же стилю кода, что и другие CallbackHandler в проекте

### Требование 3: Рефакторинг TrashCommandHandler

**User Story:** Как разработчик, я хочу удалить дублирующий код из TrashCommandHandler, чтобы избежать дублирования логики.

#### Критерии приёмки

1. WHEN TrashCommandHandler создан, THEN THE System SHALL удалить метод handleTrashCallback из TrashCommandHandler
2. WHEN TrashCommandHandler создан, THEN THE System SHALL удалить приватные методы handleRestore и handlePermanentDelete из TrashCommandHandler
3. THE TrashCommandHandler SHALL сохранить только логику отображения списка удалённых событий
4. THE TrashCommandHandler SHALL продолжать создавать inline-кнопки с callback data "trash_restore_" и "trash_delete_"
