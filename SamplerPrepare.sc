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

	//Voice-mode playback. Returns the Synth so the caller may register it
	//in SSampler#activeVoices. Also tracked in SamplerQuery.playing for parity.
	//
	//Dispatches to \ssvoice1 (mono) or \ssvoice2 (stereo) by buffer arity --
	//same dispatch policy as the existing #play method.
	//
	//With args.loop == 0 the synth uses a self-terminating linen envelope
	//(perceptual parity with \ssplaybuf{1,2}).
	//With args.loop > 0 the synth uses an ASR envelope held open by gate;
	//the caller drops gate via Synth#set(\gate, 0) to release.
	playVoice {arg args, synthID = UniqueID.next.asSymbol;
		var voiceEnv;
		var synth;
		var common;
		var loopDirInt = case
			{ args.loopDir == \fwd }   { 0 }
			{ args.loopDir == \rev }   { 1 }
			{ args.loopDir == \palin } { 2 }
			{ args.loopDir.isNumber }  { args.loopDir.asInteger }
			{ true } { 0 };
		var loopModeInt = case
			{ args.loopMode == \trapezoid } { 0 }
			{ args.loopMode == \trap }      { 0 }
			{ args.loopMode == \xfade }     { 1 }
			{ args.loopMode.isNumber }      { args.loopMode.asInteger }
			{ true } { 0 };
		//Ableton release-mode dispatch: 0=off, 1=oneShot, 2=loop (fwd), 3=palin.
		var releaseModeInt = case
			{ args.releaseMode == \off }     { 0 }
			{ args.releaseMode == \oneShot } { 1 }
			{ args.releaseMode == \loop }    { 2 }
			{ args.releaseMode == \fwd }     { 2 }
			{ args.releaseMode == \palin }   { 3 }
			{ args.releaseMode.isNumber }    { args.releaseMode.asInteger }
			{ true } { 0 };
		//If a release region is active, use the gate-driven ASR envelope so
		//the synth survives long enough for the release region to be heard.
		//Mirrors Ableton: the release region is audible only while the amp
		//envelope's release segment is still ramping down.
		var releaseActive = releaseModeInt > 0;

		voiceEnv = if((args.loop > 0) or: { releaseActive }) {
			Env.asr(args.attack, 1, args.release, \sin).asArray
		} {
			Env.linen(args.attack,
				max(duration - args.attack - args.release, 0.001),
				args.release, 1, \sine).asArray
		};

		common = [
			\rate, this.rate,
			\amp, args.amp,
			\pan, args.pan,
			\out, args.out,
			\startPos, this.position,
			\dur, duration,
			\gate, args.gate,
			\loop, args.loop,
			\loopDir, loopDirInt,
			\loopMode, loopModeInt,
			\loopStart, args.loopStart ? 0,
			\loopEnd, args.loopEnd ? 0,
			\loopXfade, args.loopXfade ? 0,
			\releaseMode, releaseModeInt,
			\releaseStart, args.releaseStart ? 0,
			\releaseEnd, args.releaseEnd ? 0,
			\releaseXfade, args.releaseXfade ? 0,
			\env, voiceEnv
		];

		synth = if(buffer.size == 2) {
			Synth(\ssvoice2, [\buf0, buffer[0], \buf1, buffer[1]] ++ common)
		} {
			Synth(\ssvoice1, [\buf, buffer[0]] ++ common)
		};
		synth.onFree({
			SamplerQuery.playing[this.midiChannel].removeAt(synthID);
		});
		SamplerQuery.playing[this.midiChannel].put(synthID, synth);
		^synth;
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