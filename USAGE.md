# GLM-CLI Usage Guide

This guide provides practical examples and workflows for using GLM-CLI in your daily development work.

## Quick Start

### First-Time Setup

```bash
# 1. Install JBang (if not already installed)
curl -Ls https://sh.jbang.dev | bash -s - app setup

# 2. Download GLM-CLI
curl -O https://raw.githubusercontent.com/yourusername/glm-cli-jbang/main/glm.groovy
chmod +x glm.groovy

# 3. Set your API key
export ZAI_API_KEY=your.api.key.here

# 4. Test the installation
./glm.groovy chat "Hello, GLM-CLI!"
```

### Install Globally (Recommended)

```bash
# Install as a system-wide command
jbang app install --name glm glm.groovy

# Now use from anywhere
glm chat "How do I create a REST API in Java?"
glm agent "Add unit tests to User.groovy"
```

## Core Usage Patterns

### 1. Quick Q&A with Chat

Use `glm chat` for quick questions and explanations:

```bash
# Simple question
glm chat "What's the difference between == and .equals() in Java?"

# Code explanation
glm chat "Explain this code: List<String> list = new ArrayList<>();"

# Ask for best practices
glm chat "Best practices for handling exceptions in Java?"

# Request code examples
glm chat "Show me how to read a file in Java"
```

### 2. Interactive Chat Sessions

Start an interactive session for back-and-forth conversation:

```bash
# Start interactive mode
glm chat

# Then type your questions interactively:
# You: How do I parse JSON in Java?
# GLM: [provides solution]
# You: What about streaming large JSON files?
# GLM: [builds on previous answer]

# Exit with:
exit
```

### 3. Autonomous Agent for File Operations

Use `glm agent` for multi-step tasks involving files:

```bash
# Create a new file
glm agent "Create a Person.groovy class with name and age fields"

# Refactor existing code
glm agent "Extract validation logic from UserService.groovy into a Validator class"

# Add tests
glm agent "Write unit tests for the PaymentProcessor class"

# Fix bugs
glm agent "Investigate and fix the null pointer exception in AuthController"
```

## Common Workflows

### Code Review Workflow

```bash
# Review a specific file
glm agent "Review User.groovy for security vulnerabilities and suggest improvements"

# Review an entire directory
glm agent "Review all Groovy files in the src/ directory and summarize issues"

# Get detailed feedback
glm chat --model glm-4-plus "Analyze this code for performance issues: [paste code]"
```

### Test Generation Workflow

```bash
# Generate tests for a single file
glm agent "Create comprehensive unit tests for Calculator.groovy"

# Generate tests with specific framework
glm agent "Write JUnit 5 tests for the UserService class"

# Generate integration tests
glm agent "Create integration tests for the REST API endpoints"
```

### Refactoring Workflow

```bash
# Extract a class
glm agent "Extract the authentication logic into a new AuthService class"

# Apply design patterns
glm agent "Refactor this code to use the Factory pattern"

# Improve code quality
glm agent "Refactor User.groovy to follow SOLID principles"

# Simplify complex logic
glm agent "Simplify the nested if statements in ProcessOrder.groovy"
```

### Documentation Workflow

```bash
# Add JavaDoc
glm agent "Add JavaDoc comments to all public methods in API.groovy"

# Generate README
glm agent "Create a README.md file explaining how to use this project"

# Document API
glm agent "Document the REST API endpoints with examples"

# Add inline comments
glm agent "Add explanatory comments to complex algorithms in Algorithm.groovy"
```

### Bug Fixing Workflow

```bash
# Investigate a bug
glm agent "Investigate why the application crashes when null user is passed"

# Fix identified issues
glm agent "Fix the null pointer exception in UserService.createUser()"

# Add error handling
glm agent "Add proper exception handling to the FileProcessor class"

# Add defensive checks
glm agent "Add null checks to all public methods in Validator class"
```

## Advanced Usage

### Using Different Models

