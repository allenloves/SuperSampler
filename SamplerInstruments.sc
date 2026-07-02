
+ SSampler {
		*initClass{

		allSampler = IdentityDictionary.new;
		defaultLoadingServer = Server.default;

		//from Halim Beere and Henrich Taube
		StartUp.add({
			SynthDef(\ssplaybuf1, {arg buf, rate = 1, dur = 1, amp = 1, pan = 0, bend=nil, out = 0, startPos = 0;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);  //ampenv parameter
				var antiClipEnv = Env.linen(0.005, dur, 0.005, amp, \sine);
				var skwctl = Control.names([\bendenv]).kr(Env.newClear(32).asArray);
				var panctl = Control.names([\panenv]).kr(Env.newClear(32).asArray);
				//dur arrives as the wall-clock length (bend already folded in by SamplerQuery),
				//so the envelopes run on plain wall time — no reciprocal compensation.
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: antiClipEnv.duration);
				var ampgen = EnvGen.kr(ampctl, 1, amp, 0, timeScale: antiClipEnv.duration, doneAction:2);
				var pangen = EnvGen.kr(panctl, 1, 1, pan, timeScale: antiClipEnv.duration);
				var source = ampgen * PlayBuf.ar(numChannels: 1, bufnum: buf, rate: rate * skwgen * BufRateScale.kr(buf), startPos: startPos * BufSampleRate.kr(buf));
				Out.ar(bus: out, channelsArray: Pan2.ar(in: source * EnvGen.kr(envelope: antiClipEnv, doneAction: 2), pos: pangen));
			}).add;

			SynthDef(\ssplaybuf2, {arg buf0, buf1, rate = 1, dur = 1, amp = 1, pan = 0, bend =  nil, out = 0, startPos = 0;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);  //ampenv parameter
				var antiClipEnv = Env.linen(0.005, dur, 0.005, amp, \sine);
				var skwctl = Control.names([\bendenv]).kr(Env.newClear(32).asArray);
				var panctl = Control.names([\panenv]).kr(Env.newClear(32).asArray);
				//dur arrives as the wall-clock length (bend already folded in by SamplerQuery),
				//so the envelopes run on plain wall time — no reciprocal compensation.
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: antiClipEnv.duration);
				var ampgen = EnvGen.kr(ampctl, 1, amp, 0, timeScale: antiClipEnv.duration, doneAction:2);
				var pangen = EnvGen.kr(panctl, 1, 1, pan, timeScale: antiClipEnv.duration);
				var source0 = ampgen * PlayBuf.ar(numChannels: 1, bufnum: buf0, rate: rate * skwgen * BufRateScale.kr(buf0), startPos: startPos * BufSampleRate.kr(buf0));
				var source1 = ampgen * PlayBuf.ar(numChannels: 1, bufnum: buf1, rate: rate * skwgen * BufRateScale.kr(buf1), startPos: startPos * BufSampleRate.kr(buf1));
				Out.ar(bus: out, channelsArray: Balance2.ar(source0 * EnvGen.kr(envelope: antiClipEnv, doneAction: 2), source1 * EnvGen.kr(envelope: antiClipEnv, doneAction: 2), pos: pangen));
			}).add;


			SynthDef(\ssexpand1, {arg buf, expand=1, dur=1, rate=1, amp=1, pan=0, bend = nil, out=0, grainRate=20, grainDur=0.15, startPos = 0, panSpread = 0.1;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);
				var skwctl = Control.names([\bendenv]).kr(Env.newClear(32).asArray);
				var panctl = Control.names([\panenv]).kr(Env.newClear(32).asArray);
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: dur * expand);
				var ampgen = EnvGen.kr(ampctl, 1, amp, 0, timeScale: dur * expand , doneAction:2);
				var pangen = EnvGen.kr(panctl, 1, 1, pan, timeScale: dur * expand);
				var trigger = Impulse.kr(grainRate + LFNoise0.kr(grainRate*2,2.0/grainDur));
				//grain read position driven by the \posenv control (normalized buffer position
				//over normalized time; language side supplies a constant-speed sweep by default,
				//or a two-segment envelope for \stretchshort peak alignment).
				var posctl = Control.names([\posenv]).kr(Env.newClear(8).asArray);
				var position = EnvGen.kr(posctl, 1, timeScale: dur * expand);
				var outsig = ampgen * GrainBuf.ar(numChannels: 2, trigger: trigger, dur: grainDur, sndbuf: buf, rate: rate * skwgen,
				pos: position, interp: 2, pan: TRand.kr(panSpread * -1,panSpread,trigger) + pangen );
				Out.ar(bus: out, channelsArray: outsig);
			}).add;

			SynthDef(\ssexpand2, {arg buf0, buf1, expand=1, dur=1, rate=1, amp=1, pan=0, bend = nil, out=0, grainRate=20, grainDur=0.15, startPos = 0, panSpread = 0.1;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);
				var skwctl = Control.names([\bendenv]).kr(Env.newClear(32).asArray);
				var panctl = Control.names([\panenv]).kr(Env.newClear(32).asArray);
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: dur * expand);
				var ampgen = EnvGen.kr(ampctl, 1, amp, 0, timeScale: dur * expand , doneAction:2);
				var pangen = EnvGen.kr(panctl, 1, 1, pan, timeScale: dur * expand);
				var trigger = Impulse.kr(grainRate + LFNoise0.kr(grainRate*2,2.0/grainDur));
				//grain read position driven by the \posenv control — see \ssexpand1.
				var posctl = Control.names([\posenv]).kr(Env.newClear(8).asArray);
				var position = EnvGen.kr(posctl, 1, timeScale: dur * expand);
				var outsig0 = ampgen * GrainBuf.ar(numChannels: 1, trigger: trigger, dur: grainDur, sndbuf: buf0, rate: rate * skwgen * BufRateScale.kr(buf0),
				pos: position, interp: 2, pan: -1);
				var outsig1 = ampgen * GrainBuf.ar(numChannels: 1, trigger: trigger, dur: grainDur, sndbuf: buf1, rate: rate * skwgen * BufRateScale.kr(buf1),
				pos: position, interp: 2, pan: 1);
				Out.ar(bus: out, channelsArray: Balance2.ar(outsig0, outsig1, pangen));
			}).add;


		})
	}

}