# SuperSampler
SuperSampler is a sampler synthesizer project on SuperCollider.  The sampler is applying audio content analysis techniques to make decisions on sample processing.


## What's new in 0.6.0

- **Peak normalization (on by default).** Samples are now peak-normalized on
  playback so every sample's peak aligns to `headroomRef` (default 0.7),
  making envelope and `amp:` values consistent across differently-recorded
  samples. Set `~yourSampler.normalize = false` to get the pre-0.6 behavior.
  Dense layering can therefore sum above full scale — a master limiter (on
  by default) acts as the safety net: SuperSampler voices route through a
  dedicated internal audio bus into the limiter, which mixes into
  `SSampler.defaultOutputBus` without touching other music on that bus.
  `defaultOutputBus` stays yours: point it at your own FX-chain bus and the
  limiter follows it live. Disable with `SSampler.limiterOff` (voices then
  output straight to `defaultOutputBus`).
- **playEnv envelopes & cleanup.** `.playEnv` now accepts `ampenv:`, `panenv:`
  and `bendenv:` (all spanning the target envelope's duration), and releases
  any residual voices when the envelope ends.
- `SamplerQuery.playing` entries are now lightweight `SamplerVoice` proxies;
  `.set` / `.free` on them still reach the underlying `Synth` unchanged.
- **Breaking (minor):** `.playEnv` gained `ampenv:`, `panenv:`, `bendenv:` parameters
  inserted before `out:`/`midiChannel:` — callers passing those positionally must
  switch to keyword arguments. Same for `SamplerDB.playEnv`.
- Playback gain is now applied linearly on all paths (it was effectively squared on
  the non-granular path before), so `amp:` values behave consistently.


## IMPORTNAT!!! Changing name space.
The Sampler class is now named SSampler in order to free the namespace for others who wish to write their own sampler synthesizer.
Please search and replace all in your code.  Sorry for the inconvenience.

## Install

### Install Dependences

* First, make sure you have installed SC3 plugins:  
https://github.com/supercollider/sc3-plugins

<!--
* Second, install SCMIR:
https://composerprogrammer.com/code.html

Copy the SCMIRExtentions folder in the .zip file to your SuperCollider extension folder.  Don't copy other folders.


<!---
* **Fix SCMIR Bug:** 
```
There is a bug in SCMIR with SuperCollider 3.7 due to the change in SuperCollider.
If you are using SuperCollider 3.7, please do the following to fix this bug: 

Open up SCMIRExtensions/Classes/SCMIRScore.sc and change line 15 from

cmd = program + "-v -2 -N" + oscFilePath.quote

to

cmd = program + "-V -2 -N" + oscFilePath.quote  // Change the lower-case v to capital V
```
-->

* Also, SuperSampler is depended on wslib and KDTree Quarks, it should be automatically installed when you install the SuperSampler Quark.  If somehow it doesn't happen, type:  
```supercollider
Quarks.install("https://github.com/supercollider-quarks/wslib");
Quarks.install("https://github.com/supercollider-quarks/KDTree");
```

### Install SuperSampler


SuperSampler is now a Quark.  However it not yet published to supercollider-quarks list.   Therefore it will not be shown in ```Quarks.gui``` window. To install SuperSampler quark, type:  
```supercollider
Quarks.install("https://github.com/allenloves/SuperSampler");
```


