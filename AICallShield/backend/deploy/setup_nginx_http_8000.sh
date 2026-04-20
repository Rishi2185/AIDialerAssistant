#!/usr/bin/env bash
set -euo pipefail

SITE_NAME="aicallshield"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF_SOURCE="${SCRIPT_DIR}/aicallshield_http_8000.conf"
CONF_TARGET="/etc/nginx/sites-available/${SITE_NAME}"
LINK_TARGET="/etc/nginx/sites-enabled/${SITE_NAME}"

if [[ ! -f "${CONF_SOURCE}" ]]; then
    echo "Config not found: ${CONF_SOURCE}"
    exit 1
fi

echo "Installing Nginx..."
sudo apt-get update
sudo apt-get install -y nginx

echo "Applying site config..."
sudo cp "${CONF_SOURCE}" "${CONF_TARGET}"
sudo ln -sf "${CONF_TARGET}" "${LINK_TARGET}"

if [[ -e /etc/nginx/sites-enabled/default ]]; then
    sudo rm -f /etc/nginx/sites-enabled/default
fi

echo "Validating and reloading Nginx..."
sudo nginx -t
sudo systemctl enable nginx
sudo systemctl restart nginx

echo "Done. Nginx now proxies HTTP traffic to 127.0.0.1:8000"
echo "Test with: curl http://YOUR_EC2_PUBLIC_IP/health"
