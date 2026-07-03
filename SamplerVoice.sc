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

	//Gate the voice off (0.1s kill-gate fade in the SynthDefs). The voice may have
	//just freed itself on the server (doneAction: 2 at playback end) with its /n_end
	//still in flight, so the /n_set travels with error posting suppressed for this
	//bundle — a gate landing on an already-gone node is the desired end state, not
	//a failure worth "FAILURE IN SERVER /n_set Node not found" on the post window.
	gateOff {
		if(synth.isKindOf(Node))
		{synth.server.sendBundle(nil, *SamplerVoice.gateOffBundle(synth))}
		{synth.set(\gate, 0)};
	}

	//['/error', -1] turns server error posting off for the current bundle only
	//(Server Command Reference), so nothing outside this gate is silenced.
	*gateOffBundle {|node| ^[['/error', -1], node.setMsg(\gate, 0)]}

	doesNotUnderstand {|selector ...args| ^synth.performList(selector, args)}
}
