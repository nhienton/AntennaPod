package de.danoeh.antennapod.core.service.playback;

import de.danoeh.antennapod.playback.base.PlayerStatus;

public interface IKeycodeStrategyFactory {
    AbstractKeycodeStrategy createStrategy(int keycode, PlayerStatus status, boolean notificationButton);
}
