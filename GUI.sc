+ SampleDescript {

	*gui {
		arg soundfile, filename, triggerByInstance = false;
		var colorSet1 = [Color(0.639,0.537,0.463), Color(0.953,0.894,0.847),Color(0.78,0.694,0.627),Color(0.498,0.4,0.325),Color(0.38,0.267,0.184)];
		var colorSet2 = [Color(0.639,0.576,0.463), Color(0.953,0.918,0.847),Color(0.78,0.725,0.627),Color(0.498,0.435,0.325),Color(0.38,0.31,0.184)];
		var colorSet3 = [Color(0.627,0.455,0.463), Color(0.937,0.831,0.835),Color(0.765,0.616,0.624),Color(0.49,0.318,0.325),Color(0.373,0.18,0.188)];
		var win, sfw, drw, drg;
		var onsetButton, onsetView, breakButton, breakView, peakIndexButton, peakIndexView, peakTimeButton, peakTimeView, startButton, startView, endButton, gridButton, endView;
		~temp = nil;


		win = Window.new("Sound Descripter", Rect(140, 800, 1100, 200))
		.front.alwaysOnTop_(true)
		.background_(colorSet1[2])
		.onClose_({soundfile.free; soundfile = nil; ~temp.free; ~temp = nil});

		if(filename.isSoundFile){

			~temp = soundfile;

			sfw = SoundFileView(win, Rect(15, 80, win.bounds.width-30, win.bounds.height-100))
			.resize_(5)
			.gridResolution_(soundfile.hoptime)
			.gridOn_(false)
			.soundfile_(SoundFile.openRead(filename)).read(closeFile: true).refresh
			.background_(Color(1,1,1))
			.background_(colorSet1[0])
			;

			//*** Draw index lines ***
			drw = UserView(win, sfw.bounds).resize_(5)
			.background_(Color(0,0,0,0))
			.drawFunc_({|uview|
				//** Reset Buttons **//
				onsetButton.value_(0);
				breakButton.value_(0);
				peakIndexButton.value_(0);
				peakTimeButton.value_(0);
				startButton.value_(0);
				endButton.value_(0);
			})
		};

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
							var linelocation = breakView.bounds.width * btime * soundfile.hoptime / soundfile.duration;
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
							var linelocation = peakIndexView.bounds.width * ptime * soundfile.hoptime / soundfile.duration;
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


		gridButton = Button(win, Rect(1030, 15, 45, 22))
		.resize_(3)
		.states_([
			["Grid", Color.black, colorSet1[1]],
			["Grid", Color.white, Color.blue]
		])
		.action_({|butt|
			if(butt.value == 1){
				sfw.gridOn_(true);
			}
			{
				sfw.gridOn_(false);
			}
		});//gridButton

	}//*gui

	gui {
		this.class.gui(this, this.filename, false);
	}

}




