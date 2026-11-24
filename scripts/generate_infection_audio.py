"""Original low drones, unstable harmonics, dry whispers and crackle. Requires ffmpeg."""
from pathlib import Path
import math, random, struct, subprocess, tempfile, wave
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/pocketmod/sounds'
root.mkdir(parents=True,exist_ok=True)
rate=22050
for name,duration in [('infection_wind',12),('infection_whisper',4),('infection_surge',3)]:
    rng=random.Random(name);low=0.0;values=[];crack=0.0
    for i in range(int(duration*rate)):
        t=i/rate;noise=rng.uniform(-1,1);low=.975*low+.025*noise
        fade=min(1,t/.8,(duration-t)/1.1);fade=max(0,fade)**1.5
        # Detuned low harmonics beat against one another; no wash of filtered surf.
        drone=(math.sin(2*math.pi*47*t)+.65*math.sin(2*math.pi*49.1*t)+.35*math.sin(2*math.pi*71.3*t))*.16
        crack=crack*.87+(rng.uniform(-.5,.5) if rng.random()<.0008 else 0)
        if name=='infection_wind':
            value=drone*(.7+.3*math.sin(t*1.7))+crack*.45+math.sin(2*math.pi*(173*t+.6*math.sin(t*.8)))*.025
        elif name=='infection_whisper':
            syllables=max(0,math.sin(t*13)*math.sin(t*3.1))**2
            # Band-modulated breath plus wavering voice-like partials, without intelligible speech.
            value=(noise-low)*.19*syllables+(math.sin(2*math.pi*(137*t+1.8*math.sin(t*2.4)))+.4*math.sin(2*math.pi*293*t))*.075*syllables+drone*.35+crack
        else:
            value=(math.sin(2*math.pi*(81*t-7*t*t))*.24+math.sin(2*math.pi*(129*t-9*t*t))*.12)*math.exp(-t*.7)+crack*.4
        values.append(int(max(-.85,min(.85,value*fade))*32767))
    with tempfile.TemporaryDirectory() as tmp:
        source=Path(tmp)/'sound.wav'
        with wave.open(str(source),'wb') as wav:
            wav.setnchannels(1);wav.setsampwidth(2);wav.setframerate(rate)
            wav.writeframes(struct.pack('<'+'h'*len(values),*values))
        subprocess.run(['ffmpeg','-loglevel','error','-y','-i',str(source),'-c:a','libvorbis','-q:a','4',str(root/(name+'.ogg'))],check=True)
    print(name,duration,'seconds')
