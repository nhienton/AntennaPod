package de.danoeh.antennapod.core.service.playback;

import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer;
import de.danoeh.antennapod.playback.base.PlayerStatus;

public class MediaPauseStrategy extends AbstractKeycodeStrategy {
    MediaPauseStrategy(PlayerStatus status) {
        this.status = status;
    }

    public boolean execute(PlaybackServiceMediaPlayer mediaPlayer) {
        if (status == PlayerStatus.PLAYING) {
            mediaPlayer.pause(!UserPreferences.isPersistNotify(), false);
            return true;
        }
        return false;
    }
}
