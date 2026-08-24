package com.suxsem.androidwakeword

internal class SpeexWrapper {

    init {
        System.loadLibrary("speex-lib")
    }

    external fun initSpeex()
    external fun processAudio(audioData: ShortArray): Boolean
    external fun destroySpeex()

}