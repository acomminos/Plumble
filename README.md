Plumble
=======

Plumble is a robust GPLv3 Mumble client for Android that uses the Jumble protocol implementation.

You can find legacy Plumble code and issues at https://www.github.com/Morlunk/Plumble-Legacy/.

Building on GNU/Linux
---------------------

    git submodule update --init --recursive
    ndk-build -C Plumble/libraries/Jumble/jni/
    ./gradlew assembleDebug

It's that simple!
