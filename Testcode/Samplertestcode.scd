a = Sampler.new(\werr);
a.db(\werr);



(
a = "/Users/mac/Documents/UIUC/Algorythm Music 3/Samples/Tubular/*.aiff".pathMatch;
t = Sampler(\tublar).load(a);
)

t.samples;
t.filenames;

//If you lost your database, you can always find it back
SamplerDB.dbs;
t = SamplerDB.dbs[\default].samplers[\tublar];

t.db;



b = t.getPlaySamples(90)[0][0].activeBuffer[0];
x = Synth(\key, [buf: b])


t.keyRanges;
t.setKeyRanges(\keynumRadious, 5)


p = t.getPlaySamples([73, 75]);


t.key([90, 30]);

t.samples[0].keynum;
t.key(71, texture: 1)

