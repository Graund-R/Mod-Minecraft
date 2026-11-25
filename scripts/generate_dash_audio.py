"""Original deterministic dash sounds: a dry arc, rushing air and a soft low landing."""
from pathlib import Path
import math,random,struct,subprocess,tempfile,wave
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/pocketmod/sounds'
root.mkdir(parents=True,exist_ok=True)
rate=22050
for name,duration in [('dash_snap',.13),('dash_rush',.32),('dash_land',.21)]:
    rng=random.Random(name);low=0.;samples=[]
    for i in range(int(rate*duration)):
        t=i/rate;u=t/duration;noise=rng.uniform(-1,1);low=.88*low+.12*noise
        edge=min(1,t/.004,(duration-t)/.025)
        if name=='dash_snap':
            value=(.21*(noise-low)+.15*math.sin(2*math.pi*(1800*t-3900*t*t)))*math.exp(-t*29)
            value+=.13*(noise-low)*math.exp(-((t-.035)/.005)**2)
        elif name=='dash_rush':
            envelope=math.sin(math.pi*u)**1.5
            value=(.25*(noise-low)+.13*low+.045*math.sin(2*math.pi*(500*t-470*t*t)))*envelope
        else:
            value=(.29*math.sin(2*math.pi*(115*t-115*t*t))+.10*low)*math.exp(-t*21)
            value+=.09*(noise-low)*math.exp(-t*44)
        samples.append(int(max(-.8,min(.8,value*max(0,edge)))*32767))
    with tempfile.TemporaryDirectory() as temp:
        source=Path(temp)/'dash.wav'
        with wave.open(str(source),'wb') as wav:
            wav.setnchannels(1);wav.setsampwidth(2);wav.setframerate(rate);wav.writeframes(struct.pack('<'+'h'*len(samples),*samples))
        subprocess.run(['ffmpeg','-loglevel','error','-y','-i',str(source),'-c:a','libvorbis','-q:a','5',str(root/(name+'.ogg'))],check=True)
    print(name,duration)
