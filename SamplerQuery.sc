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
		var keyNums = args.keynums;
		var texture = args.texture;
		var finalList = [];

		keyNums.asArray.do{|keyNum, keynumIndex|
			var sampleList = [];
			var keySign = keyNum.sign;
			var samplePrep = SamplerPrepare.new;
			samplePrep.bufServer = sampler.bufServer;
			keyNum = keyNum.abs;

			//find keyNums in the keyRanges of each sample sections, send the sample section information
			sampler.keyRanges.do{|thisSample, index|
				thisSample.do{|thisSection, idx|
					if((keyNum <= thisSection[1]) && (keyNum >= thisSection[0]))
					{
						samplePrep.sample = sampler.samples[index];
						samplePrep.samplerName = sampler.name;
						samplePrep.setRate(2**((keyNum - sampler.samples[index].keynum[idx])/12) * keySign);
						samplePrep.section = idx;
						sampleList = sampleList.add(samplePrep)};
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
				// address for the clost keynum will be:
				//sortIndexes[1][sortIndexes[0].indexIn(keyNum)]
				samplePrep.sample = sampler.samples[sortIndexes[1][sortIndexes[0].indexIn(keyNum)][0]];
				samplePrep.samplerName = sampler.name;
				samplePrep.section = sortIndexes[1][sortIndexes[0].indexIn(keyNum)][1];
				samplePrep.setRate(2**((keyNum - samplePrep.sample.keynum[samplePrep.section]) / 12) * keySign);

				sampleList = sampleList.add(samplePrep)
			};

			//reduce samples by texture value, based on the distance of key Numbers.
			//Sample pitch closer to the key number gets picked first.
			sampleList = sampleList.sort({|a,b| (a.sample.keynum[a.section]-keyNum).abs < (b.sample.keynum[b.section]-keyNum).abs})[0..(texture !? {texture-1})];

			finalList = finalList ++ sampleList;
		};

		finalList = finalList.scramble[0..(texture !? {texture-1})];

		//args.setSamples(finalList); //send the list to SamplerArguments class and get global duration

		^finalList;  //list of SamplePrepare class
	}







	//====================================================
	//calculate starting time for each sample in a group
	*getPlayTime {arg args;  //args is a SamplerArguments class
		var playSamples = args.playSamples;
		var syncmode = args.syncmode;
		var globalDur = args.globalDur;
		var globalAttackDur = args.globalAttackDur;

		switch(syncmode.asArray[0].asSymbol,


			//keep the full length to samples, line up the peak time together
			\keeplength,{
				var waittime = 0, startpos = 0, elapsed = 0;

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
					{thisSample.sample.attackDur[thisSample.section] / thisSample.rate.abs}
					{thisSample.sample.releaseDur[thisSample.section] / thisSample.rate.abs};

					previousPeakTime = if(previousSample.rate.isPositive)
					{previousSample.sample.attackDur[previousSample.section] / previousSample.rate.abs}
					{previousSample.sample.releaseDur[previousSample.section] / previousSample.rate.abs};

					waittime = (previousPeakTime - thisPeakTime).thresh(0);  //wait time before pitch bend.

					thisSample.wait = waittime;
					thisSample.position = if(thisSample.rate.isNegative){thisSample.sample.activeBuffer[thisSample.section][0].duration}{startpos};
					thisSample.expand = args.expand;
					thisSample.bend = args.bend;


					//pitch bend adjustments
					//variation with n in the front means "normalized"
					if(args.bend != #[1, 1, -99, -99, 1, 1, 1, 0])
					{
						var nElapsed = elapsed / globalDur;  //normalize elapsed time (0-1)
						var nWait = waittime / globalDur;
						var nDur = thisSample.duration / globalDur;
						var rBendEnv = args.bend.asEnv.reciprocal;

						//wait time equals to the integral of reciprocal bend envelope
						thisSample.wait = (rBendEnv.integral(nElapsed + nWait) - rBendEnv.integral(nElapsed)) * rBendEnv.copy.duration_(globalDur).integral;
						//local bend envelope for each sound
						thisSample.bend = args.bend.asEnv.subEnv(nElapsed + nWait, nDur).asArray;

						elapsed = elapsed + thisSample.wait;
					}
					{elapsed = elapsed + waittime;};
				}
			},


			//assign a peak time where the pick of sound gesture happens
			\peakat,{

				var previousPeakTime = syncmode.asArray[1] ? globalAttackDur; //initial peak time
				var extraTime, nglobalAttackDur, rBendEnv, elapsed = 0;


				//1:  adjust for the global peak time after pitch bend
				//    variation with n in the front means "normalized", value from 0-1
				//-------- For pitch bend ------
				if(args.bend != #[1, 1, -99, -99, 1, 1, 1, 0]){
					extraTime = previousPeakTime - globalAttackDur;
					nglobalAttackDur = globalAttackDur / globalDur;
					//this might be a choice of design:
					//cut the bend envelope when some portion of audio needs to be cut from setting the peak time
					if(extraTime.isNegative){
						var nExtraTime = extraTime.abs / globalDur;
						args.bend = args.bend.asEnv.subEnv(nExtraTime, 1 - nExtraTime).asArray;
					};
					rBendEnv = args.bend.asEnv.reciprocal;
					previousPeakTime = (rBendEnv.integral(nglobalAttackDur) * rBendEnv.copy.duration_(globalDur).integral) + extraTime.thresh(0);
				};//-----------------------------


				//2:  sort samples by the attack time of the section, longer first
				playSamples = playSamples.sort({|a, b|
					var aAttack = if(a.rate.isPositive) {a.sample.attackDur[a.section]} {a.sample.releaseDur[a.section]};
					var bAttack = if(b.rate.isPositive) {b.sample.attackDur[b.section]} {b.sample.releaseDur[b.section]};
					(aAttack / a.rate.abs) > (bAttack / b.rate.abs)
				});


				//3:  find waiting time by difference of peak time
				playSamples.do{|thisSample, index|
					var thisPeakTime, waittime;
					thisPeakTime = if(thisSample.rate.isPositive)
					{thisSample.sample.attackDur[thisSample.section] / thisSample.rate.abs}
					{thisSample.sample.releaseDur[thisSample.section] / thisSample.rate.abs};

					//------for pitch bend-------
					if(args.bend != #[1, 1, -99, -99, 1, 1, 1, 0]){
						var nThisPeakTime = thisPeakTime / globalDur;
						var nElapsed = elapsed / globalDur;
						var nDur = thisSample.duration / globalDur;
						var nWait;

						thisPeakTime = rBendEnv.integral(nThisPeakTime + nElapsed) - rBendEnv.integral(nElapsed) * rBendEnv.copy.duration_(globalDur).integral;
						waittime = previousPeakTime - thisPeakTime;
						nWait = waittime / globalDur;
						thisSample.bend = args.bend.asEnv.subEnv(nElapsed + nWait, nDur).asArray;
						extraTime.postln;
						elapsed = elapsed + waittime - extraTime.thresh(0); //for the first time: elapsed will be 0
						extraTime = 0; //then discard the extra time
					}//-----------------------
					{waittime = previousPeakTime - thisPeakTime};


					thisSample.wait = waittime.thresh(0);
					thisSample.expand = args.expand;
					thisSample.position = if(thisSample.rate.isPositive){
						waittime.neg.thresh(0) * thisSample.rate.abs
					}
					{
						thisSample.sample.activeBuffer[thisSample.section][0].duration - (waittime.neg.thresh(0) * thisSample.rate.abs);
					};

					thisPeakTime = (thisPeakTime - waittime.neg.thresh(0)).thresh(0);
					previousPeakTime = thisPeakTime;



				}

			},

			//cut the beginning of sample file, start from the peak point
			\percussive,{
				var startpos = 0, waittime = 0;
				args.globalAttackDur = 0;
				args.globalDur = args.globalReleaseDur;

				playSamples.do{|thisSample, index|
					var thisPeakTime, startpos;
					var nDur = thisSample.duration / globalDur;

					thisPeakTime = thisSample.sample.attackDur[thisSample.section];

					thisSample.position = (thisPeakTime-0.01).thresh(0);
					thisSample.wait = waittime;
					thisSample.expand = args.expand;
					thisSample.bend = args.bend;

					//adjust for pitchbend
					if(args.bend != #[1, 1, -99, -99, 1, 1, 1, 0]){
						thisSample.bend = args.bend.asEnv.subEnv(0, nDur).asArray;
					};
				}
			},

			//conventional sample playing
			\nosorting,{
				var startpos = 0, waittime = 0;
				playSamples.do{|thisSample, index|
					thisSample.position = if(thisSample.rate.isNegative){thisSample.sample.activeBuffer[thisSample.section][0].duration}{startpos};
					thisSample.wait = 0;
					thisSample.expand = args.expand;
					thisSample.bend = args.bend;
				}
			},

			//expand shorter sample to fit the largest sample
			\stratchshort,{
				var globalAttackDur = playSamples.collect({|thisSample, index| thisSample.sample.attackDur[thisSample.section]}).maxItem;
				playSamples.do{|thisSample, index|
					var stratch = globalAttackDur / (thisSample.sample.attackDur[thisSample.section] / thisSample.rate.abs);
					thisSample.wait = 0;
					thisSample.position = 0;
					if(stratch != 1){thisSample.expand = stratch * (args.expand ? 1)}{thisSample.expand = args.expand;};
					thisSample.bend = args.bend;
				}
			}
		);
		//args.playSamples = playSamples;
		^playSamples;
	}



}