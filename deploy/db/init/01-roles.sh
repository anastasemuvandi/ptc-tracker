#!/bin/sh
# Runs once, on first initialisation of the database volume, as the owner
# (POSTGRES_USER). Creates the two runtime login roles. Grants are applied
# by the Flyway migrations.
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v app_pw="$PTC_DB_APP_PASSWORD" -v auth_pw="$PTC_DB_AUTH_PASSWORD" <<'SQL'
CREATE ROLE ptc_app LOGIN PASSWORD :'app_pw';
CREATE ROLE ptc_auth LOGIN PASSWORD :'auth_pw';
REVOKE ALL ON DATABASE :"DBNAME" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"DBNAME" TO ptc_app, ptc_auth;
SQL
