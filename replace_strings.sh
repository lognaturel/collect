#!/bin/sh

find strings/src/main/res -type f -exec sed -i "" 's|ODK|HNEC|g' {} +
