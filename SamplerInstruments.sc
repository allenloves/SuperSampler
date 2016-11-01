+ Sampler {
		*initClass{
		StartUp.add({

			SynthDef(\NRT_playbuf,
				{
					arg buf, out = 0;
					Out.ar(out, PlayBuf.ar(buf.numChannels, buf, BufRateScale.ir(buf)));
				}).add;

			SynthDef(\ssplaybuf1, {arg buf, rate = 1, dur = 1, amp = 1, pan = 0, out = 0, startPos = 0;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);  //ampenv parameter
				var antiClipEnv = Env.linen(0.005, dur, 0.005, amp, \sine);
				var skwctl = Control.names([\bend]).kr(Env.newClear(32).asArray);
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: antiClipEnv.duration);
				var ampgen = EnvGen.kr(ampctl, 1, amp, 0, timeScale: antiClipEnv.duration * skwgen.reciprocal, doneAction:2);
				var source = ampgen * PlayBuf.ar(numChannels: 1, bufnum: buf, rate: rate * skwgen * BufRateScale.kr(buf), startPos: startPos * BufSampleRate.kr(buf));
				Out.ar(bus: out, channelsArray: Pan2.ar(in: source * EnvGen.kr(envelope: antiClipEnv, doneAction: 2), pos: pan));
			}).add;

			SynthDef(\ssplaybuf2, {arg buf, rate = 1, dur = 1, amp = 1, pan = 0, out = 0, startPos = 0;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);  //ampenv parameter
				var antiClipEnv = Env.linen(0.005, dur, 0.005, amp, \sine);
				var skwctl = Control.names([\bend]).kr(Env.newClear(32).asArray);
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: antiClipEnv.duration);
				var ampgen = EnvGen.kr(ampctl, 1, amp, 0, timeScale: antiClipEnv.duration * skwgen.reciprocal, doneAction:2);
				var source = ampgen * PlayBuf.ar(numChannels: 2, bufnum: buf, rate: rate * skwgen * BufRateScale.kr(buf), startPos: startPos * BufSampleRate.kr(buf));
				Out.ar(bus: out, channelsArray: Pan2.ar(in: source * EnvGen.kr(envelope: antiClipEnv, doneAction: 2), pos: pan));
			}).add;


			SynthDef(\ssexpand, {arg buf=0, expand=1, dur=1, rate=1, amp=1, pan=0, out=0, grainRate=20, grainDur=0.15, startPos = 0, panSpread = 0.1;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);
				var skwctl = Control.names([\bend]).kr(Env.newClear(32).asArray);
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: dur * expand);
				var ampgen = EnvGen.kr(ampctl, 1, amp, 0, timeScale: dur * expand , doneAction:2);
				var trigger = Impulse.kr(grainRate + LFNoise0.kr(grainRate*2,2.0/grainDur));
				var position = Line.kr(start: startPos/BufDur.ir(buf), end: 1, dur:  (dur * expand));  //dur:  ((BufDur.ir(buf) - startPos) * expand));
				var outsig = ampgen * GrainBuf.ar(numChannels: 2, trigger: trigger, dur: grainDur, sndbuf: buf, rate: rate * skwgen,
					pos: position, interp: 2, pan: TRand.kr(panSpread * -1,panSpread,trigger) + pan );
				Out.ar(bus: out, channelsArray: outsig);
			}).add;


		})
	}

}