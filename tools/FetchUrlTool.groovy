package tools

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.loader.UrlDocumentLoader
import dev.langchain4j.data.document.parser.TextDocumentParser

class FetchUrlTool implements Tool {
    @Override
    String getName() { "fetch_url" }

    @Override
    String getDescription() { "Fetch and read content from a specific URL. Useful for reading documentation, articles, or web pages at a known address." }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                url: [
                    type: "string",
                    description: "The URL to fetch content from."
                ]
            ],
            required: ["url"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        try {
            String url = args.get("url")
            if (!url) {
                return "Error: url is required"
            }

            Document document = UrlDocumentLoader.load(url, new TextDocumentParser())
            
            StringBuilder sb = new StringBuilder()
            sb.append("Successfully fetched content from: ${url}\n")
            sb.append("=" * 80).append("\n\n")
            
            String text = document.text()
            
            if (text.length() > 10000) {
                sb.append("Note: Content truncated to 10000 characters\n\n")
                sb.append(text.substring(0, 10000))
            } else {
                sb.append(text)
            }
            
            return sb.toString()
        } catch (IllegalArgumentException e) {
            return "Error: Invalid URL - ${e.message}"
        } catch (Exception e) {
            return "Error fetching URL: ${e.message}"
        }
    }
}
