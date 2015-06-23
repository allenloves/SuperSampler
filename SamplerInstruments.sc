+ Sampler {
		*initClass{
		StartUp.add({
			SynthDef(\playbuf, {arg buf, rate = 1, startPos = 0, dur = 1, amp = 1, pan = 0;
				var antiClipEnv = Env.linen(0.005, dur, 0.005, amp, \sine);
				var source;
				source = PlayBuf.ar(buf.numChannels, buf, rate * BufRateScale.kr(buf), startPos: startPos * BufSampleRate.kr(buf));
				Out.ar(0, Pan2.ar(source * EnvGen.kr(antiClipEnv, doneAction: 2), pan));
			}).add;
		})
	}

}