#!/bin/sh
NETBEANS_MAVEN="/c/Program Files/Apache NetBeans/java/maven/bin/mvn"

if [ -f "$NETBEANS_MAVEN" ]; then
    exec "$NETBEANS_MAVEN" "$@"
else
    exec mvn "$@"
fi
