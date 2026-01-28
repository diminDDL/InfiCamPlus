A fork of InfiCam from Netman that adds support for raw camera modes. 

Downloads available at:
https://github.com/diminDDL/InfiCamPlus/releases

Original:
https://gitlab.com/netman69/inficam

Desktop Python Script:
https://github.com/diminDDL/IR-Py-Thermal

## Camera Model Support Chart
### Legend
✅ - Fully supported

🆗 - Quite usable, but may have some small quirks

🟨 - Works but has quirks

🟥 - Doesn't work


| Model | VID:PID | Supported | Note | See More |
| ----- | ------- | --------- | ---- | -------- |
| T2S+ v1  | 1514:xxxx | ✅ | Original InfiCam supported this camera well. | [InfiCam](https://gitlab.com/netman69/inficam) |
| T2S+ v2  | 04b4:0100 | 🆗 | Temperature might require an offset compensation. | [#2](https://github.com/diminDDL/InfiCamPlus/issues/2) |
| P2 Pro   | 0bda:5830 | ✅ | Users report it working. | [#1](https://github.com/diminDDL/InfiCamPlus/issues/1), [#11](https://github.com/diminDDL/InfiCamPlus/pull/11) |
| HT301    | 1514:0001 | 🟥 | Not supported at this time, PRs are welcome | [#5](https://github.com/diminDDL/InfiCamPlus/issues/5) |
| UTi261M/UTi722M | 0bda:5830 | 🟥 | Not supported at this time, PRs are welcome | [#7](https://github.com/diminDDL/InfiCamPlus/issues/7) |
| HT820| 0bda:5840 | ✅ | Users report it working. | [#12](https://github.com/diminDDL/InfiCamPlus/issues/12) |


