#!/bin/bash
# Создаёт отдельную БД и учётную запись на каждый сервис (принцип database-per-service, ТЗ 8.3).
set -e
for svc in identity catalog planning booking notification; do
  var="$(echo "${svc}" | tr '[:lower:]' '[:upper:]')_DB_PASSWORD"
  pw="${!var}"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE USER ${svc} WITH PASSWORD '${pw}';
CREATE DATABASE ${svc}_db OWNER ${svc};
REVOKE ALL ON DATABASE ${svc}_db FROM PUBLIC;
SQL
done
