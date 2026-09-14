#!/bin/bash
# Rebuild the Android toolchain in the sandbox (nothing outside /home/user survives
# a message boundary, so this has to be re-runnable).
set -x
sudo -n apt-get update -qq >/dev/null 2>&1
sudo -n apt-get install -y -qq openjdk-21-jdk-headless unzip >/dev/null 2>&1
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

sudo -n mkdir -p /opt/android-sdk /opt/gradle-home
sudo -n chown -R 1000:1000 /opt/android-sdk /opt/gradle-home

mkdir -p /opt/android-sdk/licenses
printf '\n24333f8a63b6825ea9c5514f83c2829b004d1fee' > /opt/android-sdk/licenses/android-sdk-license
printf '\n84831b9409646a918e30573bab4c9c91346d8abd' > /opt/android-sdk/licenses/android-sdk-preview-license

cd /tmp
curl -sSL -o cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-9862592_latest.zip
rm -rf /tmp/cmdext && mkdir -p /tmp/cmdext
unzip -q -o cmdtools.zip -d /tmp/cmdext
mkdir -p /opt/android-sdk/cmdline-tools
rm -rf /opt/android-sdk/cmdline-tools/latest
mv /tmp/cmdext/cmdline-tools /opt/android-sdk/cmdline-tools/latest

yes 2>/dev/null | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk --licenses >/dev/null 2>&1
/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk "platform-tools" "platforms;android-36" "build-tools;36.0.0"

curl -sSL -o /tmp/gradle.zip https://services.gradle.org/distributions/gradle-8.13-bin.zip
rm -rf /opt/gradle-8.13
unzip -q -o /tmp/gradle.zip -d /opt
sudo -n ln -sf /opt/gradle-8.13/bin/gradle /usr/local/bin/gradle

# D8 needs more memory than this box has; swap stops the OOM killer eating the build
if [ ! -f /swapfile ]; then
  sudo -n fallocate -l 3G /swapfile && sudo -n chmod 600 /swapfile &&
  sudo -n mkswap /swapfile >/dev/null 2>&1 && sudo -n swapon /swapfile
fi
echo "TOOLCHAIN READY"
java -version 2>&1 | head -1
