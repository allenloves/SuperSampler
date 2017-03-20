//Env dependent on wslib quark

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
