#include <jni.h>
#include <stdio.h>
#include <linux/ioctl.h>
#include <android/log.h>
#include <fcntl.h>
#include <unistd.h>

int fd_gled = 0;
int fd_buzzer = 0;
int fd_seg = 0;
int fd_triffic = 0;
int fd_matrix = 0;
int fd_motor = 0;
int fd_stepper = 0;
int fd_relay = 0;

char *DevGled = "/dev/leds_ctl";
char *DevBuzzer = "/dev/buzzer_ctl";
char *DevCled = "/dev/cled_ctl";
char *DevSeg = "/dev/seg_ctl";
char *DevTraffic = "/dev/expandleds_ctl";
char *DevMatrix = "/dev/matrix_ctl";
char *DevMotor = "/dev/motor_ctl";
char *DevStepper = "/dev/stepper_ctl";
char *DevRelay = "/dev/relay_ctl";

char *NameGled = "Gled";
char *NameBuzzer = "Buzzer";
char *NameSeg = "Seg";
char *NameTraffic = "expandleds_ctl";
char *NameMatrix = "MatrixLed";
char *NameMotor = "motor_ctl";
char *NameStepper = "Stepper";
char *NameRelay = "Relay";


jint Java_com_intl_fingerprintaccesscontrol_HardwareControler_Open(JNIEnv* env, jobject obj) {
	fd_gled = open(DevGled, 0);
	fd_buzzer = open(DevBuzzer, 0);
	fd_seg = open(DevSeg, 0);
	fd_triffic = open(DevTraffic, 0);
	fd_matrix = open(DevMatrix, 0);
	fd_motor = open(DevMotor, 0);
	fd_stepper = open(DevStepper, 0);
	fd_relay = open(DevRelay, 0);
	if (fd_gled < 0) {
		__android_log_print(ANDROID_LOG_INFO, NameGled, "Open device fail.");
	} else {
		__android_log_print(ANDROID_LOG_INFO, NameGled, "Open device success.");
	}
	if (fd_buzzer < 0) {
		__android_log_print(ANDROID_LOG_INFO, NameBuzzer, "Open device fail.");
	} else {
		__android_log_print(ANDROID_LOG_INFO, NameBuzzer, "Open device success.");
	}
	if (fd_seg < 0) {
		__android_log_print(ANDROID_LOG_INFO, NameSeg, "Open device fail.");
	} else {
		__android_log_print(ANDROID_LOG_INFO, NameSeg, "Open device success.");
	}
	if (fd_triffic < 0) {
		__android_log_print(ANDROID_LOG_INFO, NameTraffic, "Open device fail.");
	} else {
		__android_log_print(ANDROID_LOG_INFO, NameTraffic, "Open device success.");
	}
	if (fd_matrix < 0) {
		__android_log_print(ANDROID_LOG_INFO, NameMatrix, "Open device fail.");
	} else {
		__android_log_print(ANDROID_LOG_INFO, NameMatrix, "Open device success.");
	}
	if (fd_motor < 0) {
		__android_log_print(ANDROID_LOG_INFO, NameMotor, "Open device fail.");
	} else {
		__android_log_print(ANDROID_LOG_INFO, NameMotor, "Open device success.");
	}
	if (fd_stepper < 0) {
		__android_log_print(ANDROID_LOG_INFO, NameStepper, "Open device fail.");
	} else {
		__android_log_print(ANDROID_LOG_INFO, NameStepper, "Open device success.");
	}
	if (fd_relay < 0) {
		__android_log_print(ANDROID_LOG_INFO, NameRelay, "Open device fail.");
	} else {
		__android_log_print(ANDROID_LOG_INFO, NameRelay, "Open device success.");
	}
	return 0;
}

