package com.example.otaprogrammer.deviceListRecycler

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.otaprogrammer.R


// item view holder
// read more at android docs -> recycler view -> view holder

class DeviceVH(val view : View) : RecyclerView.ViewHolder(view) {

    // consists of a single textView for each item
    val textName : TextView = view.findViewById(R.id.text_name)
}