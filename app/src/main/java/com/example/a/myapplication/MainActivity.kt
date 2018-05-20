package com.example.a.myapplication

import android.os.Bundle

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.preference.PreferenceManager
import android.util.Log
import android.view.*
import android.net.sip.*
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.ToggleButton

import java.text.ParseException
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.media.AudioManager




class MainActivity : Activity(), View.OnTouchListener {

    var sipAddress: String? = null

    var manager: SipManager? = null
    var me: SipProfile? = null
    var call: SipAudioCall? = null
    var callReceiver: IncomingCallReceiver? = null

    var mRecognizer: SpeechRecognizer? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val pushToTalkButton = findViewById<ToggleButton>(R.id.pushToTalk)
        pushToTalkButton.setOnTouchListener(this)

        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)            //음성인식 intent생성
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)    //데이터 설정
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)//음성인식 언어 설정
            mRecognizer = SpeechRecognizer.createSpeechRecognizer(this)                //음성인식 객체
            mRecognizer?.setRecognitionListener(listener)                                        //음성인식 리스너 등록
            mRecognizer?.startListening(i)

            //showDialog(CALL_ADDRESS)
        }

        val hangUpButton = findViewById<Button>(R.id.hang_up_button)
        hangUpButton.setOnClickListener {
            if (call != null) {
                try {
                    call!!.endCall()
                    initializeManager()
                } catch (se: SipException) {
                    Log.d("error", "Error ending call.", se)
                }
                call!!.close()
            }
        }

        // Set up the intent filter.  This will be used to fire an
        // IncomingCallReceiver when someone calls the SIP address used by this
        // application.
        val filter = IntentFilter()
        filter.addAction("android.SipDemo.INCOMING_CALL")
        callReceiver = IncomingCallReceiver()
        this.registerReceiver(callReceiver, filter)

        // "Push to talk" can be a serious pain when the screen keeps turning off.
        // Let's prevent that.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initializeManager()
    }

    public override fun onStart() {
        super.onStart()
        initializeManager()
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (call != null) {
            call!!.close()
        }

        closeLocalProfile()

        if (callReceiver != null) {
            this.unregisterReceiver(callReceiver)
        }
    }

    fun initializeManager() {
        if (manager == null) {
            manager = SipManager.newInstance(this)
        }
        initializeLocalProfile()
    }

    /**
     * Logs you into your SIP provider, registering this device as the location to
     * send SIP calls to for your SIP address.
     */
    fun initializeLocalProfile() {
        if (manager == null) {
            return
        }

        if (me != null) {
            closeLocalProfile()
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(baseContext)
        val username = prefs.getString("namePref", "soulmade00")
        val domain = prefs.getString("domainPref", "sip.linphone.org")
        val password = prefs.getString("passPref", "cshmgu1004")

        if (username!!.length == 0 || domain!!.length == 0 || password!!.length == 0) {
            showDialog(UPDATE_SETTINGS_DIALOG)
            return
        }

        try {
            val builder = SipProfile.Builder(username, domain)
            builder.setPassword(password)
            me = builder.build()

            val i = Intent()
            i.action = "android.SipDemo.INCOMING_CALL"
            val pi = PendingIntent.getBroadcast(this, 0, i, Intent.FILL_IN_DATA)
            manager!!.open(me, pi, null)


            // This listener must be added AFTER manager.open is called,
            // Otherwise the methods aren't guaranteed to fire.

            manager!!.setRegistrationListener(me!!.uriString, object : SipRegistrationListener {
                override fun onRegistering(localProfileUri: String) {
                    updateStatus("Registering with SIP Server...")
                }

                override fun onRegistrationDone(localProfileUri: String, expiryTime: Long) {
                    updateStatus("Ready")
                }

                override fun onRegistrationFailed(localProfileUri: String, errorCode: Int,
                                                  errorMessage: String) {
                    updateStatus("Registration failed.  Please check settings.")
                }
            })
        } catch (pe: ParseException) {
            updateStatus("Connection Error.")
        } catch (se: SipException) {
            updateStatus("Connection error.")
        }

    }

    /**
     * Closes out your local profile, freeing associated objects into memory
     * and unregistering your device from the server.
     */
    fun closeLocalProfile() {
        if (manager == null) {
            return
        }
        try {
            if (me != null) {
                manager!!.close(me!!.uriString)
            }
        } catch (ee: Exception) {
            Log.d("good", "Failed to close local profile.", ee)
        }

    }

    /**
     * Make an outgoing call.
     */
    fun initiateCall() {

        updateStatus(sipAddress)

        try {
            val listener = object : SipAudioCall.Listener() {
                // Much of the client's interaction with the SIP Stack will
                // happen via listeners.  Even making an outgoing call, don't
                // forget to set up a listener to set things up once the call is established.
                override fun onCallEstablished(call: SipAudioCall) {
                    call.startAudio()
                    call.setSpeakerMode(true)
                    call.toggleMute()
                    updateStatus(call)
                }

                override fun onCallEnded(call: SipAudioCall) {
                    updateStatus("Ready.")
                }
            }

            call = manager!!.makeAudioCall(me!!.uriString, sipAddress, listener, 30)

        } catch (e: Exception) {
            Log.i("error", "Error when trying to close manager.", e)
            if (me != null) {
                try {
                    manager!!.close(me!!.uriString)
                } catch (ee: Exception) {
                    Log.i("error", "Error when trying to close manager.", ee)
                    ee.printStackTrace()
                }

            }
            if (call != null) {
                call!!.close()
            }
        }

    }

    /**
     * Updates the status box at the top of the UI with a messege of your choice.
     * @param status The String to display in the status box.
     */
    fun updateStatus(status: String?) {
        // Be a good citizen.  Make sure UI changes fire on the UI thread.
        this.runOnUiThread {
            val labelView = findViewById(R.id.sipLabel) as TextView
            labelView.text = status
        }
    }

    /**
     * Updates the status box with the SIP address of the current call.
     * @param call The current, active call.
     */
    fun updateStatus(call: SipAudioCall) {
        var useName: String? = call.peerProfile.displayName
        if (useName == null) {
            useName = call.peerProfile.userName
        }
        updateStatus(useName + "@" + call.peerProfile.sipDomain)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = true
    }

    //음성인식 리스너
    private val listener = object : RecognitionListener {
        //입력 소리 변경 시
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onResults(results: Bundle) {
            var key = ""
            key = SpeechRecognizer.RESULTS_RECOGNITION
            val mResult = results.getStringArrayList(key)
            val rs = arrayOfNulls<String>(mResult.size)
            mResult.toArray(rs)
            updateStatus(rs[0])
        }
        //음성 인식 준비가 되었으면
        override fun onReadyForSpeech(params: Bundle) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            updateStatus("에러5")
        }
        override fun onBeginningOfSpeech() {}                            //입력이 시작되면
        override fun onPartialResults(partialResults: Bundle) {}       //인식 결과의 일부가 유효할 때
        //미래의 이벤트를 추가하기 위해 미리 예약되어진 함수
        override fun onEvent(eventType: Int, params: Bundle) {
            updateStatus("에러2")
        }
        override fun onBufferReceived(buffer: ByteArray) {
            updateStatus("에러3")
        }                //더 많은 소리를 받을 때
    }

    /**
     * Updates whether or not the user's voice is muted, depending on whether the button is pressed.
     * @param v The View where the touch event is being fired.
     * @param event The motion to act on.
     * @return boolean Returns false to indicate that the parent view should handle the touch event
     * as it normally would.
     */
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (call == null) {
            return false
        } else if (event.action == MotionEvent.ACTION_DOWN && call != null && call!!.isMuted) {
            call!!.toggleMute()
        } else if (event.action == MotionEvent.ACTION_UP && !call!!.isMuted) {
            call!!.toggleMute()
        }
        return false
    }

    override fun onCreateDialog(id: Int): Dialog? {
        when (id) {
            CALL_ADDRESS -> {
                val factory = LayoutInflater.from(this)
                val textBoxView = factory.inflate(R.layout.call_address_dialog, null)
                return AlertDialog.Builder(this)
                        .setTitle("Call Someone.")
                        .setView(textBoxView)
                        .setPositiveButton(
                                android.R.string.ok, DialogInterface.OnClickListener { dialog, whichButton ->
                            val textField = textBoxView.findViewById(R.id.calladdress_edit) as EditText
                            sipAddress = textField.text.toString()
                            initiateCall()
                        })
                        .setNegativeButton(
                                android.R.string.cancel, DialogInterface.OnClickListener { dialog, whichButton ->
                            // Noop.
                        })
                        .create()
            }
        }
        return null
    }

    companion object {

        private val CALL_ADDRESS = 1
        private val SET_AUTH_INFO = 2
        private val UPDATE_SETTINGS_DIALOG = 3
        private val HANG_UP = 4
    }
}
