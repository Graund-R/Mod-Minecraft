"""Run bloodthirst logic checks against a compiled Forge MDK, without starting Minecraft.
Usage: JAVA_HOME=/path/to/jdk8 python3 tests/run-headless.py /path/to/mdk
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

repo = Path(__file__).resolve().parents[1]
mdk = Path(sys.argv[1]).resolve()
cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches'
forge = next((cache / 'forge_gradle/minecraft_user_repo').rglob('*mapped_snapshot*.jar'))
classpath = os.pathsep.join(map(str, [
    mdk / 'build/classes/java/main', mdk / 'build/resources/main', forge,
    *sorted((cache / 'modules-2/files-2.1').glob('*/*/*/*/*.jar')),
]))
java_home = os.environ.get('JAVA_HOME')
def java_tool(name):
    return str(Path(java_home) / 'bin' / name) if java_home else name
with tempfile.TemporaryDirectory(prefix='pocketmod-checks-') as target:
    subprocess.run([java_tool('javac'), '-encoding', 'UTF-8', '-cp', classpath,
                    '-d', target, str(repo / 'tests/java/pocketmod/BloodthirstSmoke.java')], check=True)
    subprocess.run([java_tool('java'), '-cp', target + os.pathsep + classpath,
                    'pocketmod.BloodthirstSmoke'], check=True)