//********************
// GUI for Sampler
// Provides loading and assinging pitches to samples
//********************
+ SSampler {

	gui {
		var win, column;
		var sampleItem = [];
		var colorSet1 = [Color(0.639,0.537,0.463), Color(0.953,0.894,0.847),Color(0.78,0.694,0.627),Color(0.498,0.4,0.325),Color(0.38,0.267,0.184)];
		var colorSet2 = [Color(0.639,0.576,0.463), Color(0.953,0.918,0.847),Color(0.78,0.725,0.627),Color(0.498,0.435,0.325),Color(0.38,0.31,0.184)];
		var colorSet3 = [Color(0.627,0.455,0.463), Color(0.937,0.831,0.835),Color(0.765,0.616,0.624),Color(0.49,0.318,0.325),Color(0.373,0.18,0.188)];
		var dragHandler = {this.load(View.currentDrag.value.pathMatch); win.refresh; this.gui; win.close;};



		win = Window.new(("SSampler__" ++ this.name).asString, Rect(140, 800, 1100, 900), scroll: true)
		.front.alwaysOnTop_(true)
		.background_(colorSet1[4])
		;


		DragSink(win, win.view.bounds)
		.background_(Color(alpha: 0))
		.resize_(5)
		.receiveDragHandler_(dragHandler)
		;

		win.view.decorator = FlowLayout( win.view.bounds, 10@10, 5@5 );


		// For Column Texts
		column = FlowView.new(win.view, (win.view.bounds.width - 20)@50);
		StaticText(column.view, 750@50).string_("Sound Sample ___ Section").align_(\center).font_(Font(size: 20, bold: true)).stringColor_(colorSet1[1]);
		StaticText(column.view, 100@50).string_("Sample Pitch").align_(\center).font_(Font(size: 20, bold: true)).stringColor_(colorSet1[1]);
		StaticText(column.view, 100@50).string_("Respond Low").align_(\center).font_(Font(size: 20, bold: true)).stringColor_(colorSet1[1]);
		StaticText(column.view, 100@50).string_("Respond High").align_(\center).font_(Font(size: 20, bold: true)).stringColor_(colorSet1[1]);


		//For each samples, there is a button indicating filename,
		//followed by anchored picth and responsing key ranges.
		samples.do{|thisSample, index| // index is the index of samples
			thisSample.keynum.do{|thisKeynum, idx| //idx is the sections in a sample
				var thisLine;
				var rangeBox = [];
				var file = PathName(thisSample.filename);
				var fileText = file.folderName ++ "/" ++ file.fileName ++ "___" ++ (idx+1).asString;

				thisLine = FlowView.new(win.view, (win.view.bounds.width - 20)@50).background_(Color.rand);

				//for filename
				Button(thisLine.view, 750@50)
				.states_([[fileText, colorSet1[4], colorSet1[1]]])
				.action_({thisSample.play(idx)})
				.receiveDragHandler_(dragHandler)
				.font_(Font(size: 14, bold: true))
				;

				//for anchored keynumber
				NumberBox(thisLine.view, 100@50)
				.value_(thisKeynum)
				.align_(\center)
				.background_(colorSet1[1])
				.font_(Font(size: 14, bold: true))
				.stringColor_(colorSet1[4])
				.receiveDragHandler_(dragHandler)
				.maxDecimals_(2)
				.action_({|input|
					var key = input.value;
					if(key.isNumber){
						thisSample.keynum.put(idx, key);
					}
				}
				;
				);

				//for key ganges
				2.do{|ix|
					NumberBox(thisLine.view, 100@50)
					.value_(keyRanges.at(thisSample)[idx][ix])
					.align_(\center)
					.background_(colorSet1[1])
					.font_(Font(size: 14, bold: true))
					.stringColor_(colorSet1[4])
					.receiveDragHandler_(dragHandler)
					.maxDecimals_(2)
					.action_({|input|
						var key = input.value;
						if(key.isNumber)
						{
							keyRanges.at(thisSample)[idx].put(ix, key);
						}
					});
				}

			}//End of thisSample.keynum.do
		};//End of samples.do

	}
}




/*

+ SamplerDB {
	gui {
		var win, column;
		var sampleItem = [];
		var colorSet1 = [Color(0.639,0.537,0.463), Color(0.953,0.894,0.847),Color(0.78,0.694,0.627),Color(0.498,0.4,0.325),Color(0.38,0.267,0.184)];
		var colorSet2 = [Color(0.639,0.576,0.463), Color(0.953,0.918,0.847),Color(0.78,0.725,0.627),Color(0.498,0.435,0.325),Color(0.38,0.31,0.184)];
		var colorSet3 = [Color(0.627,0.455,0.463), Color(0.937,0.831,0.835),Color(0.765,0.616,0.624),Color(0.49,0.318,0.325),Color(0.373,0.18,0.188)];
		var dragHandler = {this.load(View.currentDrag.value.pathMatch); win.refresh; this.gui; win.close;};


		win = Window.new(("Sampler__" ++ name).asString, Rect(140, 800, 900, 1200), scroll: true)
		.front.alwaysOnTop_(true)
		.background_(colorSet1[4])
		;
	}
}

*/