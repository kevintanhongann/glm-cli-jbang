package commands

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import core.ModelCatalog

@Command(name = "models", description = "List available models", mixinStandardHelpOptions = true)
class ModelsCommand implements Runnable {

    @Parameters(index = "0", arity = "0..1", description = "Provider ID to filter models by")
    String provider

    @Option(names = ["-v", "--verbose"], description = "Show detailed model information")
    boolean verbose = false

    @Option(names = ["-r", "--refresh"], description = "Refresh model catalog from remote API")
    boolean refresh = false

    @Override
    void run() {
        if (refresh) {
            ModelCatalog.refreshCache()
            println()
        }

        if (provider) {
            def providerInfo = ModelCatalog.getProvider(provider)
            if (!providerInfo) {
                println "Error: Provider '${provider}' not found"
                println "\nAvailable providers:"
                ModelCatalog.getProviders().each { id, info ->
                    println "  ${id} - ${info.name}"
                }
                return
            }

            println "Models for ${providerInfo.name}:"
            println()
            
            def models = ModelCatalog.getModelsForProvider(provider)
            if (models.isEmpty()) {
                println "  No models found for this provider"
                return
            }

            ModelCatalog.printModels(models, verbose)
        } else {
            println "Available models:"
            println()
            
            def allModels = ModelCatalog.getAllModels()
            if (allModels.isEmpty()) {
                println "  No models available"
                return
            }

            def sortedModels = allModels.values().sort { a, b ->
                def providerCompare = a.provider <=> b.provider
                if (providerCompare != 0) {
                    return providerCompare
                }
                return a.name <=> b.name
            }

            ModelCatalog.printModels(sortedModels, verbose)
        }
    }
}