```bash
# Fast, cost-effective (default)
glm chat --model glm-4-flash "Quick question"

# Balanced quality/speed
glm chat --model glm-4 "Code review task"

# Higher quality reasoning
glm chat --model glm-4-plus "Complex architectural question"

# Latest with multi-function calls
glm agent --model glm-4.5 "Multi-step refactoring task"

# Vision capabilities
glm chat --model glm-4v "Analyze this image" [image support planned]
```

### Provider Selection

```bash
# Default custom GLM provider
glm chat "Using default provider"

# Use LangChain4j provider
glm chat --provider langchain4j "Using LangChain4j"

# Use ZAI Coding Plan (extended context, thinking mode)
glm agent --provider zai-coding-plan "Complex coding task requiring deep reasoning"
```

### Configuration-Based Workflow

Set up config for your specific workflow:

```toml
# ~/.glm/config.toml

[api]
key = "your.api.key.here"

# For quick prototyping
[behavior]
default_model = "glm-4-flash"
safety_mode = "always_allow"

[agent]
temperature = 0.9

[chat]
system_prompt = "You are a senior software engineer helping with rapid prototyping."
```

## Practical Examples

### Example 1: Building a REST API

```bash
# Step 1: Create the model
glm agent "Create a User model with id, name, email fields in Groovy"

# Step 2: Create the controller
glm agent "Create a REST controller for User CRUD operations using Micronaut"

# Step 3: Add validation
glm agent "Add validation to User model (email format, name length)"

# Step 4: Generate tests
glm agent "Write unit tests for the UserController"

# Step 5: Document API
glm agent "Create API documentation with examples for all endpoints"
```

### Example 2: Migrating Code

```bash
# Migrate from one framework to another
glm agent "Migrate this Spring Boot controller to Micronaut"

# Update to new language version
glm agent "Update this Java 8 code to use Java 17 features"

# Change database access pattern
glm agent "Refactor JDBC code to use JPA/Hibernate"
```

### Example 3: Performance Optimization

```bash
# Analyze performance bottlenecks
glm agent "Analyze DataProcessor.groovy for performance issues"

# Implement caching
glm agent "Add caching to the frequently accessed data in UserService"

# Optimize queries
glm agent "Optimize the database queries in Repository classes"

# Implement lazy loading
glm agent "Refactor to use lazy loading for large data sets"
```

### Example 4: Security Hardening

```bash
# Add input validation
glm agent "Add input validation to all REST API endpoints"

# Sanitize outputs
glm agent "Implement XSS protection in the web layer"

# Add authentication
glm agent "Add JWT-based authentication to the API"

# Audit sensitive operations
glm agent "Add audit logging for all user data access"
```

## Tips and Best Practices

### 1. Be Specific and Clear

**Bad:**
```bash
glm agent "Fix the code"
```

**Good:**
```bash
glm agent "Fix the null pointer exception in UserService.createUser() when the email parameter is null"
```

### 2. Provide Context

**Bad:**
```bash
glm agent "Make it faster"
```

**Good:**
```bash
glm agent "Optimize the DataProcessor.processBatch() method which currently takes 10 seconds to process 1000 records"
```

### 3. Use the Right Model

- **glm-4-flash**: Simple questions, quick iterations
- **glm-4**: Code reviews, refactoring, documentation
- **glm-4-plus**: Architecture decisions, complex problems
- **glm-4.5**: Multi-step tasks, reasoning-heavy work

### 4. Review Diff Before Applying

Always review the diff preview when using the agent:

```bash
# The agent will show:
# --- File: User.groovy (original)
# +++ File: User.groovy (modified)
# - public String name;
# + private String name;
# + public String getName() { return name; }
# + public void setName(String name) { this.name = name; }
#
# Apply changes? (Y/n)

# Review carefully before typing 'Y'
```

### 5. Break Down Complex Tasks

```bash
# Instead of one huge request:
glm agent "Build a complete e-commerce system"

# Break it down:
glm agent "Create Product model"
glm agent "Create ShoppingCart class"
glm agent "Implement checkout logic"
glm agent "Add payment processing"
glm agent "Write tests for all components"
```

### 6. Use Interactive Mode for Exploration

```bash
# Start interactive mode
glm chat

# Explore ideas:
# You: How would you implement a caching layer?
# GLM: [explains options]
# You: What about using Redis?
# GLM: [discusses Redis implementation]
# You: Show me code examples
# GLM: [provides code]
```

