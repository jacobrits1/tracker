package si.krivec.tracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.AccessTokenTracker;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.Profile;
import com.facebook.ProfileTracker;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.purplebrain.adbuddiz.sdk.AdBuddiz;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import analytics.AnalyticsApplication;
import asynctasks.FacebookUserSignUp;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private LoginButton loginButton;
    private ProfileTracker profileTracker;
    private AccessTokenTracker accessTokenTracker;
    private Button registerButton;
    private Button loginMailButton;

    // Firebase login
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;

    // Google Analytics
    private Tracker analyticsTracker;
    private static final String TAG = "Google Analytics";
    private static final String name = "MainActivity";

    CallbackManager callbackManager;
    public static String USER_FB_ID;

    private static final String ADDBUDDIZ_PUBLISHER_KEY = "579dda59-d218-483e-ad84-79e1fd885d2a";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initFacebookSDK();

        mAuth = FirebaseAuth.getInstance();
        initFirebaseAuth();

        setContentView(R.layout.activity_main);
        loginButton = (LoginButton) findViewById(R.id.login_button);
        //logoutButton = (Button) findViewById(R.id.btnLogout);

        registerButton = (Button) findViewById(R.id.btnRegisterMain);
        registerButton.setOnClickListener(this);

        loginMailButton = (Button) findViewById(R.id.btnLoginMain);
        loginMailButton.setOnClickListener(this);

        configureFacebookProfileTracker();

        configureFacebookAccessToken();

        configureGoogleAnalytics();

        configureAdBuddiz();

        SharedPreferences pref = getApplicationContext().getSharedPreferences("TrackerConf", 0); // 0 - for private mode
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean("signInWithEmailAndPassword", false);
        editor.commit();
    }

    private void initFacebookSDK() {
        FacebookSdk.sdkInitialize(getApplicationContext());
        callbackManager = CallbackManager.Factory.create();
    }

    private void configureGoogleAnalytics() {
        AnalyticsApplication application = (AnalyticsApplication) getApplication();
        analyticsTracker = application.getDefaultTracker();

        // Enable Advertising Features.
        analyticsTracker.enableAdvertisingIdCollection(true);

        analyticsTracker.send(new HitBuilders.EventBuilder()
                .setCategory("onCreate")
                .setAction("Application opened")
                .build());
    }

    private void configureAdBuddiz() {
        AdBuddiz.setPublisherKey(ADDBUDDIZ_PUBLISHER_KEY);
        AdBuddiz.cacheAds(this);
    }

    private void configureFacebookProfileTracker() {
        profileTracker = new ProfileTracker() {
            @Override
            protected void onCurrentProfileChanged(Profile oldProfile, Profile currentProfile) {
                analyticsTracker.send(new HitBuilders.EventBuilder()
                        .setCategory("onCreate")
                        .setAction("onCurrentProfileChanged")
                        .build());

                USER_FB_ID = currentProfile != null ? currentProfile.getId(): "";
                signUpUser(currentProfile);
            }
        };
    }

    private void signUpUser(Profile profile) {
        USER_FB_ID = profile != null ? profile.getId(): "";
        try {
            new FacebookUserSignUp().execute(profile).get();
        } catch (InterruptedException | ExecutionException e) {
            Log.d("MainActivity:", e.getMessage());
            FirebaseCrash.report(e);
            e.printStackTrace();
        }

        analyticsTracker.send(new HitBuilders.EventBuilder()
                .setCategory("Login")
                .setAction("User SIGN UP")
                .build());
    }

    private void configureFacebookAccessToken() {
        accessTokenTracker = new AccessTokenTracker() {
            @Override
            protected void onCurrentAccessTokenChanged(
                    AccessToken oldAccessToken,
                    AccessToken currentAccessToken) {

                analyticsTracker.send(new HitBuilders.EventBuilder()
                        .setCategory("onCreate")
                        .setAction("onCurrentAccessTokenChanged")
                        .build());

                // On AccessToken changes fetch the new profile which fires the event on
                // the ProfileTracker if the profile is different
                Profile.fetchProfileForCurrentAccessToken();
            }
        };

        if(AccessToken.getCurrentAccessToken() != null) {
            // Ensure that our profile is up to date
            Profile.fetchProfileForCurrentAccessToken();
            USER_FB_ID = Profile.getCurrentProfile().getId();
            Intent selectionActivity = new Intent(MainActivity.this, SelectionActivity.class);
            startActivity(selectionActivity);
        } else {
            loginButton.registerCallback(callbackManager,
                    new FacebookCallback<LoginResult>() {
                        @Override
                        public void onSuccess(LoginResult loginResult) {
                            analyticsTracker.send(new HitBuilders.EventBuilder()
                                    .setCategory("Login")
                                    .setAction("Facebook login SUCCESS")
                                    .build());

                            //Toast.makeText(getApplicationContext(), "Sucess login", Toast.LENGTH_SHORT).show();
                            LoginManager.getInstance().logInWithReadPermissions(MainActivity.this,
                                    Arrays.asList("public_profile", "user_friends"));

                            // Start app main screen
                            Intent selectionActivity = new Intent(MainActivity.this, SelectionActivity.class);
                            startActivity(selectionActivity);
                        }

                        @Override
                        public void onCancel() {
                            analyticsTracker.send(new HitBuilders.EventBuilder()
                                    .setCategory("Login")
                                    .setAction("CANCEL login")
                                    .build());
                            //Toast.makeText(getApplicationContext(), "Cancel login", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(FacebookException e) {
                            analyticsTracker.send(new HitBuilders.EventBuilder()
                                    .setCategory("Login")
                                    .setAction("Facebook login error: " + e.getMessage())
                                    .build());
                            //Toast.makeText(getApplicationContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            Log.d("Facebook Login ERROR: ", e.getMessage());
                        }
                    });
        }
    }

    private void initFirebaseAuth() {
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
                } else {
                    // User is signed out
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                }
                // ...
            }
        };
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onClick(View v) {
        /*if(v.getId() == logoutButton.getId()) {
            analyticsTracker.send(new HitBuilders.EventBuilder()
                    .setCategory("Login")
                    .setAction("User LOGOUT")
                    .build());

            LoginManager.getInstance().logOut();
        }*/

        if(v.getId() == registerButton.getId()) {
            Intent register = new Intent(this, RegistrationActivity.class);
            startActivity(register);
        } else if(v.getId() == loginMailButton.getId()) {
            Intent login = new Intent(this, LoginActivity.class);
            startActivity(login);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.i(TAG, "Setting screen name: " + name);
        analyticsTracker.setScreenName("Image~" + name);
        analyticsTracker.send(new HitBuilders.ScreenViewBuilder().build());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_refresh_login_data) {
            AccessToken.refreshCurrentAccessTokenAsync();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        accessTokenTracker.stopTracking();
        profileTracker.stopTracking();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }
}
