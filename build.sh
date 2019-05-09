#!/usr/bin/env bash

ARTIFACT_NAME=$(clj -A:artifact-name)
ARTIFACT_ID=$(echo "$ARTIFACT_NAME" | cut -f1)
ARTIFACT_VERSION=$(echo "$ARTIFACT_NAME" | cut -f2)
JAR_FILENAME="$ARTIFACT_ID-$ARTIFACT_VERSION.jar"

echo -e "Build \"scenari\" jar: target/$JAR_FILENAME"

clj -A:build --app-group-id scenari --app-artifact-id $ARTIFACT_ID --app-version $ARTIFACT_VERSION 2>&1 > /dev/null

if [ $? -eq 0 ]; then
    echo "Successfully built \"scenari\"'s artifact: target/$JAR_FILENAME"
else
    echo "Fail to built \"scenari\"'s artifact!"
    exit 1
fi
