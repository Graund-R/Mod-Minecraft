"""Dash presentation checks against a compiled Forge MDK. Does not launch Minecraft."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
repo=Path(__file__).resolve().parents[1]
mdk=Path(sys.argv[1]).resolve()
cache=Path(os.environ.get('GRADLE_USER_HOME',str(Path.home()/'.gradle')))/'caches'
forge=next((cache/'forge_gradle/minecraft_user_repo').rglob('*mapped_snapshot*.jar'))
classpath=os.pathsep.join(map(str,[mdk/'build/classes/java/main',forge,*sorted((cache/'modules-2/files-2.1').glob('*/*/*/*/*.jar'))]))
def tool(name):return str(Path(os.environ['JAVA_HOME'])/'bin'/name) if 'JAVA_HOME' in os.environ else name
with tempfile.TemporaryDirectory(prefix='dash-checks-') as target:
    sources=sorted((repo/'tests/java/pocketmod/dash').glob('*.java'))
    result=subprocess.run([tool('javac'),'-encoding','UTF-8','-cp',classpath,'-d',target,*map(str,sources)])
    if result.returncode:sys.exit(result.returncode)
    for name in ['GlQueryBufferSmoke','DashPathSmoke','DashPresentationSmoke']:
        result=subprocess.run([tool('java'),'-Xverify:all','-cp',target+os.pathsep+classpath,'pocketmod.'+name])
        if result.returncode:sys.exit(result.returncode)