### 7. Leverage Streaming for Long Responses

```bash
# Streaming is enabled by default for better experience
glm chat "Explain microservices architecture in detail"

# You'll see the response flow in real-time
```

## Common Use Cases

### For Backend Developers

```bash
# Create APIs
glm agent "Create REST endpoints for user management"

# Write database migrations
glm agent "Create Flyway migration for adding profile table"

# Implement business logic
glm agent "Implement order processing logic with validation"

# Generate DTOs
glm agent "Create DTO classes for API requests/responses"
```

### For Frontend Developers

```bash
# Generate component templates
glm agent "Create React component for user profile"

# Write API client code
glm agent "Create Axios-based API client for backend endpoints"

# Implement state management
glm agent "Add Redux slice for authentication state"

# Create test utils
glm agent "Create test utilities for React components"
```

### For DevOps Engineers

```bash
# Generate Dockerfile
glm agent "Create a Dockerfile for a Spring Boot application"

# Write CI/CD pipelines
glm agent "Create a GitHub Actions workflow for testing and deployment"

# Generate Kubernetes manifests
glm agent "Create Kubernetes deployment manifests for the application"

# Write deployment scripts
glm agent "Create a bash script for deploying the application"
```

### For Students/Learners

```bash
# Learn concepts
glm chat "Explain polymorphism with examples"

# Get homework help
glm agent "Help me understand how to implement a binary search tree"

# Practice coding
glm agent "Give me exercises to practice Java collections"

# Debug assignments
glm chat "Why is this code throwing an IndexOutOfBoundsException?"
```

### For Technical Writers

```bash
# Generate documentation
glm agent "Create API documentation for the User endpoints"

# Write tutorials
glm agent "Write a step-by-step tutorial for using this library"

# Create examples
glm agent "Add usage examples to the README.md"

# Translate docs
glm agent "Translate the documentation from English to Chinese"
```

## Productivity Boosters

### Alias Common Commands

```bash
# Add to ~/.bashrc or ~/.zshrc

# Quick chat
alias glc='glm chat'

# Quick agent
alias gla='glm agent'

# Code review
alias glr='glm agent "Review the current file"'

# Generate tests
alias glt='glm agent "Write unit tests for the current file"'

# Now use:
glc "Quick question"
gla "Fix this bug"
glr
glt
```

### Integrate with Git Hooks

```bash
# Pre-commit code review (add to .git/hooks/pre-commit)
#!/bin/bash
echo "Running GLM code review..."
glm agent "Review the staged files for issues"

# Pre-push check (add to .git/hooks/pre-push)
#!/bin/bash
echo "Checking for common issues..."
glm chat "Check for potential security issues in the codebase"
```

### Use with Project Templates

```bash
# Initialize new project
glm agent "Create a basic Spring Boot project structure with config, controller, service layers"

# Add standard files
glm agent "Create .gitignore, README.md, and LICENSE files for this project"

# Setup testing framework
glm agent "Set up JUnit 5 testing structure with test utilities"
```

## Troubleshooting Common Issues

### Agent Not Responding

```bash
# Check API key
echo $ZAI_API_KEY

# Try a simple chat to verify connection
glm chat "Test"

# Check internet connection
curl https://open.bigmodel.cn
```

### Diff Shows No Changes

```bash
# The file content might not have changed
# Or the agent is proposing the same content
# Review the agent's message for context

# Try being more specific:
glm agent "Refactor User.groovy to use builder pattern"
```

### Too Many Tool Calls

```bash
# The agent might be stuck in a loop
# Interrupt with Ctrl+C

# Be more specific in your request:
glm agent "Create only the User class, don't add tests yet"
```

### Permission Denied

```bash
# Check file permissions
ls -la

# Make files writable
chmod +w filename.groovy

# Or use sudo with caution (not recommended for development)
```

## Integration with Other Tools

### With Git

```bash
# Get AI help for git operations
glm chat "How do I squash the last 3 commits?"

# Analyze git history
glm chat "What does this git diff show?" [paste diff]

# Generate commit messages
glm chat "Write a commit message for these changes: [paste changes]"
```

