//A lightweight proxy wrapping a playing Synth with its metadata.
//Unknown messages (.set/.free/.release/...) forward to the synth, so code that
//grabs entries from SamplerQuery.playing for MIDI CC control works unchanged.
SamplerVoice {
	var <synth, <ampScale, <onsetAbs, <sample, <section, <rate, <wallDur, <gestureID;

	*new {|synth, ampScale = 1, onsetAbs = 0, sample, section = 0, rate = 1, wallDur = 1, gestureID|
		^super.newCopyArgs(synth, ampScale, onsetAbs, sample, section, rate, wallDur, gestureID)
	}

	//Object already defines no-op free/release (unrelated dependants bookkeeping), so
	//doesNotUnderstand never fires for those two selectors — forward them explicitly.
	free {^synth.free}
	release {|releaseTime| ^synth.release(releaseTime)}

	doesNotUnderstand {|selector ...args| ^synth.performList(selector, args)}
}
