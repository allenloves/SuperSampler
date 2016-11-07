(
s.waitForBoot{
	a = (Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Tubular/a5.aiff").pathMatch;
t = Sampler(\tublar).load(a);
}
)


t.key(rrand(50.0, 80.0));

t.key([60, 65], [\peakat, 1])
t.key(60, bend: [0, 1, 0.2, 1, 1, 3])
t.key(60, expand: 10);
t.key(60)
t.key(-60)
t.key(-60, [\peakat, 1])
t.key([-60, 60])
t.key([30, 40, 50, 60, 70, 80], bend: [0, 1, 0.2, 1, 1, 3])
t.key([-60, 60], [\peakat, 1])


t.key([40, 30], ampenv: [0, 0, 0.5, 5, 1, 0]);

t.key([69, 81], expand: 10);

t.key([30, 40, -50, 50, -60, 60, 70, 80], [\peakat, 3])

t.key([70, 81]);
t.key([-81], \percussive);  //Should be no sound.
t.key([30, 81], [\peakat, 1]);
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
t.samples[0].numChannels;
t.samples[0].buffer.numChannels;
t.key(71)

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
s.waitForBoot{
a = (Platform.resourceDir +/+ "sounds/a11wlk01.wav").pathMatch;
f = Sampler(\alva, \speak).load(a);
};
)

f.keynums;
f.samples[0].keynum;
f.samples[0].play;
f.setKeyRanges(infoArray: 3);
f.getPlayTime(f.getPlaySamples(70);).flop[3];
f.key(60);
f.key(60, expand: 10);


f.key(34);
f.key(34, expand: 10);
f.key(34, expand: 10, bend: [0, 1, 0.3, 1, 1, 3]);
f.key(1)
f.key(-60);

f.key([70, 30, 60], [\peakat, 3], expand: 10);

f.key([70, 30, 60], \percussive);
f.key([70, 30, 60], \nosorting)

~alvaenv = f.samples[0].env;

f.playEnv([70, 30, 60], Env());

/////////////////////////////////////////////////////////////////////////////
(
s.waitForBoot{
	a = (Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Reel Palindruome_44.1kHz_int16.aiff").pathMatch;
g = Sampler(\reel).load(a)
}
)

g.key(g.samples[0].keynum) //original sound
g.key(60);
g.key(60, \percussive, dur: 0.01)
g.key(60, dur: 0.2)
g.key(60, expand: 10);
g.key(60, expand: 0.5);
g.key(40, ampenv: [0,1, 1, 0]);

g.key(60, expand: 7, grainRate: 100, bend: [0, 1, 0.3, 1, 1,  0.3]);

g.key([70, 30, 50, 40, 60])
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


/////////////////////////////////////////////////////////////////////////////

(
s.waitForBoot{
	a = (Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Nickel 2.wav").pathMatch;
	i = Sampler(\nickel).load(a);
}
)

i.key(10);
i.key(30, expand: 30);

i.key([10, 40, 80]);





/////////////////////////////////////////////////////////////////////////////

(
s.waitForBoot{
	a = (Quarks.folder ++ "/SuperSampler/Testcode/Sounds/onsettest.wav").pathMatch;
	h = Sampler(\clicks).load(a);
}
)


h.key([ 76, 95, 29], expand: 20 );

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
d.samplers;
d.playEnv([60, 90, 50], Env());
d.playEnv([60, 90, 50], ~alvaenv);
