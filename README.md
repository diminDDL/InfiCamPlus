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

If you wish to add support for a camera that is not yet supported, please provide the VID and PID as well as any other information in an issue. Ideally you should be ready to work on the code yourself to add support for something as we can't work on cameras we don't have physically.

## Camera Model Support Chart
### Legend
✅ - Fully supported

🆗 - Quite usable, but may have some small quirks

🟨 - Works but has quirks

🟥 - Doesn't work


| Model | VID:PID | Supported | Note | See More |
| ----- | ------- | --------- | ---- | -------- |
| T2S+ v1  | 1514:xxxx | ✅ | Original InfiCam supported this camera well. | [InfiCam](https://gitlab.com/netman69/inficam) |
| T2S+ v2  | 04b4:0100 | ✅ | Working in v1.0.4. | [#2](https://github.com/diminDDL/InfiCamPlus/issues/2), [#18](https://github.com/diminDDL/InfiCamPlus/pull/18) |
| P2 Pro   | 0bda:5830 | ✅ | Users report it working in v1.0.3. Broken in v1.0.4 | [#1](https://github.com/diminDDL/InfiCamPlus/issues/1), [#11](https://github.com/diminDDL/InfiCamPlus/pull/11) |
| HT301    | 1514:0001 | 🟥 | Not supported at this time, PRs are welcome | [#5](https://github.com/diminDDL/InfiCamPlus/issues/5) |
| UTi261M/UTi722M | 0bda:5830 | 🟥 | Not supported at this time, PRs are welcome | [#7](https://github.com/diminDDL/InfiCamPlus/issues/7) |
| HT820| 0bda:5840 | ✅ | Users report it working. | [#12](https://github.com/diminDDL/InfiCamPlus/issues/12) |


