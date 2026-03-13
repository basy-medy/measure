package me.mattlogan.twentyseven;

import android.content.Context;

import me.mattlogan.twentyseven.messages.IncomingMessageRouter;
import me.mattlogan.twentyseven.messages.MessagePublisher;
import me.mattlogan.twentyseven.messages.NearbyConnectionManager;

public final class ServiceLocator {

  private static ServiceLocator instance;

  private final PlaneTracker planeTracker;
  private final NearbyConnectionManager connectionManager;
  private final IncomingMessageRouter messageRouter;
  private final MessagePublisher messagePublisher;

  public static void init(Context context) {
    if (instance == null) {
      instance = new ServiceLocator(context.getApplicationContext());
    }
  }

  public static ServiceLocator get() {
    if (instance == null) {
      throw new IllegalStateException("ServiceLocator not initialized");
    }
    return instance;
  }

  private ServiceLocator(Context context) {
    planeTracker = new PlaneTracker();
    messageRouter = new IncomingMessageRouter();
    connectionManager = new NearbyConnectionManager(context, messageRouter);
    messagePublisher = new MessagePublisher(connectionManager);
  }

  public PlaneTracker planeTracker() {
    return planeTracker;
  }

  public NearbyConnectionManager connectionManager() {
    return connectionManager;
  }

  public IncomingMessageRouter messageRouter() {
    return messageRouter;
  }

  public MessagePublisher messagePublisher() {
    return messagePublisher;
  }
}
