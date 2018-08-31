package in.raji.billstack;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.api.services.drive.model.File;

import java.util.ArrayList;

public class ListActivity extends AppCompatActivity {
    GoogleApi _googleApi;

    RecyclerView recyclerView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        _googleApi = GoogleApi.getGoogleApi(this, this);
        recyclerView = findViewById(R.id.listViewResults);
        ProgressDialogUtility.showProgressDialog(this);
        _googleApi.loadBillStack(this);

    }


    public void publishToUI(ArrayList<File> files) {
        LinearLayoutManager mLayoutManager = new LinearLayoutManager(this);
        // recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));

        // specify an adapter (see also next example)
        MyAdapter mAdapter = new MyAdapter(this, files);
        recyclerView.setAdapter(mAdapter);
        ProgressDialogUtility.cancelProgressDialog();
    }


}
