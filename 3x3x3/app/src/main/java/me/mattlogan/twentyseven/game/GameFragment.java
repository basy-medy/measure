package me.mattlogan.twentyseven.game;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import me.mattlogan.twentyseven.PlaneTracker;
import me.mattlogan.twentyseven.R;
import me.mattlogan.twentyseven.ServiceLocator;
import me.mattlogan.twentyseven.databinding.FragmentGameBinding;
import me.mattlogan.twentyseven.messages.IncomingMessageRouter;
import me.mattlogan.twentyseven.messages.MessagePublisher;
import timber.log.Timber;

public class GameFragment extends Fragment
    implements IncomingMessageRouter.GameplayListener, BoardView.ActionListener {

  private IncomingMessageRouter messageRouter;
  private MessagePublisher messagePublisher;
  private PlaneTracker planeTracker;

  private FragmentGameBinding binding;

  private Game game;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup root, Bundle state) {
    ServiceLocator sl = ServiceLocator.get();
    messageRouter = sl.messageRouter();
    messagePublisher = sl.messagePublisher();
    planeTracker = sl.planeTracker();

    binding = FragmentGameBinding.inflate(inflater, root, false);
    messageRouter.addGameUpdatedListener(this);
    binding.boardView.setActionListener(this);
    binding.planeLabel.setText(planeTracker.currentPlane().toDisplayString());
    binding.buttonNewGame.setOnClickListener(v -> onNewGameClicked());
    onGameUpdated(Game.createNewGame());
    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    messageRouter.removeGameUpdatedListener(this);
    binding = null;
  }

  @Override
  public void onGameUpdated(Game game) {
    Timber.d("onGameUpdated: %s", game);
    this.game = game;
    updateViews();
  }

  @Override
  public void onActionTaken(int space, char mark) {
    Timber.d("onActionTaken, space: %d, mark %c", space, mark);
    char[][][] grid = game.grid();
    grid[space % 3][space / 3][planeTracker.currentPlane().zValue()] = mark;
    game.incrementTurn();
    messagePublisher.publishGameUpdateMessage(game);
    updateViews();
  }

  private void updateViews() {
    if (binding == null) return;
    WinChecker.Win win = WinChecker.checkForWinner(game.grid());
    if (win != null) {
      binding.gameStatusText.setText(getString(R.string.x_wins, win.winner()));
      binding.boardView.showWin(win.winner(), win.spaces(), planeTracker.currentPlane().zValue());
      binding.buttonNewGame.setVisibility(View.VISIBLE);
    } else {
      binding.boardView.updateTurn(game.turn());
      binding.boardView.updateGrid(game.grid(), planeTracker.currentPlane().zValue());
      binding.boardView.clearWin();
      binding.gameStatusText.setText(getString(R.string.xs_turn, game.turn()));
      binding.buttonNewGame.setVisibility(View.GONE);
    }
  }

  private void onNewGameClicked() {
    Timber.d("onNewGameClicked");
    game = Game.createNewGame();
    updateViews();
    messagePublisher.publishGameUpdateMessage(game);
  }
}
