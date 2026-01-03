package tui

import com.googlecode.lanterna.*
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.KeyType
import core.ModelCatalog

/**
 * Dialog for selecting a model from the model catalog.
 * Mimics OpenCode's model selection with Favorites, Recent, and grouped providers.
 */
class ModelSelectionDialog {

    private MultiWindowTextGUI textGUI
    private BasicWindow dialogWindow
    private TextBox searchBox
    private ActionListBox modelListBox
    private List<ModelItem> allModels = []
    private List<ModelItem> filteredModels = []
    private Map<Integer, ModelItem> listboxIndexToModel = [:]
    private List<String> recentModels = []
    private List<String> favoriteModels = []
    private String selectedModel = null

    ModelSelectionDialog(MultiWindowTextGUI textGUI) {
        this.textGUI = textGUI
        this.recentModels = loadRecentModels()
        this.favoriteModels = loadFavoriteModels()
        this.allModels = buildModelList()
        this.filteredModels = allModels
    }

    static class ModelItem {

        String id
        String name
        String description
        String provider
        boolean isFree
        String category

        ModelItem(String id, String name, String description, String provider, boolean isFree, String category) {
            this.id = id
            this.name = name
            this.description = description
            this.provider = provider
            this.isFree = isFree
            this.category = category
        }

        String getDisplayString() {
            if (category == 'Favorites' || category == 'Recent') {
                return "${name}  [${provider}]"
            }
            return name
        }

    }

    String show() {
        dialogWindow = new BasicWindow('Select Model')
        dialogWindow.setHints(Arrays.asList(Window.Hint.CENTERED, Window.Hint.MODAL))

        Panel mainPanel = new Panel()
        mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))

        Panel searchPanel = new Panel()
        searchPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

        searchPanel.addComponent(new Label('Search: '))
        searchBox = new TextBox(new TerminalSize(40, 1), '', TextBox.Style.SINGLE_LINE)
        searchPanel.addComponent(searchBox)

        mainPanel.addComponent(searchPanel)
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE))

        modelListBox = new ActionListBox()
        updateModelList('')

        mainPanel.addComponent(modelListBox.withBorder(Borders.singleLine('Models')))
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE))

        Panel hintPanel = new Panel()
        hintPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
        hintPanel.addComponent(new Label('↑↓: Navigate  Enter: Select  Esc: Close'))
        mainPanel.addComponent(hintPanel)

        dialogWindow.setComponent(mainPanel)

        setupSearchHandler()

        // Pattern 2: Use addWindow + waitUntilClosed instead of addWindowAndWait
        textGUI.addWindow(dialogWindow)
        dialogWindow.waitUntilClosed()

        return selectedModel
    }

    private void setupSearchHandler() {
        searchBox.setInputFilter({ textBox, key ->
            KeyType keyType = key.getKeyType()

            if (keyType == KeyType.Escape) {
                close()
                return false
            }

            if (keyType == KeyType.Backspace || keyType == KeyType.Character) {
                String filter = searchBox.getText()
                updateModelList(filter)
            }

            return true
        })
    }

    private List<ModelItem> buildModelList() {
        List<ModelItem> items = []

        def allModelsMap = ModelCatalog.getAllModels()

        // Add favorites section
        favoriteModels.each { modelId ->
            def model = allModelsMap[modelId]
            if (model) {
                items << new ModelItem(
                    modelId,
                    model.name,
                    model.description,
                    model.provider,
                    isFreeModel(model),
                    'Favorites'
                )
            }
        }

        // Add recents section (excluding favorites)
        recentModels.each { modelId ->
            if (modelId in favoriteModels) return

            def model = allModelsMap[modelId]
            if (model) {
                items << new ModelItem(
                    modelId,
                    model.name,
                    model.description,
                    model.provider,
                    isFreeModel(model),
                    'Recent'
                )
            }
        }

        // Group by provider
        Map<String, List<ModelItem>> providerGroups = [:]
        allModelsMap.each { modelId, model ->
            if (modelId in favoriteModels || modelId in recentModels) return

            if (!providerGroups[model.provider]) {
                providerGroups[model.provider] = []
            }

            providerGroups[model.provider] << new ModelItem(
                modelId,
                model.name,
                model.description,
                model.provider,
                isFreeModel(model),
                model.provider
            )
        }

        // Sort by provider name and add
        def sortedProviders = providerGroups.keySet().sort()
        sortedProviders.each { providerId ->
            def providerModels = providerGroups[providerId]
            providerModels.sort { a, b -> a.name <=> b.name }
            items.addAll(providerModels)
        }

        return items
    }

    private boolean isFreeModel(model) {
        return model.cost?.input == 0 && model.cost?.output == 0
    }

    private void updateModelList(String filter) {
        modelListBox.clearItems()
        listboxIndexToModel.clear()

        if (filter.isEmpty()) {
            filteredModels = allModels
        } else {
            String lowerFilter = filter.toLowerCase()
            filteredModels = allModels.findAll { item ->
                item.name.toLowerCase().contains(lowerFilter) ||
                item.id.toLowerCase().contains(lowerFilter) ||
                item.provider.toLowerCase().contains(lowerFilter) ||
                (item.description && item.description.toLowerCase().contains(lowerFilter))
            }
        }

        if (filteredModels.isEmpty()) {
            modelListBox.addItem('No matching models found', { -> /* no action */ })
            return
        }

        String currentCategory = null
        int listboxIndex = 0

        filteredModels.each { item ->
            if (item.category != currentCategory) {
                if (currentCategory != null) {
                    modelListBox.addItem('', { -> /* separator, no action */ })
                    listboxIndex++
                }
                currentCategory = item.category
                modelListBox.addItem("── ${ currentCategory } ──", { -> /* header, no action */ })
                listboxIndex++
            }

            String label = item.getDisplayString()
            if (item.isFree) {
                label += ' (Free)'
            }
            // Capture item in closure for selection
            def currentItem = item
            modelListBox.addItem(label, { ->
                this.selectedModel = currentItem.id
                addToRecentModels(currentItem.id)
                close()
            })
            listboxIndexToModel[listboxIndex] = item
            listboxIndex++
        }
    }

    private List<String> loadRecentModels() {
        def config = core.Config.load()
        return config.behavior?.recentModels ?: []
    }

    private List<String> loadFavoriteModels() {
        def config = core.Config.load()
        return config.behavior?.favoriteModels ?: []
    }

    private void addToRecentModels(String modelId) {
        try {
            def tomlMapper = new com.fasterxml.jackson.dataformat.toml.TomlMapper()
            def configPath = java.nio.file.Paths.get(System.getProperty('user.home'), '.glm', 'config.toml')

            def config = null
            if (java.nio.file.Files.exists(configPath)) {
                config = tomlMapper.readValue(configPath.toFile(), core.Config.class)
            } else {
                config = new core.Config()
            }

            def recents = config.behavior?.recentModels ?: []

            recents.remove(modelId)
            recents.add(0, modelId)

            if (recents.size() > 10) {
                recents = recents.take(10)
            }

            config.behavior.recentModels = recents

            def parentDir = configPath.getParent()
            if (!java.nio.file.Files.exists(parentDir)) {
                java.nio.file.Files.createDirectories(parentDir)
            }

            tomlMapper.writer().writeValue(configPath.toFile(), config)
        } catch (Exception e) {
            System.err.println("Error saving config: ${e.message}")
        }
    }

    void close() {
        if (dialogWindow != null) {
            dialogWindow.close()
            dialogWindow = null
        }
    }

}
