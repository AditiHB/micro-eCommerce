#!/bin/bash
# Creates one database per service, since the official postgres image only
# creates the single database named by POSTGRES_DB. Reads a comma-separated
# list from POSTGRES_MULTIPLE_DATABASES.
set -e

if [ -n "$POSTGRES_MULTIPLE_DATABASES" ]; then
    echo "Creating databases: $POSTGRES_MULTIPLE_DATABASES"
    IFS=',' read -ra DATABASES <<< "$POSTGRES_MULTIPLE_DATABASES"
    for db in "${DATABASES[@]}"; do
        db="$(echo "$db" | xargs)"
        echo "Creating database '$db' owned by '$POSTGRES_USER'"
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
            SELECT 'CREATE DATABASE "$db" OWNER "$POSTGRES_USER"'
            WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
EOSQL
    done
fi
