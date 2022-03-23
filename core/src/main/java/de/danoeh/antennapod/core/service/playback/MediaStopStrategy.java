package de.danoeh.antennapod.core.service.playback;

import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer;
import de.danoeh.antennapod.playback.base.PlayerStatus;

public class MediaStopStrategy extends AbstractKeycodeStrategy {
    MediaStopStrategy(PlayerStatus status) {
        this.status = status;
    }

    @Override
    public boolean execute(PlaybackServiceMediaPlayer mediaPlayer) {
        if (status == PlayerStatus.PLAYING) {
            mediaPlayer.pause(true, true);
        }

        return true;
    }
}
