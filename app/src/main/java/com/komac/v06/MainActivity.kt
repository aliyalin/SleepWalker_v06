package com.komac.v06

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupMenu
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener

import com.google.android.gms.maps.model.PolylineOptions
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Response
import okhttp3.Call

import org.json.JSONObject
import java.io.IOException





class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private var mGoogleMap: GoogleMap? = null
    private var originMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var origin: LatLng? = null
    private var destination: LatLng? = null
    private val client = OkHttpClient()




    private lateinit var mapFragment: SupportMapFragment
    private lateinit var autocompleteFragment: AutocompleteSupportFragment


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        maker_cleaning()



        Places.initialize(applicationContext,getString(R.string.maps_api_key))
        autocompleteFragment = supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                as AutocompleteSupportFragment
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.ADDRESS, Place.Field.LAT_LNG))
        autocompleteFragment.setOnPlaceSelectedListener(object: PlaceSelectionListener {
            override fun onError(p0: Status){
                Toast.makeText(this@MainActivity, "Some Error in Search", Toast.LENGTH_SHORT).show()
            }
            override fun onPlaceSelected(place: Place){
                //val add = place.address
                //val id = place.id

                val latLng = place.latLng!!
                zoomOnMap(latLng)

                // Eğer başlangıç noktası henüz seçilmediyse, origin olarak belirle
                if (origin == null) {
                    origin = latLng
                    originMarker = mGoogleMap?.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("Başlangıç: ${place.address}")
                    )
                }
                else if (destination == null) {
                    destination = latLng
                    destinationMarker = mGoogleMap?.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("Bitiş: ${place.address}")
                    )

                    // İki nokta seçildiyse, rota çiz
//                    if (origin != null && destination != null) {
//                        drawRoute(origin!!, destination!!)
//                    }
                }

                // Haritayı seçilen noktaya yakınlaştır
                mGoogleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12f))
            }
        })
        val createRouteButton: Button = findViewById(R.id.create_route_button)
        createRouteButton.setOnClickListener {
            // Eğer hem başlangıç hem de bitiş noktası seçildiyse, rota oluştur

            if (origin != null && destination != null) {
                drawRoute(origin!!, destination!!)
            } else {
                Toast.makeText(this, "Başlangıç ve Bitiş noktalarını seçiniz", Toast.LENGTH_SHORT).show()
            }
        }
        val mapOptionButton:ImageButton=findViewById(R.id.mapOptionsMenu)
        val popupMenu= PopupMenu(this,mapOptionButton)
        popupMenu.menuInflater.inflate(R.menu.map_options,popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            changeMap(menuItem.itemId)
            true
        }
        mapOptionButton.setOnClickListener{
            popupMenu.show()
        }


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)


    }
    private fun drawRoute(origin: LatLng, destination: LatLng) {
        val apiKey = getString(R.string.maps_api_key) // API anahtarınızı alın
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${origin.latitude},${origin.longitude}" +
                "&destination=${destination.latitude},${destination.longitude}" +
                "&mode=driving&key=$apiKey"

        val request = Request.Builder().url(url).build()

        Toast.makeText(this, "draw içi", Toast.LENGTH_SHORT).show()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Rota alınamadı", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                responseData?.let {
                    val polyline = parseRoute(it)
                    runOnUiThread {
                        mGoogleMap?.addPolyline(PolylineOptions().addAll(polyline).width(10f))
                    }
                }
            }
        })
    }
    private fun parseRoute(jsonData: String): List<LatLng> {
        val jsonObject = JSONObject(jsonData)
        val routes = jsonObject.getJSONArray("routes")
        val polylinePoints = mutableListOf<LatLng>()

        if (routes.length() > 0) {
            val legs = routes.getJSONObject(0).getJSONArray("legs")
            val steps = legs.getJSONObject(0).getJSONArray("steps")

            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                val startLocation = step.getJSONObject("start_location")
                val endLocation = step.getJSONObject("end_location")

                val startLatLng = LatLng(startLocation.getDouble("lat"), startLocation.getDouble("lng"))
                val endLatLng = LatLng(endLocation.getDouble("lat"), endLocation.getDouble("lng"))

                polylinePoints.add(startLatLng)
                polylinePoints.add(endLatLng)
            }
        }
        if (polylinePoints.isEmpty()) {
            Log.e("Route Parsing", "No polyline data found!")
        }
        return polylinePoints
    }


    private fun maker_cleaning() {
        val myButton: Button = findViewById(R.id.clr_mkr)

        myButton.setOnClickListener {
            mGoogleMap?.clear()
        }
    }



    private fun zoomOnMap(latLng: LatLng) {
        val newLatLngZoom = CameraUpdateFactory.newLatLngZoom(latLng,12f) // 12f -> zoom level
        mGoogleMap?.animateCamera(newLatLngZoom)
    }
    private fun changeMap(itemId: Int) {
        when (itemId) {
            R.id.normal_map -> mGoogleMap?.mapType = GoogleMap.MAP_TYPE_NORMAL
            R.id.hybrid_map_map -> mGoogleMap?.mapType = GoogleMap.MAP_TYPE_HYBRID
            R.id.satellite_map -> mGoogleMap?.mapType = GoogleMap.MAP_TYPE_SATELLITE
            R.id.terrain_map -> mGoogleMap?.mapType = GoogleMap.MAP_TYPE_TERRAIN
        }

    }


    override fun onMapReady(googleMap: GoogleMap) {
        mGoogleMap = googleMap
    }
}