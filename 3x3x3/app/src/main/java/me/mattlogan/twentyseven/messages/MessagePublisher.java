package me.mattlogan.twentyseven.messages;

import me.mattlogan.twentyseven.Plane;
import me.mattlogan.twentyseven.game.Game;
import timber.log.Timber;

public final class MessagePublisher {

  static final String PLANE_SELECTED = "PLANE_SELECTED_";
  static final String GAME_UPDATED = "GAME_UPDATED_";

  private final NearbyConnectionManager connectionManager;

  public MessagePublisher(NearbyConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
  }

  public void publishPlaneSelectedMessage(Plane plane) {
    String message = PLANE_SELECTED + plane;
    Timber.d("Publishing plane selected message: %s", message);
    connectionManager.sendMessage(message);
  }

  public void publishGameUpdateMessage(Game game) {
    String message = GAME_UPDATED + game;
    Timber.d("Publishing game updated message: %s", message);
    connectionManager.sendMessage(message);
  }
}
