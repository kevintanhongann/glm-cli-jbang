package core

import models.Message as ModelMessage
import groovy.sql.Sql

@Singleton(strict=false)
class MessageStore {
    private Sql getDb() {
        def sm = SessionManager.instance
        if (!sm) {
            throw new IllegalStateException("SessionManager not initialized")
        }
        return sm.database
    }

    String saveMessage(String sessionId, ModelMessage message) {
        def messageId = generateMessageId()

        getDb().execute("""
            INSERT INTO messages
            (id, session_id, role, content, created_at, parent_id, tokens_input, tokens_output, finish_reason, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                messageId,
                sessionId,
                message.role,
                message.content,
                new Date(),
                null,
                null,
                null,
                null,
                null
            ]
        )

        return messageId
    }

    String saveMessage(String sessionId, ModelMessage message, int inputTokens, int outputTokens, String finishReason = null) {
        def messageId = generateMessageId()

        getDb().execute("""
            INSERT INTO messages
            (id, session_id, role, content, created_at, parent_id, tokens_input, tokens_output, finish_reason, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                messageId,
                sessionId,
                message.role,
                message.content,
                new Date(),
                null,
                inputTokens,
                outputTokens,
                finishReason,
                null
            ]
        )

        return messageId
    }

    List<ModelMessage> getMessages(String sessionId, int limit = 0) {
        String sql = "SELECT * FROM messages WHERE session_id = ? ORDER BY created_at ASC"
        def params = [sessionId]

        if (limit > 0) {
            sql += " LIMIT ?"
            params << limit
        }

        def rows = getDb().rows(sql, params)

        return rows.collect { row ->
            def msg = new ModelMessage(row.role, row.content)
            return msg
        }
    }

    ModelMessage getMessage(String messageId) {
        def row = getDb().firstRow(
            "SELECT * FROM messages WHERE id = ?",
            [messageId]
        )

        if (!row) return null

        return new ModelMessage(row.role, row.content)
    }

    void deleteMessage(String messageId) {
        getDb().execute("DELETE FROM messages WHERE id = ?", [messageId])
    }

    void deleteMessagesForSession(String sessionId) {
        getDb().execute("DELETE FROM messages WHERE session_id = ?", [sessionId])
    }

    int getMessageCount(String sessionId) {
        def row = getDb().firstRow(
            "SELECT COUNT(*) as count FROM messages WHERE session_id = ?",
            [sessionId]
        )
        return row.count
    }

    private String generateMessageId() {
        return "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
    }
}
