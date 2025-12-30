#!/bin/bash

# Simple setup script to add LangChain4j support to GLM-CLI
# This downloads the jar file if not available and places it in local lib

set -e

LANGCHAIN4J_VERSION="1.10.0-beta18"
LANGCHAIN4J_JAR="langchain4j-community-zhipu-ai-${LANGCHAIN4J_VERSION}.jar"
LOCAL_LIB_DIR="./lib"

# Create lib directory if it doesn't exist
mkdir -p "$LOCAL_LIB_DIR"

# Check if jar already exists
if [ -f "$LOCAL_LIB_DIR/$LANGCHAIN4J_JAR" ]; then
    echo "LangChain4j JAR already exists at $LOCAL_LIB_DIR/$LANGCHAIN4J_JAR"
else
    echo "Downloading LangChain4j JAR from Maven Central..."
    
    # Try to download from Maven Central
    DOWNLOAD_URL="https://repo1.maven.org/maven2/dev/langchain4j/dev/langchain4j/community-zhipu-ai/${LANGCHAIN4J_VERSION}/langchain4j-community-zhipu-ai-${LANGCHAIN4J_VERSION}.jar"
    
    if command -v curl --silent --show-error --location "$DOWNLOAD_URL" -o "$LOCAL_LIB_DIR/$LANGCHAIN4J_JAR.part"; then
        rm -f "$LOCAL_LIB_DIR/$LANGCHAIN4J_JAR.part"
        echo "✓ Downloaded LangChain4j JAR"
    else
        echo "✗ Failed to download JAR (curl not available)"
        echo "Please download manually from: https://github.com/langchain4j/langchain4j/releases/download"
        exit 1
fi

echo "LangChain4j setup complete!"
echo "JAR location: $LOCAL_LIB_DIR/$LANGCHAIN4J_JAR"
echo ""
echo "To use with JBang, add to glm.groovy:"
echo "  //DEPS file:///path/to/lib/langchain4j-community-zhipu-ai-\${LANGCHAIN4J_VERSION}.jar"
echo ""
echo "Or set CLASSPATH:"
echo "  export CLASSPATH=\$LOCAL_LIB_DIR:\$CLASSPATH"
