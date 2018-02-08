//Query Functions for SuperSampler by Allen Wu

SamplerQuery {

	classvar <> playing; //a temporary address for all sounds that is playing for applying .set() to sounds after it's triggered

	*initClass {
		playing = [];
		17.do{
			var midiChannel = Dictionary.new;
			playing = playing.add(midiChannel);
		};
	}




	//gather samples by using midi key numbers
	*getSamplesByKeynum {|sampler, args, filterFunc = true|
		var keyNums = args.keynums.asArray.flat;
		var texture = args.texture;
		var finalList = [];

		keyNums.asArray.do{|keyNum, keynumIndex|
			var sampleList = [];
			var keySign = keyNum.sign;
			var samplePrep = SamplerPrepare.new;

			samplePrep.bufServer = sampler.bufServer;
			keyNum = keyNum.abs;

			//find keyNums in the keyRanges of each sample sections, send the sample section information
			sampler.keyRanges.keysValuesDo{|thisSample, thisKeyRange|
				// for each section in the sample
				thisKeyRange.do{|thisSection, idx| //idx is the section indes within the sample
					if((keyNum <= thisSection[1]) && (keyNum >= thisSection[0]))
					{
						samplePrep.sample = thisSample;
						samplePrep.samplerName = sampler.name;
						samplePrep.duration = args.dur;
						samplePrep.section = idx;
						samplePrep.setRate(2**((keyNum - thisSample.keynum[idx])/12) * (keySign + 1 - keySign.abs));
						samplePrep.buffer = samplePrep.sample.activeBuffer[samplePrep.section];
						samplePrep.midiChannel = args.midiChannel;
						sampleList = sampleList.add(samplePrep)
					};
				}
			};

			//When nothing is found in the keyRange, find the closest keynum to be the buffer.
			if(sampleList.isEmpty)
			{
				var sortIndexes = Dictionary.new;

				sampler.samples.do{|thisSample, index|
					thisSample.keynum.do{|thisKeynum, idx|
						//address arrays in the form of [Which sample, Which section]
						sortIndexes.put(thisKeynum, [index, idx]);
					}
				};

				sortIndexes = sortIndexes.asSortedArray.flop;

				// sortIndexes[0]==keynums in sorted order
				// sortIndexes[1]==Index arrays in sorted order
				// address for the closest keynum will be:
				//sortIndexes[1][sortIndexes[0].indexIn(keyNum)]
				samplePrep.sample = sampler.samples[sortIndexes[1][sortIndexes[0].indexIn(keyNum)][0]];
				samplePrep.samplerName = sampler.name;
				samplePrep.duration = args.dur;
				samplePrep.section = sortIndexes[1][sortIndexes[0].indexIn(keyNum)][1];
				samplePrep.setRate(2**((keyNum - samplePrep.sample.keynum[samplePrep.section]) / 12) * (keySign + 1 - keySign.abs));
				samplePrep.buffer = samplePrep.sample.activeBuffer[samplePrep.section];
				//samplePrep.duration = args.dur;
				samplePrep.midiChannel = args.midiChannel;


				sampleList = sampleList.add(samplePrep)
			};


			//reduce samples by texture value, based on the distance of key Numbers.
			//Sample pitch closer to the key number gets picked first.
			sampleList = sampleList.sort({|a,b| (a.sample.keynum[a.section]-keyNum).abs < (b.sample.keynum[b.section]-keyNum).abs})[0..(texture !? {texture-1})];

			//make textures with minor pitch diviation if the size of samples doesn't reach the texture value.
			if(texture.isNumber){
				if(sampleList.size < texture) {
					var prepList = sampleList.wrapExtend(texture - sampleList.size);
					prepList.do{|thisSamplePrep, index|
						thisSamplePrep.setRate(2**((keyNum + rand2(0.3) - samplePrep.sample.keynum[samplePrep.section]) / 12) * (keySign + 1 - keySign.abs));
						sampleList = sampleList.add(thisSamplePrep);
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

				//sort samples by the attack time of the section, longer first
				playSamples = playSamples.sort({|a, b|
					var aAttack = if(a.rate.isPositive) {a.sample.attackDur[a.section]} {a.sample.releaseDur[a.section]};
					var bAttack = if(b.rate.isPositive) {b.sample.attackDur[b.section]} {b.sample.releaseDur[b.section]};
					(aAttack / a.rate.abs) > (bAttack / b.rate.abs)
				});

				//thisSample is a SamplePrepare object
				playSamples.do{|thisSample, index|
					var previousIndex = (index - 1).thresh(0);
					var previousSample = playSamples[previousIndex];
					var thisPeakTime, previousPeakTime;


					thisPeakTime = if(thisSample.rate.isPositive)
					{thisSample.attackDur / thisSample.rate.abs}
					{thisSample.releaseDur / thisSample.rate.abs};

					startpos = if(thisSample.rate.isPositive)
					{(thisSample.sample.attackDur[thisSample.section] - thisSample.attackDur[thisSample.section]).thresh(0)}
					{(thisSample.sample.releaseDur[thisSample.section] - thisSample.releaseDur[thisSample.section]).thresh(0)};

					previousPeakTime = if(previousSample.rate.isPositive)
					{previousSample.attackDur[previousSample.section] / previousSample.rate.abs}
					{previousSample.releaseDur[previousSample.section] / previousSample.rate.abs};

					waittime = (previousPeakTime - thisPeakTime).thresh(0);  //wait time before pitch bendenv.

					thisSample.wait = waittime * expand;
					thisSample.position = if(thisSample.rate.isNegative){(thisSample.sample.activeBuffer[thisSample.section][0].duration - startpos).thresh(0)}{startpos};
					thisSample.expand = args.expand;

					nElapsed = elapsed / globalDur;  //normalize elapsed time (0-1)
					nWait = waittime * expand / globalDur;
					nDur = thisSample.duration * expand / globalDur;

					thisSample.ampenv = args.ampenv.asEnv.subEnv(nElapsed + nWait, nDur).stretch.asArray;
					thisSample.panenv = args.panenv.asEnv.subEnv(nElapsed + nWait, nDur).stretch.asArray;

					thisSample.bendenv = args.bendenv;


					//pitch bendenv adjustments
					//variation with n in the front means "normalized"
					if(args.bendenv != #[1, 1, -99, -99, 1, 1, 1, 0])
					{
						var rBendEnv = args.bendenv.asEnv.reciprocal;
						//wait time equals to the integral of reciprocal bend envelope of the waiting time
						thisSample.wait = (rBendEnv.integral(nElapsed + nWait) - rBendEnv.integral(nElapsed)) * rBendEnv.copy.duration_(globalDur).integral;
						//local bend envelope for each sound
						thisSample.bendenv = args.bendenv.asEnv.subEnv(nElapsed + nWait, nDur).asArray;

						elapsed = elapsed + thisSample.wait;
					}
					{elapsed = elapsed + waittime;};
				}
			},


			//assign a peak time where the pick of sound gesture happens.  e.g. [\peakat, 3]
			\peakat,{
				var previousPeakTime = syncmode.asArray[1] ? 0; //initial peak time

				//sort samples by the attack time of the section, longer first
				playSamples = playSamples.sort({|a, b|
					var aAttack = if(a.rate.isPositive) {a.sample.attackDur[a.section]} {a.sample.releaseDur[a.section]};
					var bAttack = if(b.rate.isPositive) {b.sample.attackDur[b.section]} {b.sample.releaseDur[b.section]};
					(aAttack / a.rate.abs) > (bAttack / b.rate.abs)
				});



				playSamples.do{|thisSample, index|
					var thisPeakTime, adjust;

					//find the peak time of playing sample
					thisPeakTime = if(thisSample.rate.isPositive)
					{thisSample.sample.attackDur[thisSample.section] / thisSample.rate.abs}
					{thisSample.sample.releaseDur[thisSample.section] / thisSample.rate.abs};

					// wait time is the difference between previous peak time and this peak time
					adjust = previousPeakTime - thisPeakTime;
					thisSample.wait = adjust.thresh(0) * expand;

					//calculate time for env adjustments
					elapsed = if(index <= 1){0}{elapsed + thisSample.wait};

					// normalized time for envelope adjustments
					nElapsed = elapsed / globalDur;  //normalize elapsed time (0-1)
					nWait = if(index == 0){0}{thisSample.wait} / globalDur;
					nDur = thisSample.duration * expand / globalDur;

					// adjust for bendenv
					if(args.bendenv != #[1, 1, -99, -99, 1, 1, 1, 0])
					{
						var nAttackTime, bendEnvForAttackTime, attackDur, bendedAttackDur;

						//the time difference between original and bend sounds is the integral of reciprocal bend envelope
						//attackDur = if(thisSample.rate.isPositive){thisSample.attackDur}{thisSample.releaseDur};
						nAttackTime = thisPeakTime * expand / globalDur;
						bendEnvForAttackTime = args.bendenv.asEnv.subEnv(nElapsed + nWait, nAttackTime).stretch.asArray;
						bendedAttackDur = (thisPeakTime * bendEnvForAttackTime.asEnv.reciprocal.integral);


						adjust = adjust + (thisPeakTime - bendedAttackDur);
						thisPeakTime = bendedAttackDur;
						thisSample.wait = adjust.thresh(0) * expand;

						//now recalibrate normalized time for envelope adjustments
						elapsed = if(index <= 1){0}{elapsed + thisSample.wait};
						nElapsed = elapsed / globalDur;  //normalize elapsed time (0-1)
						nWait = if(index == 0){0}{thisSample.wait} / globalDur;
						nDur = thisSample.duration * expand / globalDur;

						thisSample.bendenv = args.bendenv.asEnv.subEnv(nElapsed + nWait, nDur).stretch.asArray;

						//thisSample.bendenv = args.bendenv;
					}
					{
						thisSample.bendenv = args.bendenv;
					};



					thisSample.expand = args.expand;
					thisSample.ampenv = args.ampenv;
					thisSample.panenv = args.panenv;

					thisSample.ampenv = args.ampenv.asEnv.subEnv(nElapsed + nWait, nDur).stretch.asArray;
					thisSample.panenv = args.panenv.asEnv.subEnv(nElapsed + nWait, nDur).stretch.asArray;



					thisSample.position = if(thisSample.rate.isPositive)
					{adjust.neg.thresh(0) * thisSample.rate.abs}
					{thisSample.buffer[0].duration - (adjust.neg.thresh(0) * thisSample.rate.abs)};

					thisPeakTime = (thisPeakTime - adjust.neg.thresh(0)).thresh(0);

					previousPeakTime = thisPeakTime;
				}
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


			//cut the beginning of sample file, start from the peak point
			\percussive,{
				var waittime = syncmode.asArray[1] ? 0;
				args.globalAttackDur = 0;
				args.globalDur = args.globalReleaseDur;

				playSamples.do{|thisSample, index|
					var thisPeakTime;
					var nDur = thisSample.duration / globalDur;

					thisPeakTime = thisSample.sample.attackDur[thisSample.section];

					thisSample.position = (thisPeakTime-0.01).thresh(0);
					thisSample.wait = waittime;
					waittime = 0;
					thisSample.expand = args.expand;
					thisSample.bendenv = args.bendenv;
					thisSample.ampenv = args.ampenv.asEnv.subEnv(0, nDur).stretch.asArray;
					thisSample.panenv = args.panenv.asEnv.subEnv(0, nDur).stretch.asArray;


					//adjust for pitchbendenv
					if(args.bendenv != #[1, 1, -99, -99, 1, 1, 1, 0]){
						thisSample.bendenv = args.bendenv.asEnv.subEnv(0, nDur).asArray;
					};
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

			//expand shorter sample to fit the largest sample
			\stratchshort,{
				var globalAttackDur = playSamples.collect({|thisSample, index| thisSample.sample.attackDur[thisSample.section]}).maxItem;
				var waittime = syncmode.asArray[1] ? 0;
				var startpos = 0;
				playSamples.do{|thisSample, index|
					var stratch = globalAttackDur / (thisSample.sample.attackDur[thisSample.section] / thisSample.rate.abs);
					thisSample.wait = waittime;
					waittime = 0;
					thisSample.position = if(thisSample.rate.isNegative){(thisSample.sample.activeBuffer[thisSample.section][0].duration-startpos).thresh(0)}{startpos};
					if(stratch != 1){thisSample.expand = stratch * (args.expand ? 1)}{thisSample.expand = args.expand;};
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