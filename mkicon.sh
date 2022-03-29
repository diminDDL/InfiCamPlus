#!/bin/sh

mksr() {
	tmp="$(mktemp -t inficam_mksr.XXXX.png)"
	convert -background none -layers flatten -resize "$1"x"$1" logo_bg.svg logo_fg.svg "$tmp"
	convert -background none -layers flatten -resize "$1"x"$1" "$tmp" \
		\( logo_mask.svg -colorspace gray \) -compose CopyOpacity -composite "$2.png"
	convert -background none -layers flatten -resize "$1"x"$1" "$tmp" \
		\( logo_mask_round.svg -colorspace gray \) -compose CopyOpacity -composite "$2_round.png"
	rm "$tmp"
}

mkfgbg() {
	e=$(( $1 * 2 / 3 ))
	convert -background none -resize "$1"x"$1" logo_bg.svg "$2_bg.png"
	convert -background none -resize "$1"x"$1" -gravity center -scale "$e"x"$e" -extent "$1"x"$1" \
		logo_fg.svg "$2_fg.png"
}

mksr 48 app/src/main/res/mipmap-mdpi/ic_launcher
mksr 72 app/src/main/res/mipmap-hdpi/ic_launcher
mksr 96 app/src/main/res/mipmap-xhdpi/ic_launcher
mksr 144 app/src/main/res/mipmap-xxhdpi/ic_launcher
mksr 192 app/src/main/res/mipmap-xxxhdpi/ic_launcher

mkfgbg 108 app/src/main/res/mipmap-mdpi/ic_launcher
mkfgbg 162 app/src/main/res/mipmap-hdpi/ic_launcher
mkfgbg 216 app/src/main/res/mipmap-xhdpi/ic_launcher
mkfgbg 324 app/src/main/res/mipmap-xxhdpi/ic_launcher
mkfgbg 432 app/src/main/res/mipmap-xxxhdpi/ic_launcher
