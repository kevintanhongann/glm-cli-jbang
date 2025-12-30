package tools

interface Tool {
    String getName()
    String getDescription()
    Map<String, Object> getParameters() // JSON Schema for parameters
    Object execute(Map<String, Object> args)
}
