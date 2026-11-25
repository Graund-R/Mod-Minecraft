"""Compile and run the affected gameplay regressions without starting Minecraft."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
repo=Path(__file__).resolve().parents[1]
mdk=Path(sys.argv[1]).resolve()
cache=Path(os.environ.get('GRADLE_USER_HOME',str(Path.home()/'.gradle')))/'caches'
forge=next((cache/'forge_gradle/minecraft_user_repo').rglob('*mapped_snapshot*.jar'))
cp=os.pathsep.join(map(str,[mdk/'build/classes/java/main',mdk/'build/resources/main',forge,*sorted((cache/'modules-2/files-2.1').glob('*/*/*/*/*.jar'))]))
def tool(name):return str(Path(os.environ['JAVA_HOME'])/'bin'/name) if 'JAVA_HOME' in os.environ else name
names=['BloodthirstSmoke','TrialPlacementSmoke','CyclicThirstSmoke','CyclicThirstIntegrationSmoke','WorldCreationSmoke','EscapeAndFlightSmoke','TerrainInfectionSmoke','ExileToolsSmoke','ExileBedsSmoke']
with tempfile.TemporaryDirectory(prefix='pocket-gameplay-') as target:
    subprocess.run([tool('javac'),'-encoding','UTF-8','-cp',cp,'-d',target,*[str(repo/'tests/java/pocketmod'/str(name+'.java')) for name in names]],check=True)
    for name in names[2:]:
        subprocess.run([tool('java'),'-Xverify:all','-cp',target+os.pathsep+cp,'pocketmod.'+name],check=True)
