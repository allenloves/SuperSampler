//SSamplerMIDI -- thin MIDI bridge for the voice-mode API on SSampler.
//
//Routes incoming MIDI Note On / Note Off to SSampler#noteOn / #noteOff,
//with CC64 sustain-pedal handling: Note Off events that arrive while the
//pedal is held are deferred and flushed on pedal release.
//
//Each instance owns its own MIDIdef keys, so multiple bridges can coexist.
//Velocity 0 on Note On is treated as Note Off (running-status convention).
//
//Usage:
//    MIDIIn.connectAll;          // user does this once
//    m = SSamplerMIDI(sampler);  // attach handlers
//    ...
//    m.free;                     // detach handlers
SSamplerMIDI {
	var <sampler;
	var <key;
	var <>channel;        //nil = any channel; otherwise filter by exact match
	var <sustain;
	var <heldByPedal;
	var <onDef, <offDef, <ccDef;

	*new {|sampler, key = nil, channel = nil|
		^super.new.init(sampler, key, channel);
	}

	init {|samp, k, ch|
		sampler = samp;
		key = k ?? { (\ssMIDI_ ++ UniqueID.next).asSymbol };
		channel = ch;
		sustain = false;
		heldByPedal = Set.new;
		if(MIDIClient.initialized.not) {
			MIDIClient.init;
			"SSamplerMIDI: MIDIClient initialized. Call MIDIIn.connectAll if needed.".postln;
		};
		this.connect;
	}

	channelMatches {|chan|
		^(channel.isNil or: { chan == channel });
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
					sampler.noteOn(note, vel);
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
