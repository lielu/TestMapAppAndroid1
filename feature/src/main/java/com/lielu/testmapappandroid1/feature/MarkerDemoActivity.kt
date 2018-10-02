/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lielu.testmapappandroid1.feature

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.media.Image
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes
import android.support.design.widget.TextInputEditText
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.res.ResourcesCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.app.AppCompatActivity
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.view.animation.BounceInterpolator
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobileconnectors.lambdainvoker.LambdaFunctionException
import com.amazonaws.mobileconnectors.lambdainvoker.LambdaInvokerFactory
import com.amazonaws.regions.Regions
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener
import com.google.android.gms.maps.GoogleMap.OnInfoWindowCloseListener
import com.google.android.gms.maps.GoogleMap.OnInfoWindowLongClickListener
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.location.*
import kotlinx.android.synthetic.main.activity_main.*
import java.util.ArrayList
import java.util.Random
import java.lang.SecurityException

/**
 * This shows how to place markers on a map.
 */
class MarkerDemoActivity :
        AppCompatActivity(),
        OnMarkerClickListener,
        OnInfoWindowClickListener,
        OnMarkerDragListener,
        OnInfoWindowLongClickListener,
        OnInfoWindowCloseListener,
        OnMapAndViewReadyListener.OnGlobalLayoutAndMapReadyListener {

    private val TAG = MarkerDemoActivity::class.java.name

    /** This is ok to be lateinit as it is initialised in onMapReady */
    private lateinit var map: GoogleMap

    /**
     * Keeps track of the last selected marker (though it may no longer be selected).  This is
     * useful for refreshing the info window.
     *
     * Must be nullable as it is null when no marker has been selected
     */
    private var lastSelectedMarker: Marker? = null

    private val markerRainbow = ArrayList<Marker>()

    /** map to store place names and locations */
    private val places = mapOf(
            "LYNNWOOD" to LatLng(47.827921,-122.3193735),
            "REDMOND" to LatLng(47.6721228,-122.1356408),
            "BELLEVUE" to LatLng(47.597808,-122.186),
            "SEATTLE" to LatLng(47.615, -122.343)
    )

    /** These can be lateinit as they are set in onCreate */
    private lateinit var topText: TextView
    private lateinit var rotationBar: SeekBar
    private lateinit var flatBox: CheckBox
    private lateinit var options: RadioGroup

    private val random = Random()

    /** Demonstrates customizing the info window and/or its contents.  */
    internal inner class CustomInfoWindowAdapter : InfoWindowAdapter {

        // These are both view groups containing an ImageView with id "badge" and two
        // TextViews with id "title" and "snippet".
        private val window: View = layoutInflater.inflate(R.layout.custom_info_window, null)
        private val contents: View = layoutInflater.inflate(R.layout.custom_info_contents, null)

        override fun getInfoWindow(marker: Marker): View? {
            if (options.checkedRadioButtonId != R.id.custom_info_window) {
                // This means that getInfoContents will be called.
                return null
            }
            render(marker, window)
            return window
        }

        override fun getInfoContents(marker: Marker): View? {
            if (options.checkedRadioButtonId != R.id.custom_info_contents) {
                // This means that the default info contents will be used.
                return null
            }
            render(marker, contents)
            return contents
        }

        private fun render(marker: Marker, view: View) {
            val badge = when (marker.title) {
                "Seattle" -> R.drawable.badge_qld
                "Bellevue" -> R.drawable.badge_sa
                "Redmond" -> R.drawable.badge_nsw
                "Lynnwood" -> R.drawable.badge_victoria
                in "Darwin Marker 1".."Darwin Marker 4" -> R.drawable.badge_nt
                else -> 0 // Passing 0 to setImageResource will clear the image view.
            }

            view.findViewById<ImageView>(R.id.badge).setImageResource(badge)

            // Set the title and snippet for the custom info window
            val title: String? = marker.title
            val titleUi = view.findViewById<TextView>(R.id.title)

            if (title != null) {
                // Spannable string allows us to edit the formatting of the text.
                titleUi.text = SpannableString(title).apply {
                    setSpan(ForegroundColorSpan(Color.RED), 0, length, 0)
                }
            } else {
                titleUi.text = ""
            }

            val snippet: String? = marker.snippet
            val snippetUi = view.findViewById<TextView>(R.id.snippet)
            if (snippet != null && snippet.length > 12) {
                snippetUi.text = SpannableString(snippet).apply {
                    setSpan(ForegroundColorSpan(Color.MAGENTA), 0, 10, 0)
                    setSpan(ForegroundColorSpan(Color.BLUE), 12, snippet.length, 0)
                }
            } else {
                snippetUi.text = ""
            }
        }
    }

    private var mLocationPermissionGranted = false

    private val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1

    private fun getLocationPermission() {
        /*
     * Request location permission, so that we can get the location of the
     * device. The result of the permission request is handled by a callback,
     * onRequestPermissionsResult.
     */
        if (ContextCompat.checkSelfPermission(this.applicationContext,
                        android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true
        } else {
            ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        mLocationPermissionGranted = false
        when (requestCode) {
            PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true
                }
            }
        }
        updateLocationUI()
    }

    private fun updateLocationUI() {
        try {
            if (mLocationPermissionGranted) {
                with(map) {
                    setMyLocationEnabled(true)
                    getUiSettings().setMyLocationButtonEnabled(true)
                }
            } else {
                with(map) {
                    setMyLocationEnabled(false)
                    getUiSettings().setMyLocationButtonEnabled(false)
                }
//                mLastKnownLocation = null
                getLocationPermission()
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message)
        }
    }

    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient

    private var mLatitude : Double = 0.0
    private var mLongtitude : Double = 0.0

    private fun getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (mLocationPermissionGranted) {
                mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
                mFusedLocationProviderClient.lastLocation
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful && task.result != null) {
                                mLatitude = task.result.latitude
                                mLongtitude = task.result.longitude
                            } else {
                                Log.w(TAG, "getLastLocation:exception", task.exception)
//                                showSnackbar(R.string.no_location_detected)
                            }
                        }
            }
        } catch(e : SecurityException)  {
            Log.e("Exception: %s", e.message);
    }
}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.marker_demo)

        topText = findViewById(R.id.top_text)

        rotationBar = findViewById<SeekBar>(R.id.rotationSeekBar).apply {
            max = 360
            setOnSeekBarChangeListener(object: OnSeekBarChangeListener {

                /** Called when the Rotation progress bar is moved */
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val rotation = seekBar?.progress?.toFloat()
                    checkReadyThen { markerRainbow.map { it.rotation = rotation ?: 0f } }
                }

                override fun onStartTrackingTouch(p0: SeekBar?) {
                    // do nothing
                }

                override fun onStopTrackingTouch(p0: SeekBar?) {
                    //do nothing
                }

            } )
        }

        flatBox = findViewById(R.id.flat)

        options = findViewById<RadioGroup>(R.id.custom_info_window_options).apply {
            setOnCheckedChangeListener { _, _ ->
                if (lastSelectedMarker?.isInfoWindowShown == true) {
                    // Refresh the info window when the info window's content has changed.
                    // must deal with the possibility that lastSelectedMarker has changed in
                    // another thread between the null check and this line, do this with !!
                    lastSelectedMarker?.showInfoWindow()
                }
            }
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        OnMapAndViewReadyListener(mapFragment, this)

        getLocationPermission()

        getDeviceLocation()
    }

    /**
     * This is the callback that is triggered when the GoogleMap has loaded and is ready for use
     */
    override fun onMapReady(googleMap: GoogleMap?) {

        // return early if the map was not initialised properly
        map = googleMap ?: return

        // create bounds that encompass every location we reference
        val boundsBuilder = LatLngBounds.Builder()
        // include all places we have markers for on the map
        places.keys.map { place -> boundsBuilder.include(places.getValue(place)) }
        val bounds = boundsBuilder.build()

        with(map) {
            // Hide the zoom controls as the button panel will cover it.
            uiSettings.isZoomControlsEnabled = false

            // Setting an info window adapter allows us to change the both the contents and
            // look of the info window.
            setInfoWindowAdapter(CustomInfoWindowAdapter())

            // Set listeners for marker events.  See the bottom of this class for their behavior.
            setOnMarkerClickListener(this@MarkerDemoActivity)
            setOnInfoWindowClickListener(this@MarkerDemoActivity)
            setOnMarkerDragListener(this@MarkerDemoActivity)
            setOnInfoWindowCloseListener(this@MarkerDemoActivity)
            setOnInfoWindowLongClickListener(this@MarkerDemoActivity)

            // Override the default content description on the view, for accessibility mode.
            // Ideally this string would be localised.
            setContentDescription("Map with lots of markers.")

            moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50))
        }

        // Add lots of markers to the googleMap.
        addMarkersToMap()

    }

    /**
     * Show all the specified markers on the map
     */
    private fun addMarkersToMap() {

        val placeDetailsMap = mutableMapOf(
                // Uses a coloured icon
                "SEATTLE" to PlaceDetails(
                        position = places.getValue("SEATTLE"),
                        title = "Seattle",
                        snippet = "Population: 2,074,200",
                        icon = BitmapDescriptorFactory
                                .defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                ),

                // Uses a custom icon with the info window popping out of the center of the icon.
                "BELLEVUE" to PlaceDetails(
                        position = places.getValue("BELLEVUE"),
                        title = "Bellevue",
                        snippet = "Population: 4,627,300",
                        icon = BitmapDescriptorFactory.fromResource(R.drawable.arrow),
                        infoWindowAnchorX = 0.5f,
                        infoWindowAnchorY = 0.5f
                ),

                // Will create a draggable marker. Long press to drag.
                "REDMOND" to PlaceDetails(
                        position = places.getValue("REDMOND"),
                        title = "Redmond",
                        snippet = "Population: 4,137,400",
                        draggable = true
                ),

                // Use a vector drawable resource as a marker icon.
                "LYNNWOOD" to PlaceDetails(
                        position = places.getValue("LYNNWOOD"),
                        title = "Lynnwood",
                        icon = vectorToBitmap(
                                R.drawable.ic_android, Color.parseColor("#A4C639"))
                )
        )

        // add 4 markers on top of each other in Darwin with varying z-indexes
//        (0 until 4).map {
//            placeDetailsMap.put(
//                "DARWIN ${it + 1}", PlaceDetails(
//                    position = places.getValue("DARWIN"),
//                    title = "Darwin Marker ${it + 1}",
//                    snippet = "z-index initially ${it + 1}",
//                    zIndex = it.toFloat()
//                )
//            )
//        }

        // place markers for each of the defined locations
        placeDetailsMap.keys.map {
            with(placeDetailsMap.getValue(it)) {
                map.addMarker(MarkerOptions()
                        .position(position)
                        .title(title)
                        .snippet(snippet)
                        .icon(icon)
                        .infoWindowAnchor(infoWindowAnchorX, infoWindowAnchorY)
                        .draggable(draggable)
                        .zIndex(zIndex))

            }
        }

        // Creates a marker rainbow demonstrating how to create default marker icons of different
        // hues (colors).
        val numMarkersInRainbow = 12
        (0 until numMarkersInRainbow).mapTo(markerRainbow) {
            map.addMarker(MarkerOptions().apply{
                position(LatLng(
                        -30 + 10 * Math.sin(it * Math.PI / (numMarkersInRainbow - 1)),
                        135 - 10 * Math.cos(it * Math.PI / (numMarkersInRainbow - 1))))
                title("Marker $it")
                icon(BitmapDescriptorFactory.defaultMarker((it * 360 / numMarkersInRainbow)
                        .toFloat()))
                flat(flatBox.isChecked)
                rotation(rotationBar.progress.toFloat())
            })
        }
    }

    /**
     * Demonstrates converting a [Drawable] to a [BitmapDescriptor],
     * for use as a marker icon.
     */
    private fun vectorToBitmap(@DrawableRes id : Int, @ColorInt color : Int): BitmapDescriptor {
        val vectorDrawable: Drawable? = ResourcesCompat.getDrawable(resources, id, null)
        if (vectorDrawable == null) {
            Log.e(TAG, "Resource not found")
            return BitmapDescriptorFactory.defaultMarker()
        }
        val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth,
                vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        DrawableCompat.setTint(vectorDrawable, color)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    /** Called when the Clear button is clicked.  */
    @Suppress("UNUSED_PARAMETER")
    fun onClearMap(view: View) {
        checkReadyThen { map.clear() }
    }

    /** Called when the Reset button is clicked.  */
    @Suppress("UNUSED_PARAMETER")
    fun onResetMap(view: View) {
        checkReadyThen {
            map.clear()
            addMarkersToMap()
        }
    }

    /** Called when the Flat check box is checked or unchecked */
    @Suppress("UNUSED_PARAMETER")
    fun onToggleFlat(view: View) {
        checkReadyThen { markerRainbow.map { marker -> marker.isFlat = flatBox.isChecked } }
    }

    private fun submitComment() {
        val commentString = this.findViewById<TextInputEditText>(R.id.comment).text
        val latitude = lastSelectedMarker?.position?.latitude
        val longitude = lastSelectedMarker?.position?.longitude

        // Create an instance of CognitoCachingCredentialsProvider
        val cognitoProvider = CognitoCachingCredentialsProvider(
                this.applicationContext, "us-west-2:40c48e69-03f3-4673-9732-e93be96d17b3", Regions.US_WEST_2)

        // Create LambdaInvokerFactory, to be used to instantiate the Lambda proxy.
        val factory = LambdaInvokerFactory(this.applicationContext,
                Regions.US_WEST_2, cognitoProvider)

        // Create the Lambda proxy object with a default Json data binder.
        // You can provide your own data binder by implementing
        // LambdaDataBinder.
        val addCommentInterface = factory.build(TestAddCommentInterface::class.java)

//        val request = AddCommentRequestClass(latitude.toString(), longitude.toString(), commentString.toString())
        val request = AddCommentRequestClass(mLatitude.toString(), mLongtitude.toString(), commentString.toString())

// The Lambda function invocation results in a network call.
// Make sure it is not called from the main thread.
        object : AsyncTask<AddCommentRequestClass, Void, ResponseClass>() {
            protected override fun doInBackground(vararg params: AddCommentRequestClass): ResponseClass? {
                // invoke "echo" method. In case it fails, it will throw a
                // LambdaFunctionException.
                try {
                    return addCommentInterface.AddCommentFunction(params[0])
                } catch (lfe: LambdaFunctionException) {
                    Log.e("Tag", "Failed to invoke echo", lfe)
                    return null
                }

            }

            protected override fun onPostExecute(result: ResponseClass?) {
                if (result == null) {
                    return
                }

                // Do a toast
                Toast.makeText(this@MarkerDemoActivity, result.greetings, Toast.LENGTH_LONG).show()
                //message.setText(result.greetings)
            }
        }.execute(request)

    }

    fun onCommentSubmit(view: View) {
        submitComment()
    }

    //
    // Marker related listeners.
    //
    override fun onMarkerClick(marker : Marker): Boolean {

        // Markers have a z-index that is settable and gettable.
        marker.zIndex += 1.0f
        Toast.makeText(this, "${marker.title} z-index set to ${marker.zIndex}",
                Toast.LENGTH_SHORT).show()

        lastSelectedMarker = marker

        if (marker.position == places.getValue("SEATTLE")) {
            // This causes the marker at Perth to bounce into position when it is clicked.
            val handler = Handler()
            val start = SystemClock.uptimeMillis()
            val duration = 1500

            val interpolator = BounceInterpolator()

            handler.post(object : Runnable {
                override fun run() {
                    val elapsed = SystemClock.uptimeMillis() - start
                    val t = Math.max(
                            1 - interpolator.getInterpolation(elapsed.toFloat() / duration), 0f)
                    marker.setAnchor(0.5f, 1.0f + 2 * t)

                    // Post again 16ms later.
                    if (t > 0.0) {
                        handler.postDelayed(this, 16)
                    }
                }
            })
        } else if (marker.position == places.getValue("BELLEVUE")) {
            // This causes the marker at Adelaide to change color and alpha.
            marker.apply {
                setIcon(BitmapDescriptorFactory.defaultMarker(random.nextFloat() * 360))
                alpha = random.nextFloat()
            }
        }

        // We return false to indicate that we have not consumed the event and that we wish
        // for the default behavior to occur (which is for the camera to move such that the
        // marker is centered and for the marker's info window to open, if it has one).
        return false
    }

    override fun onInfoWindowClick(marker : Marker) {
        Toast.makeText(this, "Click Info Window", Toast.LENGTH_SHORT).show()
    }

    override fun onInfoWindowClose(marker : Marker) {
        Toast.makeText(this, "Close Info Window", Toast.LENGTH_SHORT).show()
    }

    override fun onInfoWindowLongClick(marker : Marker) {
        Toast.makeText(this, "Info Window long click", Toast.LENGTH_SHORT).show()
    }

    override fun onMarkerDragStart(marker : Marker) {
        topText.text = getString(R.string.on_marker_drag_start)
    }

    override fun onMarkerDragEnd(marker : Marker) {
        topText.text = getString(R.string.on_marker_drag_end)
    }

    override fun onMarkerDrag(marker : Marker) {
        topText.text = getString(R.string.on_marker_drag, marker.position.latitude, marker.position.longitude)
    }

    /**
     * Checks if the map is ready, the executes the provided lambda function
     *
     * @param stuffToDo the code to be executed if the map is ready
     */
    private fun checkReadyThen(stuffToDo : () -> Unit) {
        if (!::map.isInitialized) {
            Toast.makeText(this, R.string.map_not_ready, Toast.LENGTH_SHORT).show()
        } else {
            stuffToDo()
        }
    }
}

/**
 * This stores the details of a place that used to draw a marker
 */
class PlaceDetails(
        val position: LatLng,
        val title: String = "Marker",
        val snippet: String? = null,
        val icon: BitmapDescriptor = BitmapDescriptorFactory.defaultMarker(),
        val infoWindowAnchorX: Float = 0.5F,
        val infoWindowAnchorY: Float = 0F,
        val draggable: Boolean = false,
        val zIndex: Float = 0F)