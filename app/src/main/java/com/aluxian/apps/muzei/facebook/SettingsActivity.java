package com.aluxian.apps.muzei.facebook;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphUser;
import com.facebook.widget.LoginButton;
import com.google.analytics.tracking.android.EasyTracker;
import com.google.analytics.tracking.android.GoogleAnalytics;
import com.squareup.picasso.Picasso;

import java.util.Arrays;

@SuppressWarnings("ConstantConditions")
public class SettingsActivity extends FragmentActivity implements PopupMenu.OnMenuItemClickListener {

    @SuppressWarnings("UnusedDeclaration")
    private static final String TAG = "SettingsActivity";

    /**
     * Intent actions
     */
    public static final String ACTION_FORCE_UPDATE = "action_force_update";
    public static final String ACTION_RESCHEDULE_UPDATE = "action_reschedule_update";

    /**
     * Intent filter action sent by LoginActivity when the user cancels the login process
     */
    public static final String ACTION_B_LOGIN_CANCELLED = "com.aluxian.apps.muzei.facebook_login_cancelled";

    /**
     * Shared preferences keys
     */
    public static final String PREF_ALBUMS = "pref_albums";
    public static final String PREF_SHUFFLE = "pref_shuffle";
    public static final String PREF_WIFI_ONLY = "pref_wifi_only";
    public static final String PREF_INTERVAL = "pref_interval";
    public static final String PREF_USER_ID = "user_id";
    public static final String PREF_USER_NAME = "user_name";
    public static final String PREF_USER_PROFILE_PICTURE = "user_profile_picture";
    public static final String PREF_USER_TIMESTAMP = "user_timestamp";

    /**
     * Default refresh interval in minutes
     */
    public static final int DEFAULT_INTERVAL = 3 * 60; // 3 hours

    /**
     * Default cached user data expiration in millis
     */
    public static final int DEFAULT_CACHE_EXP = 3 * 60 * 60 * 1000; // 3 hours

    /**
     * Required Facebook permissions
     */
    private static final String PERMISSION_USER_PHOTOS = "user_photos";
    private static final String PERMISSION_FRIENDS_PHOTOS = "friends_photos";

    /**
     * Shared preferences and editor
     */
    private SharedPreferences mSharedPrefs;
    private SharedPreferences.Editor mSharedPrefsEditor;

    /**
     * Facebook lifecycle
     */
    private UiLifecycleHelper mFacebookUiHelper;
    private Session mFacebookSession;
    private boolean mUserLoggedIn;
    private boolean mFirstStateChange;

    /**
     * Flag to keep track whether the user's profile picture and name have been loaded
     */
    private boolean mProfileInfoLoaded;

    /**
     * Toggleable preferences' states
     */
    private boolean mRefreshPrefToggle;
    private boolean mWifiOnlyPrefToggle;

    /**
     * ActionBar overflow menu
     */
    private PopupMenu mOverflowMenu;
    private View mOverflowButton;

    /**
     * UI elements
     */
    private ImageView mProfileImageView;
    private LoginButton mFacebookButton;
    private TextView mIntervalPrefToggle;
    private TextView mNameTextView;
    private View mLoadingIndicator;
    private View mContainerView;
    private View mPreferencesContainer;
    private View mIntervalPickerContainer;
    private View mRootView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        loadActionBar();

        // Google Analytics
        if (BuildConfig.DEBUG) {
            GoogleAnalytics.getInstance(this).setDryRun(true);
        }

        // Find views
        mProfileImageView = (ImageView) findViewById(R.id.img_profile);
        mFacebookButton = (LoginButton) findViewById(R.id.btn_auth);
        mNameTextView = (TextView) findViewById(R.id.txt_name);
        mLoadingIndicator = findViewById(R.id.loading);
        mContainerView = findViewById(R.id.container);
        mPreferencesContainer = findViewById(R.id.container_preferences);
        mIntervalPickerContainer = findViewById(R.id.container_interval);
        mRootView = findViewById(android.R.id.content);

        // Initialise Facebook fields
        mFacebookUiHelper = new UiLifecycleHelper(this, callback);
        mFacebookUiHelper.onCreate(savedInstanceState);
        mFacebookSession = Session.getActiveSession();

        if (mFacebookSession != null) {
            mUserLoggedIn = mFacebookSession.isOpened();
        }

