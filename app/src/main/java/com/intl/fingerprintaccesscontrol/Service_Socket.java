package com.intl.fingerprintaccesscontrol;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.StrictMode;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Service_Socket extends Service {
    private static final String strIPAddr = "127.0.0.1";
    private static final int iPort = 8899;
    private static final int INTERVAL4TESTSOCKET = 7000;
    public static Socket socket;
    public static DataInputStream in;
    public static DataOutputStream out;
    public static boolean bSocketflag;
    public static String strMessageFor;
    public byte[] recvbuffer = new byte[1024];
    private boolean IsRun = true;
    private byte[] dataSend;
    private ExecutorService mThreadPool;
    private Handler mHandler;
    private Timer mTimer;

    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public Service_Socket getService() {
            return Service_Socket.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        //Android 4.0+ the socket communication must be added
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectLeakedSqlLiteObjects().penaltyLog().penaltyDeath().build());

        bSocketflag = false;
        strMessageFor = "MainActivity";

        //create threadpool
        mThreadPool = Executors.newCachedThreadPool();
        socketConnect();
        socketRecv();

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case 1:
                        socketTest();
                        break;
                    case 2:
                        Toast.makeText(getApplicationContext(), "Connected Succeed", Toast.LENGTH_SHORT).show();
                        break;
                    case 3:
                        Toast.makeText(getApplicationContext(), "Connected Failed", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        };

        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Message msg = mHandler.obtainMessage();
                msg.what = 1;
                mHandler.sendMessage(msg);
            }
        }, 500, INTERVAL4TESTSOCKET);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        socketDisconnect();
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        IsRun = false;
        bSocketflag = false;
    }

    //Broadcast the socket data
    public void sendMsgtoActivty(String msg) {
        Intent intent = new Intent(strMessageFor);
        intent.putExtra("msg", msg);
        sendBroadcast(intent);
    }

    // Thread for disconnect socket
    public void socketDisconnect() {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                if (bSocketflag) {
                    try {
                        in.close();
                        out.close();
                        socket.close();
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    // Thread for connect socket
    public void socketConnect() {
        socketDisconnect();
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                	Thread.sleep(100);
                    socket = new Socket();
                    socket.setSoTimeout(100);
                    SocketAddress isa = new InetSocketAddress(strIPAddr, iPort);
                    socket.connect(isa, 100);
                    in = new DataInputStream(socket.getInputStream());
                    out = new DataOutputStream(socket.getOutputStream());
                    bSocketflag = true;

                    Message msg = mHandler.obtainMessage();
                    msg.what = 2;
                    mHandler.sendMessage(msg);
                } catch (Exception e) {
                    bSocketflag = false;

                    Message msg = mHandler.obtainMessage();
                    msg.what = 3;
                    mHandler.sendMessage(msg);
                }
            }
        });
    }

    public void socketReconnect() {
        socketDisconnect();
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                	Thread.sleep(100);
                    socket = new Socket();
                    socket.setSoTimeout(100);
                    SocketAddress isa = new InetSocketAddress(strIPAddr, iPort);
                    socket.connect(isa, 100);
                    in = new DataInputStream(socket.getInputStream());
                    out = new DataOutputStream(socket.getOutputStream());
                    bSocketflag = true;

                    Message msg = mHandler.obtainMessage();
                    msg.what = 2;
                    mHandler.sendMessage(msg);
                } catch (Exception e) {
                    bSocketflag = false;

                    Message msg = mHandler.obtainMessage();
                    msg.what = 3;
                    mHandler.sendMessage(msg);
                }
            }
        });
    }

    // Thread for test socket
    public void socketTest() {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                if (bSocketflag) {
                    try {
                        socket.sendUrgentData(0xFF);// connected test
                    } catch (Exception e) {
                        socketReconnect();
                    }
                }
            }
        });
    }

    // Thread for send socket
    public void socketSend(byte[] data) {
        dataSend = data;
        socketTest();
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                if (bSocketflag) {
                    try {
                        out.write(dataSend, 0, dataSend.length);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    // Thread for receive socket
    public void socketRecv() {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                while (IsRun) {
                    try {
                        if (bSocketflag) {
                            int iCount = in.read(recvbuffer);
                            if (iCount != -1) {
                                byte[] data = new byte[iCount];
                                for (int i = 0; i < iCount; i++) {
                                    data[i] = recvbuffer[i];
                                }
                                String str = new String(data, "ISO-8859-1");
                                sendMsgtoActivty(str);
                            }
                        }
                        Thread.sleep(100);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }
}
