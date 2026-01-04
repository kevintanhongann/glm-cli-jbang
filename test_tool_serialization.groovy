import com.fasterxml.jackson.databind.ObjectMapper
import tools.ReadFileTool

// Create ObjectMapper
def mapper = new ObjectMapper()

// Get parameters from ReadFileTool
def tool = new ReadFileTool()
def params = tool.getParameters()

println "Parameters object:"
println params
println "\nType of limit description:"
println params.properties.limit.description.getClass()
println "\nValue:"
println params.properties.limit.description

println "\n--- Serialized JSON ---"
def json = mapper.writeValueAsString(params)
println json

// Check if it's a GString issue
println "\n--- Is GString? ---"
println params.properties.limit.description instanceof groovy.lang.GString
