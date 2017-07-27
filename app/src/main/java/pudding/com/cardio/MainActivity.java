package pudding.com.cardio;

import android.Manifest;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ViewFlipper;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;


public class MainActivity
        extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2
{
    public static String SHARED_PREFERENCE_FILE_NAME = "pudding.com.cardio.config";
    private static String STATE_LAYOUT = "main_activity_main_state";
    private static int LAYOUT_DISPLAY = 0;
    private static int LAYOUT_CONFIG = 1;
    private static int PERMISSION_CAMERA_REQUEST_CODE = 1;
    private static String LOG_TAG = "Cardio.MainActivity";

    //User Interface
    private int layout; //Current Layout
    private boolean layoutConfigFlag; //True - Camera View shown, False - Graph View Shown

    //Utility Objects
    private BlobLocator locator;
    private PeakFilter filter;
    private PaceMaker paceMaker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Load OpenCV
        System.loadLibrary(getString(R.string.library_opencv_name));

        //Request Camera Permission
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                MainActivity.PERMISSION_CAMERA_REQUEST_CODE);

        //Determine Layout
        this.layout = MainActivity.LAYOUT_DISPLAY;
        if(savedInstanceState != null)
            this.layout = savedInstanceState.getInt(MainActivity.STATE_LAYOUT);

        //Setup Utility Objects
        this.locator = new BlobLocator();
        this.filter = new PeakFilter();
        this.paceMaker = new PaceMaker();
        this.loadConfig();

        //Load UI
        setContentView(R.layout.activity_main);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == MainActivity.PERMISSION_CAMERA_REQUEST_CODE) {
            //Camera Permission Rejected
            if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                //Terminate due to Lack of Camera Permissions
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
                dialogBuilder.setMessage(R.string.dialog_camera_message);
                dialogBuilder.setCancelable(false); //Block Back Button
                dialogBuilder.setPositiveButton(R.string.dialog_camera_button_title,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                MainActivity.this.finish(); //Die Gracefully
                            }
                        });

                dialogBuilder.create().show();
            }
        }

        //Camera Permission Accepted
        this.setupLayout(this.layout);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(MainActivity.STATE_LAYOUT, this.layout);
    }

    //Menu Methods
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if(this.layout == MainActivity.LAYOUT_DISPLAY)
            getMenuInflater().inflate(R.menu.menu_display, menu);
        else if(this.layout == MainActivity.LAYOUT_CONFIG)
            getMenuInflater().inflate(R.menu.menu_config, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        if(item.getItemId() == R.id.menu_item_config)
        {
            this.setupLayout(MainActivity.LAYOUT_CONFIG);
            invalidateOptionsMenu();
        }
        else if(item.getItemId() == R.id.menu_item_toggle)
        {
            this.toggleConfigUI();
        }

        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();

        this.stopCameraView();
    }

    @Override
    protected void onResume() {
        super.onResume();

        this.setupCameraView();
    }

    //CV Methods
    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        //Locate LED
        Mat displayFrame = inputFrame.rgba();
        Mat frame = inputFrame.gray();
        Mat processFrame = this.locator.process(inputFrame.gray());

        boolean locationResult = this.locator.locate(processFrame);
        this.process(this.locator.getValue(processFrame));

        if(this.layout == MainActivity.LAYOUT_CONFIG)
        {
            if(locationResult == true)
                MatFragment.drawMarker(displayFrame, this.locator.getBlobLocation(),
                    new Size(this.locator.getBlobSize(), this.locator.getBlobSize()));

            this.writeBitmapFragment(displayFrame);

            return frame;
        }
        else //Display Layout
        {
        }
    }

    @Override
    public void onCameraViewStopped() {
    }

    private void setupCameraView()
    {
        CameraBridgeViewBase cameraView = (CameraBridgeViewBase) findViewById(R.id.view_cv_camera);

        if(cameraView != null)
        {
            cameraView.setCvCameraViewListener(this);
            cameraView.enableView();
        }
    }

    private void stopCameraView()
    {
        CameraBridgeViewBase cameraView =
                ((CameraBridgeViewBase)this.findViewById(R.id.view_cv_camera));
        if(cameraView != null) cameraView.disableView();
    }


    //UI Methods
    private void setupLayout(int layout)
    {
        this.stopCameraView();

        if(this.layout != layout) {
            ((ViewFlipper) findViewById(R.id.view_flipper)).showNext();
            this.layout = layout;
        }

        if(layout == MainActivity.LAYOUT_CONFIG)
        {
            this.setupCameraView();
            this.setupGraphFragment();
            this.setupConfigFragment();
            this.setupBitmapFragment();
        }
        else //Display Layout
        {
            this.setupCameraView();
            this.setupGraphFragment();
        }

    }

    //Bitmap Fragment
    private void setupBitmapFragment()
    {
        if(layout == MainActivity.LAYOUT_CONFIG)
        {
            MatFragment matFragment =
                    (MatFragment)getFragmentManager().
                            findFragmentById(R.id.frame_fragment_calibrate_bitmap);

            if(matFragment == null)
            {
                matFragment = MatFragment.newInstance();
                getFragmentManager().beginTransaction().add(R.id.frame_fragment_calibrate_bitmap,
                        matFragment).commit();
            }
        }
    }

    private void writeBitmapFragment(Mat mat)
    {
        MatFragment matFragment =
                (MatFragment)getFragmentManager().
                        findFragmentById(R.id.frame_fragment_calibrate_bitmap);

        if(matFragment != null)
        {
            matFragment.putMat(mat);
        }
    }

    //Graph Fragment
    private void setupGraphFragment()
    {
        if(layout == MainActivity.LAYOUT_CONFIG)
        {
            GraphFragment graphFragment =
                    (GraphFragment)getFragmentManager().
                            findFragmentById(R.id.frame_fragment_calibrate_graph);

            if(graphFragment == null)
            {
                graphFragment = GraphFragment.newInstance(null);
                getFragmentManager().beginTransaction().add(R.id.frame_fragment_calibrate_graph,
                        graphFragment).commit();
            }

            graphFragment.addGraph(getString(R.string.graph_signal_name),
                    ContextCompat.getColor(this, R.color.view_graph_color_signal),
                    ContextCompat.getColor(this, R.color.view_graph_color_signal));

            graphFragment.addGraph(getString(R.string.graph_mean_name),
                    ContextCompat.getColor(this, R.color.view_graph_color_mean),
                    ContextCompat.getColor(this, R.color.view_graph_color_mean));

            graphFragment.addGraph(getString(R.string.graph_standard_deviation_name),
                    ContextCompat.getColor(this, R.color.view_graph_color_standard_deviation),
                    ContextCompat.getColor(this, R.color.view_graph_color_standard_deviation));

            graphFragment.setOffset(new Point(0.0, 0.0)); //Reset Offset
            graphFragment.setGraphWidth(50);
        }
        else //Display Layout
        {
            GraphFragment graphFragment =
                    (GraphFragment)getFragmentManager().
                            findFragmentById(R.id.frame_fragment_data_display);

            if(graphFragment == null)
            {
                graphFragment = GraphFragment.newInstance(null);
                getFragmentManager().beginTransaction().add(R.id.frame_fragment_display_graph,
                        graphFragment).commit();
            }

            graphFragment.addGraph(getString(R.string.graph_beat_name),
                    ContextCompat.getColor(this, R.color.view_graph_color_beat),
                    ContextCompat.getColor(this, R.color.view_graph_color_beat));

            graphFragment.setOffset(new Point(0.0, 0.0)); //Reset Offset
            graphFragment.setGraphWidth(50);
        }
    }

    private void writeGraphFragment(double value, boolean peak, double mean,
                                    double standard_deviation)
    {
        if(this.layout == MainActivity.LAYOUT_CONFIG)
        {
            //Update Graph
            GraphFragment graphFragment = (GraphFragment)getFragmentManager().
                    findFragmentById(R.id.frame_fragment_calibrate_graph);
            if(graphFragment != null)
            {
                if(graphFragment.getOffset().x == 0.0) graphFragment.setOffset(
                        new Point(System.currentTimeMillis(), 0.0));

                //Add Data
                graphFragment.addPoint(getString(R.string.graph_signal_name),
                        new Point(System.currentTimeMillis(), value));

                graphFragment.addPoint(getString(R.string.graph_mean_name),
                        new Point(System.currentTimeMillis(), mean));

                graphFragment.addPoint(getString(R.string.graph_standard_deviation_name),
                        new Point(System.currentTimeMillis(), standard_deviation));
            }

        }
        else //Layout Display
        {
            //Update Graph
            GraphFragment graphFragment = (GraphFragment)getFragmentManager().
                    findFragmentById(R.id.frame_fragment_display_graph);
            if(graphFragment != null)
            {
                if(graphFragment.getOffset().x == 0.0) graphFragment.setOffset(
                        new Point(System.currentTimeMillis(), 0.0));
                //Add Data
                if(peak == true) graphFragment.addPoint(getString(R.string.graph_beat_name),
                        new Point(System.currentTimeMillis(), 1.0));
                else graphFragment.addPoint(getString(R.string.graph_beat_name),
                        new Point(System.currentTimeMillis(), 0.0));
            }
        }
    }

    //Config Fragment
    private void setupConfigFragment()
    {
        if(this.layout == MainActivity.LAYOUT_CONFIG) {
            ConfigFragment configFragment =
                    (ConfigFragment)
                            getFragmentManager().findFragmentById(R.id.frame_fragment_config);

            if (configFragment == null) {
                configFragment = ConfigFragment.newInstance();
                getFragmentManager().beginTransaction().
                        add(R.id.frame_fragment_config, configFragment).commit();
            }
        }
    }

    private void toggleConfigUI()
    {
        if(this.layoutConfigFlag == true)
        {
            //Mat shown currently
            View view = getFragmentManager().findFragmentById(R.id.frame_fragment_calibrate_graph).getView();
            if(view != null)
                getFragmentManager().findFragmentById(R.id.frame_fragment_calibrate_graph).getView().
                    setAlpha((float) 1.0);

            View bitmapView = getFragmentManager().findFragmentById(R.id.frame_fragment_calibrate_bitmap).getView();
            if(bitmapView != null)
                getFragmentManager().findFragmentById(R.id.frame_fragment_calibrate_bitmap).getView().
                        setAlpha((float) 0.0);
        }
        else
        {
            //Graph Shown currently
            View bitmapView = getFragmentManager().findFragmentById(R.id.frame_fragment_calibrate_bitmap).getView();
            if(bitmapView != null)
                bitmapView.setAlpha((float) 1.0);

            View graphView = getFragmentManager().findFragmentById(R.id.frame_fragment_calibrate_graph).getView();
            if(graphView != null)
                graphView.setAlpha((float) 0.0);
        }

        this.layoutConfigFlag = !this.layoutConfigFlag; //Toggle Flag
    }

    //Utility Methods
    private void process(double value)
    {
        //Process
        boolean peak = this.filter.determinePeak(value);
        if(peak == true) this.paceMaker.seed();
        double pace = this.paceMaker.pace();

        Log.d(LOG_TAG, "PACE: "  + pace + " bpm");
        //Update UI
        double adjustedStandardDeviation = this.filter.computeMean() +
                (this.filter.computeStandardDeviation() * Double.parseDouble(
                        getSharedPreferences(MainActivity.SHARED_PREFERENCE_FILE_NAME, 0).
                                getString("filter_threshold", "1.0")));

        this.writeGraphFragment(value, peak, this.filter.computeMean(), adjustedStandardDeviation);
    }

    public void loadConfig()
    {
        SharedPreferences preferences =
                getSharedPreferences(MainActivity.SHARED_PREFERENCE_FILE_NAME, 0);

        //General
        this.paceMaker.setSeedDuration(
                Integer.parseInt(
                        preferences.getString("pacemaker_duration", "2500")));

        this.paceMaker.setPeriod(
                Integer.parseInt(
                        preferences.getString("pacemaker_period", "60000")));

        this.paceMaker.setLogSize(
                Integer.parseInt(
                        preferences.getString("pacemaker_log", "3")));

        //Filter
        this.filter.setPeakThreshold(
                Double.parseDouble(
                        preferences.getString("filter_threshold", "1.0")));
        this.filter.setLogSize(
                Integer.parseInt(
                        preferences.getString("filter_log", "3")));

        //Computer Vision
        this.locator.setBlobColor(
                Double.parseDouble(
                        preferences.getString("blob_color", "255.0")));
        this.locator.setBlobMinArea(
                Double.parseDouble(
                        preferences.getString("blob_min_area", "100.0")));
        this.locator.setBlobMinCircularity(
                Double.parseDouble(
                        preferences.getString("blob_min_circularity", "0.8")));
        this.locator.setBlobMinConvexity(
                Double.parseDouble(
                        preferences.getString("blob_min_convexity", "0.7")));
        this.locator.setBlobMinInertia(
                Double.parseDouble(
                        preferences.getString("blob_min_inertia", "0.7")));
        this.locator.loadConfig();

    }
}
