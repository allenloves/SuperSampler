//Env dependent on wslib quark


+ Collection {
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
	pairsAsEnv {|curve=\lin|
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
				if (x<=tail) {
					Error("x values not in increasing order:" + this).throw;
				};
			};
			tail=x;
			yary=yary.add(y)});
		if ((tail==1).not) {xary=xary/tail;};
		xary=xary.differentiate.drop(1);
		^Env.new(yary,xary,curve);
	}

}


+SequenceableCollection {
	//separate a Collection at the index point in an Array
	chop{|indexArray|
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
	peakTime{|groupThresh = 0.32|
		var outcome = [];
		if(this.at(0.01) < this.at(0)){outcome = outcome.add(0)};
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
}