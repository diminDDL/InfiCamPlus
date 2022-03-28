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
