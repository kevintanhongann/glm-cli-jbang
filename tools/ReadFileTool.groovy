package tools

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ReadFileTool implements Tool {
    @Override
    String getName() { "read_file" }

    @Override
    String getDescription() { "Read the content of a file at the specified path." }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                path: [type: "string", description: "The path to the file to read."]
            ],
            required: ["path"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        String pathStr = args.get("path")
        Path path = Paths.get(pathStr).normalize()
        if (!Files.exists(path)) {
            return "Error: File not found: ${pathStr}"
        }
        return Files.readString(path)
    }
}
