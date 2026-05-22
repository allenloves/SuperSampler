//SSamplerMIDI -- thin MIDI bridge for the voice-mode API on SSampler.
//
//Routes incoming MIDI Note On / Note Off to SSampler#noteOn / #noteOff,
//with CC64 sustain-pedal handling: Note Off events that arrive while the
//pedal is held are deferred and flushed on pedal release.
//
//Each instance owns its own MIDIdef keys, so multiple bridges can coexist.
//Velocity 0 on Note On is treated as Note Off (running-status convention).
//
//`defaults` is an Event of extra keyword arguments passed verbatim to
//SSampler#noteOn for every triggered note. Any noteOn arg is fair game:
//loop, loopDir, loopMode, loopXfade, attack, decay, sustainLevel, release,
//releaseMode, releaseStart, releaseEnd, releaseXfade, pan, texture, etc.
//
//Usage:
//    MIDIIn.connectAll;          // user does this once
//    m = SSamplerMIDI(sampler);  // attach handlers (default settings)
//    m.defaults = (              // keyboard player gets a release loop
//        loop: 1, loopXfade: 0.05,
//        attack: 0.01, decay: 0.2, sustainLevel: 0.8, release: 2.0,
//        releaseMode: \loop, releaseStart: 80000, releaseXfade: 0.02
//    );
//    ...
//    m.free;                     // detach handlers
SSamplerMIDI {
	var <sampler;
	var <key;
	var <>channel;        //nil = any channel; otherwise filter by exact match
	var <sustain;
	var <heldByPedal;
	var <onDef, <offDef, <ccDef;
	var <>defaults;       //Event of extra keyword args forwarded to noteOn

	*new {|sampler, key = nil, channel = nil, defaults = nil|
		^super.new.init(sampler, key, channel, defaults);
	}

	init {|samp, k, ch, d|
		sampler = samp;
		key = k ?? { (\ssMIDI_ ++ UniqueID.next).asSymbol };
		channel = ch;
		sustain = false;
		heldByPedal = Set.new;
		defaults = d ?? { Event.new };
		if(MIDIClient.initialized.not) {
			MIDIClient.init;
			"SSamplerMIDI: MIDIClient initialized. Call MIDIIn.connectAll if needed.".postln;
		};
		this.connect;
	}

	channelMatches {|chan|
		^(channel.isNil or: { chan == channel });
	}

	//Pass note + velocity + the user's defaults Event through to noteOn.
	//performKeyValuePairs maps each [\key, val] pair onto the matching
	//noteOn keyword arg.
	triggerNote {|note, vel|
		var pairs = [\note, note, \vel, vel];
		defaults.keysValuesDo({|k, v| pairs = pairs.add(k).add(v) });
		^sampler.performKeyValuePairs(\noteOn, pairs);
	}

	connect {
		onDef = MIDIdef.noteOn((key ++ "_on").asSymbol, {|vel, note, chan|
			if(this.channelMatches(chan)) {
				//running-status convention: NoteOn vel 0 means NoteOff.
				if(vel == 0) {
					this.handleNoteOff(note);
				} {
					//A re-press during sustain cancels the deferred noteOff
					//for that note, then stacks a new voice on top.
					heldByPedal.remove(note);
					this.triggerNote(note, vel);
				};
			};
		});
		offDef = MIDIdef.noteOff((key ++ "_off").asSymbol, {|vel, note, chan|
			if(this.channelMatches(chan)) {
				this.handleNoteOff(note);
			};
		});
		ccDef = MIDIdef.cc((key ++ "_cc").asSymbol, {|val, ccNum, chan|
			if(this.channelMatches(chan) and: { ccNum == 64 }) {
				this.handleSustain(val);
			};
		});
	}

	handleNoteOff {|note|
		if(sustain) {
			heldByPedal.add(note);
		} {
			sampler.noteOff(note);
		};
	}

	//CC64: >=64 pedal down, <64 pedal up.
	handleSustain {|val|
		if(val >= 64) {
			sustain = true;
		} {
			sustain = false;
			heldByPedal.copy.do{|note| sampler.noteOff(note) };
			heldByPedal.clear;
		};
	}

	disconnect {
		[onDef, offDef, ccDef].do{|def| def !? { def.free } };
		onDef = nil; offDef = nil; ccDef = nil;
	}

	free {
		this.disconnect;
		heldByPedal.clear;
		sustain = false;
	}
}
