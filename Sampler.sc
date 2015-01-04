
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
		SynthDef(\key, {arg buf, rate = 1, startpos = 0;
			var source = PlayBuf.ar(2, buf, rate * BufRateScale.kr(buf), startPos: startpos * BufRateScale.kr(buf), doneAction: 2);
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
	getPlaySamples{|keyNum|
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
			// address for the clost keynum will be
			//sortIndexes[1][sortIndexes[0].indexIn(keyNum)]
			closestSample = samples[sortIndexes[1][sortIndexes[0].indexIn(keyNum)][0]];
			closestSection = sortIndexes[1][sortIndexes[0].indexIn(keyNum)][1];
			playRate = 2**((keyNum - closestSample.keynum[closestSection]) / 12);

			sampleList = sampleList.add([closestSample, closestSection, playRate])
		};

		^sampleList;
	}


	key{arg keynums, strategy = \keepLength, texture = nil;
		var playSamples = [];
		//generate a list of samples and section indexs to play
		keynums.asArray.do{|note, index|
			//the structure of sample is
			//[SampleDescript, section index, playRate]
			//sorting sample list by the distance of keynum from the sample keynum
			var sampleList = this.getPlaySamples(note).sort({|a,b| (a[0].keynum[a[1]]-note).abs < (b[0].keynum[b[1]]-note).abs})[0..(texture !? {texture-1})];
			sampleList.do{|sample, idx|
				//adding the keynum to play in the bufdata array for rate in the synth
				playSamples = playSamples.add(sample);
			};
		};

		switch(strategy.asSymbol,
			\keepLength,{
				//sort samples by the attack time of the section, longer first
				playSamples = playSamples.sort({|a, b| (a[0].attackDur[a[1]] * a[2]) > (b[0].attackDur[b[1]] * b[2])});
				playSamples.do{|thisSample, index|
					var yieldtime = 0, startpos = 0;
					var previousIndex = (index - 1).thresh(0);
					yieldtime = thisSample[0].peakTime;
				}
			}
		);

		Routine.run{
			playSamples.do{|thisSample, index|
				Synth(\key, [buf: thisSample[0].activeBuffer[thisSample[1]], rate: thisSample[2]]);
				thisSample[0].filename.postln;
			};
		}
	}

}//end of Sampler class


