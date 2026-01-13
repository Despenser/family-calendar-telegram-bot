-- ============================================================================
-- Миграция V6: Создание таблицы пунктов чек-листа
-- ============================================================================
-- Описание: Создание таблицы checklist_items для хранения пунктов чек-листов
--           внутри событий с возможностью отметки выполнения
-- Требования: 22.3
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Таблица checklist_items: Хранит пункты чек-листов событий
-- ----------------------------------------------------------------------------
CREATE TABLE checklist_items (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    text VARCHAR(500) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    position INTEGER NOT NULL,
    completed_at TIMESTAMP,
    completed_by BIGINT,
    
    -- Constraints
    CONSTRAINT checklist_items_event_fk FOREIGN KEY (event_id) 
        REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT checklist_items_completed_by_fk FOREIGN KEY (completed_by) 
        REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT checklist_items_text_not_empty CHECK (LENGTH(TRIM(text)) > 0),
    CONSTRAINT checklist_items_position_positive CHECK (position >= 0),
    CONSTRAINT checklist_items_completed_logic CHECK (
        (completed = FALSE AND completed_at IS NULL AND completed_by IS NULL) OR
        (completed = TRUE AND completed_at IS NOT NULL AND completed_by IS NOT NULL)
    )
);

-- Комментарии для таблицы checklist_items
COMMENT ON TABLE checklist_items IS 
    'Пункты чек-листов событий для отслеживания выполнения задач';

COMMENT ON COLUMN checklist_items.id IS 
    'Уникальный идентификатор пункта чек-листа';

COMMENT ON COLUMN checklist_items.event_id IS 
    'Ссылка на событие, к которому относится чек-лист';

COMMENT ON COLUMN checklist_items.text IS 
    'Текст пункта чек-листа';

COMMENT ON COLUMN checklist_items.completed IS 
    'Флаг выполнения пункта: true - выполнен, false - не выполнен';

COMMENT ON COLUMN checklist_items.position IS 
    'Порядковый номер пункта в чек-листе (для сортировки)';

COMMENT ON COLUMN checklist_items.completed_at IS 
    'Дата и время отметки пункта как выполненного';

COMMENT ON COLUMN checklist_items.completed_by IS 
    'Пользователь, отметивший пункт как выполненный';

-- ----------------------------------------------------------------------------
-- Индексы для таблицы checklist_items
-- ----------------------------------------------------------------------------

-- Индекс для быстрого получения всех пунктов чек-листа события
CREATE INDEX idx_checklist_event_id ON checklist_items(event_id);

COMMENT ON INDEX idx_checklist_event_id IS 
    'Индекс для быстрого получения всех пунктов чек-листа конкретного события';

-- Составной индекс для получения пунктов с сортировкой по позиции
CREATE INDEX idx_checklist_event_position ON checklist_items(event_id, position);

COMMENT ON INDEX idx_checklist_event_position IS 
    'Индекс для получения пунктов чек-листа с сортировкой по порядковому номеру';

-- Составной индекс для фильтрации выполненных/невыполненных пунктов
CREATE INDEX idx_checklist_event_completed ON checklist_items(event_id, completed);

COMMENT ON INDEX idx_checklist_event_completed IS 
    'Индекс для быстрой фильтрации выполненных и невыполненных пунктов чек-листа';

-- ============================================================================
-- Конец миграции V6
-- ============================================================================
