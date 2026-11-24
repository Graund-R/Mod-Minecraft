# Pocket Mod

Minecraft 1.12.2 / Forge. The working source of truth is `main` in Graund-R/Mod-Minecraft.

## Delivering builds

- Commit completed source changes and increment `version.txt` for each new published build.
- The GitHub Actions workflow builds main and publishes a release after success. Do not manually upload a JAR, make a separate download branch, or ask the user to request publication for each build.
- Before reporting delivery success, verify the Actions run, release version and JAR. Give the user this stable mobile download URL: https://github.com/Graund-R/Mod-Minecraft/releases/latest/download/pocketmod.jar
- If a build fails, fix it or report the actual failure; do not claim the permanent link contains the new version until it is verified.
- Published version tags are not overwritten. Pull requests and other branches build without publishing.
