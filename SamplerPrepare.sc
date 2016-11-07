SamplerPrepare {
	var <> sample;
	var <> section;
	var <> wait;
	var <> rate;
	var <> position;
	var <> expand;

	*new {
		^super.new.init();
	}

	init {
		section = 0;
		wait = 0;
		rate = 1;
		position = 0;
		expand = nil;
	}
}