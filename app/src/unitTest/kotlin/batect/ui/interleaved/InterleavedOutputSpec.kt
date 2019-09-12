/*
   Copyright 2017-2019 Charles Korn.

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

package batect.ui.interleaved

import batect.config.Container
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.ui.Console
import batect.ui.ConsoleColor
import batect.ui.text.Text
import batect.ui.text.TextRun
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object InterleavedOutputSpec : Spek({
    describe("interleaved output") {
        given("the task name is longer than all containers' names") {
            val taskName = "short"
            val container1 = Container("c1", imageSourceDoesNotMatter())
            val container2 = Container("c2", imageSourceDoesNotMatter())
            val containers = setOf(container1, container2)
            val console by createForEachTest { mock<Console>() }
            val output by createForEachTest { InterleavedOutput(taskName, containers, console) }

            on("printing output for a container") {
                beforeEachTest { output.printForContainer(container1, TextRun("Some container output")) }

                it("prints output for each container aligned to the end of the task's name") {
                    verify(console).println(Text.bold("c1    | ") + Text("Some container output"))
                }
            }

            on("printing output for the task") {
                beforeEachTest { output.printForTask(TextRun("Some task output")) }

                it("prints output aligned to the end of the task name, with the task name printed in bold white text") {
                    verify(console).println(Text("short | ", ConsoleColor.White, true) + TextRun("Some task output"))
                }
            }
        }

        given("the task name is shorter than all containers' names") {
            val taskName = "t"
            val container1 = Container("container1", imageSourceDoesNotMatter())
            val container2 = Container("c2", imageSourceDoesNotMatter())
            val containers = setOf(container1, container2)
            val console by createForEachTest { mock<Console>() }
            val output by createForEachTest { InterleavedOutput(taskName, containers, console) }

            on("printing output for a container") {
                beforeEachTest { output.printForContainer(container1, TextRun("Some container output")) }

                it("prints output for each container aligned to the end of the longest container name") {
                    verify(console).println(Text.bold("container1 | ") + Text("Some container output"))
                }
            }

            on("printing output for the task") {
                beforeEachTest { output.printForTask(TextRun("Some task output")) }

                it("prints output aligned to the end of the longest container name, with the task name printed in bold white text") {
                    verify(console).println(Text("t          | ", ConsoleColor.White, true) + TextRun("Some task output"))
                }
            }
        }
    }
})