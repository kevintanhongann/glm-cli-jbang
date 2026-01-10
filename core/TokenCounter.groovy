package core

import models.Message

class TokenCounter {
    private static final Map<String, Double> TOKEN_ESTIMATES = [
        "english_word": 0.75,
        "chinese_char": 0.5,
        "code_token": 0.75,
        "special_char": 0.25
    ]

    static int estimateMessageTokens(Message message) {
        return estimateTextTokens(message.content ?: "")
    }

    static int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0
        }
        double count = 0
        String[] words = text.split("\\s+")
        for (String word : words) {
            if (word.length() == 0) continue
            if (word ==~ /^[\u4e00-\u9fa5]+$/) {
                count += word.length() * TOKEN_ESTIMATES["chinese_char"]
            } else if (word ==~ /^[a-zA-Z]+$/) {
                count += TOKEN_ESTIMATES["english_word"]
            } else if (word ==~ /^[\{\}\[\]\(\)\.;,\"']+$/) {
                count += TOKEN_ESTIMATES["special_char"]
            } else {
                count += TOKEN_ESTIMATES["code_token"]
            }
        }
        return Math.ceil(count) as int
    }

    static int estimateToolCallTokens(String toolName, String arguments) {
        return Math.ceil((toolName.length() + arguments.length()) * 0.4) as int
    }

    static int estimateHistoryTokens(List<Message> history) {
        return history.sum { estimateMessageTokens(it) } ?: 0
    }

    static int estimateTotalContextTokens(List<Message> history, String systemPrompt) {
        int historyTokens = estimateHistoryTokens(history)
        int systemTokens = estimateTextTokens(systemPrompt)
        int overhead = 50  // Response formatting overhead
        return historyTokens + systemTokens + overhead
    }
}
