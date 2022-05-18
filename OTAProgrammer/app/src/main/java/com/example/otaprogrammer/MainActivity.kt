package com.example.otaprogrammer


import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

// tag for debugging
const val TAG = "abrakadabra"

// access the uri of the kernel file in intents using this key
const val KERNEL_PATH = "com.example.otaprogrammer.KERNEL_PATH"
// access the uri of the application file in intents using this key
const val APPLICATION_PATH = "com.example.otaprogrammer.APPLICATION_PATH"



// this activity is responsible for getting the kernel and the application file from the user
class MainActivity : AppCompatActivity() {

    // local variables to store the respective uris
    private var kernelUri: Uri? = null
    private var appUri : Uri? = null


    // intent to open file manager and select kernel file
    // content uri of the selected file will be passed to this callback when the user is done selecting the file.
    private val getKernelFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        // Handle the returned Uri
        if(uri == null){
            val text = "Please choose a file"
            val toast = Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT)
            toast.show()
        }else{
            //assign it to the local variable for now
            this.kernelUri = uri
            updateUI()
        }
    }

    // intent to open file manager and select application file
    // content uri of the selected file will be passed to this callback when the user is done selecting the file.
    private val getAppFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->

        // Handle the returned Uri
        if(uri == null){
            val text = "Please choose a file"
            val toast = Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT)
            toast.show()
        }else{
            //assign it to the local variable for now
            this.appUri = uri
            updateUI()
        }
    }


    // function to get name of the file as string from the content uri of the file.
    // input  - content uri of the file
    // returns - filename as a string
    private fun getFileName(uri : Uri): String {
        var fileName : String = "GENERIC FILENAME"
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        val cr: ContentResolver = applicationContext.contentResolver
        val metaCursor: Cursor? = cr.query(uri, projection, null, null, null)
        if (metaCursor != null) {
            try {
                if (metaCursor.moveToFirst()) {
                    fileName = metaCursor.getString(0)
                }
            } finally {
                metaCursor.close()
            }
        }
        return fileName
    }

    // this function updates the ui based on the state
    // state could be - only kernel file chosen, only application file chosen, both kernel and application file chosen.
    // if both files are chosen -> enable the next button
    private fun updateUI(){
        val nextBtn : Button = findViewById(R.id.button_next)
        val textKernelFilename : TextView = findViewById(R.id.text_kernel_filename)
        val textAppFilename : TextView = findViewById(R.id.text_app_filename)
        nextBtn.isEnabled = true

        if(kernelUri != null){
            textKernelFilename.text = getFileName(kernelUri!!)
        }else {
            textKernelFilename.text = "Please choose a file"
            nextBtn.isEnabled = false
        }

        if(appUri != null){
            textAppFilename.text = getFileName(appUri!!)
        }else{
            textAppFilename.text = "Please choose a file"
            nextBtn.isEnabled = false
        }
    }

    // if this function sounds unfamiliar -> read about activity lifecycles in android docs.
    // when saving the state, save the kernel and application file uri's if they are there in the bundle.
    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putParcelable(KERNEL_PATH, kernelUri)
        savedInstanceState.putParcelable(APPLICATION_PATH, appUri)
    }

    // if this function sounds unfamiliar -> read about activity lifecycles in android docs.
    // when restoring the state, extract the kernel and application file uri's if they are there in the bundle and assign to respective local variables
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        kernelUri = savedInstanceState.getParcelable(KERNEL_PATH)!!
        appUri = savedInstanceState.getParcelable(APPLICATION_PATH)!!
    }

    // if this function sounds unfamiliar -> read about activity lifecycles in android docs.
    // this is called when the activity is created
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate called")
        super.onCreate(savedInstanceState)


        // if there is some data in the saved state, restore it
        if(savedInstanceState != null){
            kernelUri = savedInstanceState.getParcelable(KERNEL_PATH)
            appUri = savedInstanceState.getParcelable(APPLICATION_PATH)
        }

        //inflate the ui
        setContentView(R.layout.activity_main)

        //update the ui based on this initial state
        updateUI()


        // set callbacks to the 'choose kernel' button
        // when button clicked -> launch the getKernelFile intent
        findViewById<Button>(R.id.button_kernel).setOnClickListener {
            getKernelFile.launch("text/plain")
        }


        // set callbacks to the 'choose application' button
        // when button clicked -> launch the getAppFile intent
        findViewById<Button>(R.id.button_app).setOnClickListener {
            getAppFile.launch("text/plain")
        }


        // set callbacks to the 'next' button
        // when button clicked -> launch the ListDevicesActivity
        findViewById<Button>(R.id.button_next).setOnClickListener{
            // define the intent
            val intent = Intent(this, ListDevicesActivity::class.java).apply {
                // pass the kernel and the application paths through the intent
                putExtra(KERNEL_PATH, kernelUri)
                putExtra(APPLICATION_PATH, appUri)
            }
            // launch the intent
            startActivity(intent)
        }
    }
}