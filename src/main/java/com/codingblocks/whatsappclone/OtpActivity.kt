package com.codingblocks.whatsappclone

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Message
import android.os.PersistableBundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.android.synthetic.main.activity_otp.*
import java.util.concurrent.TimeUnit

const val PHONE_NUMBER = "phoneNumber"
class OtpActivity : AppCompatActivity(), View.OnClickListener {

    lateinit var callbacks:PhoneAuthProvider.OnVerificationStateChangedCallbacks
    var phoneNumber:String? = null
    var mVerificationId:String? = null
    var mResendToken:PhoneAuthProvider.ForceResendingToken? = null
    private lateinit var progressDialog:ProgressDialog
    private var mCounterDown:CountDownTimer? = null
    private var timeLeft: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)
        initViews()
        startVerify()
    }

    private fun startVerify() {
        startPhoneNumberVerification(phoneNumber!!)
        showTimer(60000)
        progressDialog = createProgressDialog("Sending a verification code", false)
        progressDialog.show()
    }

    private fun initViews() {
        phoneNumber = intent.getStringExtra(PHONE_NUMBER)
        verifyTv.text = getString(R.string.verify_number,phoneNumber)
        setSpannableString()

        verificationBtn.setOnClickListener(this)
        resendBtn.setOnClickListener(this)

        callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // This callback will be invoked in two situations:
                // 1 - Instant verification. In some cases the phone number can be instantly
                //     verified without needing to send or enter a verification code.
                // 2 - Auto-retrieval. On some devices Google Play services can automatically
                //     detect the incoming verification SMS and perform verification without
                //     user action.
                signInWithPhoneAuthCredential(credential)
                if (::progressDialog.isInitialized){
                    progressDialog.dismiss()
                }
                val smsCode = credential.smsCode
                if(!smsCode.isNullOrBlank())
                    sentcodeEt.setText(smsCode)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                // This callback is invoked in an invalid request for verification is made,
                // for instance if the the phone number format is not valid.
                if (::progressDialog.isInitialized){
                    progressDialog.dismiss()
                }

                if (e is FirebaseAuthInvalidCredentialsException) {
                    // Invalid request
                    // ...
                } else if (e is FirebaseTooManyRequestsException) {
                    // The SMS quota for the project has been exceeded
                    // ...
                }
                notifyUserandRetry("Your Phone number might be wrong or connection error.Retry again!")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                progressDialog.dismiss()
                counterTv.isVisible = false

                mVerificationId = verificationId
                mResendToken = token
            }
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun setSpannableString() {
        val span = SpannableString(getString(R.string.waiting_text_2,phoneNumber))
        val clickableSpan = object : ClickableSpan(){
            override fun onClick(widget: View) {
                showLoginActivity()
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
                ds.color = ds.linkColor
            }
        }
        span.setSpan(clickableSpan,span.length - 13,span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        waitingTv.movementMethod = LinkMovementMethod.getInstance()
        waitingTv.text = span
    }

    private fun notifyUserandRetry(message: String) {
        MaterialAlertDialogBuilder(this).apply {
            setMessage(message)
            setPositiveButton("Ok") { _, _ ->
                showLoginActivity()
            }

            setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

            setCancelable(false)
            create()
            show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        outState.putLong("timeLeft",timeLeft)
        outState.putString(PHONE_NUMBER,phoneNumber)
    }

    private fun startPhoneNumberVerification(phoneNumber: String) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
            phoneNumber,
            60,
            TimeUnit.SECONDS,
            this,
            callbacks
        )
    }

    private fun showLoginActivity() {
        startActivity(Intent(this,LoginActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        val mAuth = FirebaseAuth.getInstance()
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this){ task ->
                if(task.isSuccessful){
                    if (::progressDialog.isInitialized) {
                        progressDialog.dismiss()
                    }
                    if (task.result?.additionalUserInfo?.isNewUser == true) {
                        showSignUpActivity()
                    }else {
                        showHomeActivity()
                    }
                }else{
                    if (::progressDialog.isInitialized) {
                        progressDialog.dismiss()
                    }

                    notifyUserandRetry("Your Phone number verification failed.Try again !!")
                }
            }
    }

    private fun showSignUpActivity() {
        val intent = Intent(this,SignUpActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showHomeActivity() {
        val intent = Intent(this,MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {

    }

    override fun onDestroy() {
        super.onDestroy()
        if (mCounterDown!=null){
            mCounterDown!!.cancel()
        }
    }

    override fun onClick(v: View?) {
        when(v){
            verificationBtn -> {
                var code = sentcodeEt.text.toString()
                if (code.isNotEmpty() && !mVerificationId.isNullOrBlank()){
                    progressDialog = createProgressDialog("Please wait....",false)
                    progressDialog.show()

                    val credential = PhoneAuthProvider.getCredential(mVerificationId!!,code)
                    signInWithPhoneAuthCredential(credential)
                }
            }
            resendBtn -> {
                if (mResendToken != null){
                    resendVerificationCode(phoneNumber.toString(), mResendToken!!)
                    showTimer(600000)
                    progressDialog = createProgressDialog("Sending a verification code",false)
                    progressDialog.show()
                }
                else{
                    Toast.makeText(this,"Sorry, You cannot request a new code now, Please wait ...",Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showTimer(milliSecInFuture: Long) {
        resendBtn.isEnabled = false
        object:CountDownTimer(milliSecInFuture,1000){
            override fun onFinish() {
                resendBtn.isEnabled = true
                counterTv.isEnabled = false
            }

            override fun onTick(millisUntilFinished: Long) {
                timeLeft = millisUntilFinished
                counterTv.text = getString(R.string.seconds_remaining,millisUntilFinished/1000)
                counterTv.isVisible = true
            }
        }.start()
    }

    private fun resendVerificationCode(phoneNumber: String,mResendToken: PhoneAuthProvider.ForceResendingToken) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
            phoneNumber,
            60,
            TimeUnit.SECONDS,
            this,
            callbacks,
            mResendToken
        )
    }
}

fun Context.createProgressDialog(message: String, isCancelable: Boolean): ProgressDialog{
    return ProgressDialog(this).apply {
        setCancelable(isCancelable)
        setMessage(message)
        setCanceledOnTouchOutside(false)
    }
}