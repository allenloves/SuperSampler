//Query Functions for SuperSampler by Allen Wu

SamplerQuery {

	classvar <> playing; //a temporary dictionary for all sounds that is playing for applying .set() to sounds after it's triggered, it is set by midi channels.

	*initClass {
		playing = [];
		17.do{
			var midiChannel = Dictionary.new;
			playing = playing.add(midiChannel);
		};
	}

	//Gate off every playing voice tagged with one of the given gestureIDs.
	//Voices fade over the SynthDef kill-gate release (0.1s) then free themselves.
	*releaseGesture {|gestureIDs|
		var ids = gestureIDs.asArray;
		playing.do{|channelDict|
			channelDict.copy.do{|voice|
				if(ids.includes(voice.gestureID)) { voice.set(\gate, 0) };
			};
		};
	}

	//Peak-normalization gain for one (sample, section): align sample peaks to
	//SSampler.headroomRef when the owning sampler has normalize enabled.
	*normGainFor {|sampler, sample, section|
		^if(sampler.normalize == true)
		{SSampler.headroomRef / sample.peakAmp[section].max(1e-4)}
		{1};
	}

	//Array interpolation lookup for a stored RMS shape, normalized so its peak = 1.
	//Out-of-range positions return 0. Pure function -- no Env objects on the trigger path.
	*shapeLookup {|data, normPos|
		var idx, frac, a, b;
		if(normPos.isNil or: {normPos < 0} or: {normPos >= 1} or: {data.isNil} or: {data.size < 2}) {^0};
		idx = normPos * (data.size - 1);
		frac = idx.frac;
		idx = idx.asInteger;
		a = data[idx];
		b = data[(idx + 1).min(data.size - 1)];
		^(a + ((b - a) * frac)) / data.maxItem.max(1e-9);
	}

	//Introspection utility: estimated summed output amplitude of every playing voice
	//at absolute time tAbs, using each voice's analyzed RMS shape (linear sum).
	//Not wired into playback -- available for metering / composition logic.
	*predictedLevelAt {|tAbs|
		var total = 0;
		playing.do{|channelDict|
			channelDict.do{|voice|
				total = total + (voice.ampScale * voice.sample.ampShapeAt(voice.section, (tAbs - voice.onsetAbs) / voice.wallDur.max(1e-9)));
			};
		};
		^total;
	}

	//SynthDef envelope controls hold a fixed maximum of segments (Env.newClear(32)
	//for amp/pan/bend, 8 for posenv). Sending a longer array makes scsynth print
	//"envelope went past end of inputs" and read garbage beyond its wired inputs.
	//Oversized envelopes are resampled uniformly to fit; anything else passes through.
	*fitEnvArray {|envArray, maxSegs = 32|
		var env, dt;
		if(envArray.isKindOf(SequenceableCollection).not) {^envArray};
		if(((envArray.size - 4) / 4) <= maxSegs) {^envArray};
		env = envArray.asEnv;
		dt = env.duration / maxSegs;
		^Env((0..maxSegs).collect{|i| env.at(i * dt)}, Array.fill(maxSegs, dt)).asArray;
	}


	//Build one voice's local gesture-envelope: slice the normalized contour gEnv on
	//the NOMINAL timeline (window s0..s0+len, peak at s0+peakOffset — trim-aware, so
	//the contour stays glued to the aligned peak), then warp each breakpoint through
	//the bend map so the linear wall-time EnvGen reproduces the contour on the audio
	//content. An explicit breakpoint lands exactly on the peak instant.
	//Units: s0/len/peakOffset in nominal seconds; gEnv normalized (domain 1 = globalDur);
	//bendWall spans globalDur; onset = the voice's wall-clock start.
	*warpEnvWindow {|gEnv, s0, len, peakOffset, bendWall, onset, globalDur|
		var mOnset = bendWall.cumIntegral(onset);
		var pts = [0, peakOffset.clip(0, len), len];
		var taus, levels;
		gEnv.timeLine.do{|tl|
			var pNom = (tl * globalDur) - s0;
			if((pNom > 1e-9) and: {pNom < (len - 1e-9)}) { pts = pts.add(pNom) };
		};
		pts = pts.sort;
		levels = pts.collect{|pNom| gEnv.at((s0 + pNom) / globalDur)};
		taus = pts.collect{|pNom| bendWall.cumIntegralInverse(mOnset + pNom) - onset};
		^Env(levels, taus.differentiate.drop(1));
	}


	//Wall-clock offset from trigger to the gesture's aligned peak.
	*predictedPeakOffset {|args|
		var syncmode = args.syncmode.asArray;
		^switch(syncmode[0].asSymbol,
			\peakat, {syncmode[1] ? 0},
			\percussive, {0},
			{	//keeplength and others: globalAttackDur, bend-aware
				if(args.bendenv != #[1, 1, -99, -99, 1, 1, 1, 0])
				{args.bendenv.asEnv.copy.duration_(args.globalDur ? 1).cumIntegralInverse(args.globalAttackDur ? 0)}
				{args.globalAttackDur ? 0}
			}
		);
	}





	//gather samples by using midi key numbers
	*getSamplesByKeynum {|sampler, args, filterFunc = true|
		var keyNums = args.keynums.asArray.flat;
		var texture = args.texture;
		var finalList = [];

		keyNums.asArray.do{|keyNum, keynumIndex|
			var sampleList = [];
			var keySign = keyNum.sign;

			keyNum = keyNum.abs;

			//find keyNums in the keyRanges of each sample sections, send the sample section information.
			//A FRESH SamplerPrepare per matching section — the old code mutated and re-added one
			//shared object, so overlapping matches collapsed onto the last section and the
			//texture detune below retuned every voice.
			sampler.keyRanges.keysValuesDo{|thisSample, thisKeyRange|
				// for each section in the sample
				thisKeyRange.do{|thisSection, idx| //idx is the section indes within the sample
					if((keyNum <= thisSection[1]) && (keyNum >= thisSection[0]))
					{
						var samplePrep = SamplerPrepare.new;
						samplePrep.bufServer = sampler.bufServer;
						samplePrep.sample = thisSample;
						samplePrep.samplerName = sampler.name;
						samplePrep.duration = args.dur;
						samplePrep.section = idx;
						samplePrep.setRate(2**((keyNum - thisSample.keynum[idx])/12) * (keySign + 1 - keySign.abs));
						samplePrep.buffer = samplePrep.sample.activeBuffer[samplePrep.section];
						samplePrep.midiChannel = args.midiChannel;
						samplePrep.normGain = SamplerQuery.normGainFor(sampler, thisSample, idx);
						sampleList = sampleList.add(samplePrep)
					};
				}
			};

			//When nothing is found in the keyRange, find the closest keynum to be the buffer.
			if(sampleList.isEmpty)
			{
				var samplePrep = SamplerPrepare.new;
				var sortIndexes = Dictionary.new;

				sampler.samples.do{|thisSample, index|
					thisSample.keynum.do{|thisKeynum, idx|
						//address arrays in the form of [Which sample, Which section]
						sortIndexes.put(thisKeynum, [index, idx]);
					}
				};

				sortIndexes = sortIndexes.asSortedArray.flop;

				// sortIndexes[0] == keynums in sorted order
				// sortIndexes[1] == Index arrays in sorted order
				// address for the closest keynum will be:
				// sortIndexes[1][sortIndexes[0].indexIn(keyNum)]
				samplePrep.bufServer = sampler.bufServer;
				samplePrep.sample = sampler.samples[sortIndexes[1][sortIndexes[0].indexIn(keyNum)][0]];
				samplePrep.samplerName = sampler.name;
				samplePrep.duration = args.dur;
				samplePrep.section = sortIndexes[1][sortIndexes[0].indexIn(keyNum)][1];
				samplePrep.setRate(2**((keyNum - samplePrep.sample.keynum[samplePrep.section]) / 12) * (keySign + 1 - keySign.abs));
				samplePrep.buffer = samplePrep.sample.activeBuffer[samplePrep.section];
				//samplePrep.duration = args.dur;
				samplePrep.midiChannel = args.midiChannel;
				samplePrep.normGain = SamplerQuery.normGainFor(sampler, samplePrep.sample, samplePrep.section);


				sampleList = sampleList.add(samplePrep);
			};


			//reduce samples by texture value, based on the distance of key Numbers.
			//Sample pitch closer to the key number gets picked first.
			sampleList = sampleList.sort({|a,b| (a.sample.keynum[a.section]-keyNum).abs < (b.sample.keynum[b.section]-keyNum).abs})[0..(texture !? {texture-1})];

			//make textures with minor pitch diviation if the size of samples doesn't reach the texture value.
			//COPY each source prep (wrapExtend yields references) and detune the copy against its
			//OWN sample/section — the original voices keep their exact rates.
			if(texture.isNumber){
				if(sampleList.size < texture) {
					var prepList = sampleList.wrapExtend(texture - sampleList.size);
					prepList.do{|srcPrep, index|
						var copyPrep = srcPrep.copy;
						copyPrep.setRate(2**((keyNum + rand2(0.3) - copyPrep.sample.keynum[copyPrep.section]) / 12) * (keySign + 1 - keySign.abs));
						sampleList = sampleList.add(copyPrep);
					}
				}
			};

			finalList = finalList ++ sampleList;
		};

		finalList = finalList.scramble[0..(texture !? {texture-1})];

		args.setSamples(finalList); //send the list to SamplerArguments class and get global duration

		^finalList;  //list of SamplePrepare class


	}






	//each item samplerArray should contain 2 members, the SampleDescript object, and section number to play
	//about section number, see SampleDescript class
	//e.g.  [[SampleDescript1, 1], [SampleDescript2, 0], [SampleDescript3, 1], .....]
	// NO ERROR Proove here
	*getSamplesByArray{|samplerArray, args|
		var samplePrep = SamplerPrepare.new;
		samplerArray.do{|sample, section, index|
			var samplePrep = SamplerPrepare.new();
			samplePrep.bufServer = sample.bufferServer;
			samplePrep.sample = sample;
			samplePrep.buffer = sample.activeBuffer(section);
			samplePrep.section = section;
			samplePrep.setRate(2**(args.detune / 12));
			//NOTE: no `sampler` reference reaches this path (see signature above), so the
			//normalize-flag-driven gain in *normGainFor is unavailable here; normGain is
			//left at the neutral 1 rather than silently normalizing. This method is not
			//currently called anywhere in the codebase (pre-existing: it also never builds
			//or returns a result list) — flagged for a follow-up task if it's ever wired up.
			samplePrep.normGain = 1;
		}

	}



	//====================================================
	//calculate starting time for each sample in a group
	*getPlayTime {arg args;  //args is a SamplerArguments class
		var playSamples = args.playSamples;
		var syncmode = args.syncmode;
		var globalDur = args.globalDur;
		var globalAttackDur = args.globalAttackDur;
		var expand = args.expand ? 1;
		var nElapsed, nWait, nDur, elapsed = 0;  //normalized statuses


		switch(syncmode.asArray[0].asSymbol,
			//keep the full length to samples, line up the peak time together
			\keeplength,{
				var waittime = 0, startpos = 0;

				//Pitch bend timing is handled exactly via the cumulative integral of the
				//bend envelope (see below). Granular playback (expand) is excluded: its
				//grain pointer moves on a fixed Line, so bend changes pitch but not timing.
				var bendActive = (args.bendenv != #[1, 1, -99, -99, 1, 1, 1, 0]) and: {args.expand.isNil};

				//sort samples by the attack time of the section, longer first
				playSamples = playSamples.sort({|a, b|
					var aAttack = if(a.rate.isPositive) {a.sample.attackDur[a.section]} {a.sample.releaseDur[a.section]};
					var bAttack = if(b.rate.isPositive) {b.sample.attackDur[b.section]} {b.sample.releaseDur[b.section]};
					(aAttack / a.rate.abs) > (bAttack / b.rate.abs)
				});

				if(bendActive)
				{
					//Exact peak alignment under a pitch bend envelope.
					//Let b(t) be the bend (rate multiplier) over wall-clock time t, and
					//M(t) = cumIntegral(t) the source (nominal-playback) seconds consumed
					//by wall time t. A sound whose peak sits P source-seconds in, started
					//at onset o, peaks at wall time M⁻¹(M(o) + P). To land every peak on
					//T_peak = M⁻¹(P_max):  o_i = M⁻¹(P_max − P_i).
					var bendWall = args.bendenv.asEnv.copy.duration_(globalDur);
					var maxPeak = 0, prevOnset = 0;

					playSamples.do{|thisSample, index|
						var thisPeakTime, thisStartpos, onset, sourceDur, wallDur;

						thisPeakTime = if(thisSample.rate.isPositive)
						{thisSample.attackDur / thisSample.rate.abs}
						{thisSample.releaseDur / thisSample.rate.abs};

						if(index == 0) { maxPeak = thisPeakTime };  //sorted: first has the longest attack

						thisStartpos = if(thisSample.rate.isPositive)
						{(thisSample.sample.attackDur.[thisSample.section] - thisSample.attackDur[thisSample.section]).thresh(0)}
						{(thisSample.sample.releaseDur[thisSample.section] - thisSample.releaseDur[thisSample.section]).thresh(0)};

						onset = bendWall.cumIntegralInverse(maxPeak - thisPeakTime);
						sourceDur = thisSample.duration;  //nominal playback seconds (before bend)
						wallDur = bendWall.cumIntegralInverse(maxPeak - thisPeakTime + sourceDur) - onset;

						thisSample.wait = onset - prevOnset;
						prevOnset = onset;
						thisSample.position = if(thisSample.rate.isNegative){(thisSample.sample.activeBuffer[thisSample.section][0].duration - thisStartpos).thresh(0)}{thisStartpos};
						thisSample.expand = args.expand;
						thisSample.duration = wallDur;  //wall-clock length under bend (synth envelope length)

						//amp/pan contours: nominal-anchored window (peak glued at maxPeak),
						//breakpoints warped through the bend map — see *warpEnvWindow
						thisSample.ampenv = SamplerQuery.warpEnvWindow(args.ampenv.asEnv, maxPeak - thisPeakTime, sourceDur, thisPeakTime, bendWall, onset, globalDur).stretch.asArray;
						thisSample.panenv = SamplerQuery.warpEnvWindow(args.panenv.asEnv, maxPeak - thisPeakTime, sourceDur, thisPeakTime, bendWall, onset, globalDur).stretch.asArray;
						thisSample.bendenv = bendWall.subEnv(onset, wallDur).stretch.asArray;
					}
				}
				{
					//thisSample is a SamplePrepare object
					playSamples.do{|thisSample, index|
						var previousIndex = (index - 1).thresh(0);
						var previousSample = playSamples[previousIndex];
						var thisPeakTime, previousPeakTime;


						thisPeakTime = if(thisSample.rate.isPositive)
						{thisSample.attackDur / thisSample.rate.abs}
						{thisSample.releaseDur / thisSample.rate.abs};

						startpos = if(thisSample.rate.isPositive)
						{(thisSample.sample.attackDur.[thisSample.section] - thisSample.attackDur[thisSample.section]).thresh(0)}
						{(thisSample.sample.releaseDur[thisSample.section] - thisSample.releaseDur[thisSample.section]).thresh(0)};

						previousPeakTime = if(previousSample.rate.isPositive)
						{previousSample.attackDur[previousSample.section] / previousSample.rate.abs}
						{previousSample.releaseDur[previousSample.section] / previousSample.rate.abs};

						waittime = (previousPeakTime - thisPeakTime).thresh(0);

						thisSample.wait = waittime * expand;
						thisSample.position = if(thisSample.rate.isNegative){(thisSample.sample.activeBuffer[thisSample.section][0].duration - startpos).thresh(0)}{startpos};
						thisSample.expand = args.expand;

						nElapsed = elapsed / globalDur;  //normalize elapsed time (0-1)
						nWait = waittime * expand / globalDur;
						nDur = thisSample.duration * expand / globalDur;

						thisSample.ampenv = args.ampenv.asEnv.subEnv(nElapsed + nWait, nDur).stretch.asArray;
						thisSample.panenv = args.panenv.asEnv.subEnv(nElapsed + nWait, nDur).stretch.asArray;

						//granular (expand) playback reaches here with a live bendenv: bend changes
						//grain pitch but not timing — still slice each voice's window of the
						//gesture-level bend contour, like ampenv/panenv above.
						thisSample.bendenv = if(args.bendenv != #[1, 1, -99, -99, 1, 1, 1, 0])
						{args.bendenv.asEnv.subEnv(nElapsed + nWait, nDur).stretch.asArray}
						{args.bendenv};

						elapsed = elapsed + (waittime * expand);  //gesture-relative onset accumulation
					}
				};
			},


			//assign a peak time where the pick of sound gesture happens.  e.g. [\peakat, 3]
			\peakat,{
				//the target is WALL-CLOCK seconds; the scheduling chain below works in
				//nominal (pre-expand) time and multiplies waits by expand at the end,
				//so convert the target into nominal time here (peaks land at target,
				//not at target * expand)
				var previousPeakTime = (syncmode.asArray[1] ? 0) / expand; //initial peak time

				//Pitch bend timing handled exactly via the cumulative bend integral;
				//granular playback (expand) excluded — see \keeplength note.
				var bendActive = (args.bendenv != #[1, 1, -99, -99, 1, 1, 1, 0]) and: {args.expand.isNil};

				//sort samples by the attack time of the section, longer first
				playSamples = playSamples.sort({|a, b|
					var aAttack = if(a.rate.isPositive) {a.sample.attackDur[a.section]} {a.sample.releaseDur[a.section]};
					var bAttack = if(b.rate.isPositive) {b.sample.attackDur[b.section]} {b.sample.releaseDur[b.section]};
					(aAttack / a.rate.abs) > (bAttack / b.rate.abs)
				});

				if(bendActive)
				{
					//Exact peak alignment on the user-assigned wall-clock peak time.
					//M(t) = cumIntegral of the bend env; sourceAtTarget = M(T_peak) is the
					//source amount consumable before the target. A sound with peak P_i:
					//  P_i <= sourceAtTarget → start at o_i = M⁻¹(sourceAtTarget − P_i)
					//  P_i  > sourceAtTarget → start at 0 and trim the source start so the
					//                          remaining attack exactly fills M(T_peak).
					var bendWall = args.bendenv.asEnv.copy.duration_(globalDur);
					var targetPeak = previousPeakTime;
					var sourceAtTarget = bendWall.cumIntegral(targetPeak);
					var prevOnset = 0;

					playSamples.do{|thisSample, index|
						var pkFull, trimNominal, onset, remainSource, wallDur;

						pkFull = if(thisSample.rate.isPositive)
						{thisSample.sample.attackDur[thisSample.section] / thisSample.rate.abs}
						{thisSample.sample.releaseDur[thisSample.section] / thisSample.rate.abs};

						if(pkFull <= sourceAtTarget)
						{
							onset = bendWall.cumIntegralInverse(sourceAtTarget - pkFull);
							trimNominal = 0;
						}
						{
							onset = 0;
							trimNominal = pkFull - sourceAtTarget;
						};

						remainSource = (thisSample.sample.activeBuffer[thisSample.section][0].duration / thisSample.rate.abs) - trimNominal;
						wallDur = bendWall.cumIntegralInverse(bendWall.cumIntegral(onset) + remainSource) - onset;

						thisSample.wait = (onset - prevOnset).thresh(0);
						prevOnset = onset;
						thisSample.expand = args.expand;
						thisSample.duration = wallDur;  //wall-clock length under bend (synth envelope length)
						thisSample.position = if(thisSample.rate.isPositive)
						{trimNominal * thisSample.rate.abs}
						{thisSample.buffer[0].duration - (trimNominal * thisSample.rate.abs)};

						//amp/pan contours: nominal-anchored window (peak glued at globalAttackDur,
						//trim skips the same leading portion), breakpoints warped through the bend map
						thisSample.ampenv = SamplerQuery.warpEnvWindow(args.ampenv.asEnv, ((args.globalAttackDur ? 0) - (pkFull - trimNominal)).max(0), remainSource, pkFull - trimNominal, bendWall, onset, globalDur).stretch.asArray;
						thisSample.panenv = SamplerQuery.warpEnvWindow(args.panenv.asEnv, ((args.globalAttackDur ? 0) - (pkFull - trimNominal)).max(0), remainSource, pkFull - trimNominal, bendWall, onset, globalDur).stretch.asArray;
						thisSample.bendenv = bendWall.subEnv(onset, wallDur).stretch.asArray;
					}
				}
				{
					playSamples.do{|thisSample, index|
						var thisPeakTime, adjust, trimNominal, remainingPre, actualDur, nStart;

						//find the peak time of playing sample
						thisPeakTime = if(thisSample.rate.isPositive)
						{thisSample.sample.attackDur[thisSample.section] / thisSample.rate.abs}
						{thisSample.sample.releaseDur[thisSample.section] / thisSample.rate.abs};

						// wait time is the difference between previous peak time and this peak time
						adjust = previousPeakTime - thisPeakTime;
						thisSample.wait = adjust.thresh(0) * expand;

						//an early target trims the source start; the voice then plays only its
						//remaining pre-peak portion plus everything after the peak
						trimNominal = adjust.neg.thresh(0);
						remainingPre = thisPeakTime - trimNominal;
						actualDur = (thisSample.duration - trimNominal).max(0.001);

						//Envelopes are anchored on the NOMINAL gesture timeline (domain globalDur,
						//aligned peak at globalAttackDur): a trimmed voice skips the same leading
						//portion of the envelopes, so the gesture contour stays glued to the peak
						//(e.g. Env([3,0,3])'s central zero lands exactly on the aligned peak when
						//the nominal gesture is symmetric).
						nStart = (((args.globalAttackDur ? 0) - (remainingPre * expand)) / globalDur).thresh(0);
						nDur = actualDur * expand / globalDur;

						thisSample.expand = args.expand;
						thisSample.duration = actualDur;  //synth envelope length = what actually sounds

						thisSample.ampenv = args.ampenv.asEnv.subEnv(nStart, nDur).stretch.asArray;
						thisSample.panenv = args.panenv.asEnv.subEnv(nStart, nDur).stretch.asArray;
						//granular (expand) playback: slice the gesture-level bend contour per voice.
						thisSample.bendenv = if(args.bendenv != #[1, 1, -99, -99, 1, 1, 1, 0])
						{args.bendenv.asEnv.subEnv(nStart, nDur).stretch.asArray}
						{args.bendenv};

						thisSample.position = if(thisSample.rate.isPositive)
						{trimNominal * thisSample.rate.abs}
						{thisSample.buffer[0].duration - (trimNominal * thisSample.rate.abs)};

						thisPeakTime = (thisPeakTime - adjust.neg.thresh(0)).thresh(0);

						previousPeakTime = thisPeakTime;
					}
				};
			},


			// peakat wiwth pitch bend support
			// \peakat2,{
			//
			// 	var previousPeakTime = syncmode.asArray[1] ? globalAttackDur; //assigned peak time by the user
			// 	var extraTime, nglobalAttackDur, rBendEnv, elapsed = 0;
			//
			//
			// 	//1:  adjust for the global peak time after pitch bend
			// 	//    variation with n in the front means "normalized", value from 0-1
			// 	//-------- For pitch bend ------
			// 	if(args.bendenv != #[1, 1, -99, -99, 1, 1, 1, 0]){
			// 		var globalAttackAfterBend;
			// 		rBendEnv = args.bendenv.asEnv.reciprocal;
			// 		globalAttackAfterBend = args.bendenv.asEnv.copy.duration_(globalDur).reciprocal.integral(globalAttackDur);
			// 		extraTime = previousPeakTime - globalAttackAfterBend;
			// 	};//-----------------------------
			//
			//
			// 	//2:  sort samples by the attack time of the section, longer first
			// 	playSamples = playSamples.sort({|a, b|
			// 		var aAttack = if(a.rate.isPositive) {a.sample.attackDur[a.section]} {a.sample.releaseDur[a.section]};
			// 		var bAttack = if(b.rate.isPositive) {b.sample.attackDur[b.section]} {b.sample.releaseDur[b.section]};
			// 		(aAttack / a.rate.abs) > (bAttack / b.rate.abs)
			// 	});
			//
			//
			// 	//3:  find waiting time by difference of peak time
			// 	playSamples.do{|thisSample, index|
			// 		var thisPeakTime, waittime;
			// 		thisPeakTime = if(thisSample.rate.isPositive)
			// 		{thisSample.sample.attackDur[thisSample.section] / thisSample.rate.abs}
			// 		{thisSample.sample.releaseDur[thisSample.section] / thisSample.rate.abs};
			//
			//
			//
			// 		//------for pitch bend, adjust the wait time based on play rate
			// 		if(args.bendenv != #[1, 1, -99, -99, 1, 1, 1, 0]){
			// 			var nThisPeakTime = thisPeakTime / globalDur;
			// 			var nElapsed = elapsed / globalDur;
			// 			var nDur = thisSample.duration / globalDur;
			// 			var nWait;
			//
			// 			thisPeakTime = rBendEnv.integral(nThisPeakTime + nElapsed) - rBendEnv.integral(nElapsed) * rBendEnv.copy.duration_(globalDur).integral;
			// 			waittime = previousPeakTime - thisPeakTime;
			//
			// 			nWait = waittime / globalDur;
			// 			thisSample.bendenv = args.bendenv.asEnv.subEnv(nElapsed + nWait, nDur).asArray;
			// 			elapsed = elapsed + waittime; //for the first time: elapsed will be 0
			// 			extraTime = 0; //then discard the extra time
			// 		}//-----------------------
			// 		{waittime = previousPeakTime - thisPeakTime; thisSample.bendEnv = args.bendEnv;};
			//
			//
			// 		thisSample.wait = waittime.thresh(0);
			// 		thisSample.expand = args.expand;
			// 		thisSample.position = if(thisSample.rate.isPositive){
			// 			rBendEnv.integral(waittime.neg.thresh(0) * thisSample.rate.abs);
			// 		}
			// 		{
			// 			thisSample.buffer[0].duration - rBendEnv.integral(waittime.neg.thresh(0) * thisSample.rate.abs);
			// 		};
			//
			// 		thisPeakTime = (thisPeakTime - waittime.neg.thresh(0)).thresh(0);
			// 		previousPeakTime = thisPeakTime;
			//
			//
			//
			// 	}
			//
			// },


			//cut the beginning of sample file, start from the peak point.
			//(The former Routine.run wrapper is gone: it contained no waits, and it made
			//these assignments asynchronous — racing the playback Routine in playArgs.)
			\percussive,{
				var waittime = syncmode.asArray[1] ? 0;
				args.globalAttackDur = 0;
				args.globalDur = args.globalReleaseDur;
				playSamples.do{|thisSample, index|
					var thisPeakTime;
					var nDur = thisSample.duration / globalDur;

					thisPeakTime = thisSample.sample.attackDur[thisSample.section];

					thisSample.position = (thisPeakTime-0.01).thresh(0);

					//Reversed voices play backward from the peak into the attack, which is
					//near-silence for short attacks — floor the start position so at least
					//~0.03s (audible seconds, i.e. scaled by |rate|) of reversed transient
					//sounds, and free the voice when the snippet ends.
					if(thisSample.rate.isPositive.not) {
						var bufDur = thisSample.sample.activeBuffer[thisSample.section][0].duration;
						thisSample.position = thisSample.position.max(0.03 * thisSample.rate.abs).min(bufDur);
						thisSample.duration = thisSample.position / thisSample.rate.abs;
					};

					thisSample.wait = waittime;
					thisSample.expand = args.expand;
					thisSample.bendenv = args.bendenv;
					thisSample.ampenv = args.ampenv.asEnv.subEnv(0, nDur).stretch.asArray;
					thisSample.panenv = args.panenv.asEnv.subEnv(0, nDur).stretch.asArray;


					//adjust for pitchbendenv
					if(args.bendenv != #[1, 1, -99, -99, 1, 1, 1, 0]){
						thisSample.bendenv = args.bendenv.asEnv.subEnv(0, nDur).asArray;
					};
					waittime = 0;
				}
			},

			//conventional sample playing
			\nosorting,{
				var startpos = 0, waittime = syncmode.asArray[1] ? 0;
				playSamples.do{|thisSample, index|
					thisSample.position = if(thisSample.rate.isNegative){(thisSample.sample.activeBuffer[thisSample.section][0].duration-startpos).thresh(0)}{startpos};
					thisSample.wait = waittime;
					waittime = 0;
					thisSample.expand = args.expand;
					thisSample.bendenv = args.bendenv;
					thisSample.ampenv = args.ampenv;
					thisSample.panenv = args.panenv;

				}
			},

			//expand shorter sample to fit the largest sample.
			//The peak can sit anywhere in the file (a crescendo peaks near the end) and
			//negative keys traverse the buffer backwards, so pre-peak and post-peak lengths
			//are aligned SEPARATELY: every voice reaches the peak at globalAttackDur and
			//ends at globalAttackDur + globalReleaseDur, via a two-segment grain-position
			//envelope (the pointer stays continuous — only its speed changes at the peak).
			\stretchshort,{
				var waittime = syncmode.asArray[1] ? 0;
				//pre/post-peak maxima in playback order (rate-sign aware, from getGlobalDur),
				//taken back to nominal (un-expanded) seconds.
				var globalAttack = args.globalAttackDur / expand;
				var globalRelease = args.globalReleaseDur / expand;
				var totalNominal = (globalAttack + globalRelease).max(1e-9);

				playSamples.do{|thisSample, index|
					var pre = if(thisSample.rate.isPositive) {thisSample.attackDur} {thisSample.releaseDur} / thisSample.rate.abs;
					var post = if(thisSample.rate.isPositive) {thisSample.releaseDur} {thisSample.attackDur} / thisSample.rate.abs;
					var bufDur = thisSample.sample.activeBuffer[thisSample.section][0].duration;
					var peakNorm = (thisSample.sample.attackDur[thisSample.section] / bufDur).clip(0, 1);

					thisSample.wait = waittime;
					waittime = 0;
					thisSample.position = if(thisSample.rate.isNegative){bufDur}{0};

					if(((pre - globalAttack).abs < 1e-9) and: {(post - globalRelease).abs < 1e-9})
					{
						//already the longest on both sides — plain playback, nothing to stretch
						thisSample.expand = args.expand;
						thisSample.posenv = nil;
					}
					{
						thisSample.expand = args.expand ? 1;
						thisSample.duration = totalNominal;
						thisSample.posenv = if(thisSample.rate.isPositive)
						{Env([0, peakNorm, 0.95], [globalAttack, globalRelease] / totalNominal).asArray}
						{Env([1, peakNorm, 0], [globalAttack, globalRelease] / totalNominal).asArray};
					};

					thisSample.bendenv = args.bendenv;
					thisSample.ampenv = args.ampenv;
					thisSample.panenv = args.panenv;
				}
			},


			//start playing samples at the designated time.  e.g. [\startat, 3]
			\startat,{
				var startPoint = syncmode.asArray[1] ? 0;
				var startpos = 0, waittime = 0;
				var previousPeakTime;

				//sort samples by the attack time of the section, longer first
				playSamples = playSamples.sort({|a, b|
					var aAttack = if(a.rate.isPositive) {a.sample.attackDur[a.section]} {a.sample.releaseDur[a.section]};
					var bAttack = if(b.rate.isPositive) {b.sample.attackDur[b.section]} {b.sample.releaseDur[b.section]};
					(aAttack / a.rate.abs) > (bAttack / b.rate.abs)
				});

				//initial peak time with the first sample
				previousPeakTime = ((playSamples[0].sample.attackDur[playSamples[0].section] / playSamples[0].rate) - (startPoint * playSamples[0].rate)).thresh(0);

				playSamples.do{|thisSample, index|
					var thisPeakTime = (thisSample.sample.attackDur[thisSample.section] / thisSample.rate);
					var adjust = previousPeakTime - thisPeakTime;

					waittime = adjust.thresh(0);
					startpos = adjust.neg.thresh(0) * thisSample.rate;

					thisPeakTime = (thisPeakTime - adjust.neg.thresh(0)).thresh(0);

					//("thisPeakTime =" + thisPeakTime).postln;
					thisSample.wait = waittime;
					thisSample.position = startpos;
					thisSample.expand = args.expand;
					thisSample.bendenv = args.bendenv;
					thisSample.ampenv = args.ampenv;
					thisSample.panenv = args.panenv;

					previousPeakTime = thisPeakTime;

				}
			};

		);
		//args.playSamples = playSamples;
		^playSamples;
	}



}