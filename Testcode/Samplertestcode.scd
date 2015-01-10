(
s.waitForBoot{
a = "/Users/mac/Documents/UIUC/Algorythm Music 3/Samples/Tubular/*.aiff".pathMatch;
t = Sampler(\tublar).load(a);
}
)

t.samples;
t.key([90, 30]);
t.key([90, 30, 60, 70, 80], \percussive);
t.key([90, 30], \nosorting);

t.samples[0].attackDur;
t.samples[0].sampleRate;
t.samples[0].bufferServer.sampleRate;
t.samples[0].keynum;
t.key(71, texture: 1)

t.samples;
t.filenames;

//If you lost your database, you can always find it back
SamplerDB.dbs;
SamplerDB.dbs[\default].samplers;
t = SamplerDB.dbs[\default].samplers[\tublar];
t.db;



~buf = t.getPlaySamples(90)[0][0].activeBuffer[0];
x = Synth(\key, [buf: ~buf])


t.keyRanges;
t.setKeyRanges(\keynumRadious, 5)


p = t.getPlaySamples([73, 75]);


(
a = (Platform.resourceDir +/+ "sounds/a11wlk01.wav").pathMatch;
f = Sampler(\alva).load(a);
)

f.samples[0].keynum;
f.setKeyRanges(infoArray: 3);
f.getPlayTime(f.getPlaySamples(70);).flop[3];
f.key(70, \percussive);

(
s.waitForBoot{
a = "/Volumes/My Sounds/My Samples/Processed/Long/Reel Palindruome.aif".pathMatch;
g = Sampler(\reel).load(a)
}
)

g.samples[0].startTime;
g.samples[0].peakTime;
g.samples[0].attackDur;
g.samples[0].activeDuration;
g.samples[0].sampleRate;
g.samples[0].activeBuffer[0].plot;
g.samples[0].keynum;
g.key([74, 37])
g.key([70, 37, 30, 100], \percussive)
g.key([70, 37], \nosorting)