package com.dstl.sunflower

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Button

class TestCmdreqDialog(context: Context,private val listener: (Int) -> Unit) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_cmdreq)

        val btn_cmdreq_01 = findViewById<Button>(R.id.btn_cmdreq_01)
        val btn_cmdreq_02 = findViewById<Button>(R.id.btn_cmdreq_02)
        val btn_cmdreq_03 = findViewById<Button>(R.id.btn_cmdreq_03)
        val btn_cmdreq_04 = findViewById<Button>(R.id.btn_cmdreq_04)
        val btn_cmdreq_05 = findViewById<Button>(R.id.btn_cmdreq_05)
        val btn_cmdreq_06 = findViewById<Button>(R.id.btn_cmdreq_06)
        val btn_cmdreq_07 = findViewById<Button>(R.id.btn_cmdreq_07)
        val btn_cmdreq_08 = findViewById<Button>(R.id.btn_cmdreq_08)
        val btn_cmdreq_09 = findViewById<Button>(R.id.btn_cmdreq_09)

        btn_cmdreq_01.setOnClickListener {
            listener(1)
            dismiss()
        }
        btn_cmdreq_02.setOnClickListener {
            listener(2)
            dismiss()
        }
        btn_cmdreq_03.setOnClickListener {
            listener(3)
            dismiss()
        }
        btn_cmdreq_04.setOnClickListener {
            listener(4)
            dismiss()
        }
        btn_cmdreq_05.setOnClickListener {
            listener(5)
            dismiss()
        }
        btn_cmdreq_06.setOnClickListener {
            listener(6)
            dismiss()
        }
        btn_cmdreq_07.setOnClickListener {
            listener(7)
            dismiss()
        }
        btn_cmdreq_08.setOnClickListener {
            listener(8)
            dismiss()
        }
        btn_cmdreq_09.setOnClickListener {
            listener(9)
            dismiss()
        }
    }

}