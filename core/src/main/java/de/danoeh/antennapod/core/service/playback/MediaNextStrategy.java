package de.danoeh.antennapod.core.service.playback;

import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer;
import de.danoeh.antennapod.playback.base.PlayerStatus;

public class MediaNextStrategy extends AbstractKeycodeStrategy {
    MediaNextStrategy(PlayerStatus status) {
        this.status = status;
    }

    public boolean execute(PlaybackServiceMediaPlayer mediaPlayer) {
        if (getStatus(mediaPlayer) == PlayerStatus.PLAYING || getStatus(mediaPlayer) == PlayerStatus.PAUSED) {
            mediaPlayer.skip();
            return true;
        }
        return false;
    }

}
