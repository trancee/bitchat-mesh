#!/bin/bash
# Verification script for bitchat-mesh library

echo "=== Bitchat Mesh Library Verification ==="
echo

# Check for Tor references
echo "Checking for unwanted Tor references..."
if grep -r "ArtiTorManager\|org.torproject\|info.guardianproject.arti" bitchat-mesh/src/main/java/; then
    echo "❌ FAILED: Found Tor references in source code"
    exit 1
else
    echo "✓ No Tor references found"
fi

# Check for Nostr references
echo "Checking for unwanted Nostr references..."
if grep -r "NostrRelayManager\|NostrClient\|NostrProtocol" bitchat-mesh/src/main/java/; then
    echo "❌ FAILED: Found Nostr references in source code"
    exit 1
else
    echo "✓ No Nostr references found"
fi

# Check that required directories exist
echo "Checking core directories..."
REQUIRED_DIRS="core crypto identity mesh model noise protocol service util"
for dir in $REQUIRED_DIRS; do
    if [ ! -d "bitchat-mesh/src/main/java/com/bitchat/android/$dir" ]; then
        echo "❌ FAILED: Missing required directory: $dir"
        exit 1
    fi
done
echo "✓ All core directories present"

# Check that excluded directories are absent
echo "Checking excluded directories..."
EXCLUDED_DIRS="nostr geohash"
for dir in $EXCLUDED_DIRS; do
    if [ -d "bitchat-mesh/src/main/java/com/bitchat/android/$dir" ]; then
        echo "❌ FAILED: Found excluded directory: $dir"
        exit 1
    fi
done
echo "✓ Excluded directories not present"

# Check build files exist
echo "Checking build configuration..."
if [ ! -f "bitchat-mesh/build.gradle.kts" ]; then
    echo "❌ FAILED: Missing build.gradle.kts"
    exit 1
fi
echo "✓ Build configuration present"

echo
echo "=== All verification checks passed! ==="
echo "Run './gradlew build' to compile the library"
