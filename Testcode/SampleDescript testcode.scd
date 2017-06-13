
//////////////////////////////
// Use GUI to show samples

w = SampleDescript.gui;
~temp.peakAmp;

///////////////////////////////////////////////////////
d = SampleDescript(Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Nickel 2.wav");
d.peakAmp;
d.globalPeakAmp;
d.rmsData.maxIndex;
d.rmsData.plot;
d.peakAmp;
d.peakTime;
d.peakIndex;
d.sampleRate;
d.numChannels;
d.keynum;
d.mfcc;

d.activeBuffer.size;

d.activeEnv.size;

SCMIR.hoptime;

d.sectionBreakPoint;
d.rmsData.maxIndex;
d.rmsData.maxIndex * SCMIR.hoptime;
d.rmsData.plot;

////////////////////////////////////////////////////////////////////
a = SampleDescript(Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Reel Palindruome_44.1kHz_int16.aiff", loadToBuffer: true)

a.arEnv(startThresh: 0.1, endThresh: 0.1);
a.filename;
a.duration;
a.play;
a.play(0);

a.numChannels; // This is a mono file
a.buffer; // Buffer is stored in an array of each mono channels
a.activeBuffer; // See the array structure
a.activeBuffer[0][0].plot; // Buffer sections is also stored in an array of each mono channels

a.rmsData.plot;
a.numChannels;

a.onsetTime;
a.onsetIndex;
a.sectionBreakPoint;

a.peakIndex;
a.peakAmp;

a.startIndex
a.startIndex * SCMIR.framehop / a.sampleRate;
a.startIndex *SCMIR.hoptime;
a.startTime;

//values below are supposed to be all equal
(a.activeRMSData[0].size - 1) * SCMIR.hoptime;
a.activeBuffer[0].duration;
a.activeDuration;
a.endTime-a.startTime;
a.attackDur+a.releaseDur;


a.endIndex * SCMIR.hoptime;
a.endTime;

a.env.plot;

a.env.peakTime;
a.peakTime;


(
{
	var env = a.env;
	Out.ar(1, SinOsc.ar(LFNoise0.kr(200, mul: 300, add: 700), mul: EnvGen.kr(env, doneAction: 2)))
}.play;
a.play;
)

/////////////////////////////////////////////////////////////////////////////////////////

f = SampleDescript(Quarks.folder ++ "/SuperSampler/Testcode/Sounds/onsettest.wav")
f.loadToBuffer;
f.numChannels; //This is a stereo file
f.buffer[1].plot; // All stereo files are loaded into an array of two mono buffers
f.activeBuffer[2][0].plot; // All stereo sections are also loaded into subarrays of two mono buffers
f.activeBuffer;
f.play;
f.play(2);
f.numChannels;
f.sampleRate;
f.duration;
f.mirDataByFeatures;
f.rmsData.plot;
f.onsetTime;
f.sectionBreakPoint;
f.globalPeakIndex;
f.globalPeakAmp;
f.frameTimes;
f.globalPeakTime;
f.onsetIndex;
f.peakIndex;
f.rmsDataBySection.plot;
f.startTime;
f.peakTime;
f.endTime;
f.attackDur;
f.pitchData.flop.plot;
f.activeEnv.plot;
f.activeDuration;
f.activeCentroidData;
f.temporalCentroid;
f.keynum;


////////////////////////////////////////////////////////////////////////////////////////////
g = SampleDescript(Quarks.folder ++ "/SuperSampler/Testcode/Sounds/3-04 Impacts-Hollow Impacts 5 Versio.wav")
g.arEnv(startThresh: 0.05, endThresh: 0.05);
g.keynum;
g.play;
g.play(0);
g.rmsData.plot;
g.numChannels;
g.mfcc.size;

g.onsetIndex;
g.sectionBreakPoint;
g.startIndex;
g.peakIndex;
g.attackDur;
g.startTime;
g.peakTime;
g.endTime;
g.centroidData;
g.temporalCentroid;
g.activeCentroidData.plot;
g.activeDissonanceData.plot;
g.getKeyNum;
g.keynumFromPitchFound;
g.keynumFromFileName;
g.keynum;
g.loadToBuffer;
g.buffer.numChannels;
g.activeBuffer[2].numChannels; //This is a stereo buffer
g.activeBuffer[2][0].play;  // All stereo buffers are separated into two mono buffers

g.env.plot;
g.activeEnv.plot

///////////////////////////////////////////////////////////////////////////////////////////
e = SampleDescript(Platform.resourceDir +/+ "sounds/a11wlk01.wav", loadToBuffer: false)
e.play; //automatically load to buffer when you call play
e.buffer;
e.startTime;
e.play(4);
e.sampleRate;
e.onsetIndex;
e.file.onsetdata;
e.startIndex;
e.endIndex;
e.temporalCentroid;
e.peakTime;
e.attackDur;
e.env.plot;
e.env.peakTime;
e.activeEnv.plot;
e.pitchData.flop.plot;
e.activeCentroidData;
e.activeDissonanceData.plot;
e.fileNameAsNote;
e.keynum;
e.loadToBuffer;

e.temporalCentroid.plot;

e.activeBuffer[1].plot;

(
{
	var env = e.env;
	Out.ar(1, SinOsc.ar(LFNoise0.kr(300, mul: 100, add: 800), mul: EnvGen.kr(env, doneAction: 2)))
}.play;
//e.play;
)


//////////////////////////////////////////////////////////////
m = SampleDescript(Quarks.folder ++ "/SuperSampler/Testcode/Sounds/Crotales/c6.aiff", loadToBuffer: true)
m.frameTimes;
m.play;
m.buffer;
m.activeBuffer;
m.pitchData.postcs;
m.pitchData.flop.plot;
m.activeEnv.plot;
m.getKeyNum;
m.keynumFromPitchFound;
m.keynumFromFileName;
m.keynum;
m.loadToBuffer;
m.buffer.class;
m.free;
m.play;
m.class;

