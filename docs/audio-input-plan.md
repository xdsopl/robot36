# Audio input integration

## Interface preparation

Prepare portrait and landscape layouts for microphone, WAV file and playback
capture inputs. Keep the existing microphone receiver operational while the new
inputs are introduced. File and playback capture selectors remain disabled until
their input paths are connected.

This step adds the UI resources, reserves space for the controls, and introduces
Java files containing package declarations and responsibility notes only.

## Current call chain

```text
MainActivity.initAudioRecord()
  -> AudioRecord.OnRecordPositionUpdateListener
  -> read PCM and convert PCM16 samples to floats
  -> level / spectrum analysis
  -> Decoder.process()
  -> display and image saving
```

## Next integration boundary

Move AudioRecord construction and blocking reads out of MainActivity without
changing the SSTV decoding algorithm. The intended handoff is:

```text
MicrophoneAudioSource, FileAudioSource or AudioPlaybackCaptureSource
  -> AudioSource PCM callback (shared input contract)
  -> session ownership and stale-callback checks
  -> MainActivity's decoder consumer
  -> Decoder.process()
```



| Responsibility | Intended owner |
| --- | --- |
| PCM layout, valid frame count and borrowed-buffer contract | AudioSource |
| Serial preparation, reads, cancellation and resource cleanup | WorkerAudioSource |
| AudioRecord configuration and PCM conversion | MicrophoneAudioSource |
| Active input selection and session identity | AudioSessionManager |
| User settings, actual decoder format and UI updates | MainActivity |
| SSTV signal interpretation and image reconstruction | Existing Decoder |


