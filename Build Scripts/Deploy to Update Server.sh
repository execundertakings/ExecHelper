#!/bin/bash
# shellcheck enable=add-default-case,avoid-nullary-conditions,quote-safe-variables,require-double-brackets

#
# Deploy to Update Server
#
# Uploads the built QA Helper ZIPs and version file to executiveundertakings.com
# so that running instances of QA Helper can auto-update.
#
# USAGE:
#   Run this script from the "Build Scripts" folder AFTER a successful build
#   via "Compile QA Helper for Mac.sh".
#
# PREREQUISITES:
#   - SSH key access to executiveundertakings.com (or be prepared to enter password)
#   - The dist/ folder must contain freshly built ZIPs
#
# SERVER SETUP (one-time):
#   On executiveundertakings.com, create the folder:
#       /var/www/html/qa-updates/
#   Make sure it is served at:
#       https://executiveundertakings.com/qa-updates/
#   The deploy script will upload these files into that folder:
#       latest-version.txt          — one-line plain-text version string
#       QAHelper-mac-universal.zip  — macOS Sierra+ (JDK 21 Universal Binary)
#       QAHelper-mac-ElCapitan.zip  — macOS El Capitan (JDK 16 Intel only)
#       QAHelper-windows-installer.zip  — Windows (if built)
#       QAHelper-linux-installer.zip    — Linux Mint (if built)
#

# ─── CONFIGURATION ────────────────────────────────────────────────────────────
# SSH user and host for executiveundertakings.com
UPDATE_SERVER_USER='your-ssh-username'
UPDATE_SERVER_HOST='executiveundertakings.com'

# Absolute path on the server where update files are served from
UPDATE_SERVER_PATH='/var/www/html/qa-updates'
# ──────────────────────────────────────────────────────────────────────────────

PATH='/usr/bin:/bin:/usr/sbin:/sbin'

PROJECT_PATH="$(cd "${BASH_SOURCE[0]%/*}/.." &> /dev/null && pwd -P)"
readonly PROJECT_PATH

DIST_PATH="${PROJECT_PATH}/dist"

# ── Verify at least one ZIP was built ─────────────────────────────────────────
if [[ ! -f "${DIST_PATH}/QAHelper-mac-universal.zip" && ! -f "${DIST_PATH}/QAHelper-mac-ElCapitan.zip" ]]; then
    >&2 echo '!!! No built ZIPs found in dist/ — run "Compile QA Helper for Mac.sh" first !!!'
    exit 1
fi

# ── Read version from the universal ZIP (fall back to ElCapitan ZIP) ──────────
zip_to_read="${DIST_PATH}/QAHelper-mac-universal.zip"
[[ ! -f "${zip_to_read}" ]] && zip_to_read="${DIST_PATH}/QAHelper-mac-ElCapitan.zip"

app_version="$(unzip -p "${zip_to_read}" 'QA Helper.app/Contents/Java/QA_Helper.jar' 2>/dev/null | unzip -p /dev/stdin '*/qa-helper-version.txt' 2>/dev/null | head -1)"

# Simpler fallback: read from source resource
if [[ -z "${app_version}" ]]; then
    app_version="$(cat "${PROJECT_PATH}/src/Resources/qa-helper-version.txt" 2>/dev/null | head -1)"
fi

if [[ -z "${app_version}" ]]; then
    >&2 echo '!!! Could not determine app version — aborting !!!'
    exit 2
fi

echo "Deploying QA Helper version ${app_version} to ${UPDATE_SERVER_HOST}..."

# ── Confirm before uploading ──────────────────────────────────────────────────
read -rp "Upload version ${app_version} to ${UPDATE_SERVER_USER}@${UPDATE_SERVER_HOST}:${UPDATE_SERVER_PATH}? [y/N] " confirm
if [[ "${confirm}" != 'y' && "${confirm}" != 'Y' ]]; then
    echo 'Aborted.'
    exit 0
fi

# ── Write local version file ──────────────────────────────────────────────────
version_file="${DIST_PATH}/latest-version.txt"
echo "${app_version}" > "${version_file}"
echo "  → Wrote latest-version.txt: ${app_version}"

# ── Upload via scp ────────────────────────────────────────────────────────────
remote="${UPDATE_SERVER_USER}@${UPDATE_SERVER_HOST}:${UPDATE_SERVER_PATH}/"

echo "  → Uploading latest-version.txt..."
scp "${version_file}" "${remote}"

for zip_name in 'QAHelper-mac-universal.zip' 'QAHelper-mac-ElCapitan.zip' 'QAHelper-windows-installer.zip' 'QAHelper-linux-installer.zip'; do
    if [[ -f "${DIST_PATH}/${zip_name}" ]]; then
        echo "  → Uploading ${zip_name}..."
        scp "${DIST_PATH}/${zip_name}" "${remote}"
    else
        echo "  (skipping ${zip_name} — not found in dist/)"
    fi
done

echo ""
echo "✓ Deploy complete! QA Helper ${app_version} is now live at:"
echo "  https://${UPDATE_SERVER_HOST}/qa-updates/"
echo ""
echo "Verify the version check URL returns the right version:"
echo "  curl https://${UPDATE_SERVER_HOST}/qa-updates/latest-version.txt"
