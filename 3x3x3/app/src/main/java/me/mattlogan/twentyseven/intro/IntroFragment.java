package me.mattlogan.twentyseven.intro;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import me.mattlogan.twentyseven.Plane;
import me.mattlogan.twentyseven.PlaneTracker;
import me.mattlogan.twentyseven.R;
import me.mattlogan.twentyseven.ServiceLocator;
import me.mattlogan.twentyseven.databinding.FragmentIntroBinding;
import me.mattlogan.twentyseven.game.GameFragment;
import me.mattlogan.twentyseven.messages.IncomingMessageRouter;
import me.mattlogan.twentyseven.messages.MessagePublisher;
import me.mattlogan.twentyseven.messages.NearbyConnectionManager;

public class IntroFragment extends Fragment
    implements IncomingMessageRouter.RemotePlaneSelectedListener,
    NearbyConnectionManager.ConnectionListener {

  private PlaneTracker planeTracker;
  private MessagePublisher messagePublisher;
  private IncomingMessageRouter messageRouter;
  private NearbyConnectionManager connectionManager;

  private FragmentIntroBinding binding;

  private int numSelectedRemotePlanes;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup root, Bundle state) {
    ServiceLocator sl = ServiceLocator.get();
    planeTracker = sl.planeTracker();
    messagePublisher = sl.messagePublisher();
    messageRouter = sl.messageRouter();
    connectionManager = sl.connectionManager();

    binding = FragmentIntroBinding.inflate(inflater, root, false);
    messageRouter.addPlaneSelectedListener(this);
    connectionManager.setConnectionListener(this);

    binding.buttonFront.setOnClickListener(v -> onLocalPlaneSelected(Plane.FRONT));
    binding.buttonMiddle.setOnClickListener(v -> onLocalPlaneSelected(Plane.MIDDLE));
    binding.buttonBack.setOnClickListener(v -> onLocalPlaneSelected(Plane.BACK));

    // Enable buttons immediately — nearby runs in background
    enableButtons();

    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    messageRouter.removePlaneSelectedListener(this);
    connectionManager.setConnectionListener(null);
    binding = null;
  }

  private void enableButtons() {
    binding.buttonFront.setEnabled(true);
    binding.buttonMiddle.setEnabled(true);
    binding.buttonBack.setEnabled(true);
  }

  private void onLocalPlaneSelected(Plane plane) {
    messagePublisher.publishPlaneSelectedMessage(plane);
    planeTracker.updatePlane(plane);
    if (numSelectedRemotePlanes == 2) {
      continueToGame();
    } else {
      showWaiting();
    }
  }

  @Override
  public void onNearbyStarted() {
    // Nearby is ready — buttons are already enabled
  }

  @Override
  public void onPeerConnected(int totalConnected) {
    if (binding != null) {
      binding.connectionStatus.setVisibility(View.VISIBLE);
      binding.connectionStatus.setText(
          getString(R.string.peers_connected, totalConnected));
    }
  }

  @Override
  public void onPeerDisconnected(int totalConnected) {
    if (binding != null) {
      binding.connectionStatus.setText(
          getString(R.string.peers_connected, totalConnected));
    }
  }

  @Override
  public void onRemotePlaneSelected(Plane plane) {
    if (binding == null) return;
    switch (plane) {
      case FRONT:
        binding.buttonFront.setEnabled(false);
        break;
      case MIDDLE:
        binding.buttonMiddle.setEnabled(false);
        break;
      case BACK:
        binding.buttonBack.setEnabled(false);
        break;
    }

    if (++numSelectedRemotePlanes == 2 && planeTracker.currentPlane() != null) {
      continueToGame();
    }
  }

  private void showWaiting() {
    binding.buttonsLayout.setVisibility(View.GONE);
    binding.waitingText.setVisibility(View.VISIBLE);
  }

  private void continueToGame() {
    requireActivity().getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.fragment_container, new GameFragment())
        .commit();
  }
}
