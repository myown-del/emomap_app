package com.example.emomap

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.emomap.databinding.ActivityMapPickerBinding
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

class MapPickerActivity : BaseActivity() {

    private lateinit var binding: ActivityMapPickerBinding
    private lateinit var mapView: MapView
    private var maplibreMap: MapLibreMap? = null
    private lateinit var locationManager: LocationManager
    private var selectedLocation: LatLng? = null
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

        binding = ActivityMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        setupToolbar()
        setupMap(savedInstanceState)
        setupActions()
    }

    override fun setupToolbar() {
        setSupportActionBar(binding.topBar.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.topBar.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun setupMap(savedInstanceState: Bundle?) {
        mapView = binding.mapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            maplibreMap = map
            map.setStyle(Style.Builder().fromJson(MapConfig.DEFAULT_STYLE_JSON)) {
                val initialLat = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN)
                val initialLon = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)
                val initialPoint = if (!initialLat.isNaN() && !initialLon.isNaN()) {
                    LatLng(initialLat, initialLon)
                } else {
                    LatLng(55.7558, 37.6176)
                }

                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        initialPoint,
                        if (!initialLat.isNaN()) 15.0 else 12.0
                    )
                )

                if (!initialLat.isNaN() && !initialLon.isNaN()) {
                    setSelectedLocation(initialPoint)
                }

                map.addOnMapClickListener { point ->
                    setSelectedLocation(point)
                    true
                }
            }
        }
    }

    private fun setupActions() {
        binding.btnSaveLocation.setOnClickListener {
            val point = selectedLocation ?: return@setOnClickListener
            setResult(
                Activity.RESULT_OK, Intent().apply {
                    putExtra(EXTRA_LATITUDE, point.latitude)
                    putExtra(EXTRA_LONGITUDE, point.longitude)
                }
            )
            finish()
        }

        binding.btnCurrentLocation.setOnClickListener {
            moveToCurrentLocation()
        }
    }

    private fun setSelectedLocation(point: LatLng) {
        val map = maplibreMap ?: return
        selectedLocation = point
        locationMarker?.remove()
        val markerOptions = MarkerOptions()
            .position(point)
            .title(getString(R.string.current_location))

        MapMarkerIconFactory.fromDrawableRes(this, R.drawable.ic_location_picker_marker)
            ?.let { markerOptions.icon(it) }

        locationMarker = map.addMarker(markerOptions)
        binding.btnSaveLocation.isEnabled = true
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

        val currentPoint = LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude)
        maplibreMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentPoint, 16.0))
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

    override fun onStart() {
        super.onStart()
        if (::mapView.isInitialized) {
            mapView.onStart()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) {
            mapView.onResume()
        }
    }

    override fun onPause() {
        if (::mapView.isInitialized) {
            mapView.onPause()
        }
        super.onPause()
    }

    override fun onStop() {
        if (::mapView.isInitialized) {
            mapView.onStop()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (::mapView.isInitialized) {
            mapView.onDestroy()
        }
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::mapView.isInitialized) {
            mapView.onLowMemory()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::mapView.isInitialized) {
            mapView.onSaveInstanceState(outState)
        }
    }

    companion object {
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
    }
}

