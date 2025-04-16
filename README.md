
# Robot36 - SSTV Image Decoder

## Audio Line-Level to Microphone-Level Converter
Decoding SSTV signals is more reliable with a clean input. Using a direct cable connection instead of acoustic coupling avoids echo, distortion, and environmental noise.

Most smartphones use TRRS connectors for headsets. In these connectors, the sleeve and the second ring (next to the sleeve) serve dual roles: depending on the standard, one is the microphone input and the other is ground. The tip and first ring carry the left and right audio channels.

When a TRS plug is inserted into a TRRS jack, the sleeve and second ring are shorted together. This allows regular stereo headphones (without a microphone) to work correctly.

Instead of determining which pin is MIC or GND for each device, galvanic isolation can be used. This avoids compatibility issues, eliminates ground loops, protects against damage, and improves robustness.

Using a line-level output (e.g., from a radio or sound card) as a microphone input introduces several challenges:

* Line-level signals swing around 1 V, while electret microphones produce signals in the millivolt range, so attenuation is needed.
* Electret microphones are biased via the TRRS connector, allowing their internal amplifiers to function. This bias must be blocked to avoid distortion.
* To make the smartphone recognize the input as a microphone, a resistor must be placed between the second ring and sleeve.

To reduce power consumption on the line-out device, a higher impedance can be achieved by inserting a resistor in series with the primary winding of a 1:1 audio transformer. If the source cannot drive high impedance, the primary can be connected directly, and attenuation applied on the secondary side. This increases power consumption and may heat the transformer.

Because the electret mic input is high-impedance and AC-coupled, the transformer’s secondary can resonate if left unterminated. Adding a resistor across the secondary dampens this resonance and flattens the frequency response. A value equal to the transformer’s impedance is typical, but a lower value can be used to both improve damping and provide additional attenuation. The ratio of the series resistor on the primary to the parallel resistor on the secondary determines the overall attenuation.

### Example Values
* Transformer: 1:1 audio transformer, 600 Ω impedance, 140 Ω DC resistance
* Primary side: 2.2 kΩ resistor in series (any value between 1 kΩ and 10 kΩ is fine; a 10 kΩ potentiometer allows adjustment)
* Secondary side: 100 Ω resistor across the winding for damping and attenuation
* DC blocking capacitor: 2.2 µF film capacitor (anything between 1 µF and 100 µF works; avoid values below 1 µF to keep low-frequency SSTV content intact)
* Microphone sensing resistor: 2.2 kΩ between the second ring and sleeve (values near 2 kΩ are fine)

### Schematic
```
 [Line IN] O---[R1]---+||+---+---|C1|---+---O [Ring 2]
                      S||S   |          |
                  T1: S||S  [R2]       [R3]
                      S||S   |          |
[Line GND] O----------+||+---+----------+---O [Sleeve]
```
Explanation of Symbols:

* [R1]: 2.2 kΩ series resistor (primary side attenuation)
* T1: 1:1 audio transformer
* [C1]: 2.2 µF capacitor (DC blocking)
* [R2]: 100 Ω damping resistor (across secondary)
* [R3]: 2.2 kΩ MIC detect resistor (between MIC and GND)
* [Line IN], [Line GND]: input from radio/sound card
* [Ring 2], [Sleeve]: TRRS plug connections to smartphone
