package com.orbits.paymentapp

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.orbits.paymentapp.R
import com.orbits.paymentapp.databinding.ActivityMainBinding
import com.physicaloid.lib.Physicaloid
import com.physicaloid.lib.usb.driver.uart.UartConfig
import io.nearpay.sdk.Environments
import io.nearpay.sdk.NearPay
import io.nearpay.sdk.utils.PaymentText
import io.nearpay.sdk.utils.enums.AuthenticationData
import io.nearpay.sdk.utils.enums.NetworkConfiguration
import io.nearpay.sdk.utils.enums.PurchaseFailure
import io.nearpay.sdk.utils.enums.TransactionData
import io.nearpay.sdk.utils.enums.UIPosition
import io.nearpay.sdk.utils.listeners.PurchaseListener
import java.util.Locale
import java.util.UUID

class MainActivity : Activity() {
    private val mHandler = Handler()
   // private var on: Button? = null
   // private var off: Button? = null
   // private var btOpen: Button? = null
    private var mSerial: Physicaloid? = null
    private var mStop = false
    private lateinit var binding : ActivityMainBinding
    private lateinit var nearpay: NearPay
    var isPurchased = false
    private var mRunningMainLoop = false
    private val mLoop = Runnable {
        val rbuf = ByteArray(4096)
        while (true) {
            val len = mSerial?.read(rbuf) ?: 0
            rbuf[len] = 0

            if (len > 0) {
                mHandler.post { }
            }

            try {
                Thread.sleep(50)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            if (mStop) {
                mRunningMainLoop = false
                return@Runnable
            }
        }
    }
    private val mUsbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                if (mSerial?.isOpened() == false) {
                    openUsbSerial()
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                mStop = true
                mSerial?.close()
            } else if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    if (mSerial?.isOpened() == false) {
                        openUsbSerial()
                    }
                }
                if (!mRunningMainLoop) {
                    mainloop()
                }
            }
        }
    }

    private fun mainloop() {
        mStop = false
        mRunningMainLoop = true
       // on?.isEnabled = true
       // off?.isEnabled = true
        Toast.makeText(this, "connected", Toast.LENGTH_SHORT).show()
        Thread(mLoop).start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this,R.layout.activity_main)

        mSerial = Physicaloid(this)

        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(mUsbReceiver, filter)
        try {
            openUsbSerial()
        } catch (e: Exception) {
            e.printStackTrace()
        }


        nearpay = NearPay.Builder()
            .context(this@MainActivity)
            .authenticationData(AuthenticationData.Email("development@aflak.com.sa"))
            .environment(Environments.SANDBOX)
            .locale(Locale.getDefault())
            .networkConfiguration(NetworkConfiguration.DEFAULT)
            .uiPosition(UIPosition.CENTER_BOTTOM)
            .paymentText(PaymentText("يرجى تمرير الطاقة", "please tap your card"))
            .loadingUi(true)
            .build()





        binding.confirmButton.setOnClickListener {
            openUsbSerial()
            callPurchase()
        }
    }

    private fun openUsbSerial() {
        if (mSerial == null) {
            Toast.makeText(this, "cannot open", Toast.LENGTH_SHORT).show()
            return
        }

        if (!mSerial!!.isOpened) {
            if (!mSerial!!.open()) {
                Toast.makeText(this, "cannot open", Toast.LENGTH_SHORT).show()
                return
            } else {
                mSerial!!.setConfig(UartConfig(115200, 8, 1, 0, false, false))
                Toast.makeText(this, "connected", Toast.LENGTH_SHORT).show()
            }
        }

        if (!mRunningMainLoop) {
            mainloop()
        }
    }

    override fun onNewIntent(intent: Intent) {
        openUsbSerial()
    }

    fun onClickOff(v: View?) {
        val pos = 1
        val writeByte = byteArrayOf(0xFA.toByte(), pos.toByte(), 0x64.toByte(), 0x00.toByte(), 0xFB.toByte())
        mSerial?.write(writeByte, writeByte.size)
    }

    fun onClickOn(v: View?) {
        val pos = 1
        val writeByte = byteArrayOf(0xFA.toByte(), pos.toByte(), 0x64.toByte(), 0x03.toByte(), 0xFB.toByte())
        println("here is position ::: $writeByte")
        mSerial?.write(writeByte, writeByte.size)
    }

    override fun onDestroy() {
        super.onDestroy()
        mSerial?.close()
        mStop = true
        unregisterReceiver(mUsbReceiver)
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.orbits.paymentapp.ACTION_USB_PERMISSION"
    }

    private fun callPurchase() {
        val customerReferenceNumber = "9ace70b7-977d-4094-b7f4-4ecb17de6753"
        val enableReceiptUi = true
        val enableReversal = true
        val finishTimeOut: Long = 2
        val requestId = UUID.randomUUID()
        val enableUiDismiss = true
        val nearpayAmount = (3.00 * 100).toLong()

        nearpay.purchase(
            nearpayAmount,
            customerReferenceNumber,
            enableReceiptUi,
            enableReversal,
            finishTimeOut,
            requestId,
            enableUiDismiss,
            object : PurchaseListener {

                override fun onPurchaseApproved(transactionData: TransactionData) {
                    val pos = 1
                    val writeByte = byteArrayOf(0xFA.toByte(), pos.toByte(), 0x64.toByte(), 0x03.toByte(), 0xFB.toByte())
                    mSerial?.write(writeByte, writeByte.size)

                    isPurchased = true
                    println("here is door open")

                }


                override fun onPurchaseFailed(purchaseFailure: PurchaseFailure) {
                    when (purchaseFailure) {
                        is PurchaseFailure.PurchaseDeclined -> {


                        }

                        is PurchaseFailure.PurchaseRejected -> {

                        }

                        is PurchaseFailure.AuthenticationFailed -> {

                        }

                        is PurchaseFailure.InvalidStatus -> {

                        }

                        is PurchaseFailure.GeneralFailure -> {

                        }

                        is PurchaseFailure.UserCancelled -> {

                        }
                    }
                }
            })

    }

    override fun onResume() {
        super.onResume()
        if (isPurchased){
            handler(4000){
                println("here is door off")
                val pos = 1
                val writeByte = byteArrayOf(0xFA.toByte(), pos.toByte(), 0x64.toByte(), 0x00.toByte(), 0xFB.toByte())
                mSerial?.write(writeByte, writeByte.size)
            }
        }

    }

    fun handler(delay: Long, block: () -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            block()
        }, delay)
    }

}
