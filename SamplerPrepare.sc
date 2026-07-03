SamplerPrepare {
	var <> bufServer;
	var <> samplerName;   //Sampler name as address in SamplerDB
	var <> sample;    //SampleDescript object
	var <> buffer;    //buffer to be played = sample.activeBuffer[section]
	var <> section;   //subset of active data choosen
	var <> wait;      //Wait time for playback
	var <> rate;      //play rate for pitch adjustment
	var <> position;  //start position in the buffer
	var <> expand;    //Granular expansion
	var <> midiChannel;

	var <> duration;  //play duration after pitch adjustment, before pitch bendenv
	var < bendenv;   //bend envelope (setter caps to 32 segments — see *fitEnvArray)
	var < ampenv;    // Amplitude Envelope (setter caps to 32 segments)
	var < panenv;    // panning envelope (setter caps to 32 segments)
	var < posenv;    // grain position envelope for \ssexpand (normalized buffer position over
	                  // normalized time). nil = constant-speed sweep (classic Line behavior);
	                  // \stretchshort sets a two-segment env to align pre/post-peak separately.
	var <> normGain;  //Set by later task; predictive-gain normalization multiplier applied to amp.

	var <> attackDur;
	var <> releaseDur;

	*new {
		^super.new.init();
	}

	init {
		section = 0;
		wait = 0;
		rate = 1;
		position = 0;
		expand = nil;
	}

	//SynthDef env controls are fixed-size; oversized arrays make scsynth read
	//garbage ("envelope went past end of inputs") — resample on the way in.
	ampenv_ {|envArray| ampenv = SamplerQuery.fitEnvArray(envArray)}
	panenv_ {|envArray| panenv = SamplerQuery.fitEnvArray(envArray)}
	bendenv_ {|envArray| bendenv = SamplerQuery.fitEnvArray(envArray)}
	posenv_ {|envArray| posenv = SamplerQuery.fitEnvArray(envArray, 8)}

	setRate {|r|
		rate = r;

		if(duration.isNil)
		{
			duration = (sample.activeBuffer[section][0].duration / rate).abs;
			attackDur = sample.attackDur[section];
			releaseDur = sample.releaseDur[section];
		}
		{
			var hdur = duration / 2;
			attackDur = min(hdur, sample.attackDur[section]);
			releaseDur = min(hdur, sample.releaseDur[section]);

		}

	}


	//grain position envelope to feed \ssexpand: stored posenv, or the classic
	//constant-speed sweep (start position → 0.95 forward / → 0 backward).
	posenvArray {
		^this.posenv ?? {
			var bufDur = buffer[0].duration;
			if(rate.isPositive)
			{Env([(position / bufDur).clip(0, 1), 0.95], [1]).asArray}
			{Env([(position / bufDur).clip(0, 1), 0], [1]).asArray};
		};
	}

	//Register a spawned synth into SamplerQuery.playing as a SamplerVoice
	//carrying the metadata used by predictive gain management.
	registerVoice {|synth, args, synthID|
		var voice = SamplerVoice(
			synth,
			args.amp * (this.normGain ? 1) * ((sample.peakAmp ? #[1])[section] ? 1),
			thisThread.seconds,
			sample, section, rate,
			duration ? 1,
			args.gestureID
		);
		SamplerQuery.playing[this.midiChannel].put(synthID, voice);
		synth.onFree{SamplerQuery.playing[this.midiChannel].removeAt(synthID)};
		^voice;
	}

	// args are realizations of a SamplerArgument object
	play {arg args, synthID = UniqueID.next.asSymbol;//a SamplerArgument object
		case
		{this.expand.isNumber}{
			case
			{buffer.size == 2}{//stereo
				var synth = Synth(\ssexpand2, [buf0: buffer[0], buf1: buffer[1], expand: this.expand, dur: duration + 0.02, rate: this.rate, startPos: this.position, amp: args.amp * (this.normGain ? 1), ampenv: this.ampenv, pan: args.pan, panenv: this.panenv, bendenv: this.bendenv, posenv: this.posenvArray, grainRate: args.grainRate, grainDur: args.grainDur, out: args.out]);
				this.registerVoice(synth, args, synthID);
			}
			{true}{//mono
				var synth = Synth(\ssexpand1, [buf: buffer[0], expand: this.expand, dur: duration + 0.02, rate: this.rate, startPos: this.position, amp: args.amp * (this.normGain ? 1), ampenv: this.ampenv, pan: args.pan, panenv: this.panenv, bendenv: this.bendenv, posenv: this.posenvArray, grainRate: args.grainRate, grainDur: args.grainDur, out: args.out]);
				this.registerVoice(synth, args, synthID);
			};
		}
		{true}{//expand is nil
			case
			{buffer.size == 2}{//stereo
				var synth = Synth(\ssplaybuf2, [buf0: buffer[0], buf1: buffer[1], rate: this.rate, startPos: this.position, dur: duration, amp: args.amp * (this.normGain ? 1), ampenv: this.ampenv, pan: args.pan, panenv: this.panenv, bendenv: this.bendenv, out: args.out]);
				this.registerVoice(synth, args, synthID);
			}
			{true}{//mono
				var synth = Synth(\ssplaybuf1, [buf: buffer[0], rate: this.rate, startPos: this.position, dur: duration, amp: args.amp * (this.normGain ? 1), ampenv: this.ampenv, pan: args.pan, panenv: this.panenv, bendenv: this.bendenv, out: args.out]);
				this.registerVoice(synth, args, synthID);
			};
		};
	}
}