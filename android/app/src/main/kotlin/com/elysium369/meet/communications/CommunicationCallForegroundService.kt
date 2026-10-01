package com.elysium369.meet.communications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Keeps an explicitly initiated/accepted call audible with a visible microphone notification. */
@AndroidEntryPoint
class CommunicationCallForegroundService:Service() {
 @Inject lateinit var transport:ElysiumCallTransport
 private var lease:String?=null
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  val call=intent?.getStringExtra(CALL_ID) ?: run {stopSelf();return START_NOT_STICKY}
  if(intent.action==END) {
   if(lease==call && transport.isCurrentForegroundLease(call)) {transport.finishForegroundCall(call);stopSelfResult(startId)}
   return START_NOT_STICKY
  }
  if(!transport.isCurrentForegroundLease(call)) {if(lease==null) stopSelfResult(startId);return START_NOT_STICKY}
  lease=call
  val manager=getSystemService(NotificationManager::class.java)
  if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel(CHANNEL,"Llamadas de voz Elysium",NotificationManager.IMPORTANCE_LOW))
  val close=PendingIntent.getService(this,0,Intent(this,CommunicationCallForegroundService::class.java).setAction(END).putExtra(CALL_ID,call),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val launch=packageManager.getLaunchIntentForPackage(packageName)
  val notification=NotificationCompat.Builder(this,CHANNEL)
   .setSmallIcon(android.R.drawable.ic_btn_speak_now)
   .setContentTitle("Llamada de voz Elysium")
   .setContentText("Llamada de voz · puedes finalizarla aquí")
   .setOngoing(true).setCategory(NotificationCompat.CATEGORY_CALL)
   .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Finalizar",close)
  if(launch!=null) notification.setContentIntent(PendingIntent.getActivity(this,1,launch,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
  startForeground(NOTIFICATION,notification.build())
  return START_NOT_STICKY
 }
 override fun onDestroy() {
  lease?.let(transport::finishForegroundCall)
  super.onDestroy()
 }
 companion object {
  const val CALL_ID="call_id"
  private const val END="com.elysium369.meet.END_COMMUNICATION_CALL"
  private const val CHANNEL="elysium_voice_calls"
  private const val NOTIFICATION=36928
 }
}
