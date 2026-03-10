package com.larvalabs.betweenus;

import android.app.Application;
import android.content.Context;

import com.larvalabs.betweenus.core.Constants;
import com.larvalabs.betweenus.utils.Utils;

/**
 *
 */
public class BetweenUsApplication extends Application {

    private static BetweenUsApplication instance;

    public static Context get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        Constants.init();

        // Location initialization is deferred to IntroActivity where
        // runtime permission is requested first (required on Android 6+).
        // Alarm scheduling is also deferred until permission is granted.
        Utils.log("Application initialized.");
    }
}
