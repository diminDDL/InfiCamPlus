#!/bin/sh
convert -background none -resize 48x48 logo.svg app/src/main/res/mipmap-mdpi/ic_launcher.png
convert -background none -resize 72x72 logo.svg app/src/main/res/mipmap-hdpi/ic_launcher.png
convert -background none -resize 96x96 logo.svg app/src/main/res/mipmap-xhdpi/ic_launcher.png
convert -background none -resize 144x144 logo.svg app/src/main/res/mipmap-xxhdpi/ic_launcher.png
convert -background none -resize 192x192 logo.svg app/src/main/res/mipmap-xxxhdpi/ic_launcher.png

convert -background none -resize 48x48 logo_round.svg app/src/main/res/mipmap-mdpi/ic_launcher_round.png
convert -background none -resize 72x72 logo_round.svg app/src/main/res/mipmap-hdpi/ic_launcher_round.png
convert -background none -resize 96x96 logo_round.svg app/src/main/res/mipmap-xhdpi/ic_launcher_round.png
convert -background none -resize 144x144 logo_round.svg app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png
convert -background none -resize 192x192 logo_round.svg app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png

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
