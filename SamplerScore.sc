SamplerScore : List {

	play {
		Routine.run({
			this.do({|argu, index|
				SSampler.playArgs(argu[0].postln);
				argu[1].wait;
			})
		})
	}
}




SamplerDBScore : List {

	play {
		Routine.run({
			this.do({|sdbscore, index|
				sdbscore[0].do({|sscore|
					sscore.play;
				});
				sdbscore[1].wait;
			})
		})
	}
}
