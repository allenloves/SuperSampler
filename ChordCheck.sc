// Check if the player is playing a chord instead of single notes

ChordCheck {
	classvar <> waittime = 0.05;
	classvar <> classlock = false;
	classvar <> classchord;
	var <> lock = false;
	var <> chord;

	*new {
		^super.new.init();
	}

	*check {arg num, func;
		if(classlock)
		{
			classchord = classchord.add(num);
		}
		{
			Routine({
				classlock = true;
				classchord = [num];
				waittime.yield;
				func.value(classchord.sort);
				classlock = false;
			}).play;
		};
	}

	init {}

	check {arg num, func;
		if(lock)
		{
			chord = chord.add(num);
		}
		{
			Routine({
				lock = true;
				chord = [num];
				waittime.yield;
				func.value(chord.sort);
				lock = false;
			}).play
		};
	}
}