# A rework of the fork that is supposed to work flawlessly for T2S+ V2, and should work with "T2S Plus / T3S / T3 Pro".

- Thermal math code was reverse-engineered from scratch to match the official Xtherm app perfectly.
- Better image processing for RAW sensors (though it could be improved with denoising).
- Architecture changes to make adding other camera models easier.
- Adjusted shutter intervals to match sensor drift.
- Added barber pole pattern for temperatures exceeding the selected range or maxing out the sensor.
- Fixed gallery button on recent Android versions.
- Improved visual feedback for the bottom button row.
- Cleanups.
- More cleanups.

## More details

I can only test the raw sensor codepath. But the non-raw logic should work (TM).
Most camera management was moved to the library (auto-shutter logic, calibration). The palette calculations were moved into the app.
The UVC library required a patch to receive the calibration data. 

Knowing how things actually work now, using the original InfiCamPlus with an offset is a bad idea!

Much of the code was rewritten, especially on the C++ side. Some things seemed overcomplicated to me. I tried to keep it simple.
My IDE insisted on reformatting everything. I hope it's not too confusing.


## P2 Pro

P2 support was removed as part of the rework. It wasn't well integrated, and I could not keep it without significantly more work.
It should be possible to add it back, but I do not own the device to do it.


A fork of InfiCam from Netman that adds support for raw camera modes.

Downloads available at:
https://github.com/diminDDL/InfiCamPlus/releases

Original:
https://gitlab.com/netman69/inficam

Desktop Python Script:
https://github.com/diminDDL/IR-Py-Thermal

## Contributing
The primary language of this repository is English.
Please write issues, discussions, pull requests, and comments in English.
**Discussions in other languages will be deleted.**


## Camera Model Support Chart
### Legend
✅ - Fully supported

🆗 - Quite usable, but may have some small quirks

🟨 - Works but has quirks

🟥 - Doesn't work


| Model | VID:PID | Supported | Note | See More |
| ----- | ------- | --------- | ---- | -------- |
| T2S+ v1  | 1514:xxxx | ✅ | Original InfiCam supported this camera well. | [InfiCam](https://gitlab.com/netman69/inficam) |
| T2S+ v2  | 04b4:0100 | ✅ | Should be perfect. | [#2](https://github.com/diminDDL/InfiCamPlus/issues/2) |
| P2 Pro   | 0bda:5830 | 🟥 | Not supported at this time, PRs are welcome | [#1](https://github.com/diminDDL/InfiCamPlus/issues/1), [#11](https://github.com/diminDDL/InfiCamPlus/pull/11) |
| HT301    | 1514:0001 | 🟥 | Not supported at this time, PRs are welcome | [#5](https://github.com/diminDDL/InfiCamPlus/issues/5) |
| UTi261M/UTi722M | 0bda:5830 | 🟥 | Not supported at this time, PRs are welcome | [#7](https://github.com/diminDDL/InfiCamPlus/issues/7) |
| HT820| 0bda:5840 | ? | Users report it working. | [#12](https://github.com/diminDDL/InfiCamPlus/issues/12) |


