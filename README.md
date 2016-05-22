# SuperSampler
SuperSampler is a sampler synthesizer project on SuperCollider.  The sampler is applying audio content analysis techniques to make decisions on sample processing.


##Install

###Install Dependences

* First, make sure you have installed SC3 plugins:  
https://github.com/supercollider/sc3-plugins

* SuperSampler is using SCMIR to extract data form sampled sounds.  Download SCMIR at:  
http://composerprogrammer.com/code.html  
unzip, and put it under ~/Library/Application Supprt/SuperCollider/Extensions/

* Also, SuperSampler is depended on wslib Quark, it should be automatically installed when you install the SuperSampler Quark.  If somehow it doesn't happen, type:  
```supercollider
Quarks.install("https://github.com/supercollider-quarks/wslib");
```

###Install SuperSampler
SuperSampler is now a Quark, in SuperCollider type:  
```supercollider
Quarks.install("https://github.com/allenloves/SuperSampler");
```
