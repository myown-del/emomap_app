package com.example.emomap

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.emomap.databinding.ActivityMapPickerBinding
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapPickerActivity : BaseActivity() {

    private lateinit var binding: ActivityMapPickerBinding
    private lateinit var mapView: MapView
    private lateinit var mapController: IMapController
    private lateinit var locationManager: LocationManager
    private var selectedLocation: GeoPoint? = null
    private var locationMarker: Marker? = null
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        ) {
            moveToCurrentLocation()
        } else {
            Toast.makeText(this, getString(R.string.location_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        binding = ActivityMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        setupToolbar()
        setupMap()
        setupActions()
    }

    override fun setupToolbar() {
        setSupportActionBar(binding.topBar.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.topBar.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun setupMap() {
        mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapController = mapView.controller
        mapView.setBuiltInZoomControls(true)
        mapView.setMultiTouchControls(true)

        val initialLat = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN)
        val initialLon = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)
        val initialPoint = if (!initialLat.isNaN() && !initialLon.isNaN()) {
            GeoPoint(initialLat, initialLon)
        } else {
            GeoPoint(55.7558, 37.6176)
        }

        mapController.setCenter(initialPoint)
        mapController.setZoom(if (!initialLat.isNaN()) 15.0 else 12.0)

        if (!initialLat.isNaN() && !initialLon.isNaN()) {
            setSelectedLocation(initialPoint)
        }

        mapView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val pickedPoint = mapView.projection.fromPixels(
                    event.x.toInt(),
                    event.y.toInt()
                ) as GeoPoint
                setSelectedLocation(pickedPoint)
            }
            false
        }
    }

    private fun setupActions() {
        binding.btnSaveLocation.setOnClickListener {
            val point = selectedLocation ?: return@setOnClickListener
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra(EXTRA_LATITUDE, point.latitude)
                putExtra(EXTRA_LONGITUDE, point.longitude)
            })
            finish()
        }

        binding.btnCurrentLocation.setOnClickListener {
            moveToCurrentLocation()
        }
    }

    private fun setSelectedLocation(point: GeoPoint) {
        selectedLocation = point

        if (locationMarker == null) {
            locationMarker = Marker(mapView).apply {
                icon = resources.getDrawable(R.drawable.ic_location_picker_marker, theme)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                mapView.overlays.add(this)
            }
        }

        locationMarker?.position = point
        locationMarker?.title = getString(R.string.current_location)
        binding.btnSaveLocation.isEnabled = true
        mapView.invalidate()
    }

    private fun moveToCurrentLocation() {
        if (!hasLocationPermission()) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

        if (lastKnownLocation == null) {
            Toast.makeText(this, getString(R.string.current_location_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        val currentPoint = GeoPoint(lastKnownLocation.latitude, lastKnownLocation.longitude)
        mapController.setCenter(currentPoint)
        mapController.setZoom(16.0)
        setSelectedLocation(currentPoint)
    }

    private fun hasLocationPermission(): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return hasFineLocation || hasCoarseLocation
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    companion object {
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
    }
}
