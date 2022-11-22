package com.example.shareall;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import android.graphics.PorterDuff;
import android.media.AudioAttributes;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.BassBoost;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    TextView tvStatus;
    Button btnWifiState;
    Button btnStart;
    Button btnStop;
    Button btnDiscover;
    EditText etMessage;
    ListView listView;

    WifiP2pManager wifiP2pManager;
    WifiP2pManager.Channel channel;
    BroadcastReceiver receiver;
    IntentFilter intentFilter;

    List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    String[] devicesNames;
    WifiP2pDevice[] deviceArray;
    Set<Integer> st;
    List<InetAddress> allListeners = new ArrayList<InetAddress>();
    WifiManager wifi;
    String hostAddress;

    boolean isHost;

    private Button startButton, stopButton;

    public byte[] buffer;
//    public DatagramSocket socket;
   private boolean status = true;
    int PORT = 8080;


    //Audio
    private Button mOn;
    private boolean isOn;
    private boolean isRecording;
    private AudioRecord record;
    private AudioTrack player;
    private AudioManager manager;
    private int recordState, playerState;
    private int minBuffer;
//    MulticastSocket serverSocket;
//    StartStreaming startStrem;

    //Audio Settings
    private final int source = MediaRecorder.AudioSource.CAMCORDER;
    private final int channel_in = AudioFormat.CHANNEL_IN_MONO;
    private final int channel_out = AudioFormat.CHANNEL_OUT_MONO;
    private final int format = AudioFormat.ENCODING_PCM_16BIT;
    int dev;
//    CustServer cstServer;

    private final static int REQUEST_ENABLE_BT = 1;
    private boolean IS_HEADPHONE_AVAILBLE = false;
    Socket socket;
    ClientClass clientClass;
    ServerClass serverClass;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeAll();
        exqListeners();
        requestUserPermission();

        //Reduce latancy
        setVolumeControlStream(AudioManager.MODE_IN_COMMUNICATION);


        mOn = (Button) findViewById(R.id.button);
        isOn = false;
        isRecording = false;

        manager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        manager.setMode(AudioManager.MODE_IN_COMMUNICATION);

