//Env dependent on wslib quark

+ Collection {

	lastMinIndex {  // return the last index of minimum value when there are more then one
		^this.size - 1 - this.reverse.minIndex;
	}

	lastMaxIndex {  // return the last index of minimum value when there are more then one
		^this.size - 1 - this.reverse.maxIndex;
	}


	removeDups {    // output a new collection without any duplicate values, by Halim Beere
		var result;
		result = this.species.new(this.size);
		this.do({ arg item;
			result.includes(item).not.if({ result.add(item) });
		});
		^result
	}

    removeNil {    // output a new collection without any empty or nil values
        var result;
        result = this.species.new(this.size);
        this.do({ arg item;
			(item.isNil || item.asArray.isEmpty).not.if({ result.add(item) });
        });
        ^result
    }

	// output a new collection with counts of occurrences within a range of deviation
	//the .inRange method is based on wslib Quark
	occurrencesArray {|deviation = 0|
		var output = [];
		this.do{|item, index|
			output = output.add(
				this.count{|itm, idx| itm.inRange(item - deviation, item + deviation)};
			);
		};
		^output;
	}

	//output most occurred items within a range of deviation
	mostOccurredItems{|deviation = 0|
		var output = [];
		var maxItem = this.occurrencesArray(deviation).maxItem;
		this.do{|item, index|
			if(this.occurrencesArray(deviation)[index] == maxItem)
			{output = output.add(item)};
		};
		^output;
	}

	// converts breakpoint envelope [x1,y1,x2,y2,...] to Env by Henrich Taube
	pairsAsEnv {|normalize = false, curve=\lin|
		var len, xary=[], yary=[], head, tail;
		if (this.isArray.not){Error("not an array of x y values:"+this).throw};
		len=this.size;
		if (len<4){Error("need at least two x y pairs:"+this).throw};
		if (len.even.not){Error("odd number of break points:"+this).throw};
		head=this.first;
		// split breakpoints into two arrays, normalize X
		this.pairsDo({|x,y|
			x=x-head;
			xary=xary.add(x);
			if (tail.isNumber) {
				if (x<tail) {
					Error("x values not in increasing order:" + this).throw;
				};
			};
			tail=x;
			yary=yary.add(y)});
		if (normalize && (tail==1).not) {xary=xary/tail;};
		xary=xary.differentiate.drop(1);
		^Env.new(yary,xary,curve);
	}


	//reciprocal of asArray on an Envelope. By Halim Beere
	// BE WARNED there is no error checking in this method.
 	asEnv {
		var array = this;
		var result;
		var curves = Array(array.size div: 4 - 1);
		(6, 10 .. array.size).do{ |i|
			if(array[i] == 5) {
				curves.add(array[i+1]);
               	 } {
                        curves.add(Env.shapeNames.findKeyForValue(array[i]));
              	 }
     		};
        	result = Env(array[0, 4 .. ], array[5, 9 .. ], curves,
                if(array[2] == -99) { nil } { array[2] },
                if(array[3] == -99) { nil } { array[3] });
		^result
	}

}


+ SequenceableCollection {
	//separate a Collection at the index point in an Array
	chop {|indexArray|
		var choppedArray = [];
		var organizedIndexArray = indexArray.asArray.asInteger.flat.removeDups.sort;
		if(organizedIndexArray.isEmpty,
			{^[this]},
			{choppedArray = choppedArray.add(this[..organizedIndexArray.first-1]);
				organizedIndexArray.doAdjacentPairs({|thisChopPoint, nextChopPoint|
				choppedArray = choppedArray.add(this[thisChopPoint..nextChopPoint-1]);
				});
				choppedArray = choppedArray.add(this[organizedIndexArray.last..]);
				^choppedArray.removeNil;
			}
		)
	}


	//make a flatted array a FloatArray
	flatAsFloatArray {
		var output = FloatArray.newClear(this.size);
		if(this.maxDepth != 1){Error("This Array is not a flat array, makesure it's maxDepth == 1").throw};
		this.do{|item, index|
			if(item.isKindOf(SimpleNumber).not){Error("This Array contains items that is not SimpleNumber").throw};
			output.put(index, item);
		};
		^output;
	}


	//rms values
	rms {|framehop = 1024|
		^this.clump(framehop).collect({|frame, index| sqrt(sum(frame ** 2 ) / frame.size)});
	}
}




