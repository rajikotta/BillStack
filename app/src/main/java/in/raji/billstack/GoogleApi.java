package in.raji.billstack;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;


public class GoogleApi implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    static GoogleApi googleApi;
    private Context _context;
    private Handler _handler;
    private GoogleCredential _credential;
    private Drive _drive;

    private GoogleApiClient _googleApiClient;       // only set during login process
    private Activity _activity;              // launch intent for login (UI)

    // Saved to data store
    private boolean _loggedIn;
    private String _refreshToken;          // store, even if user is logged out as we may need to reuse

    FileListAndErrorMsg fileListAndErr;
    private static final String ClientID = "176724531262-h3g0lgmnq77isrhvo1e5cqkuafjd0g77.apps.googleusercontent.com"; // web client
    private static final String ClientSecret = "qRdrt-hFznqdaa8LTtVT2nx1"; // web client

    private class FileAndErrorMsg {
        public File file;
        public String errorMsg;

        public FileAndErrorMsg(File file_, String errorMsg_) {
            file = file_;
            errorMsg = errorMsg_;
        }
    }

    public void setActivity(Activity activity) {
        _activity = activity;
    }

    private class FileListAndErrorMsg {
        public ArrayList<File> fileList;
        public String errorMsg;

        public FileListAndErrorMsg(ArrayList<File> fileList_, String errorMsg_) {
            fileList = fileList_;
            errorMsg = errorMsg_;
        }
    }

    // -------------------
    // Constructor
    // -------------------

    public static GoogleApi getGoogleApi(Context context, Activity activity) {
        if (googleApi != null)
            return googleApi;
        else
            return googleApi = new GoogleApi(context, activity);
    }

    private GoogleApi(Context context, Activity activity) {

        _context = context;
        _handler = new Handler();
        loadFromPrefs();        //  loggedIn, refreshToken

        // create credential; will refresh itself automatically (in Drive calls) as long as valid refresh token exists
        HttpTransport transport = new NetHttpTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        _credential = new GoogleCredential.Builder()
                .setTransport(transport)
                .setJsonFactory(jsonFactory)
                .setClientSecrets(ClientID, ClientSecret)       // .addRefreshListener
                .build();
        _credential.setRefreshToken(_refreshToken);

        // Get app name from Manifest (for Drive builder)
        ApplicationInfo appInfo = context.getApplicationInfo();
        String appName = appInfo.labelRes == 0 ? appInfo.nonLocalizedLabel.toString() : context.getString(appInfo.labelRes);

        _drive = new Drive.Builder(transport, jsonFactory, _credential).setApplicationName(appName).build();
        startAuth(activity);
    }

    // -------------------
    // Auth
    // -------------------

    // https://developers.google.com/identity/sign-in/android/offline-access#before_you_begin
    // https://developers.google.com/identity/sign-in/android/offline-access#enable_server-side_api_access_for_your_app
    // https://android-developers.googleblog.com/2016/02/using-credentials-between-your-server.html
    // https://android-developers.googleblog.com/2016/05/improving-security-and-user-experience.html


    public boolean isLoggedIn() {
        return _loggedIn;
    }

    public void startAuth(Activity activity) {
        startAuth(activity, false);
    }

    public void startAuth(Activity activity, boolean forceRefreshToken) {

        _activity = activity;
        _loggedIn = false;
        saveToPrefs();


        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)

                .requestScopes(new Scope("https://www.googleapis.com/auth/drive"))
                .requestServerAuthCode(ClientID, forceRefreshToken)     // if force, guaranteed to get back refresh token, but will show "offline access?" if Google already issued refresh token
                .build();

        _googleApiClient = new GoogleApiClient.Builder(activity)

                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        _googleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        // Called soon after .connect()
        // This is only called when starting our Login process.  Sign Out first so select-account screen shown.  (OK if not already signed in)
        Auth.GoogleSignInApi.signOut(_googleApiClient).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                // Start sign in
                Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(_googleApiClient);

                _activity.startActivityForResult(signInIntent, 1001);
                ProgressDialogUtility.showProgressDialog(_activity);// Activity's onActivityResult will use the same code: 1
            }
        });
    }

    @Override
    public void onConnectionSuspended(int cause) {
        authDone("Connection suspended.");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        authDone("Connection failed.");
    }

    public void handleSignInResult(GoogleSignInResult result) {

        // Callback from Activity > onActivityResult
        if (result.isSuccess()) {
            GoogleSignInAccount acct = result.getSignInAccount();
            String authCode = acct.getServerAuthCode();

            new Thread(new ContinueAuthWithAuthCode_Background(authCode)).start();
        } else {
            authDone("Login canceled or unable to connect to Google.");    // can we get better error message?
            ProgressDialogUtility.cancelProgressDialog();
        }
    }

    private class ContinueAuthWithAuthCode_Background implements Runnable {

        String _authCode;

        public ContinueAuthWithAuthCode_Background(String authCode) {
            _authCode = authCode;
        }

        public void run() {

            // Convert authCode to tokens
            GoogleTokenResponse tokenResponse = null;
            String errorMsg = null;
            try {
                tokenResponse = new GoogleAuthorizationCodeTokenRequest(new NetHttpTransport(), JacksonFactory.getDefaultInstance(), "https://www.googleapis.com/oauth2/v4/token", ClientID, ClientSecret, _authCode, "").execute();
            } catch (IOException e) {
                errorMsg = e.getLocalizedMessage();
            }
            final GoogleTokenResponse tokenResponseFinal = tokenResponse;
            final String errorMsgFinal = errorMsg;

            _handler.post(new Runnable() {
                public void run() {
                    // Main thread
                    GoogleTokenResponse tokenResponse = tokenResponseFinal;
                    String errorMsg = errorMsgFinal;
                    ProgressDialogUtility.cancelProgressDialog();
                    if (tokenResponse != null && errorMsg == null) {
                        _credential.setFromTokenResponse(tokenResponse);    // this will keep old refresh token if no new one sent
                        _refreshToken = _credential.getRefreshToken();
                        _loggedIn = true;
                        saveToPrefs();
                        // FIXME: if our refresh token is bad and we're not getting a new one, how do we deal with this?
                        Log("New refresh token: " + tokenResponse.getRefreshToken());
                    } else if (errorMsg == null)
                        errorMsg = "Get token error.";   // shouldn't get here
                    authDone(errorMsg);
                }
            });
        }
    }

    private void authDone(String errorMsg) {
        // Disconnect (we only need googleApiClient for login process)
        if (_googleApiClient != null && _googleApiClient.isConnected())
            _googleApiClient.disconnect();
        _googleApiClient = null;
    }

    /*
    public void signOut() {
        Auth.GoogleSignInApi.signOut(_googleApiClient).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
            }
        });
    }

    public void revokeAccess() {
        // FIXME: I don't know yet, but this may revoke access for all android devices
        Auth.GoogleSignInApi.revokeAccess(_googleApiClient).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
            }
        });
    }
    */

    public void LogOut() {
        _loggedIn = false;
        saveToPrefs();      // don't clear refresh token as we may need again
    }


    // -------------------
    // API Calls
    // -------------------


    public void loadBillStack(Activity activity) {
        _activity = activity;
        new Thread(new LoadBillStackTask()).start();
    }

    private class LoadBillStackTask implements Runnable {
        public void run() {

            FileAndErrorMsg fileAndErr = getFolderFromName("BillStack", null);
            if (fileAndErr.errorMsg != null)
                Log("getFolderFromName error: " + fileAndErr.errorMsg);
            else {
                fileListAndErr = getFileListInFolder(fileAndErr.file);
                if (fileListAndErr.errorMsg != null)
                    Log("getFileListInFolder error: " + fileListAndErr.errorMsg);
                else {
                    Log("file count: " + fileListAndErr.fileList.size());
//                    for (File file : fileListAndErr.fileList) {
//                        Log(file.getName());
//                    }
                }
            }

            _handler.post(new Runnable() {
                public void run() {
                    ((ListActivity) _activity).publishToUI(fileListAndErr.fileList);

                }
            });
        }
    }

    public void downloadFile(String driveID) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                OutputStream outputStream = new ByteArrayOutputStream();
                try {
                    _drive.files().get(driveID)
                            .executeMediaAndDownloadTo(outputStream);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    private FileAndErrorMsg getFolderFromName(String folderName, File parent) {

        // parent can be null for top level
        // Working with folders: https://developers.google.com/drive/v3/web/folder

        File folder = null;
        folderName = folderName.replace("'", "\\'");    // escape '
        String q = String.format(Locale.US, "mimeType='application/vnd.google-apps.folder' and '%s' in parents and name='%s' and trashed=false", parent == null ? "root" : parent.getId(), folderName);
        String errorMsg = null;
        try {
            FileList result = _drive.files().list().setQ(q).setPageSize(1000)

                    .execute();
            int foundCount = 0;
            for (File file : result.getFiles()) {
                foundCount++;
                folder = file;
            }
            if (foundCount == 0) errorMsg = "Folder not found: " + folderName;
            else if (foundCount > 1)
                errorMsg = "More than one folder found with name (" + foundCount + "): " + folderName;
        } catch (IOException e) {
            errorMsg = e.getLocalizedMessage();
        }
        if (errorMsg != null) folder = null;
        return new FileAndErrorMsg(folder, errorMsg);
    }

    private FileListAndErrorMsg getFileListInFolder(File folder) {

        // folder can be null for top level; does not return subfolder names
        ArrayList<File> fileList = new ArrayList<File>();
        String q = String.format(Locale.US, "mimeType != 'application/vnd.google-apps.folder' and '%s' in parents and trashed=false", folder == null ? "root" : folder.getId());
        String errorMsg = null;
        try {
            String pageToken = null;
            do {
                FileList result = _drive.files().list().setQ(q)
                        .setPageSize(1000).setPageToken(pageToken)
                        .setFields("nextPageToken, files(name,thumbnailLink, webViewLink)").execute();
                Log.d("raji", result.getFiles().get(0).getWebContentLink() + result.getFiles().get(0).getWebViewLink());
                fileList.addAll(result.getFiles());
                pageToken = result.getNextPageToken();
            } while (pageToken != null);
        } catch (IOException e) {
            errorMsg = e.getLocalizedMessage();
        }
        if (errorMsg != null) fileList = null;
        return new FileListAndErrorMsg(fileList, errorMsg);
    }

    public void uploadFile() {
        new Thread(new FileUploadTask()).start();
    }


    class FileUploadTask implements Runnable {

        @Override
        public void run() {
            java.io.File tempFile = ((MainActivity) _activity).saveBitmap(getFileName());
            File fileMetadata = new File();
            fileMetadata.setName(getFileName());
            try {
                fileMetadata.setParents(Collections.singletonList(createFolder()));
            } catch (IOException e) {
                e.printStackTrace();
            }

            FileContent mediaContent = new FileContent("image/png", tempFile);
            File file = null;
            try {
                _activity.runOnUiThread(new Runnable() {
                    public void run() {
                        ProgressDialogUtility.showProgressDialog(_activity);
                    }
                });


                file = _drive.files().create(fileMetadata, mediaContent)
                        .setFields("id, parents")
                        .execute();

            } catch (IOException e) {
                Toast.makeText(_activity, _activity.getString(R.string.billAdded), Toast.LENGTH_SHORT).show();

                e.printStackTrace();
            }
            System.out.println("File ID: " + file.getId());
            _handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(_activity, _activity.getString(R.string.billAdded), Toast.LENGTH_SHORT).show();

                }
            });
            ProgressDialogUtility.cancelProgressDialog();
        }
    }

    private String createFolder() throws IOException {

        FileAndErrorMsg fileAndErr = getFolderFromName("BillStack", null);
        if (fileAndErr.errorMsg == null)

            return fileAndErr.file.getId();
        else {
            File fileMetadata = new File();
            fileMetadata.setName("BillStack");
            fileMetadata.setMimeType("application/vnd.google-apps.folder");

            File file = _drive.files().create(fileMetadata)
                    .setFields("id")
                    .execute();
            System.out.println("Folder ID: " + file.getId());
            return file.getId();
        }
    }

    private String getFileName() {
        Calendar c = Calendar.getInstance();
        System.out.println("Current time => " + c.getTime());

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String formattedDate = df.format(c.getTime());
        return formattedDate + ".png";

    }


    // -------------------
    // Misc
    // -------------------

    private void Log(String msg) {
        Log.v("ept", msg);
    }


    // -------------------
    // Load/Save Tokens
    // -------------------


    private void loadFromPrefs() {
        SharedPreferences pref = _context.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        _loggedIn = pref.getBoolean("GoogleLoggedIn", false);
        _refreshToken = pref.getString("GoogleRefreshToken", null);
    }

    private void saveToPrefs() {
        SharedPreferences.Editor editor = _context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit();
        editor.putBoolean("GoogleLoggedIn", _loggedIn);
        editor.putString("GoogleRefreshToken", _refreshToken);
        editor.apply();     // async

    }

}