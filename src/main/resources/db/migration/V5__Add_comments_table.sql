-- ============================================================================
-- Миграция V5: Создание таблицы комментариев
-- ============================================================================
-- Описание: Создание таблицы comments для хранения комментариев членов семьи
--           к событиям с возможностью обсуждения деталей
-- Требования: 21.3
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Таблица comments: Хранит комментарии к событиям
-- ----------------------------------------------------------------------------
CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT comments_event_fk FOREIGN KEY (event_id) 
        REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT comments_user_fk FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT comments_text_not_empty CHECK (LENGTH(TRIM(text)) > 0)
);

-- Комментарии для таблицы comments
COMMENT ON TABLE comments IS 
    'Комментарии членов семьи к событиям для обсуждения деталей';

COMMENT ON COLUMN comments.id IS 
    'Уникальный идентификатор комментария';

COMMENT ON COLUMN comments.event_id IS 
    'Ссылка на событие, к которому относится комментарий';

COMMENT ON COLUMN comments.user_id IS 
    'Автор комментария';

COMMENT ON COLUMN comments.text IS 
    'Текст комментария';

COMMENT ON COLUMN comments.created_at IS 
    'Дата и время создания комментария';

-- ----------------------------------------------------------------------------
-- Индексы для таблицы comments
-- ----------------------------------------------------------------------------

-- Индекс для быстрого получения всех комментариев события
CREATE INDEX idx_comments_event_id ON comments(event_id);

COMMENT ON INDEX idx_comments_event_id IS 
    'Индекс для быстрого получения всех комментариев конкретного события';

-- Составной индекс для получения комментариев события с сортировкой по дате
CREATE INDEX idx_comments_event_created ON comments(event_id, created_at);

COMMENT ON INDEX idx_comments_event_created IS 
    'Индекс для получения комментариев события с сортировкой по дате создания';

-- Индекс для поиска комментариев по дате создания (для общей ленты активности)
CREATE INDEX idx_comments_created_at ON comments(created_at);

COMMENT ON INDEX idx_comments_created_at IS 
    'Индекс для поиска последних комментариев по всем событиям';

-- ============================================================================
-- Конец миграции V5
-- ============================================================================