### With IDEs

**VSCode (via tasks)**:
```json
// .vscode/tasks.json
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "GLM Review Current File",
      "type": "shell",
      "command": "glm",
      "args": ["agent", "Review ${file}"]
    }
  ]
}
```

**Vim/Neovim**:
```vim
" .vimrc
command! GLMReview execute "!glm agent \"Review the current file\""
command! -nargs=1 GLMChat execute "!glm chat <args>"
```

### With Build Tools

```bash
# Generate build configuration
glm agent "Create a Maven pom.xml for this project"

# Generate Gradle build file
glm agent "Create a Gradle build.gradle file with dependencies"

# Update dependencies
glm agent "Update the Maven dependencies to their latest versions"
```

## Learning Resources

### Progressive Learning Path

**Day 1: Basics**
```bash
glm chat "What is GLM-CLI?"
glm chat "Explain the difference between chat and agent commands"
glm agent "Create a simple Hello World script"
```

**Day 2: File Operations**
```bash
glm agent "Read and summarize README.md"
glm agent "Add a comment to User.groovy"
glm agent "Create a new test file"
```

**Day 3: Code Modification**
```bash
glm agent "Refactor User.groovy to use builder pattern"
glm agent "Add input validation to all controllers"
glm agent "Extract a utility class"
```

**Day 4: Complex Tasks**
```bash
glm agent "Add unit tests for all classes in src/"
glm agent "Generate API documentation"
glm agent "Migrate from Java 8 to Java 17 features"
```

**Day 5: Advanced Usage**
```bash
glm agent "Implement caching layer"
glm agent "Add authentication and authorization"
glm agent "Create CI/CD pipeline configuration"
```

## Performance Tips

### Reduce Token Usage

```bash
# Be specific (fewer tokens)
glm agent "Fix the NPE in UserService.createUser() line 45"

# Instead of:
glm agent "Find and fix all bugs in the UserService class"

# Use appropriate models
glm chat --model glm-4-flash "Quick syntax question"

# For complex tasks only:
glm chat --model glm-4-plus "Complex architectural review"
```

### Improve Response Quality

```bash
# Provide context
glm agent "In the context of a Spring Boot application, create a REST controller..."

# Specify requirements
glm agent "Create a User class with: name (String, required), age (int, >= 18), email (valid format)"

# Ask for explanations
glm chat "Explain why this implementation uses the Factory pattern"
```

### Handle Large Files

```bash
# Process in chunks
glm agent "Read the first 100 lines of large-file.groovy and summarize"

# Work with specific sections
glm agent "Refactor the processOrder method in OrderService.groovy"

# Use plan mode for analysis (no writes)
glm chat "Analyze the structure of this large codebase"
```

## Community and Support

### Getting Help

```bash
# Check help
glm --help
glm chat --help
glm agent --help

# View version
glm --version

# Test installation
glm chat "Installation test successful!"
```

### Reporting Issues

If you encounter issues:
1. Check the [FAQ.md](FAQ.md) for common problems
2. Review the [troubleshooting](#troubleshooting-common-issues) section
3. Search [GitHub Issues](https://github.com/yourusername/glm-cli-jbang/issues)
4. Create a new issue with details

### Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on:
- Code contributions
- Documentation improvements
- Bug reports
- Feature requests

## Additional Resources

- [README.md](README.md) - Main project documentation
- [CONFIGURATION.md](CONFIGURATION.md) - Detailed configuration options
- [FAQ.md](FAQ.md) - Frequently asked questions
- [AGENTS.md](AGENTS.md) - Agent system deep dive
- [TOOLS.md](TOOLS.md) - Complete tool reference
- [DEVELOPMENT.md](DEVELOPMENT.md) - Development guide

---

**Happy coding with GLM-CLI!** 🚀

For updates and discussions:
- [GitHub Repository](https://github.com/yourusername/glm-cli-jbang)
- [GitHub Discussions](https://github.com/yourusername/glm-cli-jbang/discussions)
- [GitHub Issues](https://github.com/yourusername/glm-cli-jbang/issues)
