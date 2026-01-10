package core

class CompactionTrigger {

    private static final double DEFAULT_WARNING_THRESHOLD = 0.75
    private static final double DEFAULT_CRITICAL_THRESHOLD = 0.90

    double warningThreshold
    double criticalThreshold
    int maxContextTokens

    CompactionTrigger(int maxContextTokens = 8000) {
        this.maxContextTokens = maxContextTokens
        this.warningThreshold = DEFAULT_WARNING_THRESHOLD
        this.criticalThreshold = DEFAULT_CRITICAL_THRESHOLD
    }

    enum TriggerLevel {

        NONE,
        WARNING,
        CRITICAL

    }

    TriggerLevel checkLevel(int currentTokens) {
        double ratio = (double) currentTokens / maxContextTokens
        if (ratio >= criticalThreshold) {
            return TriggerLevel.CRITICAL
        } else if (ratio >= warningThreshold) {
            return TriggerLevel.WARNING
        }
        return TriggerLevel.NONE
    }

    boolean shouldCompact(int currentTokens) {
        return checkLevel(currentTokens) != TriggerLevel.NONE
    }

    String getWarningMessage(int currentTokens) {
        def level = checkLevel(currentTokens)
        return switch (level) {
            case TriggerLevel.CRITICAL ->
                "Context at ${(currentTokens * 100 / maxContextTokens)}% - COMPACTING NOW"
            case TriggerLevel.WARNING ->
                "Context at ${(currentTokens * 100 / maxContextTokens)}% - Consider compacting"
            default -> ''
        }
    }

}
