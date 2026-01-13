-- ============================================================================
-- Миграция V3: Добавление расширенных функций
-- ============================================================================
-- Описание: Расширение таблицы events для поддержки новых функций:
--           - Временные интервалы (end_time)
--           - Персональные события (is_personal)
--           - Повторяющиеся события (series_id)
--           - Заметки о завершении (completion_note)
--           - Корзина удаленных событий (deleted_at)
--           - Автоматическое завершение (completed_at)
--           - Новые статусы: completed, deleted
-- Требования: 26.2, 32.1, 25.3, 19.2
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Шаг 1: Добавление новых значений в CHECK constraint для статусов
-- ----------------------------------------------------------------------------

-- Удаляем старый CHECK constraint
ALTER TABLE events 
    DROP CONSTRAINT IF EXISTS events_status_check;

-- Добавляем новый CHECK constraint с расширенным списком статусов
ALTER TABLE events 
    ADD CONSTRAINT events_status_check 
    CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'DELETED'));

COMMENT ON CONSTRAINT events_status_check ON events IS 
    'Валидация статуса события: DRAFT (черновик), ACTIVE (активное), COMPLETED (завершенное), DELETED (удаленное в корзину)';

-- ----------------------------------------------------------------------------
-- Шаг 2: Добавление новых полей в таблицу events
-- ----------------------------------------------------------------------------

-- Время окончания события (для временных интервалов)
ALTER TABLE events 
    ADD COLUMN end_time TIME;

COMMENT ON COLUMN events.end_time IS 
    'Время окончания события (опционально, для событий с временным интервалом)';

-- Флаг персонального события
ALTER TABLE events 
    ADD COLUMN is_personal BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN events.is_personal IS 
    'Флаг персонального события: true - видно только создателю, false - видно всей семье';

-- Идентификатор серии для повторяющихся событий
ALTER TABLE events 
    ADD COLUMN series_id VARCHAR(255);

COMMENT ON COLUMN events.series_id IS 
    'UUID серии для связи повторяющихся событий (NULL для обычных событий)';

-- Заметка о завершении события
ALTER TABLE events 
    ADD COLUMN completion_note TEXT;

COMMENT ON COLUMN events.completion_note IS 
    'Заметка пользователя о том, как прошло завершенное событие';

-- Дата и время удаления (для корзины)
ALTER TABLE events 
    ADD COLUMN deleted_at TIMESTAMP;

COMMENT ON COLUMN events.deleted_at IS 
    'Дата и время перемещения события в корзину (NULL для активных событий)';

-- Дата и время завершения
ALTER TABLE events 
    ADD COLUMN completed_at TIMESTAMP;

COMMENT ON COLUMN events.completed_at IS 
    'Дата и время автоматического или ручного завершения события';

-- ----------------------------------------------------------------------------
-- Шаг 3: Создание индексов для оптимизации запросов
-- ----------------------------------------------------------------------------

-- Индекс для поиска событий по series_id (повторяющиеся события)
CREATE INDEX idx_events_series_id ON events(series_id) 
    WHERE series_id IS NOT NULL;

COMMENT ON INDEX idx_events_series_id IS 
    'Индекс для быстрого поиска всех событий одной серии повторений';

-- Индекс для фильтрации персональных событий
CREATE INDEX idx_events_is_personal ON events(is_personal);

COMMENT ON INDEX idx_events_is_personal IS 
    'Индекс для быстрой фильтрации персональных и семейных событий';

-- Индекс для работы с корзиной (удаленные события)
CREATE INDEX idx_events_deleted_at ON events(deleted_at) 
    WHERE deleted_at IS NOT NULL;

COMMENT ON INDEX idx_events_deleted_at IS 
    'Индекс для быстрого поиска удаленных событий в корзине и автоматической очистки старых записей';

-- Составной индекс для поиска событий пользователя по статусу и дате удаления
CREATE INDEX idx_events_user_status_deleted ON events(user_id, status, deleted_at) 
    WHERE status = 'DELETED';

COMMENT ON INDEX idx_events_user_status_deleted IS 
    'Индекс для быстрого получения корзины пользователя с сортировкой по дате удаления';

-- Составной индекс для поиска завершенных событий
CREATE INDEX idx_events_completed ON events(status, completed_at) 
    WHERE status = 'COMPLETED';

COMMENT ON INDEX idx_events_completed IS 
    'Индекс для быстрого поиска завершенных событий';

-- ----------------------------------------------------------------------------
-- Шаг 4: Добавление constraint для валидации временного интервала
-- ----------------------------------------------------------------------------

-- Проверка, что время окончания позже времени начала (если оба указаны)
ALTER TABLE events 
    ADD CONSTRAINT events_time_interval_check 
    CHECK (
        end_time IS NULL OR 
        event_time IS NULL OR 
        end_time > event_time
    );

COMMENT ON CONSTRAINT events_time_interval_check ON events IS 
    'Валидация временного интервала: время окончания должно быть позже времени начала';

-- ============================================================================
-- Конец миграции V3
-- ============================================================================
