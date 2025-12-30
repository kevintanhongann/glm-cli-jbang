# Contributing to GLM-CLI

Thank you for your interest in contributing to GLM-CLI! This document provides guidelines for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Submitting Changes](#submitting-changes)
- [Code Review Process](#code-review-process)

## Code of Conduct

We are committed to providing a welcoming and inclusive environment. Please:

- Be respectful and considerate
- Use inclusive language
- Focus on constructive feedback
- Assume good intentions

## Getting Started

### Prerequisites

- **Java 11+** installed
- **JBang** installed - [Install JBang](https://jbang.dev/download/)
- **GLM-4 API key** - Get from [bigmodel.cn](https://open.bigmodel.cn/)
- **Git** for version control
- **IDE** (optional) - IntelliJ IDEA, VSCode, or similar

### Setting Up Development Environment

1. **Clone the repository**

```bash
git clone https://github.com/yourusername/glm-cli-jbang.git
cd glm-cli-jbang
```

2. **Install JBang** (if not already installed)

```bash
# Using curl
curl -Ls https://sh.jbang.dev | bash -s - app setup

# Using brew
brew install jbang
```

3. **Verify installation**

```bash
./glm.groovy --version
# Output: glm-cli 0.1
```

4. **Configure API key**

```bash
# Option 1: Environment variable
export ZAI_API_KEY=your.api.key.here

# Option 2: Config file
mkdir -p ~/.glm
cat > ~/.glm/config.toml << EOF
[api]
key = "your.api.key.here"
EOF
```

5. **Test the CLI**

```bash
./glm.groovy chat "Hello!"
```

### IDE Setup

#### IntelliJ IDEA

```bash
# Open project in IntelliJ
jbang edit glm.groovy

# Or open as directory
open -a "IntelliJ IDEA" .
```

- Configure project SDK to use JBang's JDK
- Ensure Groovy support is enabled
- Install Picocli plugin for better IDE integration

#### VSCode

```bash
# Install extensions
code --install-extension groovygroovy.vscode-groovy
code --install-extension dbaeumer.vscode-eslint

# Open project
code .
```

Create `.vscode/settings.json`:

```json
{
  "groovy.compileClasspath": ["lib/*"],
  "files.associations": {
    "*.groovy": "groovy"
  }
}
```

## Development Workflow

### Branch Strategy

- `main` - Production-ready code
- `dev` - Development branch
- `feature/*` - Feature branches
- `bugfix/*` - Bug fix branches
- `docs/*` - Documentation updates

### Creating a Feature Branch

```bash
# Update main first
git checkout main
git pull origin main

# Create feature branch
git checkout -b feature/your-feature-name

# Or bugfix branch
git checkout -b bugfix/your-bugfix-name
```

### Making Changes

1. **Edit source files** in appropriate directories

```
glm-cli-jbang/
├── commands/     # Add/modify CLI commands
├── core/         # Add/modify core logic
├── tools/        # Add/modify tools
└── models/       # Add/modify data models
```

2. **Update dependencies** in `glm.groovy` if needed

```groovy
//DEPS new.dependency:version
//DEPS another.dependency:version
```

3. **Register new source files** in `glm.groovy`

```groovy
//SOURCES commands/NewCommand.groovy
//SOURCES core/NewComponent.groovy
```

4. **Test your changes**

```bash
# Run locally
./glm.groovy chat "Test new feature"

# Or install locally
jbang app install --force --name glm glm.groovy
glm chat "Test"
```

### Code Style

Follow the style guidelines in [STYLE_GUIDE.md](./STYLE_GUIDE.md):

- Use explicit types for public APIs
- Prefer guard clauses over nested conditionals
- Keep functions focused and small
- Write meaningful comments
- Follow naming conventions

### Committing Changes

1. **Check what changed**

```bash
git status
git diff
```

2. **Stage relevant files**

```bash
git add commands/NewCommand.groovy
git add core/NewComponent.groovy
```

3. **Write descriptive commit messages**

```bash
# Format: <type>(<scope>): <description>
git commit -m "feat(agent): add parallel tool execution support"

# Examples:
# feat: add new grep tool
# fix: correct path normalization in write_file
# docs: update installation instructions
# refactor: simplify agent loop logic
```

**Commit types:**
- `feat` - New feature
- `fix` - Bug fix
- `docs` - Documentation changes
- `style` - Code style changes (formatting)
- `refactor` - Code refactoring
- `test` - Adding or updating tests
- `chore` - Maintenance tasks

### Testing

#### Manual Testing

Test your changes manually:

```bash
# Test chat command
./glm.groovy chat "Test message"

# Test agent command
./glm.groovy agent "Create test file"

# Test with different models
./glm.groovy chat --model glm-4 "Complex task"
```

#### Adding Automated Tests

When adding tests (when test framework is available):

```groovy
// tests/ReadFileToolSpec.groovy
class ReadFileToolSpec extends Specification {
    def "read file returns content"() {
        given: "a read file tool"
        def tool = new ReadFileTool()

        when: "reading a file"
        def result = tool.execute([path: "test.txt"])

        then: "content is returned"
        result.contains("expected content")
    }
}
```

Run tests:

```bash
# When test framework is integrated
./glm.groovy test
```

## Submitting Changes

### Pull Request Process

1. **Push your branch**

```bash
git push origin feature/your-feature-name
```

2. **Create Pull Request**

- Go to repository on GitHub
- Click "New Pull Request"
- Select your feature branch
- Target `main` branch

3. **Fill PR Template**

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
How did you test this change?

## Checklist
- [ ] Code follows style guide
- [ ] Self-reviewed the code
- [ ] Added/updated tests
- [ ] Updated documentation
- [ ] No new warnings
```

### PR Requirements

- **Clear description** of what was changed and why
- **Tested** changes work as expected
- **Updated documentation** if behavior changed
- **Followed** [STYLE_GUIDE.md](./STYLE_GUIDE.md)
- **Clean history** without merge commits
- **Descriptive commits** with proper format

### Pull Request Labels

Maintainers will add labels:
- `bug` - Bug fix
- `enhancement` - New feature
- `documentation` - Docs update
- `good first issue` - Good for newcomers
- `help wanted` - Needs contribution

### Review Process

#### What to Expect

1. **Automated checks** - CI/CD pipelines run tests
2. **Code review** - Maintainer reviews your code
3. **Feedback** - Requested changes or approval
4. **Merge** - Approved PRs are merged to main

#### Handling Feedback

- Address requested changes
- Respond to reviewer questions
- Update PR with fixes
- Request review again when ready

## Code Review Process

### Reviewing Pull Requests

When reviewing others' work:

1. **Be constructive** - Focus on improving code quality
2. **Be specific** - Point out exact issues with line references
3. **Be respectful** - Assume good intentions
4. **Ask questions** - If something is unclear

### Review Checklist

- [ ] Code works as described
- [ ] Follows project style guide
- [ ] Includes necessary tests
- [ ] Documentation updated if needed
- [ ] No security vulnerabilities
- [ ] Performance acceptable

## Reporting Bugs

### Bug Report Template

```markdown
**Description**
Clear description of bug

**Steps to Reproduce**
1. Run '...'
2. See error '...'

**Expected Behavior**
What should happen

**Actual Behavior**
What actually happens

**Environment**
- OS: [e.g., macOS 14.0]
- Java version: [e.g., OpenJDK 17]
- GLM-CLI version: [e.g., 0.1]

**Additional Context**
Logs, screenshots, etc.
```

### Where to Report

- **GitHub Issues** - For bugs and feature requests
- **Discussions** - For questions and ideas
- **Discord** - For community support (if available)

## Requesting Features

### Feature Request Template

```markdown
**Problem Description**
What problem does this solve?

**Proposed Solution**
How should it work?

**Alternatives Considered**
What other options did you consider?

**Additional Context**
Examples, mockups, etc.
```

## Documentation Updates

When contributing code changes:

- Update **README.md** if user-facing changes
- Update **technicalSpec.md** if API changes
- Update **AGENTS.md** if agent behavior changes
- Add **examples** for new features

## Getting Help

### Resources

- **Documentation** - Check [README.md](./README.md) and other docs
- **Issues** - Search existing GitHub issues
- **Discussions** - Ask questions in GitHub Discussions
- **Code** - Review similar code in codebase

### Contact

- **GitHub Issues** - Report bugs and request features
- **GitHub Discussions** - Ask questions
- **Email** - For security issues only

## Recognition

Contributors are recognized in:

- **CONTRIBUTORS.md** - List of contributors
- **Release Notes** - Mentioned in releases
- **Changelog** - Listed in changelog

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).

## Additional Resources

- [STYLE_GUIDE.md](./STYLE_GUIDE.md) - Code style guidelines
- [AGENTS.md](./AGENTS.md) - Agent system guidelines
- [DEVELOPMENT.md](./DEVELOPMENT.md) - Development guide
- [Architecture](./architecture.md) - Technical architecture
- [Technical Spec](./technicalSpec.md) - API reference

---

Thank you for contributing to GLM-CLI! 🎉
