package si.krivec.tracker;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;

import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphUser;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;

import utils.Constants;
import utils.RoutesUtils;
import utils.WebUtils;


public class MainActivity extends FragmentActivity {

    private static final int SPLASH = 0;
    private static final int SELECTION = 1;
    private static final int SETTINGS = 2;
    private static final int FRAGMENT_COUNT = SETTINGS  +1;

    public static String USER_FB_ID;
    private String userName;
    private String userBirthday;

    private final String RESPONSE_OK = "OK";

    private Fragment[] fragments = new Fragment[FRAGMENT_COUNT];

    private MenuItem settings;

    private boolean isResumed = false;

    private UiLifecycleHelper uiHelper;
    private Session.StatusCallback callback =
            new Session.StatusCallback() {
                @Override
                public void call(Session session,
                                 SessionState state, Exception exception) {
                    onSessionStateChange(session, state, exception);
                }
            };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FragmentManager fm = getSupportFragmentManager();
        fragments[SPLASH] = fm.findFragmentById(R.id.splashFragment);
        fragments[SELECTION] = fm.findFragmentById(R.id.selectionFragment);
        fragments[SETTINGS] = fm.findFragmentById(R.id.userSettingsFragment);

        FragmentTransaction transaction = fm.beginTransaction();
        for(int i = 0; i < fragments.length; i++) {
            transaction.hide(fragments[i]);
        }
        transaction.commit();

        uiHelper = new UiLifecycleHelper(this, callback);
        uiHelper.onCreate(savedInstanceState);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // only add the menu when the selection fragment is showing
        if (fragments[SELECTION].isVisible()) {
            if (menu.size() == 0) {
                settings = menu.add(R.string.action_settings);
            }
            return true;
        } else {
            menu.clear();
            settings = null;
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.equals(settings)) {
            showFragment(SETTINGS, true);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        uiHelper.onResume();
        isResumed = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        uiHelper.onPause();
        isResumed = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        uiHelper.onDestroy();
    }

    private void onSessionStateChange(Session session, SessionState state, Exception exception) {
        if (session != null && session.isOpened()) {
            // Get the user's data.
            makeMeRequest(session);
        }

        // Only make changes if the activity is visible
        if (isResumed) {
            FragmentManager manager = getSupportFragmentManager();
            // Get the number of entries in the back stack
            int backStackSize = manager.getBackStackEntryCount();
            // Clear the back stack
            for (int i = 0; i < backStackSize; i++) {
                manager.popBackStack();
            }
            if (state.isOpened()) {
                // If the session state is open:
                // Show the authenticated fragment
                //showFragment(SELECTION, false);
                Intent intentSelection = new Intent(this, SelectionActivity.class);
                startActivity(intentSelection);
            } else if (state.isClosed()) {
                // If the session state is closed:
                // Show the login fragment
                showFragment(SPLASH, false);
            }
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        Session session = Session.getActiveSession();

        if (session != null && session.isOpened()) {
            // if the session is already open,
            // try to show the selection fragment
            //showFragment(SELECTION, false);
            Intent intentSelection = new Intent(this, SelectionActivity.class);
            startActivity(intentSelection);
        } else {
            // otherwise present the splash screen
            // and ask the person to login.
            showFragment(SPLASH, false);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        uiHelper.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        uiHelper.onSaveInstanceState(outState);
    }



    private void showFragment(int fragmentIndex, boolean addToBackStack) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        for (int i = 0; i < fragments.length; i++) {
            if (i == fragmentIndex) {
                transaction.show(fragments[i]);
            } else {
                transaction.hide(fragments[i]);
            }
        }
        if (addToBackStack) {
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    private void makeMeRequest(final Session session) {
        // Make an API call to get user data and define a
        // new callback to handle the response.
        Request request = Request.newMeRequest(session,
                new Request.GraphUserCallback() {
                    @Override
                    public void onCompleted(GraphUser user, Response response) {
                        // If the response is successful
                        if (session == Session.getActiveSession()) {
                            if (user != null) {
                                userName = user.getFirstName() + (user.getMiddleName() != null ? user.getMiddleName() + " " : " ") +  user.getLastName();
                                USER_FB_ID = user.getId();
                                userBirthday = user.getBirthday();

                                // Get user last route
                                JSONObject lastRoute = RoutesUtils.getLastRoute(MainActivity.USER_FB_ID);

                                if(lastRoute != null) {
                                    try {
                                        String success = lastRoute.getString("success");
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                                userSignUp();
                            }
                        }
                        if (response.getError() != null) {
                            // Handle errors, will do so later.
                        }
                    }
                });
       request.executeAsync();
       // TODO: Change to request.executeAndWait()
    }

    private void userSignUp() {
        new AsyncTask<String, Void, Intent>() {

            @Override
            protected Intent doInBackground(String... params) {
                if (USER_FB_ID != null && userName != null) {
                    String parameters = "userFbSignUp=true"
                            + "&username=" + userName
                            + "&idFacebook=" + USER_FB_ID
                            + "&birthday=" + userBirthday;

                    String response = WebUtils.executePost(Constants.BACKEND_URL + "/users", parameters);

                    if(response != null) {
                        try {
                            JSONObject jsonResponse = new JSONObject(response);

                            if (jsonResponse.getBoolean("success")) {
                                Log.d("MainActivity", "SUCCESS: " + jsonResponse.toString());
                            } else {
                                Log.d("MainActivity", "WARNING: " + jsonResponse.toString());
                            }
                        } catch (JSONException jex) {
                            Log.d("MainActivity", "ERROR: " + jex.getMessage());
                        }
                    }
                }


                return null;
            }
        }.execute();
    }
}
