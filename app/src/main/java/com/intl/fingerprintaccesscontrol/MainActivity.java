package com.intl.fingerprintaccesscontrol;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int MAXBUFFLEN = 1024;
    private static final int INTERVAL4PROCESSINGBUFFDATA = 100;
    private Timer mTimer;
    private Handler mHandler;
    private boolean bDataLock = false;
    private int iDataIn = 0;
    private int iDataOut = 0;
    private byte[] bytesDataRecBuff = new byte[MAXBUFFLEN];

    private TextView txtFingerInfo;
    //是否打开设备成功标志
    private boolean bStatus;
    //指令标志，等待该标志的指令回应
    private int iStatus;
    //图像特征存储缓冲区标志
    private byte bImgBuff;
    //当前的指纹操作 1：录入指纹 2：单一对比 3：搜索对比
    private int iType;
    //定时执行GetImage方法，等待手指放入传感器
    private Timer mTimer4GetImage;

    private boolean bLock = true;

    //todo isSet
    private boolean isSetting = false;
    int fingerCnt = 0;
    //todo 组件声明
    private View indexPage, settingPage, backHome, setting, setToIndex;


    private Service_Socket mService;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((Service_Socket.LocalBinder) service).getService();
            Service_Socket.strMessageFor = TAG;

            //切换串口
            new Timer().schedule(new TimerTask() {
                public void run() {
                    switchSerial(1, 115200);
                }
            }, 100);
            //切换模块
            new Timer().schedule(new TimerTask() {
                public void run() {
                    switchModule(2);
                }
            }, 300);
            //切换串口
            new Timer().schedule(new TimerTask() {
                public void run() {
                    switchSerial(1, 57600);
                }
            }, 500);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    private ServiceMsgReceiver mServiceMsgRecv;

    public class ServiceMsgReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(TAG)) {
                String msg = intent.getStringExtra("msg");
                try {
                    receiveData(msg.getBytes("ISO-8859-1"));
                } catch (Exception e) {
                    Log.i(TAG, "Error!");
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        HardwareControler.Open();
        HardwareControler.RelaySetValue(0, 0);
        HardwareControler.RelaySetValue(1, 0);

        initEnv();
        bindService(new Intent(this, Service_Socket.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume() {
        Service_Socket.strMessageFor = TAG;
        super.onResume();
    }

    @Override
    public void onDestroy() {
        stopTimer4GetImage();
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        HardwareControler.Close();
        super.onDestroy();
    }

    public void onClick_Event(View v) {
        //todo 点击事件
        switch (v.getId()) {
            case R.id.setting: {
                isSetting = true;
                if (fingerCnt == 0) {
                    //如果没有指纹，直接进入设置页
                    indexPage.setVisibility(View.GONE);
                    settingPage.setVisibility(View.VISIBLE);
                } else {
                    //如果有，先进行指纹识别，成功后在进入设置页
                    if (!bStatus) {
                        Toast.makeText(getApplicationContext(), "请先打开指纹设备后再进行操作！", Toast.LENGTH_LONG).show();
                        return;
                    }
                    startTimer4GetImage();
                    iType = 2;
                    bImgBuff = 0x01;
                }
                break;
            }
            case R.id.backHome: {
                isSetting = false;
                if (fingerCnt == 0) {
                    Toast.makeText(getApplicationContext(), "还没有指纹，先去设置里录入指纹吧！", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!bStatus) {
                    Toast.makeText(getApplicationContext(), "请先打开指纹设备后再进行操作！", Toast.LENGTH_LONG).show();
                    return;
                }
                startTimer4GetImage();
                iType = 2;
                bImgBuff = 0x01;
                break;
            }
            case R.id.setToIndex: {
                indexPage.setVisibility(View.VISIBLE);
                settingPage.setVisibility(View.GONE);
                break;
            }
            case R.id.btnStartModule:
                cmd_VfyPwd();
                break;
            case R.id.btnAddFinger:
                if (!bStatus) {
                    Toast.makeText(getApplicationContext(), "请先打开指纹设备后再进行操作！", Toast.LENGTH_SHORT).show();
                    return;
                }
                startTimer4GetImage();
                iType = 1;
                bImgBuff = 0x01;
                break;
            case R.id.btnSingleCompare:
                if (!bStatus) {
                    Toast.makeText(getApplicationContext(), "请先打开指纹设备后再进行操作！", Toast.LENGTH_LONG).show();
                    return;
                }
                startTimer4GetImage();
                iType = 2;
                bImgBuff = 0x01;
                break;
            case R.id.btnOpenDoor:
                openDoor();
                break;
            default:
                break;
        }
    }

    private void openDoor() {
        if (bLock) {
            bLock = false;
            new Timer().schedule(new TimerTask() {
                public void run() {
                    HardwareControler.RelaySetValue(0, 1);
                    HardwareControler.RelaySetValue(1, 1);
                }
            }, 100);
            new Timer().schedule(new TimerTask() {
                public void run() {
                    HardwareControler.RelaySetValue(0, 0);
                    HardwareControler.RelaySetValue(1, 0);
                    bLock = true;
                }
            }, 5100);
        }
    }

    private void startTimer4GetImage() {
        mTimer4GetImage = new Timer();
        mTimer4GetImage.schedule(new TimerTask() {
            @Override
            public void run() {
                Message message = new Message();
                message.what = 2;
                mHandler.sendMessage(message);
            }
        }, 100, 1000);
    }

    private void stopTimer4GetImage() {
        if (mTimer4GetImage != null) {
            mTimer4GetImage.cancel();
            mTimer4GetImage = null;
        }
    }

    //初始化
    private void initEnv() {
        //初始化数据缓冲标志
        bDataLock = false;
        iDataIn = 0;
        iDataOut = 0;

        bStatus = false;
        iStatus = 0;
        bImgBuff = 0x01;
        iType = 0;
        txtFingerInfo = (TextView) findViewById(R.id.txtFingerInfo);
        //todo 获取组件实例
        indexPage = findViewById(R.id.index);
        settingPage = findViewById(R.id.settingPage);
        backHome = findViewById(R.id.backHome);
        setting = findViewById(R.id.setting);
        setToIndex = findViewById(R.id.setToIndex);

        // 注册广播接收器
        mServiceMsgRecv = new ServiceMsgReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TAG);
        registerReceiver(mServiceMsgRecv, filter);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 1:
                        processingData();
                        break;
                    case 2:
                        cmd_GetImage();
                        break;
                }
                super.handleMessage(msg);
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
        }, 500, INTERVAL4PROCESSINGBUFFDATA);
    }

    //接收数据到数据缓冲区内
    private void receiveData(byte[] bRecData) {
        //Luo Test
        //Log.i(TAG + ":Data", bytes2HexString(bRecData));
        int i;
        int iDataLen = bRecData.length;
        if (bDataLock == false) {
            bDataLock = true;
            if (iDataIn + iDataLen <= MAXBUFFLEN) {
                for (i = 0; i < iDataLen; i++) {
                    bytesDataRecBuff[iDataIn + i] = bRecData[i];
                }
                iDataIn += iDataLen;
            } else {
                for (i = iDataIn; i < MAXBUFFLEN; i++) {
                    bytesDataRecBuff[i] = bRecData[i - iDataIn];
                }
                for (i = 0; i < iDataLen - MAXBUFFLEN + iDataIn; i++) {
                    bytesDataRecBuff[i] = bRecData[i + MAXBUFFLEN - iDataIn];
                }
                iDataIn = iDataLen - MAXBUFFLEN + iDataIn;
            }
            bDataLock = false;
        }
    }

    //读取当前缓冲区中的数据
    private int validReceiveLen() {
        if (iDataOut < iDataIn) {
            return (iDataIn - iDataOut);
        } else if (iDataOut > iDataIn) {
            return (MAXBUFFLEN - iDataOut + iDataIn);
        }
        return 0;
    }

    //返回后面第iNum有效数据的位置
    private int dataOutAdd(int iNum) {
        int ret = 0;
        if (iDataOut + iNum < MAXBUFFLEN) {
            ret = iDataOut + iNum;
        } else if (iDataOut + iNum > MAXBUFFLEN) {
            ret = iDataOut + iNum - MAXBUFFLEN;
        }
        return ret;
    }

    //从缓冲区内读出有效数据
    private void processingData() {
        if (bDataLock == false) {
            bDataLock = true;
            if (iDataIn != iDataOut) {
                int iValidLen = validReceiveLen();
                while (iValidLen > 10) {
                    //判断包头
                    if (bytesDataRecBuff[dataOutAdd(0)] == (byte) 0xEF && bytesDataRecBuff[dataOutAdd(1)] == (byte) 0x01) {
                        int iPacketLen = bytesDataRecBuff[dataOutAdd(8)] + 9;
                        //判断数据包是否完整
                        if (iPacketLen <= iValidLen) {
                            //读出一个数据包
                            byte[] Packet = new byte[iPacketLen];
                            for (int i = 0; i < iPacketLen; i++) {
                                Packet[i] = bytesDataRecBuff[dataOutAdd(i)];
                            }
                            processingPacket(Packet);
                            iDataOut = dataOutAdd(iPacketLen);
                        }
                        bDataLock = false;
                        return;
                    } else {
                        iDataOut = dataOutAdd(1);
                        iValidLen--;
                    }
                }
            }
            bDataLock = false;
        }
    }

    //处理接收的数据包
    private void processingPacket(byte[] Packet) {
        if (Packet[2] == (byte) 0xFF && Packet[3] == (byte) 0xFF && Packet[4] == (byte) 0xFF && Packet[5] == (byte) 0xFF && Packet[6] == (byte) 0x07) {
            //Log.i(TAG,CommonUnit.bytes2HexString(Packet));
            switch (iStatus) {
                case 0x13:
                    if (Packet[9] == 0x00) {
                        bStatus = true;
                        txtFingerInfo.setText("指纹模块打开成功");
                    } else {
                        bStatus = false;
                        txtFingerInfo.setText("指纹模块打开失败");
                    }
                    break;
                case 0x0D:
                    if (Packet[9] == 0x00) {
                        bStatus = true;
                        txtFingerInfo.setText("清空指纹数据库成功");
                    } else {
                        bStatus = false;
                        txtFingerInfo.setText("清空指纹数据库失败");
                    }
                    break;
                case 0x01:
                    if (Packet[9] == 0x00) {
                        stopTimer4GetImage();
                        cmd_GenChar(bImgBuff);
                    }
                    break;
                case 0x02:
                    if (Packet[9] == 0x00) {
                        if (iType == 1) {
                            if (bImgBuff == 2) {
                                cmd_RegModel();
                            } else {
                                cmd_GenChar(++bImgBuff);
                            }
                        } else if (iType == 2) {
                            cmd_LoadChar();
                        } else if (iType == 3) {
                            cmd_HighSpeedSearch();
                        }
                    } else {
                        txtFingerInfo.setText("缓存图像特征失败");
                    }
                    break;
                case 0x05:
                    if (Packet[9] == 0x00) {
                        cmd_StoreChar();
                    } else {
                        txtFingerInfo.setText("图像特征合成模版失败");
                    }
                    break;
                case 0x06:
                    if (Packet[9] == 0x00) {
                        txtFingerInfo.setText("指纹录入成功");
                        //todo 计数++
                        fingerCnt++;
                    } else {
                        txtFingerInfo.setText("指纹录入失败");
                    }
                    break;
                case 0x07:
                    if (Packet[9] == 0x00) {
                        cmd_Match();
                    } else {
                        txtFingerInfo.setText("读取模版失败");
                    }
                    break;
                case 0x03:
                    if (Packet[9] == 0x00) {
                        if (isSetting) {
                            //进入设置页
                            indexPage.setVisibility(View.GONE);
                            settingPage.setVisibility(View.VISIBLE);
                        } else {
                            //todo 开门
                            openDoor();
                            if (indexPage.getVisibility() == View.VISIBLE) {
                                Intent intent = new Intent();
                                //前一个（MainActivity.this）是目前页面，后面一个是要跳转的下一个页面
                                intent.setClass(MainActivity.this, Main2Activity.class);
                                startActivity(intent);
                            }
                        }
                        txtFingerInfo.setText("当前指纹与位置0x01对比结果为匹配成功");
                    } else {
                        txtFingerInfo.setText("当前指纹与位置0x01对比结果为匹配失败");
                    }
                    break;
                case 0x1B:
                    if (Packet[9] == 0x00) {
                        if (isSetting) {
                            //如果要进入设置
                            indexPage.setVisibility(View.GONE);
                            settingPage.setVisibility(View.VISIBLE);
                        } else {
                            //todo 开门
                            openDoor();
                            if (indexPage.getVisibility() == View.VISIBLE) {
                                //监听按钮，如果点击，就跳转
                                Intent intent = new Intent();
                                //前一个（MainActivity.this）是目前页面，后面一个是要跳转的下一个页面
                                intent.setClass(MainActivity.this, Main2Activity.class);
                                startActivity(intent);
                            }
                        }

                        txtFingerInfo.setText("当前指纹识在指纹库中对比结果为匹配成功");
                    } else {
                        txtFingerInfo.setText("当前指纹识在指纹库中对比结果为匹配失败");
                    }
                    break;
                default:
                    txtFingerInfo.setText("当前指令无效");
                    break;
            }
        }
    }

    private void cmd_Match() {
        byte[] bytes = new byte[12];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0x01;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xFF;
        bytes[4] = (byte) 0xFF;
        bytes[5] = (byte) 0xFF;
        bytes[6] = (byte) 0x01;
        bytes[7] = (byte) 0x00;

        bytes[8] = (byte) 0x03;

        bytes[9] = (byte) 0x03;

        checkSum(bytes, bytes[8]);
        mService.socketSend(bytes);
        iStatus = bytes[9];
        txtFingerInfo.setText("精确比对两枚指纹特征......");
    }

    private void cmd_LoadChar() {
        byte[] bytes = new byte[15];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0x01;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xFF;
        bytes[4] = (byte) 0xFF;
        bytes[5] = (byte) 0xFF;
        bytes[6] = (byte) 0x01;
        bytes[7] = (byte) 0x00;

        bytes[8] = (byte) 0x06;

        bytes[9] = (byte) 0x07;
        bytes[10] = (byte) 0x02;
        String strAddr = "1";
        bytes[11] = (byte) 0x00;
        bytes[12] = (byte) Integer.parseInt(strAddr);

        checkSum(bytes, bytes[8]);
        mService.socketSend(bytes);
        iStatus = bytes[9];
        txtFingerInfo.setText("读出指纹模版......");
    }

    private void cmd_HighSpeedSearch() {
        byte[] bytes = new byte[17];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0x01;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xFF;
        bytes[4] = (byte) 0xFF;
        bytes[5] = (byte) 0xFF;
        bytes[6] = (byte) 0x01;
        bytes[7] = (byte) 0x00;

        bytes[8] = (byte) 0x08;

        bytes[9] = (byte) 0x1B;
        bytes[10] = (byte) 0x01;
        bytes[11] = (byte) 0x00;
        bytes[12] = (byte) 0x00;
        bytes[13] = (byte) 0x01;
        bytes[14] = (byte) 0x01;

        checkSum(bytes, bytes[8]);
        mService.socketSend(bytes);
        iStatus = bytes[9];
        txtFingerInfo.setText("高速搜索指纹数据库......");
    }

    private void cmd_GetImage() {
        byte[] bytes = new byte[12];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0x01;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xFF;
        bytes[4] = (byte) 0xFF;
        bytes[5] = (byte) 0xFF;
        bytes[6] = (byte) 0x01;
        bytes[7] = (byte) 0x00;

        bytes[8] = (byte) 0x03;

        bytes[9] = (byte) 0x01;

        checkSum(bytes, bytes[8]);
        mService.socketSend(bytes);
        iStatus = bytes[9];
        txtFingerInfo.setText("请将手指平放在传感器上......");
    }

    private void cmd_GenChar(byte bBufferID) {
        byte[] bytes = new byte[13];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0x01;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xFF;
        bytes[4] = (byte) 0xFF;
        bytes[5] = (byte) 0xFF;
        bytes[6] = (byte) 0x01;
        bytes[7] = (byte) 0x00;

        bytes[8] = (byte) 0x04;

        bytes[9] = (byte) 0x02;
        bytes[10] = bBufferID;

        checkSum(bytes, bytes[8]);
        mService.socketSend(bytes);
        iStatus = bytes[9];
        txtFingerInfo.setText("缓存图像特征" + String.valueOf(bBufferID) + "......");
    }

    private void cmd_RegModel() {
        byte[] bytes = new byte[12];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0x01;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xFF;
        bytes[4] = (byte) 0xFF;
        bytes[5] = (byte) 0xFF;
        bytes[6] = (byte) 0x01;
        bytes[7] = (byte) 0x00;

        bytes[8] = (byte) 0x03;

        bytes[9] = (byte) 0x05;

        checkSum(bytes, bytes[8]);
        mService.socketSend(bytes);
        iStatus = bytes[9];
        txtFingerInfo.setText("图像特征合成模板......");
    }

    private void cmd_StoreChar() {
        byte[] bytes = new byte[15];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0x01;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xFF;
        bytes[4] = (byte) 0xFF;
        bytes[5] = (byte) 0xFF;
        bytes[6] = (byte) 0x01;
        bytes[7] = (byte) 0x00;

        bytes[8] = (byte) 0x06;

        bytes[9] = (byte) 0x06;
        bytes[10] = (byte) 0x02;

        String strAddr = "1";
        bytes[11] = (byte) 0x00;
        bytes[12] = (byte) Integer.parseInt(strAddr);

        checkSum(bytes, bytes[8]);
        mService.socketSend(bytes);
        iStatus = bytes[9];
        txtFingerInfo.setText("将模板存储到位置" + strAddr + "中......");
    }

    private void cmd_VfyPwd() {
        byte[] bytes = new byte[16];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0x01;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xFF;
        bytes[4] = (byte) 0xFF;
        bytes[5] = (byte) 0xFF;
        bytes[6] = (byte) 0x01;
        bytes[7] = (byte) 0x00;

        bytes[8] = (byte) 0x07;

        bytes[9] = (byte) 0x13;
        bytes[10] = (byte) 0x00;
        bytes[11] = (byte) 0x00;
        bytes[12] = (byte) 0x00;
        bytes[13] = (byte) 0x00;

        checkSum(bytes, bytes[8]);
        mService.socketSend(bytes);
        iStatus = bytes[9];
        txtFingerInfo.setText("打开指纹识别设备......");
    }

    private void cmd_Empty() {
        byte[] bytes = new byte[12];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0x01;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xFF;
        bytes[4] = (byte) 0xFF;
        bytes[5] = (byte) 0xFF;
        bytes[6] = (byte) 0x01;
        bytes[7] = (byte) 0x00;

        bytes[8] = (byte) 0x03;

        bytes[9] = (byte) 0x0D;

        checkSum(bytes, bytes[8]);
        mService.socketSend(bytes);
        iStatus = bytes[9];
        txtFingerInfo.setText("清空指纹数据库......");
    }

    //计算校验和
    private void checkSum(byte[] bytes, byte bLen) {
        int iSum = 0;
        for (int i = 0; i < bLen + 1; i++) {
            iSum += bytes[i + 6];
        }
        byte retH = (byte) ((iSum & 0xFF00) >> 8);
        byte retL = (byte) (iSum & 0xFF);
        bytes[bytes.length - 1] = retL;
        bytes[bytes.length - 2] = retH;
    }

    //设置校验位
    private void setSummationVerify(byte[] bytes) {
        byte b = 0x00;
        for (int i = 0; i < bytes.length - 1; i++) {
            b += bytes[i];
        }
        bytes[bytes.length - 1] = b;
    }

    //切换串口设置
    public void switchSerial(int iWhich, int iBaudrate) {
        byte[] bytes = new byte[4];
        bytes[0] = (byte) 0x4C;
        bytes[1] = (byte) 0x4B;
        bytes[2] = (byte) iWhich;
        switch (iBaudrate) {
            case 4800:
                bytes[3] = (byte) 0x00;
                break;
            case 9600:
                bytes[3] = (byte) 0x01;
                break;
            case 38400:
                bytes[3] = (byte) 0x02;
                break;
            case 57600:
                bytes[3] = (byte) 0x03;
                break;
            case 115200:
                bytes[3] = (byte) 0x04;
                break;
        }
        mService.socketSend(bytes);
    }

    //切换模块连接
    public void switchModule(int iWhich) {
        byte[] bytes = new byte[7];
        bytes[0] = (byte) 0xAA;
        bytes[1] = (byte) bytes.length;
        bytes[2] = (byte) 0x01;
        bytes[3] = (byte) 0x00;
        bytes[4] = (byte) 0x01;
        bytes[5] = (byte) iWhich;
        setSummationVerify(bytes);
        mService.socketSend(bytes);
    }
}
