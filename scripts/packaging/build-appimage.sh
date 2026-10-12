#!/usr/bin/env bash
# Wraps the app-image jpackage produced into an AppImage.
#
# jpackage builds DMG, MSI, DEB and RPM but not AppImage, and AppImage is how a Linux user who is not on
# a Debian derivative installs a desktop app. The app-image jpackage leaves behind is a self-contained
# directory, which is exactly what appimagetool wants, so this only has to add the desktop entry, the
# icon and the AppRun script that AppImage expects.
set -euo pipefail

APP_NAME="Servante"
APP_ID="app.servante"
DISTRIBUTABLE="app/build/compose/binaries/main/app/${APP_NAME}"
STAGING="app/build/appimage/${APP_NAME}.AppDir"
OUTPUT_DIR="app/build/appimage"
TOOL="build/appimage-tool/appimagetool"

if [[ ! -d "${DISTRIBUTABLE}" ]]; then
  echo "No app image at ${DISTRIBUTABLE}. Run ./gradlew :app:createDistributable first." >&2
  exit 1
fi

rm -rf "${STAGING}"
mkdir -p "${STAGING}" "${OUTPUT_DIR}"
cp -a "${DISTRIBUTABLE}/." "${STAGING}/"

cat > "${STAGING}/AppRun" <<'LAUNCHER'
#!/usr/bin/env bash
HERE="$(dirname "$(readlink -f "${0}")")"
exec "${HERE}/bin/Servante" "$@"
LAUNCHER
chmod +x "${STAGING}/AppRun"

cat > "${STAGING}/${APP_ID}.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=${APP_NAME}
Comment=The open source toolbox for Android and Kotlin developers
Exec=Servante
Icon=${APP_ID}
Categories=Development;
Terminal=false
DESKTOP

# AppImage requires an icon at the AppDir root named after the desktop entry.
ICON_SOURCE="app/src/main/resources/icons/servante.png"
if [[ -f "${ICON_SOURCE}" ]]; then
  cp "${ICON_SOURCE}" "${STAGING}/${APP_ID}.png"
else
  echo "No icon at ${ICON_SOURCE}; the AppImage will use the system default." >&2
fi

ARCH="$(uname -m)" "${TOOL}" --no-appstream "${STAGING}" \
  "${OUTPUT_DIR}/${APP_NAME}-$(uname -m).AppImage"

echo "Wrote ${OUTPUT_DIR}/${APP_NAME}-$(uname -m).AppImage"
