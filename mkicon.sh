#!/bin/sh

#mksquare() {
#	size=$1
#	convert -background none -layers flatten -resize "$size"x"$size" logo_bg.svg logo_fg.svg \
#		-gravity Center \( +clone  -alpha extract \
#		-draw "fill black polygon 0,0 0,15 15,0 fill white circle 15,15 15,0" \
#		\( +clone -flip \) -compose Multiply -composite \
#		\( +clone -flop \) -compose Multiply -composite \
#		\) -alpha off -compose CopyOpacity -composite "$2"
#}

mksquare() {
	tmp="$(mktemp -t inficam_mkicon.XXXX.png)"
	convert -background none -layers flatten -resize "$1"x"$1" logo_bg.svg logo_fg.svg "$tmp"
	convert -background none -layers flatten -resize "$1"x"$1" "$tmp" \
		\( logo_mask.svg -colorspace gray \) -compose CopyOpacity -composite "$2"
	convert -background none -layers flatten -resize "$1"x"$1" "$tmp" \
		\( logo_mask_round.svg -colorspace gray \) -compose CopyOpacity -composite "$2"
	rm "$tmp"
}

mkcircle() {
	size=$1
	half=$(( $1 / 2 ))
	convert -background none -layers flatten -resize "$size"x"$size" logo_bg.svg logo_fg.svg \
		-gravity Center \( -size "$size"x"$size" xc:Black -fill White \
		-draw "circle $half $half $half 0" -alpha Copy \) \
		-compose CopyOpacity -composite "$2"
}

mkicon() {
	mksquare "$1" "$2.png"
	mkcircle "$1" "$2_round.png"
}

mkicon 48 app/src/main/res/mipmap-mdpi/ic_launcher
mkicon 72 app/src/main/res/mipmap-hdpi/ic_launcher
mkicon 96 app/src/main/res/mipmap-xhdpi/ic_launcher
mkicon 144 app/src/main/res/mipmap-xxhdpi/ic_launcher
mkicon 192 app/src/main/res/mipmap-xxxhdpi/ic_launcher

convert -background none -resize 108x108 logo_fg.svg app/src/main/res/mipmap-mdpi/ic_launcher_fg.png
convert -background none -resize 162x162 logo_fg.svg app/src/main/res/mipmap-hdpi/ic_launcher_fg.png
convert -background none -resize 216x216 logo_fg.svg app/src/main/res/mipmap-xhdpi/ic_launcher_fg.png
convert -background none -resize 324x324 logo_fg.svg app/src/main/res/mipmap-xxhdpi/ic_launcher_fg.png
convert -background none -resize 432x432 logo_fg.svg app/src/main/res/mipmap-xxxhdpi/ic_launcher_fg.png

convert -background none -resize 108x108 logo_bg.svg app/src/main/res/mipmap-mdpi/ic_launcher_bg.png
convert -background none -resize 162x162 logo_bg.svg app/src/main/res/mipmap-hdpi/ic_launcher_bg.png
convert -background none -resize 216x216 logo_bg.svg app/src/main/res/mipmap-xhdpi/ic_launcher_bg.png
convert -background none -resize 324x324 logo_bg.svg app/src/main/res/mipmap-xxhdpi/ic_launcher_bg.png
convert -background none -resize 432x432 logo_bg.svg app/src/main/res/mipmap-xxxhdpi/ic_launcher_bg.png
