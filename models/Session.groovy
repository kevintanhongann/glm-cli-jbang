package models

import groovy.transform.Canonical

@Canonical
class Session {
    String id
    String projectHash
    String directory
    String title
    String agentType
    String model
    Date createdAt
    Date updatedAt
    boolean isArchived = false
    String metadata
}
