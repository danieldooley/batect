/*
   Copyright 2017 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.ui

import batect.config.Container
import batect.docker.DockerContainer
import batect.model.events.ContainerBecameHealthyEvent
import batect.model.events.ContainerRemovedEvent
import batect.model.steps.CleanUpContainerStep
import batect.model.steps.CreateTaskNetworkStep
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.RemoveContainerStep
import batect.model.steps.RunContainerStep
import batect.testutils.CreateForEachTest
import batect.testutils.imageSourceDoesNotMatter
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object FancyEventLoggerSpec : Spek({
    describe("a fancy event logger") {
        val whiteConsole by CreateForEachTest(this) { mock<Console>() }
        val console by CreateForEachTest(this) {
            mock<Console> {
                on { withColor(eq(ConsoleColor.White), any()) } doAnswer {
                    val printStatements = it.getArgument<Console.() -> Unit>(1)
                    printStatements(whiteConsole)
                }
            }
        }

        val redErrorConsole by CreateForEachTest(this) { mock<Console>() }
        val errorConsole by CreateForEachTest(this) {
            mock<Console> {
                on { withColor(eq(ConsoleColor.Red), any()) } doAnswer {
                    val printStatements = it.getArgument<Console.() -> Unit>(1)
                    printStatements(redErrorConsole)
                }
            }
        }

        val startupProgressDisplay by CreateForEachTest(this) { mock<StartupProgressDisplay>() }

        val logger by CreateForEachTest(this) {
            FancyEventLogger(console, errorConsole, startupProgressDisplay)
        }

        describe("when logging that a step is starting") {
            val container = Container("task-container", imageSourceDoesNotMatter())
            val dockerContainer = DockerContainer("some-id")

            on("while the task is starting up") {
                val step = CreateTaskNetworkStep
                logger.onStartingTaskStep(step)

                it("notifies the startup progress display of the step and then reprints it") {
                    inOrder(startupProgressDisplay) {
                        verify(startupProgressDisplay).onStepStarting(step)
                        verify(startupProgressDisplay).print(console)
                    }
                }
            }

            on("and that step is to run the task container") {
                val step = RunContainerStep(container, dockerContainer)
                logger.onStartingTaskStep(step)

                it("notifies the startup progress display of the step, reprints it and then prints a blank line") {
                    inOrder(startupProgressDisplay, console) {
                        verify(startupProgressDisplay).onStepStarting(step)
                        verify(startupProgressDisplay).print(console)
                        verify(console).println()
                    }
                }
            }

            mapOf(
                    "after the task has run" to RunContainerStep(container, dockerContainer),
                    "after an error message has been displayed" to DisplayTaskFailureStep("Something went wrong")
            ).forEach { description, originalStep ->
                describe(description) {
                    beforeEachTest {
                        logger.onStartingTaskStep(originalStep)
                        reset(startupProgressDisplay)
                    }

                    mapOf(
                            "remove container" to RemoveContainerStep(container, dockerContainer),
                            "clean up container" to CleanUpContainerStep(container, dockerContainer)
                    ).forEach { description, step ->
                        describe("when a '$description' step is starting") {
                            on("and no 'remove container' or 'clean up container' steps have run before") {
                                logger.onStartingTaskStep(step)

                                it("prints a message to the output") {
                                    verify(whiteConsole).println("\nCleaning up...")
                                }

                                it("does not notify the startup progress display") {
                                    verify(startupProgressDisplay, never()).onStepStarting(step)
                                }

                                it("does not reprint the startup progress display") {
                                    verify(startupProgressDisplay, never()).print(console)
                                }
                            }

                            on("and a 'remove container' step has already been run") {
                                val previousStep = RemoveContainerStep(Container("other-container", imageSourceDoesNotMatter()), DockerContainer("some-other-id"))
                                logger.onStartingTaskStep(previousStep)

                                logger.onStartingTaskStep(step)

                                it("only prints one message to the output") {
                                    verify(whiteConsole, times(1)).println("\nCleaning up...")
                                }

                                it("does not notify the startup progress display") {
                                    verify(startupProgressDisplay, never()).onStepStarting(step)
                                }

                                it("does not reprint the startup progress display") {
                                    verify(startupProgressDisplay, never()).print(console)
                                }
                            }

                            on("and a 'clean up container' step has already been run") {
                                val previousStep = CleanUpContainerStep(Container("other-container", imageSourceDoesNotMatter()), DockerContainer("some-other-id"))
                                logger.onStartingTaskStep(previousStep)

                                logger.onStartingTaskStep(step)

                                it("only prints one message to the output") {
                                    verify(whiteConsole, times(1)).println("\nCleaning up...")
                                }

                                it("does not notify the startup progress display") {
                                    verify(startupProgressDisplay, never()).onStepStarting(step)
                                }

                                it("does not reprint the startup progress display") {
                                    verify(startupProgressDisplay, never()).print(console)
                                }
                            }
                        }
                    }
                }
            }

            on("and that step is to display an error message") {
                val step = DisplayTaskFailureStep("Something went wrong.")
                logger.onStartingTaskStep(step)

                it("prints the message to the output") {
                    inOrder(redErrorConsole) {
                        verify(redErrorConsole).println()
                        verify(redErrorConsole).println(step.message)
                    }
                }

                it("does not notify the startup progress display") {
                    verify(startupProgressDisplay, never()).onStepStarting(step)
                }

                it("does not reprint the startup progress display") {
                    verify(startupProgressDisplay, never()).print(console)
                }
            }
        }

        describe("when logging an event") {
            on("while the task is starting up") {
                val event = ContainerBecameHealthyEvent(Container("some-container", imageSourceDoesNotMatter()))
                logger.postEvent(event)

                it("notifies the startup progress display of the event and then reprints it") {
                    inOrder(startupProgressDisplay) {
                        verify(startupProgressDisplay).onEventPosted(event)
                        verify(startupProgressDisplay).print(console)
                    }
                }
            }

            on("after the task has run") {
                logger.onStartingTaskStep(RunContainerStep(Container("task-container", imageSourceDoesNotMatter()), DockerContainer("some-id")))
                reset(startupProgressDisplay)

                val event = ContainerRemovedEvent(Container("some-container", imageSourceDoesNotMatter()))
                logger.postEvent(event)

                it("does not reprint the startup progress display") {
                    verify(startupProgressDisplay, never()).print(any())
                }

                it("does not notify the startup progress display of the event") {
                    verify(startupProgressDisplay, never()).onEventPosted(event)
                }
            }

            on("after an error message has been displayed") {
                logger.onStartingTaskStep(DisplayTaskFailureStep("Something went wrong"))
                reset(startupProgressDisplay)

                val event = ContainerBecameHealthyEvent(Container("some-container", imageSourceDoesNotMatter()))
                logger.postEvent(event)

                it("does not reprint the startup progress display") {
                    verify(startupProgressDisplay, never()).print(any())
                }

                it("does not notify the startup progress display of the event") {
                    verify(startupProgressDisplay, never()).onEventPosted(event)
                }
            }
        }
    }
})