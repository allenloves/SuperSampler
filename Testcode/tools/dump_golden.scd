//Golden analysis dump for the C++ port (behavioral reference, ADR-0003).
//Analyzes every sound file under Testcode/Sounds with SampleDescript defaults
//(the same parameters SSampler.load uses) and writes the per-section analysis
//DECISIONS to Testcode/tools/golden_analysis.json.
//No scsynth boot needed: loadToBuffer:false keeps analysis NRT-only.
//Run: sh runtests.sh golden
(
var toolsDir = PathName(thisProcess.nowExecutingPath).pathOnly;
var soundsDir = toolsDir ++ "../Sounds/";
var outPath = toolsDir ++ "golden_analysis.json";
var audioExts = ["wav", "aiff", "aif"];

var esc = {|s| s.asString.replace("\\", "\\\\").replace("\"", "\\\"")};
var num = {|x|
	case
	{x.isNil} {"null"}
	{x.isNumber.not} {"null"}
	{x.asFloat.isNaN} {"null"}
	{x.asString}
};
var numArr = {|a| "[" ++ ((a ? []).collect(num).join(", ")) ++ "]"};

var files, records = [], failures = [];

files = PathName(soundsDir).deepFiles
	.select{|p| audioExts.includesEqual(p.extension.toLower)}
	.collect{|p| p.fullPath}
	.sort;

("dumping % files".format(files.size)).postln;

fork {
	files.do{|path|
		var rel = path.copyToEnd(soundsDir.size);
		var d, sections, err;
		("analyzing: " ++ rel).postln;
		err = try {
			d = SampleDescript(path);  //defaults == SSampler.load analysis params
			nil
		} {|e| e};
		if(err.notNil) {
			failures = failures.add(rel);
			("ANALYSIS_FAILED: " ++ rel ++ " (" ++ err.errorString ++ ")").postln;
		} {
			sections = (d.peakTime ? []).collect{|pt, i|
				"      {"
				++ "\"onsetTime\": " ++ num.(d.onsetTime !? {d.onsetTime[i]})
				++ ", \"startTime\": " ++ num.(d.startTime !? {d.startTime[i]})
				++ ", \"endTime\": " ++ num.(d.endTime !? {d.endTime[i]})
				++ ", \"peakTime\": " ++ num.(pt)
				++ ", \"peakAmp\": " ++ num.(d.peakAmp !? {d.peakAmp[i]})
				++ ", \"attackDur\": " ++ num.(d.attackDur !? {d.attackDur[i]})
				++ ", \"releaseDur\": " ++ num.(d.releaseDur !? {d.releaseDur[i]})
				++ ", \"temporalCentroid\": " ++ num.(d.temporalCentroid !? {d.temporalCentroid[i]})
				++ ", \"keynum\": " ++ num.(d.keynum !? {d.keynum[i]})
				++ "}"
			};
			records = records.add(
				"  {\n"
				++ "    \"file\": \"" ++ esc.(rel) ++ "\",\n"
				++ "    \"sampleRate\": " ++ num.(d.sampleRate)
				++ ", \"numChannels\": " ++ num.(d.numChannels)
				++ ", \"duration\": " ++ num.(d.duration)
				++ ", \"numFrames\": " ++ num.(d.numFrames)
				++ ", \"framehop\": " ++ num.(d.framehop)
				++ ", \"hoptime\": " ++ num.(d.hoptime) ++ ",\n"
				++ "    \"globalPeakTime\": " ++ num.(d.globalPeakTime)
				++ ", \"globalPeakAmp\": " ++ num.(d.globalPeakAmp) ++ ",\n"
				++ "    \"keynumFromFileName\": " ++ num.(d.keynumFromFileName) ++ ",\n"
				++ "    \"keynumFromPitchFound\": " ++ numArr.(d.keynumFromPitchFound) ++ ",\n"
				++ "    \"numSections\": " ++ (d.peakTime ? []).size ++ ",\n"
				++ "    \"sections\": [\n" ++ sections.join(",\n") ++ "\n    ]\n"
				++ "  }"
			);
			d.free;
		};
	};

	File.use(outPath, "w", {|f|
		f.write(
			"{\n"
			++ "  \"generator\": \"dump_golden.scd\",\n"
			++ "  \"role\": \"behavioral reference (decision equivalence, ADR-0003)\",\n"
			++ "  \"analyzer\": \"SuperSampler SC quark 0.7.0 / SS_SCMIR\",\n"
			++ "  \"params\": {\"startThresh\": 0.01, \"endThresh\": 0.01, \"onsetThresh\": 0.5, "
			++ "\"groupingThresh\": 0.32, \"filenameAsNote\": false, \"pitchThresh\": "
			++ num.(SampleDescript.pitchThresh) ++ "},\n"
			++ "  \"analysisRate\": " ++ num.(SS_SCMIR.samplingrate)
			++ ", \"framehopRef\": " ++ num.(SS_SCMIR.framehop) ++ ",\n"
			++ "  \"files\": [\n" ++ records.join(",\n") ++ "\n  ]\n"
			++ "}\n"
		);
	});
	("wrote: " ++ outPath).postln;

	if(failures.isEmpty) {
		"GOLDEN_DUMP_DONE".postln;
		0.exit;
	} {
		("GOLDEN_DUMP_FAILED " ++ failures.size ++ ": " ++ failures.join(", ")).postln;
		1.exit;
	};
};
)
