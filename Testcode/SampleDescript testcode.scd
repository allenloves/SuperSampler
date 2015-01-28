
a = SampleDescript(Platform.userExtensionDir ++ "/SuperSampler/Testcode/Reel Palindruome_44.1kHz_int16.aiff")

a.arEnv(startThresh: 0.1, endThresh: 0.1);
a.filename;
a.duration;
a.play;
a.play(0);
a.activeBuffer[0].duration;
a.activeBuffer[0].write(Platform.userExtensionDir ++ "/SuperSampler/Testcode/Reel Palindruome_activeBuffer_44.1kHz_int16.aiff", sampleFormat: "int16")
a.rmsData.plot;

a.startIndex
a.startIndex * SCMIR.framehop;
a.startIndex *SCMIR.hoptime;
a.startTime;

(a.activeRMSData[0].size - 1) * SCMIR.hoptime;
a.activeDuration;
a.endTime-a.startTime;
a.attackDur+a.releaseDur;

a.endIndex * SCMIR.hoptime;
a.endTime;

SCMIR.framehop;

((a.peakIndex+0.5) * SCMIR.hoptime);
(a.peakIndex+2) * SCMIR.hoptime;
SCMIR.frameTime(a.peakIndex);
a.peakTime;

a.peakTime-a.startTime;
a.attackDur;


a.pitchData.flop.plot;
a.activeEnv.plot;
a.temporalCentroid;
a.activeDissonanceData.plot;
a.activeEnv[0].plot;

(
{
	var env = a.env;
	Out.ar(1, SinOsc.ar(LFNoise0.kr(200, mul: 300, add: 700), mul: EnvGen.kr(env, doneAction: 2)))
}.play;
a.play;
)

/////////////////////////////////////////////////////////////////////////////////////////

f = SampleDescript(Platform.userExtensionDir ++ "onsettest.wav")
f.play;
f.play(0);
f.duration;
f.mirDataByFeatures;
f.rmsData.plot;
f.onsetTime;
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
g = SampleDescript("/Volumes/My Sounds/Sound Fx/Hollywood Edge/Ct 03/3-04 Impacts-Hollow Impacts 5 Versio.wav")
g.arEnv(startThresh: 0.05, endThresh: 0.05);
g.play;
g.play(0);
g.activeEnv.plot;
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
g.buffer;
g.activeBuffer;


///////////////////////////////////////////////////////////////////////////////////////////
e = SampleDescript(Platform.resourceDir +/+ "sounds/a11wlk01.wav", loadToBuffer: false)
e.play;
e.play(2);
e.sampleRate;
e.onsetIndex;
e.file.onsetdata;
e.startIndex;
e.endIndex;
e.temporalCentroid;
e.peakTime;
e.attackDur;
e.env.plot;
e.activeEnv.plot;
e.pitchData.flop.plot;
e.temporalCentroid;
e.activeCentroidData;
e.activeDissonanceData.plot;
e.fileNameAsNote;
e.keynum;
e.loadToBuffer;
e.buffer;

e.activeBuffer[1].plot;

(
{
	var env = e.env;
	Out.ar(1, SinOsc.ar(LFNoise0.kr(300, mul: 100, add: 800), mul: EnvGen.kr(env, doneAction: 2)))
}.play;
e.play;
)

m = SampleDescript("/Users/mac/Documents/UIUC/Algorythm Music 3/Samples/Crotales/c6.aiff", loadToBuffer: true)
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