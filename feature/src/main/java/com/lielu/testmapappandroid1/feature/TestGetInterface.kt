package com.lielu.testmapappandroid1.feature

import com.amazonaws.mobileconnectors.lambdainvoker.LambdaFunction

interface MyInterface {

    /**
     * Invoke the Lambda function "AndroidBackendLambdaFunction".
     * The function name is the method name.
     */
    @LambdaFunction
    fun TestGetFunction(request: RequestClass): ResponseClass

}