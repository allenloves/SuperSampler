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

	//Multiply the amplitude of two FLAT Envs
	* {|anotherEnv|
		var timeline = (this.timeLine.asArray ++ anotherEnv.timeLine.asArray).removeDups.sort;
		var amp = [];
		timeline.do{|time, index|
			amp = amp.add(this.at(time) * anotherEnv.at(time));
		};
		^[timeline, amp].flop.flat.pairsAsEnv;
	}

	* {|num|
		var level = this.levels;
		var time = this.times;
		^Env.new(level * num, time, this.curves, this.releaseNode, this.loopNode, this.offset);

	}

	//concatenate two envelopes
	++ {|anotherEnv|
		if(anotherEnv.isNil)
		{^this}
		{^Env.new(this.levels ++ anotherEnv.levels, this.times++0++anotherEnv.times, this.curves, this.releaseNode, this.loopNode, this.offset).removeDups};
	}

	removeDups {
		var level = this.levels;
		var time = this.times;
		time.do{|thisTime, index|
			if((thisTime == 0) && (level[index] == level[index + 1])){level.removeAt(index); time.removeAt(index)};
		}
		^Env.new(level, time, this.curves, this.releaseNode, this.loopNode, this.offset);
	}

	//Get peaking times in an Envelope
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

	//get amplitude in each peak times
	peakAmp {|groupThresh = 0.32|
		var outcome = [];
		this.peakTime(groupThresh).do{|thisPeakTime, index|
			outcome = outcome.add(this.at(thisPeakTime));
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



	//Envelope normalization
	normalize {|max = 1|
		^this.range(0, max);
	}

	//setting duration of an envelope without changing the shape
	stretch {|duration = 1|
		var timeline = this.timeLine;
		var levels = this.levels;
		var normalizedTimeLine = [];

		timeline.do{|thisTime, index|
			normalizedTimeLine = normalizedTimeLine ++ linlin(thisTime, 0, this.duration, 0, duration);
		};

		^[normalizedTimeLine, levels].flop.flat.pairsAsEnv;
	}


	//segment an envelope to several envelopes
	//the outcome is an array of two items:
	//[[Array of Envelopes], [wait time for next envelope for a Routine]]
	segment {arg numSegs = 1, crossfade = 1, strategy = \atpeak;
		var segmentTime = [], envs =[], waittime;
		var strat = strategy.asArray[0];

		crossfade = min(crossfade.abs, this.duration / (numSegs + 3));
		numSegs = (numSegs - 1).asInteger.thresh(0);


		case
		//strategy: geometric series, 2 would be default base
		////put e.g. [\geo, 2]
		{strat == \geo}
		{
			var base = strategy.asArray[1] ? 2;
			numSegs.do{|index|
				var a = this.duration * ((1-base) / (1- (base ** numSegs)));
				segmentTime = segmentTime.add(a * (base ** index));
			}
		}

		//strategy: exponential, 2 would be default exponential
		//put e.g. [\expo, 2]
		{strat == \expo}
		{
			var expl, explseg;
			var powr = strategy.asArray[1] ? 2;

			expl = {|powr, base|
				case
				{powr < 0}{powr = 0}
				{powr > 1}{powr = 1};

				if(base == 1)
				{powr}
				{ (base**powr - 1) / (base - 1)};
			};

			numSegs.do{|index|
				var x1 = (index + 1) / numSegs;
				var x2 = index / numSegs;
				var f1 = expl.value(x1, powr);
				var f2 = expl.value(x2, powr);
				segmentTime = segmentTime.add( this.duration * (f1 - f2) );
			}
		}

		//strategy: at peak
		{strat == \atpeak}
		{
			var peakAmp = this.peakAmp;
			var peakTime = this.peakTime;
			var extraSegs, ampOrder;

			extraSegs = (numSegs - peakAmp.size).thresh(0);

			//exclude if peak is close to the beginning or ending of the envelope
			peakTime.do{|thisPeakTime, index|
				if((peakTime[index] <= crossfade) || (peakTime[index] >= (this.duration - crossfade)))
				{peakTime.removeAt(index); peakAmp.removeAt(index)};
			};

			extraSegs = (numSegs - peakAmp.size).thresh(0);
			//order peakAmp so the biggest peak gets segmented first
			ampOrder = peakAmp.order({|a,b| a>b});

			(numSegs - extraSegs).do{|index|
				segmentTime = segmentTime.add(peakTime[ampOrder[index]]);
			};

			extraSegs.do{|index|
				segmentTime = segmentTime.add((this.duration - (crossfade * 2)).rand + crossfade);
			}
		}

		//strategy: random
		{strat == \random}
		{
			numSegs.do{
				segmentTime = segmentTime.add((this.duration - (crossfade * 2)).rand + crossfade);
			};
		};


		//gather envelopes from segment time
		segmentTime = segmentTime.sort;

		case
		{segmentTime.isEmpty}{^[[this, 0]]}

		{segmentTime.size == 1}{
			segmentTime = segmentTime[0];
			envs = envs.add((this.subEnv(0, segmentTime) ++ if(crossfade > 0){Env.new([this.at(segmentTime), 0], [crossfade])}).removeDups);
			envs = envs.add((if(crossfade>0){Env.new([0, this.at(segmentTime)],[crossfade])} ++ this.subEnv(segmentTime, this.duration - segmentTime)).removeDups);
			waittime = [segmentTime, 0];
			^[envs, waittime].flop;
		}

		{true}{
			envs = envs.add((this.subEnv(0, segmentTime.first) ++ if(crossfade > 0){Env.new([this.at(segmentTime.first), 0], [crossfade])}).removeDups);

			segmentTime.doAdjacentPairs{|thisTime, nextTime, index|
				var attack, release;
				if(crossfade > 0)
				{
					attack = Env.new([0, this.at(thisTime)], [min(crossfade, thisTime)]);
					release = Env.new([this.at(nextTime), 0], [min(crossfade, (this.duration - nextTime))]);
					//release.plot;
				};
				envs = envs.add((attack ++ this.subEnv(thisTime, nextTime-thisTime) ++ release).removeDups);
				waittime = waittime.add(if(index == 0){(thisTime - attack.duration - crossfade).thresh(0)}{(thisTime - segmentTime[index - 1] - crossfade).thresh(0)});
			};

			envs = envs.add((if(crossfade>0){Env.new([0, this.at(segmentTime.last)],[crossfade])} ++ this.subEnv(segmentTime.last, this.duration - segmentTime.last)).removeDups);
			waittime = waittime.add((segmentTime.last - segmentTime[segmentTime.size - 2]-crossfade).thresh(0)).add(0);
			^[envs, waittime].flop;
		};


	}

}
