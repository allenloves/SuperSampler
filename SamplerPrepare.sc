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
	var <> bendenv;   //bend envelope
	var <> ampenv;    // Amplitude Envelope
	var <> panenv;    // panning envelope

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

	play {arg args, synthID = UniqueID.next.asSymbol;//a SamplerArgument object
		case
		{this.expand.isNumber}{
			case
			{buffer.size == 2}{//stereo
				SamplerQuery.playing[this.midiChannel].put(synthID, Synth(\ssexpand2, [buf0: buffer[0], buf1: buffer[1], expand: this.expand, dur: duration + 0.02, rate: this.rate, startPos: this.position, amp: args.amp, ampenv: this.ampenv, pan: args.pan, panenv: this.panenv, bendenv: this.bendenv, grainRate: args.grainRate, grainDur: args.grainDur, out: args.out]).onFree{SamplerQuery.playing[this.midiChannel].removeAt(synthID)});
			}
			{true}{//mono
				SamplerQuery.playing[this.midiChannel].put(synthID, Synth(\ssexpand1, [buf: buffer[0], expand: this.expand, dur: duration + 0.02, rate: this.rate, startPos: this.position, amp: args.amp, ampenv: this.ampenv, pan: args.pan, panenv: this.panenv, bendenv: this.bendenv, grainRate: args.grainRate, grainDur: args.grainDur, out: args.out]).onFree{SamplerQuery.playing[this.midiChannel].removeAt(synthID)});
			};
		}
		{true}{//expand is nil
			case
			{buffer.size == 2}{//stereo
				SamplerQuery.playing[this.midiChannel].put(synthID, Synth(\ssplaybuf2, [buf0: buffer[0], buf1: buffer[1], rate: this.rate, startPos: this.position, dur: duration, amp: args.amp, ampenv: this.ampenv, pan: args.pan, panenv: this.panenv, bendenv: this.bendenv, out: args.out]).onFree{SamplerQuery.playing[this.midiChannel].removeAt(synthID)});
			}
			{true}{//mono
				SamplerQuery.playing[this.midiChannel].put(synthID, Synth(\ssplaybuf1, [buf: buffer[0], rate: this.rate, startPos: this.position, dur: duration, amp: args.amp, ampenv: this.ampenv, pan: args.pan, panenv: this.panenv, bendenv: this.bendenv, out: args.out]).onFree{SamplerQuery.playing[this.midiChannel].removeAt(synthID)});
			};
		};
	}
}