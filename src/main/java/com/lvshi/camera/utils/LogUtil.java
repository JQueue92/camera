package com.lvshi.camera.utils;

import android.content.Context;
import android.util.Log;

import com.example.camera.BuildConfig;


public class LogUtil {
	private static final String TAG = "LogUtil/" ;
	private static boolean logEnable = BuildConfig.DEBUG ;

	public static void switchLog(){
		logEnable = !logEnable;
		System.out.println("enableLog:"+ logEnable);
	}
	
	public static void d(String tag,String msg){
		if(logEnable){
			Log.d(TAG+tag, msg);
		}
	}
	
	public static void d(String msg){
		if(logEnable){
			Log.d(TAG, msg);
		}
	}
	
	public static void e(String tag,Throwable e){
		if(logEnable){
			Log.e(TAG+tag, "Exception", e);
		}
	}

	public static void e(Throwable e){
		if(logEnable){
			Log.e(TAG, "Exception", e);
		}
	}

	public static void e(String msg){
		if(logEnable){
			Log.e(TAG, msg);
		}
	}

	public static void e(String tag,String msg){
		if(logEnable){
			Log.e(TAG+tag, msg);
		}
	}

	public static void e(Error e){
		if(logEnable){
			Log.e(TAG, "Error", e);
		}
	}
	
	public static void sout(String msg){
		if(logEnable){
			System.out.println(msg);
		}
	}

	public static boolean isLogEnable(){
		return logEnable;
	}
}
