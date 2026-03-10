package com.larvalabs.betweenus.activity;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.larvalabs.betweenus.AppSettings;
import com.larvalabs.betweenus.R;
import com.larvalabs.betweenus.client.ServerResponse;
import com.larvalabs.betweenus.client.ServerUtil;
import com.larvalabs.betweenus.events.ServerResponseEvent;
import com.larvalabs.betweenus.utils.Utils;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 *
 */
public class UserInfoActivity extends Activity {

    private EditText usernameEditText;
    private AppSettings appSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_user_info);

        appSettings = new AppSettings(this);

        // Get username EditText and set continue button click listener.
        usernameEditText = (EditText) findViewById(R.id.user_info_et);

        String username = appSettings.getUsername();
        if (!TextUtils.isEmpty(username)) {
            usernameEditText.setText(username);
        }

        usernameEditText.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                usernameEditText.setError(null);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        TextView continueButton = (TextView) findViewById(R.id.continue_btn);
        if (continueButton != null) {
            continueButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onContinueButtonClick();
                }
            });
        }
    }

    private void onContinueButtonClick() {
        if (isUsernameContentValid()) {
            long serverId = appSettings.getServerUserId();
            if (serverId == -1) {
                // Server unavailable — proceed to pairing directly
                TouchPhonesActivity.launch(UserInfoActivity.this);
                return;
            }
            ServerUtil.getService().setUsername(appSettings.getServerUserId(), appSettings.getUsername())
                    .enqueue(new Callback<ServerResponse>() {
                        @Override
                        public void onResponse(Call<ServerResponse> call, Response<ServerResponse> response) {
                            TouchPhonesActivity.launch(UserInfoActivity.this);
                        }

                        @Override
                        public void onFailure(Call<ServerResponse> call, Throwable t) {
                            Utils.error("Failed to set username on server: " + t.getMessage());
                            // Proceed anyway — server may be unavailable
                            TouchPhonesActivity.launch(UserInfoActivity.this);
                        }
                    });
        }
    }

    private boolean isUsernameContentValid() {
        String username = usernameEditText.getText().toString();
        if (TextUtils.isEmpty(username)) {
            usernameEditText.setError(getString(R.string.username_edittext_error));
            return false;
        }
        appSettings.setUsername(username);
        return true;
    }

}
