package com.nuvoton.nutoothbrush

import android.content.Context
import com.nuvoton.nutoothbrush.util.Log
import android.content.Context.MODE_PRIVATE

import android.content.SharedPreferences
import android.widget.ArrayAdapter
import com.nuvoton.nutoothbrush.ble.BluetoothLeData
import com.nuvoton.nutoothbrush.ble.BluetoothLeDataManager
import com.nuvoton.nutoothbrush.ble.BluetoothLeDataManager.context
import com.nuvoton.nutoothbrush.util.TinyDB
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


enum class Commands constructor(val value: String){
    CMD_GET_ID              ("ID"),
    CMD_GET_HISTORY 	    ("UT"),
    CMD_RESET_MODE1         ("M1R"),
    CMD_RESET_MODE2         ("M2R"),
    CMD_RESET_MODE3         ("M3R"),
    CMD_RESET_MODE4         ("M4R"),
    CMD_MODE1_SET           ("M1P000T000"),
}

object CMDManager {
    private var TAG = "CMDManager"

    public var setAutoReConnect = true
    public var isConnecting = false
    public var DeviceID = "WP Test"
    public var Array_Adapter: ArrayAdapter<*>? = null
    public val Displaylist : ArrayList<String> = ArrayList()

    public fun notifyDataSetChangedDisplaylist(context: Context){
        val l = CMDManager.getHistoryData(CMDManager.DeviceID)
        Displaylist.clear()
        if(l.size == 0){
            Displaylist.add(0,context.getString(R.string.Unkown_History_Text))

        }else{
            for(i in l.size-1 downTo 0){
                val s = l.get(i).split(",")
                val date = Date(s[0].toLong())
                val sec = s[1].toLong()
                var formatter = SimpleDateFormat("yyyy/MM/dd      hh:mm       ")
                var formattedDate = formatter.format(date)

                Displaylist.add(0,formattedDate+ formatDurationTime(sec))
            }
        }
    }

    fun  sendCMD_Set_Mode(WRITE_BC: BluetoothLeData.CharacteristicData,power_progress:Int,time_progress:Int,mode:Int){
        var cmd = "M1P098T120"
        var p_progress = ""
        var t_progress = ""
        if(power_progress == 100){
            p_progress = "100"
        }else{
            p_progress = "0"+power_progress
        }
        if(time_progress <100){
            t_progress = "0"+time_progress
        }else{
            t_progress = time_progress.toString()
        }
        cmd = "M" + mode + "P" + p_progress + "T"+ t_progress

        WRITE_BC!!.write(cmd,bwc)
        Log.i(TAG, "sendCMD_Set_Mode  CMD: "+cmd)
        CMDManager.saveModeInfo(mode,power_progress,time_progress)
    }

   private fun formatDurationTime(durationSeconds: Long): String {
        var hours = 0L
        var minutes = 0L
        var seconds = durationSeconds
        if (seconds >= 3600) {
            hours = seconds / 3600
            seconds -= hours * 3600
        }
        if (seconds >= 60) {
            minutes = seconds / 60
            seconds -= minutes * 60
        }
       if(minutes > 0){
           return "" + minutes +" "+context.getString(R.string.minute)+" "+ + seconds +" "+context.getString(R.string.Sec_Text)
       }else{
           return "" + seconds +" "+context.getString(R.string.Sec_Text)
       }
//        return Formatter().format("%1\$02d 分 %2\$02d 秒", minutes, seconds).toString()
    }

    fun toCMD_GetID(): ByteArray{
        return Commands.CMD_GET_ID.value.toByteArray()
    }

    fun toCMD_GetHistory(): ByteArray{
        return Commands.CMD_GET_HISTORY.value.toByteArray()
    }

    fun toCMD_SetMode(modeIndex:Int,power:Int,time:Int): ByteArray{

        val p = addZeroForNum(power.toString(),3)
        val t = addZeroForNum(time.toString(),3)

        val cmd = "M${modeIndex}P${p}T${t}"
        Log.i(TAG, "toCMD_SetMode:" + cmd)
        return cmd.toByteArray()
    }

    private fun addZeroForNum(s: String, strLength: Int): String {
        var str = s
        var strLen = str.length
        if (strLen < strLength) {
            while (strLen < strLength) {
                val sb = StringBuffer()
                sb.append("0").append(str) // 左補0
                // sb.append(str).append("0");//右補0
                str = sb.toString()
                strLen = str.length
            }
        }
        return str
    }

     fun addHistoryData(deviceID:String,sec:String){

         val s = sec.toIntOrNull()
         if (s == null){ return }

         val date = Date(System.currentTimeMillis())
         val millis :Long = date.time

         val tinyDB : TinyDB = TinyDB(context)
         val list =  tinyDB.getListString(deviceID+"History")

         list.add(0,millis.toString()+","+s)

         tinyDB.putListString(deviceID+"History",list)
         tinyDB.putString("lastDeviceID",deviceID)
    }

     fun getHistoryData(deviceID:String):ArrayList<String>{

         val tinyDB : TinyDB = TinyDB(context)

        return tinyDB.getListString(deviceID+"History")
    }

    fun getlastHistory(callback: (date: Date?, sec: String?,displayText:String) -> Unit){
        val tinyDB : TinyDB = TinyDB(context)
        val id = tinyDB.getString("lastDeviceID")
        if(id == ""){
            callback.invoke(null,null,"無紀錄")
            return
        }
        val list = tinyDB.getListString(id+"History")

        val s = list[0].split(",")
        val date = Date(s[0].toLong())
        val sec = s[1]
        var formatter = SimpleDateFormat("MM/dd   hh:mm   ")
        var formattedDate = formatter.format(date)

        callback.invoke(date,sec,formattedDate+sec+" 秒")
    }

    fun getDeviceDate(deviceID:String):Date{

        var tinyDB : TinyDB = TinyDB(context)
        var dateLong = tinyDB.getLong(deviceID+"Date")

        if(dateLong != 0.toLong()) {
            val myDate = Date(dateLong)
            return myDate
        }

        val date = Date(System.currentTimeMillis())
        val millis :Long = date.time
        tinyDB.putLong(deviceID+"Date",millis)
        val myDate = Date(millis)
        return myDate
    }

    fun saveModeInfo(mode:Int,power:Int,sec:Int){
        val tinyDB : TinyDB = TinyDB(context)
        tinyDB.putInt("power"+mode,power)
        tinyDB.putInt("sec"+mode,sec)
    }

    fun getModeInfo(mode:Int,callback:(power:Int,sec:Int)->Unit){

        val tinyDB : TinyDB = TinyDB(context)
        val p = tinyDB.getInt("power"+mode)
        val s = tinyDB.getInt("sec"+mode)
        callback.invoke(p,s)
    }

    fun saveBleMAC(bd: BluetoothLeData){
        var tinyDB : TinyDB = TinyDB(context)
        tinyDB.putString("BleMAC",bd.getbleMacAddress())
    }
    fun getLastBleMAC():String{
        var tinyDB : TinyDB = TinyDB(context)
        return tinyDB.getString("BleMAC")
    }

    val bwc = BluetoothLeData.writeCallBack { status ->
        Log.i("writeCallBack", ""+status)
    }
}