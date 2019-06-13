(
s.waitForBoot{
	a = (Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Tubular/*.aiff").pathMatch;
	t = Sampler(\tublar).load(a, filenameAsKeynum: true);
}
)

// play a note by midi key number
t.key(81);
// play random note
t.key;
t.gui;
t.samples[1].keynum;
t.keyRanges;

t.key(0, amp: 2.5);

Sampler(\tublar).key;


SS_SCMIR.framehop;

// play a chord with an array
t.key([30, 40, 50, 60, 70, 80]);
t.key([30, 40, 50, 60, 70, 80], \nosorting);
t.key([30, 40, 50, 60, 70, 80], \percussive);
t.key([30, 40, 50, 60, 70, 80], \stratchshort);


t.key([30, 40, 50, 60, 70, 80], [\peakat, 2]);

// pitch bend with an x-y envelope array
t.key([60, 66], bendenv: [0, 1, 0.2, 1, 1, 3]);

// granular expand
t.key(60, expand: 2);
t.key(60, pan: -1)

// amplitude envelope
t.key(60, ampenv: [0, 0, 0.5, 5, 1, 0]);
t.key(60, ampenv: Env());


t.key(60, panenv: [0,-1, 0.5, 1, 1, -1]);

// negative key number plays hte sound backword.
t.key(-60);
t.key(-60, dur: 0.1);

// fit the soudn into an envelope
t.playEnv(Env());

// show playing samplers
SamplerQuery.playing[0];


// Combining positive and negative key number for a gesture
t.key([-60, 60]);
t.key([-60, 60], dur: 1);
t.key([-60, 60], expand: 2);
t.key([-60, 60], bendenv: [0, 1, 0.2, 1, 1, 3], ampenv: Env([3,1,3]), panenv: Env());
t.key([-60, 60], expand: 2, bendenv: [0, 1, 0.2, 1, 1, 3])


t.key([-40, 30], ampenv: [0, 0, 0.5, 5, 1, 0]);
t.key([-60, 60], panenv: Env([-1, 1, -1]));

t.key([-60, 60], [\peakat, 1], panenv: Env([-1, 1, -1]));

t.key([-60, 60], [\peakat, 5], bendenv: [0, 1, 0.2, 1, 0.5, 3, 1, 0.5], panenv: Env([-1, 1, -1]));

t.key([30, 40, -50, 50, -60, 60, 70, 80], [\peakat, 5], ampenv: Env([3,0,3]))
t.key([30, 40, -50, 50, -60, 60, 70, 80], [\startats, 1])

t.key([70, 81], \stratchshort);
t.key([70, 81], \percussive);
t.key([-81], \percussive);  //Should be no sound.
t.key([30, 81], [\percussive, 1]);
t.key([30, 81], \stratchshort);
t.key([30, 81], [\nosorting, 1]);

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
t.samples[0].buffer;
t.samples[0].mfcc;

t.averageDuration;
t.averageTemporalCentroid;
t.averageMFCC;

t.samples.do{|thisSample| thisSample.peakTime.postln};
t.samples.do{|thisSample| thisSample.activeDuration.postln};

t.samples[0].keynum;
t.filenames;

t.numActiveBuffer;

t.samples[0].play;

//If you lost your database, you can always find it back
SamplerDB.dbs;
SamplerDB.dbs[\default].samplers;
t = SamplerDB.dbs[\default].samplers[\tublar];
t.db;

t.keyRanges;
t.setKeyRanges(\keynumRadious, 5)

t.playEnv(Env(), [70, 80, 90]);
t.playEnv(Env(), {rrand(70, 90)});
t.playEnv(Env(), {rrand(70, 90)}, dur: 0.05);

t.playEnv(Env([1,0,1], [1,1]), {rrand(70, 90)});

t.playEnv(~cageenv, {rrand(100, 127.0)});


/////////////////////////////////////////////////////////////////////////////
(
s.waitForBoot{
f = Sampler(\cage, \speak).load([Quarks.folder ++ "/SuperSampler/Testcode/Sounds/cage.wav"]);
};
)

f.keynums;
f.setKeynums([[20, 30, 40, 50, 60, 70, 80, 90, 100]]);
f.gui;

~cageenv = f.samples[0].env.normalize(1);
~cageenv.plot;

~cageenv.invert.test;
~cageenv.reverse.test;

~cageenv.reciprocal.plot;


(
~cageenv.test;
f.samples[0].play;
)


~cageenv.duration;
f.samples[0].duration;


f.samples[0].keynum;
f.samples[0].play();
f.setKeyRanges(infoArray: 3);
f.numActiveBuffer;
f.averageDuration;
f.averageTemporalCentroid;

f.key;
f.key(60);
f.key(60, expand: 10);
f.key(60, [\peakat, 3]);

f.key(34);
f.key(34, expand: 2);
f.key(34, expand: 10, bendenv: [0, 1, 0.3, 1, 1, 3]);
f.key(1)
f.key(-60);

f.key([70, 30, 60], [\peakat, 3], expand: 10, amp: 0.5);

f.key([70, 30, 60], \percussive);
f.key([70, 30, 60], \nosorting)

f.samples[0].play(pan: 0);


f.playEnv(~cageenv, {[20, 30, 40, 50, 60, 70, 80, 90, 100].choose});

/////////////////////////////////////////////////////////////////////////////
(
s.waitForBoot{
	a = (Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Reel Palindruome_44.1kHz_int16.aiff").pathMatch;
	g = Sampler(\reel).load(a);
}
)




g.dbs.at(\default).key([40, 80]);

g.key(g.samples[0].keynum) //original sound
g.key(40);
g.key;
g.key(60, \percussive, dur: 0.01)
g.key(60, dur: 1);
g.key(60, [\peakat, 1], expand: 2);
g.key([60, 70], expand: 2);
g.key(40, ampenv: [0,1, 1, 0]);




g.key(60, expand: 7, bend: [0, 1, 0.3, 1, 1,  0.3]);

g.key([70, 30, 50, 40, 60], \nosorting);

g.key([70, 30, 50, 40, 60], dur: 1);


g.key([70, 30, 50, 40, 60], [\peakat, 5])


g.key([70, 30, 50, 40, 60], \percussive, texture: 0)
g.key([70, 30, 50, 40, 60], \stratchshort)
g.key([70, 30, 50, 40, 60], \stratchshort, expand: 5, bendenv: [0, 1, 0.3, 1, 1,  0.3]);

g.samples[0].startTime;
g.samples[0].peakTime;
g.samples[0].attackDur;
g.samples[0].activeDuration;
g.samples[0].sampleRate;
g.bufServer.sampleRate;
g.samples[0].activeBuffer[0].plot;
g.samples[0].play;


g.getPlaySamples(SamplerArguments.new.set(keynums: [60, 80, 50]));

(
g.playEnv(Env(), {rrand(70, 90)});
g.playEnv(Env(), {rrand(30, 40)});
)

g.playEnv(Env([0,1,0,1,0], [1,1,1,1]), {rrand(30, 50)});
g.playEnv(Env([1,0,1,0,1]), {rrand(30, 50)});


(
g.playEnv(~cageenv, {rrand(70,90)});
f.samples[0].play;
)

/////////////////////////////////////////////////////////////////////////////

(
s.waitForBoot{
	a = (Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Nickel 2.wav").pathMatch;
	i = Sampler(\nickel).load(a);
}
)

i.key;
i.key(30, expand: 30);
i.key({rrand(10, 20)});

i.key([-40, 10]);

i.numActiveSample;
i.averageDuration;
i.samples[0].activeBuffer.size;
i.averageDuration;
i.averageTemporalCentroid;


x = i.key({rrand(10, 30)});
x.keynums;

Sampler.playArgs(x);

i.key([10, 40], expand: 60, ampenv: ~cageenv.normalize);


Env().plot;
i.playEnv(Env(), {rrand(10, 40)});

~cageenv.plot;
(
i.playEnv(~cageenv.normalize, {rrand(10, 40)});
Sampler(\cage).samples[0].play;
)


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
d.samplers.keys.asArray.scramble;
d.remove(\tublar);
d.add(\tublar, t);

d.key([-60, 60], numSampler: 2);
d.kdTree.asArray;

d.playEnv(env: Env(), morph: [2, 0, \atpeak], diversity: 6);

SamplerDB(\default).playEnv(env: Env([0,1,0,1,0]), morph: 2, diversity: 6);
SamplerDB(\default).playEnv(Env());


(
SamplerDB(\default).playEnv(env: ~cageenv, samplerThickness: 7, morph: [2, 0]);
Sampler(\cage).samples[0].play;
)

~cageenv.peakTime;