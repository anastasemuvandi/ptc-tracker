#!/bin/sh
# Prints fresh secrets in .env format. Run once per installation.
set -eu
rand() { head -c "$1" /dev/urandom | base64 | tr -d '\n=+/' | cut -c1-"$2"; }
echo "PTC_DB_OWNER_PASSWORD=$(rand 48 40)"
echo "PTC_DB_APP_PASSWORD=$(rand 48 40)"
echo "PTC_DB_AUTH_PASSWORD=$(rand 48 40)"
# AES-256 key protecting TOTP secrets at rest (base64, 32 bytes). Back it up:
# losing it forces every user to re-enrol MFA.
echo "PTC_DATA_KEY=$(head -c 32 /dev/urandom | base64 | tr -d '\n')"
