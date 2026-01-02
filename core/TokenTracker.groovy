package core

import models.TokenStats
import groovy.sql.Sql

@Singleton(strict=false)
class TokenTracker {
    private Sql database

    TokenTracker() {
        this.database = SessionManager.instance.getDatabase()
    }

    void recordTokens(String sessionId, int inputTokens, int outputTokens, BigDecimal cost = 0.0000) {
        def totalTokens = inputTokens + outputTokens

        def existing = database.firstRow(
            "SELECT * FROM token_stats WHERE session_id = ?",
            [sessionId]
        )

        if (existing) {
            database.execute("""
                UPDATE token_stats
                SET total_tokens = total_tokens + ?,
                    total_cost = total_cost + ?
                WHERE session_id = ?
                """,
                [totalTokens, cost, sessionId]
            )
        } else {
            database.execute("""
                INSERT INTO token_stats (session_id, total_tokens, total_cost)
                VALUES (?, ?, ?)
                """,
                [sessionId, totalTokens, cost]
            )
        }
    }

    TokenStats getTokenStats(String sessionId) {
        def row = database.firstRow(
            "SELECT * FROM token_stats WHERE session_id = ?",
            [sessionId]
        )

        if (!row) return null

        return new TokenStats(
            sessionId: row.session_id,
            totalTokens: row.total_tokens,
            totalCost: row.total_cost,
            lastCompaction: row.last_compaction
        )
    }

    void updateCompactionTime(String sessionId) {
        database.execute(
            "UPDATE token_stats SET last_compaction = ? WHERE session_id = ?",
            [new Date(), sessionId]
        )
    }

    List<TokenStats> getAllTokenStats() {
        def rows = database.rows("SELECT * FROM token_stats ORDER BY total_tokens DESC")

        return rows.collect { row ->
            new TokenStats(
                sessionId: row.session_id,
                totalTokens: row.total_tokens,
                totalCost: row.total_cost,
                lastCompaction: row.last_compaction
            )
        }
    }
}
