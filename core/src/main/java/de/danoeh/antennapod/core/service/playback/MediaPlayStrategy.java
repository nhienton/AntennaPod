package de.danoeh.antennapod.core.service.playback;

import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer;
import de.danoeh.antennapod.playback.base.PlayerStatus;

public class MediaPlayStrategy extends AbstractKeycodeStrategy {
    private PlayerStatus status;

    MediaPlayStrategy(PlayerStatus status) {
        this.status = status;
    }

    public boolean execute(PlaybackServiceMediaPlayer mediaPlayer) {
        if (status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED) {
            mediaPlayer.resume();
        } else if (status == PlayerStatus.INITIALIZED) {
            mediaPlayer.setStartWhenPrepared(true);
            mediaPlayer.prepare();
        }  else {
            return false;
        }
        return true;
    }
}
