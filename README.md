# SuperSampler
SuperSampler is a sampler synthesizer project on SuperCollider.  The sampler is applying audio content analysis techniques to make decisions on sample processing.


##Install

###Install Dependences

* First, make sure you have installed SC3 plugins:  
https://github.com/supercollider/sc3-plugins

* SuperSampler is using SCMIR to extract data form sampled sounds.  Download SCMIR at:  
http://composerprogrammer.com/code.html  
unzip, and put it under ~/Library/Application Supprt/SuperCollider/Extensions/

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

* Also, SuperSampler is depended on wslib Quark, it should be automatically installed when you install the SuperSampler Quark.  If somehow it doesn't happen, type:  
```supercollider
Quarks.install("https://github.com/supercollider-quarks/wslib");
```

###Install SuperSampler


SuperSampler is now a Quark.  However it not yet published to supercollider-quarks list.   Therefore it will not be shown in ```Quarks.gui``` window. To install SuperSampler quark, type:  
```supercollider
Quarks.install("https://github.com/allenloves/SuperSampler");
```

**Fix the UnitTesting Bug**
UnitTesting is installed along with KDTree Quark while SuperSampler is installed. However it has not updated with new version of SuperCollider for a while which caused a compatibility issue.  To solve this, go to your downloaded-quarks/UnitTesting folder and delete everything inside.  You will be good to go. 


