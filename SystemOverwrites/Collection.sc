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
