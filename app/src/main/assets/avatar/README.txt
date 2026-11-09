PUT YOUR 3D MODEL HERE
======================

File name (exact):   app/src/main/assets/avatar/avatar.glb

There is intentionally NO model in this folder. Until you add one, the app shows a clearly labelled
placeholder on the home screen. Rebuild the APK after adding the file.

You can also skip rebuilding: open the app, Settings -> 3D avatar -> "Import .glb model".
An imported model is stored in the app's private storage and takes priority over this one.

Model requirements
- glTF 2.0 binary (.glb), fully self-contained (textures and data inside the file).
- Do not use Draco, Meshopt or Basis compression (may not load).
- Y-up, facing +Z (the glTF default). Any real-world scale works; the camera frames it automatically.
- Phone-friendly size: ideally under 15 MB, under 60k triangles, textures 1024 px or smaller.
  The app refuses files above 40 MB (weak phones), 80 MB (normal) or 120 MB (strong phones).

What makes each feature work (nothing is faked, missing parts are simply skipped)
- Facial expressions, blinking, mouth movement: morph targets (blend shapes) named like ARKit /
  Ready Player Me / VRM: eyeBlinkLeft, eyeBlinkRight, mouthSmile(Left/Right), eyeWide(Left/Right),
  eyeSquint(Left/Right), browInnerUp, browOuterUp(Left/Right), browDown(Left/Right),
  mouthFrown(Left/Right), jawOpen (or mouthOpen). Also accepted: Blink, Joy, Fun, Angry, Sorrow, A.
  For Blender exports, enable "Shape Keys" and "Include: Custom Properties" so target names are kept.
- Head motion: a node whose name ends in "Head" (Head, mixamorig:Head, J_Bip_C_Head). Otherwise only a small
  whole-model sway is used.
- Gestures and idle: animation clips whose names contain these words (case-insensitive):
    idle / breath / stand     -> idle loop
    wave / greet / hello      -> greeting wave
    nod                       -> small nod
    tilt                      -> head tilt
    happy / joy / cheer       -> happy reaction
    laugh                     -> laughing reaction
    think / ponder            -> thinking pose (loops while thinking)
    listen                    -> listening pose (loops while listening)
    talk / speak              -> speaking gestures (loops while speaking)
  Settings -> 3D avatar -> "Avatar status" lists exactly what was found in your model.

Lip sync
Mouth movement follows the loudness of the voice (open and close). It is not true phoneme lip sync.
For that you need viseme blend shapes (viseme_aa, viseme_O, ...) AND a viseme/phoneme source such as an
audio-analysis library. Gemini Live does not provide viseme timing.
