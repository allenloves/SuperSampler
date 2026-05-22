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
		//Per-(sample, section) overrides (SSampler#setSampleVoiceArgs).
		//Take precedence over args.<field>. Lookup may yield nil at any layer.
		var sampler = SSampler.allSampler.at(this.samplerName);
		var overrides = if(sampler.isNil) { nil } {
			sampler.getSampleVoiceArgs(this.sample, this.section)
		};
		var pick = {|key, fallback|
			if(overrides.isNil) { fallback } {
				var v = overrides.at(key);
				if(v.isNil) { fallback } { v };
			};
		};
		var effLoop         = pick.(\loop,         args.loop);
		var effLoopDir      = pick.(\loopDir,      args.loopDir);
		var effLoopMode     = pick.(\loopMode,     args.loopMode);
		var effLoopStart    = pick.(\loopStart,    args.loopStart);
		var effLoopEnd      = pick.(\loopEnd,      args.loopEnd);
		var effLoopXfade    = pick.(\loopXfade,    args.loopXfade);
		var effAttack       = pick.(\attack,       args.attack);
		var effDecay        = pick.(\decay,        args.decay);
		var effSustainLevel = pick.(\sustainLevel, args.sustainLevel);
		var effRelease      = pick.(\release,      args.release);
		var effReleaseMode  = pick.(\releaseMode,  args.releaseMode);
		var effReleaseStart = pick.(\releaseStart, args.releaseStart);
		var effReleaseEnd   = pick.(\releaseEnd,   args.releaseEnd);
		var effReleaseXfade = pick.(\releaseXfade, args.releaseXfade);
		var effAmpenv       = pick.(\ampenv,       nil);

		var loopDirInt = case
			{ effLoopDir == \fwd }   { 0 }
			{ effLoopDir == \rev }   { 1 }
			{ effLoopDir == \palin } { 2 }
			{ effLoopDir.isNumber }  { effLoopDir.asInteger }
			{ true } { 0 };
		var loopModeInt = case
			{ effLoopMode == \trapezoid } { 0 }
			{ effLoopMode == \trap }      { 0 }
			{ effLoopMode == \xfade }     { 1 }
			{ effLoopMode.isNumber }      { effLoopMode.asInteger }
			{ true } { 0 };
		//Ableton release-mode dispatch: 0=off, 1=oneShot, 2=loop (fwd), 3=palin.
		var releaseModeInt = case
			{ effReleaseMode == \off }     { 0 }
			{ effReleaseMode == \oneShot } { 1 }
			{ effReleaseMode == \loop }    { 2 }
			{ effReleaseMode == \fwd }     { 2 }
			{ effReleaseMode == \palin }   { 3 }
			{ effReleaseMode.isNumber }    { effReleaseMode.asInteger }
			{ true } { 0 };
		//If a release region is active, use the gate-driven ASR envelope so
		//the synth survives long enough for the release region to be heard.
		//Mirrors Ableton: the release region is audible only while the amp
		//envelope's release segment is still ramping down.
		var releaseActive = releaseModeInt > 0;

		//A per-sample ampenv (Env) overrides the ADSR builder entirely.
		//If gated (loop > 0 or releaseActive) and the Env has no release
		//node, set one to the penultimate breakpoint so .set(\gate, 0)
		//can run the final segment as the release.
		voiceEnv = if(effAmpenv.isKindOf(Env)) {
			var e = effAmpenv;
			if(e.releaseNode.isNil and: { (effLoop > 0) or: { releaseActive } }) {
				e = e.copy;
				e.releaseNode = (e.levels.size - 2).max(0);
			};
			e.asArray
		} {
			//Gate-driven ADSR for sustained / release-mode voices; self-
			//terminating linen for one-shots without a release region. ADSR
			//collapses to ASR when sustainLevel == 1 (decay segment is flat),
			//preserving the pre-decay/sustainLevel default behavior.
			if((effLoop > 0) or: { releaseActive }) {
				Env.adsr(effAttack, effDecay, effSustainLevel,
					effRelease, 1, \sin).asArray
			} {
				Env.linen(effAttack,
					max(duration - effAttack - effRelease, 0.001),
					effRelease, 1, \sine).asArray
			}
		};

		common = [
			\rate, this.rate,
			\amp, args.amp,
			\pan, args.pan,
			\out, args.out,
			\startPos, this.position,
			\dur, duration,
			\gate, args.gate,
			\loop, effLoop,
			\loopDir, loopDirInt,
			\loopMode, loopModeInt,
			\loopStart, effLoopStart ? 0,
			\loopEnd, effLoopEnd ? 0,
			\loopXfade, effLoopXfade ? 0,
			\releaseMode, releaseModeInt,
			\releaseStart, effReleaseStart ? 0,
			\releaseEnd, effReleaseEnd ? 0,
			\releaseXfade, effReleaseXfade ? 0,
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