        mFirstStateChange = true;
        mFacebookButton.setReadPermissions(Arrays.asList(PERMISSION_USER_PHOTOS, PERMISSION_FRIENDS_PHOTOS));

        // Initialise shared preferences
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPrefsEditor = mSharedPrefs.edit();

        // Try to load the user's name and profile picture from cache
        mProfileImageView.setBackgroundResource(R.drawable.transp_circle);
        long cacheExpirationTime = DEFAULT_CACHE_EXP + mSharedPrefs.getLong(PREF_USER_TIMESTAMP, 0);

        if (mUserLoggedIn && System.currentTimeMillis() < cacheExpirationTime) {
            mNameTextView.setText(mSharedPrefs.getString(PREF_USER_NAME, null));
            loadProfilePicture(mSharedPrefs.getString(PREF_USER_PROFILE_PICTURE, null));
            mProfileInfoLoaded = true;
        } else {
            mProfileImageView.setImageResource(R.drawable.profile);
        }

        // Set listener to get notified when the layout has been drawn
        mRootView.getViewTreeObserver().addOnGlobalLayoutListener(new RootOnGlobalLayoutListener());
    }

    /**
     * Update UI when the Facebook session state changes
     *
     * @param session Facebook session object
     * @param state   Facebook state enum
     */
    private void onSessionStateChange(Session session, SessionState state) {
        boolean loginStateSwitched = mUserLoggedIn != state.isOpened();
        mUserLoggedIn = state.isOpened();

        // Reload user's information
        if (mFirstStateChange || loginStateSwitched) {
            if (mUserLoggedIn) {
                if (!mProfileInfoLoaded) {
                    Request.newMeRequest(session, new FacebookMeRequestCallback()).executeAsync();
                }
                mOverflowButton.setVisibility(View.VISIBLE);
            } else {
                mOverflowButton.setVisibility(View.GONE);
            }
        }

        // Update UI by animating views according to the login state
        if (!mFirstStateChange && loginStateSwitched) {
            switchedState(true);
        }

        mFacebookSession = session;
        mFirstStateChange = false;
    }

    /**
     * Called when the Facebook session state changes from logged in to logged out or vice versa
     *
     * @param updateWallpaper if false, only the UI will be updated
     */
    private void switchedState(boolean updateWallpaper) {
        if (updateWallpaper) {
            startService(new Intent(this, ArtSource.class).setAction(ACTION_FORCE_UPDATE));
        }

        if (mUserLoggedIn) {
            switchedStateLoggedIn();
        } else {
            switchedStateLoggedOut();
        }
    }

    /**
     * When the user logs in: move mProfileImageView to the top, hide the Facebook button and show the preferences
     */
    private void switchedStateLoggedIn() {
        mProfileImageView.animate()
                .setInterpolator(new OvershootInterpolator(0.85f))
                .setStartDelay(200)
                .setDuration(500)
                .y(mProfileImageView.getHeight() * 1.15f);

        mLoadingIndicator.animate()
                .setInterpolator(new LinearInterpolator())
                .setStartDelay(0)
                .setDuration(100)
                .alpha(0);

        mContainerView.setVisibility(View.VISIBLE);
        mContainerView.animate()
                .setInterpolator(new LinearInterpolator())
                .setStartDelay(300)
                .setDuration(300)
                .alpha(1f);
    }

    /**
     * When the user logs in: move mProfileImageView to the center, show the Facebook button and hide the preferences
     */
    private void switchedStateLoggedOut() {
        mProfileImageView.animate()
                .setInterpolator(new OvershootInterpolator(0.85f))
                .setStartDelay(300)
                .setDuration(500)
                .y(mRootView.getHeight() / 2 - mProfileImageView.getHeight() * 0.9f);

        mFacebookButton.setVisibility(View.VISIBLE);
        mFacebookButton.animate()
                .setInterpolator(new LinearInterpolator())
                .setStartDelay(200)
                .setDuration(500)
                .alpha(1f);

        mContainerView.animate()
                .setInterpolator(new LinearInterpolator())
                .setStartDelay(0)
                .setDuration(200)
                .alpha(0)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mContainerView.setVisibility(View.GONE);
                    }
                });

        // Set mProfileImageView's image back to default
        if (mProfileInfoLoaded) {
            mProfileImageView.setImageResource(R.drawable.profile);
            mProfileInfoLoaded = false;
        }
    }

    /**
     * User has just clicked the Facebook login button, replace it with a loading indicator
     */
    private void switchedStateToOpening() {
        mFacebookButton.animate()
                .setInterpolator(new LinearInterpolator())
                .setStartDelay(0)
                .setDuration(100)
                .alpha(0)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mFacebookButton.setVisibility(View.GONE);
                    }
                });

        mLoadingIndicator.animate()
                .setInterpolator(new LinearInterpolator())
                .setStartDelay(0)
                .setDuration(100)
                .alpha(1f);
    }

    /**
     * This is called after the user tries to log in but cancels: replace the loading indicator with the login button
     */
    private void switchedStateOpeningFailed() {
        mFacebookButton.setVisibility(View.VISIBLE);
        mFacebookButton.animate()
                .setInterpolator(new LinearInterpolator())
                .setStartDelay(0)
                .setDuration(100)
                .alpha(1f);

        mLoadingIndicator.animate()
                .setInterpolator(new LinearInterpolator())
                .setStartDelay(0)
                .setDuration(100)
                .alpha(0);
    }

    /**
     * Set the custom ActionBar view and add listeners
     */
    @TargetApi(19)
    private void loadActionBar() {
        View actionBarView = getLayoutInflater().inflate(R.layout.actionbar, null);
        getActionBar().setCustomView(actionBarView);

        // Set up overflow menu
        mOverflowButton = actionBarView.findViewById(R.id.actionbar_overflow);
        mOverflowMenu = new PopupMenu(this, mOverflowButton);
        mOverflowMenu.inflate(R.menu.settings);
        mOverflowMenu.setOnMenuItemClickListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mOverflowButton.setOnTouchListener(mOverflowMenu.getDragToOpenListener());
        }

        // Done button click listener
        actionBarView.findViewById(R.id.actionbar_done).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mUserLoggedIn) {
                    mRootView.animate().alpha(0);
                }

                finish();
            }
        });

        // Overflow button click listener
        mOverflowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOverflowMenu.show();
            }
        });
    }

    /**
     * Set up the preferences
     */
    private void loadPreferences() {
        // Preference containers
        final View albumsPref = mPreferencesContainer.findViewById(R.id.pref_albums);
        final View shufflePref = mPreferencesContainer.findViewById(R.id.pref_shuffle);
        final View wifiOnlyPref = mPreferencesContainer.findViewById(R.id.pref_wifi_only);
        final View intervalPref = mPreferencesContainer.findViewById(R.id.pref_interval);

        // Preference toggles
        final View shuffleToggleOn = shufflePref.findViewById(R.id.pref_shuffle_toggle_on);
        final View shuffleToggleOff = shufflePref.findViewById(R.id.pref_shuffle_toggle_off);
        final View wifiOnlyToggleOn = wifiOnlyPref.findViewById(R.id.pref_wifi_only_toggle_on);
        final View wifiOnlyToggleOff = wifiOnlyPref.findViewById(R.id.pref_wifi_only_toggle_off);
        mIntervalPrefToggle = (TextView) intervalPref.findViewById(R.id.pref_interval_toggle);

        // Toggle positions
        // Y1 = Above (invisible)
        // Y2 = Default (visible)
        // Y3 = Below (invisible)
        final float shuffleToggleY2 = shuffleToggleOn.getY();
        final float shuffleToggleY1 = shuffleToggleY2 - shufflePref.getHeight();
        final float shuffleToggleY3 = shuffleToggleY2 + shufflePref.getHeight();
        final float wifiOnlyToggleY2 = wifiOnlyToggleOn.getY();
        final float wifiOnlyToggleY1 = wifiOnlyToggleY2 - wifiOnlyPref.getHeight();
        final float wifiOnlyToggleY3 = wifiOnlyToggleY2 + wifiOnlyPref.getHeight();

        // Load default preference states
        mRefreshPrefToggle = mSharedPrefs.getBoolean(PREF_SHUFFLE, true);
        mWifiOnlyPrefToggle = mSharedPrefs.getBoolean(PREF_WIFI_ONLY, true);

        if (mRefreshPrefToggle) {
            shuffleToggleOff.setY(shuffleToggleY3);
        } else {
            shuffleToggleOn.setY(shuffleToggleY1);
        }

        if (mWifiOnlyPrefToggle) {
            wifiOnlyToggleOff.setY(wifiOnlyToggleY3);
        } else {
            wifiOnlyToggleOn.setY(wifiOnlyToggleY1);
        }

        // Shuffle preference's click listener
        shufflePref.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mRefreshPrefToggle) {
                    mRefreshPrefToggle = false;
                    shuffleToggleOn.animate().y(shuffleToggleY1).alpha(0);
                    shuffleToggleOff.animate().y(shuffleToggleY2).alpha(1);
                } else {
                    mRefreshPrefToggle = true;
                    shuffleToggleOn.animate().y(shuffleToggleY2).alpha(1);
                    shuffleToggleOff.animate().y(shuffleToggleY3).alpha(0);
                }

                mSharedPrefsEditor.putBoolean(PREF_SHUFFLE, mRefreshPrefToggle).apply();
            }
        });

        // WiFi only preference's click listener
        wifiOnlyPref.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mWifiOnlyPrefToggle) {
                    mWifiOnlyPrefToggle = false;
                    wifiOnlyToggleOn.animate().y(wifiOnlyToggleY1).alpha(0);
                    wifiOnlyToggleOff.animate().y(wifiOnlyToggleY2).alpha(1);
                } else {
                    mWifiOnlyPrefToggle = true;
                    wifiOnlyToggleOn.animate().y(wifiOnlyToggleY2).alpha(1);
                    wifiOnlyToggleOff.animate().y(wifiOnlyToggleY3).alpha(0);
                }

                mSharedPrefsEditor.putBoolean(PREF_WIFI_ONLY, mWifiOnlyPrefToggle).apply();
            }
        });

        // Set saved interval and click listener
        mIntervalPrefToggle.setText(Utils.formatTime(mSharedPrefs.getInt(PREF_INTERVAL, DEFAULT_INTERVAL)));
        intervalPref.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPreferencesContainer.animate()
                        .setDuration(300)
                        .alpha(0)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                mPreferencesContainer.setVisibility(View.INVISIBLE);
                            }
                        });

                mIntervalPickerContainer.setVisibility(View.VISIBLE);
                mIntervalPickerContainer.animate()
                        .setDuration(300)
                        .alpha(1);
            }
        });

        // Set selected albums and click listener
        //albumsPref.setText(Utils.formatTime(mSharedPrefs.getInt(PREF_INTERVAL, DEFAULT_INTERVAL)));
        albumsPref.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        // Interval pickers
        NumberPicker daysPicker = (NumberPicker) mIntervalPickerContainer.findViewById(R.id.interval_picker_days);
        NumberPicker hoursPicker = (NumberPicker) mIntervalPickerContainer.findViewById(R.id.interval_picker_hours);
        NumberPicker minutesPicker = (NumberPicker) mIntervalPickerContainer.findViewById(R.id.interval_picker_minutes);

        daysPicker.setMaxValue(31);
        daysPicker.setMinValue(0);

        hoursPicker.setMaxValue(23);
        hoursPicker.setMinValue(0);

        minutesPicker.setMaxValue(59);
        minutesPicker.setMinValue(0);
    }

    /**
     * Layout listener to know when to start animations
     */
    private class RootOnGlobalLayoutListener implements ViewTreeObserver.OnGlobalLayoutListener {
        @Override
        public void onGlobalLayout() {
            mFacebookButton.setY(mRootView.getHeight() / 2 + mFacebookButton.getHeight() * 0.8f);
            mLoadingIndicator.setY(mFacebookButton.getY());

            if (mUserLoggedIn) {
                mProfileImageView.setY(mProfileImageView.getHeight() * 1.15f);

                // Fade in entire layout
                mContainerView.setAlpha(1f);
                mRootView.setAlpha(0);
                mRootView.animate().alpha(1f);
            } else {
                mProfileImageView.setY(mRootView.getHeight() / 2 - mProfileImageView.getHeight() / 2);

                // Initial scale-up animation
                mProfileImageView.setScaleX(0);
                mProfileImageView.setScaleY(0);
                mProfileImageView.animate()
                        .setInterpolator(new OvershootInterpolator())
                        .scaleX(1f)
                        .scaleY(1f)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                switchedState(false);
                            }
                        });
            }

            // Listen for clicks on the Facebook login button
            mFacebookButton.addAdditionalOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchedStateToOpening();
                }
            });

            loadPreferences();
            mRootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
    }

    /**
     * Facebook /me request callback
     */
    private class FacebookMeRequestCallback implements Request.GraphUserCallback {
        @Override
        public void onCompleted(GraphUser user, Response response) {
            if (user == null) {
                return;
            }

            // Load user data
            String formatUrl = "http://graph.facebook.com/%s/picture?width=%d&height=%2$d";
            String profilePictureUrl = String.format(formatUrl, user.getId(), 512);

            mNameTextView.setAlpha(0);
            mNameTextView.setText(user.getName());
            mNameTextView.animate().alpha(1);
            mProfileInfoLoaded = true;

            loadProfilePicture(profilePictureUrl);

            // Cache data
            mSharedPrefsEditor
                    .putString(PREF_USER_ID, user.getId())
                    .putString(PREF_USER_NAME, user.getName())
                    .putString(PREF_USER_PROFILE_PICTURE, profilePictureUrl)
                    .putLong(PREF_USER_TIMESTAMP, System.currentTimeMillis())
                    .apply();
        }
    }

    /**
     * Facebook session status change callback
     */
    private Session.StatusCallback callback = new Session.StatusCallback() {
        @Override
        public void call(Session session, SessionState state, Exception exception) {
            onSessionStateChange(session, state);
        }
    };

    /**
     * Broadcast receiver to know when the user cancelled the login process
     */
    private BroadcastReceiver mOnLoginCancelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switchedStateOpeningFailed();
        }
    };

    /**
     * Helper method to load the user's profile picture
     *
     * @param profilePictureUrl url to the profile picture
     */
    private void loadProfilePicture(String profilePictureUrl) {
        new Picasso.Builder(SettingsActivity.this)
                .listener(new Picasso.Listener() {
                    @Override
                    public void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception) {
                        mProfileInfoLoaded = false;
                    }
                })
                .build()
                .load(profilePictureUrl)
                .transform(new RoundTransformation(512))
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(mProfileImageView);
    }

    /*@Override
    public void onPickerDialogSet(int interval) {
        mSharedPrefsEditor.putInt(PREF_INTERVAL, interval).apply();
        mIntervalPrefToggle.setText(Utils.formatTime(interval));

        // Reschedule next update
        startService(new Intent(this, ArtSource.class).setAction(ACTION_RESCHEDULE_UPDATE));
    }*/

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_view_timeline:
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                String schemeUrl = "fb://profile/" + mSharedPrefs.getString(PREF_USER_ID, "");

                if (Utils.canOpenFacebookUrl(schemeUrl, this)) {
                    intent.setData(Uri.parse(schemeUrl));
                } else {
                    String pageUrl = "https://www.facebook.com/" + mSharedPrefs.getString(PREF_USER_ID, "");
                    intent.setData(Uri.parse(pageUrl));
                }

                startActivity(intent);
                break;
            case R.id.action_logout:
                mFacebookSession.closeAndClearTokenInformation();
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        EasyTracker.getInstance(this).activityStart(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // For scenarios where the main activity is launched and user
        // session is not null, the session state change notification
        // may not be triggered. Trigger it if it's open/closed.
        Session session = Session.getActiveSession();
        if (session != null && (session.isOpened() || session.isClosed())) {
            onSessionStateChange(session, session.getState());
        }

        mFacebookUiHelper.onResume();

        // Get notified when the user cancels the login process
        registerReceiver(mOnLoginCancelReceiver, new IntentFilter(ACTION_B_LOGIN_CANCELLED));
    }

    @Override
    public void onPause() {
        super.onPause();
        mFacebookUiHelper.onPause();

        // Pause listening for the login cancel intent
        unregisterReceiver(mOnLoginCancelReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        EasyTracker.getInstance(this).activityStop(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mFacebookUiHelper.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        // User is leaving the activity, fade out
        if (mUserLoggedIn) {
            mRootView.animate().alpha(0);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mFacebookUiHelper.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mFacebookUiHelper.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                if (mUserLoggedIn) {
                    mOverflowMenu.show();
                    return true;
                }
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

}
