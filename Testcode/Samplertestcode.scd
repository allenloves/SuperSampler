(
s.waitForBoot{
	a = (Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Tubular/*.aiff").pathMatch;
t = Sampler(\tublar, \demo).load(a);
}
)

t.key(81);
t.key;  // play random note
t.key([30, 40, 50, 60, 70, 80])
t.key([30, 40, 50, 60, 70, 80], \nosorting);
t.key([30, 40, 50, 60, 70, 80], \percussive);

t.key([60, 65], [\peakat, 1]);
t.key([60, 66], bend: [0, 1, 0.2, 1, 1, 3]);
t.key(60, expand: 2);
t.key(-60);
t.key(-60, expand: 2);


t.key([-60, 60]);
t.key([-60, 60], expand: 2);
t.key([-60], bend: [0, 1, 0.2, 1, 1, 3]);
t.key([-60, 60, -48, 48], bend: [0, 1, 0.2, 1, 1, 3]);
t.key([-48, 48], bend: [0, 2, 1, 2]);
t.key([-60, 60, -48, 48], bend: [0, 1, 0.2, 1, 0.2, 1.3, 0.4, 1.3, 0.4, 1.6, 0.6, 1.6, 0.6, 2, 1, 2]);


t.key([-60, 60], [\peakat, 1])
t.key([-60, 60], expand: 2, bend: [0, 1, 0.2, 1, 1, 3])



t.key([40, 30], ampenv: [0, 0, 0.5, 5, 1, 0]);

t.key([69, 81], expand: 10);

t.key([30, 40, -50, 50, -60, 60, 70, 80], [\peakat, 3])

t.key([70, 81], \stratchshort);
t.key([-81], \percussive);  //Should be no sound.
t.key([30, 81], [\peakat, 1]);
t.key([30, 81], \stretchshort);
t.key([30, 81], \nosorting);

t.setKeyRanges(\keyNumOnly);


//get information from sample
t.samples[0].startTime;
t.samples[0].startIndex * SCMIR.framehop;
t.samples[0].peakTime;
(t.samples[0].peakIndex + 0.5) * SCMIR.framehop;
t.samples[0].attackDur;
t.samples[0].sampleRate;
t.samples[0].bufferServer.sampleRate;
t.samples[0].keynum;
t.samples[0].numChannels;
t.samples[0].buffer.numChannels;

t.samples.do{|thisSample| thisSample.peakTime.postln};

t.samples[0].keynum;
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
f = Sampler(\alva, \speak).load(a);  // The 2nd parameter indicates another SamplerDB database
};
)


~alvaenv = f.samples[0].env;
~alvaenv.plot;

~alvaenv.reciprocal.plot;

f.keynums;
f.samples[0].keynum;
f.samples[0].play;
f.setKeyRanges(infoArray: 3);
f.getPlayTime(f.getPlaySamples(70);).flop[3];
f.key;
f.key(60);
f.key(60, expand: 10);


f.key(34);
f.key(34, expand: 10);
f.key(34, expand: 10, bend: [0, 1, 0.3, 1, 1, 3]);
f.key(1)
f.key(-60);

f.key([70, 30, 60], [\peakat, 3], expand: 10, amp: 0.5);

f.key([70, 30, 60], \percussive);
f.key([70, 30, 60], \nosorting)



f.playEnv([70, 30, 60], Env());

/////////////////////////////////////////////////////////////////////////////
(
s.waitForBoot{
	a = (Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Reel Palindruome_44.1kHz_int16.aiff").pathMatch;
g = Sampler(\reel).load(a)
}
)

g.key(g.samples[0].keynum) //original sound
g.key(40);
g.key;
g.key(60, \percussive, dur: 0.01)
g.key(60, dur: 0.2)
g.key(60, expand: 10);
g.key(60, expand: 2);
g.key(40, ampenv: [0,1, 1, 0]);


g.key(60, expand: 7, bend: [0, 1, 0.3, 1, 1,  0.3]);

g.key([70, 30, 50, 40, 60], \nosorting);

g.key([70, 30, 50, 40, 60])
g.key([70, 30, 50, 40, 60], [\peakat, 3])
g.key([70, 30, 50, 40, 60], \percussive, texture: 0)
g.key([70, 30, 50, 40, 60], \stratchshort)
g.key([70, 30, 50, 40, 60], \stratchshort, expand: 5, bend: [0, 1, 0.3, 1, 1,  0.3]);

g.samples[0].startTime;
g.samples[0].peakTime;
g.samples[0].attackDur;
g.samples[0].activeDuration;
g.samples[0].sampleRate;
g.bufServer.sampleRate;
g.samples[0].activeBuffer[0].plot;


g.getPlaySamples(SamplerArguments.new.set(keynums: [60, 80, 50]));


/////////////////////////////////////////////////////////////////////////////

(
s.waitForBoot{
	a = (Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Nickel 2.wav").pathMatch;
	i = Sampler(\nickel).load(a);
}
)

i.key;
i.key(30, expand: 30);

i.key([-40, 10]);
i.key([10, 40]);
i.key([10, 40], expand: 60, ampenv: ~alvaenv.normalize);


Env().plot;
i.playEnv([10, 40], Env());

~alvaenv.plot;
i.playEnv([10, 40], ~alvaenv.normalize);



/////////////////////////////////////////////////////////////////////////////

(
s.waitForBoot{
	a = (Quarks.folder ++ "/SuperSampler/Testcode/Sounds/onsettest.wav").pathMatch;
	h = Sampler(\clicks).load(a);
}
)


h.numChannels;
h.key([ 76, 95, 29], expand: 20 );

h.samples[0].temporalCentroid;
h.key([60, 50]);
h.playEnv([60, 90, 50], Env());


//////////////////////////////////////////////////////////////////////////////
//If you lost the pointer to your database, you can always find it back
SamplerDB.dbs; //list of all sampler databases
SamplerDB.dbs[\default].samplers;  //list of samplers in this database
g = SamplerDB.dbs[\default].samplers[\reel];  //a specific sampler
g.name;
g.db(\reel); //return a list of sampeler in the same database
g.dbs; //an dictionary of pairs of names and SamplerDBs instance where this sampler belongs to


g.dbs.do({|samplerDB, index| samplerDB.samplers.postln});


d = SamplerDB.dbs[\default];
d.samplers;

Env().plot;
d.playEnv([60, 90, 50], Env());

~alvaenv.plot;
d.playEnv([60, 90, 50], ~alvaenv.normalize);
