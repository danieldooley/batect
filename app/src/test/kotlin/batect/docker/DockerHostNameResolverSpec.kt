/*
   Copyright 2017-2018 Charles Korn.

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

package batect.docker

import batect.os.OperatingSystem
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.utils.Version
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DockerHostNameResolverSpec : Spek({
    describe("a Docker host name resolver") {
        val systemInfo by createForEachTest { mock<SystemInfo>() }
        val dockerClient by createForEachTest { mock<DockerClient>() }
        val resolver by createForEachTest { DockerHostNameResolver(systemInfo, dockerClient) }

        given("the local system is running OS X") {
            beforeEachTest { whenever(systemInfo.operatingSystem).doReturn(OperatingSystem.Mac) }

            on("the Docker version being less than 17.06") {
                whenever(dockerClient.getDockerVersionInfo()).doReturn(DockerVersionInfoRetrievalResult.Succeeded(
                    createDockerVersionInfoWithServerVersion(Version(17, 5, 0))
                ))

                val result = resolver.resolveNameOfDockerHost()

                it("returns that getting the Docker host's name is not supported") {
                    assertThat(result, equalTo(DockerHostNameResolutionResult.NotSupported))
                }
            }

            data class Scenario(val dockerVersion: Version, val expectedHostName: String, val description: String)

            listOf(
                Scenario(Version(17, 6, 0), "docker.for.mac.localhost", "the Docker version being 17.06"),
                Scenario(Version(17, 6, 0, "mac1"), "docker.for.mac.localhost", "the Docker version being above 17.06 but less than 17.12"),
                Scenario(Version(17, 12, 0), "docker.for.mac.host.internal", "the Docker version being 17.12"),
                Scenario(Version(17, 12, 0, "mac2"), "docker.for.mac.host.internal", "the Docker version being above 17.12 but less than 18.03"),
                Scenario(Version(18, 3, 0), "host.docker.internal", "the Docker version being 18.03"),
                Scenario(Version(18, 3, 0, "mac3"), "host.docker.internal", "the Docker version being above 18.03")
            ).forEach { (dockerVersion, expectedHostName, description) ->
                on(description) {
                    whenever(dockerClient.getDockerVersionInfo()).doReturn(DockerVersionInfoRetrievalResult.Succeeded(
                        createDockerVersionInfoWithServerVersion(dockerVersion)
                    ))

                    val result = resolver.resolveNameOfDockerHost()

                    it("returns that getting the Docker host's name is '$expectedHostName'") {
                        assertThat(result, equalTo(DockerHostNameResolutionResult.Resolved(expectedHostName)))
                    }
                }
            }

            on("the Docker version not being able to be determined") {
                whenever(dockerClient.getDockerVersionInfo()).doReturn(DockerVersionInfoRetrievalResult.Failed("Couldn't get version."))

                val result = resolver.resolveNameOfDockerHost()

                it("returns that getting the Docker host's name is not supported") {
                    assertThat(result, equalTo(DockerHostNameResolutionResult.NotSupported))
                }
            }
        }

        on("the local system is running Linux") {
            whenever(systemInfo.operatingSystem).thenReturn(OperatingSystem.Linux)

            val result = resolver.resolveNameOfDockerHost()

            it("returns that getting the Docker host's name is not supported") {
                assertThat(result, equalTo(DockerHostNameResolutionResult.NotSupported))
            }
        }

        on("the local system is running another operating system") {
            whenever(systemInfo.operatingSystem).thenReturn(OperatingSystem.Other)

            val result = resolver.resolveNameOfDockerHost()

            it("returns that getting the Docker host's name is not supported") {
                assertThat(result, equalTo(DockerHostNameResolutionResult.NotSupported))
            }
        }
    }
})

private fun createDockerVersionInfoWithServerVersion(version: Version) = DockerVersionInfo(version, "", "", "")
