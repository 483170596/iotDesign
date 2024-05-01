package com.intl.fingerprintaccesscontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

import static com.intl.fingerprintaccesscontrol.HardwareControler.TrafficSetValue;

public class Main2Activity extends Activity {

    private static final String TAG = "SmartHomeMain";
    private static final int MAXBUFFLEN = 1024;
    private static final int INTERVAL4PROCESSINGBUFFDATA = 100;
    private Timer mTimer;
    private Handler mHandler;
    private boolean bDataLock = false;
    private int iDataIn = 0;
    private int iDataOut = 0;
    private byte[] bytesDataRecBuff = new byte[MAXBUFFLEN];

    /*** wsn define ***/
    private static final byte FRAMETYPE_WSN = (byte) 0x02;           //无线传感类型
    private static final byte WSNCMD_INTERVALUPLOAD = (byte) 0x03;   //定时上传
    private static final byte WSNCMD_TRIGGERUPLOAD = (byte) 0x05;    //触发上传
    //无线类型
    private static final byte WSNTYPE_ZIGBEE = (byte) 0x01;          //Zigbee
    private static final byte WSNTYPE_WIFI = (byte) 0x02;            //Wifi
    private static final byte WSNTYPE_BLUETOOTH = (byte) 0x03;       //Bluetooth

    private boolean bLightStatus = false;

    private boolean bAlarmStatus = false;
    private boolean bFanStatus = false;

    private Timer mDataTimer;
    private Handler mDataHandler;

    private SharedPreferences spData;

    /*** ui define ***/
    private TextView txt_lr_sun, txt_lr_temp, txt_lr_humi, txt_lr_shaker;
    private TextView txt_bd_sun, txt_bd_temp, txt_bd_humi;

    private Button btn_alarm, btn_curtain_open, btn_curtain_close, btn_lights, btn_fan;
    private Button btn_in;

    //todo 状态
    private boolean bBdLed = false;
    private boolean bBdTra = false;
    private int tra_value = 0b000000000111; // 初始状态，只有1个灯是亮的
    private int tra_cnt = 1;
    private boolean tra_st = true;
    //todo 组件声明
    private Button btn_bd_led, btn_out, btn_bd_tra;
    // todo 定时器 及 定时任务
    private Timer timer_for_tra = new Timer();
    private TimerTask timerTask_for_tra;

    private CheckBox check_shaker, check_humi;

