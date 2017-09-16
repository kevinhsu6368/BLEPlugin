package generalplus.com.blespeechplugin;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.unity3d.player.UnityPlayer;

/**
 * Created by stringchiang on 2016/10/12.
 */

public class PermissionGranter {
	private final static String UNITY_CALLBACK_GAMEOBJECT_NAME = "NoodlePermissionGranter";
	private final static String UNITY_CALLBACK_METHOD_NAME = "permissionRequestCallbackInternal";
	private final static String PERMISSION_GRANTED = "PERMISSION_GRANTED"; // this will be an arg to the above method
	private final static String PERMISSION_DENIED = "PERMISSION_DENIED";

	public static String getPermissionStringFromEnumInt(int permissionEnum) throws Exception
	{
		switch (permissionEnum)
		{
			case 0:
				return Manifest.permission.ACCESS_COARSE_LOCATION;
			// "and the rest is still unwritten" - Natasha Bedingfield
		}
		Log.e("NoodlePermissionGranter", "Error. Unknown permissionEnum " + permissionEnum);
		throw new Exception(String.format("Error. Unknown permissionEnum %d",permissionEnum));
	}

	public static void grantPermission(Activity currentActivity, int permissionEnum)
	{
		// permission enum must match ordering in NoodlePermissionGranter.cs
		final Activity act = currentActivity;
		Log.i("NoodlePermissionGranter","grantPermission " + permissionEnum) ;
		if (Build.VERSION.SDK_INT < 23) {
			Log.i("NoodlePermissionGranter","Build.VERSION.SDK_INT < 23 (" + Build.VERSION.SDK_INT+")");
			UnityPlayer.UnitySendMessage(UNITY_CALLBACK_GAMEOBJECT_NAME, UNITY_CALLBACK_METHOD_NAME, PERMISSION_GRANTED);
			return;
		}

		try
		{
			final int PERMISSIONS_REQUEST_CODE = permissionEnum;
			final String permissionFromEnumInt = getPermissionStringFromEnumInt(permissionEnum);
			if (currentActivity.checkCallingOrSelfPermission(permissionFromEnumInt) == PackageManager.PERMISSION_GRANTED) {
				Log.i("NoodlePermissionGranter", "already granted");
				UnityPlayer.UnitySendMessage(UNITY_CALLBACK_GAMEOBJECT_NAME, UNITY_CALLBACK_METHOD_NAME, PERMISSION_GRANTED);
				return;
			}

			final FragmentManager fragmentManager = currentActivity.getFragmentManager();
			final Fragment request = new Fragment() {

				@Override public void onStart()
				{
					super.onStart();
					Log.i("NoodlePermissionGranter","fragment start");
					String[] permissionsToRequest = new String [] {permissionFromEnumInt};
					Log.i("NoodlePermissionGranter","fragment start " + permissionsToRequest[0]);
					requestPermissions(permissionsToRequest, PERMISSIONS_REQUEST_CODE);
				}

				@Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
				{
					Log.i("NoodlePermissionGranter", "onRequestPermissionsResult");
					if (requestCode != PERMISSIONS_REQUEST_CODE)
						return;

					if (grantResults.length > 0
						&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
						// permission was granted, yay! Do the
						// contacts-related task you need to do.
						Log.i("NoodlePermissionGranter", PERMISSION_GRANTED);
						UnityPlayer.UnitySendMessage(UNITY_CALLBACK_GAMEOBJECT_NAME, UNITY_CALLBACK_METHOD_NAME, PERMISSION_GRANTED);
					} else {

						// permission denied, boo! Disable the
						// functionality that depends on this permission.
						Log.i("NoodlePermissionGranter",PERMISSION_DENIED);
						UnityPlayer.UnitySendMessage(UNITY_CALLBACK_GAMEOBJECT_NAME, UNITY_CALLBACK_METHOD_NAME, PERMISSION_DENIED);
					}


					FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
					fragmentTransaction.remove(this);
					fragmentTransaction.commit();

					// shouldBeOkayToStartTheApplicationNow();
				}
			};

			FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
			fragmentTransaction.add(0, request);
			fragmentTransaction.commit();
		}
		catch(Exception error)
		{
			Log.w("[NoodlePermissionGranter]", String.format("Unable to request permission: %s", error.getMessage()));
			UnityPlayer.UnitySendMessage(UNITY_CALLBACK_GAMEOBJECT_NAME, UNITY_CALLBACK_METHOD_NAME, PERMISSION_DENIED);
		}
	}
}
