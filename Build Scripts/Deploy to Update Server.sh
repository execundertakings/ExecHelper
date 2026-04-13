#!/bin/bash
# shellcheck enable=add-default-case,avoid-nullary-conditions,quote-safe-variables,require-double-brackets

#
# Deploy ExecHelper build to files.executiveundertakings.com
#
# Copies the built ZIPs and version file into the Public Files folder,
# which is automatically deployed to Cloudflare Pages within ~2 minutes
# by the com.executiveundertakings.publicfiles LaunchAgent.
#
# Run this after a successful build from "Compile QA Helper for Mac.sh".
#
# The LaunchAgent watches for ANY file change (including in qa-updates/)
# and triggers a Wrangler deploy automatically. You can also force an
# immediate deploy by running:
#   python3 ~/Documents/deploy-public-files.py
#

PATH='/usr/bin:/bin:/usr/sbin:/sbin'

PUBLIC_FILES_DIR="${HOME}/Documents/public files"
QA_UPDATES_DIR="${PUBLIC_FILES_DIR}/qa-updates"

PROJECT_PATH="$(cd "${BASH_SOURCE[0]%/*}/.." &> /dev/null && pwd -P)"
readonly PROJECT_PATH

DIST_PATH="${PROJECT_PATH}/dist"

# ── Verify at least one ZIP was built ────────────────────────────────────────
if [[ ! -f "${DIST_PATH}/QAHelper-mac-ElCapitan.zip" && ! -f "${DIST_PATH}/QAHelper-mac-universal.zip" ]]; then
    >&2 echo '!!! No built ZIPs found in dist/ — run "Compile QA Helper for Mac.sh" first !!!'
    exit 1
fi

# ── Read version from the built ZIP ──────────────────────────────────────────
zip_to_read="${DIST_PATH}/QAHelper-mac-ElCapitan.zip"
[[ ! -f "${zip_to_read}" ]] && zip_to_read="${DIST_PATH}/QAHelper-mac-universal.zip"

app_version="$(unzip -p "${zip_to_read}" 'ExecHelper.app/Contents/Java/QA_Helper.jar' 2>/dev/null \
    | unzip -p /dev/stdin '*/qa-helper-version.txt' 2>/dev/null \
    | head -1)"

# Fallback: read from source
if [[ -z "${app_version}" ]]; then
    app_version="$(head -1 "${PROJECT_PATH}/src/Resources/qa-helper-version.txt" 2>/dev/null)"
fi

if [[ -z "${app_version}" ]]; then
    >&2 echo '!!! Could not determine app version — aborting !!!'
    exit 2
fi

echo "Deploying ExecHelper version ${app_version} to files.executiveundertakings.com/qa-updates/"
echo ""

read -rp "Confirm deploy? [y/N] " confirm
if [[ "${confirm}" != 'y' && "${confirm}" != 'Y' ]]; then
    echo 'Aborted.'
    exit 0
fi

# ── Copy files into qa-updates folder ────────────────────────────────────────
mkdir -p "${QA_UPDATES_DIR}"

echo "${app_version}" > "${QA_UPDATES_DIR}/latest-version.txt"
echo "  ✓ latest-version.txt → ${app_version}"

for zip_name in 'QAHelper-mac-ElCapitan.zip' 'QAHelper-mac-universal.zip' \
                'QAHelper-windows-installer.zip' 'QAHelper-linux-installer.zip'; do
    if [[ -f "${DIST_PATH}/${zip_name}" ]]; then
        cp "${DIST_PATH}/${zip_name}" "${QA_UPDATES_DIR}/${zip_name}"
        echo "  ✓ ${zip_name}"
    fi
done

# Extract app into qa-updates/ for direct access
for zip_name in 'QAHelper-mac-ElCapitan.zip' 'QAHelper-mac-universal.zip'; do
    if [[ -f "${QA_UPDATES_DIR}/${zip_name}" ]]; then
        extracted_name="${zip_name%.zip}"
        rm -rf "${QA_UPDATES_DIR}/${extracted_name}"
        ditto -xk "${QA_UPDATES_DIR}/${zip_name}" "${QA_UPDATES_DIR}/${extracted_name}"
        echo "  ✓ Extracted ${extracted_name}/ExecHelper.app"
    fi
done


echo ""
echo "✓ Files copied to qa-updates/."
echo "  The deploy agent will publish them to files.executiveundertakings.com within ~2 minutes."
echo ""
echo "  To deploy immediately, run:"
echo "    python3 ~/Documents/deploy-public-files.py"
echo ""
echo "  Verify live at:"
echo "    curl https://files.executiveundertakings.com/qa-updates/latest-version.txt"