//        //Check for headset availability
//        AudioDeviceInfo[] audioDevices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
//        for (AudioDeviceInfo deviceInfo : audioDevices) {
//            if (deviceInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || deviceInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET || deviceInfo.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
//                IS_HEADPHONE_AVAILBLE = true;
//            }
//        }


    }

    public void initAudio() {
        //Tests all sample rates before selecting one that works
        int sample_rate = getSampleRate();
        minBuffer = AudioRecord.getMinBufferSize(sample_rate, channel_in, format);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        record = new AudioRecord(source, sample_rate, channel_in, format, minBuffer);
        recordState = record.getState();
        int id = record.getAudioSessionId();
        Log.d("Record", "ID: " + id);
        playerState = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            player = new AudioTrack(
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                    new AudioFormat.Builder().setEncoding(format).setSampleRate(sample_rate).setChannelMask(channel_out).build(),
                    minBuffer,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
            playerState = player.getState();
            // Formatting Audio
            if(AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler echo = AcousticEchoCanceler.create(id);
                echo.setEnabled(true);
                Log.d("Echo", "Off");
            }
            if(NoiseSuppressor.isAvailable()) {
                NoiseSuppressor noise = NoiseSuppressor.create(id);
                noise.setEnabled(true);
                Log.d("Noise", "Off");
            }
            if(AutomaticGainControl.isAvailable()) {
                AutomaticGainControl gain = AutomaticGainControl.create(id);
                gain.setEnabled(false);
                Log.d("Gain", "Off");
            }
            BassBoost base = new BassBoost(1, player.getAudioSessionId());
            base.setStrength((short) 1000);
        }
    }

    public void startAudio() {
//        int read = 0, write = 0;
//        if(recordState == AudioRecord.STATE_INITIALIZED && playerState == AudioTrack.STATE_INITIALIZED) {
//            record.startRecording();
//            player.play();
//            isRecording = true;
//            Log.d("Record", "Recording...");
//        }
//        while(isRecording) {
//            short[] audioData = new short[minBuffer];
//            if(record != null)
//                read = record.read(audioData, 0, minBuffer);
//            else
//                break;
//            Log.d("Record", "Read: " + read);
//            if(player != null)
//                write = player.write(audioData, 0, read);
//            else
//                break;
//            Log.d("Record", "Write: " + write);
//        }
    }

    public void endAudio() {
//        if(record != null) {
//            if(record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
//                record.stop();
//            isRecording = false;
//            Log.d("Record", "Stopping...");
//        }
//        if(player != null) {
//            if(player.getPlayState() == AudioTrack.PLAYSTATE_PLAYING)
//                player.stop();
//            isRecording = false;
//            Log.d("Player", "Stopping...");
//        }
    }

    public int getSampleRate() {
        //Find a sample rate that works with the device
        for (int rate : new int[] {8000, 11025, 16000,  22050, 44100, 48000}) {
            int buffer = AudioRecord.getMinBufferSize(rate, channel_in, format);
            if (buffer > 0)
                return rate;
        }
        return -1;
    }

    public void requestUserPermission() {

        ArrayList<String> permissions = new ArrayList<String>();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission_group.NEARBY_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission_group.NEARBY_DEVICES);
            }
        }
        String[] str = new String[permissions.size()];

        for (int i = 0; i < permissions.size(); i++) {
            str[i] = permissions.get(i);
        }

        ActivityCompat.requestPermissions(this, str, 1);

    }

    private void exqListeners() {

        btnWifiState.setOnClickListener(view -> {
            Intent intent = new Intent(Settings.Panel.ACTION_WIFI);
            startActivityForResult(intent, 1);

        });

        btnDiscover.setOnClickListener(view -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestUserPermission();
                return;
            }

            wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    tvStatus.setText("Discovery Started");
                }

                @Override
                public void onFailure(int i) {
                    final Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                }
            });
        });

        listView.setOnItemClickListener((adapterView, view, i, l) -> {
            final WifiP2pDevice device = deviceArray[i];
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = device.deviceAddress;
            wifiP2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    tvStatus.setText("Connected device : "+device.deviceAddress);
                }

                @Override
                public void onFailure(int i) {
                    tvStatus.setText("not connected");
                }
            });
        });

        btnStop.setOnClickListener(view -> {
//            ExecutorService executor = Executors.newSingleThreadExecutor();
//            String msg = etMessage.getText().toString();
//            executor.execute(()->{
//                if(msg!=null && isHost){
//
//                }else if(msg != null && !isHost){
//
//                }
//            });

            isRecording=false;

        });

        btnStart.setOnClickListener(view -> {

            ExecutorService executor = Executors.newSingleThreadExecutor();

            executor.execute(()->{
                if(isHost){

                    int read = 0, write = 0;
                    if(recordState == AudioRecord.STATE_INITIALIZED) {
                        record.startRecording();
                        isRecording = true;
                        Log.d("Record", "Recording...");
                    }

                    while(isRecording) {

                        byte[] audioData = new byte[minBuffer];
                        if(record != null)
                            read = record.read(audioData, 0, minBuffer);
                        else
                            break;
                        Log.d("Record", "Read: " + read);
                        if(read>0)
                            serverClass.write(audioData);
                        Log.d("Record", "Write: " + write);

                    }

                }else if(!isHost){

//                    clientClass.write(msg.getBytes());
                    isRecording=true;

                }
            });

        });

    }



