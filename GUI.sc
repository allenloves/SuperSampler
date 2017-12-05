+ SampleDescript {

	*gui {
		arg soundfile, filename, triggerByInstance = false;
		var colorSet1 = [Color(0.639,0.537,0.463), Color(0.953,0.894,0.847),Color(0.78,0.694,0.627),Color(0.498,0.4,0.325),Color(0.38,0.267,0.184)];
		var colorSet2 = [Color(0.639,0.576,0.463), Color(0.953,0.918,0.847),Color(0.78,0.725,0.627),Color(0.498,0.435,0.325),Color(0.38,0.31,0.184)];
		var colorSet3 = [Color(0.627,0.455,0.463), Color(0.937,0.831,0.835),Color(0.765,0.616,0.624),Color(0.49,0.318,0.325),Color(0.373,0.18,0.188)];
		var win, sfw, drw, drg;
		var onsetButton, onsetView, breakButton, breakView, peakIndexButton, peakIndexView, peakTimeButton, peakTimeView, startButton, startView, endButton, endView;
		~temp = nil;


		win = Window.new("Sound Descripter", Rect(140, 800, 1100, 200))
		.front.alwaysOnTop_(true)
		.background_(colorSet1[2])
		.onClose_({soundfile.free; soundfile = nil; ~temp.free; ~temp = nil});

		//*** Drop Area ***
		drg = DragSink(win, Rect(15, 15, 700, 50))
		.resize_(2)
		.background_(colorSet1[1])
		.align_(\center)
		.string_("drop file here")
		.action_({|obj|
			//obj.string.postln;
			if(obj.string.isSoundFile || triggerByInstance = true){  //using wslib
				filename = filename ?? obj.string;


				sfw = SoundFileView(win, Rect(15, 80, win.bounds.width-30, win.bounds.height-100))
				.resize_(5).gridOn_(false)
				.soundfile_(SoundFile.openRead(filename)).read(closeFile: true).refresh
				.background_(Color(1,1,1))
				.background_(colorSet1[0])
				;


				soundfile = soundfile ?? SampleDescript(obj.string, loadToBuffer: false);
				~temp = soundfile;

				//*** Draw index lines ***
				drw = UserView(win, sfw.bounds).resize_(5)
				.background_(Color(0,0,0,0))
				.drawFunc_({|uview|


					// //*** Draw SCMIR hoptime grid line***
					// block{|break|
					// 	var linelocation = 0;
					// 	while({linelocation < uview.bounds.width},
					// 		{
					// 			Pen.strokeColor_(Color.gray)
					// 			.moveTo(linelocation @ 0)
					// 			.lineTo(linelocation @ (uview.bounds.height))
					// 			.stroke;
					// 			linelocation = linelocation + (~temp.hoptime * uview.bounds.width / soundfile.duration);
					// 		};
					// 	)
					// };

					//** Reset Buttons **//
					onsetButton.value_(0);
					breakButton.value_(0);
					peakIndexButton.value_(0);
					peakTimeButton.value_(0);
					startButton.value_(0);
					endButton.value_(0);


				};)//.drawFunc
			}//if statement
		});//.DragSink action

		if(triggerByInstance == true){drg.doAction};


		//** Draw Onset **//
		onsetButton = Button(win, Rect(730, 15, 90, 22))
		.resize_(3)
		.states_([
			["Onset", Color.black, colorSet1[1]],
			["Onset", Color.white, Color.red]
		])
		.action_({|butt|
			if(soundfile.class == SampleDescript){
				if(butt.value == 1)
				{
					onsetView = UserView(win, sfw.bounds).resize_(5).background_(Color(0,0,0,0))
					.drawFunc_({|onsetView|
						//***Draw onset***
						soundfile.onsetTime.do({|otime|
							var linelocation = onsetView.bounds.width * otime / soundfile.duration;
							Pen.strokeColor_(Color.white)
							.moveTo(linelocation @ 0)
							.lineTo(linelocation @ (onsetView.bounds.height))
							.width_(4)
							.stroke;
						});
					});
				}
				{onsetView.remove};
			}
		});//onsetButton


		//** Draw Section BreakPoint **//
		breakButton = Button(win, Rect(830, 15, 90, 22))
		.resize_(3)
		.states_([
			["Break Point", Color.black, colorSet1[1]],
			["Break Point", Color.black, Color.cyan]
		])
		.action_({|butt|
			if(soundfile.class == SampleDescript){
				if(butt.value == 1)
				{
					breakView = UserView(win, sfw.bounds).resize_(5).background_(Color(0,0,0,0))
					.drawFunc_({|breakView|
						soundfile.sectionBreakPoint.do({|btime|
							var linelocation = breakView.bounds.width * btime * ~temp.hoptime / soundfile.duration;
							Pen.strokeColor_(Color.cyan)
							.moveTo(linelocation @ 0)
							.lineTo(linelocation @ (breakView.bounds.height))
							.stroke;
						});
					});
				}
				{breakView.remove};
			}
		});//breakButton



		//*** Draw Peak Index ***//
		peakIndexButton = Button(win, Rect(730, 42, 90, 22))
		.resize_(3)
		.states_([
			["Peak Index", Color.black, colorSet1[1]],
			["Peak Index", Color.black, Color.white]
		])
		.action_({|butt|
			if(soundfile.class == SampleDescript){
				if(butt.value == 1)
				{
					peakTimeButton.valueAction_(0);
					peakIndexView = UserView(win, sfw.bounds).resize_(5).background_(Color(0,0,0,0))
					.drawFunc_({|breakView|
						soundfile.peakIndex.do({|ptime|
							var linelocation = peakIndexView.bounds.width * ptime * ~temp.hoptime / soundfile.duration;
							Pen.strokeColor_(Color.white)
							.moveTo(linelocation @ 0)
							.lineTo(linelocation @ (peakIndexView.bounds.height))
							.stroke;
						});
					});
				}
				{peakIndexView.remove};
			}
		});//peakIndexButton


		//*** Draw Peak Time ***//
		peakTimeButton = Button(win, Rect(830, 42, 90, 22))
		.resize_(3)
		.states_([
			["Peak Time", Color.black, colorSet1[1]],
			["Peak Time", Color.black, Color.white]
		])
		.action_({|butt|
			if(soundfile.class == SampleDescript){
				if(butt.value == 1)
				{
					peakIndexButton.valueAction_(0);
					peakTimeView = UserView(win, sfw.bounds).resize_(5).background_(Color(0,0,0,0))
					.drawFunc_({|breakView|
						soundfile.peakTime.do({|ptime|
							var linelocation = peakTimeView.bounds.width * ptime / soundfile.duration;
							Pen.strokeColor_(Color.white)
							.moveTo(linelocation @ 0)
							.lineTo(linelocation @ (peakTimeView.bounds.height))
							.stroke;
						});
					});
				}
				{peakTimeView.remove};
			}
		});//peakTimeButton

		//** Draw Start Point **//
		startButton = Button(win, Rect(930, 15, 90, 22))
		.resize_(3)
		.states_([
			["Start Time", Color.black, colorSet1[1]],
			["Start Time", Color.black, Color.green]
		])
		.action_({|butt|
			if(soundfile.class == SampleDescript){
				if(butt.value == 1)
				{
					startView = UserView(win, sfw.bounds).resize_(5).background_(Color(0,0,0,0))
					.drawFunc_({|breakView|
						soundfile.startTime.do({|stime|
							var linelocation = startView.bounds.width * stime / soundfile.duration;
							Pen.strokeColor_(Color.green)
							.moveTo(linelocation @ 0)
							.lineTo(linelocation @ startView.bounds.height)
							.stroke;
						});//draw section lines
					});
				}
				{startView.remove};
			}
		});//startButton



		//** Draw End Point **//
		endButton = Button(win, Rect(930, 42, 90, 22))
		.resize_(3)
		.states_([
			["End Time", Color.black, colorSet1[1]],
			["End Time", Color.white, Color.blue]
		])
		.action_({|butt|
			if(soundfile.class == SampleDescript){
				if(butt.value == 1)
				{
					endView = UserView(win, sfw.bounds).resize_(5).background_(Color(0,0,0,0))
					.drawFunc_({|breakView|
						soundfile.endTime.do({|stime|
							var linelocation = endView.bounds.width * stime / soundfile.duration;
							Pen.strokeColor_(Color.blue)
							.moveTo(linelocation @ 0)
							.lineTo(linelocation @ endView.bounds.height)
							.stroke;
						});//draw section lines
					});
				}
				{endView.remove};
			}
		});//endButton

	}//*gui

	gui {
		this.class.gui(this, this.filename, true);
	}

}




+ Sampler {

	gui {
		var win;
		var colorSet1 = [Color(0.639,0.537,0.463), Color(0.953,0.894,0.847),Color(0.78,0.694,0.627),Color(0.498,0.4,0.325),Color(0.38,0.267,0.184)];
		var colorSet2 = [Color(0.639,0.576,0.463), Color(0.953,0.918,0.847),Color(0.78,0.725,0.627),Color(0.498,0.435,0.325),Color(0.38,0.31,0.184)];
		var colorSet3 = [Color(0.627,0.455,0.463), Color(0.937,0.831,0.835),Color(0.765,0.616,0.624),Color(0.49,0.318,0.325),Color(0.373,0.18,0.188)];

		win = Window.new("Sound Descripter", Rect(140, 800, 1100, 200))
		.front.alwaysOnTop_(true)
		.background_(colorSet1[2]);




	}



}