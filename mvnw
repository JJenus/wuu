#!/bin/sh
# Maven Wrapper for the tracking engine project.
# This wrapper:
#   1. Pins Maven 3.9.9 (downloaded once into .mvn/wrapper/maven-dist)
#   2. Forces JAVA_HOME to Java 21 (LTS) so surefire/jacoco work
#     (Maven 3.8.7 shipped with the system uses Java 25 which has
#     module-system incompatibilities with several plugins.)

set -e

# ----- locate Java 21 -----
JAVA_21=""
for candidate in \
    /usr/lib/jvm/java-21-openjdk-amd64 \
    /usr/lib/jvm/java-1.21.0-openjdk-amd64 \
    /opt/jdk-21 \
    /Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home
do
    if [ -x "$candidate/bin/java" ]; then
        JAVA_21="$candidate"
        break
    fi
done

if [ -z "$JAVA_21" ] && command -v java >/dev/null 2>&1; then
    # Fall back to whichever java is on PATH and is version 21.
    # JVM option notices can precede the version line (e.g. coverage agents).
    JAVA_VERSION="$(java -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1)"
    if [ "$JAVA_VERSION" = "21" ]; then
        JAVA_21="$(dirname $(dirname $(readlink -f $(which java))))"
    fi
fi

if [ -z "$JAVA_21" ]; then
    echo "ERROR: Java 21 not found. Install OpenJDK 21 or update JAVA_21_PATH in mvnw." >&2
    exit 1
fi

export JAVA_HOME="$JAVA_21"
export PATH="$JAVA_21/bin:$PATH"

# ----- locate or download Maven 3.9.9 -----
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MVN_DIST="$SCRIPT_DIR/.mvn/wrapper/maven-dist"
MVN_VERSION="3.9.9"
MVN_HOME="$MVN_DIST/apache-maven-$MVN_VERSION"

if [ ! -x "$MVN_HOME/bin/mvn" ]; then
    echo "Downloading Maven $MVN_VERSION to $MVN_DIST ..."
    mkdir -p "$MVN_DIST"
    MVN_ZIP="$MVN_DIST/apache-maven-$MVN_VERSION-bin.zip"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL -o "$MVN_ZIP" \
            "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MVN_VERSION/apache-maven-$MVN_VERSION-bin.zip"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$MVN_ZIP" \
            "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MVN_VERSION/apache-maven-$MVN_VERSION-bin.zip"
    else
        echo "ERROR: neither curl nor wget found, cannot download Maven." >&2
        exit 1
    fi
    unzip -q "$MVN_ZIP" -d "$MVN_DIST"
    rm -f "$MVN_ZIP"
fi

export PATH="$MVN_HOME/bin:$PATH"

# ----- run Maven -----
exec mvn "$@"
