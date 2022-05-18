package com.example.otaprogrammer.Services

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.util.Log
import com.example.otaprogrammer.R
import java.io.BufferedReader
import java.io.InputStreamReader

class FileReader(val context: Context, val handler : Handler){

    // state machine
    companion object{
        val STATE_READING = 10      // state when the file is being read
        val STATE_IDLE = 11     // state when the file reader is idle
    }


    // keep track of the state the service is in
    var mState = STATE_IDLE

    // reference to the ReaderThread
    var mReaderThread : ReaderThread? = null


    // start the process
    fun start(kernelUri : Uri, appUri: Uri, btservice: BluetoothService){

        // close already running threads
        if(mReaderThread != null ) {
            mReaderThread!!.cancel()
            mReaderThread = null
        }


        //spawn a new thread
        mReaderThread = ReaderThread(kernelUri, appUri, btservice)
        mReaderThread!!.start()

        setState(STATE_READING)
    }

    // set the state and notify the UI about the state change
    @Synchronized
    private fun setState(state : Int){
        mState = state
        handler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget()
    }

    // stop the service
    @Synchronized
    fun stop(){
        if(mReaderThread != null ) {
            mReaderThread!!.cancel()
            mReaderThread = null
        }
    }


    // thread that performs the file reading
    inner class ReaderThread(val kernelUri: Uri, val appUri : Uri, val btservice : BluetoothService) : Thread(){

        // add a custom method to String class
        // convert string that contain Hex data to the corresponding byte array representation
        fun String.HexStringToByteArray(): ByteArray {
            check(length % 2 == 0) { "Must have an even length" }

            return chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }

        // request the BluetoothService to write a byte array to the channel
        fun writeByteArray(bytes: ByteArray){
            btservice.writeByteArray(bytes)
        }

        // request the BluetoothService to write a single byte to the channel
        fun writeByte(byte : Int){
            btservice.writeByte(byte)
        }

        // read the data from the file chunk by chunk and send it to the BT connected  thread
        private fun writeFile(uri : Uri, contentResolver: ContentResolver) {
            val buffer = BufferedReader(InputStreamReader(contentResolver.openInputStream(uri)))
            var temp : Int
            var hex : String = ""
            var cur : Char

            while(!this.isInterrupted){
                temp = buffer.read()

                // ship the data every 5 bytes or when the end of file is reached
                if(temp == -1 || hex.length == 10){
                    // dump what in hex to write
                    writeByteArray(hex.HexStringToByteArray())
                    hex = ""
                    if(temp == -1) break
                }


                cur = temp.toChar()
                if(cur.isLetterOrDigit()){
                    hex += cur
                }
            }
        }


        // for debugging purposes
//        fun helper(str : String ) {
//            var temp = 0
//            var hex : String = ""
//            var cur : Char
//            while(temp <= str.length){
//
//                if(temp == str.length || hex.length == 2){
//                    writeByte(hex.toInt(16))
//                    hex = ""
//                    if (temp == str.length ) return
//                }
//
//                cur = str[temp]
//                if(cur.isLetterOrDigit()){
//                    hex += cur
//                }
//
//                temp++
//            }
//        }


        // the main business logic
        fun otaprogram(kernelUri: Uri, appUri: Uri, contentResolver: ContentResolver){


            // write Hex 41 for autobaud lock
            writeByteArray("41".HexStringToByteArray())
            Log.d("imp", "sent first 41")

//            writeByteArray("FE".HexStringToByteArray())
//            Log.d("imp", "sent first 41")
//            val kernelText = context.getString(R.string.rogues)
//
//            // write kernel file
            writeFile(kernelUri, contentResolver)
//            helper(kernelText)
            Log.d("imp", "sent kernel file")
//
//          sleep for 2sec
            sleep(2000)
            // write HExu 41 immediately after the kernel is sent
            writeByteArray("41".HexStringToByteArray())
            Log.d("imp", "sent second 41")
////
            //wait for 500 ms
            sleep(500)

            // write dfu instructions
            val instructions : String = "E41B0000000101001BE4"
            writeByteArray(instructions.HexStringToByteArray())
            Log.d("imp", "sent instructions")
//
//
            //  write app file immediately after the instructions
            writeFile(appUri, contentResolver)
            Log.d("imp", "sent app file")

            //write Hex 2D 23 times
            val end = "2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D"
            writeByteArray(end.HexStringToByteArray())
            Log.d("imp", "send 2D")



            setState(STATE_IDLE)

        }

        // thread will perform whatever is in this function
        override fun run(){
            otaprogram(kernelUri, appUri, context.contentResolver)
        }

        fun cancel(){
            interrupt()
        }
    }


}