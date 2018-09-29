package com.lielu.testmapappandroid1.feature

import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

//import com.amazonaws.mobileconnectors.apigateway.ApiClientFactory
//import com.amazonaws.mobileconnectors.apigateway.ApiRequest
//import com.lielu.testmapappandroid1.clientsdk.WildRydesClient
import com.amazonaws.mobileconnectors.lambdainvoker.*
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.regions.Regions
import android.os.AsyncTask.execute
import android.util.Log
import android.widget.Toast


class MainActivity : AppCompatActivity() {

    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> {
                //message.setText(R.string.title_home)
                startActivity(Intent(this, BasicMapDemoActivity::class.java))
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_dashboard -> {
                //message.setText(R.string.title_dashboard)
                startActivity(Intent(this, MarkerDemoActivity::class.java))
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_notifications -> {
                message.setText(R.string.title_notifications)
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)

        // Create an instance of CognitoCachingCredentialsProvider
        val cognitoProvider = CognitoCachingCredentialsProvider(
                this.applicationContext, "us-west-2:df2eeb71-a887-4898-bb04-a8a7ea175413", Regions.US_WEST_2)

// Create LambdaInvokerFactory, to be used to instantiate the Lambda proxy.
        val factory = LambdaInvokerFactory(this.applicationContext,
                Regions.US_WEST_2, cognitoProvider)

// Create the Lambda proxy object with a default Json data binder.
// You can provide your own data binder by implementing
// LambdaDataBinder.
        //val myInterface = factory.build(MyInterface::class.java)
        val addCommentInterface = factory.build(TestAddCommentInterface::class.java)

        //val request = RequestClass("John", "Doe")
        val request = AddCommentRequestClass("105.1134", "-40.983748", "Comment from Android!")

// The Lambda function invocation results in a network call.
// Make sure it is not called from the main thread.
        object : AsyncTask<AddCommentRequestClass, Void, ResponseClass>() {
            protected override fun doInBackground(vararg params: AddCommentRequestClass): ResponseClass? {
                // invoke "echo" method. In case it fails, it will throw a
                // LambdaFunctionException.
                try {
                    return addCommentInterface.AddCommentFunction(params[0])
                } catch (lfe: LambdaFunctionException) {
                    Log.e("Tag", "Failed to invoke echo", lfe)
                    return null
                }

            }

            protected override fun onPostExecute(result: ResponseClass?) {
                if (result == null) {
                    return
                }

                // Do a toast
                Toast.makeText(this@MainActivity, result.greetings, Toast.LENGTH_LONG).show()
                message.setText(result.greetings)
            }
        }.execute(request)
    }
}
