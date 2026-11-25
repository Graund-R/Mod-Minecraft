"""Original spatial fold/release: filtered inward breath, then a soft resonant opening."""
from pathlib import Path
import math,random,struct,subprocess,tempfile,wave
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/pocketmod/sounds'
rate=22050
for name,duration in [('teleport_fold',.23),('teleport_unfold',.29)]:
    rng=random.Random(name);low=0.;values=[]
    for i in range(int(rate*duration)):
        t=i/rate;u=t/duration;n=rng.uniform(-1,1);low=.94*low+.06*n
        edge=max(0,min(1,t/.006,(duration-t)/.018))
        if name=='teleport_fold':
            envelope=math.sin(math.pi*u)**.65*(.25+.75*u)
            value=(.23*low+.11*(n-low)+.08*math.sin(2*math.pi*(260*t+1300*t*t)))*envelope
        else:
            value=(.24*math.sin(2*math.pi*(140*t-155*t*t))+.10*low)*math.exp(-t*17)
            value+=.08*(n-low)*math.sin(math.pi*u)**1.8
        values.append(int(max(-.8,min(.8,value*edge))*32767))
    with tempfile.TemporaryDirectory() as temp:
        source=Path(temp)/'fold.wav'
        with wave.open(str(source),'wb') as wav:
            wav.setnchannels(1);wav.setsampwidth(2);wav.setframerate(rate);wav.writeframes(struct.pack('<'+'h'*len(values),*values))
        subprocess.run(['ffmpeg','-loglevel','error','-y','-i',str(source),'-c:a','libvorbis','-q:a','5',str(root/(name+'.ogg'))],check=True)
    print(name,duration)
