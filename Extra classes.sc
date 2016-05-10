//Env dependent on wslib quark


+ Collection {

	removeDups {    // output a new collection without any duplicate values
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

	//reciprocal of asArray on an Envelope.
	// BE WARNED there is not error checking in this method.
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


+SequenceableCollection {
	//separate a Collection at the index point in an Array
	chop {|indexArray|
		var choppedArray = [];
		var organizedIndexArray = indexArray.asArray.asInteger.flat.removeDups.sort; //removeDups is in "VKey Extra Classes.sc"
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
}


+Env {
	//this is using welib Quark
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
	//This one also dependent on welib quark.
	asPairsArray {|normalize = false|
		var timeline = this.timeLine;
		if(normalize){timeline = timeline / timeline.last};
		^[timeline, this.levels].flop.flat;
	}



	//Output a portion of an envelope, plus a crossfade time at beginning and end of envelope
	//This one also dependent on welib quark.
	subEnv {|from  = 0, dur = 0, xfade = 0|
		var outcome = [];
		var timeline = this.timeLine.asArray;
		var levels = this.levels.asArray;
		var start = timeline.indexOfGreaterThan(from).asInteger;
		var end = timeline.indexInBetween(from + dur).floor.asInteger;

		if(start.isNil)
		{outcome = nil}
		{
			var timecopy = timeline[start..end] - from + xfade;
			var levelcopy = levels[start..end];
			var copy = [timecopy, levelcopy].flop.flat;
			outcome = (outcome ++ [0, 0, xfade, this.at(from)] ++ copy ++ [xfade+dur, this.at(from+dur)] ++ [xfade*2+dur, 0]).pairsAsEnv(curve: this.curves);
		};
		^outcome;
	}

	//integrating under an envelope (only works on simple linear, non-sustaining Envs)
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

}