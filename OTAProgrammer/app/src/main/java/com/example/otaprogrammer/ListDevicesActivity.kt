package com.example.otaprogrammer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
//import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.otaprogrammer.deviceListRecycler.DeviceAdapter

// access the device from the intent using this key
const val DEVICE_TO_CONNECT = "device_to_connect"

//uuid to connect to the HC-05 module
const val MY_UUID = "00001101-0000-1000-8000-00805f9b34fb"

// this activity lists the paired bluetooth devices on the screen for the user to select on eof the devices to connect to
// this activity also prompts the user to grant required permissions for the app to function properly and turns on the BT if it is not already
class ListDevicesActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.M)


    // local variables to store the kernel and application file uri
    private var kernelUri: Uri? = null
    private var appUri : Uri? = null


    // bluetooth adapter
    // if this is unfamiliar -> read more about bluetooth in android docs
    lateinit var bluetoothAdapter : BluetoothAdapter

    // list to store the devices paired with our device using bluetooth
    // initialized as empty list
    val pairedDevices : MutableList<BluetoothDevice> = arrayListOf()

    // adapter to the recycler view
    // more at android docs -> recycler view
    // pairedDevices is passed by reference
    val recyclerViewAdapter : DeviceAdapter = DeviceAdapter(this, pairedDevices)


    // intent to enable bluetooth on the device
    // result of action is passed as an argument in the callback
    private var requestBluetooth: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            //bluetooth on granted
            listPairedDevices()
        }else{
            //bluetooth on denied
            Toast.makeText(applicationContext, "bluetooth on denied", Toast.LENGTH_SHORT).show()
        }
    }


    // intent to ask for BT permissions then enable bluetooth then list paired devices
    // arg : permissions -
    private val requestPermissionTurnOnBT =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

            // check if all required permissions are granted
            var permissionGranted = true
            permissions.forEach{
                permissionGranted = it.value and permissionGranted
            }

            // some permissions not granted -
            if(!permissionGranted) permissionDenied()
            // all permissions granted but bluetooth is turned off
            else if(!bluetoothAdapter.isEnabled){
                // enable the bluetooth on the device
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                requestBluetooth.launch(enableBtIntent)
            // permissions are granted and bluetooth  is already on then go on to list the devices
            }else listPairedDevices()
        }


    // save the kernel and application uri to the state
    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putParcelable(KERNEL_PATH, kernelUri)
        savedInstanceState.putParcelable(APPLICATION_PATH, appUri)
    }

    // restore the kernel and application uri
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        kernelUri = savedInstanceState.getParcelable(KERNEL_PATH)!!
        appUri = savedInstanceState.getParcelable(APPLICATION_PATH)!!
    }


    // fun to list the paired devices
    //
    @SuppressLint("MissingPermission")
    private fun listPairedDevices(){
        // fetch the set from the bluetooth adapter
        val pairedDevicesSet: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices

        // sanity check
        if(pairedDevicesSet != null) {

            // clear the list
            pairedDevices.clear()

            // add all the devices to the list
            pairedDevices.addAll(pairedDevicesSet.toList())

            //update the recycler because the new data is added to the list
            recyclerViewAdapter.notifyDataSetChanged()
        }
    }

    // handle permission denied condition
    private fun permissionDenied(){
        Toast.makeText(applicationContext, "Please grant permissions first", Toast.LENGTH_SHORT).show()
    }


    // fun called on creation of the activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_devices)

        if(savedInstanceState != null) {
            kernelUri = savedInstanceState.getParcelable(KERNEL_PATH)
            appUri = savedInstanceState.getParcelable(APPLICATION_PATH)
        }

        // specific to the recycler view
        val recycler_device_list: RecyclerView = findViewById(R.id.recycler_device_list)
        recycler_device_list.layoutManager = LinearLayoutManager(this)
        recycler_device_list.adapter = recyclerViewAdapter

        //fetch uris from the intent
        kernelUri = intent.getParcelableExtra(KERNEL_PATH)!!
        appUri = intent.getParcelableExtra(APPLICATION_PATH)!!


        //sanity check to see whether device supports bt

        //device does not support bt
        if (getSystemService(BluetoothManager::class.java).adapter == null) {
            Toast.makeText(
                applicationContext,
                "Your device does not support Bluetooth",
                Toast.LENGTH_SHORT
            ).show()
        }
        // device supports BT
        else {
            // get the bluetooth adapter
            bluetoothAdapter = getSystemService(BluetoothManager::class.java).adapter

            // handle permissions according to the android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
                // launch the requestPermissionTurnOnBT intent
                requestPermissionTurnOnBT.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            }
            else {
                if (!bluetoothAdapter.isEnabled){
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                requestBluetooth.launch(enableBtIntent)
                }else listPairedDevices()
            }
        }
    }

    // this fun is called when user clicks on one of the paired devices
    // arg : device - the BluetoothDevice object corresponding to the device the user has clicked
    // launches the TransferDataActivity
    fun onDeviceClick(device: BluetoothDevice){
        val intent = Intent(this, TransferDataActivity::class.java).apply {
            //pass the device, kernel uri and application uri to the intent
            putExtra(DEVICE_TO_CONNECT, device)
            putExtra(KERNEL_PATH, kernelUri)
            putExtra(APPLICATION_PATH, appUri)
        }
        startActivity(intent)
    }


}