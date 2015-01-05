
//Instance of SamplerDB is a database of multiple Sampler
//The data structure of SamplerDB Instance is a Dictionary in this format
// [ \Sampler_Name -> Sampler_Instance,  \Sampler_Name -> Sampler_Instance,  ...]
SamplerDB{
	classvar <dbs;  //System log for all sampler databases.
	var <label;
	var <samplers;  //database of Samplers

	*new{arg dbname = \default;
		^super.new.init(dbname);
	}

	*initClass{
		dbs = Dictionary.new;
	}

	*isLoaded{arg dbname;
		^dbs.at(dbname.asSymbol).isNil.not;
	}

	*free {
		dbs = nil;
	}

	init{arg dbname;
		label = dbname;
		if(this.class.dbs.at(label).isNil.not)
		{
			"Overwritting existing database".postln;
			this.class.dbs.at(label).free;
			this.class.dbs.put(label, this)
		}
		{this.class.dbs.put(label, this)};

		samplers = Dictionary.new;
	}

	put{arg name, sampler;
		samplers.put(name.asSymbol, sampler);
	}


	//TODO: Free all Samplers in the database.
	free{
		dbs.removeAt(label);
		samplers = nil;
	}
}//End of SamplerDB class



//instance of Sampler is a database of multiple SampleDescript
Sampler{
	var <dbs;  // a SamplerDB instance.
	var <filenames;
	var <samples;
	var <activeSamples;
	//                           |------ Each Sample -------------|	 |-----Each Sample---
	//                            |--Section---|  |--Section---|      |--Section---|
	var <keyRanges;// in format [[[upper, lower], [upper, lower]..], [[upper, lower], [upper, lower]...], ....]


	*new{arg name, dbname = \default;
		^super.new.init(name, dbname);
	}

	*initClass{
		StartUp.add({
			SynthDef(\key, {arg buf, rate = 1, startPos = 0;
				var source = PlayBuf.ar(1, buf, rate * BufRateScale.kr(buf), startPos: startPos * BufSampleRate.kr(buf), doneAction: 2);
				Out.ar(0, source);
			}).add;
		})
	}

	db{arg name;
		if(name.isNil.not)
		{
			var it = dbs.samplers.at(name);
			if (it.isNil) {
				Error("This sampler does not exist: " + name).throw;
			};
			^it
		}
		{
			var it = dbs.samplers;
			if (it.isNil) {
				Error("This sampler does not exist: " + name).throw;
			};
			^it
		}
	}

	init{arg name, dbname;
		if(name.isNil){Error("A sampler name is needed").throw;};
		if(SamplerDB.isLoaded(dbname))
		{
			dbs = SamplerDB.dbs.at(dbname);
		}
		{
			dbs = SamplerDB.new(dbname);

		};
		dbs.put(name.asSymbol, this);
	}

	load {arg soundfiles, server = Server.default, filenameAsKeynum = true;
		Routine.run{
			var dict = Dictionary.newFrom([filenames, samples].flop.flat);
			soundfiles.do{|filename, index|
				var sample;
				if(dict[filename.asSymbol].isNil.not)
				{"This file is already loaded, reloading".postln;
					dict[filename.asSymbol].free;
				};
				sample = SampleDescript(filename, loadToBuffer: true, filenameAsNote: filenameAsKeynum);
				dict.put(filename.asSymbol, sample);
			};
			dict = dict.asSortedArray.flop;
			filenames = dict[0];
			samples = dict[1];
			this.setKeyRanges;
		}
	}

	setKeyRanges{arg strategy = \keynumRadious, infoArray = [10];
		keyRanges = [];
		switch(strategy.asSymbol,
			\keynumRadious,{//given a range radious from the keynum of each sample sections.
				samples.do{|thisSample, index|
					var radious = infoArray.asArray.wrapAt(index);
					keyRanges = keyRanges.add([(thisSample.keynum - radious).thresh(0), thisSample.keynum + radious].flop)
				};
			},
			\fullRange,{//every sample is responded in full range of midi key number.
				samples.do{|thisSample, index|
					var sectionRanges = [];
					thisSample.keynum.do{|thisSection, idx|
						sectionRanges = sectionRanges.add([0, 127]);
					};
					keyRanges = keyRanges.add(sectionRanges);
				}
			},
			\keynumOnly,{//only respond to the keynum
				samples.do{|thisSample, index|
					var thisKeynum = thisSample.keynum;
					keyRanges = keyRanges.add([thisKeynum, thisKeynum].flop)
				}
			}
		)
	}


	//get a list of SampleDescripts and it's keynum of each active buffer inside the key range of provided keynum.
	//for each qualified buffers, ourput a bufData array of [SampleDescript, section index, playRate]
	//Out put will be [[bufferData1], [bufferData2], .....]
	getPlaySamples{|keyNums, texture = nil|
		var finalList = [];
		keyNums.asArray.do{|keyNum, keynumIndex|
			var sampleList = [];
			keyRanges.do{|thisSample, index|
				thisSample.do{|thisSection, idx|
					var playRate;
					if((keyNum <= thisSection[1]) && (keyNum >= thisSection[0]))
					{
						playRate = 2**((keyNum - samples[index].keynum[idx])/12);
						sampleList = sampleList.add([samples[index], idx, playRate])};
				}
			};

			//When nothing is found in the keyRange, find the closest keynum to be the buffer.
			if(sampleList.isEmpty)
			{
				var sortIndexes = Dictionary.new;
				var closestSample, closestSection, playRate;
				samples.do{|thisSample, index|
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
				closestSample = samples[sortIndexes[1][sortIndexes[0].indexIn(keyNum)][0]];
				closestSection = sortIndexes[1][sortIndexes[0].indexIn(keyNum)][1];
				playRate = 2**((keyNum - closestSample.keynum[closestSection]) / 12);

				sampleList = sampleList.add([closestSample, closestSection, playRate])
			};

			//reduce samples by texture value, based on the distance of key Numbers.
			sampleList = sampleList.sort({|a,b| (a[0].keynum[a[1]]-keyNum).abs < (b[0].keynum[b[1]]-keyNum).abs})[0..(texture !? {texture-1})];

			finalList = finalList ++ sampleList;
		}

		^finalList;
	}

	//the structure of sampleList is
	//[SampleDescript, section index, playRate]
	getPlayTime{arg playSamples, strategy = \keepLength;
		var yieldtime = 0, startpos = 0;
		switch(strategy.asSymbol,
			\keepLength,{
				//sort samples by the attack time of the section, longer first
				playSamples = playSamples.sort({|a, b| (a[0].attackDur[a[1]] * a[2]) > (b[0].attackDur[b[1]] * b[2])}); // why less than?
				playSamples.do{|thisSample, index|
					var previousIndex = (index - 1).thresh(0);
					var previousSample = playSamples[previousIndex];
					var thisPeakTime = (thisSample[0].peakTime[thisSample[1]] - thisSample[0].startTime[thisSample[1]]) * thisSample[2];
					var previousPeakTime = (previousSample[0].peakTime[previousSample[1]] - previousSample[0].startTime[previousSample[1]]) * previousSample[2];
					yieldtime = yieldtime + (previousPeakTime - thisPeakTime).abs;
					playSamples[index] = playSamples[index] ++ yieldtime ++ startpos;
				}
			},
			\percussive,{
				playSamples.do{|thisSample, index|
					var thisPeakTime = thisSample[0].peakTime[thisSample[1]];
					startpos = thisPeakTime - 0.05;
					playSamples[index] = playSamples[index] ++ yieldtime ++ startpos;
				}
			},
			\nosorting,{
				playSamples.do{|thisSample, index|
					playSamples[index] = playSamples[index] ++ yieldtime ++ startpos;
				}
			}
		);
		^playSamples;
	}


	key{arg keynums, strategy = \keepLength, texture = nil;
		var playSamples = this.getPlaySamples(keynums.asArray, texture);
		playSamples = this.getPlayTime(playSamples, strategy: strategy);

		//for Each Sample, the dataStructure is
		//[SampleDescript, section index, play rate, yield time, start position]
		Routine.run{
			playSamples.do{|thisSample, index|
				thisSample[3].yield;
				Synth(\key, [buf: thisSample[0].activeBuffer[thisSample[1]], rate: thisSample[2], startPos: thisSample[4]]);
				thisSample[0].activeBuffer[thisSample[1]].path.postln;
				thisSample.postln;
			};
		}
	}


}//end of Sampler class


