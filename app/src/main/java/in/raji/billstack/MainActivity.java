package in.raji.billstack;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Android Drive Quickstart activity. This activity takes a photo and saves it in Google Drive. The
 * user is prompted with a pre-made dialog which allows them to choose the file location.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "drive-quickstart";
    private static final int REQUEST_CODE_CAPTURE_IMAGE = 1;

    private Bitmap mBitmapToSave;


    GoogleApi _googleApi;

    @Override

    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!isTipscreenShown()) {
            // The user hasn't seen the OnboardingFragment yet, so show it
            startActivity(new Intent(this, TipScreenActivity.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isTipscreenShown() && _googleApi == null) {
            _googleApi = GoogleApi.getGoogleApi(this, this);


        } else if (_googleApi != null) {

            _googleApi.setActivity(MainActivity.this);

        }
    }

    private boolean isTipscreenShown() {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this);
        // Check if we need to display our OnboardingFragment
        return sharedPreferences.getBoolean(
                TipScreen.COMPLETED_ONBOARDING_PREF_NAME, false);
    }


    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 1001:
                GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
                _googleApi.handleSignInResult(result);

                break;
            case REQUEST_CODE_CAPTURE_IMAGE:
                Log.i(TAG, "capture image request code");
                // Called after a photo has been taken.
                if (resultCode == Activity.RESULT_OK) {
                    Log.i(TAG, "Image captured successfully.");
                    // Store the image data as a bitmap for writing later.
                    mBitmapToSave = (Bitmap) data.getExtras().get("data");
                    _googleApi.uploadFile();


                }
                break;

        }
    }


    public File saveBitmap(String fileName) {
        File cacheDir = getBaseContext().getCacheDir();
        File f = new File(cacheDir, fileName);

        try {
            FileOutputStream out = new FileOutputStream(
                    f);
            mBitmapToSave.compress(
                    Bitmap.CompressFormat.JPEG,
                    100, out);
            out.flush();
            out.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return f;
    }

    public void addNewBill(View view) {
        // Start camera.
//        startActivity(new Intent(this, ListActivity.class));
        if (_googleApi != null && !_googleApi.isLoggedIn()) {

            Toast.makeText(this, getString(R.string.pleaseSelectAccount), Toast.LENGTH_SHORT).show();
            _googleApi = GoogleApi.getGoogleApi(this, this);

        } else

            startActivityForResult(
                    new Intent(MediaStore.ACTION_IMAGE_CAPTURE), REQUEST_CODE_CAPTURE_IMAGE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (_googleApi != null && !_googleApi.isLoggedIn()) {
            Toast.makeText(this, getString(R.string.pleaseSelectAccount), Toast.LENGTH_SHORT).show();
            _googleApi = GoogleApi.getGoogleApi(this, this);
        } else
            startActivity(new Intent(this, ListActivity.class));
        return true;


    }


}