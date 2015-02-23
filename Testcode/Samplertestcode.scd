(
s.waitForBoot{
	a = (Platform.userExtensionDir ++ "/SuperSampler/Testcode/Tubular/*.aiff").pathMatch;
t = Sampler(\tublar).load(a);
}
)


t.key([30, 81]);

t.key([69, 81]);
t.key([30, 81], \percussive);
t.key([30, 81], \nosorting);

t.setKeyRanges(\keyNumOnly);

t.samples[0].startTime;
t.samples[0].startIndex * SCMIR.framehop;
t.samples[0].peakTime;
(t.samples[0].peakIndex + 0.5) * SCMIR.framehop;
t.samples[0].attackDur;
t.samples[0].sampleRate;
t.samples[0].bufferServer.sampleRate;
t.samples[1].keynum;
t.key(71, texture: 1)

t.samples.do{|thisSample| thisSample.peakTime.postln};

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



/////////////////////////////////////////////////////////////////////////////
(
a = (Platform.resourceDir +/+ "sounds/a11wlk01.wav").pathMatch;
f = Sampler(\alva, \speak).load(a);
)

f.samples[0].keynum;
f.samples[0].play;
f.setKeyRanges(infoArray: 3);
f.getPlayTime(f.getPlaySamples(70);).flop[3];
f.key([70, 30, 60]);
f.key([70, 30, 60], \percussive);
f.key([70, 30, 60], \nosorting)

~alvaenv = f.samples[0].env;

f.playEnv([70, 30, 60], Env());

/////////////////////////////////////////////////////////////////////////////
(
s.waitForBoot{
	a = (Platform.userExtensionDir ++ "/SuperSampler/Testcode/Reel Palindruome_44.1kHz_int16.aiff").pathMatch;
g = Sampler(\reel).load(a)
}
)



g.key(38.033508618673)

g.key([70, 30, 50, 40, 60])

g.key([70, 30, 50, 40, 60], [\startat, 5], texture: 1)
g.key([70, 30, 50, 40, 60], [\peakat, 1])

g.key([70, 30, 50, 40, 60], \percussive, texture: 0)

g.key([70, 30, 50, 40, 60], \nosorting)

g.samples[0].startTime;
g.samples[0].peakTime;
g.samples[0].attackDur;
g.samples[0].activeDuration;
g.samples[0].sampleRate;
g.bufServer.sampleRate;
g.samples[0].activeBuffer[0].plot;
g.samples[0].keynum;

/////////////////////////////////////////////////////////////////////////////

(
s.waitForBoot{
	a = (Platform.userExtensionDir ++ "/SuperSampler/Testcode/onsettest.wav").pathMatch;
	h = Sampler(\clicks).load(a);
}
)


h.key([ 76, 95, 29] )

h.samples[0].temporalCentroid;
h.key([60, 50]);
h.playEnv([60, 90, 50], Env());


//////////////////////////////////////////////////////////////////////////////
//If you lost the pointer to your database, you can always find it back
SamplerDB.dbs;
SamplerDB.dbs[\default].samplers;
g = SamplerDB.dbs[\default].samplers[\reel];
g.db;



d = SamplerDB.dbs[\default];
d.playEnv([60, 90, 50], Env());
d.playEnv([60, 90, 50], ~alvaenv);
