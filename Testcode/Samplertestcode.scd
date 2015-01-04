a = Sampler.new(\werr);
a.db(\werr);



(
a = "/Users/mac/Documents/UIUC/Algorythm Music 3/Samples/Tubular/*.aiff".pathMatch;
t = Sampler(\tublar).load(a);
)



t.load(a, filenameAsKeynum: false);
t.samples;
t.filenames;

SamplerDB.dbs;

t.db;

b = t.getPlaySamples(66)[0][0].activeBuffer[0];
x = Synth(\key, [buf: b])


t.keyRanges;
t.setKeyRanges(\keynumRadious, 5)


t.getPlaySamples(73);
t = SamplerDB.dbs[\default].samplers[\tublar];

t.key([90, 30]);

t.samples[0].keynum;
t.key(71, texture: 1)

