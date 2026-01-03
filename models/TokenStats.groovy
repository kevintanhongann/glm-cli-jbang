package models

import groovy.transform.Canonical

@Canonical
class TokenStats {
    String sessionId
    int totalTokens = 0
    int inputTokens = 0
    int outputTokens = 0
    BigDecimal totalCost = 0.0000
    Date lastCompaction

    int getOutputTokens() {
        return totalTokens - inputTokens
    }
}
