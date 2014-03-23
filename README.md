Plumble
=======

Plumble is a robust GPLv3 Mumble client for Android that uses the Jumble protocol implementation.

<a href="https://play.google.com/store/apps/details?id=com.morlunk.mumbleclient">
  <img alt="Get it on Google Play" src="https://developer.android.com/images/brand/en_generic_rgb_wo_45.png" />
</a>
<a href="https://f-droid.org/repository/browse/?fdid=com.morlunk.mumbleclient">
  <img alt="Get it on F-Droid" src="https://fsfe.org/campaigns/android/f-droid.png" width="45" />
</a>

You can find legacy Plumble code and issues at https://www.github.com/Morlunk/Plumble-Legacy/

Building on GNU/Linux
---------------------

    git submodule update --init --recursive
    ndk-build -C libraries/Jumble/src/main/jni/
    ./gradlew assembleDebug

It's that simple!


Translate
---------

Contribute translations to Plumble using [Transifex](https://www.transifex.com/projects/p/plumble/)!
