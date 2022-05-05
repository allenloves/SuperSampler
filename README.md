# SuperSampler
SuperSampler is a sampler synthesizer project on SuperCollider.  The sampler is applying audio content analysis techniques to make decisions on sample processing.


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


