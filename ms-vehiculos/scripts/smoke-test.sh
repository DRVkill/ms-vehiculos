#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:9004}"
PATENTE="SMK$(date +%s | tail -c 4)"

esperar() {
  for i in $(seq 1 30); do
    if curl -fsS "$BASE_URL/actuator/health/readiness" >/dev/null 2>&1; then return 0; fi
    echo "Esperando al servicio ($i/30)..."; sleep 4
  done
  echo "El servicio no respondió"; exit 1
}

check() {
  local code
  code=$(curl -s -o /tmp/smoke.json -w '%{http_code}' -X "$1" "$BASE_URL$2" \
    -H 'Content-Type: application/json' ${4:+-d "$4"})
  if [ "$code" != "$3" ]; then
    echo "FALLO $1 $2 -> $code (esperado $3)"; cat /tmp/smoke.json; exit 1
  fi
  echo "OK    $1 $2 -> $code"
}

esperar
check GET  /actuator/health/liveness 200
check GET  / 200
check POST /api/v1/vehiculos 201 \
  "{\"patente\":\"$PATENTE\",\"marca\":\"Smoke\",\"modelo\":\"Test\",\"anio\":2024,\"tipo\":\"Sedan\",\"disponible\":true}"
ID=$(sed -E 's/.*"id":([0-9]+).*/\1/' /tmp/smoke.json)
check GET    "/api/v1/vehiculos/$ID" 200
check DELETE "/api/v1/vehiculos/$ID" 204
echo "Smoke test superado"
