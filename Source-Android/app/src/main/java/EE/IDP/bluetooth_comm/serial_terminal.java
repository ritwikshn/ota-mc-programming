package EE.IDP.bluetooth_comm;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Arrays;
import java.util.Collections;
import java.nio.ByteBuffer;

public class serial_terminal extends Fragment implements ServiceConnection, serial_file {

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private serial_Serv service;

    private TextView receiveText;
    private TextView sendText;
    private TextView sendText1;
    private text_style.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = text_style.newline_crlf;
    private String null_char = "\0";


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), serial_Serv.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), serial_Serv.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), serial_Serv.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((serial_Serv.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);


        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
  /*  @Override
    private View screen(@NonNull LayoutInflater inflater, ViewGroup container){
      


        });

        return view1;
    }*/


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

//                View click;
//
//        if(connected == Connected.True) {
//                View view1 = inflater.inflate(R.layout.test, container, false);
//                View write_click = view1.findViewById(R.id.button2);
//                click = write_click;
//        }
//
//
//                    click.setOnClickListener(c -> {


                    View view = inflater.inflate(R.layout.serial_terminal, container, false);
                    receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
                    receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
                    receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

                    sendText = view.findViewById(R.id.send_text);
                    hexWatcher = new text_style.HexWatcher(sendText);
                    hexWatcher.enable(hexEnabled);
                    sendText.addTextChangedListener(hexWatcher);
                    sendText.setHint(hexEnabled ? "HEX mode" : "");


//        sendText1 = view.findViewById(R.id.send_text1);
//        hexWatcher = new TextUtil.HexWatcher(sendText1);
//        hexWatcher.enable(hexEnabled);
//        sendText1.addTextChangedListener(hexWatcher);
//        sendText1.setHint(hexEnabled ? "HEX mode" : "");


                    View sendBtn = view.findViewById(R.id.send_btn);
                    sendBtn.setOnClickListener(v -> {
                        send(sendText.getText().toString());
//            send(sendText1.getText().toString());
                    });
                    return view;
//                });
//
//                return view1;


   }



    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            sendText.setText("");
            hexWatcher.enable(hexEnabled);
            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            serial_open socket = new serial_open(getActivity().getApplicationContext(), device);
            service.connect(socket);

        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

// can use this to convert from float to byte array
//    public static byte[] floatToByteArray(float value) {
//        int intBits =  Float.floatToIntBits(value);
//        return new byte[] {
//                (byte) (intBits >> 24), (byte) (intBits >> 16), (byte) (intBits >> 8), (byte) (intBits) };
//    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {


            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                text_style.toHexString(sb, text_style.fromHexString(str));
                text_style.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = text_style.fromHexString(msg);
            } else {
                float f_msg;
                msg = str;
                f_msg= Float.parseFloat(msg);
                data = ByteBuffer.allocate(4).putFloat(f_msg).array();


                /// str -> written in the field
                // 1. convert str to float.
                // 2. convert this float to byte array and assign to data variable
                // data = floatToByteArray(float value)
              //  data = (str + null_char).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
            service.write(data);
        }


        catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {
        if(hexEnabled) {
            receiveText.append(text_style.toHexString(data) + '\n');
        } else {
<<<<<<< HEAD:Source-Android/app/src/main/java/EE/IDP/bluetooth_comm/serial_terminal.java

            float f_mgs;
            ByteBuffer buf = ByteBuffer.wrap(data);
            f_mgs= buf.getFloat();
            String msg = String.valueOf(f_mgs);



            if(newline.equals(text_style.newline_crlf) && msg.length() > 0) {
                msg = msg.replace(text_style.newline_crlf, text_style.newline_lf);




=======
            // cast byte array to float and display float value as string
            float f_mgs;
            Collections.reverse(Arrays.asList(data));
            ByteBuffer buf = ByteBuffer.wrap(data);
            f_mgs= buf.getFloat();
            String msg = String.valueOf(f_mgs);
//            String msg = new String(data);

            if(newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                // don't show CR as ^M if directly before LF
                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                // special handling if CR and LF come in separate fragments
>>>>>>> 4ea552ceb07f50be56a316e7a633d72148c87bf4:Source-Android/app/src/main/java/de/kai_morich/simple_bluetooth_terminal/TerminalFragment.java
                if (pendingNewline && msg.charAt(0) == '\n') {
                    Editable edt = receiveText.getEditableText();

                    if (edt != null && edt.length() > 1)
                        edt.replace(edt.length() - 2, edt.length(), "");
                }

                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }
            receiveText.append(text_style.toCaretString(msg, newline.length() != 0));
        }
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }


    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;

    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

}
