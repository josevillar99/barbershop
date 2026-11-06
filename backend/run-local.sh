#!/usr/bin/env bash
# Arranca el backend en local (sin Docker) contra el Oracle que corre en el
# contenedor oracle-db (debe estar levantado: docker compose up -d oracle-db).
set -euo pipefail

cd "$(dirname "$0")"

ENV_FILE="../.env"
if [ ! -f "$ENV_FILE" ]; then
    echo "No se encuentra $ENV_FILE. Copia .env.example a .env y rellena tus credenciales." >&2
    exit 1
fi

while IFS='=' read -r key value; do
    [[ -z "$key" || "$key" == \#* ]] && continue
    value="${value%$'\r'}"
    export "$key=$value"
done < "$ENV_FILE"

export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"

export DB_URL="jdbc:oracle:thin:@localhost:1521/XEPDB1"
export DB_USER="$DB_APP_USER"
export DB_PASSWORD="$DB_APP_PASSWORD"

echo "Usando $(java -version 2>&1 | head -1)"
mvn spring-boot:run
