package me.mattlogan.twentyseven;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import me.mattlogan.twentyseven.databinding.ActivityMainBinding;
import me.mattlogan.twentyseven.intro.IntroFragment;
import me.mattlogan.twentyseven.messages.NearbyConnectionManager;
import timber.log.Timber;

public final class MainActivity extends AppCompatActivity {

  private NearbyConnectionManager connectionManager;

  private final ActivityResultLauncher<String[]> permissionLauncher =
      registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
        boolean allGranted = !result.containsValue(false);
        if (allGranted) {
          Timber.d("All permissions granted");
          startNearby();
        } else {
          Timber.d("Some permissions denied, Nearby may not work");
          startNearby();
        }
      });

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    connectionManager = ServiceLocator.get().connectionManager();

    if (savedInstanceState == null) {
      getSupportFragmentManager()
          .beginTransaction()
          .add(R.id.fragment_container, new IntroFragment())
          .commit();
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    requestPermissionsAndStart();
  }

  @Override
  public void onStop() {
    super.onStop();
    connectionManager.stop();
  }

  private void requestPermissionsAndStart() {
    List<String> needed = new ArrayList<>();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      addIfNeeded(needed, Manifest.permission.BLUETOOTH_ADVERTISE);
      addIfNeeded(needed, Manifest.permission.BLUETOOTH_CONNECT);
      addIfNeeded(needed, Manifest.permission.BLUETOOTH_SCAN);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      addIfNeeded(needed, Manifest.permission.NEARBY_WIFI_DEVICES);
    }
    addIfNeeded(needed, Manifest.permission.ACCESS_FINE_LOCATION);

    if (needed.isEmpty()) {
      startNearby();
    } else {
      permissionLauncher.launch(needed.toArray(new String[0]));
    }
  }

  private void addIfNeeded(List<String> list, String permission) {
    if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
      list.add(permission);
    }
  }

  private void startNearby() {
    connectionManager.start();
  }
}