//    OutputStream outputStream;
//    class StartStreaming extends Thread{
//
//        @Override
//        public void run() {
////            try {
////
////                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
////                    return;
////                }
////
////                int read = 0, write = 0;
////                if(recordState == AudioRecord.STATE_INITIALIZED) {
////                    record.startRecording();
////                    isRecording = true;
////                    Log.d("Record", "Recording...");
////
////                    Log.d("tag", InetAddress.getByName("192.168.49.255").toString());
////                }
////
//////                    InetAddress group = InetAddress.getByName("203.0.113.0");
//////                    DatagramPacket packet;
//////                    packet = new DatagramPacket(buf, buf.length, group, 4446);
//////                    socket.send(packet);
////
////
////                Log.d("hi","hi");
////                socket = new DatagramSocket(PORT);
////                    socket.setBroadcast(true);
////                if(socket!=null)
////
////                    while (status) {
////
////                        byte[] buffer = new byte[minBuffer];
////
////                        if (record != null)
////                            read = record.read(buffer, 0, minBuffer);
////                        else
////                            break;
////                        Log.d("Record", "Read: " + read);
////
////                        DatagramPacket packet = new DatagramPacket(buffer, minBuffer, InetAddress.getByName("192.168.49.255"), PORT);
////                        socket.send(packet);
////                        Log.d("hi","hi");
////                    }
////
////                if(socket != null)
////                    socket.close();
////
////                record.stop();
////                isRecording = false;
////
////            } catch (IOException e) {
////                Log.e("VS", "UnknownHostException");
////            }
////
//
//
//
//       // ###########################
//
//
//
//
//            try {
//                ServerSocket serverSocket = new ServerSocket(8889);
//                Socket socket = serverSocket.accept();
//
//                outputStream = socket.getOutputStream();
//
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//            ExecutorService executor = Executors.newSingleThreadExecutor();
//            Handler handler = new Handler(Looper.getMainLooper());
//
//            executor.execute(() -> {
////                byte[] buffer = new byte[1024];
//                int bytes;
//                if (recordState == AudioRecord.STATE_INITIALIZED)
//                    record.startRecording();
//                isRecording=true;
//
//                while (socket != null) {
//
//                    byte[] buffer = new byte[1024];
//
//
////                        bytes = inputStream.read(buffer);
//
////                    handler.post(() -> {
////
////                        if (recordState == AudioRecord.STATE_INITIALIZED)
////                            record.startRecording();
////
////                        int read = record.read(buffer, 0, minBuffer);
////                        Log.d("output",Integer.toString(read));
////
////                        try {
////                            outputStream.write(buffer);
////                        } catch (IOException e) {
////                            e.printStackTrace();
////                        }
////
////                        record.stop();
////                    });
//
//
//
//
//                    int read = record.read(buffer, 0, minBuffer);
//                    Log.d("output",Integer.toString(read));
//
//                    try {
//                        outputStream.write(buffer);
//                        outputStream.flush();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//
//                record.stop();
//                isRecording=false;
//
//                });
//
//            try {
//                outputStream.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//        }
//
//
//    }


    public void ToGo(View view) {
        Intent i = new Intent(getApplicationContext(),taptospeak.class);
        startActivity(i);

    }


