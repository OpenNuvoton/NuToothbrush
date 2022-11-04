package com.nuvoton.nutoothbrush

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import com.nuvoton.nutoothbrush.CMDManager.Array_Adapter
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList

class HistoryActivity : AppCompatActivity() {

    private lateinit var _textView_LastSec :TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        _textView_LastSec = findViewById<View>(R.id.textView_LastSec) as TextView
        CMDManager.getlastHistory { date, sec, displayText ->
            runOnUiThread {
                _textView_LastSec.setText(sec)
            }
        }



        var mListView = findViewById<ListView>(R.id.History_ListView)
        Array_Adapter = ArrayAdapter(this,R.layout.list_item, CMDManager.Displaylist)
        mListView.adapter = Array_Adapter

        CMDManager.notifyDataSetChangedDisplaylist(this)

        Array_Adapter!!.notifyDataSetChanged()
    }
}