package com.mapbox.navigation.ui.voice;

import com.mapbox.api.directions.v5.models.VoiceInstructions;

import timber.log.Timber;

class NavigationVoiceListener implements VoiceListener {

  private SpeechPlayerProvider speechPlayerProvider;
  private SpeechAudioFocusManager audioFocusManager;

  NavigationVoiceListener(SpeechPlayerProvider speechPlayerProvider,
      SpeechAudioFocusManager audioFocusManager) {
    this.speechPlayerProvider = speechPlayerProvider;
    this.audioFocusManager = audioFocusManager;
  }

  @Override
  public void onStart() {
    audioFocusManager.requestAudioFocus();
  }

  @Override
  public void onDone() {
    audioFocusManager.abandonAudioFocus();
  }

  @Override
  public void onError(String errorText, VoiceInstructions voiceInstructions) {
    Timber.d("Issue3216 errorText=%s, voiceInstructions=%s", errorText, voiceInstructions.announcement());
    speechPlayerProvider.retrieveAndroidSpeechPlayer().play(voiceInstructions);
  }
}
