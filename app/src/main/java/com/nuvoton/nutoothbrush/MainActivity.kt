package com.nuvoton.nutoothbrush

import android.Manifest
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.materialdialogs.list.updateListItems
import com.nuvoton.nuisptool_android.Util.DialogTool
import com.nuvoton.nuisptool_android.Util.HEXTool
import com.nuvoton.nutoothbrush.ble.BluetoothLeData
import com.nuvoton.nutoothbrush.ble.BluetoothLeDataManager
import com.nuvoton.nutoothbrush.util.Log
import com.nuvoton.nutoothbrush.util.PermissionManager
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import android.animation.ValueAnimator

import android.animation.ValueAnimator.AnimatorUpdateListener
import com.nuvoton.nutoothbrush.CMDManager.bwc
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private var TAG = "MainActivity"

    private var _deviceID = ""
    //BLE
    private val _bdm = BluetoothLeDataManager.getInstance()
    public var WRITE_BC: BluetoothLeData.CharacteristicData? = null
    public var NOTIFY_BC: BluetoothLeData.CharacteristicData? = null
    public var BLE_DATA: BluetoothLeData? = null
    //SCAN
    private var _scanResultArray: ArrayList<ScanResult> = ArrayList<ScanResult>()
    private var _scanResultDeviceArray: ArrayList<String> = ArrayList<String>()
    private lateinit var _alertDialog: MaterialDialog
    private var _tempMode = 1
    private var _tempBleState = BluetoothProfile.STATE_DISCONNECTED

    private lateinit var _radioButton_Mode1: RadioButton
    private lateinit var _radioButton_Mode2: RadioButton
    private lateinit var _radioButton_Mode3: RadioButton
    private lateinit var _radioButton_Mode4: RadioButton
    private lateinit var _connectDeviceButton: Button
    private lateinit var _historyButton: Button
    private lateinit var button: Button
    private var _radioButtonList: Array<RadioButton> = arrayOf()
    private lateinit var _powerSeekBar: SeekBar
    private lateinit var _timeSeekBar: SeekBar
    private lateinit var _displayPower :TextView
    private lateinit var _displayTime :TextView
    private lateinit var _textView_bleStuts :TextView
    private lateinit var _textView_Battery :TextView
    private lateinit var _textView_device_name :TextView
    private lateinit var _textView_history :TextView
    private lateinit var _textView_diffDays :TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        BluetoothLeDataManager.context = this

        button = findViewById<View>(R.id.button) as Button
        button.setOnClickListener {
            if(WRITE_BC != null){
                WRITE_BC!!.write("ID",bwc)
            }
        }
        _textView_diffDays  = findViewById<View>(R.id.textView_diffDays) as TextView
        _textView_history = findViewById<View>(R.id.textView_history) as TextView
        _textView_bleStuts = findViewById<View>(R.id.textView_bleStuts) as TextView
        _textView_Battery = findViewById<View>(R.id.textView_Battery) as TextView
        _textView_device_name = findViewById<View>(R.id.textView_device_name) as TextView
        _connectDeviceButton = findViewById<View>(R.id.BleButton) as Button
        _connectDeviceButton.setOnClickListener(onConnectClickButton)
        _historyButton = findViewById<View>(R.id.button_history) as Button
        _historyButton.setOnClickListener(onHistoryClickButton)
        _displayTime = findViewById<View>(R.id.display_sec) as TextView
        _displayPower = findViewById<View>(R.id.display_power) as TextView
        _powerSeekBar = findViewById<View>(R.id.seekBar_Power) as SeekBar
        _powerSeekBar.setOnSeekBarChangeListener(SeekBarListener)
        _timeSeekBar = findViewById<View>(R.id.seekBar_Time) as SeekBar
        _timeSeekBar.setOnSeekBarChangeListener(SeekBarListener)
        _radioButton_Mode1 = findViewById<View>(R.id.radioButton_Mode1) as RadioButton
        _radioButton_Mode1.setOnCheckedChangeListener(CompoundButtonOnCheckedChangeListener)
        _radioButton_Mode1.isChecked = true
        _radioButton_Mode2 = findViewById<View>(R.id.radioButton_Mode2) as RadioButton
        _radioButton_Mode2.setOnCheckedChangeListener(CompoundButtonOnCheckedChangeListener)
        _radioButton_Mode3 = findViewById<View>(R.id.radioButton_Mode3) as RadioButton
        _radioButton_Mode3.setOnCheckedChangeListener(CompoundButtonOnCheckedChangeListener)
        _radioButton_Mode4 = findViewById<View>(R.id.radioButton_Mode4) as RadioButton
        _radioButton_Mode4.setOnCheckedChangeListener(CompoundButtonOnCheckedChangeListener)
        _radioButtonList = arrayOf(_radioButton_Mode1,_radioButton_Mode2,_radioButton_Mode3,_radioButton_Mode4)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestMultiplePermissions.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        }else{
            runOnUiThread {
                val pm = PermissionManager(this)
                val permissionArray = ArrayList<PermissionManager.PermissionType>()
                permissionArray.add(PermissionManager.PermissionType.GPS)
                permissionArray.add(PermissionManager.PermissionType.BLUETOOTH)
                permissionArray.add(PermissionManager.PermissionType.BLUETOOTH_SCAN)
                permissionArray.add(PermissionManager.PermissionType.BLUETOOTH_CONNECT)

                pm.selfPermission("權限", permissionArray)
            }
        }

        this.setUI(BluetoothProfile.STATE_DISCONNECTED)
    }

    private fun setUI(bleState:Int){

        _tempBleState = bleState

        runOnUiThread {
            _timeSeekBar.max = 600
            _timeSeekBar.min = 1
            _powerSeekBar.max = 100
            _powerSeekBar.min = 1

            CMDManager.getlastHistory { date, sec, displayText ->
                runOnUiThread {
                    _textView_history.setText(displayText)
                }
            }

            when (bleState) {
                BluetoothProfile.STATE_CONNECTING -> {
                    _timeSeekBar.isEnabled = false
                    _powerSeekBar.isEnabled = false
                    _connectDeviceButton.setText("取消")
                    _textView_bleStuts.setText("連線狀態：連線中")
                    _textView_bleStuts.setTextColor(this.getColor(R.color.nuTextOrange))
                }
                BluetoothProfile.STATE_CONNECTED -> {
                    _timeSeekBar.isEnabled = true
                    _powerSeekBar.isEnabled = true
                    _connectDeviceButton.setText("中斷連線")
                    _textView_bleStuts.setText("連線狀態：已連線")
                    _textView_bleStuts.setTextColor(this.getColor(R.color.nuTextBlue))
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _timeSeekBar.isEnabled = false
                    _powerSeekBar.isEnabled = false
                    _textView_Battery.setText("？")
                    _connectDeviceButton.setText("藍芽連線")
                    _textView_bleStuts.setText("連線狀態：未連線")
                    _textView_bleStuts.setTextColor(this.getColor(R.color.nuTextRed))



                    if (CMDManager.setAutoReConnect == true) {
                        runOnUiThread {
                            doReConnect()
                        }
                    }

                }
            }
        }

    }


    private val requestMultiplePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d("test006", "${it.key} = ${it.value}")
            }
    }

    object private val onHistoryClickButton = View.OnClickListener {

//        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_in_left);
        Log.i(TAG, "onHistoryClickButton:"+BLE_DATA?.isConnect  +" _deviceID:" +_deviceID)

        if(BLE_DATA?.isConnect != true || _deviceID ==""){
            DialogTool.showAlertDialog(this,"未連接","查看使用紀錄前,請先連線.",true,false,null)
            return@OnClickListener
        }

        val intent = Intent(applicationContext, HistoryActivity::class.java).apply {
        }
        startActivity(intent)
    }

    private val SeekBarListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seek: SeekBar,progress: Int, fromUser: Boolean) {
            //拖曳途中觸發事件，回傳參數 progress 告知目前拖曳數值
            if(seek == _powerSeekBar){
                _displayPower.setText(""+progress+" ％")
            }
            if(seek == _timeSeekBar){
                _displayTime.setText(""+progress+" 秒")
            }
        }

        override fun onStartTrackingTouch(seek: SeekBar) {
            //開始拖曳時觸發事件
        }

        override fun onStopTrackingTouch(seek: SeekBar) {
            //停止拖曳時觸發事件

            if(seek == _powerSeekBar){
                Toast.makeText(this@MainActivity,"設定強度：" + seek.progress + " %",Toast.LENGTH_SHORT).show()
            }
            if(seek == _timeSeekBar){
                Toast.makeText(this@MainActivity,"設定時間：" + seek.progress + " 秒",Toast.LENGTH_SHORT).show()
            }

            if(WRITE_BC != null){
                CMDManager.sendCMD_Set_Mode(WRITE_BC!!,_powerSeekBar.progress,_timeSeekBar.progress,_tempMode)
            }
        }
    }

    /**
     * on CompoundButtonOnCheckedChangeListener
     */
    private val CompoundButtonOnCheckedChangeListener = CompoundButton.OnCheckedChangeListener { compoundButton, b ->
            if (b == false) return@OnCheckedChangeListener
            when (compoundButton) {
                _radioButton_Mode1 -> {
                    Log.i(TAG, "onClickButton:_radioButton_Mode1")
                    _tempMode = 1
                }
                _radioButton_Mode2 -> {
                    Log.i(TAG, "onClickButton:_radioButton_Mode2")
                    _tempMode = 2
                }
                _radioButton_Mode3 -> {
                    Log.i(TAG, "onClickButton:_radioButton_Mode3")
                    _tempMode = 3
                }
                _radioButton_Mode4 -> {
                    Log.i(TAG, "onClickButton:_radioButton_Mode4")
                    _tempMode = 4
                }
            }

            for (rb in _radioButtonList) {
                if (compoundButton != rb) {
                    rb.isChecked = false
                }
            }

            CMDManager.getModeInfo(_tempMode, callback = { power , sec ->
                if(power == 0 || sec == 0){
                    when (_tempMode) { //預設值
                        1 ->{
                            _powerSeekBar.progress = 98
                            _timeSeekBar.progress = 120
                        }
                        2 ->{
                            _powerSeekBar.progress = 92
                            _timeSeekBar.progress = 90
                        }
                        3 ->{
                            _powerSeekBar.progress = 95
                            _timeSeekBar.progress = 60
                        }
                        4 ->{
                            _powerSeekBar.progress = 90
                            _timeSeekBar.progress = 30
                        }
                    }
                }else{
                    _powerSeekBar.progress = power
                    _timeSeekBar.progress = sec
                }

                if(WRITE_BC != null){
                    CMDManager.sendCMD_Set_Mode(WRITE_BC!!,_powerSeekBar.progress,_timeSeekBar.progress,_tempMode)
                }

            })
        }

    private val onConnectClickButton = View.OnClickListener {
        Log.i(TAG, "onConnectClickButton")

        if(_tempBleState == BluetoothProfile.STATE_CONNECTING){
            CMDManager.setAutoReConnect = false
            BLE_DATA?.setDisClose()
            this.setUI(BluetoothProfile.STATE_DISCONNECTED)
            return@OnClickListener
        }

        if(BLE_DATA?.isConnect == true){
            CMDManager.setAutoReConnect = false
            BLE_DATA?.setDisClose()
            this.setUI(BluetoothProfile.STATE_DISCONNECTED)
            return@OnClickListener
        }
        ScanBleDevice()
    }

    /**
     *  打開ＢＬＥ搜尋
     */
    private fun ScanBleDevice() {

        if (_bdm.isBluetoothEnabled(BluetoothLeDataManager.context) == false) {
            Toast.makeText(BluetoothLeDataManager.context, "ble not supported", Toast.LENGTH_SHORT)
                .show();
            return
        }
        if (_bdm.isGPSEnabled(BluetoothLeDataManager.context) == false) {
            Toast.makeText(BluetoothLeDataManager.context, "GPS not supported", Toast.LENGTH_SHORT)
                .show();
            return
        }

        _scanResultDeviceArray.clear()
        _scanResultArray.clear()

        _alertDialog = MaterialDialog(BluetoothLeDataManager.context)
            .cancelOnTouchOutside(false)
            .cancelable(false)
            .title(text = "BLE Device Scan...")
            .listItems(items = _scanResultDeviceArray) { dialog, index, text ->
                //TODO:點擊ＢＬＥ裝置事件

                if (index >= _scanResultArray.size) return@listItems

//                _bleDevice!!.setText("BLE Device: " + _scanResultArray.get(index).scanRecord!!.deviceName)
//                Log.d(TAG, "onOptionsItemSelected:" + _scanResultArray.get(index).scanRecord!!.deviceName + " " + _scanResultArray.get(index).device.uuids)
                BLE_DATA = _bdm.getBluetoothLeData(BluetoothLeDataManager.context, _scanResultArray.get(index).device.address)

                _bdm.scanLeDevice(false, BluetoothLeDataManager.context, scanCallback) //停止搜尋
                this.connectBle(bleData = BLE_DATA!!)//藍芽連線

                _alertDialog.dismiss()
            }
            .negativeButton(null, "cancel") { materialDialog: MaterialDialog? ->
                Log.d(TAG, "ScanBleDevice Cancel")
                _bdm.scanLeDevice(false, BluetoothLeDataManager.context, scanCallback) //停止搜尋
                _alertDialog.dismiss()

            }
        _alertDialog.show()

        _bdm.scanLeDevice(true, BluetoothLeDataManager.context, scanCallback)

    }

    /**
     * ＢＬＥ搜尋結果
     */
    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            Log.d(TAG, "onScanResult  callbackType:$callbackType   result:$result")

            var displayName = result.scanRecord!!.deviceName + "\n" + result.device.address

            if (result.scanRecord == null) return

            if (!_scanResultArray.contains(result) && !_scanResultDeviceArray.contains(displayName)) {
                if (result.scanRecord!!.deviceName != null) {
                    _scanResultArray.add(result)
                    Log.d(TAG, "onScanResult  deviceName:" + result.scanRecord!!.deviceName)
                    _scanResultDeviceArray.add(displayName)
                    _alertDialog.updateListItems(items = _scanResultDeviceArray)
                }
            }

        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            Log.d(TAG, "results:$results")
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.d(TAG, "errorCode:$errorCode")
        }
    }

    /**
     * ＢＬＥ藍芽連線
     */
    private fun connectBle(bleData: BluetoothLeData) {

        if (bleData == null) {
            return
        }

        bleData.setOnStateChangeListener(onStateChangeListener)

        bleData.connectLeDevice {
            Log.i("MainActivity", "connectLeDevice:" + it)
            if (it != true) {
                onStateChangeListener.onStateChange(bleData.getbleMacAddress(), 0, 0)
                return@connectLeDevice
            }
            for (bs in bleData.servicesDataArray) {
                for (bc in bs.characteristicDataArray) {
                    Log.i("MainActivity", "characteristic:" + bc.uuid)

                    //專門用來寫入之特徵
                    if (bc.uuid.indexOf("50515253-5455-5657-5859-5a5b5c5d5e5f") > -1) {
                        WRITE_BC = bc
                    }

                    //專門用來監聽之特徵
                    //30313233-3435-3637-3839-3a3b3c3d3e3f  >> 2.x SDK
                    //0000fa02-0000-1000-8000-00805f9b34fb  >> 1.X SDK
                    if (bc.uuid.indexOf("0000fa02-0000-1000-8000-00805f9b34fb") > -1) {
                        NOTIFY_BC = bc
                        bc.setNotify(true, myNotifyListener)
                        Log.i("MainActivity", "setNotify:" + bc.uuid)
                    }
                    if (bc.uuid.indexOf("30313233-3435-3637-3839-3a3b3c3d3e3f") > -1) {
                        NOTIFY_BC = bc
                        bc.setNotify(true, myNotifyListener)
                        Log.i("MainActivity", "setNotify:" + bc.uuid)
                    }
                }
            }
        }
    }

    /***
     * 收通知的地方
     */
    private val myNotifyListener = BluetoothLeData.notifCallBack { bleMAC, UUID, Value ->

        val date = Date()
        val formatter = SimpleDateFormat("mm:ss.SSS")
        val disPlayValue = String(Value, StandardCharsets.UTF_8)
        Log.i("myNotifyListener","disPlayValue:" + disPlayValue )

        val v : ByteArray = byteArrayOf(Value[0])

        when(String(v, StandardCharsets.UTF_8)){
            "A" ->{ //ID
                _deviceID = disPlayValue
                CMDManager.DeviceID = disPlayValue
                runOnUiThread {
                    _textView_device_name.setText("裝置名稱："+_deviceID)
                }

                //TODO 讀取使用註冊日期
                val date = CMDManager.getDeviceDate(_deviceID)

                val today_date = Date(System.currentTimeMillis())
                val today_long :Long = today_date.time
                val lastday_long :Long = date.time
                val diff: Long = today_long - lastday_long
                val diffDays = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)
                _textView_diffDays.setText(""+(90-diffDays))

            }
            "B" ->{ //Battery
                //TODO 顯示電池電量
                val vBattery : ByteArray = byteArrayOf(Value[1],Value[2],Value[3])
                val vbs = String(vBattery, StandardCharsets.UTF_8)

                runOnUiThread {
                    _textView_Battery.setText((vbs.toInt()).toString())
                }
            }

            "C" ->{ //ChangeMode
                //TODO 切換模式通知
                val c : ByteArray = byteArrayOf(Value[1])
                val modeIndex = String(c, StandardCharsets.UTF_8).toInt()
                runOnUiThread {
                    when(modeIndex){
                        1 ->{
                            _radioButton_Mode1.isChecked = true
                            CompoundButtonOnCheckedChangeListener.onCheckedChanged(_radioButton_Mode1,false)
                        }
                        2 ->{
                            _radioButton_Mode2.isChecked = true
                            CompoundButtonOnCheckedChangeListener.onCheckedChanged(_radioButton_Mode2,false)
                        }
                        3 ->{
                            _radioButton_Mode3.isChecked = true
                            CompoundButtonOnCheckedChangeListener.onCheckedChanged(_radioButton_Mode3,false)
                        }
                        4 ->{
                            _radioButton_Mode4.isChecked = true
                            CompoundButtonOnCheckedChangeListener.onCheckedChanged(_radioButton_Mode4,false)
                        }
                    }
                }
            }

            "O" ->{
                //TODO 存下刷牙紀錄

                val t : ByteArray = byteArrayOf(Value[1],Value[2],Value[3])
                CMDManager.addHistoryData(_deviceID,String(t, StandardCharsets.UTF_8))
                CMDManager.notifyDataSetChangedDisplaylist()
                CMDManager.getlastHistory { date, sec, displayText ->
                    _textView_history.setText(displayText)
                }
                if(CMDManager.Array_Adapter != null){
                    runOnUiThread {
                        CMDManager.Array_Adapter!!.notifyDataSetChanged()
                    }
                }

            }
        }
        //TODO 更新最後紀錄
        CMDManager.getlastHistory { date, sec, displayText ->
            runOnUiThread {
                _textView_history.setText(displayText)
            }
        }
    }

    /**
     * 監聽ＢＬＥ連線變化
     */
    private var onStateChangeListener = BluetoothLeData.onStateChange { MAC, status, newState ->

        Log.i("onStateChangeListener","MAC:" + MAC + "  status:" + status + "  newState:" + newState )


        if (newState == BluetoothProfile.STATE_CONNECTING) {
        }

        if (newState == BluetoothProfile.STATE_CONNECTED) {

            CMDManager.saveBleMAC(BLE_DATA!!)
            CMDManager.setAutoReConnect = true

            thread {
                while (WRITE_BC == null){

                }
                if(WRITE_BC != null){
                    Thread.sleep(1000)
                    WRITE_BC!!.write("ID",bwc)
                }
            }


        }

        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            WRITE_BC = null
            BLE_DATA?.setDisClose()
        }

        runOnUiThread {
            this.setUI(newState)
        }
    }

    private fun doReConnect(){
        if(BLE_DATA == null) {
            val bleMAC = CMDManager.getLastBleMAC()
            if(bleMAC == ""){
                return
            }
            BLE_DATA = _bdm.getBluetoothLeData(BluetoothLeDataManager.context,bleMAC)
            if(CMDManager.setAutoReConnect == true){
                BLE_DATA?.setDisClose()
                setUI(BluetoothProfile.STATE_CONNECTING)
                this.connectBle(bleData = BLE_DATA!!)//藍芽連線
            }
            return
        }
        if(CMDManager.setAutoReConnect == true){
            BLE_DATA?.setDisClose()
            setUI(BluetoothProfile.STATE_CONNECTING)
            this.connectBle(bleData = BLE_DATA!!)//藍芽連線
        }
    }


}