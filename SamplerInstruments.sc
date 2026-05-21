
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
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: antiClipEnv.duration);
				var ampgen = EnvGen.kr(ampctl, 1, amp, 0, timeScale: antiClipEnv.duration * skwgen.reciprocal, doneAction:2);
				var pangen = EnvGen.kr(panctl, 1, 1, pan, timeScale: antiClipEnv.duration * skwgen.reciprocal);
				var source = ampgen * PlayBuf.ar(numChannels: 1, bufnum: buf, rate: rate * skwgen * BufRateScale.kr(buf), startPos: startPos * BufSampleRate.kr(buf));
				Out.ar(bus: out, channelsArray: Pan2.ar(in: source * EnvGen.kr(envelope: antiClipEnv, doneAction: 2), pos: pangen));
			}).add;

			SynthDef(\ssplaybuf2, {arg buf0, buf1, rate = 1, dur = 1, amp = 1, pan = 0, bend =  nil, out = 0, startPos = 0;
				var ampctl = Control.names([\ampenv]).kr(Env.newClear(32).asArray);  //ampenv parameter
				var antiClipEnv = Env.linen(0.005, dur, 0.005, amp, \sine);
				var skwctl = Control.names([\bendenv]).kr(Env.newClear(32).asArray);
				var panctl = Control.names([\panenv]).kr(Env.newClear(32).asArray);
				var skwgen = EnvGen.kr(skwctl, 1, 1, 0, timeScale: antiClipEnv.duration);
				var ampgen = EnvGen.kr(ampctl, 1, amp, 0, timeScale: antiClipEnv.duration * skwgen.reciprocal, doneAction:2);
				var pangen = EnvGen.kr(panctl, 1, 1, pan, timeScale: antiClipEnv.duration * skwgen.reciprocal);
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
				var position = Line.kr(start: startPos/BufDur.ir(buf), end: (rate.sign + 1)/2 * 0.95, dur:  (dur * expand));  //dur:  ((BufDur.ir(buf) - startPos) * expand));
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
				var position = Line.kr(start: startPos/BufDur.ir(buf0), end: (rate.sign + 1)/2 * 0.95, dur:  (dur * expand));  //dur:  ((BufDur.ir(buf) - startPos) * expand));
				var outsig0 = ampgen * GrainBuf.ar(numChannels: 1, trigger: trigger, dur: grainDur, sndbuf: buf0, rate: rate * skwgen * BufRateScale.kr(buf0),
				pos: position, interp: 2, pan: -1);
				var outsig1 = ampgen * GrainBuf.ar(numChannels: 1, trigger: trigger, dur: grainDur, sndbuf: buf1, rate: rate * skwgen * BufRateScale.kr(buf1),
				pos: position, interp: 2, pan: 1);
				Out.ar(bus: out, channelsArray: Balance2.ar(outsig0, outsig1, pangen));
			}).add;


			// Voice-mode mono SynthDef.
			// Lifetime is owned by a single EnvGen with doneAction:2.
			// Caller (SamplerPrepare#playVoice) supplies the envelope shape:
			//   - one-shot: Env.linen(attack, body, release) -> self-frees at end.
			//   - gated:    Env.asr(attack, 1, release) + gate -> released via .set(\gate, 0).
			//
			// loop = 0: Line.ar pointer over the whole section, rate baked into dur.
			// loop = 1: Phasor.ar wraps between [loopStart, loopEnd] at rate*BufRateScale.
			// loopDir : 0 fwd, 1 rev, 2 palin.
			// loopMode: 0 trapezoid window (default), 1 equal-power 2-tap crossfade.
			//   trapezoid: cheap, sharper, optionally disabled via loopXfade <= 0.
			//   xfade:     continuous sin/cos crossfade between ptr and a half-cycle
			//              offset reader. Doubles BufRd cost; phaseyness on
			//              slowly-varying material. Palindrome falls back to ptr
			//              (no seam to mask).
			SynthDef(\ssvoice1, {arg buf, rate = 1, amp = 1, pan = 0, out = 0,
				                startPos = 0, dur = 1, gate = 1,
				                loop = 0, loopDir = 0, loopMode = 0,
				                loopStart = 0, loopEnd = 0,
				                loopXfade = 0;
				var envCtl = \env.kr(Env.newClear(8).asArray);
				var bufSR = BufSampleRate.kr(buf);
				var bufFrames = BufFrames.kr(buf);
				var startFrames = startPos * bufSR;
				var step = rate * BufRateScale.kr(buf);
				// loopEnd <= 0 means "use whole buffer".
				var lEnd = Select.kr(loopEnd > 0, [bufFrames, loopEnd]);
				var lStart = loopStart;
				var llen = (lEnd - lStart).max(1);
				var halfLen = llen * 0.5;
				var fwd = Phasor.ar(0, step,     lStart, lEnd, lStart);
				var rev = Phasor.ar(0, step.neg, lStart, lEnd, lEnd);
				// Palindrome: unfolded triangle phase tri in [0, 2*llen),
				// folded into [lStart, lEnd] by reflecting around llen.
				var tri = Phasor.ar(0, step.abs, 0, 2 * llen);
				var pal = lStart + (llen - (tri - llen).abs);
				// Half-cycle-offset readers for the xfade mode.
				var fwdAlt = Phasor.ar(0, step,     lStart, lEnd, lStart + halfLen);
				var revAlt = Phasor.ar(0, step.neg, lStart, lEnd, lEnd   - halfLen);
				var loopPtr  = Select.ar(loopDir, [fwd,    rev,    pal]);
				var loopPtr2 = Select.ar(loopDir, [fwdAlt, revAlt, pal]);
				var onePtr = Line.ar(startFrames, startFrames + bufFrames, dur);
				var ptr  = Select.ar(loop, [onePtr, loopPtr]);
				var ptr2 = Select.ar(loop, [onePtr, loopPtr2]);
				var sig1 = BufRd.ar(1, buf, ptr,  loop: 1, interpolation: 4);
				var sig2 = BufRd.ar(1, buf, ptr2, loop: 1, interpolation: 4);
				// Normalized loop-cycle phase: 0 at lStart, 1 at lEnd. Same
				// expression handles fwd (sawtooth 0->1), rev (sawtooth 1->0),
				// pal (triangle 0->1->0).
				var ph = ((ptr - lStart) / llen).clip(0, 1);
				// loopMode 0: trapezoid window.
				var f = ((loopXfade * bufSR) / llen).max(0.0001);
				var winRaw = ((ph / f).clip(0, 1) * ((1 - ph) / f).clip(0, 1));
				var winEnabled = loop * (loopXfade > 0);
				var win = (winEnabled * winRaw) + (1 - winEnabled);
				var sigTrap = sig1 * win;
				// loopMode 1: equal-power 2-tap crossfade. g1*sig1 + g2*sig2
				// with g1^2 + g2^2 = 1; g1 = 0 at the seam (ptr at lStart for fwd
				// or at lEnd-equivalent for rev), masking the discontinuity.
				// Palin has no seam, so we fall back to sig1.
				var g1 = sin(ph * (pi/2));
				var g2 = cos(ph * (pi/2));
				var sigSeam = (sig1 * g1) + (sig2 * g2);
				var isPalin = (loopDir > 1);
				var sigXfade = ((1 - isPalin) * sigSeam) + (isPalin * sig1);
				var sigLoop = Select.ar(loopMode, [sigTrap, sigXfade]);
				// One-shot is always sig1 (loopMode irrelevant).
				var sig = Select.ar(loop, [sig1, sigLoop]);
				var env = EnvGen.kr(envCtl, gate, doneAction: 2);
				Out.ar(out, Pan2.ar(sig * env * amp, pan));
			}).add;


			// Voice-mode stereo SynthDef. Mirrors \ssvoice1 exactly except for:
			//   - two source buffers (buf0 = L, buf1 = R)
			//   - Balance2 stereo positioning
			// Pointer math is shared and derives sample rate / frame count from
			// buf0 (the loader always allocates L/R as a matched pair).
			SynthDef(\ssvoice2, {arg buf0, buf1, rate = 1, amp = 1, pan = 0, out = 0,
				                startPos = 0, dur = 1, gate = 1,
				                loop = 0, loopDir = 0, loopMode = 0,
				                loopStart = 0, loopEnd = 0,
				                loopXfade = 0;
				var envCtl = \env.kr(Env.newClear(8).asArray);
				var bufSR = BufSampleRate.kr(buf0);
				var bufFrames = BufFrames.kr(buf0);
				var startFrames = startPos * bufSR;
				var step = rate * BufRateScale.kr(buf0);
				var lEnd = Select.kr(loopEnd > 0, [bufFrames, loopEnd]);
				var lStart = loopStart;
				var llen = (lEnd - lStart).max(1);
				var halfLen = llen * 0.5;
				var fwd = Phasor.ar(0, step,     lStart, lEnd, lStart);
				var rev = Phasor.ar(0, step.neg, lStart, lEnd, lEnd);
				var tri = Phasor.ar(0, step.abs, 0, 2 * llen);
				var pal = lStart + (llen - (tri - llen).abs);
				var fwdAlt = Phasor.ar(0, step,     lStart, lEnd, lStart + halfLen);
				var revAlt = Phasor.ar(0, step.neg, lStart, lEnd, lEnd   - halfLen);
				var loopPtr  = Select.ar(loopDir, [fwd,    rev,    pal]);
				var loopPtr2 = Select.ar(loopDir, [fwdAlt, revAlt, pal]);
				var onePtr = Line.ar(startFrames, startFrames + bufFrames, dur);
				var ptr  = Select.ar(loop, [onePtr, loopPtr]);
				var ptr2 = Select.ar(loop, [onePtr, loopPtr2]);
				var sig1L = BufRd.ar(1, buf0, ptr,  loop: 1, interpolation: 4);
				var sig1R = BufRd.ar(1, buf1, ptr,  loop: 1, interpolation: 4);
				var sig2L = BufRd.ar(1, buf0, ptr2, loop: 1, interpolation: 4);
				var sig2R = BufRd.ar(1, buf1, ptr2, loop: 1, interpolation: 4);
				var ph = ((ptr - lStart) / llen).clip(0, 1);
				// Trapezoid window (same shape for L and R).
				var f = ((loopXfade * bufSR) / llen).max(0.0001);
				var winRaw = ((ph / f).clip(0, 1) * ((1 - ph) / f).clip(0, 1));
				var winEnabled = loop * (loopXfade > 0);
				var win = (winEnabled * winRaw) + (1 - winEnabled);
				var sigTrapL = sig1L * win;
				var sigTrapR = sig1R * win;
				// Equal-power 2-tap crossfade.
				var g1 = sin(ph * (pi/2));
				var g2 = cos(ph * (pi/2));
				var isPalin = (loopDir > 1);
				var sigSeamL = (sig1L * g1) + (sig2L * g2);
				var sigSeamR = (sig1R * g1) + (sig2R * g2);
				var sigXfadeL = ((1 - isPalin) * sigSeamL) + (isPalin * sig1L);
				var sigXfadeR = ((1 - isPalin) * sigSeamR) + (isPalin * sig1R);
				var sigLoopL = Select.ar(loopMode, [sigTrapL, sigXfadeL]);
				var sigLoopR = Select.ar(loopMode, [sigTrapR, sigXfadeR]);
				var sigL = Select.ar(loop, [sig1L, sigLoopL]);
				var sigR = Select.ar(loop, [sig1R, sigLoopR]);
				var env = EnvGen.kr(envCtl, gate, doneAction: 2);
				Out.ar(out, Balance2.ar(sigL * env * amp, sigR * env * amp, pan));
			}).add;
		})
	}

}