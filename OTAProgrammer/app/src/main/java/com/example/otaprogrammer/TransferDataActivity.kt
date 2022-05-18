package com.example.otaprogrammer

import android.bluetooth.BluetoothDevice
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.otaprogrammer.Services.*

// this activity handles the connection to the device as well as
// the task of transferring and receiving data once the devices are connected
class TransferDataActivity : AppCompatActivity() {

    // state machine
    val STATE_NOT_CONNECTED = 0     // when our device is not connected to any other device
    val STATE_CONNECTING = 1    // when our device is establishing a connection to the remote device
    val STATE_CONNECTED_IDLE = 2    // when our device is connected to the remote device but is not exchanging any data
    val STATE_SENDING = 3   // when the devices are exchanging data over BT


    // various services and handlers
    var fileReader : FileReader? = null
    val handler : TransferHandler = TransferHandler()
    var btservice : BluetoothService? = null



    lateinit var textBox : TextView
    lateinit var btnReconnect : Button
    lateinit var btnOtaProgram : Button



    var device : BluetoothDevice? = null
    var kernelUri : Uri? = null
    var appUri : Uri? = null

    // variable to keep track of the state
    var state = STATE_NOT_CONNECTED



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transfer_data)

        //initializing various properties
        kernelUri = intent.getParcelableExtra(KERNEL_PATH)
        appUri = intent.getParcelableExtra(APPLICATION_PATH)

        textBox = findViewById(R.id.text_message)
        btnReconnect = findViewById(R.id.btn_reconnect)
        btnOtaProgram = findViewById(R.id.btn_otaprogram)

        // get the device to connect to from the intent
        device = intent.getParcelableExtra<BluetoothDevice>(DEVICE_TO_CONNECT)

        // get the service instance
        if(device != null){
            btservice = BluetoothService(this, handler, this.device!!)
            fileReader = FileReader(this, handler)
            btservice!!.connect()
        }
        else Log.d(TAG, "device passed to activity is null")


        // attach listener to button - ota program
        findViewById<Button>(R.id.btn_otaprogram).setOnClickListener(){
            otaProgram()
        }

        // attach listener to button reconnect
        findViewById<Button>(R.id.btn_reconnect).setOnClickListener(){
            reconnect()
        }
    }


    // reconnect to the remote device
    fun reconnect(){
        fileReader!!.stop()
        btservice!!.reconnect()
    }




    // this fun changes the UI according to the state
    fun onStateChange(){
        when(state) {
            STATE_NOT_CONNECTED -> {
                // user can try to reconnect when in this state
                // user cannot exchange data when in this state
                if(fileReader != null) fileReader!!.stop()
                textBox.text = "Not connected to the device"
                btnReconnect.isEnabled = true
                btnOtaProgram.isEnabled = false
            }
            STATE_CONNECTED_IDLE -> {
                // user cannot try to reconnect when in this state
                // user can exchange data when in this state
                if(textBox.text == "Connecting....") textBox.text = ""
                btnOtaProgram.isEnabled = true
                btnReconnect.isEnabled = false
                Toast.makeText(applicationContext, "Connected", Toast.LENGTH_SHORT).show()
            }
            STATE_CONNECTING-> {
                // user cannot try to reconnect when in this state
                // user cannot exchange data when in this state
                if(fileReader != null) fileReader!!.stop()
                textBox.text = "Connecting...."
                btnReconnect.isEnabled = false
                btnOtaProgram.isEnabled = false
            }
            STATE_SENDING -> {
                // user cannot try to reconnect when in this state
                // user cannot exchange data when in this state
                btnOtaProgram.isEnabled = false
                btnReconnect.isEnabled = false
            }
        }

    }



    //this fun uses the FileReader service to start the ReaderThread
    // look at the FileReader class for better understanding
    fun otaProgram(){
        fileReader!!.start(kernelUri!!, appUri!!, btservice!!)
    }

    // when activity destroys -> stop the bluetooth service and
    // thus all threads associated with it
    override fun onDestroy() {
        btservice!!.stop()
        super.onDestroy()
    }

    // fun to convert a byte array to space delimited pair of Hex values
    fun ByteArray.toHexString(): String = joinToString(separator = " ") { eachByte -> "%02x".format(eachByte) }

    // fun to append text to the textView automatically scroll the scrollView to the end
    fun appendAndScroll(text : String){
        textBox.append(text)
        textBox.append("\n")
        findViewById<ScrollView>(R.id.scroll).also { it.post{it.fullScroll(View.FOCUS_DOWN)} }
    }

    // handler for TransferDataActivity
    // this handler is used by other threads to communicate to the TransferDataActivity thread
    // read more at android docs -> Handler
    inner class TransferHandler  : Handler(Looper.getMainLooper()){
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                // message indicating that something was written over the BT channel to the remote device
                MESSAGE_WRITE -> {
                    val writeBuf = msg.obj as ByteArray

                    // construct a string from the buffer
                    val writeMessage = writeBuf.toHexString()

                    // append writeMessage to textView
                    appendAndScroll(writeMessage)
                }
                // message that something is received over the BT channel and is ready to be read from the buffer.
                MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray

                    // construct a string from the valid bytes in the buffer
                    // msg.arg1 is the number of bytes to be read
                    val text = String(readBuf, 0, msg.arg1)

                    // text formatting
                    val readMessage = "<font color= #00FF00>MC: $text</font>"

                    appendAndScroll(readMessage)
                }

                // message indicating the desire to display a toast
                MESSAGE_TOAST -> Toast.makeText(
                    applicationContext, msg.getData().getString(TOAST),
                    Toast.LENGTH_SHORT
                ).show()

                // message indicating state change
                // services send messages when their state changes
                // change the state of this activity accordingly
                MESSAGE_STATE_CHANGE ->{
                    when(msg.arg1){

                        BluetoothService.STATE_NOT_CONNECTED ->
                            state = STATE_NOT_CONNECTED
                        BluetoothService.STATE_CONNECTING ->
                            state = STATE_CONNECTING
                        BluetoothService.STATE_CONNECTED ->
                            state = STATE_CONNECTED_IDLE
                        FileReader.STATE_READING ->
                            state = STATE_SENDING
                        FileReader.STATE_IDLE ->{
                            when(btservice!!.getState()){
                                BluetoothService.STATE_CONNECTED -> state = STATE_CONNECTED_IDLE
                                BluetoothService.STATE_NOT_CONNECTED -> state = STATE_NOT_CONNECTED
                                BluetoothService.STATE_CONNECTING -> state = STATE_CONNECTING
                            }
                        }
                    }

                    // update the ui based on the new state
                    onStateChange()
                }
            }
        }
    }




}

