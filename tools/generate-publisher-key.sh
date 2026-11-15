#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

KEY_DIR="$(realpath -m "${1:-$HOME/.config/tomos-maps/publisher}")"
PRIVATE_KEY="$KEY_DIR/ed25519-private.pem"
PUBLIC_KEY="$KEY_DIR/ed25519-public.pem"

command -v openssl >/dev/null 2>&1 || { echo "OpenSSL is required." >&2; exit 2; }
mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"
if [[ -e "$PRIVATE_KEY" || -e "$PUBLIC_KEY" ]]; then
  echo "A publisher key already exists in $KEY_DIR; refusing to overwrite it." >&2
  exit 1
fi

openssl genpkey -algorithm ED25519 -out "$PRIVATE_KEY"
chmod 600 "$PRIVATE_KEY"
openssl pkey -in "$PRIVATE_KEY" -pubout -out "$PUBLIC_KEY"
chmod 644 "$PUBLIC_KEY"
PUBLIC_DER="$KEY_DIR/ed25519-public.der"
openssl pkey -pubin -in "$PUBLIC_KEY" -outform DER -out "$PUBLIC_DER"
PUBLIC_BASE64="$(base64 < "$PUBLIC_DER" | tr -d '\n')"
rm -f "$PUBLIC_DER"

echo "Created a new Ed25519 publisher key pair."
echo "Private key (keep secret; do not commit): $PRIVATE_KEY"
echo "Public key: $PUBLIC_KEY"
echo
echo "Set RegionalPackageBuildConfig.PINNED_ED25519_PUBLIC_KEY_BASE64 to:"
echo "$PUBLIC_BASE64"
echo
echo "Store the private PEM in a protected secret or secure release environment."
