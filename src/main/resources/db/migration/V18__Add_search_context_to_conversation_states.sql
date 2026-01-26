-- Добавление полей для хранения контекста поиска событий
-- Используется для сохранения информации о сообщении поиска для последующего редактирования

ALTER TABLE conversation_states
ADD COLUMN search_chat_id BIGINT,
ADD COLUMN search_message_id INTEGER;

-- Комментарии к новым полям
COMMENT ON COLUMN conversation_states.search_chat_id IS 'ID чата для поиска событий';
COMMENT ON COLUMN conversation_states.search_message_id IS 'ID сообщения для редактирования при поиске';
