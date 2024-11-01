#!/bin/bash

git config user.email chankyuuketsuki@gmail.com
git config user.name "吸血鬼ちゃん"

wget https://coliseum.ai/api/tournaments/aic2024/download/scaffold -O aic2024.zip
unzip aic2024.zip
mv AIC2024/jars .
rm -r AIC2024
rm aic2024.zip
