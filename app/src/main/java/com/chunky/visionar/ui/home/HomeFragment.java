package com.chunky.visionar.ui.home;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.chunky.visionar.BuildConfig;
import com.chunky.visionar.R;
import com.chunky.visionar.databinding.FragmentHomeBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.PhotoMetadata;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FetchPhotoRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Earth;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import org.w3c.dom.Text;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

public class HomeFragment extends Fragment implements OnMapReadyCallback {

    private FragmentHomeBinding binding;

    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    Button dialogButton;
    private boolean mUserRequestedInstall = true;

    private Session mSession;


    boolean blockRender = false;

    private boolean locationPermissionGranted;
    private final LatLng defaultLocation = new LatLng(-33.8523341, 151.2106085);

    private static final int DEFAULT_ZOOM = 15;

    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";
    private Location lastKnownLocation;

    private CameraPosition cameraPosition;

    private View view;

    private static final int M_MAX_ENTRIES = 5;

    private Context context;

    private GoogleMap map;

    private Earth globalEarth;

    private String[] likelyPlaceNames;
    private String[] likelyPlaceAddresses;
    private List[] likelyPlaceAttributions;
    private LatLng[] likelyPlaceLatLngs;
    private Anchor[] likelyPlaceAnchors;

    private ArFragment mapArFragment;

    private PlacesClient placesClient;

    private boolean reRender = true;

    private int currentPlaceIndex;
    private LatLng currentPlaceCoords;

    private FusedLocationProviderClient fusedLocationProviderClient;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        context = getContext();



        binding = FragmentHomeBinding.inflate(inflater, container, false);

        view = binding.getRoot();

        dialogButton = (Button) binding.getRoot().findViewById(R.id.button);

        mapArFragment = (ArFragment) getChildFragmentManager().findFragmentById(R.id.mapArFragment);


        dialogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCurrentPlace();
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);

        mapFragment.getMapAsync(this);

        if (savedInstanceState != null) {
            lastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            cameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        if(!Sceneform.isSupported(context)){
            return view;
        }

        initPlacesApi();

        initArSession();

        globalEarth = mSession.getEarth();



        return view;
    }


    public static double distance(double lat1, double lat2, double lon1,
                                  double lon2, double el1, double el2) {

        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        double height = el1 - el2;

        distance = Math.pow(distance, 2) + Math.pow(height, 2);

        return Math.sqrt(distance);
    }

    private void initArSession() {
        try {
            createSession();
        } catch (Exception e){
            Log.d("AR session error", "initArSession: "+e.getMessage());
        }
    }

    void initPlacesApi() {
        String apiKey = BuildConfig.PLACES_API_KEY;

        // Log an error if apiKey is not set.
        if (TextUtils.isEmpty(apiKey) || apiKey.equals("DEFAULT_API_KEY")) {
            Log.e("Places test", "No api key");
//            finish();
            return;
        }
        Places.initializeWithNewPlacesApiEnabled(getApplicationContext(), apiKey);

        placesClient = Places.createClient(getApplicationContext());
        fusedLocationProviderClient =  LocationServices.getFusedLocationProviderClient(this.getContext());
    }

    public void createSession() throws UnavailableDeviceNotCompatibleException, UnavailableSdkTooOldException, UnavailableArcoreNotInstalledException, UnavailableApkTooOldException {
        mSession = new Session(context);

        Config config = new Config(mSession);
        config.setCloudAnchorMode(Config.CloudAnchorMode.ENABLED);
        config.setGeospatialMode(Config.GeospatialMode.ENABLED);

        mSession.configure(config);
    }

    private Context getApplicationContext() {
        return this.getContext();
    }

    private void getLocationPermission() {

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
        } else {
            this.getActivity().requestPermissions(
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        locationPermissionGranted = false;
        if (requestCode
                == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true;
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
        updateLocationUI();
    }

    private void updateLocationUI() {
        if (map == null) {
            return;
        }
        try {
            if (locationPermissionGranted) {
                map.setMyLocationEnabled(true);
                map.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                map.setMyLocationEnabled(false);
                map.getUiSettings().setMyLocationButtonEnabled(false);
                lastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (locationPermissionGranted) {
                LocationManager manager =(LocationManager) context.getSystemService(Context.LOCATION_SERVICE);


                LocationListener listener = new LocationListener() {
                    @Override
                    public void onLocationChanged(@NonNull Location location) {
                        lastKnownLocation = location;
                        if (lastKnownLocation == null || likelyPlaceNames == null){
                            return;
                        }

                        LatLng userCoords = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());

                        TextView debugView = view.findViewById(R.id.latlng);

                        debugView.setText(userCoords.latitude+","+userCoords.longitude+"::"+lastKnownLocation.getLatitude()+","+lastKnownLocation.getLongitude());

//                        if (currentPlaceCoords != null && distance(userCoords.latitude, currentPlaceCoords.latitude,userCoords.longitude,currentPlaceCoords.longitude,0,0) == 0 ){
//                            return;
//                        }

                        if (blockRender){
                            return;
                        }

                        currentPlaceCoords = userCoords;
                        double leastDist = 1000.0;
                        int i = 0;
                        if (likelyPlaceLatLngs.length == 0){
                            return;
                        }
                        for(LatLng coord: likelyPlaceLatLngs){
                            double placeDistance = distance(userCoords.latitude, coord.latitude, userCoords.longitude,coord.longitude, 0.0,10.0);
                            if (placeDistance < leastDist){
                                leastDist = placeDistance;
                                currentPlaceIndex = i;
                            }
                            i++;
                        }
                        LatLng selectedPlaceCoords = new LatLng(likelyPlaceLatLngs[currentPlaceIndex].latitude, likelyPlaceLatLngs[currentPlaceIndex].longitude);
                        buildGeoAnchor(selectedPlaceCoords, likelyPlaceNames[currentPlaceIndex], likelyPlaceAnchors[currentPlaceIndex]);
                        blockRender = true;
                        Toast.makeText(context, "Updated location"+location.getLatitude()+"_"+location.getLongitude(), Toast.LENGTH_SHORT).show();
                    }
                };

                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10F, listener);
//                LocationCallback cb =  new LocationCallback() {
//                    @Override
//                    public void onLocationResult(LocationResult locationResult) {
//                        if (locationResult == null) {
//                            return;
//                        }
//                        for (Location location : locationResult.getLocations()) {
//                            // Update UI with location data
//                            // ...
//                            Toast.makeText(context, "Updated location"+location.getLatitude()+"_"+location.getLongitude(), Toast.LENGTH_SHORT).show();
//                            lastKnownLocation = location;
//                        }
//                    }
//                };
//
//                LocationRequest req = LocationRequest.create();
//                req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
//                Task<Void> locationResult = fusedLocationProviderClient.requestLocationUpdates(req,cb, Looper.getMainLooper());
//                locationResult.addOnCompleteListener(getActivity(), new OnCompleteListener<Location>() {
//                    @Override
//                    public void onComplete(@NonNull Task<Location> task) {
//                        if (task.isSuccessful()) {
//                            // Set the map's camera position to the current location of the device.
//                            lastKnownLocation = task.getResult();
//                            if (lastKnownLocation != null) {
//                                map.moveCamera(CameraUpdateFactory.newLatLngZoom(
//                                        new LatLng(lastKnownLocation.getLatitude(),
//                                                lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
//                            }
//                        } else {
////                            Log.d(, "Current location is null. Using defaults.");
////                            Log.e(TAG, "Exception: %s", task.getException());
//                            map.moveCamera(CameraUpdateFactory
//                                    .newLatLngZoom(defaultLocation, DEFAULT_ZOOM));
//                            map.getUiSettings().setMyLocationButtonEnabled(false);
//                        }
//                    }
//                });
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage(), e);
        }
    }

    public static boolean compareCoords(LatLng coord1, LatLng coord2){
        return coord1.latitude == coord2.longitude && coord1.longitude == coord2.longitude;
    }


    private void showCurrentPlace() {
        if (map == null) {
            return;
        }

        if (locationPermissionGranted) {
            // Use fields to define the data types to return.
            List<Place.Field> placeFields = Arrays.asList(Place.Field.NAME, Place.Field.ADDRESS,
                    Place.Field.LAT_LNG);

            // Use the builder to create a FindCurrentPlaceRequest.
            FindCurrentPlaceRequest request =
                    FindCurrentPlaceRequest.newInstance(placeFields);

            // Get the likely places - that is, the businesses and other points of interest that
            // are the best match for the device's current location.
            @SuppressWarnings("MissingPermission") final
            Task<FindCurrentPlaceResponse> placeResult =
                    placesClient.findCurrentPlace(request);
            placeResult.addOnCompleteListener (new OnCompleteListener<FindCurrentPlaceResponse>() {
                @Override
                public void onComplete(@NonNull Task<FindCurrentPlaceResponse> task) {
                    if (task.isSuccessful() && task.getResult() != null) {
                        FindCurrentPlaceResponse likelyPlaces = task.getResult();

                        // Set the count, handling cases where less than 5 entries are returned.
                        int count;
                        if (likelyPlaces.getPlaceLikelihoods().size() < M_MAX_ENTRIES) {
                            count = likelyPlaces.getPlaceLikelihoods().size();
                        } else {
                            count = M_MAX_ENTRIES;
                        }

//                        buildGeoAnchor(new LatLng(17.36343729032835, 78.56080977153654), "My Home

                        int i = 0;
                        likelyPlaceNames = new String[count];
                        likelyPlaceAddresses = new String[count];
                        likelyPlaceAttributions = new List[count];
                        likelyPlaceLatLngs = new LatLng[count];
                        likelyPlaceAnchors = new Anchor[count];

                        for (PlaceLikelihood placeLikelihood : likelyPlaces.getPlaceLikelihoods()) {
                            // Build a list of likely places to show the user.
                            LatLng coords = placeLikelihood.getPlace().getLatLng();
                            String placeName = placeLikelihood.getPlace().getName();
                            likelyPlaceNames[i] = placeName;
                            likelyPlaceAddresses[i] = placeLikelihood.getPlace().getAddress();
                            likelyPlaceAttributions[i] = placeLikelihood.getPlace()
                                    .getAttributions();
                            likelyPlaceLatLngs[i] = coords;
                            Anchor locationAnchor = globalEarth.createAnchor(coords.latitude, coords.longitude, 10.0, new float[]{0,0,0,0});
//                            mSession.hostCloudAnchor(locationAnchor);
                            likelyPlaceAnchors[i] = locationAnchor;
                            i++;
                            if (i > (count - 1)) {
                                break;
                            }
                        }

//                        mapArFragment.getArSceneView().getScene().addOnUpdateListener(new Scene.OnUpdateListener() {
//                            @Override
//                            public void onUpdate(FrameTime frameTime) {
//
//                            }
//                        });


                        // Show a dialog offering the user the list of likely places, and add a
                        // marker at the selected place.
                        openPlacesDialog();
                    }
                    else {
                        Log.e("error", "Exception: %s", task.getException());
                    }
                }
            });
        } else {
            // The user has not granted permission.
            Log.i("error", "The user did not grant location permission.");

            // Add a default marker, because the user hasn't selected a place.
            map.addMarker(new MarkerOptions()
                    .title("Default location selectted")
                    .position(defaultLocation)
                    .snippet("Some basic info"));

            // Prompt the user for permission.
            getLocationPermission();
        }
    }

    void buildGeoAnchor(LatLng coords, String placeName, Anchor locationAnchor){
        AnchorNode locationAnchorNode = new AnchorNode(locationAnchor);

        locationAnchorNode.setParent(mapArFragment.getArSceneView().getScene());

        TransformableNode model = new TransformableNode(mapArFragment.getTransformationSystem());

        model.setParent(locationAnchorNode);

        Node dialogNode = new Node();
        dialogNode.setParent(model);
        dialogNode.setEnabled(false);
        dialogNode.setLocalPosition(new Vector3(0,0,0));
        ViewRenderable.builder().setView(context, R.layout.tiger_card_view).build().thenAccept((render)->{
            View placeAnchorView  = render.getView();
            dialogNode.setRenderable(render);
            dialogNode.setEnabled(true);
            ((TextView)placeAnchorView.findViewById(R.id.placeNameTextView)).setText(placeName);
            Button okayButton = placeAnchorView.findViewById(R.id.okButton);
            okayButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    blockRender = false;
                    locationAnchorNode.setWorldPosition(new Vector3(0,100,0));
                    locationAnchorNode.setEnabled(false);
                }
            });
            ((TextView)placeAnchorView.findViewById(R.id.placeDescriptionTextView)).setText(likelyPlaceAddresses[currentPlaceIndex]);
            Toast.makeText(context, "Created anchor at "+ placeName, Toast.LENGTH_LONG).show();
        }).exceptionally((throwable)->{
            Toast.makeText(context, "Unable to place the anchors", Toast.LENGTH_LONG).show();
            throw new AssertionError("could not create anchor");
        });
    }
    private void openPlacesDialog() {
        // Ask the user to choose the place where they are now.
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // The "which" argument contains the position of the selected item.
                Log.d("Fetch Anchors", "onClick: "+mSession.getAllAnchors().toString());
                LatLng markerLatLng = likelyPlaceLatLngs[which];
                String markerSnippet = likelyPlaceAddresses[which];
                if (likelyPlaceAttributions[which] != null) {
                    markerSnippet = markerSnippet + "\n" + likelyPlaceAttributions[which];
                }

                // Add a marker for the selected place, with an info window
                // showing information about that place.
                map.addMarker(new MarkerOptions()
                        .title(likelyPlaceNames[which])
                        .position(markerLatLng)
                        .snippet(markerSnippet));

                // Position the map's camera at the location of the marker.
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(markerLatLng,
                        DEFAULT_ZOOM));
            }
        };

        // Display the dialog.
        AlertDialog dialog = new AlertDialog.Builder(this.context)
                .setTitle("Pick a place")
                .setItems(likelyPlaceNames, listener)
                .show();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (map != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, map.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, lastKnownLocation);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        mSession.close();
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            if (mSession == null) {
                switch (ArCoreApk.getInstance().requestInstall(getActivity(), mUserRequestedInstall)) {
                    case INSTALLED:
                        // Success: Safe to create the AR session.
                        mSession = new Session(context);
                        break;
                    case INSTALL_REQUESTED:
                        // When this method returns `INSTALL_REQUESTED`:
                        // 1. ARCore pauses this activity.
                        // 2. ARCore prompts the user to install or update Google Play
                        //    Services for AR (market://details?id=com.google.ar.core).
                        // 3. ARCore downloads the latest device profile data.
                        // 4. ARCore resumes this activity. The next invocation of
                        //    requestInstall() will either return `INSTALLED` or throw an
                        //    exception if the installation or update did not succeed.
                        mUserRequestedInstall = false;
                        return;
                }
            }
        } catch (UnavailableUserDeclinedInstallationException e) {
            // Display an appropriate message to the user and return gracefully.
            Toast.makeText(this.context, "TODO: handle exception " + e, Toast.LENGTH_LONG);
            return;
        } catch (Exception e) {
            return;  // mSession remains null, since session creation has failed.
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.map = googleMap;

        this.map.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            // Return null here, so that getInfoContents() is called next.
            public View getInfoWindow(Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                // Inflate the layouts for the info window, title and snippet.
                View infoWindow = getLayoutInflater().inflate(R.layout.custom_info_contents,
                        (FrameLayout) view.findViewById(R.id.map), false);

                TextView title = infoWindow.findViewById(R.id.title);
                title.setText(marker.getTitle());

                TextView snippet = infoWindow.findViewById(R.id.snippet);
                snippet.setText(marker.getSnippet());

                return infoWindow;
            }
        });

        updateLocationUI();

        getDeviceLocation();
    }
}