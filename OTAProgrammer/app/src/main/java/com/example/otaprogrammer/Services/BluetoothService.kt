package com.example.otaprogrammer.Services

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import com.example.otaprogrammer.MY_UUID
import com.example.otaprogrammer.TAG
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*


const val MESSAGE_READ: Int = 0
const val MESSAGE_WRITE: Int = 1
const val MESSAGE_TOAST: Int = 2
const val MESSAGE_STATE_CHANGE = 3
const val TOAST = "toast"



class BluetoothService(val context : Context, val handler : Handler, val device : BluetoothDevice){

    private val bluetoothManager  : BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter : BluetoothAdapter = bluetoothManager.adapter



    var connectThread : ConnectThread? = null
    var connectedThread : ConnectedThread? = null


    // state machine
    companion object{
        val STATE_NOT_CONNECTED = 0     // when not connected to the remote device
        val STATE_CONNECTING = 1        // when establishing connection to the remote device
        val STATE_CONNECTED = 2         // when connected to the remote device
    }


    // keep track of the state
    private var state : Int = STATE_NOT_CONNECTED



    // synchronized says that only one thread can access this fun at a time
    // when  one thread uses it, it locks the fun so that others cant use this
    @Synchronized
    private fun setState(state: Int) {
        this.state = state
        // Give the new state to the Handler so the UI Activity can update
        handler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget()
    }

   // synchronized says that only one thread can access this fun at a time
    // when  one thread uses it, it locks the fun so that others cant use this
    @Synchronized
    fun getState(): Int {
        return state
    }


    // tear down the current connection and connect again
    fun reconnect(){
        this.stop()
        this.connect()
    }

    // connect to the remote device
    @Synchronized
    fun connect(){

        // Cancel any thread currently running a connection
        if (state == STATE_CONNECTING) {
            if (connectThread != null) { connectThread!!.cancel();connectThread = null;}
        }
        // Cancel any thread currently running a connection
        if (connectedThread != null) {connectedThread!!.cancel(); connectedThread = null;}

        // spawn  a thread
        connectThread = ConnectThread(device)
        connectThread!!.start()
        setState(STATE_CONNECTING)
    }

    // to be called when the connecting process is success
    // arg : socket - the BluetoothScoket object that is obtained when the connection succeeds
    @Synchronized
    fun connected(socket : BluetoothSocket){

        // Cancel the thread that completed the connection
        if (connectedThread != null) {connectedThread!!.cancel(); connectedThread = null;}
        // Cancel any thread currently running a connection
        if (connectedThread != null) {connectedThread!!.cancel(); connectedThread = null;}


        // assign a new thread to do the work
        connectedThread = ConnectedThread(socket)
        connectedThread!!.start()
        setState(STATE_CONNECTED)
    }

    // write a string to the remote device over the channel
    // arg : msg - string to be written
    fun writeString(msg : String){
        writeByteArray(msg.toByteArray())
    }

    // write a byte array to the remote device over the channel
    // arg : bytes - byte array to be written
    fun writeByteArray(bytes : ByteArray){
        if(bytes.isEmpty()) return

        var r : ConnectedThread

        // obtain a lock
        synchronized (this) {
            if (state != STATE_CONNECTED) return
            r = connectedThread!!
        }
        r.write(bytes)
    }

    // called when connection establishment fails
    // inform the user about the same
    private fun connectionFailed() {
        setState(STATE_NOT_CONNECTED)
        // Send a failure message back to the Activity
        val msg: Message = handler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(TOAST, "Unable to connect device")
        msg.setData(bundle)
        handler.sendMessage(msg)
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private fun connectionLost() {
        setState(STATE_NOT_CONNECTED)
        // Send a failure message back to the Activity
        val msg: Message = handler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(TOAST, "Device connection was lost")
        msg.setData(bundle)
        handler.sendMessage(msg)
    }

    // stop the threads
    @Synchronized
    fun stop(){
        if(connectThread != null ){ connectThread!!.cancel(); connectThread = null;}
        if(connectedThread != null) { connectedThread!!.cancel(); connectedThread = null; }
        setState(STATE_NOT_CONNECTED)
    }


    // write a single byte over the channel
    // arg : byte - byte to be written
    fun writeByte(byte : Int){
        connectedThread!!.writeByte(byte)
    }


    // thread to read/ write data over the channel
    inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {

        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        override fun run() {
            var numBytes: Int // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                // Read from the InputStream.
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    connectionLost()
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }

                // Send the obtained bytes to the UI activity.
                handler.obtainMessage(MESSAGE_READ, numBytes, -1, mmBuffer).sendToTarget()
            }
        }

        // Call this from the main activity to send data to the remote device.
        fun write(bytes: ByteArray) {
            try {

                mmOutStream.write(bytes)
                val writeMsg = handler.obtainMessage(MESSAGE_WRITE, bytes.size, -1, bytes)
                writeMsg.sendToTarget()
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)

                // Send a failure message back to the activity.
                val writeErrorMsg = handler.obtainMessage(MESSAGE_TOAST)
                val bundle = Bundle().apply {
                    putString(TOAST, "Couldn't send data to the other device")
                }
                writeErrorMsg.data = bundle
                handler.sendMessage(writeErrorMsg)
                return
            }

            // Share the sent message with the UI activity.
//            val writtenMsg = handler.obtainMessage(
//                MESSAGE_WRITE, -1, -1, mmBuffer)
//            writtenMsg.sendToTarget()
        }

        fun writeByte(byte : Int){
            try {
                mmOutStream.write(byte)
//                val writeMsg = handler.obtainMessage(MESSAGE_WRITE, bytes.size, -1, bytes)
//                writeMsg.sendToTarget()
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)

                // Send a failure message back to the activity.
                val writeErrorMsg = handler.obtainMessage(MESSAGE_TOAST)
                val bundle = Bundle().apply {
                    putString(TOAST, "Couldn't send data to the other device")
                }
                writeErrorMsg.data = bundle
                handler.sendMessage(writeErrorMsg)
                return
            }

            // Share the sent message with the UI activity.
//            val writtenMsg = handler.obtainMessage(
//                MESSAGE_WRITE, -1, -1, mmBuffer)
//            writtenMsg.sendToTarget()
        }

        // Call this method from the main activity to shut down the connection.
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }


    // thread that performs connection request to the remote device
    @SuppressLint("MissingPermission")
    inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
                device.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID))
        }

        override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery()
            Log.d(TAG, "Starting to connect to device")
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket?.connect()
            } catch (e: IOException) {
                connectionFailed()
                // Close the socket
                try {
                    mmSocket?.close()
                } catch (e2: IOException) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2)
                }
                // Start the service over to restart listening mode
//                this.start()
                return
            }

            // if connection success
            connected(mmSocket!!)
            Log.d(TAG, "Connected to the device")
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }



}