package commands

import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import core.Auth
import models.AuthCredential

@Command(name = "auth", description = "Manage authentication credentials", mixinStandardHelpOptions = true, subcommands = [AuthLoginCommand.class, AuthLogoutCommand.class, AuthListCommand.class])
class AuthCommand implements Runnable {

    @Override
    void run() {
        System.out.println("Use 'glm auth <subcommand>' to manage credentials")
        System.out.println("Subcommands: login, logout, list")
    }
}

@Command(name = "login", description = "Login to a provider", mixinStandardHelpOptions = true)
class AuthLoginCommand implements Runnable {

    @Parameters(index = "0", arity = "0..1", description = "Provider ID (default: zai)")
    String provider = "zai"

    private static final Map PROVIDER_CONFIG = [
        zai: [
            name: "Zai/Zhipu AI",
            description: "GLM-4 and Coding Plan API",
            url: "https://open.bigmodel.cn/usercenter/apikeys"
        ],
        zhipu: [
            name: "Zhipu AI",
            description: "GLM-4 API",
            url: "https://open.bigmodel.cn/usercenter/apikeys"
        ]
    ]

    @Override
    void run() {
        println()

        if (!PROVIDER_CONFIG.containsKey(provider)) {
            println("Error: Unknown provider '$provider'")
            println("\nSupported providers: ${PROVIDER_CONFIG.keySet().join(', ')}")
            return
        }

        def config = PROVIDER_CONFIG[provider]

        println("Add credential for: ${config.name}")
        println("${config.description}")
        println()
        println("Create an API key at: ${config.url}")
        println()

        def console = System.console()
        if (console == null) {
            println("Error: Cannot read password - please run in an interactive terminal")
            System.exit(1)
        }

        def key = console.readLine("Enter your API key: ")
        if (key == null) {
            println("\nCancelled")
            return
        }

        key = key.trim()

        if (key.isEmpty()) {
            println("Error: API key cannot be empty")
            System.exit(1)
        }

        def credential = new AuthCredential(
            type: "api",
            key: key,
            provider: provider,
            timestamp: System.currentTimeMillis()
        )

        Auth.set(provider, credential)

        println()
        println("✓ Login successful")
        println("  Credential stored for: ${config.name}")
    }
}

@Command(name = "logout", description = "Logout from a provider", mixinStandardHelpOptions = true)
class AuthLogoutCommand implements Runnable {

    @Parameters(index = "0", arity = "0..1", description = "Provider ID")
    String provider

    private static final Map PROVIDER_CONFIG = [
        zai: [
            name: "Zai/Zhipu AI",
            description: "GLM-4 and Coding Plan API",
            url: "https://open.bigmodel.cn/usercenter/apikeys"
        ],
        zhipu: [
            name: "Zhipu AI",
            description: "GLM-4 API",
            url: "https://open.bigmodel.cn/usercenter/apikeys"
        ]
    ]

    @Override
    void run() {
        def credentials = Auth.all()

        if (credentials.isEmpty()) {
            println("No credentials found")
            return
        }

        if (provider) {
            if (!credentials.containsKey(provider)) {
                println("Error: No credential found for provider '$provider'")
                println("\nCredentials:")
                credentials.each { k, v ->
                    def config = PROVIDER_CONFIG[k]
                    def name = config?.name ?: k
                    println("  - $name ($k)")
                }
                return
            }

            Auth.remove(provider)
            println("✓ Removed credential for: ${provider}")
            return
        }

        def console = System.console()
        println("Select provider to remove:")
        println()

        def providers = credentials.keySet() as List
        providers.eachWithIndex { prov, index ->
            def config = PROVIDER_CONFIG[prov]
            def name = config?.name ?: prov
            println("  ${index + 1}. $name ($prov)")
        }
        println()

        def selection = console.readLine("Enter number: ")
        if (selection == null) {
            println("\nCancelled")
            return
        }

        def index = selection.trim().toInteger() - 1
        if (index < 0 || index >= providers.size()) {
            println("Error: Invalid selection")
            return
        }

        def selectedProvider = providers[index]
        Auth.remove(selectedProvider)
        println("✓ Removed credential for: ${selectedProvider}")
    }
}

@Command(name = "list", description = "List all credentials", mixinStandardHelpOptions = true)
class AuthListCommand implements Runnable {

    private static final Map PROVIDER_CONFIG = [
        zai: [
            name: "Zai/Zhipu AI",
            description: "GLM-4 and Coding Plan API",
            url: "https://open.bigmodel.cn/usercenter/apikeys"
        ],
        zhipu: [
            name: "Zhipu AI",
            description: "GLM-4 API",
            url: "https://open.bigmodel.cn/usercenter/apikeys"
        ]
    ]

    @Override
    void run() {
        def credentials = Auth.all()

        if (credentials.isEmpty()) {
            println("No credentials configured")
            return
        }

        println("Credentials (${credentials.size}):")
        println()

        credentials.each { prov, data ->
            def config = PROVIDER_CONFIG[prov]
            def name = config?.name ?: prov
            def description = config?.description ?: ""
            def type = data.type ?: "api"
            def timestamp = data.timestamp ?: 0

            println("  Provider: ${name}")
            println("    ID: ${prov}")
            println("    Type: ${type}")
            if (description) {
                println("    Description: ${description}")
            }
            if (timestamp > 0) {
                def date = new Date(timestamp)
                println("    Added: ${date}")
            }
            println("    Key: ${data.key ? '***' + data.key[-4..-1] : '(none)'}")
            println()
        }

        def homeDir = System.getProperty("user.home")
        def authPath = new File(homeDir, ".glm/auth.json").absolutePath
        println("Credentials file: ${authPath}")
    }
}