//    InputStream inputStream;
//
//    class CustServer extends Thread{
//
//        @Override
//        public void run() {
//
////            serverSocket = null;
////
////            WifiManager.MulticastLock lock = wifi.createMulticastLock("dk.aboaya.pingpong");
////
////            try {
////                lock.acquire();
//////                serverSocket = new DatagramSocket(PORT);
//////                serverSocket.setBroadcast(true);
//////                serverSocket.setSoTimeout(15000); //15 sec wait for the client to connect
////
////                serverSocket = new MulticastSocket(PORT);
////                serverSocket.joinGroup(InetAddress.getByName("192.168.49.255"));
////
////            } catch (SocketException e) {
////                Log.d("exp","hi");
////                e.printStackTrace();
////            } catch (IOException e) {
////                e.printStackTrace();
////            }
////
////
////            int read = 0, write = 0;
////            if( playerState == AudioTrack.STATE_INITIALIZED) {
////                player.play();
////            }
////            DatagramPacket packet = null;
////
////            while(status) {
////
////                byte[] data = new byte[minBuffer];
////                try {
////                    packet = new DatagramPacket(data, minBuffer);
////
////                    if(serverSocket != null)
////                        serverSocket.receive(packet);
////
////                } catch (IOException e) {
////                    e.printStackTrace();
////                }
////
////                if(player != null)
////                    write = player.write(packet.getData(), 0, read);
////                else
////                    break;
////                Log.d("Record", "Write: " + write);
////            }
////            player.stop();
////            lock.release();
////            if(serverSocket!=null)
////                serverSocket.close();
//
//
//            // ###########################
//
//
//            try {
//                Socket testsocket = new Socket();
//                testsocket.connect(new InetSocketAddress(hostAddress,8889));
//
//                 inputStream = testsocket.getInputStream();
////                 OutputStream outputStream = testsocket.getOutputStream();
//
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//            ExecutorService executorService = Executors.newSingleThreadExecutor();
//            Handler handler = new Handler(Looper.getMainLooper());
//
//            executorService.execute(() -> {
//                byte[] buffer = new byte[1024];
//                int bytes;
//
//                if( playerState == AudioTrack.STATE_INITIALIZED) {
//                    player.play();
//                }
//
//                while (socket != null){
//                    try {
//                        buffer = new byte[1024];
//                        bytes = inputStream.read(buffer);
//                        if(bytes>0){
//                            int finalBytes = bytes;
////                            handler.post(() -> {
////                                String message = new String(buffer,0,finalBytes);
////
////                                int read = 0, write = 0;
////                                if( playerState == AudioTrack.STATE_INITIALIZED) {
////                                    player.play();
////                                }
////
////                                if(player != null)
////                                    write = player.write(buffer, 0, read);
////
////                                Log.d("Record", "Write: " + write + message);
////
////                                player.stop();
////                            });
//
//                            String message = new String(buffer,0,finalBytes);
//
//                            int read = 0, write = 0;
//
//
//                            if(player != null)
//                                write = player.write(buffer, 0, read);
//
//                            Log.d("Record", "Write: " + write + message);
//
//
//
//
//                        }
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            });
//
//
//
//        }
//    }

    private void initializeAll() {

        tvStatus = findViewById(R.id.tvConStatus);
        btnWifiState = findViewById(R.id.btnWifiState);
        btnDiscover = findViewById(R.id.btnDiscover);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        listView = findViewById(R.id.listview);

        wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = wifiP2pManager.initialize(this,getMainLooper(),null);
        receiver = new WifiDirectBroadcastRecever(wifiP2pManager,channel,this);
        wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        initAudio();
        st =  new HashSet<Integer>();

        Log.d("debug","1");

    }

    InetAddress getBroadcastAddress() throws IOException {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = wifi.getDhcpInfo();
        // handle null somehow

        int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++)
            quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
        return InetAddress.getByAddress(quads);
    }

    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {

            if(!peers.equals(wifiP2pDeviceList.getDeviceList())) {
                peers.clear();
                peers.addAll(wifiP2pDeviceList.getDeviceList());

                devicesNames = new String[wifiP2pDeviceList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[wifiP2pDeviceList.getDeviceList().size()];

                int index = 0;
                for (WifiP2pDevice device : wifiP2pDeviceList.getDeviceList()) {
                    devicesNames[index] = device.deviceName;
                    deviceArray[index] = device;
                    index++;
                }

                Log.d("debug", String.valueOf(devicesNames.length));
                ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, devicesNames);
                listView.setAdapter(adapter);

//                 adapter.addAll(wifiP2pDeviceList.getDeviceList().toString());
                adapter.notifyDataSetChanged();
                if (peers.size() == 0) {
                    tvStatus.setText("No devices found");
                    return;
                }

            }

        }
    };

    WifiP2pManager.ConnectionInfoListener connectionInfoListener =  new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {

            final InetAddress groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;
            if(wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner ){
                tvStatus.setText("HOST");
                isHost=true;
                serverClass = new ServerClass();
                serverClass.start();
            }else{
                tvStatus.setText("CLIENT");
                isHost=false;
                clientClass = new ClientClass(groupOwnerAddress);
                clientClass.start();
            }

        }
    };


    public class ServerClass extends Thread{

        ServerSocket serverSocket;
        private InputStream inputStream;
        private OutputStream outputStream;


        public void write(byte[] bytes){

            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(8889);
                socket = serverSocket.accept();
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

            } catch (IOException e) {
                e.printStackTrace();
            }

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());

            executor.execute(() -> {
                byte[] buffer = new byte[1024];
                int bytes;

                while (socket!=null){
                    try {
                        bytes = inputStream.read(buffer);
                        if(bytes > 0){
                            int finalBytes = bytes;
                            handler.post(() -> {
                                String str = new String(buffer,0,finalBytes);

                            });

                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            });

        }
    }


    public class ClientClass extends Thread{
        String hostAddress;
        private InputStream inputStream;
        private OutputStream outputStream;

        public void write(byte[] bytes){

            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        public ClientClass(InetAddress hostAddress){
            this.hostAddress = hostAddress.getHostAddress();
            socket = new Socket();
        }

        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostAddress,8889),500);

                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

            } catch (IOException e) {
                e.printStackTrace();
            }

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());

            executorService.execute(() -> {

                int bytes;
                if(playerState == AudioTrack.STATE_INITIALIZED) {
                    player.play();
                    Log.d("Record", "Recording...");
                }
                while (socket != null){

                    byte[] buffer = new byte[minBuffer];

                    try {
                        bytes = inputStream.read(buffer);
                        if(bytes>0){

                            int finalBytes = bytes;
                            final int[] write = new int[1];
                            handler.post(() -> {

                                if(player != null)
                                    if(isRecording)
                                        write[0] = player.write(buffer, 0, finalBytes);
                                    Log.d("Record", "Write: " + write[0]);

                            });

                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

                player.stop();
            });

        }
    }



    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver,intentFilter);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestUserPermission();
            return;
        }


        wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                tvStatus.setText("Discovery Started");
            }

            @Override
            public void onFailure(int i) {
                tvStatus.setText("Discovery Failed");
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        if(serverSocket!=null)
//            serverSocket.close();
//        if(socket != null)
//            socket.close();



    }
}