jint Java_com_intl_fingerprintaccesscontrol_HardwareControler_Close(JNIEnv* env, jobject obj) {
	close(fd_gled);
	close(fd_buzzer);
	close(fd_seg);
	close(fd_triffic);
	close(fd_matrix);
	close(fd_motor);
	close(fd_stepper);
	close(fd_relay);
	__android_log_print(ANDROID_LOG_INFO, NameGled, "Close device.");
	__android_log_print(ANDROID_LOG_INFO, NameBuzzer, "Close device.");
	__android_log_print(ANDROID_LOG_INFO, NameSeg, "Close device.");
	__android_log_print(ANDROID_LOG_INFO, NameTraffic, "Close device.");
	__android_log_print(ANDROID_LOG_INFO, NameMatrix, "Close device.");
	__android_log_print(ANDROID_LOG_INFO, NameMotor, "Close device.");
	__android_log_print(ANDROID_LOG_INFO, NameStepper, "Close device.");
	__android_log_print(ANDROID_LOG_INFO, NameRelay, "Close device.");
	return 0;
}

jint Java_com_intl_fingerprintaccesscontrol_HardwareControler_GledSetValue(JNIEnv* env, jobject obj, jint iValue1, jint iValue2) {
	ioctl(fd_gled, iValue1, iValue2);
	__android_log_print(ANDROID_LOG_INFO, NameGled, "GledSetValue %d, %d.", iValue1, iValue2);
	return 0;
}

jint Java_com_intl_fingerprintaccesscontrol_HardwareControler_MatrixSetValue(JNIEnv* env, jobject obj, jint iValue1, jint iValue2) {
	ioctl(fd_matrix, iValue1, iValue2);
	__android_log_print(ANDROID_LOG_INFO, NameMatrix, "SetValue %d, %d.", iValue1, iValue2);
	return 0;
}

jint Java_com_intl_fingerprintaccesscontrol_HardwareControler_TrafficSetValue(JNIEnv* env, jobject obj, jint iValue1, jint iValue2) {
	ioctl(fd_triffic, iValue1, iValue2);
	__android_log_print(ANDROID_LOG_INFO, NameTraffic, "SetValue %d, %d.", iValue1, iValue2);
	return 0;
}

jint Java_com_intl_fingerprintaccesscontrol_HardwareControler_StepperSetValue(JNIEnv* env, jobject obj, jint iValue1, jint iValue2) {
	ioctl(fd_stepper, iValue1, iValue2);
	__android_log_print(ANDROID_LOG_INFO, NameStepper, "SetValue %d, %d.", iValue1, iValue2);
	return 0;
}

jint Java_com_intl_fingerprintaccesscontrol_HardwareControler_MotorSetValue(JNIEnv* env, jobject obj, jint iValue1, jint iValue2) {
	ioctl(fd_motor, iValue1, iValue2);
	__android_log_print(ANDROID_LOG_INFO, NameMotor, "MotorSetValue %d, %d.", iValue1, iValue2);
	return 0;
}

jint Java_com_intl_fingerprintaccesscontrol_HardwareControler_SegSetValue(JNIEnv* env, jobject obj, jint iValue1, jint iValue2) {
	ioctl(fd_seg, iValue1, iValue2);
	__android_log_print(ANDROID_LOG_INFO, NameSeg, "SetValue %d, %d.", iValue1, iValue2);
	return 0;
}

jint Java_com_intl_fingerprintaccesscontrol_HardwareControler_BuzzerSetValue(JNIEnv* env, jobject obj, jint iValue1, jint iValue2) {
	ioctl(fd_buzzer, iValue1, iValue2);
	__android_log_print(ANDROID_LOG_INFO, NameBuzzer, "SetValue %d, %d.", iValue1, iValue2);
	return 0;
}


jint Java_com_intl_fingerprintaccesscontrol_HardwareControler_RelaySetValue(JNIEnv* env, jobject obj, jint iValue1, jint iValue2) {
	ioctl(fd_relay, iValue1, iValue2);
	__android_log_print(ANDROID_LOG_INFO, NameRelay, "SetValue %d, %d.", iValue1, iValue2);
	return 0;
}


