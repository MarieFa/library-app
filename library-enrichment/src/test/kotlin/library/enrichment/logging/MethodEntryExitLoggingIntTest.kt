package library.enrichment.logging

import library.enrichment.logging.MethodEntryExitLoggingIntTest.TestConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testit.testutils.logrecorder.api.LogRecord
import org.testit.testutils.logrecorder.junit5.RecordLoggers
import utils.classification.IntegrationTest

@IntegrationTest
@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [TestConfiguration::class])
@ActiveProfiles("test")
internal class MethodEntryExitLoggingIntTest {

    @ComponentScan
    @EnableAspectJAutoProxy
    class TestConfiguration {
        @Bean fun exampleClass() = ExampleClass()
        @Bean fun annotatedExampleClass() = AnnotatedExampleClass()
    }

    @Autowired lateinit var example: ExampleClass
    @Autowired lateinit var annotatedExample: AnnotatedExampleClass

    @RecordLoggers(LogMethodEntryAndExitAspect::class)
    @Test fun `logging proxy is activated by annotation`(record: LogRecord) {
        example.openPublicMethod()
        annotatedExample.openPublicMethod()
        assertThat(record.messages).hasSize(2)
    }

    @RecordLoggers(LogMethodEntryAndExitAspect::class)
    @Test fun `open public methods are logged`(record: LogRecord) {
        annotatedExample.openPublicMethod()
        val className = AnnotatedExampleClass::class.java.name
        val expectedMessages = arrayOf(
                "executing method: void $className.openPublicMethod()",
                "successfully executed method: void $className.openPublicMethod()"
        )
        assertThat(record.messages).containsExactly(*expectedMessages)
    }

    @RecordLoggers(LogMethodEntryAndExitAspect::class)
    @Test fun `only open public methods are logged`(logRecord: LogRecord) {
        annotatedExample.closedPublicMethod()
        annotatedExample.internalMethod()
        assertThat(logRecord.messages).hasSize(0)
    }

    open class ExampleClass {
        open fun openPublicMethod() {}
    }

    @LogMethodEntryAndExit
    open class AnnotatedExampleClass {
        open fun openPublicMethod() {}
        fun closedPublicMethod() {}
        internal fun internalMethod() {}
    }

}