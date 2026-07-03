# SuperSampler
SuperSampler is a sampler synthesizer project on SuperCollider.  The sampler is applying audio content analysis techniques to make decisions on sample processing.


## What's new in 0.7.0

### Load live-recorded Buffers

`load` now accepts Buffers mixed into the file list — record something,
then hand the Buffer straight to the sampler:

```supercollider
b = Buffer.alloc(s, s.sampleRate * 4);
//... record into b with RecordBuf, then:
~sampler.load([b], action: {|smp| smp.key(60)});
```

- The Buffer is rendered to a temp file (float WAV, so hot recordings
  don't clip) and analyzed like any sound file. The workflow is
  "record → load (a few seconds of analysis) → play".
- The temp file is auto-named after the Buffer's server and bufnum, so
  loading the same Buffer twice is skipped as a duplicate. Re-recorded
  into the same Buffer? Pass `override: true` to re-analyze.
- Name the recording yourself with an association:
  `~sampler.load([b -> \riff])`.
- Temp files are deleted when the sampler is freed. Your source Buffer
  is never touched.

The help files were also completed and modernized in this release cycle
(new pages for SamplerVoice, SamplerQuery, SamplerArguments,
SamplerScore; all pages brought up to date with 0.6.x behavior).


## What's new in 0.6.5 – 0.6.7

### 0.6.7 — Windows compatibility (0.6.7 is the first version expected to work on Windows)

- **Windows users could not load any sound file — fixed.** Two independent
  bugs in the SCMIR analysis pipeline broke every Windows load:
  - A broken platform check meant the non-realtime analysis was told to
    write its (unused) audio output to `/dev/null`, which does not exist on
    Windows — scsynth exited before analyzing anything. Windows now writes
    to a real file in the temp directory.
  - The code waiting for the analysis to finish polled the Unix `ps`
    command, which Windows doesn't have — SuperSampler read the analysis
    file back while it was still being written. The wait is now based on
    the process's actual exit, on every platform.

  If you tried SuperSampler on Windows before and it silently failed to
  load samples, please update and try again.

### 0.6.6 — `load` is reliable and repeatable

- **`action:` fires once, when the sampler is actually ready.** Previously
  the `load` completion action fired once *per file*, raced the sampler's
  internal bookkeeping, and never fired at all when every file was already
  loaded. It now fires exactly once, after all buffers are loaded and the
  kd-tree/keyRanges are built — the sampler is fully playable inside it.
  The action receives the sampler: `x.load(files, action: {|smp| smp.key(60)})`.
- **Duplicate files are pre-checked.** Files repeated within one `load` call,
  or already loaded with live server data, warn and are skipped before any
  analysis runs.
- **Re-loading no longer corrupts the corpus statistics.** The per-sampler
  averages (and thus the kd-tree position used by `SamplerDB.playEnv`
  diversity morphing) used to shrink every time the same files were loaded
  again; they are now recomputed in full on every load.
- **Lost server data is actually detected.** If the server rebooted (or the
  buffers were freed) since a file was loaded, `load` now really asks the
  server and re-reads the file — the old check trusted a stale client-side
  value and could leave the sampler silently playing nothing.

### 0.6.5 — lifecycle fixes

- **`.free` now works.** `SSampler.free` used to throw on its first line and
  release nothing. It now removes the sampler from its `SamplerDB`s (whose
  kd-trees rebuild without it), frees every sample's server buffers (these
  leaked before — buffer arrays were "freed" with a no-op), and clears the
  global name registry.
- **`SamplerDB` kd-trees no longer accumulate.** Every `load`/`add` used to
  duplicate all tree nodes, and removed samplers stayed searchable forever.
- **End-of-envelope voice release no longer prints server FAILUREs.** The
  `playEnv` cleanup gate could land on a voice that had just finished by
  itself; the harmless `/n_set Node not found` error is now suppressed for
  exactly that message.


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


