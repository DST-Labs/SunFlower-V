package com.dstl.sunflower

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch

class TestCustomDialog(context: Context, val speed: Int, val alt : Double, val radius: Double, val circlepoint: Int, private val listener: (Int,Double, Double, Int, Int, Boolean, Boolean, Boolean) -> Unit) : Dialog(context){

    override fun onStart() {
        super.onStart()
        val displayMetrics = context.resources.displayMetrics
        val dialogWidth = displayMetrics.widthPixels / 2

        window?.setLayout(
            dialogWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_dialog)

        val edt_setspeed = findViewById<EditText>(R.id.set_speed_edt)
        val edt_setAlt = findViewById<EditText>(R.id.set_alt_edt)
        val edt_circleradius = findViewById<EditText>(R.id.circle_radius_edt)
        val edt_circlepoint = findViewById<EditText>(R.id.circle_point_edt)
        val edt_circlestart = findViewById<EditText>(R.id.circle_start_edt)
        val sw_spin = findViewById<Switch>(R.id.circle_cwccw_sw)
        val sw_infinity = findViewById<Switch>(R.id.circle_infinity_sw)
        val btn_circle = findViewById<Button>(R.id.circle_bt)
        val btn_custom = findViewById<Button>(R.id.custom_bt)


        var speeddata : Int = speed
        var altdata : Double = alt
        var circleradius : Double = radius
        var circlepointdata : Int = circlepoint
        var circlestartdata : Int = 0
        var spin : Boolean = true
        var infinity : Boolean = true


        edt_setspeed.setText(speeddata.toString())
        edt_setAlt.setText(altdata.toString())
        edt_circleradius.setText(circleradius.toString())
        edt_circlepoint.setText(circlepointdata.toString())
        edt_circlestart.setText(circlestartdata.toString())

        sw_spin.setOnCheckedChangeListener{ CompoundButton ,b ->
            if(b){
                spin = false // 정방향
                Log.d("TestCustomDialog", "sw_spin : " + spin.toString())
            }
            else{
                spin = true // 역방향
                Log.d("TestCustomDialog", "sw_spin : " + spin.toString())
            }
        }

        sw_infinity.setOnCheckedChangeListener{ CompoundButton ,b ->
            if(b){
                infinity = false // 정방향
                Log.d("TestCustomDialog", "sw_infinity : " + infinity.toString())
            }
            else{
                infinity = true // 역방향
                Log.d("TestCustomDialog", "sw_infinity : " + infinity.toString())
            }
        }

        btn_circle.setOnClickListener{
            speeddata = edt_setspeed.text.toString().toInt()
            altdata = edt_setAlt.text.toString().toDouble()
            circleradius = edt_circleradius.text.toString().toDouble()
            circlepointdata = edt_circlepoint.text.toString().toInt()
            circlestartdata = edt_circlestart.text.toString().toInt()
            listener(speeddata,altdata,circleradius,circlepointdata,circlestartdata,spin,infinity,true)
            dismiss()
        }

        btn_custom.setOnClickListener{
            speeddata = edt_setspeed.text.toString().toInt()
            altdata = edt_setspeed.text.toString().toDouble()
            circleradius = edt_circleradius.text.toString().toDouble()
            circlepointdata = edt_circlepoint.text.toString().toInt()
            circlestartdata = edt_circlestart.text.toString().toInt()
            listener(speeddata,circleradius,altdata,circlepointdata,circlestartdata,spin,infinity,false)
            dismiss()
        }

    }
}