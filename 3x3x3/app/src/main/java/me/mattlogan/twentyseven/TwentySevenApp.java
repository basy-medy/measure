package me.mattlogan.twentyseven;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

import timber.log.Timber;

public class TwentySevenApp extends Application {

  @Override
  public void onCreate() {
    super.onCreate();
    Timber.plant(new Timber.DebugTree());
    DynamicColors.applyToActivitiesIfAvailable(this);
    ServiceLocator.init(this);
  }
}
