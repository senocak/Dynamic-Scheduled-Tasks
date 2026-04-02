package com.github.senocak.jobscheduler.config

import com.github.senocak.jobscheduler.logger
import org.slf4j.Logger
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.util.StreamUtils
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Method
import java.util.Date
import java.util.stream.Stream

@Configuration
class RestClientInterceptor : ClientHttpRequestInterceptor {
    private val log: Logger by logger()

    @Throws(exceptionClasses = [IOException::class])
    override fun intercept(
        request: HttpRequest,
        requestBody: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        val callerMethod: Method? = resolveCallingMethod()
        val startTime: Long = System.currentTimeMillis()
        try {
            val response: ClientHttpResponse = execution.execute(request, requestBody)
            val responseBody: ByteArray = StreamUtils.copyToByteArray(response.body)
            val httpStatus: HttpStatus? = HttpStatus.resolve(response.statusCode.value())
            val classMethodPair: Pair<String, String> = getClassAndMethodName(callerMethod)
            val className: String = classMethodPair.first
            val methodName: String = classMethodPair.second
            log.info("<$className> $methodName Rest Web Service Called With Uri: ${request.uri}, Request Method: ${request.method.name()}, Request : [${requestBody.contentToString()}] -> Returned Response Status: $httpStatus, Response : [${responseBody.contentToString()}] -> Response Time: ${(Date().time - startTime)} ms")
            return BufferingClientHttpResponseWrapper(response, responseBody)
        } catch (ex: Exception) {
            log.error("<intercept> Unknown Exception Occurred.", ex)
            throw ex
        }
    }

    private fun resolveCallingMethod(): Method? {
        try {
            return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk { frames: Stream<StackWalker.StackFrame> -> frames
                    .filter { f: StackWalker.StackFrame -> isCallerClass(f.declaringClass) }
                    .findFirst()
                    .map<Method> { frame: StackWalker.StackFrame? -> this.findMethodFromFrame(frame!!) }
                    .orElse(null)
                }
        } catch (e: Exception) {
            log.error("<resolveCallingMethod> Unknown Exception Occurred. ${e.message}")
        }

        return null
    }

    private fun isCallerClass(clazz: Class<*>): Boolean {
        val className: String = clazz.getName()
        return className.startsWith("com.turkcell.cmp") && !className.startsWith("com.turkcell.cmp.core.aop.RestClientInterceptor")
    }

    private fun findMethodFromFrame(frame: StackWalker.StackFrame): Method? {
        for (method: Method in frame.declaringClass.declaredMethods) {
            if (method.name == frame.methodName) {
                return method
            }
        }
        return null
    }

    private fun getClassAndMethodName(callerMethod: Method?): Pair<String, String> {
        var className = "UnknownClass"
        var methodName = "UnknownMethod"
        if (callerMethod != null) {
            methodName = callerMethod.name
            className = callerMethod.declaringClass.getSimpleName()
        }
        return Pair(className, methodName)
    }

    private class BufferingClientHttpResponseWrapper(
        private val original: ClientHttpResponse,
        private val body: ByteArray
    ) : ClientHttpResponse {
        override fun getStatusCode(): HttpStatusCode = original.statusCode
        override fun getStatusText(): String = original.statusText
        override fun close() { original.close() }
        override fun getBody(): InputStream = ByteArrayInputStream(body)
        override fun getHeaders(): HttpHeaders = original.headers
    }
}
