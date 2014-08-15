package com.donnfelker.android.bootstrap.authenticator;


import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.donnfelker.android.bootstrap.Injector;
import com.donnfelker.android.bootstrap.core.BootstrapService;
import com.donnfelker.android.bootstrap.core.Constants;
import com.donnfelker.android.bootstrap.ui.MainActivity;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.model.GraphUser;
import com.github.kevinsawicki.wishlist.Toaster;
import com.squareup.otto.Bus;

import javax.inject.Inject;

import static android.accounts.AccountManager.KEY_AUTHTOKEN;


public class FacebookAuthenticatorActivity extends AccountAuthenticatorActivity {

    private static final String PARAM_LOGIN_TYPE_FACEBOOK = "facebook";

    private Boolean mConfirmCredentials = false;
    private Boolean mRequestNewAccount = true;

    private AccountManager mAccountManager;
    private ProgressDialog mProgressDialog = null;

    @Inject
    BootstrapService bootstrapService;

    @Inject
    Bus bus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Injector.inject(this);
        mAccountManager = AccountManager.get(this);

        final ProgressDialog progressDialog = ProgressDialog.show(
                FacebookAuthenticatorActivity.this, "Connecting to Facebook",
                "Logging in with Facebook");

        doFacebookSso(progressDialog, savedInstanceState);

    }


    @Override
    protected void onResume() {
        super.onResume();
        bus.register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        bus.unregister(this);
    }

    /**
     * Facebook SSO O auth
     */
    private void doFacebookSso(final ProgressDialog progressDialog, final Bundle savedInstanceState) {
        try {
            Session.openActiveSession(this, true, new Session.StatusCallback() {
                @Override
                public void call(Session session, SessionState state, Exception exception) {
                    if (exception == null) {
                        if (state.equals(RESULT_CANCELED)) {
                            Toast.makeText(FacebookAuthenticatorActivity.this, "FB login cancelled",
                                    Toast.LENGTH_LONG).show();
                        } else if (state.isOpened()) {
                            if (progressDialog != null && progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                            Toast.makeText(FacebookAuthenticatorActivity.this, "Logged in with Facebook.",
                                    Toast.LENGTH_LONG).show();

                            loginFacebook(progressDialog, session);
                        }
                    } else {
                        error(exception.getMessage());
                    }
                }
            });
        } catch (Exception ex) {
            Log.i("Bootstrap - SignIn", ex.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
    }

    /*
     * Login a Bootstrap User with Facebook credentials
     */
    private void loginFacebook(final ProgressDialog progressDialog, final Session session) {

        // Request user data and show the results
        com.facebook.Request.newMeRequest(session, new com.facebook.Request.GraphUserCallback() {
            public void onCompleted(GraphUser graphUser, Response response) {
                boolean onSuccess = response.getError() == null;

                if (onSuccess) {
                    CharSequence text = "Logged in.";
                    Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();

                    onAuthenticationResult(graphUser.asMap().get("email").toString(), session.getAccessToken());

                    FacebookAuthenticatorActivity.this.startActivity(new Intent(FacebookAuthenticatorActivity.this, MainActivity.class));
                    FacebookAuthenticatorActivity.this.finish();

                } else {
                    Exception e = response.getError().getException();
                    final Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause != null) {
                        Toaster.showLong(FacebookAuthenticatorActivity.this, cause.getMessage());
                    }
                }

            }
        }).executeAsync();


    }

    /**
     * Called as a result of an Bootstrap Authentication if credentials needed to be confirmed
     * (needed for Android Account Manager in case credentials change/expire.)
     */
    private void finishConfirmCredentials(boolean result, String authKey, String password) {
        final Account account = new Account(authKey, Constants.Auth.BOOTSTRAP_ACCOUNT_TYPE);
        mAccountManager.setPassword(account, password);
        mAccountManager.setUserData(account, KEY_AUTHTOKEN, PARAM_LOGIN_TYPE_FACEBOOK);
        final Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_BOOLEAN_RESULT, result);
        setResult(RESULT_OK, intent);
        finish();
    }


    /**
     * Finishes the login process by creating/updating the account with the Android
     * AccountManager.
     */
    private void finishLogin(String authToken, String password) {

        final Account account = new Account(authToken, Constants.Auth.BOOTSTRAP_ACCOUNT_TYPE);
        if (mRequestNewAccount) {
            Bundle userData = new Bundle();
            userData.putString(KEY_AUTHTOKEN, PARAM_LOGIN_TYPE_FACEBOOK);
            mAccountManager.addAccountExplicitly(account, password, userData);
        } else {
            mAccountManager.setPassword(account, password);
            mAccountManager.setUserData(account, KEY_AUTHTOKEN, PARAM_LOGIN_TYPE_FACEBOOK);
        }
        final Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, authToken);
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, Constants.Auth.BOOTSTRAP_ACCOUNT_TYPE);
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }


    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("Authenticating...");
        dialog.setIndeterminate(true);

        mProgressDialog = dialog;
        return dialog;
    }

    /**
     * Hide progress dialog
     */
    @SuppressWarnings("deprecation")
    protected void hideProgress() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    /**
     * Show progress dialog
     */
    @SuppressWarnings("deprecation")
    protected void showProgress() {
        showDialog(0);
    }


    protected void error(String error) {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }


    /**
     * Called following a successful login to process the result and persist to AccountManager
     */
    public void onAuthenticationResult(String authToken, String password) {
        boolean success = ((authToken != null) && (authToken.length() > 0));
        hideProgress();

        if (success) {
            if (!mConfirmCredentials) {
                finishLogin(authToken, password);
            } else {
                finishConfirmCredentials(success, authToken, password);
            }
        } else {
            Toast.makeText(this, "Login Failed", Toast.LENGTH_LONG).show();
        }
    }
}
