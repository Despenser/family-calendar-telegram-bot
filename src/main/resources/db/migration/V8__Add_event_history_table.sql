-- ============================================================================
-- Миграция V8: Создание таблицы истории изменений событий
-- ============================================================================
-- Описание: Создание ENUM action_type и таблицы event_history для
--           отслеживания всех изменений событий с указанием автора и времени
-- Требования: 29.1, 29.2
-- ============================================================================

-- ----------------------------------------------------------------------------
-- ENUM action_type: Типы действий с событиями
-- ----------------------------------------------------------------------------
CREATE TYPE action_type AS ENUM ('created', 'updated', 'deleted', 'restored');

COMMENT ON TYPE action_type IS 
    'Тип действия с событием: created (создано), updated (обновлено), deleted (удалено), restored (восстановлено)';

-- ----------------------------------------------------------------------------
-- Таблица event_history: Хранит историю изменений событий
-- ----------------------------------------------------------------------------
CREATE TABLE event_history (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    action_type action_type NOT NULL,
    field_name VARCHAR(100),
    old_value TEXT,
    new_value TEXT,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT event_history_user_fk FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT event_history_field_logic CHECK (
        (action_type IN ('created', 'deleted', 'restored') AND field_name IS NULL) OR
        (action_type = 'updated' AND field_name IS NOT NULL)
    )
);

-- Комментарии для таблицы event_history
COMMENT ON TABLE event_history IS 
    'История изменений событий для отслеживания всех действий пользователей';

COMMENT ON COLUMN event_history.id IS 
    'Уникальный идентификатор записи истории';

COMMENT ON COLUMN event_history.event_id IS 
    'Идентификатор события (может быть удалено, поэтому без FK)';

COMMENT ON COLUMN event_history.user_id IS 
    'Пользователь, выполнивший действие';

COMMENT ON COLUMN event_history.action_type IS 
    'Тип действия: created, updated, deleted, restored';

COMMENT ON COLUMN event_history.field_name IS 
    'Название измененного поля (только для action_type = updated)';

COMMENT ON COLUMN event_history.old_value IS 
    'Старое значение поля (для updated)';

COMMENT ON COLUMN event_history.new_value IS 
    'Новое значение поля (для updated)';

COMMENT ON COLUMN event_history.changed_at IS 
    'Дата и время выполнения действия';

-- ----------------------------------------------------------------------------
-- Индексы для таблицы event_history
-- ----------------------------------------------------------------------------

-- Индекс для быстрого получения истории конкретного события
CREATE INDEX idx_event_history_event_id ON event_history(event_id);

COMMENT ON INDEX idx_event_history_event_id IS 
    'Индекс для быстрого получения всей истории изменений конкретного события';

-- Составной индекс для получения истории события с сортировкой по дате
CREATE INDEX idx_event_history_event_changed ON event_history(event_id, changed_at DESC);

COMMENT ON INDEX idx_event_history_event_changed IS 
    'Индекс для получения истории события с сортировкой по дате (новые первыми)';

-- Индекс для поиска действий по дате (для общего аудита)
CREATE INDEX idx_event_history_changed_at ON event_history(changed_at DESC);

COMMENT ON INDEX idx_event_history_changed_at IS 
    'Индекс для поиска последних действий по всем событиям (аудит системы)';

-- Составной индекс для фильтрации по типу действия и дате
CREATE INDEX idx_event_history_action_changed ON event_history(action_type, changed_at DESC);

COMMENT ON INDEX idx_event_history_action_changed IS 
    'Индекс для фильтрации действий по типу с сортировкой по дате';

-- ============================================================================
-- Конец миграции V8
-- ============================================================================
