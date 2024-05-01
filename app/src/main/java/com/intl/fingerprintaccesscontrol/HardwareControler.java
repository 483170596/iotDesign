package com.intl.fingerprintaccesscontrol;

public class HardwareControler {
	public static native int Open();

	public static native int Close();

	public static native int MatrixSetValue(int value1, int value2);

	public static native int TrafficSetValue(int value1, int value2);

	public static native int StepperSetValue(int value1, int value2);

	public static native int MotorSetValue(int value1, int value2);

	public static native int SegSetValue(int value1, int value2);

	public static native int BuzzerSetValue(int value1, int value2);

	public static native int RelaySetValue(int value1, int value2);

	public static native int GledSetValue(int value1, int value2);

	static {
		System.loadLibrary("dev_ctl");
	}
}