+ Env {

	//Create an window envelope for cross fading
	*xfade {|dur = 3, fadeDur = 1, curve = \lin, releaseNode = nil, loopNode = nil, offset = 0|
		^this.new([0, 1, 1, 0], [(fadeDur - ((dur - (fadeDur * 2).neg.thresh(0)) * 0.5)).thresh(0), (dur - (fadeDur * 2)).thresh(0), (fadeDur - ((dur - (fadeDur * 2).neg.thresh(0)) * 0.5)).thresh(0)], curve: curve, releaseNode: releaseNode, loopNode: loopNode, offset: offset);
	}

	//same as xfade, different name, default with Welch window.
	*window {|dur = 3, fadeDur = 1, curve = \welch, releaseNode = nil, loopNode = nil, offset = 0|
		^this.new([0, 1, 1, 0], [(fadeDur - ((dur - (fadeDur * 2).neg.thresh(0)) * 0.5)).thresh(0), (dur - (fadeDur * 2)).thresh(0), (fadeDur - ((dur - (fadeDur * 2).neg.thresh(0)) * 0.5)).thresh(0)], curve: curve, releaseNode: releaseNode, loopNode: loopNode, offset: offset);
	}

	//Get peak time in an Envelope
	//this is using wslib Quark
	peakTime {|groupThresh = 0.32|
		var outcome = [];
		if(this.at(0.01) < this.at(0)){outcome = outcome.add(0)}; // Check if the first node is a peak
		this.timeLine.do{|thisNode, index|
			if( (this.at(thisNode - 0.01) < this.at(thisNode))	&& (this.at(thisNode) >= this.at(thisNode + 0.01)) )
			{
				if(outcome.isEmpty.not)
				{
					if(thisNode - outcome.last < 0.32)
					{
						if(this.at(thisNode) > this.at(outcome.last))
						{outcome = outcome.put(outcome.size-1, thisNode)};
					}
					{outcome = outcome.add(thisNode)};
				}
				{outcome = outcome.add(thisNode)};
			};
		};
		^outcome;
	}


	//export an Env to breakpoint envelope array [x1, y1, x2, y2....]. x represents timeline and y represents levels.
	//This one is dependent on wslib quark.
	asPairsArray {|normalize = false|
		var timeline = this.timeLine;
		if(normalize){timeline = timeline / timeline.last};
		^[timeline, this.levels].flop.flat;
	}



	//Output a portion of a FLAT envelope
	//This one also dependent on wslib quark.
	subEnv {|from  = 0, dur = 0|
		var outcome = [];
		var timeline = this.timeLine.asArray;
		var levels = this.levels.asArray;
		var start = timeline.indexOfGreaterThan(from);
		var end = timeline.indexInBetween(from + dur).floor.asInteger;

		if(start.isNil)
		{outcome = nil}
		{
			if(start > end) // no breakpoint in between the cutting point
			{
				outcome = [[0] ++ [dur] , [this.at(from)] ++ [this.at(from + dur)]].flop.flat.pairsAsEnv(curve: this.curves);
			}
			{
				var timecopy = timeline[start..end] - from;
				var levelcopy = levels[start..end];
				if(timecopy.last >= dur){timecopy.removeAt(timecopy.size-1); levelcopy.removeAt(levelcopy.size-1);}; //sometimes Float does not pass exact values

				outcome = [[0] ++ timecopy ++ [dur] , [this.at(from)] ++ levelcopy ++ [this.at(from + dur)]].flop.flat.pairsAsEnv(curve: this.curves);
			};
		};
		^outcome;
	}


	//integrating under an envelope for only multi breakpoint linear envelopes, no curved, sine or others.
	//if the integral time is more than the duration of the envelope, output the integral of the whole envelope.
	integral {|time|
		var area = 0;
		var durations = this.times;
		var levels = this.levels;
		var timeindex = this.timeLine.indexOfGreaterThan(time ?? this.timeLine.maxItem) ?? this.timeLine.lastIndex;
		var dur = min(time ?? this.duration, this.duration);


		//add all trapezoid areas for each breakpoint
		(timeindex-1).do({|index|
			area = area + (levels[index] + levels[index + 1] * durations[index] / 2);
		});

		//add final trapezoid where the assigned time sits in
		area = area + (levels[timeindex -1] + this.at(dur) * (dur - this.timeLine[timeindex - 1]) /2);
		^area;
	}


	//get a reciprocal function of an envelope
	reciprocal {
		var level = this.levels.reciprocal;
		^Env.new(level, this.times, this.curves, this.releaseNode, this.loopNode, this.offset);
	}


	//output a copy of inverted Env
	invert {
		var level = (this.levels.flat.maxItem + this.levels.flat.minItem) - this.levels;
		^Env.new(level, this.times, this.curves, this.releaseNode, this.loopNode, this.offset);
	}

	//output a copy of reversed Env
	reverse {
		^Env.new(this.levels.reverse, this.times.reverse, this.curves, this.releaseNode, this.loopNode, this.offset);
	}

	//Multiply the amplitude of two FLAT Envs
	* {|anotherEnv|
		var timeline = (this.timeLine.asArray ++ anotherEnv.timeLine.asArray).removeDups.sort;
		var amp = [];
		timeline.do{|time, index|
			amp = amp.add(this.at(time) * anotherEnv.at(time));
		};
		^[timeline, amp].flop.flat.pairsAsEnv;
	}

	//Envelope normalization
	normalize {|max = 1|
		^this.range(0, max);
	}
}


+ SoundFile {

	//Split a multichannel sound file into several mono sound files.
	//The splitted mono files will be stored in the same file folder and same file type
	//with the filename attached with channel number such as "filename_1.wav" and "filename_2.wav".
	split {
		var rawArray = FloatArray.newClear(this.numFrames*this.numChannels);
		var path = PathName(this.path);
		this.readData(rawArray);
		rawArray = rawArray.clump(this.numChannels).flop; //[[channel 1], [channel 2], ...]
		rawArray.do{|chanArray, index|
			var file = SoundFile.new.headerFormat_(this.headerFormat).sampleFormat_(this.sampleFormat).numChannels_(1);
			file.openWrite(path.pathOnly +/+ path.fileNameWithoutExtension ++ "_" ++ (index+1).asString ++ "." ++ path.extension);
			file.writeData(chanArray.flatAsFloatArray);
			file.close;
		}
	}

}


+ Buffer {
	//Split a stereo Buffer into an Array of two mono Buffers, not working!
	/*
	split {
		if(this.numChannels == 2){
			var output = [Buffer.alloc(this.server, this.numFrames), Buffer.alloc(this.server, this.numFrames)];
			this.loadToFloatArray(action:
				{
					|item, index|
					var data = item.clump(this.numChannels).flop;
					data.do{|frame, chn|
						output[chn].setn(0, frame);
					};
			});
			^output;
		}
	}
*/

	bufRateScale {
		^this.sampleRate / this.server.sampleRate;
	}
}