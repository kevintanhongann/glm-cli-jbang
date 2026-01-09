# GLM-CLI Testing Framework

This directory contains comprehensive testing for all GLM-CLI features implemented in the previous phases.

## Test Structure

The testing framework is organized into several categories:

### 1. Unit Tests (`tests/core/`)
Unit tests for individual components:
- `EventBusTest.groovy` - Event system functionality
- `ReactiveStateTest.groovy` - Reactive state management
- `StateRegistryTest.groovy` - State registry operations
- `AgentStateTest.groovy` - Agent state management
- `TokenCounterTest.groovy` - Token counting functionality
- `CompactionTriggerTest.groovy` - Session compaction triggers
- `SummaryGeneratorTest.groovy` - Conversation summarization
- `HistoryPrunerTest.groovy` - History pruning operations
- `SessionCompactorTest.groovy` - Session compaction workflow
- `DoomLoopDetectorTest.groovy` - Doom loop detection
- `PermissionManagerTest.groovy` - Permission management
- `PermissionPromptHandlerTest.groovy` - Permission prompting
- `DoomLoopAgentTest.groovy` - Doom loop handling
- `TuiPermissionPromptHandlerTest.groovy` - TUI permission handling

### 2. Integration Tests (`tests/integration/`)
Integration tests for component interactions:
- `EventSystemIntegrationTest.groovy` - Event system integration with Agent and TUI
- `SessionCompactionIntegrationTest.groovy` - Session compaction integration with Agent loop
- `DoomLoopIntegrationTest.groovy` - Doom loop detection integration with Agent execution

### 3. Performance Tests (`tests/performance/`)
Performance benchmarks to ensure no significant overhead:
- `EventSystemPerformanceTest.groovy` - Event system performance
- `TokenCounterPerformanceTest.groovy` - Token counting performance
- `CompactionPerformanceTest.groovy` - Compaction performance
- `LoopDetectionPerformanceTest.groovy` - Loop detection performance

### 4. Manual Tests (`tests/manual/`)
Manual testing scripts for interactive testing:
- `TuiInteractionTest.groovy` - TUI interaction testing
- `EndToEndWorkflowTest.groovy` - End-to-end workflow testing
- `ConfigurationTest.groovy` - Configuration testing

### 5. Test Runners
- `test-runner.groovy` - Standard test runner for unit and integration tests
- `comprehensive-test-runner.groovy` - Comprehensive test runner for all test types

## Running Tests

### Quick Start
Run all unit and integration tests:
```bash
jbang tests/test-runner.groovy
```

### Comprehensive Testing
Run all test types (unit, integration, performance, manual):
```bash
jbang tests/comprehensive-test-runner.groovy --all
```

### Selective Testing
Run specific test categories:
```bash
# Run only unit tests
jbang tests/comprehensive-test-runner.groovy --unit

# Run only integration tests
jbang tests/comprehensive-test-runner.groovy --integration

# Run only performance tests
jbang tests/comprehensive-test-runner.groovy --performance

# Run only manual tests
jbang tests/comprehensive-test-runner.groovy --manual
```

### Manual Test Scripts
Run individual manual test scripts:
```bash
# TUI interaction testing
jbang tests/manual/TuiInteractionTest.groovy

# End-to-end workflow testing
jbang tests/manual/EndToEndWorkflowTest.groovy

# Configuration testing
jbang tests/manual/ConfigurationTest.groovy
```

## Test Dependencies

The testing framework uses the following dependencies:
- **Spock Framework** - Primary testing framework
- **JUnit 5** - Test execution engine
- **Lanterna** - For TUI component testing

All dependencies are managed through JBang and should be automatically resolved.

## Test Coverage

The test suite covers:

### Core Components
- ✅ EventBus - Event publishing and subscription
- ✅ ReactiveState - State management and change notifications
- ✅ StateRegistry - State registration and retrieval
- ✅ AgentState - Agent state lifecycle management
- ✅ TokenCounter - Token estimation for various content types
- ✅ CompactionTrigger - Session compaction triggering logic
- ✅ SummaryGenerator - Conversation summarization
- ✅ HistoryPruner - History pruning algorithms
- ✅ SessionCompactor - Complete compaction workflow
- ✅ DoomLoopDetector - Loop detection algorithms
- ✅ PermissionManager - Permission request handling
- ✅ PermissionPromptHandler - Permission prompting
- ✅ DoomLoopAgent - Doom loop handling workflow

### Integration Scenarios
- ✅ Event system integration with Agent and TUI
- ✅ Session compaction integration with Agent loop
- ✅ Doom loop detection integration with Agent execution

### Performance Benchmarks
- ✅ Event system performance under load
- ✅ Token counting performance with large texts
- ✅ Compaction performance with large histories
- ✅ Loop detection performance with large histories

### Manual Testing
- ✅ TUI component interaction testing
- ✅ End-to-end workflow simulation
- ✅ Configuration loading and validation

## Test Best Practices

### Unit Tests
- Test individual components in isolation
- Use mocks and stubs for dependencies
- Focus on specific functionality
- Ensure fast execution times

### Integration Tests
- Test component interactions
- Use real components where possible
- Focus on integration points
- Validate end-to-end workflows

### Performance Tests
- Measure execution time and memory usage
- Test with realistic data sizes
- Set performance thresholds
- Monitor for regressions

### Manual Tests
- Provide interactive testing interfaces
- Test complex workflows
- Validate user experience
- Test configuration scenarios

## Troubleshooting

### Common Issues

1. **Test Dependencies Not Found**
   - Ensure JBang is properly installed
   - Check internet connection for dependency resolution
   - Verify dependency versions in test files

2. **TUI Tests Not Working**
   - Ensure terminal supports ANSI escape codes
   - Check if running in headless environment
   - Verify Lanterna dependencies are loaded

3. **Performance Tests Timing Out**
   - Check system resources (CPU, memory)
   - Verify test thresholds are appropriate
   - Consider running on more powerful hardware

4. **Integration Tests Failing**
   - Check component dependencies
   - Verify test data setup
   - Ensure proper cleanup between tests

### Debugging Tips

1. **Enable Verbose Output**
   ```bash
   jbang tests/test-runner.groovy -v
   ```

2. **Run Single Test Class**
   ```bash
   jbang tests/core/EventBusTest.groovy
   ```

3. **Check Test Dependencies**
   ```bash
   jbang --verbose tests/test-runner.groovy
   ```

## Continuous Integration

The test suite is designed to work with CI/CD pipelines:

1. **Unit Tests** - Fast execution, suitable for pre-commit hooks
2. **Integration Tests** - Medium execution time, suitable for CI builds
3. **Performance Tests** - Longer execution time, suitable for nightly builds
4. **Manual Tests** - Interactive, suitable for manual QA processes

## Contributing

When adding new features:

1. **Write Unit Tests** - Create tests for new components
2. **Write Integration Tests** - Test component interactions
3. **Add Performance Tests** - Ensure no performance regressions
4. **Update Manual Tests** - Add manual testing for new workflows
5. **Update Documentation** - Document new test cases

When modifying existing features:

1. **Run Existing Tests** - Ensure no regressions
2. **Update Tests** - Modify tests for changed behavior
3. **Add New Tests** - Cover new functionality
4. **Verify Performance** - Ensure no performance degradation