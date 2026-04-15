#!/bin/bash
# shellcheck enable=add-default-case,avoid-nullary-conditions,quote-safe-variables,require-double-brackets

#
# Deploy ExecHelper build to files.executiveundertakings.com
#
# 1. Uploads the Mac ZIP to the matching GitHub Release (creates the release
#    if it doesn't already exist; --clobbers if it does).
# 2. Copies ZIPs + latest-version.txt into the Public Files folder.
# 3. Runs deploy-public-files.py to push to Cloudflare Pages immediately.
#
# Usage:
#   bash "Deploy to Update Server.sh"          # interactive confirm
#   bash "Deploy to Update Server.sh" --yes    # skip confirm (called from build script)
#

PATH='/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin'

PUBLIC_FILES_DIR="${HOME}/Documents/Public Files"
QA_UPDATES_DIR="${PUBLIC_FILES_DIR}/qa-updates"

PROJECT_PATH="$(cd "${BASH_SOURCE[0]%/*}/.." &> /dev/null && pwd -P)"
readonly PROJECT_PATH

DIST_PATH="${PROJECT_PATH}/dist"

# ── Parse flags ──────────────────────────────────────────────────────────────
YES=false
for arg in "$@"; do
    [[ "${arg}" == '--yes' || "${arg}" == '-y' ]] && YES=true
done

# ── Verify at least one Mac ZIP was built ────────────────────────────────────
if [[ ! -f "${DIST_PATH}/QAHelper-mac-ElCapitan.zip" && ! -f "${DIST_PATH}/QAHelper-mac-universal.zip" ]]; then
    >&2 echo '!!! No built ZIPs found in dist/ — run "Compile QA Helper for Mac.sh" first !!!'
    exit 1
fi

# ── Read version ──────────────────────────────────────────────────────────────
app_version="$(head -1 "${PROJECT_PATH}/src/Resources/qa-helper-version.txt" 2>/dev/null)"

if [[ -z "${app_version}" ]]; then
    >&2 echo '!!! Could not determine app version — aborting !!!'
    exit 2
fi

echo "Deploying ExecHelper ${app_version} to GitHub + files.executiveundertakings.com"
echo ""

if ! $YES; then
    read -rp "Confirm deploy? [y/N] " confirm
    if [[ "${confirm}" != 'y' && "${confirm}" != 'Y' ]]; then
        echo 'Aborted.'
        exit 0
    fi
fi

# ── GitHub Release ────────────────────────────────────────────────────────────
if command -v gh &> /dev/null; then
    echo "Uploading to GitHub Release v${app_version}..."
    if ! gh release view "v${app_version}" --repo execundertakings/ExecHelper &> /dev/null; then
        gh release create "v${app_version}" \
            --title "ExecHelper ${app_version}" \
            --repo execundertakings/ExecHelper \
            "${DIST_PATH}/QAHelper-mac-ElCapitan.zip" \
            && echo "  ✓ Created GitHub Release v${app_version}"
    else
        # Only upload ElCapitan — we don't maintain a Universal build
        if [[ -f "${DIST_PATH}/QAHelper-mac-ElCapitan.zip" ]]; then
            gh release upload "v${app_version}" "${DIST_PATH}/QAHelper-mac-ElCapitan.zip" \
                --repo execundertakings/ExecHelper --clobber \
                && echo "  ✓ Uploaded QAHelper-mac-ElCapitan.zip to GitHub Release v${app_version}"
        fi
    fi
else
    >&2 echo '  ⚠ gh CLI not found — skipping GitHub Release upload'
fi

# ── Cloudflare Pages deploy ───────────────────────────────────────────────────
mkdir -p "${QA_UPDATES_DIR}"

echo "${app_version}" > "${QA_UPDATES_DIR}/latest-version.txt"
echo "  ✓ latest-version.txt → ${app_version}"

if [[ -f "${DIST_PATH}/QAHelper-mac-ElCapitan.zip" ]]; then
    cp "${DIST_PATH}/QAHelper-mac-ElCapitan.zip" "${QA_UPDATES_DIR}/QAHelper-mac-ElCapitan.zip"
    echo "  ✓ Copied QAHelper-mac-ElCapitan.zip"
fi

echo ""
echo "Pushing to Cloudflare Pages..."
python3 "${HOME}/Documents/deploy-public-files.py" && echo "  ✓ Deployed to files.executiveundertakings.com"

echo ""
echo "✓ Done. Verify:"
echo "  curl https://files.executiveundertakings.com/qa-updates/latest-version.txt"
