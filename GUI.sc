+ SampleDescript {

	*gui {
		arg soundfile, filename;
		var colorSet1 = [Color(0.639,0.537,0.463), Color(0.953,0.894,0.847),Color(0.78,0.694,0.627),Color(0.498,0.4,0.325),Color(0.38,0.267,0.184)];
		var colorSet2 = [Color(0.639,0.576,0.463), Color(0.953,0.918,0.847),Color(0.78,0.725,0.627),Color(0.498,0.435,0.325),Color(0.38,0.31,0.184)];
		var colorSet3 = [Color(0.627,0.455,0.463), Color(0.937,0.831,0.835),Color(0.765,0.616,0.624),Color(0.49,0.318,0.325),Color(0.373,0.18,0.188)];
		var win, sfw, drw, drg;
		var onsetButton, onsetView, breakButton, breakView, peakIndexButton, peakIndexView, peakTimeButton, peakTimeView, startButton, startView, endButton, gridButton, endView;
		var triggeredBySink =  false;
		~temp = nil;


		win = Window.new("Sound Descripter", Rect(140, 800, 1100, 200))
		.front.alwaysOnTop_(true)
		.background_(colorSet1[2])
		.onClose_({
			onsetButton.valueAction = 0;
			breakButton.valueAction = 0;
			peakIndexButton.valueAction = 0;
			startButton.valueAction = 0;
			endButton.valueAction = 0;
			gridButton.valueAction = 0;

			if(triggeredBySink)
			{soundfile.free; soundfile = nil; ~temp.free; ~temp = nil};
		})
		;

		if(filename.isNil.not){

			~temp = soundfile;
			triggeredBySink = false;
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

			if(obj.string.isSoundFile){  //using wslib
				filename = filename ?? obj.string;

				triggeredBySink = true;
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



		win = Window.new(("SSampler__" ++ this.name).asString, Rect(140, 800, 1180, 900), scroll: true)
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
		StaticText(column.view, 80@50).string_("Voice").align_(\center).font_(Font(size: 20, bold: true)).stringColor_(colorSet1[1]);


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
				};

				//Per-sample voice-mode editor. Highlights cyan if any
				//overrides are already set for this (sample, section).
				Button(thisLine.view, 80@50)
				.font_(Font(size: 13, bold: true))
				.states_([
					[if(this.getSampleVoiceArgs(thisSample, idx).isNil) {"VEdit"} {"VEdit *"},
						colorSet1[4],
						if(this.getSampleVoiceArgs(thisSample, idx).isNil) {colorSet1[1]} {Color(0.5, 0.85, 0.95)}]
				])
				.action_({ this.voiceEditor(thisSample, idx) });

			}//End of thisSample.keynum.do
		};//End of samples.do

	}


	//================================================================
	// Per-(SampleDescript, section) voice-mode editor.
	//
	// Displays the section's waveform with draggable loop and release
	// region selections on top, plus ADSR + loop/release-mode controls.
	// Changes are saved to #sampleVoiceArgs as the user edits, so the
	// next #noteOn / #keyVoice for this section picks them up. Active
	// voice synths are not retroactively retuned (changes take effect
	// on next trigger; click Audition to hear).
	//================================================================
	voiceEditor {arg targetSample = nil, targetSection = 0;
		var win, colorSet;
		var sfv, sf, currentSample, currentSection;
		var startSample, durSample, sectionDurSec, sectionSR;
		var bufFrames;
		var pairs, labels;
		var sampleSelector, editTargetMenu;
		var loopOnMenu, loopDirMenu, loopModeMenu;
		var loopStartBox, loopEndBox, loopXfadeBox;
		var relModeMenu, relStartBox, relEndBox, relXfadeBox;
		var attackBox, decayBox, sustainBox, releaseBox;
		var statusText;
		var auditionBtn, releaseBtn, allOffBtn, resetBtn;
		var loadSection, writeOverride, frameToSec, secToFrame;
		var dirSymbols, modeSymbols, relModeSymbols;

		colorSet = [Color(0.639,0.537,0.463), Color(0.953,0.894,0.847),
			Color(0.78,0.694,0.627), Color(0.498,0.4,0.325), Color(0.38,0.267,0.184)];

		if(samples.isNil or: { samples.isEmpty }) {
			Error("SSampler#voiceEditor: no samples loaded.").throw;
		};

		//Build flat list of (sample, section) pairs.
		pairs = [];
		labels = [];
		samples.do{|s|
			var f = PathName(s.filename);
			s.keynum.do{|kn, idx|
				pairs = pairs.add([s, idx]);
				labels = labels.add(
					f.fileName ++ "  __" ++ (idx+1).asString ++
					"  (key " ++ kn.asInteger.asString ++ ")"
				);
			};
		};

		targetSample = targetSample ? pairs[0][0];
		currentSample = targetSample;
		currentSection = targetSection;

		dirSymbols     = [\fwd, \rev, \palin];
		modeSymbols    = [\trapezoid, \xfade];
		relModeSymbols = [\off, \oneShot, \loop, \palin];

		win = Window.new(
			"SSampler Voice Editor -- " ++ this.name.asString,
			Rect(140, 200, 1100, 620)
		).front.alwaysOnTop_(true).background_(colorSet[4])
		.onClose_({ this.allNotesOff });

		// ---- Header: sample selector + edit-target menu ----
		StaticText(win, Rect(15, 12, 70, 24))
		.string_("Sample:").stringColor_(colorSet[1])
		.font_(Font(size: 13, bold: true));

		sampleSelector = PopUpMenu(win, Rect(90, 12, 640, 24))
		.items_(labels)
		.background_(colorSet[1])
		.stringColor_(colorSet[4]);

		StaticText(win, Rect(745, 12, 60, 24))
		.string_("Edit:").stringColor_(colorSet[1])
		.font_(Font(size: 13, bold: true));

		editTargetMenu = PopUpMenu(win, Rect(800, 12, 140, 24))
		.items_(["Loop region", "Release region"])
		.background_(colorSet[1])
		.stringColor_(colorSet[4]);

		statusText = StaticText(win, Rect(950, 12, 135, 24))
		.stringColor_(Color(0.85, 0.95, 0.85))
		.font_(Font(size: 11));

		// ---- Waveform view ----
		sfv = SoundFileView(win, Rect(15, 50, 1070, 200))
		.background_(colorSet[0])
		.gridOn_(false);
		sfv.setSelectionColor(0, Color(0.2, 0.85, 0.95, 0.45));   // loop = cyan
		sfv.setSelectionColor(1, Color(1.0, 0.55, 0.15, 0.45));   // release = orange

		// Frame <-> seconds helpers (resolved per section).
		frameToSec = {|fr| fr / sectionSR };
		secToFrame = {|sec| (sec * sectionSR).asInteger };

		// ---- Loop controls ----
		StaticText(win, Rect(15, 260, 220, 24))
		.string_("Loop region")
		.stringColor_(Color(0.2, 0.85, 0.95))
		.font_(Font(size: 14, bold: true));

		StaticText(win, Rect(15, 290, 60, 22)).string_("Loop:")
		.stringColor_(colorSet[1]);
		loopOnMenu = PopUpMenu(win, Rect(75, 290, 90, 22))
		.items_(["off", "on"]).background_(colorSet[1]).stringColor_(colorSet[4]);

		StaticText(win, Rect(175, 290, 50, 22)).string_("Dir:")
		.stringColor_(colorSet[1]);
		loopDirMenu = PopUpMenu(win, Rect(225, 290, 95, 22))
		.items_(["fwd", "rev", "palin"]).background_(colorSet[1]).stringColor_(colorSet[4]);

		StaticText(win, Rect(330, 290, 60, 22)).string_("Mode:")
		.stringColor_(colorSet[1]);
		loopModeMenu = PopUpMenu(win, Rect(390, 290, 110, 22))
		.items_(["trapezoid", "xfade"]).background_(colorSet[1]).stringColor_(colorSet[4]);

		StaticText(win, Rect(15, 320, 80, 22)).string_("Start (s):")
		.stringColor_(colorSet[1]);
		loopStartBox = NumberBox(win, Rect(100, 320, 100, 22))
		.background_(colorSet[1]).stringColor_(colorSet[4]).maxDecimals_(4)
		.minDecimals_(3).step_(0.001);

		StaticText(win, Rect(210, 320, 70, 22)).string_("End (s):")
		.stringColor_(colorSet[1]);
		loopEndBox = NumberBox(win, Rect(285, 320, 100, 22))
		.background_(colorSet[1]).stringColor_(colorSet[4]).maxDecimals_(4)
		.minDecimals_(3).step_(0.001);

		StaticText(win, Rect(395, 320, 80, 22)).string_("Xfade (s):")
		.stringColor_(colorSet[1]);
		loopXfadeBox = NumberBox(win, Rect(480, 320, 80, 22))
		.background_(colorSet[1]).stringColor_(colorSet[4]).maxDecimals_(4)
		.minDecimals_(3).step_(0.001);

		// ---- Release region controls ----
		StaticText(win, Rect(570, 260, 220, 24))
		.string_("Release region")
		.stringColor_(Color(1.0, 0.55, 0.15))
		.font_(Font(size: 14, bold: true));

		StaticText(win, Rect(570, 290, 60, 22)).string_("Mode:")
		.stringColor_(colorSet[1]);
		relModeMenu = PopUpMenu(win, Rect(630, 290, 100, 22))
		.items_(["off", "oneShot", "loop", "palin"])
		.background_(colorSet[1]).stringColor_(colorSet[4]);

		StaticText(win, Rect(570, 320, 80, 22)).string_("Start (s):")
		.stringColor_(colorSet[1]);
		relStartBox = NumberBox(win, Rect(655, 320, 100, 22))
		.background_(colorSet[1]).stringColor_(colorSet[4]).maxDecimals_(4)
		.minDecimals_(3).step_(0.001);

		StaticText(win, Rect(765, 320, 70, 22)).string_("End (s):")
		.stringColor_(colorSet[1]);
		relEndBox = NumberBox(win, Rect(840, 320, 100, 22))
		.background_(colorSet[1]).stringColor_(colorSet[4]).maxDecimals_(4)
		.minDecimals_(3).step_(0.001);

		StaticText(win, Rect(950, 320, 80, 22)).string_("Xfade (s):")
		.stringColor_(colorSet[1]);
		relXfadeBox = NumberBox(win, Rect(1030, 320, 60, 22))
		.background_(colorSet[1]).stringColor_(colorSet[4]).maxDecimals_(4)
		.minDecimals_(3).step_(0.001);

		// ---- ADSR amp envelope ----
		StaticText(win, Rect(15, 380, 280, 24))
		.string_("Amp envelope (ADSR)")
		.stringColor_(Color(0.9, 0.85, 0.6))
		.font_(Font(size: 14, bold: true));

		StaticText(win, Rect(15, 410, 80, 22)).string_("Attack (s):")
		.stringColor_(colorSet[1]);
		attackBox = NumberBox(win, Rect(100, 410, 80, 22))
		.background_(colorSet[1]).stringColor_(colorSet[4]).maxDecimals_(4)
		.minDecimals_(3).step_(0.001);

		StaticText(win, Rect(190, 410, 75, 22)).string_("Decay (s):")
		.stringColor_(colorSet[1]);
		decayBox = NumberBox(win, Rect(265, 410, 80, 22))
		.background_(colorSet[1]).stringColor_(colorSet[4]).maxDecimals_(4)
		.minDecimals_(3).step_(0.001);

		StaticText(win, Rect(355, 410, 65, 22)).string_("Sustain:")
		.stringColor_(colorSet[1]);
		sustainBox = NumberBox(win, Rect(420, 410, 80, 22))
		.background_(colorSet[1]).stringColor_(colorSet[4]).maxDecimals_(3)
		.minDecimals_(2).step_(0.01).clipLo_(0).clipHi_(1);

		StaticText(win, Rect(510, 410, 80, 22)).string_("Release (s):")
		.stringColor_(colorSet[1]);
		releaseBox = NumberBox(win, Rect(590, 410, 80, 22))
		.background_(colorSet[1]).stringColor_(colorSet[4]).maxDecimals_(4)
		.minDecimals_(3).step_(0.001);

		// ---- Action buttons ----
		// Audition uses noteOn at the section's anchored pitch. Held voices
		// live in the standard activeVoices registry; the Note Off button
		// targets that same pitch, All Notes Off releases everything.
		auditionBtn = Button(win, Rect(15, 460, 140, 36))
		.states_([["Audition", Color.white, Color(0.2, 0.55, 0.3)]])
		.font_(Font(size: 14, bold: true))
		.action_({
			var pitch = currentSample.keynum[currentSection].asInteger.clip(0, 127);
			this.noteOn(pitch);
		});

		releaseBtn = Button(win, Rect(165, 460, 140, 36))
		.states_([["Note Off", Color.white, Color(0.55, 0.3, 0.2)]])
		.font_(Font(size: 14, bold: true))
		.action_({
			var pitch = currentSample.keynum[currentSection].asInteger.clip(0, 127);
			this.noteOff(pitch);
		});

		allOffBtn = Button(win, Rect(315, 460, 140, 36))
		.states_([["All Notes Off", Color.white, Color(0.55, 0.2, 0.2)]])
		.font_(Font(size: 14, bold: true))
		.action_({ this.allNotesOff });

		resetBtn = Button(win, Rect(945, 460, 140, 36))
		.states_([["Reset Overrides", Color.white, Color(0.35, 0.35, 0.45)]])
		.font_(Font(size: 14, bold: true))
		.action_({
			this.clearSampleVoiceArgs(currentSample, currentSection);
			loadSection.(currentSample, currentSection);
			statusText.string_("Overrides cleared.");
		});

		StaticText(win, Rect(15, 510, 1070, 50))
		.string_("Drag the cyan band to set the loop region; drag the orange band to set the release region.\n"
			"Switch which band you're editing with the \"Edit\" dropdown above.  Changes save automatically; click Audition to hear them.")
		.stringColor_(colorSet[2])
		.font_(Font(size: 11));

		// ----------------------------------------------------------
		// Override writer. Called from every UI control. Single
		// source of truth -- always reads the UI then stamps the
		// override Event for the currently-selected (sample, section).
		// ----------------------------------------------------------
		writeOverride = {
			var overrides = Event.new;
			overrides[\loop]         = loopOnMenu.value.asInteger;
			overrides[\loopDir]      = dirSymbols[loopDirMenu.value];
			overrides[\loopMode]     = modeSymbols[loopModeMenu.value];
			overrides[\loopStart]    = secToFrame.(loopStartBox.value.max(0));
			overrides[\loopEnd]      = secToFrame.(loopEndBox.value.max(0));
			overrides[\loopXfade]    = loopXfadeBox.value.max(0);
			overrides[\releaseMode]  = relModeSymbols[relModeMenu.value];
			overrides[\releaseStart] = secToFrame.(relStartBox.value.max(0));
			overrides[\releaseEnd]   = secToFrame.(relEndBox.value.max(0));
			overrides[\releaseXfade] = relXfadeBox.value.max(0);
			overrides[\attack]       = attackBox.value.max(0);
			overrides[\decay]        = decayBox.value.max(0);
			overrides[\sustainLevel] = sustainBox.value.clip(0, 1);
			overrides[\release]      = releaseBox.value.max(0);
			//Replace wholesale so deselected fields don't linger.
			this.clearSampleVoiceArgs(currentSample, currentSection);
			this.setSampleVoiceArgs(currentSample, currentSection, overrides);
			statusText.string_("Saved.");
		};

		// ----------------------------------------------------------
		// Selection -> UI sync. Read the cyan/orange band positions
		// from the SoundFileView, push into NumberBoxes, then call
		// writeOverride.
		// ----------------------------------------------------------
		sfv.mouseUpAction_({|view|
			var lStart, lSize, rStart, rSize;
			lStart = view.selectionStart(0); lSize = view.selectionSize(0);
			rStart = view.selectionStart(1); rSize = view.selectionSize(1);
			//Subtract startSample to convert from file frames to section
			//(sub-buffer) frames -- the SynthDef expects sub-buffer frames.
			loopStartBox.value_(frameToSec.((lStart - startSample).max(0)));
			loopEndBox.value_(frameToSec.((lStart + lSize - startSample).max(0)));
			relStartBox.value_(frameToSec.((rStart - startSample).max(0)));
			relEndBox.value_(frameToSec.((rStart + rSize - startSample).max(0)));
			writeOverride.value;
		});

		editTargetMenu.action_({|menu|
			sfv.currentSelection_(menu.value);
		});

		// NumberBox -> selection sync. When the user edits a numeric value
		// directly, push it back into the SoundFileView and re-save.
		[loopStartBox, loopEndBox].do{|box|
			box.action_({
				var lo, hi;
				lo = secToFrame.(loopStartBox.value.max(0));
				hi = secToFrame.(loopEndBox.value.max(lo / sectionSR));
				sfv.setSelection(0, [lo + startSample, (hi - lo).max(0)]);
				writeOverride.value;
			});
		};
		[relStartBox, relEndBox].do{|box|
			box.action_({
				var lo, hi;
				lo = secToFrame.(relStartBox.value.max(0));
				hi = secToFrame.(relEndBox.value.max(lo / sectionSR));
				sfv.setSelection(1, [lo + startSample, (hi - lo).max(0)]);
				writeOverride.value;
			});
		};

		[loopOnMenu, loopDirMenu, loopModeMenu, relModeMenu,
			loopXfadeBox, relXfadeBox,
			attackBox, decayBox, sustainBox, releaseBox].do{|v|
			v.action_({ writeOverride.value });
		};

		sampleSelector.action_({|menu|
			var pair = pairs[menu.value];
			loadSection.(pair[0], pair[1]);
		});

		// ----------------------------------------------------------
		// Load (or re-load) one (sample, section) into the editor:
		// re-reads the soundfile range for the SoundFileView, then
		// resolves defaults or existing overrides into the UI.
		// ----------------------------------------------------------
		loadSection = {|smpl, scn|
			var overrides, loopRegion, relRegion;
			var defaultLoopRegion, defaultReleaseRegion;
			currentSample = smpl;
			currentSection = scn;
			sectionSR = smpl.sampleRate;
			startSample = ((smpl.startTime[scn] * sectionSR) - 6).max(0).asInteger;
			//Section duration in frames matches what loadToBuffer stored.
			durSample = ((smpl.endTime[scn] - smpl.startTime[scn]) * sectionSR).asInteger;
			bufFrames = if(smpl.activeBuffer[scn].notNil) {
				smpl.activeBuffer[scn][0].numFrames ? durSample
			} { durSample };
			sectionDurSec = bufFrames / sectionSR;

			try {
				sf = SoundFile.openRead(smpl.filename);
				sfv.readFile(sf, startSample, durSample);
				if(sf.notNil) { sf.close };
			} {|err|
				statusText.string_("waveform load failed");
			};

			overrides = this.getSampleVoiceArgs(smpl, scn) ?? { Event.new };

			//Defaults: full section for loop, last 30% for release.
			defaultLoopRegion    = [0, bufFrames];
			defaultReleaseRegion = [(bufFrames * 0.7).asInteger, bufFrames];

			loopRegion = [
				overrides[\loopStart] ? defaultLoopRegion[0],
				overrides[\loopEnd]   ? defaultLoopRegion[1]
			];
			relRegion = [
				overrides[\releaseStart] ? defaultReleaseRegion[0],
				overrides[\releaseEnd]   ? defaultReleaseRegion[1]
			];

			//Push into UI. Defaults below match noteOn's arg defaults --
			//since writeOverride saves every UI field, displayed defaults
			//must match noteOn so untouched fields are effective no-ops.
			loopOnMenu.value_((overrides[\loop] ? 1).asInteger.clip(0, 1));
			loopDirMenu.value_(
				(dirSymbols.indexOf(overrides[\loopDir] ? \fwd) ? 0).asInteger);
			loopModeMenu.value_(
				(modeSymbols.indexOf(overrides[\loopMode] ? \trapezoid) ? 0).asInteger);
			loopStartBox.value_(frameToSec.(loopRegion[0]));
			loopEndBox.value_(frameToSec.(loopRegion[1]));
			loopXfadeBox.value_(overrides[\loopXfade] ? 0.02);

			relModeMenu.value_(
				(relModeSymbols.indexOf(overrides[\releaseMode] ? \off) ? 0).asInteger);
			relStartBox.value_(frameToSec.(relRegion[0]));
			relEndBox.value_(frameToSec.(relRegion[1]));
			relXfadeBox.value_(overrides[\releaseXfade] ? 0.02);

			attackBox.value_(overrides[\attack] ? 0.005);
			decayBox.value_(overrides[\decay] ? 0.0);
			sustainBox.value_(overrides[\sustainLevel] ? 1.0);
			releaseBox.value_(overrides[\release] ? 0.05);

			//Selections in file frames (selection coord space).
			sfv.setSelection(0, [loopRegion[0] + startSample, loopRegion[1] - loopRegion[0]]);
			sfv.setSelection(1, [relRegion[0]  + startSample, relRegion[1]  - relRegion[0] ]);
			sfv.currentSelection_(editTargetMenu.value);

			statusText.string_("Loaded.");
		};

		//Initial selection: open with the requested (sample, section) or
		//default to the first row. If the caller's target isn't found in
		//the flat pair list, fall back to pairs[0] -- otherwise the editor
		//would try to load a sample that isn't in this sampler.
		block {|break|
			pairs.do{|p, i|
				if(p[0] === targetSample and: { p[1] == targetSection }) {
					sampleSelector.value_(i);
					break.value;
				}
			};
			sampleSelector.value_(0);
			currentSample = pairs[0][0];
			currentSection = pairs[0][1];
		};

		loadSection.(currentSample, currentSection);
		^win;
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