    private EditText edit_openFan, edit_closeFan;

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
                    switchModule(1);
                }
            }, 200);
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
        setContentView(R.layout.activity_main2);

        initControl();
        initView();
        initData();
        initEnv();
        bindService(new Intent(this, Service_Socket.class), mConnection, Context.BIND_AUTO_CREATE);

        inMode();
        outMode();
    }

    //todo led回调函数
    private void bd_led_on() {
        if (bBdTra) {
            Toast.makeText(getApplicationContext(), "请先关闭流水灯", Toast.LENGTH_SHORT).show();
            return;
        }
        bBdLed = true;
        //开led
        Log.i(TAG, "led: 开led");
        HardwareControler.TrafficSetValue(0, 0b111111111111);
        btn_bd_led.setText("关闭");
        btn_bd_led.setTextColor(Color.RED);
    }

    //todo led回调函数
    private void bd_led_off() {
        //关led
        Log.i(TAG, "led: 关led");
        HardwareControler.TrafficSetValue(1, 0);
        btn_bd_led.setText("打开");
        btn_bd_led.setTextColor(Color.GREEN);
        bBdLed = false;
    }

    //todo tra回调函数
    private void bd_tra_on() {
        if (bBdLed) {
            Toast.makeText(getApplicationContext(), "请先关闭照明灯", Toast.LENGTH_SHORT).show();
            return;
        }
        bBdTra = true;
        timer_for_tra = new Timer();
        //todo 设置定时任务
        timerTask_for_tra = new TimerTask() {
            @Override
            public void run() {
                //流水灯
                if (tra_st) {
                    HardwareControler.TrafficSetValue(0, tra_value);
                    tra_value = ((tra_value >> 2) | (tra_value << 10));
                    tra_cnt++;
                    if (tra_cnt > 10) {
                        tra_cnt = 1;
                        tra_st = false;
                    }
                } else {
                    if (tra_cnt % 3 == 0) {
                        tra_value = 0b001001001001;
                        HardwareControler.TrafficSetValue(0, tra_value);
                    } else if (tra_cnt % 3 == 1) {
                        tra_value = 0b010010010010;
                        HardwareControler.TrafficSetValue(0, tra_value);
                    } else {
                        tra_value = 0b100100100100;
                        HardwareControler.TrafficSetValue(0, tra_value);
                    }
                    tra_cnt++;
                    if (tra_cnt > 6) {
                        tra_cnt = 1;
                        tra_st = true;
                        tra_value = 0b000000000111;
                    }
                }
            }
        };
        timer_for_tra.schedule(timerTask_for_tra, 100, 400);
        //开流水灯
        Log.i(TAG, "led: 开流水灯");
        btn_bd_tra.setText("关闭");
        btn_bd_tra.setTextColor(Color.RED);
    }

    private void bd_tra_off() {
        //关led
        Log.i(TAG, "led: 关流水灯");
        timer_for_tra.cancel();
        timer_for_tra = null;
        timerTask_for_tra.cancel();
        timerTask_for_tra = null;
        HardwareControler.TrafficSetValue(1, 0);
        btn_bd_tra.setText("打开");
        btn_bd_tra.setTextColor(Color.GREEN);

        bBdTra = false;
    }

    private void inMode() {
        btn_in.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlarmOff();  //关报警

                FanOff();    //关风扇

                HardwareControler.StepperSetValue(1, 8);    //开窗帘左

                LightOn();   //开灯
            }
        });
    }

    private void outMode() {
        btn_out.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlarmOff();         //关报警

                FanOn();    //开风扇

                HardwareControler.StepperSetValue(0, 8);    //关窗帘右

                LightOff();   //关灯

                //todo 关灯 延时关继电器 关定时器 跳转
                if (bBdTra) bd_tra_off();
                if (bBdLed) bd_led_off();

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
                    }
                }, 5100);

                finish();
            }
        });
    }

    private void initData() {
        mDataTimer = new Timer();
        mDataTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Message message = new Message();
                message.what = 1;
                mDataHandler.sendMessage(message);
            }
        }, 500, 1000);

        mDataHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 1:
                        String strIllumination = getIllumination();
                        String strHumiture = getHumiture();
                        try {
                            if (!strHumiture.equals("")) {

                                String strHumi = strHumiture.substring(0, 3);
                                String strTemp = strHumiture.substring(3, 6);
                                int iTemp = Integer.parseInt(strTemp);
                                sett(iTemp);
                                int iHumi = Integer.parseInt(strHumi);
                                seth(iHumi);

                            }
                            if (!strIllumination.equals("")) {
                                strIllumination = strIllumination.substring(0,
                                        strIllumination.length() - 1);
                                int iIllumination = Integer.parseInt(strIllumination);
                                setIllumination((int) (iIllumination * 0.8));
                            }

                        } catch (Exception e) {
                            Log.i("strIllumination", strIllumination);
                            Log.i("strHumiture", strHumiture);
                        }
                        break;
                }
                super.handleMessage(msg);
            }
        };
    }

    private void sett(int iValue) {
        DecimalFormat fNum = new DecimalFormat("#0.0");
        float fValue = 0.1f * iValue;
        txt_lr_temp.setText(fNum.format(fValue) + " ℃");
    }

    private void seth(int iValue) {
        DecimalFormat fNum = new DecimalFormat("#0.0");
        float fValue = 0.1f * iValue;
        txt_lr_humi.setText(fNum.format(fValue) + " %");
    }

    private void setIllumination(int iValue) {
        txt_lr_sun.setText(String.valueOf(iValue) + " Lux");
    }

    private String getHumiture() {
        String str = "";
        try {
            FileInputStream in = new FileInputStream("/dev/dht11_ctl");
            byte bytes[] = new byte[50];
            int iCount = in.read(bytes);
            if (iCount != -1) {
                str = new String(bytes, 0, iCount);
            }
            in.close();
        } catch (Exception e) {
            Log.i("Humiture", "error!");
        }
        return str;
    }

    private String getIllumination() {
        String str = "";
        try {
            FileInputStream in = new FileInputStream(
                    "/sys/bus/iio/devices/iio:device0/in_voltage1_raw");
            byte bytes[] = new byte[50];
            int iCount = in.read(bytes);
            if (iCount != -1) {
                str = new String(bytes, 0, iCount);
            }
            in.close();
        } catch (Exception e) {
            Log.i("Illumination", "error!");
        }
        return str;
    }

    private void initControl() {
        HardwareControler.Open();
        HardwareControler.BuzzerSetValue(0, 1);      //关闭蜂鸣器
        HardwareControler.MotorSetValue(1, 0);       //关闭风扇
    }

    private void initView() {
        //todo 获取视图实例
        btn_bd_led = findViewById(R.id.btn_bd_led);
        btn_bd_tra = findViewById(R.id.btn_bd_tra);

        txt_lr_sun = (TextView) findViewById(R.id.txt_lr_sun);
        txt_lr_temp = (TextView) findViewById(R.id.txt_lr_temp);
        txt_lr_humi = (TextView) findViewById(R.id.txt_lr_humi);
        txt_lr_shaker = (TextView) findViewById(R.id.txt_lr_shaker);

        txt_bd_sun = (TextView) findViewById(R.id.txt_bd_sun);
        txt_bd_temp = (TextView) findViewById(R.id.txt_bd_temp);
        txt_bd_humi = (TextView) findViewById(R.id.txt_bd_humi);

        btn_alarm = (Button) findViewById(R.id.btn_ctl_alarm);
        btn_alarm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bAlarmStatus) {
                    AlarmOff();
                } else {
                    AlarmOn();
                }
            }
        });
        btn_curtain_open = (Button) findViewById(R.id.btn_ctl_curtain_open);
        btn_curtain_open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HardwareControler.StepperSetValue(1, 8);
            }
        });
        btn_curtain_close = (Button) findViewById(R.id.btn_ctl_curtain_close);
        btn_curtain_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HardwareControler.StepperSetValue(0, 8);
            }
        });
        btn_lights = (Button) findViewById(R.id.btn_ctl_lights);
        btn_lights.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bLightStatus) {
                    LightOff();
                } else {
                    LightOn();
                }
            }
        });

        //todo 绑定led事件
        btn_bd_led.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bBdLed) {
                    bd_led_off();
                } else {
                    bd_led_on();
                }
            }
        });

        //todo 绑定事件 tra
        btn_bd_tra.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bBdTra) {
                    bd_tra_off();
                } else {
                    bd_tra_on();
                }
            }
        });

        btn_fan = (Button) findViewById(R.id.btn_ctl_fan);
        btn_fan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bFanStatus) {
                    FanOff();
                } else {
                    FanOn();
                }
            }
        });

        btn_in = (Button) findViewById(R.id.btn_mode_in);
        btn_out = (Button) findViewById(R.id.btn_mode_out);

        check_shaker = (CheckBox) findViewById(R.id.check_shaker);
        check_humi = (CheckBox) findViewById(R.id.check_humi);

        edit_openFan = (EditText) findViewById(R.id.edit_open);
        edit_closeFan = (EditText) findViewById(R.id.edit_close);


        spData = getSharedPreferences("spData", Activity.MODE_PRIVATE);
        String cShaker = spData.getString("SHAKER", "false");
        String cHumi = spData.getString("HUMI", "false");
        if (cShaker.equals("true")) {
            check_shaker.setChecked(true);
        } else {
            check_shaker.setChecked(false);
        }
        if (cHumi.equals("true")) {
            check_humi.setChecked(true);
        } else {
            check_humi.setChecked(false);
        }

        String aOpen = spData.getString("OPEN", "80");
        String aClose = spData.getString("CLOSE", "40");
        edit_openFan.setText(aOpen);
        edit_closeFan.setText(aClose);

        check_shaker.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    SharedPreferences.Editor editor = spData.edit();
                    editor.putString("SHAKER", "true");
                    editor.commit();
                } else {
                    SharedPreferences.Editor editor = spData.edit();
                    editor.putString("SHAKER", "false");
                    editor.commit();
                }
            }
        });

        check_humi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    String strOpen = edit_openFan.getText().toString().trim();
                    String strClose = edit_closeFan.getText().toString().trim();
                    SharedPreferences.Editor editor = spData.edit();
                    if (!strOpen.equals("")) {
                        editor.putString("OPEN", strOpen);
                    }
                    if (!strClose.equals("")) {
                        editor.putString("CLOSE", strClose);
                    }
                    editor.putString("HUMI", "true");
                    editor.commit();
                } else {
                    SharedPreferences.Editor editor = spData.edit();
                    editor.putString("HUMI", "false");
                    editor.commit();
                }
            }
        });
    }

    private void AlarmOn() {
        bAlarmStatus = true;
        HardwareControler.BuzzerSetValue(1, 1);
        btn_alarm.setText("关闭");
        btn_alarm.setTextColor(Color.RED);
    }

    private void AlarmOff() {
        bAlarmStatus = false;
        HardwareControler.BuzzerSetValue(0, 1);
        btn_alarm.setText("打开");
        btn_alarm.setTextColor(Color.GREEN);
    }

    private void FanOn() {
        bFanStatus = true;
        HardwareControler.MotorSetValue(1, 1);
        btn_fan.setText("关闭");
        btn_fan.setTextColor(Color.RED);
    }

    private void FanOff() {
        bFanStatus = false;
        HardwareControler.MotorSetValue(1, 0);
        btn_fan.setText("打开");
        btn_fan.setTextColor(Color.GREEN);
    }

    private void LightOn() {
        sendControlCmd((byte) 0x01, (byte) 0xA5, (byte) 0x01, (byte) 0xff, (byte) 0xff, (byte) 0xff);
        bLightStatus = true;
        btn_lights.setText("关闭");
        btn_lights.setTextColor(Color.RED);
    }

    private void LightOff() {
        sendControlCmd((byte) 0x01, (byte) 0xA5, (byte) 0x01, (byte) 0x0, (byte) 0x0, (byte) 0x0);
        bLightStatus = false;
        btn_lights.setText("打开");
        btn_lights.setTextColor(Color.GREEN);
    }

    @Override
    public void onResume() {
        Service_Socket.strMessageFor = TAG;
        //todo 归家自动执行
        AlarmOff();  //关报警
        FanOff();    //关风扇
        HardwareControler.StepperSetValue(1, 8);    //开窗帘左
//        LightOn();   //开灯

        super.onResume();
    }

    @Override
    public void onDestroy() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            new AlertDialog.Builder(Main2Activity.this).setIcon(R.mipmap.ic_launcher).setTitle("退出").setMessage("确认退出吗？")
                    .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialoginterface, int i) {
                            finish();
                        }
                    }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialoginterface, int i) {
                }
            }).show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    //初始化
    private void initEnv() {
        //初始化数据缓冲标志
        bDataLock = false;
        iDataIn = 0;
        iDataOut = 0;

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

    //从缓冲区内读出有效数据
    private void processingData() {
        if (bDataLock == false) {
            bDataLock = true;
            int iPacketLen = 0;
            int i, iValidLen, iReadPos;
            while (iDataIn != iDataOut) {
                if (bytesDataRecBuff[iDataOut] == (byte) 0xAA) {// 判断是否为包头
                    iValidLen = validReceiveLen();// 包含有效数据长度
                    if (iValidLen < 8) { // 有效长度太短
                        bDataLock = false;
                        return;
                    }
                    if (iDataOut + 1 >= MAXBUFFLEN) {
                        iPacketLen = bytesDataRecBuff[iDataOut + 1 - MAXBUFFLEN] & 0xFF;
                    } else {
                        iPacketLen = bytesDataRecBuff[iDataOut + 1] & 0xFF;
                    }
                    if (iValidLen < iPacketLen) { // 包不完整
                        bDataLock = false;
                        return;
                    }
                    if (iPacketLen > 7 && iPacketLen < 40) { // 数据长度正常
                        iReadPos = iDataOut;
                        byte[] buf = new byte[iPacketLen];
                        for (i = 0; i < iPacketLen; i++) {// 读出包并进行校验和计算
                            buf[i] = bytesDataRecBuff[iReadPos++];
                            if (iReadPos >= MAXBUFFLEN)
                                iReadPos = 0;
                        }
                        if (checkSummationVerify(buf)) {
                            iDataOut = iReadPos;
                            processingPacket(buf);
                            bDataLock = false;
                            return;
                        }
                    }
                }
                iDataOut++;
                if (iDataOut >= MAXBUFFLEN) {
                    iDataOut = 0;
                }
            }
            bDataLock = false;
        }
    }

    //处理接收的数据包
    private void processingPacket(byte[] Packet) {
        //Luo Test
        Log.i(TAG + ":Packet", bytes2HexString(Packet));

        if (Packet[2] == FRAMETYPE_WSN) {
            if (Packet[4] == WSNCMD_INTERVALUPLOAD || Packet[4] == WSNCMD_TRIGGERUPLOAD) {
                byte bWsnType = Packet[5];
                byte bSensorType = Packet[7];
                byte bSensorIndex = Packet[8];

                switch (bWsnType) {
                    case WSNTYPE_ZIGBEE:
                        if (bSensorType == 0x1) {
                            int iValue = (Packet[10] & 0xFF) * 256 + (Packet[11] & 0xFF);
                            String str = String.valueOf(iValue) + " Lux";
                            txt_bd_sun.setText(str);
                        }
                        break;
                    case WSNTYPE_WIFI:
                        if (bSensorType == 0x5) {
                            int iValue = Packet[10] & 0xFF;
                            String str = "";
                            if (iValue == 0) {
                                str = "无震动";
                            } else if (iValue == 1) {
                                str = "有震动";
                            }
                            txt_lr_shaker.setText(str);

                            if (check_shaker.isChecked()) {
                                if (iValue == 0) {
                                    if (bAlarmStatus) {
                                        AlarmOff();
                                    }
                                } else if (iValue == 1) {
                                    if (!bAlarmStatus) {
                                        AlarmOn();
                                    }
                                }
                            }
                        }
                        break;
                    case WSNTYPE_BLUETOOTH:
                        if (bSensorType == 0x2) {
                            int iTemp = (Packet[10] & 0xFF) * 256 + (Packet[11] & 0xFF);
                            int iHumi = (Packet[12] & 0xFF) * 256 + (Packet[13] & 0xFF);
                            DecimalFormat formater = new DecimalFormat("#0.0");
                            String strTemp = formater.format(iTemp / 100.0) + " ℃";
                            txt_bd_temp.setText(strTemp);
                            String strHumi = formater.format(iHumi / 100.0) + " ％";
                            txt_bd_humi.setText(strHumi);

                            if (check_humi.isChecked()) {
                                String strOpen = edit_openFan.getText().toString().trim();
                                String strClose = edit_closeFan.getText().toString().trim();
                                int iOpen = 100;
                                int iClose = 0;
                                if (!strOpen.equals("")) {
                                    iOpen = Integer.parseInt(strOpen);
                                }
                                if (!strClose.equals("")) {
                                    iClose = Integer.parseInt(strClose);
                                }
                                if ((iHumi / 100) > iOpen) {
                                    if (!bFanStatus)
                                        FanOn();
                                } else if ((iHumi / 100) < iClose) {
                                    if (bFanStatus)
                                        FanOff();
                                }
                            }
                        }
                        break;
                }
            }
        }
    }

    //获取校验结果
    private boolean checkSummationVerify(byte[] bytes) {
        byte b = 0x00;
        for (int i = 0; i < bytes.length - 1; i++) {
            b += bytes[i];
        }
        if (bytes[bytes.length - 1] == b) {
            return true;
        }
        return false;
    }

    //设置校验位
    private void setSummationVerify(byte[] bytes) {
        byte b = 0x00;
        for (int i = 0; i < bytes.length - 1; i++) {
            b += bytes[i];
        }
        bytes[bytes.length - 1] = b;
    }

    //二进制数组转字符串
    private String bytes2HexString(byte[] bytes) {
        String ret = "";
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(bytes[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            ret += hex.toUpperCase();
        }
        return ret;
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

    //发送控制命令(带一个参数)
    private void sendControlCmd(byte bWsnType, byte bCtrlerType, byte bIndex, byte bValue) {
        byte[] bytes = new byte[12];
        bytes[0] = (byte) 0xAA;
        bytes[1] = (byte) bytes.length;
        bytes[2] = (byte) 0x02;
        bytes[3] = (byte) 0x00;
        bytes[4] = (byte) 0x0F;
        bytes[5] = bWsnType;
        bytes[6] = (byte) 0x00;
        bytes[7] = bCtrlerType;
        bytes[8] = bIndex;
        bytes[9] = (byte) 0x01;
        bytes[10] = bValue;
        setSummationVerify(bytes);
        mService.socketSend(bytes);
    }

    //发送控制命令(带两个参数)
    private void sendControlCmd(byte bWsnType, byte bCtrlerType, byte bIndex, byte bValue1, byte bValue2) {
        byte[] bytes = new byte[13];
        bytes[0] = (byte) 0xAA;
        bytes[1] = (byte) bytes.length;
        bytes[2] = (byte) 0x02;
        bytes[3] = (byte) 0x00;
        bytes[4] = (byte) 0x0F;
        bytes[5] = bWsnType;
        bytes[6] = (byte) 0x00;
        bytes[7] = bCtrlerType;
        bytes[8] = bIndex;
        bytes[9] = (byte) 0x02;
        bytes[10] = bValue1;
        bytes[11] = bValue2;
        setSummationVerify(bytes);
        mService.socketSend(bytes);
    }

    //发送控制命令(带三个参数)
    private void sendControlCmd(byte bWsnType, byte bCtrlerType, byte bIndex, byte bValue1, byte bValue2, byte bValue3) {
        byte[] bytes = new byte[14];
        bytes[0] = (byte) 0xAA;
        bytes[1] = (byte) bytes.length;
        bytes[2] = (byte) 0x02;
        bytes[3] = (byte) 0x00;
        bytes[4] = (byte) 0x0F;
        bytes[5] = bWsnType;
        bytes[6] = (byte) 0x00;
        bytes[7] = bCtrlerType;
        bytes[8] = bIndex;
        bytes[9] = (byte) 0x03;
        bytes[10] = bValue1;
        bytes[11] = bValue2;
        bytes[12] = bValue3;
        setSummationVerify(bytes);
        mService.socketSend(bytes);
    }
}
