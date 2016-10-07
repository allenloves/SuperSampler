//Env dependent on wslib quark

+ Collection {

	lastMinIndex {  // return the last index of minimum value when there are more then one
		^this.size - 1 - this.reverse.minIndex;
	}

	lastMaxIndex {  // return the last index of minimum value when there are more then one
		^this.size - 1 - this.reverse.maxIndex;
	}


	removeDups {    // output a new collection without any duplicate values, by Dr. Halim Beere
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

	// converts breakpoint envelope [x1,y1,x2,y2,...] to Env
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

	//reciprocal of asArray on an Envelope. By Dr. Halim Beere
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
}


+ Env {
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
		var start = timeline.indexOfGreaterThan(from).asInteger;
		var end = timeline.indexInBetween(from + dur).floor.asInteger;

		if(start.isNil)
		{outcome = nil}
		{
			var timecopy = timeline[start..end] - from;
			var levelcopy = levels[start..end];
			outcome = [timecopy, levelcopy].flop.flat.pairsAsEnv(curve: this.curves);
		};
		^outcome;
	}

	//integrating under an envelope (only works on simple linear, non-sustaining Envs) By Dr. Halim Beere
	integral {
		var xs, ys, points, area = 0;
		xs = this.times;
		ys = this.levels;
		points = ys.size - 1;
		points.do({|index|
			var length, height, temparea;
			length = xs.at(index);
			height = (ys.at(index + 1) + ys.at(index)) / 2;
			area = area + (length * height);
		});
		^area;
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

	//Multiply two FLAT Envs
	* {
		arg anotherEnv;
		var timeline = (this.timeLine.asArray ++ anotherEnv.timeLine.asArray).removeDups.sort;
		var amp = [];
		timeline.do{|time, index|
			amp = amp.add(this.at(time) * anotherEnv.at(time));
		};
		^[timeline, amp].flop.flat.pairsAsEnv;
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
	//Split a stereo Buffer into an Array of two mono Buffers
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
}