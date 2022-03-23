package de.danoeh.antennapod.core.service.playback;

import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer;
import de.danoeh.antennapod.playback.base.PlayerStatus;

public class MediaPreviousStrategy extends AbstractKeycodeStrategy {
    @Override
    public boolean execute(PlaybackServiceMediaPlayer mediaPlayer) {
        if (getStatus(mediaPlayer) == PlayerStatus.PLAYING || getStatus(mediaPlayer) == PlayerStatus.PAUSED) {
            mediaPlayer.seekTo(0);
            return true;
        }
        return false;

    }
}
