package utils.extensions

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import utils.ifNotInCiEnvironment
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Thread.sleep
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Will enable the [DockerizedRabbitMQExtension] to start a RabbitMQ Docker container
 * before the first test is executed and remove it after the last test was executed.
 */
@Retention
@Target(AnnotationTarget.CLASS)
@ExtendWith(DockerizedRabbitMQExtension::class)
annotation class UseDockerToRunRabbitMQ

private class DockerizedRabbitMQExtension : DockerizedDependencyExtension(
        dockerComposeFile = "docker-rabbitmq.yml",
        startupPhrase = "started TCP Listener on"
)

private abstract class DockerizedDependencyExtension(
        private val dockerComposeFile: String,
        private val startupPhrase: String
) : BeforeAllCallback, AfterAllCallback {

    private val dockerCompose = "docker-compose -f src/test/resources/$dockerComposeFile"
    private val log = ConcurrentLinkedQueue<String>()

    override fun beforeAll(context: ExtensionContext) = ifNotInCiEnvironment {
        onlyInFirst(context) {
            execute("$dockerCompose down").waitFor()
            execute("$dockerCompose up")
            if (!waitUntilServiceWasStarted()) {
                error("service did not start in time: ${javaClass.simpleName}")
            }
        }
    }

    override fun afterAll(context: ExtensionContext) = ifNotInCiEnvironment {
        onlyInFirst(context) {
            execute("$dockerCompose down").waitFor()
        }
    }

    private fun execute(command: String): Process {
        val process = Runtime.getRuntime().exec(command)
        startAsDaemonThread { process.inputStream.streamToLog() }
        startAsDaemonThread { process.errorStream.streamToLog() }
        return process
    }

    private fun startAsDaemonThread(body: () -> Unit) {
        with(Thread { body() }) {
            isDaemon = true
            start()
        }
    }

    private fun InputStream.streamToLog() {
        BufferedReader(InputStreamReader(this)).use {
            it.lines().forEach {
                println(it)
                log.add(it)
            }
        }
    }

    private fun waitUntilServiceWasStarted(): Boolean {
        val start = now()
        var started = false
        while (!started && (now() - start) < 30_000L) {
            while (log.peek() != null) {
                if (log.poll().contains(startupPhrase, true)) {
                    started = true
                }
            }
            sleep(100L)
        }
        return started
    }

    private fun now() = System.currentTimeMillis()

    private fun onlyInFirst(context: ExtensionContext, body: () -> Unit) {
        val firstContext = context.store.get("firstContext", ExtensionContext::class.java)
        if (firstContext == null || firstContext == context) {
            context.store.put("firstContext", context)
            body()
        }
    }

    private val ExtensionContext.store: ExtensionContext.Store
        get() = getStore(ExtensionContext.Namespace.create("DockerizedDependencies[$dockerComposeFile]"))
}