#!/bin/sh
set -eu

CERT_DIR=/etc/nginx/certs
CERT_FILE="$CERT_DIR/server.crt"
KEY_FILE="$CERT_DIR/server.key"
HOST_NAME="${PUBLIC_HOST:-localhost}"
CERT_DAYS="${TLS_CERT_DAYS:-365}"

mkdir -p "$CERT_DIR"

if [ -f "$CERT_FILE" ] && [ -f "$KEY_FILE" ]; then
  exit 0
fi

SAN_LIST="DNS:localhost,IP:127.0.0.1"
case "$HOST_NAME" in
  *[!0-9.]* )
    SAN_LIST="$SAN_LIST,DNS:$HOST_NAME"
    ;;
  * )
    SAN_LIST="$SAN_LIST,IP:$HOST_NAME"
    ;;
esac

openssl req -x509 -nodes -newkey rsa:2048 \
  -days "$CERT_DAYS" \
  -keyout "$KEY_FILE" \
  -out "$CERT_FILE" \
  -subj "/CN=$HOST_NAME" \
  -addext "subjectAltName=$SAN_LIST"