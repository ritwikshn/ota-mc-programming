package com.example.otaprogrammer.deviceListRecycler

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.otaprogrammer.ListDevicesActivity
import com.example.otaprogrammer.R



// this is the adapter for recylcer view
// read more about recycler view at android docs -> recycler view
// arg : context - context of application
// arg : deviceList - reference of the list that holds the devices to be displayed in the recycler view
class DeviceAdapter(val context: Context, val deviceList : MutableList<BluetoothDevice>): RecyclerView.Adapter<DeviceVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceVH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.device_item, parent, false)
        return DeviceVH(view)
    }

    // bind the device at a specific position to the view holder
    // arg : holder - the view holder object
    // arg : position - index in the device list
    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: DeviceVH, position: Int) {
        val device : BluetoothDevice =  deviceList[position]
        holder.textName.text = device.name

        // attach a listener to the holder
        holder.view.setOnClickListener{
            // when device is clicked call ListDevicesActivity.onDeviceClick and pass the device as an argument
            (context as ListDevicesActivity).onDeviceClick(device)
        }
    }

    // returns the number of devices in the list
    override fun getItemCount(): Int {
        return deviceList.size
    }


}