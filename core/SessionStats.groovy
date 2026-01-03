package core

class SessionStat {
    String sessionId
    int totalTokens = 0
    BigDecimal totalCost = 0.0000
    int inputTokens = 0
    int outputTokens = 0
    List<LspServerInfo> lspServers = []
    List<ModifiedFile> modifiedFiles = []
    Date lastUpdated = new Date()

    void addLspServer(String lspId, String status, String error = null, String root = null) {
        def existing = lspServers.find { it.lspId == lspId }
        if (existing) {
            existing.status = status
            existing.error = error
            existing.root = root
        } else {
            lspServers << new LspServerInfo(lspId: lspId, status: status, error: error, root: root)
        }
    }

    void addModifiedFile(String filePath, int additions = 0, int deletions = 0) {
        def existing = modifiedFiles.find { it.filePath == filePath }
        if (existing) {
            existing.additions = additions
            existing.deletions = deletions
        } else {
            modifiedFiles << new ModifiedFile(filePath: filePath, additions: additions, deletions: deletions)
        }
    }

    void updateTokens(int inputTokens, int outputTokens, BigDecimal cost = 0.0000) {
        this.inputTokens += inputTokens
        this.outputTokens += outputTokens
        this.totalTokens += (inputTokens + outputTokens)
        this.totalCost += cost
        this.lastUpdated = new Date()
    }
}

class LspServerInfo {
    String lspId
    String status
    String error
    String root
    Date lastUpdated = new Date()
}

class ModifiedFile {
    String filePath
    int additions = 0
    int deletions = 0
}
