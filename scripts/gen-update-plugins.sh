#!/usr/bin/env bash
# Генерирует updatePlugins.xml — указатель кастомного репозитория плагинов IntelliJ
# на текущую версию плагина. Значения берутся из окружения CI (см. .gitverse/workflows/publish.yaml).
# Файл должен отдаваться статикой GitVerse Pages байт-в-байт (рядом лежит .nojekyll).
set -euo pipefail

PLUGIN_ID="${PLUGIN_ID:?PLUGIN_ID is required (из plugin.xml <id>)}"
VERSION="${VERSION:?VERSION is required (= тег без префикса v)}"
SINCE_BUILD="${SINCE_BUILD:?SINCE_BUILD is required}"
UNTIL_BUILD="${UNTIL_BUILD:-}"          # пусто = без верхней границы
ASSET_URL="${ASSET_URL:?ASSET_URL is required (публичный URL .zip на GitVerse Pages)}"

UNTIL_ATTR=""
[ -n "$UNTIL_BUILD" ] && UNTIL_ATTR=" until-build=\"$UNTIL_BUILD\""

cat > updatePlugins.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<plugins>
  <plugin id="$PLUGIN_ID" url="$ASSET_URL" version="$VERSION">
    <idea-version since-build="$SINCE_BUILD"${UNTIL_ATTR}/>
  </plugin>
</plugins>
EOF

echo "Сгенерирован updatePlugins.xml:"
cat updatePlugins.xml
