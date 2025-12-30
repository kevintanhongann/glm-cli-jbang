package tools

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ListFilesTool implements Tool {
    @Override
    String getName() { "list_files" }

    @Override
    String getDescription() { "List files in a directory." }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                path: [type: "string", description: "The directory path (default: .)."],
                recursive: [type: "boolean", description: "List files recursively."]
            ],
            required: ["path"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        String pathStr = args.get("path") ?: "."
        Path dir = Paths.get(pathStr).normalize()
        
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return "Error: Directory not found: ${pathStr}"
        }

        try {
            def files = []
            Files.walk(dir, 1).forEach { p ->
                files.add(p.toString())
            }
            return files.join("\n")
        } catch (Exception e) {
            return "Error listing files: ${e.message}"
        }
    }
}
