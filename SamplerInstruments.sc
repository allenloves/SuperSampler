
+ SSampler {
		*initClass{

		allSampler = IdentityDictionary.new;
		defaultLoadingServer = Server.default;

		//from Halim Beere and Henrich Taube
		StartUp.add({
			SynthDef(\ssplaybuf1, {arg buf, rate = 1, dur = 1, amp = 1, pan = 0, bend=nil, out = 0, startPos = 0, gate = 1;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);  //ampenv parameter
				var antiClipEnv = Env.linen(0.005, dur, 0.005, 1, \sine);
				var skwctl = Control.names([\bendenv]).kr(Env.newClear(32).asArray);
				var panctl = Control.names([\panenv]).kr(Env.newClear(32).asArray);
				//system-wide kill switch: set(\gate, 0) fades the voice out in 0.1s and frees it
				var killGate = EnvGen.kr(Env.asr(0, 1, 0.1), gate, doneAction: 2);
				//live controls: .set(\amp, ...) / .set(\pan, ...) on a playing voice take effect
				//immediately (lagged 50ms) — EnvGen levelScale/offset would only be read at init
				var liveAmp = Lag.kr(amp, 0.05);
				var livePan = Lag.kr(pan, 0.05);
				//dur arrives as the wall-clock length (bend already folded in by SamplerQuery),
				//so the envelopes run on plain wall time — no reciprocal compensation.
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: antiClipEnv.duration);
				var ampgen = EnvGen.kr(ampctl, 1, 1, 0, timeScale: antiClipEnv.duration, doneAction:2);
				var pangen = EnvGen.kr(panctl, 1, 1, 0, timeScale: antiClipEnv.duration) + livePan;
				var source = killGate * liveAmp * ampgen * PlayBuf.ar(numChannels: 1, bufnum: buf, rate: rate * skwgen * BufRateScale.kr(buf), startPos: startPos * BufSampleRate.kr(buf));
				Out.ar(bus: out, channelsArray: Pan2.ar(in: source * EnvGen.kr(envelope: antiClipEnv, doneAction: 2), pos: pangen));
			}).add;

			SynthDef(\ssplaybuf2, {arg buf0, buf1, rate = 1, dur = 1, amp = 1, pan = 0, bend =  nil, out = 0, startPos = 0, gate = 1;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);  //ampenv parameter
				var antiClipEnv = Env.linen(0.005, dur, 0.005, 1, \sine);
				var skwctl = Control.names([\bendenv]).kr(Env.newClear(32).asArray);
				var panctl = Control.names([\panenv]).kr(Env.newClear(32).asArray);
				//system-wide kill switch: set(\gate, 0) fades the voice out in 0.1s and frees it
				var killGate = EnvGen.kr(Env.asr(0, 1, 0.1), gate, doneAction: 2);
				//live controls: .set(\amp, ...) / .set(\pan, ...) on a playing voice take effect
				//immediately (lagged 50ms) — EnvGen levelScale/offset would only be read at init
				var liveAmp = Lag.kr(amp, 0.05);
				var livePan = Lag.kr(pan, 0.05);
				//dur arrives as the wall-clock length (bend already folded in by SamplerQuery),
				//so the envelopes run on plain wall time — no reciprocal compensation.
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: antiClipEnv.duration);
				var ampgen = EnvGen.kr(ampctl, 1, 1, 0, timeScale: antiClipEnv.duration, doneAction:2);
				var pangen = EnvGen.kr(panctl, 1, 1, 0, timeScale: antiClipEnv.duration) + livePan;
				var source0 = killGate * liveAmp * ampgen * PlayBuf.ar(numChannels: 1, bufnum: buf0, rate: rate * skwgen * BufRateScale.kr(buf0), startPos: startPos * BufSampleRate.kr(buf0));
				var source1 = killGate * liveAmp * ampgen * PlayBuf.ar(numChannels: 1, bufnum: buf1, rate: rate * skwgen * BufRateScale.kr(buf1), startPos: startPos * BufSampleRate.kr(buf1));
				Out.ar(bus: out, channelsArray: Balance2.ar(source0 * EnvGen.kr(envelope: antiClipEnv, doneAction: 2), source1 * EnvGen.kr(envelope: antiClipEnv, doneAction: 2), pos: pangen));
			}).add;


			SynthDef(\ssexpand1, {arg buf, expand=1, dur=1, rate=1, amp=1, pan=0, bend = nil, out=0, grainRate=20, grainDur=0.15, startPos = 0, panSpread = 0.1, gate = 1;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);
				var skwctl = Control.names([\bendenv]).kr(Env.newClear(32).asArray);
				var panctl = Control.names([\panenv]).kr(Env.newClear(32).asArray);
				//system-wide kill switch: set(\gate, 0) fades the voice out in 0.1s and frees it
				var killGate = EnvGen.kr(Env.asr(0, 1, 0.1), gate, doneAction: 2);
				//live controls: .set(\amp, ...) / .set(\pan, ...) on a playing voice take effect
				//immediately (lagged 50ms) — EnvGen levelScale/offset would only be read at init
				var liveAmp = Lag.kr(amp, 0.05);
				var livePan = Lag.kr(pan, 0.05);
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: dur * expand);
				var ampgen = EnvGen.kr(ampctl, 1, 1, 0, timeScale: dur * expand , doneAction:2);
				var pangen = EnvGen.kr(panctl, 1, 1, 0, timeScale: dur * expand) + livePan;
				var trigger = Impulse.kr(grainRate + LFNoise0.kr(grainRate*2,2.0/grainDur));
				//grain read position driven by the \posenv control (normalized buffer position
				//over normalized time; language side supplies a constant-speed sweep by default,
				//or a two-segment envelope for \stretchshort peak alignment).
				var posctl = Control.names([\posenv]).kr(Env.newClear(8).asArray);
				var position = EnvGen.kr(posctl, 1, timeScale: dur * expand);
				var outsig = killGate * liveAmp * ampgen * GrainBuf.ar(numChannels: 2, trigger: trigger, dur: grainDur, sndbuf: buf, rate: rate * skwgen,
				pos: position, interp: 2, pan: TRand.kr(panSpread * -1,panSpread,trigger) + pangen );
				Out.ar(bus: out, channelsArray: outsig);
			}).add;

			SynthDef(\ssexpand2, {arg buf0, buf1, expand=1, dur=1, rate=1, amp=1, pan=0, bend = nil, out=0, grainRate=20, grainDur=0.15, startPos = 0, panSpread = 0.1, gate = 1;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);
				var skwctl = Control.names([\bendenv]).kr(Env.newClear(32).asArray);
				var panctl = Control.names([\panenv]).kr(Env.newClear(32).asArray);
				//system-wide kill switch: set(\gate, 0) fades the voice out in 0.1s and frees it
				var killGate = EnvGen.kr(Env.asr(0, 1, 0.1), gate, doneAction: 2);
				//live controls: .set(\amp, ...) / .set(\pan, ...) on a playing voice take effect
				//immediately (lagged 50ms) — EnvGen levelScale/offset would only be read at init
				var liveAmp = Lag.kr(amp, 0.05);
				var livePan = Lag.kr(pan, 0.05);
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: dur * expand);
				var ampgen = EnvGen.kr(ampctl, 1, 1, 0, timeScale: dur * expand , doneAction:2);
				var pangen = EnvGen.kr(panctl, 1, 1, 0, timeScale: dur * expand) + livePan;
				var trigger = Impulse.kr(grainRate + LFNoise0.kr(grainRate*2,2.0/grainDur));
				//grain read position driven by the \posenv control — see \ssexpand1.
				var posctl = Control.names([\posenv]).kr(Env.newClear(8).asArray);
				var position = EnvGen.kr(posctl, 1, timeScale: dur * expand);
				var outsig0 = killGate * liveAmp * ampgen * GrainBuf.ar(numChannels: 1, trigger: trigger, dur: grainDur, sndbuf: buf0, rate: rate * skwgen * BufRateScale.kr(buf0),
				pos: position, interp: 2, pan: -1);
				var outsig1 = killGate * liveAmp * ampgen * GrainBuf.ar(numChannels: 1, trigger: trigger, dur: grainDur, sndbuf: buf1, rate: rate * skwgen * BufRateScale.kr(buf1),
				pos: position, interp: 2, pan: 1);
				Out.ar(bus: out, channelsArray: Balance2.ar(outsig0, outsig1, pangen));
			}).add;

			//Preview players for SampleDescript.play. Pre-built here so auditioning a
			//sample starts with plain-Synth latency — the old per-call {}.play compiled
			//a fresh SynthDef every time, starting tens of ms late (audible as a
			//constant flam when comparing against playEnv gestures).
			SynthDef(\sspreview1, {arg buf, rate = 1, pan = 0, level = 1, out = 0;
				Out.ar(out, Pan2.ar(PlayBuf.ar(1, buf, rate: BufRateScale.kr(buf) * rate, doneAction: 2), pan, level));
			}).add;
			SynthDef(\sspreview2, {arg buf0, buf1, rate = 1, pan = 0, level = 1, out = 0;
				Out.ar(out, Balance2.ar(
					PlayBuf.ar(1, buf0, rate: BufRateScale.kr(buf0) * rate, doneAction: 2),
					PlayBuf.ar(1, buf1, rate: BufRateScale.kr(buf1) * rate, doneAction: 2),
					pan, level));
			}).add;

			//Master limiter (see SSampler.limiterOn / *limiterOff). Fixed stereo (2ch);
			//reads the dedicated internal SuperSampler bus and MIXES the limited signal
			//into the hardware output (plain Out — other music on that bus is untouched).
			SynthDef(\sslimiter, {arg in, out = 0;
				Out.ar(out, Limiter.ar(In.ar(in, 2), 0.95, 0.01));
			}).add;

			//limiter is ON by default: register the per-boot bus allocation and the
			//per-ServerTree respawn (see SSampler.initLimiterHooks).
			SSampler.initLimiterHooks;

		})
	}

}